package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.korean.KOREAN_ALTERNATIVES
import com.dero.juststudykorean.korean.speech.normalizeSpoken

// FASE_9_PLAN 5 and design/quiz-speaking.md C: the spoken practice of the Quiz (D9-6: not scored, unlimited tries).

/** The form's type (never sent to the generator: it asks for write_ko). */
const val SPEAK = "speak_ko"

/** The form's types as the generator gets them: Say it in Korean asks for Write in Korean (FASE_9_PLAN 5). */
fun requestTypes(types: List<String>): List<String> =
    (types.filter { it != SPEAK } + if (SPEAK in types) listOf("write_ko") else emptyList()).distinct()

/** Can be said: a rules' write_ko whose answer is only Hangul (any " / " alternative) and has 2 blocks or more. */
fun speakable(q: QuestionsEntity): Boolean = q.id > 0 && q.type == "write_ko" &&
    q.correctAnswer.split(KOREAN_ALTERNATIVES).all { alt -> normalizeSpoken(alt).let { it.length >= 2 && it.all { c -> c in '가'..'힣' } } }

/** Which questions are spoken: all the sayable ones if only Say it in Korean is on; alternating (the first spoken) if both. */
fun spokenIds(questions: List<QuestionsEntity>, speakOn: Boolean, writeOn: Boolean): Set<Long> {
    if (!speakOn) return emptySet()
    val sayable = questions.filter(::speakable)
    return (if (writeOn) sayable.filterIndexed { i, _ -> i % 2 == 0 } else sayable).map { it.id }.toSet()
}

/** Read it out loud for a sentence of 2 words or more; otherwise, say the word from its meaning (design B). */
fun readAloud(q: QuestionsEntity) = q.correctAnswer.split(KOREAN_ALTERNATIVES).first().trim().contains(' ')
