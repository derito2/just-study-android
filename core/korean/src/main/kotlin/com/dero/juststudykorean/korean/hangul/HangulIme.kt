package com.dero.juststudykorean.korean.hangul

/**
 * FASE_8_PLAN 3.2: the 두벌식 automaton of the games' own keyboard. Closed blocks plus the block being composed
 * (lead?, vowel parts, tail parts). Input: compatibility jamo, space, delete. [text] is the whole text at any moment.
 */
class HangulIme {
    private val closed = mutableListOf<String>()
    private var lead: Char? = null
    private val vowel = StringBuilder() // one jamo, or the two parts of a vowel joined here (ㅗ + ㅏ)
    private val tail = StringBuilder() // "", one jamo, or the two parts of a compound final

    val text: String get() = closed.joinToString("") + composing()

    private fun vowelChar(): Char? = when (vowel.length) {
        0 -> null
        1 -> vowel[0]
        else -> joinVowels(vowel[0], vowel[1])
    }

    private fun composing(): String {
        val v = vowelChar()
        val l = lead
        return when {
            l != null && v != null -> compose(l, v, tail.toString())?.toString() ?: "$l$v$tail"
            l != null -> l.toString()
            v != null -> v.toString()
            else -> ""
        }
    }

    private fun empty() = lead == null && vowel.isEmpty()

    private fun close() {
        composing().takeIf { it.isNotEmpty() }?.let { closed += it }
        lead = null; vowel.clear(); tail.clear()
    }

    fun input(jamo: Char) = if (jamo in VOWELS) vowel(jamo) else consonant(jamo)

    private fun consonant(c: Char) {
        val v = vowelChar()
        when {
            lead != null && v != null && tail.isEmpty() && canBeTail(c) -> tail.append(c)
            lead != null && v != null && tail.length == 1 && joinTails(tail[0], c) != null -> tail.append(c)
            else -> { close(); lead = c }
        }
    }

    private fun vowel(v: Char) {
        when {
            empty() -> vowel.append(v)
            tail.isNotEmpty() -> { // the last consonant of the final starts the next block: 간 + ㅏ → 가 · 나
                val moved = tail.last()
                tail.setLength(tail.length - 1)
                close()
                lead = moved; vowel.append(v)
            }
            vowel.isEmpty() -> vowel.append(v) // only a lead
            vowel.length == 1 && joinVowels(vowel[0], v) != null -> vowel.append(v)
            else -> { close(); vowel.append(v) }
        }
    }

    fun space() { close(); closed += " " }

    /** In composition: the last jamo (a compound vowel or final back to its first part). Else: the previous block. */
    fun delete() {
        when {
            tail.isNotEmpty() -> tail.setLength(tail.length - 1)
            vowel.isNotEmpty() -> vowel.setLength(vowel.length - 1)
            lead != null -> lead = null
            closed.isNotEmpty() -> closed.removeAt(closed.lastIndex)
        }
    }
}
