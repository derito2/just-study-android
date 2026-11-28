package com.dero.juststudykorean.korean.grammar

import com.dero.juststudykorean.korean.hangul.indexesOf
import com.dero.juststudykorean.korean.hangul.syllableOf
import com.dero.juststudykorean.korean.past

// F3 (FASE_11_PLAN PR 5): the conjugation engine of Grammar Lab. Golden rule: what can't be checked is never asked, so
// conjugate returns nothing for a word whose irregularity or type is not sure. The reference is ConjugatorTest.

enum class WordKind { Verb, Adjective }

/** What an ending attaches to. [TypeDependent]: its form changes between verbs and adjectives (present only). */
enum class Applies { Verb, Both, TypeDependent }

// Sources for [Applies]: 한국어기초사전 (krdict.korean.go.kr), the "앞말" note of each ending; the owner fixed the
// verb-only ones (-(으)ㄹ게요, -기로 하다, -지 마세요, -(으)ㄹ 뻔했다, -아/어 보다, -(으)ㅂ시다, -(으)세요, -고 있어요, -고 싶어요).
enum class Ending(val label: String, val applies: Applies) {
    PoliteAeo("-아요/어요", Applies.Both), PastPolite("-았어요/었어요", Applies.Both), Formal("-습니다/ㅂ니다", Applies.Both),
    And("-고", Applies.Both), SoBecause("-아서/어서", Applies.Both), If("-(으)면", Applies.Both), Because("-(으)니까", Applies.Both),
    Future("-(으)ㄹ 거예요", Applies.Both), Want("-고 싶어요", Applies.Verb), Progressive("-고 있어요", Applies.Verb),
    LetUs("-(으)ㅂ시다", Applies.Verb), Please("-(으)세요", Applies.Verb),
    Can("-(으)ㄹ 수 있어요", Applies.Verb), // ability; krdict: '-ㄹ 수 있다' 동사 뒤 (with adjectives it means "may")
    But("-지만", Applies.Both), Exclaim("-네요", Applies.Both), Recall("-더라고요", Applies.Both), YouKnow("-잖아요", Applies.Both),
    Reason("-거든요", Applies.Both), Confirm("-지요", Applies.Both), Promise("-(으)ㄹ게요", Applies.Verb),
    Whether("-(으)ㄹ지", Applies.Both), TheMore("-(으)ㄹ수록", Applies.Both),
    WorthIt("-(으)ㄹ 만하다", Applies.Verb), // krdict '만하다': 동사 뒤에서 '-을 만하다' 구성으로
    Almost("-(으)ㄹ 뻔했다", Applies.Verb), FearThat("-(으)ㄹ까 봐", Applies.Both), While("-(으)면서", Applies.Both),
    Decide("-기로 하다", Applies.Verb), Dont("-지 마세요", Applies.Verb), Try("-아/어 보다", Applies.Verb),
    Must("-아/어야 하다", Applies.Both),
    AndThen("-아/어가지고", Applies.Verb), // krdict '가지다' 보조 동사: 동사 뒤에서 '-어 가지고' 구성으로
    During("-는 동안", Applies.Verb), // krdict '동안': 동사 뒤에서 '-는 동안'
    Hearsay("-대요", Applies.TypeDependent), Realize("-구나", Applies.TypeDependent),
    Background("-(으)ㄴ/는데요", Applies.TypeDependent), Seems("-는 것 같다", Applies.TypeDependent),
}

enum class Irregular { None, B, D, S, Reu, H }

// The closed lists (krdict 활용 of each word). A stem ending in ㅂ, ㄷ, ㅅ, ㅎ or 르 that is in neither is not asked.
private val IRREGULAR = mapOf(
    Irregular.B to "돕다 곱다 춥다 덥다 쉽다 어렵다 가깝다 무겁다 가볍다 맵다 귀엽다 아름답다 반갑다 고맙다 즐겁다 눕다 더럽다 시끄럽다 차갑다 뜨겁다 부럽다 무섭다 싱겁다",
    Irregular.D to "듣다 걷다 싣다 깨닫다 알아듣다",
    Irregular.S to "짓다 낫다 붓다 잇다 긋다",
    Irregular.Reu to "모르다 빠르다 부르다 다르다 고르다 기르다 자르다 마르다 오르다 누르다 흐르다 서두르다 게으르다",
    Irregular.H to "그렇다 이렇다 저렇다 어떻다 빨갛다 노랗다 파랗다 하얗다 까맣다",
).flatMap { (kind, words) -> words.split(" ").map { it to kind } }.toMap()
private val REGULAR = ("입다 잡다 좁다 씹다 뽑다 업다 접다 닫다 받다 믿다 얻다 쏟다 뜯다 웃다 씻다 벗다 빗다 솟다 좋다 놓다 넣다 낳다 " +
    "닿다 쌓다 따르다 치르다 들르다").split(" ").toSet()
private val HONORIFIC = setOf("먹다", "마시다", "자다", "있다", "말하다") // -(으)세요 is 드세요, 주무세요…: not listable here

private const val T_NONE = 0; private const val T_RIEUL = 8; private const val T_B = 17; private const val T_S = 19
private const val T_D = 7; private const val T_H = 27
private const val L_IEUNG = 11
private const val V_A = 0; private const val V_AE = 1; private const val V_YA = 2; private const val V_YAE = 3; private const val V_EO = 4
private const val V_E = 5; private const val V_YEO = 6; private const val V_O = 8; private const val V_WA = 9; private const val V_WAE = 10
private const val V_OE = 11; private const val V_U = 13; private const val V_WEO = 14; private const val V_EU = 18; private const val V_I = 20
private val JAMO_TAIL = mapOf('ㄴ' to 4, 'ㄹ' to T_RIEUL, 'ㅂ' to T_B)

/** Adds [suffix]; one that starts with ㄴ, ㄹ or ㅂ goes into the last syllable (null if that one already has a final consonant). */
private fun attach(base: String, suffix: String): String? {
    val tail = JAMO_TAIL[suffix.firstOrNull()] ?: return base + suffix
    val (l, v, t) = indexesOf(base.lastOrNull() ?: return null) ?: return null
    return if (t == T_NONE) base.dropLast(1) + syllableOf(l, v, tail) + suffix.drop(1) else null
}

// The ending after the 아/어 form, after -(으), or straight after the stem.
private val AEO = mapOf(Ending.PoliteAeo to "요", Ending.SoBecause to "서", Ending.Try to " 보다", Ending.Must to "야 하다", Ending.AndThen to "가지고")
private val EU = mapOf(
    Ending.If to "면", Ending.Because to "니까", Ending.While to "면서", Ending.Please to "세요", Ending.LetUs to "ㅂ시다",
    Ending.Future to "ㄹ 거예요", Ending.Can to "ㄹ 수 있어요", Ending.Promise to "ㄹ게요", Ending.Whether to "ㄹ지",
    Ending.TheMore to "ㄹ수록", Ending.WorthIt to "ㄹ 만하다", Ending.Almost to "ㄹ 뻔했다", Ending.FearThat to "ㄹ까 봐",
)
private val PLAIN = mapOf(
    Ending.And to "고", Ending.But to "지만", Ending.Exclaim to "네요", Ending.Recall to "더라고요", Ending.YouKnow to "잖아요",
    Ending.Reason to "거든요", Ending.Want to "고 싶어요", Ending.Progressive to "고 있어요", Ending.Decide to "기로 하다",
    Ending.Dont to "지 마세요", Ending.During to "는 동안",
)

/** The irregular class of [verb] (by its last word: 알아듣다 is 듣다); null when its ending is ambiguous and it is in no list. */
fun irregularOf(verb: String): Irregular? {
    val stem = verb.removeSuffix("다")
    IRREGULAR.entries.filter { verb.endsWith(it.key) }.maxByOrNull { it.key.length }?.let { return it.value }
    if (REGULAR.any { verb.endsWith(it) }) return Irregular.None
    val (_, vowel, tail) = indexesOf(stem.lastOrNull() ?: return null) ?: return null
    val ambiguous = tail in setOf(T_B, T_D, T_S, T_H) || (stem.last() == '르' && stem.length >= 2) || (vowel == V_U && tail == T_NONE && stem.last() == '푸')
    return if (ambiguous) null else Irregular.None
}

/** The type of a word of your notes from its English meaning, as Games' wordType; null when not sure. */
fun wordKindOf(korean: String, meaning: String): WordKind? {
    if (!korean.endsWith("다") || korean in setOf("이다", "아니다", "있다", "없다", "되다")) return null
    val senses = meaning.split(Regex("[/;,]")).map { it.trim() }.filter { it.isNotEmpty() }
    val kinds = senses.map { m ->
        when {
            m.startsWith("To be ", ignoreCase = true) && m.length > 6 -> WordKind.Adjective
            m.startsWith("To ", ignoreCase = true) && !m.startsWith("To be ", ignoreCase = true) -> WordKind.Verb
            else -> null
        }
    }.toSet()
    return kinds.singleOrNull()
}

/** How a form was built, for Explain: the stem and each rule applied, in order. */
data class ConjugationTrace(val stem: String, val steps: List<String>)

private class Builder(val verb: String, val irregular: Irregular) {
    val stem = verb.removeSuffix("다")
    val steps = mutableListOf<String>()
    val last = stem.last()
    val parts = indexesOf(last)!!
    val head = stem.dropLast(1)
    fun with(lead: Int = parts.first, vowel: Int = parts.second, tail: Int = parts.third) = head + syllableOf(lead, vowel, tail)

    fun attach(base: String, suffix: String) = com.dero.juststudykorean.korean.grammar.attach(base, suffix)!!

    /** The 아/어 form: the right one first, then the uncontracted one where it is also standard. */
    fun aeo(): List<String> {
        val (lead, vowel, tail) = parts
        fun bright(v: Int) = v in setOf(V_A, V_YA, V_O)
        when (irregular) {
            Irregular.H -> { steps += "$verb is ㅎ-irregular: ㅎ drops and the vowel becomes ㅐ"; return listOf(with(vowel = if (vowel == V_YA) V_YAE else V_AE, tail = T_NONE)) }
            Irregular.B -> {
                val ending = if (verb.endsWith("돕다") || verb.endsWith("곱다")) "와" else "워"
                steps += "$verb is ㅂ-irregular: ㅂ becomes $ending before 아/어"; return listOf(with(tail = T_NONE) + ending)
            }
            Irregular.D -> { steps += "$verb is ㄷ-irregular: ㄷ becomes ㄹ before a vowel"; return listOf(with(tail = T_RIEUL) + if (bright(vowel)) "아" else "어") }
            Irregular.S -> { steps += "$verb is ㅅ-irregular: ㅅ drops before a vowel, with no contraction"; return listOf(with(tail = T_NONE) + if (bright(vowel)) "아" else "어") }
            Irregular.Reu -> {
                val (pl, pv, _) = indexesOf(head.last())!!
                val ending = if (bright(pv)) "라" else "러"
                steps += "$verb is 르-irregular: 르 becomes ㄹ$ending"; return listOf(head.dropLast(1) + syllableOf(pl, pv, T_RIEUL) + ending)
            }
            Irregular.None -> Unit
        }
        if (last == '하') { steps += "하 + 여 → 해"; return listOf(head + "해", head + "하여") }
        if (tail != T_NONE) { val e = if (bright(vowel)) "아" else "어"; steps += "$last has a final consonant and ${if (e == "아") "a bright vowel (ㅏ/ㅗ)" else "no ㅏ/ㅗ"}, so -$e"; return listOf(stem + e) }
        val plain = when (vowel) {
            V_A, V_EO, V_YEO -> listOf(stem)
            V_AE, V_E -> listOf(stem, stem + "어")
            V_O -> listOf(with(vowel = V_WA)) + if (lead == L_IEUNG && head.isEmpty()) emptyList() else listOf(stem + "아")
            V_U -> listOf(with(vowel = V_WEO), stem + "어")
            V_I -> listOf(with(vowel = V_YEO), stem + "어")
            V_OE -> listOf(with(vowel = V_WAE), stem + "어")
            V_EU -> {
                val brightBefore = head.isNotEmpty() && bright(indexesOf(head.last())!!.second)
                steps += "으 drops before 아/어"; listOf(with(vowel = if (brightBefore) V_A else V_EO))
            }
            else -> listOf(stem + "어")
        }
        if (plain.first() != stem + "어" && vowel != V_EU) steps += "$last + 아/어 contracts → ${plain.first().last()}"
        return plain
    }

    /** -(으)X: 으 after a final consonant; ㄹ drops before ㄴ, ㅂ and ㅅ and merges with ㄹ; the irregulars change first. */
    fun eu(suffix: String): String {
        val (_, _, tail) = parts
        return when {
            irregular == Irregular.B -> { steps += "$verb is ㅂ-irregular: ㅂ becomes 우"; attach(with(tail = T_NONE) + "우", suffix) }
            irregular == Irregular.D -> { steps += "$verb is ㄷ-irregular: ㄷ becomes ㄹ, then 으"; attach(with(tail = T_RIEUL) + "으", suffix) }
            irregular == Irregular.S -> { steps += "$verb is ㅅ-irregular: ㅅ drops, 으 stays"; attach(with(tail = T_NONE) + "으", suffix) }
            irregular == Irregular.H -> { steps += "$verb is ㅎ-irregular: ㅎ drops, no 으"; attach(with(tail = T_NONE), suffix) }
            tail == T_RIEUL -> when {
                suffix.first() == 'ㄹ' -> { steps += "$last ends in ㄹ, which merges with -(으)ㄹ"; stem + suffix.drop(1) }
                suffix.first() in "ㄴㅂ" || indexesOf(suffix.first())?.first in setOf(2, 7, 9) -> { steps += "$last ends in ㄹ, which drops before ㄴ, ㅂ and ㅅ"; attach(with(tail = T_NONE), suffix) }
                else -> { steps += "$last ends in ㄹ: no 으"; stem + suffix }
            }
            tail != T_NONE -> { steps += "$last has a final consonant, so 으 is added"; attach(stem + "으", suffix) }
            else -> attach(stem, suffix)
        }
    }

    /** A suffix that starts with a consonant; ㄹ drops before ㄴ (사네요, 사는). */
    fun plain(suffix: String): String {
        if (parts.third == T_RIEUL && indexesOf(suffix.first())?.first == 2) { steps += "$last ends in ㄹ, which drops before ㄴ"; return with(tail = T_NONE) + suffix }
        return stem + suffix
    }

    /** -습니다 after a final consonant (ㄹ drops: 삽니다), -ㅂ니다 otherwise. */
    fun formal() = if (parts.third != T_NONE && parts.third != T_RIEUL) stem + "습니다" else eu("ㅂ니다")
}

/** Every right form of [verb] + [ending] (the usual one first); empty when it can't be checked. [kind]: null if not sure. */
fun conjugate(verb: String, ending: Ending, kind: WordKind? = null): Set<String> = conjugateTraced(verb, ending, kind)?.first.orEmpty()

internal fun conjugateTraced(verb: String, ending: Ending, kind: WordKind?): Pair<Set<String>, ConjugationTrace>? {
    if (!verb.endsWith("다") || verb.length < 2 || verb in setOf("이다", "아니다") || verb.removeSuffix("다").any { indexesOf(it) == null }) return null
    when (ending.applies) {
        Applies.Verb -> if (kind != WordKind.Verb) return null
        Applies.TypeDependent -> if (kind == null) return null
        Applies.Both -> Unit
    }
    if (ending == Ending.Please && verb in HONORIFIC) return null
    val irregular = irregularOf(verb) ?: return null
    if (irregular == Irregular.Reu && verb.removeSuffix("다").length < 2) return null
    val b = Builder(verb, irregular)
    val adj = kind == WordKind.Adjective
    val forms: List<String> = when (ending) {
        in AEO -> b.aeo().map { it + AEO.getValue(ending) }
        Ending.PastPolite -> b.aeo().mapNotNull(::past).map { it + "어요" }
        in EU -> listOf(b.eu(EU.getValue(ending)))
        Ending.Exclaim -> listOfNotNull(b.plain("네요"), if (irregular == Irregular.H) b.with(tail = T_NONE) + "네요" else null) // both standard (국립국어원, 2015)
        in PLAIN -> listOf(b.plain(PLAIN.getValue(ending)))
        Ending.Formal -> listOf(b.formal())
        Ending.Confirm -> listOf(b.plain("지요"), b.plain("죠"))
        Ending.Hearsay -> listOf(if (adj) b.stem + "대요" else if (b.parts.third == T_NONE || b.parts.third == T_RIEUL) b.eu("ㄴ대요") else b.plain("는대요"))
        Ending.Realize -> listOf(if (adj) b.stem + "구나" else b.plain("는구나"))
        Ending.Background -> listOf(if (adj) b.eu("ㄴ데요") else b.plain("는데요"))
        Ending.Seems -> listOf(if (adj) b.eu("ㄴ 것 같다") else b.plain("는 것 같다"))
        else -> error("no rule for $ending")
    }
    return forms.toCollection(LinkedHashSet()) to ConjugationTrace(b.stem, b.steps.distinct())
}

/** Plausible wrong forms for Choose: no contraction, 아/어 swapped, the irregular done as a regular, 으 or ㄹ wrong. Never a right one. */
fun commonMistakes(verb: String, ending: Ending, kind: WordKind? = null): Set<String> {
    val right = conjugate(verb, ending, kind)
    if (right.isEmpty()) return emptySet()
    val stem = verb.removeSuffix("다")
    val wrong = mutableListOf<String?>()
    when (ending) {
        in AEO -> AEO.getValue(ending).let { rest -> wrong += stem + "아" + rest; wrong += stem + "어" + rest }
        Ending.PastPolite -> { wrong += stem + "았어요"; wrong += stem + "었어요" }
        in EU -> EU.getValue(ending).let { rest ->
            wrong += attach(stem + "으", rest) // 으 where it doesn't go (가으면, 살으면), or the irregular done as a regular (듣으면)
            wrong += attach(stem, rest) // no 으 (먹면), or ㄹ kept (살니까)
        }
        else -> { // the rest after the stem; none when the form contracts the stem (공부하 → 공부합니다): no glued form
            val rest = PLAIN[ending] ?: right.first().takeIf { it.startsWith(stem) }?.removePrefix(stem)
            if (rest != null) { wrong += stem + "어" + rest; wrong += stem + "으" + rest }
        }
    }
    return wrong.filterNotNull().filter { it !in right && it.isNotBlank() }.toCollection(LinkedHashSet())
}
