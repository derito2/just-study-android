package com.dero.juststudykorean.data.golden

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.data.progress.TopicProgress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ProgressGoldenTest : GoldenDb() {
    private val repo by lazy { ProgressRepository(db, clock) }

    @Test
    fun summary() = runBlocking {
        val s = repo.summary()
        assertJson("summary", golden("progress_summary"), obj(
            "questions_answered" to s.questionsAnswered, "correct" to s.correct, "accuracy" to s.accuracy, "sessions" to s.sessions,
            "total_study_seconds" to s.totalStudySeconds, "streak_days" to s.streakDays, "last_studied_at" to s.lastStudiedAt,
            "today" to obj(
                "date" to s.today.date, "questions" to s.today.questions, "correct" to s.today.correct, "accuracy" to s.today.accuracy,
                "study_seconds" to s.today.studySeconds, "sessions" to s.today.sessions, "cards" to s.today.cards,
            ),
        ))
    }

    @Test
    fun weekly() = runBlocking {
        val w = repo.weekly()
        assertJson("weekly", golden("weekly"), obj(
            "words_learned" to obj("this_week" to w.wordsThisWeek, "last_week" to w.wordsLastWeek),
            "quiz_accuracy" to obj("this_week" to w.accuracyThisWeek, "last_week" to w.accuracyLastWeek),
            "quiz_answers_this_week" to w.answersThisWeek, "quiz_answers_last_week" to w.answersLastWeek,
        ))
    }

    @Test
    fun calendar() = runBlocking {
        for (want in golden("calendar").jsonArray) {
            val year = want.jsonObject.getValue("year").jsonPrimitive.int
            val month = want.jsonObject.getValue("month").jsonPrimitive.int
            val c = repo.calendarNow(year, month)
            assertJson("calendar $year-$month", want, obj(
                "year" to c.year, "month" to c.month, "timezone" to c.zone,
                "days" to c.days.map { d ->
                    obj("date" to d.date, "studied" to d.studied, "minutes" to d.minutes, "questions" to d.questions, "correct" to d.correct,
                        "accuracy" to d.accuracy, "topics" to d.topics, "status" to d.status)
                },
            ))
        }
    }

    @Test
    fun timeline() = runBlocking {
        val want = golden("timeline").jsonObject
        for (unit in TimelineUnit.entries) {
            val key = unit.name.lowercase()
            val t = repo.timeline(unit)
            assertJson("timeline $key", want.getValue(key), obj(
                "unit" to key, "timezone" to t.zone,
                "points" to t.points.map { p ->
                    obj("start" to p.start, "minutes" to p.minutes, "answers" to p.answers, "accuracy" to p.accuracy,
                        "cards_new" to p.cardsNew, "cards_learning" to p.cardsLearning, "cards_due" to p.cardsDue)
                },
            ))
        }
    }

    private fun TopicProgress.json() = obj(
        *listOfNotNull(
            "topic_id" to topicId, "name" to name, "category" to category, "attempts" to attempts, "correct" to correct,
            "accuracy" to accuracy, "mastery" to mastery, "last_studied_at" to lastStudiedAt, "has_material" to hasMaterial,
            cardsNew?.let { "cards_new" to it }, cardsLearning?.let { "cards_learning" to it }, cardsDue?.let { "cards_due" to it },
        ).toTypedArray(),
    )

    @Test
    fun topicsAndRecommended() = runBlocking {
        val want = golden("topics").jsonObject
        val t = repo.topics(3)
        assertJson("topics", want.getValue("topics"), obj(
            "topics" to t.all.map { it.json() }, "most_difficult" to t.mostDifficult.map { it.json() }, "best" to t.best.map { it.json() },
        ))
        assertJson("recommended", want.getValue("recommended"), js(repo.recommended(3).map {
            obj("topic_id" to it.topicId, "name" to it.name, "priority" to it.priority, "score" to it.score, "mastery" to it.mastery, "reasons" to it.reasons)
        }))
    }
}
