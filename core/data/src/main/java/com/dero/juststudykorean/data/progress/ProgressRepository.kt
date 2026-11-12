package com.dero.juststudykorean.data.progress

import androidx.room3.useReaderConnection
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.StudySessionTopicsEntity
import com.dero.juststudykorean.data.db.StudySessionsEntity
import com.dero.juststudykorean.data.db.UserTopicProgressEntity
import com.dero.juststudykorean.data.time.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

// Port of backend/app/services/progress_service.py, progress/engine.py, progress/recommend.py and
// services/study_service.py, checked against golden/db (ProgressGoldenTest). Dates in the database are
// epoch millis in UTC; days are cut in the phone's zone (AppClock.zone).

data class Today(val date: LocalDate, val questions: Int, val correct: Int, val accuracy: Double?, val studySeconds: Long, val sessions: Int, val cards: Int)

data class ProgressSummary(
    val questionsAnswered: Int,
    val correct: Int,
    val accuracy: Double?,
    val sessions: Int,
    val totalStudySeconds: Long,
    val streakDays: Int,
    val lastStudiedAt: ZonedDateTime?,
    val today: Today,
)

/** Last 7 days against the 7 before: words that moved from New to Learning, and quiz accuracy. */
data class Weekly(
    val wordsThisWeek: Int,
    val wordsLastWeek: Int?,
    val accuracyThisWeek: Double?,
    val accuracyLastWeek: Double?,
    val answersThisWeek: Int,
    val answersLastWeek: Int,
)

data class CalendarDay(
    val date: LocalDate,
    val studied: Boolean,
    val minutes: Long,
    val questions: Int,
    val correct: Int,
    val accuracy: Double?,
    val topics: List<String>,
    /** none, green (≥ 80 %), yellow (50–79 %) or red. */
    val status: String,
)

data class CalendarMonth(val year: Int, val month: Int, val zone: ZoneId, val days: List<CalendarDay>)

enum class TimelineUnit(val periods: Int) { Day(14), Week(12), Month(12), Year(5) }

data class TimelinePoint(
    val start: LocalDate,
    val minutes: Long,
    val answers: Int,
    val accuracy: Double?,
    val cardsNew: Int,
    val cardsLearning: Int,
    val cardsDue: Int,
)

data class Timeline(val unit: TimelineUnit, val zone: ZoneId, val points: List<TimelinePoint>)

/** A topic you have answered. Card counts are null when the topic has no cards. */
data class TopicProgress(
    val topicId: Long,
    val name: String,
    val category: String,
    val attempts: Int,
    val correct: Int,
    val accuracy: Double,
    val mastery: Double,
    val lastStudiedAt: ZonedDateTime?,
    val hasMaterial: Boolean,
    val cardsNew: Int?,
    val cardsLearning: Int?,
    val cardsDue: Int?,
)

data class Topics(val all: List<TopicProgress>, val mostDifficult: List<TopicProgress>, val best: List<TopicProgress>)

data class Recommendation(val topicId: Long, val name: String, val priority: String, val score: Double, val mastery: Double?, val reasons: List<String>)

private const val MASTERY_WINDOW = 10
private const val GREEN_FROM = 0.8
private const val YELLOW_FROM = 0.5
private val WEEK = Duration.ofDays(7)
private val AGAIN_DELAY = Duration.ofMinutes(10)
internal val SESSION_IDLE: Duration = Duration.ofMinutes(30)
private const val REC_HIGH_BELOW = 0.5
private const val REC_MEDIUM_BELOW = 0.8
private const val REC_STALE_DAYS = 7
private val LEVELS = listOf("low", "medium", "high")
private val LEVEL_SCORE = mapOf("low" to 100, "medium" to 200, "high" to 300)

/** Python's round(x) for the minutes and percentages: half to even. */
internal fun pyRound(x: Double): Long = Math.rint(x).toLong()

private fun accuracy(correct: Int, total: Int): Double? = if (total > 0) correct.toDouble() / total else null

internal fun dayStatus(questions: Int, correct: Int): String {
    if (questions == 0) return "none"
    val ratio = correct.toDouble() / questions
    return if (ratio >= GREEN_FROM) "green" else if (ratio >= YELLOW_FROM) "yellow" else "red"
}

/** Days in a row with at least one answer, ending today (or yesterday if not yet today). */
internal fun streak(days: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in days) today else today.minusDays(1)
    var count = 0
    while (day in days) { count++; day = day.minusDays(1) }
    return count
}

@Singleton
class ProgressRepository @Inject constructor(private val db: JustStudyDatabase, private val clock: AppClock) {
    private val zone get() = clock.zone
    private fun local(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)
    private fun day(millis: Long): LocalDate = local(millis).toLocalDate()
    private fun startOf(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private class Attempt(val id: Long, val correct: Boolean, val createdAt: Long, val topic: String?)

    private suspend fun attempts(): List<Attempt> {
        val questions = db.questionsDao().all().associateBy { it.id }
        val topics = db.topicsDao().all().associateBy { it.id }
        return db.questionAttemptsDao().all().mapNotNull { a ->
            val q = questions[a.questionId] ?: return@mapNotNull null // the web joins Question
            Attempt(a.id, a.isCorrect, a.createdAt, q.topicId?.let { topics[it]?.name })
        }
    }

    suspend fun summary(): ProgressSummary = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val today = day(now)
        val rows = attempts()
        val sessions = db.studySessionsDao().all()
        val correct = rows.count { it.correct }
        val todayRows = rows.filter { day(it.createdAt) == today }
        val todaySessions = sessions.filter { day(it.startedAt) == today }
        val last = rows.maxOfOrNull { it.createdAt }
        val todayCorrect = todayRows.count { it.correct }
        ProgressSummary(
            questionsAnswered = rows.size,
            correct = correct,
            accuracy = accuracy(correct, rows.size),
            sessions = sessions.size,
            totalStudySeconds = sessions.sumOf { it.durationSeconds },
            streakDays = streak(rows.map { day(it.createdAt) }.toSet(), today),
            lastStudiedAt = last?.let(::local),
            today = Today(
                today, todayRows.size, todayCorrect, accuracy(todayCorrect, todayRows.size),
                todaySessions.sumOf { it.durationSeconds }, todaySessions.size,
                // Reviewing cards is studying too.
                db.flashcardReviewsDao().all().count { day(it.reviewedAt) == today },
            ),
        )
    }

    suspend fun weekly(): Weekly = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val thisStart = now - WEEK.toMillis()
        val lastStart = now - 2 * WEEK.toMillis()
        val firstReview = db.flashcardReviewsDao().all().groupBy { it.flashcardId }.mapValues { (_, r) -> r.minOf { it.reviewedAt } }
        val cards = db.flashcardsDao().all().associateBy { it.id }
        fun words(start: Long, end: Long?) = firstReview.filter { (id, at) -> id in cards && at >= start && (end == null || at < end) }
            .map { cards.getValue(it.key).koreanWord }.distinct().size
        val answers = db.questionAttemptsDao().all()
        fun totals(start: Long, end: Long?) = answers.filter { it.createdAt >= start && (end == null || it.createdAt < end) }.let { a -> a.size to a.count { it.isCorrect } }
        val (totalNow, correctNow) = totals(thisStart, null)
        val (totalBefore, correctBefore) = totals(lastStart, thisStart)
        // No reviews before this week: nothing to compare with (the arrow stays gray).
        val hadCardsBefore = firstReview.values.any { it < thisStart }
        Weekly(
            words(thisStart, null), if (hadCardsBefore) words(lastStart, thisStart) else null,
            accuracy(correctNow, totalNow), accuracy(correctBefore, totalBefore), totalNow, totalBefore,
        )
    }

    fun calendar(year: Int, month: Int): Flow<CalendarMonth> =
        db.invalidationTracker.createFlow("question_attempts", "study_sessions", "questions", "topics").map { calendarNow(year, month) }

    suspend fun calendarNow(year: Int, month: Int): CalendarMonth = withContext(Dispatchers.IO) {
        val first = LocalDate.of(year, month, 1)
        val next = first.plusMonths(1)
        val start = startOf(first)
        val end = startOf(next)
        class Info(var questions: Int = 0, var correct: Int = 0, val topics: MutableSet<String> = sortedSetOf())
        val perDay = HashMap<LocalDate, Info>()
        for (a in attempts().filter { it.createdAt in start until end }) {
            val info = perDay.getOrPut(day(a.createdAt)) { Info() }
            info.questions++
            if (a.correct) info.correct++
            if (!a.topic.isNullOrEmpty()) info.topics += a.topic
        }
        val seconds = HashMap<LocalDate, Long>()
        for (s in db.studySessionsDao().all().filter { it.startedAt in start until end }) seconds.merge(day(s.startedAt), s.durationSeconds, Long::plus)
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it < next }.map { d ->
            val info = perDay[d] ?: Info()
            CalendarDay(
                d, info.questions > 0, pyRound((seconds[d] ?: 0L) / 60.0), info.questions, info.correct,
                accuracy(info.correct, info.questions), info.topics.toList(), dayStatus(info.questions, info.correct),
            )
        }.toList()
        CalendarMonth(year, month, zone, days)
    }

    private fun periodStart(day: LocalDate, unit: TimelineUnit): LocalDate = when (unit) {
        TimelineUnit.Week -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TimelineUnit.Month -> day.withDayOfMonth(1)
        TimelineUnit.Year -> day.withDayOfYear(1)
        TimelineUnit.Day -> day
    }

    private fun previous(start: LocalDate, unit: TimelineUnit): LocalDate = when (unit) {
        TimelineUnit.Day -> start.minusDays(1)
        TimelineUnit.Week -> start.minusDays(7)
        TimelineUnit.Month -> start.minusDays(1).withDayOfMonth(1)
        TimelineUnit.Year -> start.minusYears(1)
    }

    private fun next(start: LocalDate, unit: TimelineUnit): LocalDate = when (unit) {
        TimelineUnit.Day -> start.plusDays(1)
        TimelineUnit.Week -> start.plusDays(7)
        TimelineUnit.Month -> start.plusMonths(1).withDayOfMonth(1)
        TimelineUnit.Year -> start.plusYears(1)
    }

    /** For each moment, how many cards were New, Learning (next review not yet) and Due, rebuilt from the reviews. */
    private suspend fun cardStates(moments: List<Long>): List<Triple<Int, Int, Int>> {
        val cards = db.flashcardsDao().all()
        val reviews = db.flashcardReviewsDao().all().sortedWith(compareBy({ it.reviewedAt }, { it.id })).groupBy { it.flashcardId }
        return moments.map { moment ->
            var new = 0; var learning = 0; var due = 0
            for (card in cards) {
                if (card.createdAt != null && card.createdAt > moment) continue
                val last = reviews[card.id].orEmpty().lastOrNull { it.reviewedAt <= moment }
                if (last == null) { new++; continue }
                val nextReview = last.reviewedAt + if (last.newInterval > 0) Duration.ofHours(last.newInterval).toMillis() else AGAIN_DELAY.toMillis()
                if (nextReview > moment) learning++ else due++
            }
            Triple(new, learning, due)
        }
    }

    suspend fun timeline(unit: TimelineUnit): Timeline = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val starts = ArrayDeque(listOf(periodStart(day(now), unit)))
        repeat(unit.periods - 1) { starts.addFirst(previous(starts.first(), unit)) }
        val first = starts.first()
        // A day of margin: each record's period is decided by its local date.
        val since = first.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() - Duration.ofDays(1).toMillis()
        class P(var seconds: Long = 0, var answers: Int = 0, var correct: Int = 0)
        val points = starts.associateWith { P() }
        fun bucket(millis: Long): P? = day(millis).takeIf { it >= first }?.let { points[periodStart(it, unit)] }
        for (a in attempts().filter { it.createdAt >= since }) bucket(a.createdAt)?.let { it.answers++; if (a.correct) it.correct++ }
        for (s in db.studySessionsDao().all().filter { it.startedAt >= since }) bucket(s.startedAt)?.let { it.seconds += s.durationSeconds }
        val ends = starts.map { startOf(next(it, unit)) }.toMutableList()
        ends[ends.lastIndex] = now
        val states = cardStates(ends)
        Timeline(unit, zone, starts.mapIndexed { i, s ->
            val p = points.getValue(s)
            TimelinePoint(s, pyRound(p.seconds / 60.0), p.answers, accuracy(p.correct, p.answers), states[i].first, states[i].second, states[i].third)
        })
    }

    /** Per topic: new cards, Learning (seen, waiting) and Due; only cards of your documents. */
    private suspend fun cardsByTopic(now: Long): Map<Long, Triple<Int, Int, Int>> {
        val reviewed = db.flashcardReviewsDao().all().map { it.flashcardId }.toSet()
        val out = LinkedHashMap<Long, IntArray>()
        for (c in db.flashcardsDao().all().sortedBy { it.id }) {
            if (c.topicId == null || c.customDeckId != null) continue
            val counts = out.getOrPut(c.topicId) { IntArray(3) }
            when {
                c.id !in reviewed -> counts[0]++
                c.dueAt <= now -> counts[2]++
                else -> counts[1]++
            }
        }
        return out.mapValues { (_, v) -> Triple(v[0], v[1], v[2]) }
    }

    private suspend fun topicsWithMaterial(): Set<Long> {
        val ready = db.documentsDao().all().filter { it.status == "ready" }.map { it.id }.toSet()
        return db.documentTopicsDao().all().filter { it.documentId in ready }.map { it.topicId }.toSet()
    }

    suspend fun topics(limit: Int = 3): Topics = withContext(Dispatchers.IO) {
        val names = db.topicsDao().all().associateBy { it.id }
        val ready = topicsWithMaterial()
        val cards = cardsByTopic(clock.now().toEpochMilli())
        val items = db.userTopicProgressDao().all().filter { it.attempts > 0 && it.topicId in names }.map { p ->
            val topic = names.getValue(p.topicId)
            val c = cards[p.topicId]
            TopicProgress(
                p.topicId, topic.name, topic.category, p.attempts.toInt(), p.correct.toInt(), p.accuracy, p.mastery,
                p.lastStudiedAt?.let(::local), p.topicId in ready, c?.first, c?.second, c?.third,
            )
        }.sortedWith(compareBy({ it.mastery }, { it.name.lowercase() }))
        val usable = items.filter { it.hasMaterial }
        Topics(items, usable.take(limit), usable.sortedWith(compareBy({ -it.mastery }, { it.name.lowercase() })).take(limit))
    }

    /** "Fix this next": a priority per topic (high / medium / low) and the reasons, deterministic. */
    suspend fun recommended(limit: Int = 3): List<Recommendation> = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilli()
        val progress = db.userTopicProgressDao().all().associateBy { it.topicId }
        val reviewed = db.flashcardReviewsDao().all().map { it.flashcardId }.toSet()
        val due = db.flashcardsDao().all().filter { it.id in reviewed && it.dueAt <= now && it.topicId != null }.groupingBy { it.topicId!! }.eachCount()
        val material = topicsWithMaterial()
        val out = mutableListOf<Recommendation>()
        for (topic in db.topicsDao().all().sortedBy { it.id }) {
            val p = progress[topic.id]
            val attempts = p?.attempts?.toInt() ?: 0
            val mastery = p?.mastery ?: 0.0
            val dueCards = due[topic.id] ?: 0
            val days = p?.lastStudiedAt?.let { Math.floorDiv(now - it, Duration.ofDays(1).toMillis()) }
            if (topic.id !in material && dueCards == 0) continue // nothing to practise it with
            val reasons = mutableListOf<String>()
            var level: String
            val gap: Double
            if (attempts == 0) {
                level = "medium"; reasons += "new topic: not practised yet"; gap = 0.5
            } else {
                level = if (mastery < REC_HIGH_BELOW) "high" else if (mastery < REC_MEDIUM_BELOW) "medium" else "low"
                reasons += "mastery ${pyRound(mastery * 100)} % ($attempts answer${if (attempts == 1) "" else "s"})"
                if (days != null && days > REC_STALE_DAYS) {
                    level = LEVELS[minOf(LEVELS.indexOf(level) + 1, LEVELS.lastIndex)]
                    reasons += "not reviewed in $days days"
                }
                gap = 1 - mastery
            }
            if (dueCards > 0) reasons += "$dueCards flashcard${if (dueCards == 1) "" else "s"} due"
            val score = LEVEL_SCORE.getValue(level) + gap * 50 + minOf(days ?: 0, 60) + 5 * minOf(dueCards, 10)
            out += Recommendation(topic.id, topic.name, level, BigDecimal(score).setScale(1, RoundingMode.HALF_EVEN).toDouble(), if (attempts == 0) null else mastery, reasons)
        }
        out.sortedWith(compareBy({ -it.score }, { it.name.lowercase() })).take(limit)
    }

    // ---- writes used by answers and reviews (inside their transaction)

    /** The automatic session: goes on with the last one of the same [mode] if you were active recently. */
    internal suspend fun attachSession(now: Long, responseMs: Long, topicId: Long?, mode: String): StudySessionsEntity {
        val dao = db.studySessionsDao()
        val last = db.hotDao().lastSession(mode)
        fun lastActivity(s: StudySessionsEntity) = s.startedAt + s.durationSeconds * 1000
        var session = if (last != null && last.endedAt == null && now - lastActivity(last) <= SESSION_IDLE.toMillis()) last else {
            if (last != null && last.endedAt == null) dao.update(last.copy(endedAt = lastActivity(last))) // closed for inactivity
            // The session starts when you started thinking the first answer (at most the idle time).
            val thinking = minOf(responseMs, SESSION_IDLE.toMillis())
            StudySessionsEntity(nextId("study_sessions"), now - thinking, null, 0, mode).also { dao.insert(it) }
        }
        session = session.copy(durationSeconds = maxOf(session.durationSeconds, Math.floorDiv(now - session.startedAt, 1000L)))
        dao.update(session)
        if (topicId != null && !db.hotDao().sessionHasTopic(session.id, topicId)) {
            db.studySessionTopicsDao().insert(StudySessionTopicsEntity(session.id, topicId))
        }
        return session
    }

    /** user_topic_progress is a cache: recomputed after every answer. */
    internal suspend fun recomputeTopic(topicId: Long) {
        val totals = db.hotDao().topicTotals(topicId)
        if (totals.attempts == 0L) return
        val latest = db.hotDao().latestAttemptsOfTopic(topicId, MASTERY_WINDOW) // the totals are counted by SQLite
        val row = UserTopicProgressEntity(
            topicId, totals.attempts, totals.correct, totals.correct.toDouble() / totals.attempts,
            latest.count { it.isCorrect }.toDouble() / MASTERY_WINDOW, latest[0].createdAt, clock.now().toEpochMilli(),
        )
        val dao = db.userTopicProgressDao()
        if (dao.all().any { it.topicId == topicId }) dao.update(row) else dao.insert(row)
    }

    internal suspend fun nextId(table: String): Long = db.useReaderConnection { c ->
        c.usePrepared("SELECT COALESCE(MAX(id), 0) + 1 FROM \"$table\"") { it.step(); it.getLong(0) }
    }
}
