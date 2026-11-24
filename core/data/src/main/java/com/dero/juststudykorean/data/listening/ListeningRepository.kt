package com.dero.juststudykorean.data.listening

import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.ListeningAttemptsEntity
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.listening.LabSentence
import com.dero.juststudykorean.korean.listening.ListenMode
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val HANDS_FREE_CARDS = 50

/**
 * F4 (FASE_11_PLAN PR 7): Listening Lab's sentences (the bank of your notes, the one Games uses), the cards of
 * hands-free (never rated here) and the attempts: only right or wrong, never the audio nor what was said (D9-1, D11-6).
 */
@Singleton
class ListeningRepository @Inject constructor(
    private val db: JustStudyDatabase,
    private val content: GameContentProvider,
    private val cards: FlashcardRepository,
    private val clock: AppClock,
) {
    suspend fun sentences(): List<LabSentence> = content.bank().sentences.map { LabSentence(it.key, it.korean, it.meaning) }

    /** Hands-free: the cards due now (then new ones), as Flashcards would show them; [dueNow]: how many are due. */
    suspend fun cards(): Pair<List<LabSentence>, Int> = withContext(Dispatchers.IO) {
        cards.dueCards(HANDS_FREE_CARDS).map { (c, _) -> LabSentence("w:" + norm(c.koreanWord), c.koreanWord, c.translation) } to cards.statsNow().dueNow
    }

    suspend fun save(mode: ListenMode, key: String, correct: Boolean) = withContext(Dispatchers.IO) {
        db.listeningAttemptsDao().insert(ListeningAttemptsEntity(db.listeningAttemptsDao().maxId() + 1, mode.name.lowercase(), key, correct, clock.now().toEpochMilli()))
    }
}
