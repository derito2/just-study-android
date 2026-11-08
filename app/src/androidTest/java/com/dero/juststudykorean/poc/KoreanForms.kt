package com.dero.juststudykorean.poc

/**
 * Ports of backend/app/rag/entries.py (korean_cores, has_form) and rag/conjugation.py (aeo_forms, past),
 * checked against golden/korean.json in NanoTest.koreanFormsMatchGolden.
 */
object KoreanForms {
    private const val BASE = 0xAC00
    private const val A = 0; private const val AE = 1; private const val YA = 2; private const val YAE = 3
    private const val EO = 4; private const val E = 5; private const val YEO = 6; private const val V_O = 8
    private const val WA = 9; private const val WAE = 10; private const val OE = 11; private const val U = 13
    private const val WEO = 14; private const val EU = 18; private const val V_I = 20
    private const val NO_TAIL = 0; private const val T_D = 7; private const val T_L = 8; private const val T_B = 17
    private const val T_S = 19; private const val T_SS = 20; private const val T_H = 27; private const val L_R = 5
    private const val HIEUT = 18

    private val hangul = Regex("[가-힣]")
    private val hangulWords = Regex("[가-힣]+")

    private fun split(ch: Char): Triple<Int, Int, Int>? {
        val code = ch.code - BASE
        if (code !in 0 until 11172) return null
        return Triple(code / 588, (code % 588) / 28, code % 28)
    }

    private fun join(lead: Int, vowel: Int, tail: Int = 0) = ((BASE + (lead * 21 + vowel) * 28 + tail)).toChar().toString()

    private fun bright(ch: Char) = split(ch)?.second in setOf(A, V_O, YA)

    fun aeoForms(verbIn: String): Set<String> {
        val verb = verbIn.trim()
        if (verb.length < 2 || !verb.endsWith("다")) return emptySet()
        val stem = verb.dropLast(1)
        if (stem.any { split(it) == null }) return emptySet()
        val last = stem.last()
        val (lead, vowel, tail) = split(last)!!
        val head = stem.dropLast(1)
        val ending = if (bright(last)) "아" else "어"
        val out = mutableSetOf<String>()
        if (last == '하') return setOf(head + "해")
        if (tail == NO_TAIL) {
            when {
                vowel == EU -> {
                    val new = if (head.isNotEmpty() && bright(head.last())) A else EO
                    out += head + join(lead, new)
                    if (lead == L_R && head.isNotEmpty()) {
                        val (pl, pv, pt) = split(head.last())!!
                        if (pt == NO_TAIL) out += head.dropLast(1) + join(pl, pv, T_L) + join(L_R, new)
                    }
                }
                vowel == V_O -> out += listOf(head + join(lead, WA), stem + "아")
                vowel == U -> out += listOf(head + join(lead, WEO), stem + "어")
                vowel == V_I -> out += listOf(head + join(lead, YEO), stem + "어")
                vowel == OE -> out += listOf(head + join(lead, WAE), stem + "어")
                vowel in setOf(A, EO, AE, E, YEO) -> { out += stem; if (vowel == AE || vowel == E) out += stem + "어" }
                else -> out += stem + ending
            }
            return out
        }
        out += stem + ending
        val plain = head + join(lead, vowel)
        when {
            tail == T_B -> out += plain + if (vowel == V_O && head.isEmpty()) "와" else "워"
            tail == T_D -> out += head + join(lead, vowel, T_L) + ending
            tail == T_S -> out += plain + ending
            tail == T_H && vowel in setOf(A, EO, YA) -> out += head + join(lead, if (vowel == YA) YAE else AE)
        }
        return out
    }

    fun past(form: String): String? {
        val parts = form.lastOrNull()?.let { split(it) } ?: return null
        if (parts.third != NO_TAIL) return null
        return form.dropLast(1) + join(parts.first, parts.second, T_SS)
    }

    private val coreSplit = Regex("""\s+(?:and|y|,)\s+|,|/""")
    private val grammarStart = Regex("""^[\s~-]*(?:\(으\)|\(이\)|[ㄱ-ㅎ]|-)""")
    private val coreLead = Regex("""^[\s~-]*(?:\(으\)|\(이\))?\s*[ㄱ-ㅎ]?\s*""")
    private val parens = Regex("""\([^)]*\)""")

    fun koreanCores(target: String): List<Pair<String, Boolean>> = target.split(coreSplit).mapNotNull { raw ->
        val grammar = grammarStart.containsMatchIn(raw)
        var part = raw.replaceFirst(coreLead, "").trim(' ', '-')
        part = part.replace(parens, "").trim()
        if (part.length > 2) part = part.removeSuffix("다").trim()
        if (hangul.containsMatchIn(part)) part to grammar else null
    }

    private fun contracts(ch: Char, vowel: String, past: Boolean, rest: String = "서"): Boolean {
        val (lead, v, tail) = split(ch) ?: return false
        if (tail != (if (past) T_SS else 0)) return false
        if (lead == HIEUT && v == AE) return true
        val plain = past || rest.take(1) in setOf("서", "야")
        if (vowel == "아") return v == WA || (v == A && plain)
        return v in setOf(YEO, WEO, WAE) || (v in setOf(EO, AE) && plain)
    }

    private val conjunctions = "그래서 그래도 그러니까 그래야 그러면 그러나 그런데 그러고 그래".split(" ").toSet()

    fun hasForm(text: String, form: String, aeo: Set<String> = emptySet()): Boolean {
        val flat = text.replace(" ", "")
        if (form in flat) return true
        if (form.endsWith("다") && form.length >= 3) {
            val bases = (setOf(form.dropLast(1)) + aeoForms(form)).let { b -> b + b.mapNotNull { past(it) } }
            if (bases.any { it.length >= 2 && it in flat }) return true
        }
        val first = form.take(1); val rest = form.drop(1)
        val tail = mapOf("ㄴ" to 4, "ㄹ" to 8, "ㅁ" to 16, "ㅂ" to 17)[first]
        if (tail != null && rest.isNotEmpty()) {
            for (word in hangulWords.findAll(text).map { it.value }) {
                for (i in 0 until word.length - rest.length) {
                    if ((word[i].code - BASE) % 28 == tail && word.substring(i + 1).startsWith(rest)) return true
                }
            }
            return false
        }
        val vowel = mapOf("아" to "아", "았" to "아", "어" to "어", "었" to "어", "여" to "어", "였" to "어")[first]
        if (vowel == null || rest.isEmpty()) return false
        val isPast = first in setOf("았", "었", "였")
        val words = hangulWords.findAll(text).map { it.value }.filterNot { it in conjunctions }.toList()
        for (word in words) {
            for (i in 0 until word.length - rest.length) {
                val after = word.substring(i + 1)
                if ((after == rest || after == rest + "요") && contracts(word[i], vowel, isPast, rest)) return true
            }
        }
        if (aeo.isEmpty()) return false
        val wanted = if (isPast) aeo.mapNotNull { past(it) }.toSet() else aeo
        for (word in words) {
            for (end in listOf(rest, rest + "요")) {
                if (!word.endsWith(end) || word.length == end.length) continue
                if (word.dropLast(end.length) in wanted) return true
            }
        }
        return false
    }

    private val particles = listOf("에서", "으로", "이랑", "하고", "을", "를", "이", "가", "은", "는", "도", "에", "만", "과", "와", "랑", "의", "로", "요")

    private fun stripParticle(w: String) = particles.firstOrNull { w.length > it.length && w.endsWith(it) }?.let { w.dropLast(it.length) } ?: w

    /**
     * Invented-Korean check for Nano output: a word counts as from the notes when its base form (particles and
     * endings removed) is in the entry: 커피를 → 커피, 먹어요/먹었어요 → 먹다, 봤어요 → 보다, 할게요 → -(으)ㄹ게요.
     * Not a lemmatizer: a new verb with a taught ending (갈게요 when the entry has no 가다) still counts as invented.
     */
    fun fromNotes(word: String, entry: String): Boolean {
        val w = stripParticle(word)
        val entryWords = hangulWords.findAll(entry).map { it.value }.toList()
        // Grammar cores of the entry ("-(으)ㄹ게요" -> 게요): the output word is that ending alone or on a stem from the entry.
        val cores = entry.lines().flatMap { koreanCores(it.substringAfter(":")) }.filter { it.second }.map { it.first }
        return entryWords.any { e ->
            val base = stripParticle(e)
            val stems = if (e.length >= 2 && e.endsWith("다")) {
                val forms = aeoForms(e) + e.dropLast(1)
                forms + forms.mapNotNull { past(it) }
            } else emptySet()
            (base.length >= 2 && base in w) || w == base || stems.any { w.startsWith(it) } ||
                (e.length >= 3 && e.endsWith("다") && hasForm(w, e))
        } || cores.any { core -> hasForm(word, core) && (word.length <= core.length + 1 || entryWords.any { it.length >= 2 && word.startsWith(it.take(2)) }) }
    }
}
