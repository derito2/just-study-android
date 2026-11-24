package com.dero.juststudykorean.korean.grammar

// F3 (FASE_11_PLAN PR 5): the Ending of a grammar point of your notes, from its pattern ("-아서/어서", "-(으)ㄴ/는데(요)").
// A point with no ending (a particle, a comparison of two forms, 이다…) has no production drills.

private val NOISE = Regex("[-~()\\s·.]")
private fun normal(text: String) = text.replace(NOISE, "")

/** Normalized spellings of each ending, as notes write them (the parentheses drop: "-(으)면" is "으면"). */
private val ALIASES: Map<String, Ending> = mapOf(
    Ending.PoliteAeo to "아요 어요 여요 해요", Ending.PastPolite to "았어요 었어요 였어요 했어요", Ending.Formal to "습니다 ㅂ니다",
    Ending.And to "고", Ending.SoBecause to "아서 어서 여서 해서", Ending.If to "으면 면", Ending.Because to "으니까 니까",
    Ending.Future to "으ㄹ거예요 ㄹ거예요 을거예요", Ending.Want to "고싶다 고싶어요", Ending.Progressive to "고있다 고있어요",
    Ending.LetUs to "으ㅂ시다 ㅂ시다 읍시다", Ending.Please to "으세요 세요", Ending.Can to "으ㄹ수있다 ㄹ수있다 을수있다 으ㄹ수있어요 ㄹ수있어요",
    Ending.But to "지만", Ending.Exclaim to "네요 네", Ending.Recall to "더라고요 더라고 더라", Ending.YouKnow to "잖아요 잖아",
    Ending.Reason to "거든요 거든", Ending.Confirm to "지요 죠", Ending.Promise to "으ㄹ게요 ㄹ게요 을게요 ㄹ게 을게",
    Ending.Whether to "으ㄹ지 ㄹ지 을지", Ending.TheMore to "으ㄹ수록 ㄹ수록 을수록", Ending.WorthIt to "으ㄹ만하다 ㄹ만하다 을만하다",
    Ending.Almost to "으ㄹ뻔했다 ㄹ뻔했다 을뻔했다 ㄹ뻔하다 을뻔하다", Ending.FearThat to "으ㄹ까봐 ㄹ까봐 을까봐", Ending.While to "으면서 면서",
    Ending.Decide to "기로하다 기로했다", Ending.Dont to "지말다 지마세요 지마", Ending.Try to "아보다 어보다", Ending.Must to "아야하다 어야하다 아야되다 어야되다",
    Ending.AndThen to "아가지고 어가지고", Ending.During to "는동안", Ending.Hearsay to "대요 ㄴ대요 는대요", Ending.Realize to "구나 는구나 군요 군",
    Ending.Background to "는데요 는데 으ㄴ데요 ㄴ데요", Ending.Seems to "는것같다 으ㄴ것같다 ㄴ것같다",
).flatMap { (ending, spellings) -> spellings.split(" ").map { it to ending } }.toMap()

private val PARTICLES = "이가 을를 은는 에 에서 에게 한테 께 도 만 와과 과 와 랑 이랑 하고 으로 로 까지 부터 보다 처럼 의 요".split(" ").toSet()

/** A particle (이/가, (으)로, 까지…): not an ending, so it never counts for or against EndingMatch. */
fun isParticle(pattern: String): Boolean = !pattern.trim().startsWith("-") && normal(pattern).replace("/", "") in PARTICLES

/** The ending of a point, read from its pattern; null if none (a comparison "A vs B", a particle, or not known). */
fun endingOf(pattern: String): Ending? {
    if (Regex("\\bvs\\b", RegexOption.IGNORE_CASE).containsMatchIn(pattern) || isParticle(pattern)) return null
    return pattern.split('/', ',').map(::normal).firstNotNullOfOrNull { ALIASES[it] }
}
