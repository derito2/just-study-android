package com.dero.juststudykorean.data.teacher

import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.korean.teacher.Teacher
import com.dero.juststudykorean.korean.teacher.TeacherAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The web's tutor.schemas: a question has 2 to 500 characters. */
const val QUESTION_MIN = 2
const val QUESTION_MAX = 500

/** Teacher over your notes (notes_fts), one question at a time, its index rebuilt when a PDF is added or removed. */
@Singleton
class TeacherService @Inject constructor(db: JustStudyDatabase) {
    private val search = NotesSearch(db)
    private val teacher = Teacher(search)
    private val lock = Mutex()
    private var seen = -1L

    suspend fun ask(question: String): TeacherAnswer = lock.withLock {
        withContext(Dispatchers.Default) {
            val version = search.version()
            if (version != seen) { teacher.invalidate(); seen = version }
            teacher.ask(question.take(QUESTION_MAX))
        }
    }
}
