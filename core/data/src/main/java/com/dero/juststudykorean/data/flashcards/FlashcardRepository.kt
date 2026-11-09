package com.dero.juststudykorean.data.flashcards

import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.db.CustomDecksEntity
import com.dero.juststudykorean.data.db.DeckGroupsEntity
import com.dero.juststudykorean.data.db.DeckPlacesEntity
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.FlashcardReviewsEntity
import com.dero.juststudykorean.data.db.FlashcardsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.prefs.CardDirection
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.Topics
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.CardState
import com.dero.juststudykorean.korean.Next
import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.korean.englishSenses
import com.dero.juststudykorean.korean.extractGrammar
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

// Port of backend/app/services/flashcard_service.py (and reference_service's merged_words / earlier_*),
// checked against golden/db (FlashcardGoldenTest, and the replay of ops.json in ReplayGoldenTest).

data class LastCard(val koreanWord: String, val translation: String)

/** A deck: a document (> 0), "Other cards" (0), a coach deck (−id) or a combined deck (−(1e9 + group)). */
data class Deck(
    val id: Long,
    val custom: Boolean,
    val merged: Boolean,
    val members: List<String>,
    val name: String,
    val total: Int,
    val new: Int,
    val learning: Int,
    val due: Int,
    val availableNow: Int,
    val lastStudiedAt: ZonedDateTime?,
    val lastCard: LastCard?,
    val nextDueAt: ZonedDateTime?,
)

data class FlashcardStats(val total: Int, val new: Int, val dueNow: Int, val learning: Int, val learned: Int, val nextDueAt: ZonedDateTime?)

data class StudyCard(val card: FlashcardsEntity, val isNew: Boolean, val direction: CardDirection)

data class ReviewItem(
    val flashcardId: Long,
    val deckId: Long,
    val koreanWord: String,
    val translation: String,
    val rating: String,
    val reviewedAt: ZonedDateTime,
    val intervalHours: Long,
)

/** Everything a deck deletion removed, to put it back (Undo). */
data class DeletedDeck(
    val cards: List<FlashcardsEntity>,
    val reviews: List<FlashcardReviewsEntity>,
    val customDecks: List<CustomDecksEntity>,
    val places: List<DeckPlacesEntity>,
    val groups: List<DeckGroupsEntity>,
)

data class CreateResult(val created: Int, val skipped: Int, val totalCards: Int)

class FlashcardException(message: String) : Exception(message)

/** What your notes say about a card, for its back ("From your notes"). */
data class CardDetails(
    val structure: List<String> = emptyList(),
    val examples: List<Pair<String, String>> = emptyList(),
    val explanation: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Where a card comes from: "Source: {name}, page {n}". */
data class CardSource(val filename: String, val page: Int)

private val LEARN_AHEAD = Duration.ofMinutes(20)
private const val GROUP_BASE = 1_000_000_000L
private const val GRAMMAR_DIFFICULTY = 2L
private const val NEW_CANDIDATES = 2000
private const val DECK_NAME_CHARS = 120
private const val MEANING_SEPARATOR = "; "

fun groupDeckId(groupId: Long) = -(GROUP_BASE + groupId)
fun isGroup(deckId: Long?) = deckId != null && deckId <= -GROUP_BASE
private fun groupOf(deckId: Long) = -deckId - GROUP_BASE

/** The deck key of a source chunk id ("3:1:0" -> 3; none -> 0). */
private fun deckKey(sourceChunkId: String?): Long = (sourceChunkId ?: "").split(":")[0].toLongOrNull() ?: 0

fun deckOf(card: FlashcardsEntity): Long = card.customDeckId?.let { -it } ?: deckKey(card.sourceChunkId)

/** s[:n] by code points (Python slicing). */
internal fun String.cut(n: Int): String = if (codePointCount(0, length) <= n) this else substring(0, offsetByCodePoints(0, n))

@Singleton
class FlashcardRepository @Inject constructor(
    private val db: JustStudyDatabase,
    private val clock: AppClock,
    private val progress: ProgressRepository,
    private val notes: NotesSource,
) {
    private val zone get() = clock.zone
    private fun local(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)

    private suspend fun reviewedIds(): Set<Long> = db.flashcardReviewsDao().all().map { it.flashcardId }.toSet()

    /** The decks behind an id: itself or, for a combined deck, the ones it combines. */
    private suspend fun members(deckId: Long?): List<Long>? = when {
        deckId == null -> null
        isGroup(deckId) -> db.deckPlacesDao().all().filter { it.groupId == groupOf(deckId) }.map { it.deckId }
        else -> listOf(deckId)
    }

    private fun inDeck(card: FlashcardsEntity, deckId: Long): Boolean = when {
        deckId < 0 -> card.customDeckId == -deckId
        deckId == 0L -> card.customDeckId == null && card.sourceChunkId == null
        else -> card.customDeckId == null && card.sourceChunkId?.startsWith("$deckId:") == true
    }

    private fun inDecks(card: FlashcardsEntity, decks: List<Long>?) = decks == null || decks.any { inDeck(card, it) }

    // ---------- reads ----------

    fun decks(): Flow<List<Deck>> =
        db.invalidationTracker.createFlow("flashcards", "flashcard_reviews", "deck_places", "deck_groups", "custom_decks", "documents").map { decksNow() }

    /** Each deck with its counters, what you reviewed last and when, arranged as you left them. */
    suspend fun decksNow(): List<Deck> = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val last = db.flashcardReviewsDao().all().groupBy { it.flashcardId }.mapValues { (_, r) -> r.maxOf { it.reviewedAt } }
        val docs = db.documentsDao().all().associateBy { it.id }
        val customs = db.customDecksDao().all().associateBy { it.id }
        val groups = LinkedHashMap<Long, MutableList<FlashcardsEntity>>()
        for (card in db.flashcardsDao().all().sortedBy { it.id }) groups.getOrPut(deckOf(card)) { mutableListOf() } += card
        val base = groups.map { (deckId, cards) ->
            val seen = cards.filter { it.id in last }
            val waiting = seen.filter { it.dueAt > now }
            val due = seen.filter { it.dueAt <= now }
            // Like Anki: an "Again" back in under 20 minutes can be reviewed already.
            val ahead = waiting.filter { it.repetitions == 0L && it.dueAt <= now + LEARN_AHEAD.toMillis() }
            val latest = seen.maxByOrNull { last.getValue(it.id) }
            val upcoming = seen.filter { it.dueAt > now }.map { it.dueAt }
            val name = when {
                deckId < 0 -> customs[-deckId]?.name
                deckId > 0 -> docs[deckId]?.originalName ?: "Deleted document #$deckId"
                else -> "Other cards"
            } ?: "Deleted document #$deckId"
            Deck(
                deckId, deckId < 0, false, emptyList(), name, cards.size, cards.size - seen.size, waiting.size, due.size,
                cards.size - seen.size + due.size + ahead.size, latest?.let { local(last.getValue(it.id)) },
                latest?.let { LastCard(it.koreanWord, it.translation) }, upcoming.minOrNull()?.let(::local),
            )
        }.sortedWith(compareBy<Deck>({ it.lastStudiedAt == null }, { -(it.lastStudiedAt?.toEpochSecond() ?: 0) }, { it.name.lowercase() }))
        arranged(base)
    }

    /** What you changed in edit mode: names, combined decks and the order. */
    private suspend fun arranged(base: List<Deck>): List<Deck> {
        val places = db.deckPlacesDao().all().associateBy { it.deckId }
        val groups = db.deckGroupsDao().all().associateBy { it.id }
        val top = mutableListOf<Deck>()
        val inside = LinkedHashMap<Long, MutableList<Deck>>()
        for (d in base) {
            val place = places[d.id]
            val deck = if (place?.name != null && place.name.isNotEmpty() && !d.custom) d.copy(name = place.name) else d
            if (place?.groupId != null && place.groupId in groups) inside.getOrPut(place.groupId) { mutableListOf() } += deck else top += deck
        }
        for ((groupId, members) in inside) top += combined(groupDeckId(groupId), groups.getValue(groupId).name, members)
        val order = places.filterValues { it.position != null }.mapValues { it.value.position!! }
        return top.sortedWith(compareBy({ it.id !in order }, { order[it.id] ?: 0L }))
    }

    private fun combined(deckId: Long, name: String, members: List<Deck>): Deck {
        val latest = members.filter { it.lastStudiedAt != null }.maxByOrNull { it.lastStudiedAt!!.toInstant() }
        return Deck(
            deckId, false, true, members.map { it.name }, name, members.sumOf { it.total }, members.sumOf { it.new },
            members.sumOf { it.learning }, members.sumOf { it.due }, members.sumOf { it.availableNow }, latest?.lastStudiedAt,
            latest?.lastCard, members.mapNotNull { it.nextDueAt }.minByOrNull { it.toInstant() },
        )
    }

    fun stats(): Flow<FlashcardStats> = db.invalidationTracker.createFlow("flashcards", "flashcard_reviews").map { statsNow() }

    suspend fun statsNow(): FlashcardStats = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val reviewed = reviewedIds()
        val cards = db.flashcardsDao().all()
        val seen = cards.filter { it.id in reviewed }
        FlashcardStats(
            cards.size, cards.size - seen.size, seen.count { it.dueAt <= now }, seen.count { it.repetitions == 0L },
            seen.count { it.repetitions >= 1 }, seen.filter { it.dueAt > now }.minOfOrNull { it.dueAt }?.let(::local),
        )
    }

    /**
     * First the reviewed cards that are due (most overdue first), then new ones (in turns between documents),
     * up to [limit]; if nothing is left, Learning cards due in the next 20 minutes (like Anki).
     */
    suspend fun dueCards(limit: Int, deckId: Long? = null, rng: PyRandom? = null): List<Pair<FlashcardsEntity, Boolean>> = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val reviewed = reviewedIds()
        val decks = members(deckId)
        val cards = db.flashcardsDao().all().filter { inDecks(it, decks) }
        val byDue = compareBy<FlashcardsEntity>({ it.dueAt }, { it.id })
        var items = mutableListOf<Pair<FlashcardsEntity, Boolean>>()
        if (reviewed.isNotEmpty()) {
            items = cards.filter { it.id in reviewed && it.dueAt <= now }.sortedWith(byDue).take(limit).map { it to false }.toMutableList()
            rng?.shuffle(items)
        }
        val room = limit - items.size
        if (room > 0) {
            val new = cards.filter { it.id !in reviewed }.sortedBy { it.id }.take(NEW_CANDIDATES).toMutableList()
            rng?.shuffle(new)
            items += mixByDocument(new).take(room).map { it to true }
        }
        if (items.isEmpty() && reviewed.isNotEmpty()) {
            items = cards.filter { it.id in reviewed && it.repetitions == 0L && it.dueAt <= now + LEARN_AHEAD.toMillis() }
                .sortedWith(byDue).take(limit).map { it to false }.toMutableList()
        }
        items
    }

    /** New cards in turns between documents (oldest first in each). */
    private fun mixByDocument(cards: List<FlashcardsEntity>): List<FlashcardsEntity> {
        val groups = LinkedHashMap<String, ArrayDeque<FlashcardsEntity>>()
        for (c in cards) groups.getOrPut((c.sourceChunkId ?: "").split(":")[0]) { ArrayDeque() } += c
        val mixed = mutableListOf<FlashcardsEntity>()
        while (groups.values.any { it.isNotEmpty() }) for (q in groups.values) q.removeFirstOrNull()?.let { mixed += it }
        return mixed
    }

    /** A round of study for a deck (null: all), shuffled. */
    suspend fun queue(deckId: Long?, direction: CardDirection, limit: Int = 20): List<StudyCard> =
        dueCards(limit, deckId, PyRandom(clock.now().toEpochMilli())).map { (card, isNew) -> StudyCard(card, isNew, direction) }

    suspend fun history(limit: Int, deckId: Long? = null): List<ReviewItem> = withContext(Dispatchers.IO) {
        val decks = members(deckId)
        val cards = db.flashcardsDao().all().associateBy { it.id }
        db.flashcardReviewsDao().all().filter { r -> cards[r.flashcardId]?.let { inDecks(it, decks) } == true }
            .sortedWith(compareByDescending<FlashcardReviewsEntity> { it.reviewedAt }.thenByDescending { it.id }).take(limit)
            .map { r ->
                val c = cards.getValue(r.flashcardId)
                ReviewItem(c.id, deckOf(c), c.koreanWord, c.translation, r.rating, local(r.reviewedAt), r.newInterval)
            }
    }

    /** Your processed documents (the Documents selector). */
    suspend fun readyDocuments(): List<DocumentsEntity> = withContext(Dispatchers.IO) { db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id } }

    /** The document and page of each card, as the web's to_out (no source for coach cards or bad ids). */
    suspend fun sources(cards: List<FlashcardsEntity>): Map<Long, CardSource> = withContext(Dispatchers.IO) {
        val names = db.documentsDao().all().associate { it.id to it.originalName }
        cards.mapNotNull { c ->
            val parts = c.sourceChunkId?.split(":") ?: return@mapNotNull null
            if (parts.size != 3) return@mapNotNull null
            val doc = parts[0].toLongOrNull() ?: return@mapNotNull null
            val page = parts[1].toIntOrNull() ?: return@mapNotNull null
            names[doc]?.let { c.id to CardSource(it, page) }
        }.toMap()
    }

    /**
     * Port of the web's card_details: words gather the examples and notes of every document; grammar cards take the
     * Grammar screen's point (grammar_points). Read from your documents when a deck opens.
     */
    suspend fun details(cards: List<FlashcardsEntity>): Map<Long, CardDetails> = withContext(Dispatchers.IO) {
        if (cards.isEmpty()) return@withContext emptyMap()
        fun strings(json: String?) = json?.let { Json.parseToJsonElement(it) as? JsonArray }?.map { it.jsonPrimitive.content }.orEmpty()
        val grammar = LinkedHashMap<String, CardDetails>()
        for (gp in db.grammarPointsDao().all().sortedWith(compareBy({ it.documentId }, { it.id }))) {
            grammar.getOrPut(norm(gp.pattern)) {
                val examples = (Json.parseToJsonElement(gp.examples) as? JsonArray)?.map { e -> e.jsonArray.let { it[0].jsonPrimitive.content to it[1].jsonPrimitive.content } }.orEmpty()
                CardDetails(strings(gp.structure), examples, strings(gp.explanation), strings(gp.notes))
            }
        }
        class Acc(val examples: MutableList<Pair<String, String>> = mutableListOf(), val notes: MutableList<String> = mutableListOf())
        val words = HashMap<String, Acc>()
        for (doc in db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }) {
            for (chunk in notes.chunks(doc)) for (pair in extractPairs(chunk)) {
                val entry = words.getOrPut(norm(pair.korean)) { Acc() }
                pair.examples.forEach { if (it !in entry.examples) entry.examples += it }
                pair.notes.forEach { if (it !in entry.notes) entry.notes += it }
            }
        }
        cards.mapNotNull { card ->
            val key = norm(card.koreanWord)
            val d = when {
                card.difficulty == GRAMMAR_DIFFICULTY && key in grammar -> grammar.getValue(key)
                key in words -> words.getValue(key).let { CardDetails(examples = it.examples, notes = it.notes) }
                else -> grammar[key]
            }
            d?.let { card.id to it }
        }.toMap()
    }

    // ---------- writes ----------

    /** Applies the button, saves the review and counts it in a flashcards session. */
    suspend fun review(cardId: Long, rating: Rating, elapsedMs: Long, cfg: Scheduling = Scheduling()): Next = db.withWriteTransaction {
        val now = clock.now().toEpochMilli()
        val card = db.flashcardsDao().all().firstOrNull { it.id == cardId } ?: throw FlashcardException("Flashcard not found.")
        val next = schedule(CardState(card.ease, card.intervalHours.toInt(), card.repetitions.toInt()), rating, cfg)
        db.flashcardReviewsDao().insert(
            FlashcardReviewsEntity(progress.nextId("flashcard_reviews"), card.id, rating.name.lowercase(), now, card.intervalHours, next.intervalHours.toLong()),
        )
        db.flashcardsDao().update(
            card.copy(ease = next.ease, intervalHours = next.intervalHours.toLong(), repetitions = next.repetitions.toLong(), dueAt = now + next.dueInMinutes * 60_000),
        )
        progress.attachSession(now, elapsedMs, card.topicId, mode = "flashcards")
        next
    }

    private suspend fun checkName(name: String): String {
        val clean = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (clean.isEmpty() || clean.codePointCount(0, clean.length) > DECK_NAME_CHARS) throw FlashcardException("Give the deck a name (1–120 characters).")
        return clean
    }

    private suspend fun checkDecks(deckIds: List<Long>) {
        val shown = decksNow().map { it.id }.toSet()
        deckIds.firstOrNull { it !in shown }?.let { throw FlashcardException("That deck doesn't exist: $it.") }
    }

    private suspend fun place(deckId: Long): DeckPlacesEntity =
        db.deckPlacesDao().all().firstOrNull { it.deckId == deckId } ?: DeckPlacesEntity(deckId, null, null, null).also { db.deckPlacesDao().insert(it) }

    /** Another name for a deck (the document keeps its name). */
    suspend fun rename(deckId: Long, name: String) {
        val clean = checkName(name)
        checkDecks(listOf(deckId))
        db.withWriteTransaction {
            when {
                isGroup(deckId) -> db.deckGroupsDao().all().first { it.id == groupOf(deckId) }.let { db.deckGroupsDao().update(it.copy(name = clean)) }
                deckId < 0 -> db.customDecksDao().all().first { it.id == -deckId }.let { db.customDecksDao().update(it.copy(name = clean)) }
                else -> db.deckPlacesDao().update(place(deckId).copy(name = clean))
            }
        }
    }

    /** The order you want to see your decks in (dragging them). */
    suspend fun reorder(deckIds: List<Long>) {
        checkDecks(deckIds)
        db.withWriteTransaction {
            deckIds.distinct().forEachIndexed { position, id -> db.deckPlacesDao().update(place(id).copy(position = position.toLong())) }
        }
    }

    /** Combines decks into one with a new name; the first is the one the other was dropped on. */
    suspend fun merge(deckIds: List<Long>, name: String): Long {
        val clean = checkName(name)
        val ids = deckIds.distinct()
        if (ids.size < 2) throw FlashcardException("Choose at least two decks to combine.")
        checkDecks(ids)
        return db.withWriteTransaction {
            val places = db.deckPlacesDao()
            val position = places.all().firstOrNull { it.deckId == ids[0] }?.position
            var target = ids.firstOrNull { isGroup(it) }
            if (target == null) {
                val group = DeckGroupsEntity(progress.nextId("deck_groups"), clean, clock.now().toEpochMilli())
                db.deckGroupsDao().insert(group)
                target = groupDeckId(group.id)
            }
            val groupId = groupOf(target)
            db.deckGroupsDao().all().first { it.id == groupId }.let { db.deckGroupsDao().update(it.copy(name = clean)) }
            for (id in ids) {
                if (id == target) continue
                if (isGroup(id)) { // a combined deck inside another: their decks come together
                    places.all().filter { it.groupId == groupOf(id) }.forEach { places.update(it.copy(groupId = groupId)) }
                    places.all().filter { it.deckId == id }.forEach { places.delete(it) }
                    db.deckGroupsDao().all().filter { it.id == groupOf(id) }.forEach { db.deckGroupsDao().delete(it) }
                } else {
                    places.update(place(id).copy(groupId = groupId, position = null))
                }
            }
            places.update(place(target).copy(position = position))
            target
        }
    }

    /** Deletes a deck's cards and their history (not the document nor the quiz). Undo with [undoDelete]. */
    suspend fun delete(deckId: Long): DeletedDeck = db.withWriteTransaction {
        val decks = members(deckId)!!
        val cards = db.flashcardsDao().all().filter { inDecks(it, decks) }
        if (cards.isEmpty()) throw FlashcardException("That deck has no cards.")
        val ids = cards.map { it.id }.toSet()
        val reviews = db.flashcardReviewsDao().all().filter { it.flashcardId in ids }
        val customs = db.customDecksDao().all().filter { -it.id in decks }
        val places = if (isGroup(deckId)) db.deckPlacesDao().all().filter { it.deckId == deckId } else emptyList()
        val groups = if (isGroup(deckId)) db.deckGroupsDao().all().filter { it.id == groupOf(deckId) } else emptyList()
        reviews.forEach { db.flashcardReviewsDao().delete(it) }
        cards.forEach { db.flashcardsDao().delete(it) }
        customs.forEach { db.customDecksDao().delete(it) }
        places.forEach { db.deckPlacesDao().delete(it) }
        groups.forEach { db.deckGroupsDao().delete(it) }
        DeletedDeck(cards, reviews, customs, places, groups)
    }

    suspend fun undoDelete(deleted: DeletedDeck) = db.withWriteTransaction {
        deleted.groups.forEach { db.deckGroupsDao().insert(it) }
        deleted.places.forEach { db.deckPlacesDao().insert(it) }
        deleted.customDecks.forEach { db.customDecksDao().insert(it) }
        deleted.cards.forEach { db.flashcardsDao().insert(it) }
        deleted.reviews.forEach { db.flashcardReviewsDao().insert(it) }
    }

    /**
     * Creates the cards still missing for some documents (empty: all). A word that already has a card in that
     * deck, or that was in an earlier document, is skipped; then each word keeps one card with all its meanings.
     */
    suspend fun createCards(documentIds: List<Long>): CreateResult = db.withWriteTransaction {
        val now = clock.now().toEpochMilli()
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
        val docs = if (documentIds.isEmpty()) ready else documentIds.distinct().map { id -> ready.firstOrNull { it.id == id } ?: throw FlashcardException("Document not found.") }
        val existing = db.flashcardsDao().all().filter { it.customDeckId == null }.map { deckKey(it.sourceChunkId) to norm(it.koreanWord) }.toMutableSet()
        val topics = Topics(db, progress)
        val earlier = HashMap<Long, Pair<Set<String>, Set<String>>>()
        var created = 0
        var skipped = 0
        var nextId = progress.nextId("flashcards")
        for (chunk in docs.flatMap(notes::chunks)) {
            val (earlierWords, earlierGrammar) = earlier.getOrPut(chunk.documentId) { earlierWords(chunk.documentId, ready) to earlierGrammar(chunk.documentId, ready) }
            suspend fun add(front: String, back: String, romanization: String?, exampleKo: String?, exampleEn: String?, difficulty: Long) = FlashcardsEntity(
                id = nextId++, vocabularyId = null, front = front, back = back, koreanWord = front.cut(120), translation = back.cut(255),
                romanization = romanization?.takeIf { it.isNotEmpty() }?.cut(120), exampleSentence = exampleKo,
                topicId = topics.idFor(chunk), difficulty = difficulty, ease = 2.5, intervalHours = 0, repetitions = 0, dueAt = now,
                sourceChunkId = chunk.chromaId, exampleTranslation = exampleEn, createdAt = now, customDeckId = null,
            )
            for (pair in extractPairs(chunk)) {
                val key = chunk.documentId to norm(pair.korean)
                if (key in existing || key.second in earlierWords) { skipped++; continue }
                existing += key
                db.flashcardsDao().insert(add(pair.korean, pair.english, pair.romanization, pair.exampleKo, pair.exampleEn, 1))
                created++
            }
            for (point in extractGrammar(chunk)) {
                val key = chunk.documentId to norm(point.pattern)
                if (key in existing || key.second in earlierGrammar) { skipped++; continue }
                existing += key
                val (ko, en) = point.examples.firstOrNull() ?: (null to null)
                db.flashcardsDao().insert(add(point.pattern, point.meaning, null, ko, en, GRAMMAR_DIFFICULTY))
                created++
            }
        }
        mergeRepeatedCards(ready)
        CreateResult(created, skipped, db.flashcardsDao().count())
    }

    /** Words (normalized) of the ready documents uploaded before [documentId]: what a new PDF repeats is not saved again. */
    private fun earlierWords(documentId: Long, ready: List<DocumentsEntity>): Set<String> =
        ready.filter { it.id < documentId }.flatMap(notes::chunks).flatMap(::extractPairs).map { norm(it.korean) }.toSet()

    private suspend fun earlierGrammar(documentId: Long, ready: List<DocumentsEntity>): Set<String> {
        val ids = ready.filter { it.id < documentId }.map { it.id }.toSet()
        return db.grammarPointsDao().all().filter { it.documentId in ids }.map { norm(it.pattern) }.toSet()
    }

    internal class Word(val korean: String, val firstDoc: Long, val meanings: MutableList<String> = mutableListOf(), val examples: MutableList<Pair<String, String>> = mutableListOf())

    /** Each vocabulary word once, with all its meanings and examples from your ready documents, in upload order. */
    internal fun mergedWords(ready: List<DocumentsEntity>): Map<String, Word> {
        val words = LinkedHashMap<String, Word>()
        for (doc in ready) for (pair in notes.chunks(doc).flatMap(::extractPairs)) {
            val korean = pair.korean.trim()
            val english = pair.english.trim()
            if (korean.isEmpty() || english.isEmpty()) continue
            val word = words.getOrPut(norm(korean)) { Word(korean, doc.id) }
            if (norm(english) !in word.meanings.map(::norm)) word.meanings += english
            val example = (pair.exampleKo ?: "").trim() to (pair.exampleEn ?: "").trim()
            if (example.first.isNotEmpty() && example !in word.examples) word.examples += example
        }
        return words
    }

    /** All meanings without repeats: "Seat" and "Seat / Spot" -> "Seat / Spot". */
    internal fun joinedMeaning(word: Word): String {
        var kept = mutableListOf<Pair<String, Set<String>>>()
        for (meaning in word.meanings) {
            val senses = englishSenses(meaning)
            if (kept.any { it.second.containsAll(senses) }) continue
            kept = kept.filterNot { senses.containsAll(it.second) }.toMutableList()
            kept += meaning to senses
        }
        return kept.joinToString(MEANING_SEPARATOR) { it.first }.ifEmpty { word.meanings.joinToString(MEANING_SEPARATOR) }
    }

    /** A word in several documents ends in ONE card with all its meanings; the others' reviews move to it. */
    private suspend fun mergeRepeatedCards(ready: List<DocumentsEntity>) {
        val merged = mergedWords(ready)
        val groups = LinkedHashMap<String, MutableList<FlashcardsEntity>>()
        for (card in db.flashcardsDao().all().filter { it.customDeckId == null && it.difficulty != GRAMMAR_DIFFICULTY }.sortedBy { it.id }) {
            val key = norm(card.koreanWord)
            if (key in merged) groups.getOrPut(key) { mutableListOf() } += card
        }
        for ((key, cards) in groups) {
            val word = merged.getValue(key)
            val sorted = cards.sortedWith(compareBy({ deckKey(it.sourceChunkId) != word.firstDoc }, { it.id }))
            var keep = sorted[0]
            val meaning = joinedMeaning(word).cut(255)
            if (keep.translation != meaning || keep.back != meaning) keep = keep.copy(translation = meaning, back = meaning)
            if (keep.exampleSentence.isNullOrEmpty() && word.examples.isNotEmpty()) {
                keep = keep.copy(exampleSentence = word.examples[0].first, exampleTranslation = word.examples[0].second.ifEmpty { null })
            }
            if (keep != sorted[0]) db.flashcardsDao().update(keep)
            for (other in sorted.drop(1)) {
                db.flashcardReviewsDao().all().filter { it.flashcardId == other.id }.forEach { db.flashcardReviewsDao().update(it.copy(flashcardId = keep.id)) }
                db.flashcardsDao().delete(other)
            }
        }
    }
}
