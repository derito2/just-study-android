package com.dero.juststudykorean.data.coach

import androidx.room3.useReaderConnection
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.TopicsEntity
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.RULE_TYPES
import com.dero.juststudykorean.data.teacher.NotesSearch
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.casefold
import com.dero.juststudykorean.korean.coach.CoachAnswer
import com.dero.juststudykorean.korean.coach.DEFAULT_TYPES
import com.dero.juststudykorean.korean.coach.DeckProposal
import com.dero.juststudykorean.korean.coach.FORCED
import com.dero.juststudykorean.korean.coach.GRAMMAR_QUIZ
import com.dero.juststudykorean.korean.coach.GRAMMAR_QUIZ_TYPES
import com.dero.juststudykorean.korean.coach.Intent
import com.dero.juststudykorean.korean.coach.PlanTopic
import com.dero.juststudykorean.korean.coach.Practice
import com.dero.juststudykorean.korean.coach.QuizProposal
import com.dero.juststudykorean.korean.coach.RECENT_WINDOWS
import com.dero.juststudykorean.korean.coach.Step
import com.dero.juststudykorean.korean.coach.TIME_BUDGET
import com.dero.juststudykorean.korean.coach.WORD_TYPES
import com.dero.juststudykorean.korean.coach.Word
import com.dero.juststudykorean.korean.coach.byLetter
import com.dero.juststudykorean.korean.coach.byTheme
import com.dero.juststudykorean.korean.coach.byTopic
import com.dero.juststudykorean.korean.coach.byWordType
import com.dero.juststudykorean.korean.coach.fallbackPlan
import com.dero.juststudykorean.korean.coach.forcedReply
import com.dero.juststudykorean.korean.coach.grammarTopic
import com.dero.juststudykorean.korean.coach.intent
import com.dero.juststudykorean.korean.coach.isLetter
import com.dero.juststudykorean.korean.coach.noteWords
import com.dero.juststudykorean.korean.coach.notesAbout
import com.dero.juststudykorean.korean.coach.plannedTools
import com.dero.juststudykorean.korean.coach.proposal
import com.dero.juststudykorean.korean.coach.resolveTheme
import com.dero.juststudykorean.korean.coach.uniqueWords
import com.dero.juststudykorean.korean.coach.wordMeaning
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.pySplit
import com.dero.juststudykorean.korean.teacher.askedWord
import com.dero.juststudykorean.korean.teacher.asksAboutIt
import com.dero.juststudykorean.korean.teacher.asksExample
import javax.inject.Inject
import javax.inject.Singleton

// Port of backend/app/agent/loop.py (StudyAgent.run_stream) and agent/tools.py without the model: every route the web
// answers with code (your entry, a grammar quiz, a topic quiz or its notes, made-to-measure material, due cards), and
// for the rest the planned tools and the deterministic plan (_fallback), as the web does when the model is down.
// The model writing the reply with the tools' data is phase 6.

private val HANGUL_WORD = Regex("[가-힣]+")
private val DASHES = Regex("[‐-―−]")
private val EMOJI = Regex("[\\x{1f300}-\\x{1faff}\\u2600-\\u27bf\\ufe0f]")
private val SPACES = Regex("[ \\t]{2,}")
private const val MISTAKE_DAYS = 30L
private const val DAY_MS = 86_400_000L
private const val CALLS_PER_TURN = 2

@Singleton
class CoachEngine @Inject constructor(
    private val db: JustStudyDatabase,
    private val clock: AppClock,
    private val progress: ProgressRepository,
    private val flashcards: FlashcardRepository,
    private val notes: NotesSource,
) {
    private val search by lazy { NotesSearch(db) }

    private suspend fun chunks(): List<Chunk> =
        db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }.flatMap(notes::chunks)

    // ---------------------------------------------------------------- data (study_service, quiz_service)

    private fun plain(name: String) = casefold(name.replace(DASHES, "-").pySplit().joinToString(" "))

    /** A topic by name, case-insensitive; else the only topic whose name has those whole words; several: none. */
    private suspend fun findTopic(name: String) = findTopicIn(db.topicsDao().all(), name)

    private fun findTopicIn(topics: List<TopicsEntity>, name: String): TopicsEntity? {
        val query = plain(name)
        val exact = topics.firstOrNull { plain(it.name) == query }
        if (exact != null || query.length < 4) return exact
        val base = if (query.endsWith("s")) query.dropLast(1) else query
        val pattern = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(base)}s?(?![\\p{L}\\p{N}_])") // Python's Unicode \\w
        return topics.filter { pattern.containsMatchIn(plain(it.name)) }.singleOrNull()
    }

    private suspend fun topicHasMaterial(topicId: Long): Boolean {
        val ready = db.documentsDao().all().filter { it.status == "ready" }.map { it.id }.toSet()
        return db.documentTopicsDao().all().any { it.topicId == topicId && it.documentId in ready }
    }

    /** Your words you last reviewed in flashcards, newest first (today, this week or all). */
    private suspend fun recentWords(words: List<Word>, window: String?): List<Word> {
        val since = RECENT_WINDOWS[window]?.let { clock.now().toEpochMilli() - it * DAY_MS }
        val index = words.associateBy { norm(it.korean) }
        val rows = db.useReaderConnection { c ->
            c.usePrepared(
                "SELECT f.korean_word, MAX(r.reviewed_at) AS at FROM flashcards f JOIN flashcard_reviews r ON r.flashcard_id = f.id " +
                    "GROUP BY f.korean_word" + (if (since != null) " HAVING MAX(r.reviewed_at) >= $since" else "") + " ORDER BY at DESC",
            ) { st -> buildList { while (st.step()) add(st.getText(0)) } }
        }
        return uniqueWords(rows.mapNotNull { index[norm(it)] })
    }

    /** What you get wrong: words of quiz questions whose last answer was wrong, and cards whose last review (30 days) was Again or Hard. */
    private suspend fun fromMistakes(words: List<Word>): List<Word> {
        val index = words.associateBy { norm(it.korean) }
        val found = mutableListOf<Word>()
        val last = db.questionAttemptsDao().all().groupBy { it.questionId }.mapValues { (_, a) -> a.maxBy { it.id } }
        for (q in db.questionsDao().all().filter { last[it.id]?.isCorrect == false }) {
            for (token in HANGUL_WORD.findAll("${q.prompt} ${q.correctAnswer}")) index[norm(token.value)]?.let { found += it }
        }
        val since = clock.now().toEpochMilli() - MISTAKE_DAYS * DAY_MS
        val cards = db.flashcardsDao().all().associateBy { it.id }
        val lastReviews = db.flashcardReviewsDao().all().groupBy { it.flashcardId }.mapValues { (_, r) -> r.maxBy { it.id } }.values
            .filter { it.flashcardId in cards && it.rating in setOf("again", "hard") && it.reviewedAt >= since }
            .sortedByDescending { it.reviewedAt }
        for (review in lastReviews) {
            val card = cards.getValue(review.flashcardId)
            found += index[norm(card.koreanWord)] ?: Word(card.koreanWord, card.translation, "", card.sourceChunkId ?: "")
        }
        return uniqueWords(found)
    }

    // ---------------------------------------------------------------- tools (agent/tools.py)

    private class Run(val output: Map<String, Any?>, val ok: Boolean = "error" !in output)

    private suspend fun weakTopics(limit: Int = 3): Run = Run(
        mapOf("topics" to progress.recommended(limit).map { mapOf("topic" to it.name, "priority" to it.priority, "reasons" to it.reasons) }),
    )

    private suspend fun recentProgress(): Run {
        val s = progress.summary()
        return Run(mapOf("questions_answered" to s.questionsAnswered, "streak_days" to s.streakDays))
    }

    private fun searchNotes(query: String): Run {
        val result = search.retrieve(query, 3)
        if (!result.enoughContext) return Run(mapOf("found" to false, "passages" to emptyList<Any>()))
        return Run(mapOf("found" to true, "passages" to result.hits.filter { it.distance <= search.maxDistance || it.lexical }.map { it.id }))
    }

    private suspend fun dueFlashcards(limit: Int = 5): Run {
        val stats = flashcards.statsNow()
        val cards = flashcards.dueCards(limit)
        return Run(mapOf("due_now" to stats.dueNow, "new_cards_never_studied" to stats.new, "cards" to cards.map { (c, new) -> c.front to new }))
    }

    private suspend fun proposeQuiz(topic: String, count: Int = 5): Run {
        val found = findTopic(topic) ?: return Run(mapOf("error" to "Unknown topic '$topic'. Use get_weak_topics to see real topics."))
        return Run(mapOf("topic" to found.name, "count" to count, "types" to DEFAULT_TYPES))
    }

    private suspend fun makePractice(kind: String, source: String, valueIn: String?, size: Int = 10): Practice {
        val words = noteWords(chunks())
        if (words.isEmpty()) return Practice(kind, "", emptyList(), "There are no words in the student's notes yet.")
        var value = (valueIn ?: "").trim()
        val chosen: List<Word>
        val title: String
        when (source) {
            "mistakes" -> { chosen = fromMistakes(words); title = "Your mistakes" }
            "letter" -> {
                if (!isLetter(value)) return Practice(kind, "", emptyList(), "For a letter, give one Korean consonant such as ㄱ, ㄴ or ㅎ.")
                chosen = byLetter(words, value); title = "Words starting with $value"
            }
            "recent" -> {
                val window = casefold(value).takeIf { it in RECENT_WINDOWS }
                chosen = recentWords(words, window)
                title = mapOf("today" to "Words you studied today", "week" to "Words you studied this week")[window ?: ""] ?: "Your latest words"
            }
            "word_type" -> {
                val kindOfWord = casefold(value).trimEnd('s')
                if (kindOfWord !in WORD_TYPES) return Practice(kind, "", emptyList(), "For word_type, say adjective, verb or noun.")
                chosen = byWordType(words, kindOfWord)
                title = mapOf("adjective" to "Adjectives", "verb" to "Verbs", "noun" to "Nouns").getValue(kindOfWord)
            }
            "topic" -> {
                val topic = (if (value.isNotEmpty()) findTopic(value) else null)
                    ?: return Practice(kind, "", emptyList(), "Unknown topic '$value'. Use get_weak_topics to see real topics.")
                chosen = byTopic(words, topic.name); title = topic.name
            }
            else -> {
                if (value.isEmpty()) return Practice(kind, "", emptyList(), "Say what the theme is, for example sports or food.")
                value = resolveTheme(value, words)
                chosen = byTheme(words, value)
                title = "${value.take(1).uppercase()}${value.drop(1)} words"
            }
        }
        if (chosen.isEmpty()) {
            return Practice(kind, title, emptyList(), "The student's notes have no words for that ($source: ${value.ifEmpty { "-" }}). " +
                    "Say so and suggest another theme or a quiz on their weak topics.", theme = value)
        }
        return Practice(kind, title, chosen.take(size))
    }

    /** What the message asks for (practice.intent), with your real topics. */
    internal suspend fun intentOf(message: String): Intent? = db.topicsDao().all().let { topics -> intent(message) { findTopicIn(topics, it) != null } }

    private fun summary(tool: String, run: Run): String {
        val o = run.output
        if ("error" in o) return o.getValue("error").toString().take(160)
        return when (tool) {
            "get_weak_topics" -> "${(o.getValue("topics") as List<*>).size} topics"
            "get_recent_progress" -> "${o["questions_answered"]} answers, streak ${o["streak_days"]} days"
            "search_knowledge_base" -> if (o["found"] == true) "found ${(o.getValue("passages") as List<*>).size} passages" else "not in notes"
            "get_due_flashcards" -> "${o["due_now"]} due, ${o["new_cards_never_studied"]} new"
            "propose_quiz" -> "${o["count"]} questions on ${o["topic"]}"
            else -> "done"
        }
    }

    // ---------------------------------------------------------------- the coach (loop.py)

    private class Turn {
        val steps = mutableListOf<Step>()
        var quiz: QuizProposal? = null
        var deck: DeckProposal? = null
        fun answer(text: String, fallback: Boolean = false) = CoachAnswer(text, steps.toList(), quiz, deck, fallback)
    }

    private fun take(practice: Practice, turn: Turn) {
        val words = practice.words.map { it.korean }
        if (practice.kind == "deck") turn.deck = DeckProposal(practice.title, words)
        else turn.quiz = QuizProposal(null, practice.title, words, minOf(10, words.size), RULE_TYPES.sorted())
    }

    /** [history]: your earlier messages in this conversation, oldest first ("an example with it" = the last word you asked about). */
    suspend fun ask(message: String, history: List<String> = emptyList()): CoachAnswer {
        val turn = Turn()
        var forced: Map<String, Any?>? = FORCED[casefold(message).pySplit().joinToString(" ").trimEnd('.', '!', '?')]
        var word = if (forced == null) askedWord(message) else null
        if (word == null && forced == null && asksAboutIt(message)) {
            word = history.asReversed().firstNotNullOfOrNull { askedWord(it) }
        }
        if (word != null) {
            turn.steps += Step("search_knowledge_base", mapOf("query" to word), true, "your entry")
            return turn.answer(wordMeaning(chunks(), word, asksExample(message)))
        }
        if (forced == null) {
            GRAMMAR_QUIZ.find(message.pySplit().joinToString(" "))?.let { return grammarQuiz(it.groupValues[1].trim(), turn) }
            if (TIME_BUDGET.containsMatchIn(message)) return turn.answer(fallback(message, turn))
            notesAbout(message)?.let { findTopic(it) }?.let { return topicNotes(it, turn) }
            val asked = intentOf(message)
            if (asked?.source == "topic") {
                val topic = findTopic(asked.value!!)!!
                if (asked.kind == "quiz") return topicQuiz(topic, minOf(5, asked.count ?: 5), turn)
                forced = linkedMapOf("kind" to "deck", "source" to "topic", "value" to topic.name)
            } else if (asked != null) {
                forced = linkedMapOf("kind" to asked.kind, "source" to asked.source, "value" to asked.value, "count" to asked.count)
            }
        }
        if (forced != null) return forced(forced, turn)
        val planned = plannedTools(message)
        if (planned == listOf("get_due_flashcards" to emptyMap<String, Any?>())) return due(turn)
        // The web's model gets these tools' data and writes the reply; without it (phase 5), the deterministic plan.
        for ((name, args) in planned.take(CALLS_PER_TURN)) {
            val run = when (name) {
                "search_knowledge_base" -> searchNotes(args.getValue("query") as String)
                "get_weak_topics" -> weakTopics()
                "get_due_flashcards" -> dueFlashcards()
                else -> recentProgress()
            }
            turn.steps += Step(name, args, run.ok, summary(name, run))
        }
        return turn.answer(fallback(message, turn).replace(EMOJI, "").replace(SPACES, " ").trim(), fallback = true)
    }

    private suspend fun fallback(message: String, turn: Turn): String {
        val recs = progress.recommended(3)
        if (recs.isNotEmpty() && turn.quiz == null) turn.quiz = proposal(recs[0].name)
        val minutes = Regex("(\\d+)\\s*(?:minutes?|mins?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(message)
        val stats = if (minutes) flashcards.statsNow() else null
        return fallbackPlan(recs.map { PlanTopic(it.name, it.priority, it.reasons) }, message, stats?.dueNow ?: 0, stats?.new ?: 0)
    }

    private suspend fun forced(arguments: Map<String, Any?>, turn: Turn): CoachAnswer {
        val args = LinkedHashMap(arguments)
        val count = args.remove("count") as Int? // "3 questions": not an argument of the tool
        var output = makePractice(args["kind"] as String, args["source"] as String, args["value"] as String?)
        val summary = output.error?.take(160) ?: "${output.kind} with ${output.words.size} words: ${output.title}"
        turn.steps += Step("make_practice", args, output.ok, summary)
        if (output.ok && count != null && output.kind == "quiz") output = output.copy(words = output.words.take(count))
        val reply = forcedReply(args, output)
        if (output.ok) take(output, turn)
        return turn.answer(reply)
    }

    private suspend fun topicQuiz(topic: TopicsEntity, count: Int, turn: Turn): CoachAnswer {
        if (!topicHasMaterial(topic.id)) {
            return turn.answer("${topic.name} isn't in your notes anymore: its document is no longer in your Library. Upload it again to practise it.")
        }
        val run = proposeQuiz(topic.name, count)
        turn.steps += Step("propose_quiz", mapOf("topic" to topic.name, "count" to count), run.ok, summary("propose_quiz", run))
        if (!run.ok) return turn.answer("I couldn't prepare a quiz on ${topic.name}. Try another topic.")
        val name = run.output.getValue("topic") as String
        turn.quiz = proposal(name).copy(count = count)
        val noun = if (count == 1) "question" else "questions"
        return turn.answer("Here is a quiz on $name: up to $count $noun from your notes (as many as that topic has material for). Press Start quiz.")
    }

    private suspend fun due(turn: Turn): CoachAnswer {
        val run = dueFlashcards()
        turn.steps += Step("get_due_flashcards", emptyMap(), true, summary("get_due_flashcards", run))
        val due = run.output["due_now"] as Int
        val new = run.output["new_cards_never_studied"] as Int
        val cards = if (due == 1) "card" else "cards"
        val news = if (new == 1) "card" else "cards"
        @Suppress("UNCHECKED_CAST")
        val sample = (run.output.getValue("cards") as List<Pair<String, Boolean>>).filter { !it.second }.joinToString(", ") { it.first }.take(120)
        val text = when {
            due > 0 -> "You have $due $cards due right now" + (if (sample.isNotEmpty()) " ($sample)" else "") +
                ", and $new new $news you haven't studied yet. Open Flashcards and clear the due ones first."
            new > 0 -> "Nothing is due right now. You have $new new $news you haven't studied yet: open Flashcards and start a deck."
            else -> "Nothing is due right now and you have no new cards. Create cards in Flashcards."
        }
        return turn.answer(text)
    }

    private suspend fun grammarQuiz(pattern: String, turn: Turn): CoachAnswer {
        val found = grammarTopic(chunks(), pattern)
        turn.steps += Step("search_knowledge_base", mapOf("query" to pattern), found != null, "grammar point")
        val topic = found?.let { findTopic(it.first) }
            ?: return turn.answer(
                "$pattern isn't one of the grammar points in your notes (it may only appear inside an example). Ask Teacher about it, or add it to a PDF in the Library.",
            )
        turn.quiz = proposal(topic.name).copy(title = found.second, count = 1, types = GRAMMAR_QUIZ_TYPES)
        return turn.answer("Here is 1 question on ${found.second} from your notes (a quiz asks each grammar point once). Press Start quiz.")
    }

    private suspend fun topicNotes(topic: TopicsEntity, turn: Turn): CoachAnswer {
        if (!topicHasMaterial(topic.id)) return turn.answer("${topic.name} isn't in your notes anymore: its document is no longer in your Library.")
        val words = byTopic(noteWords(chunks()), topic.name)
        turn.steps += Step("search_knowledge_base", emptyMap(), true, "${words.size} words in ${topic.name}")
        turn.quiz = proposal(topic.name)
        if (words.isEmpty()) return turn.answer("Your notes cover ${topic.name}. Press Start quiz to practise it, or ask Teacher to explain it.")
        val sample = words.take(6).joinToString(", ") { "${it.korean} (${it.english})" }
        val more = if (words.size > 6) "…" else ""
        val noun = if (words.size == 1) "word" else "words"
        return turn.answer("Your notes on ${topic.name} have ${words.size} $noun, for example: $sample$more. Press Start quiz to practise them.")
    }
}
