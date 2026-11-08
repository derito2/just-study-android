package com.dero.juststudykorean.poc

import org.json.JSONObject
import java.text.Normalizer

/** PoC 2: the four Nano jobs (plan 10.1), their prompts and deterministic validators. No note text is logged. */
object NanoTasks {
    // --- Ports of backend/app/quiz/text.py (checked against golden/text.json in NanoTest.validatorsMatchGolden).
    private val notWord = Regex("""[^\w\s]|_""")
    private val spaces = Regex("""\s+""")
    private val hangul = Regex("[ㄱ-ㆎ가-힣]")
    private val hangulWord = Regex("[ㄱ-ㆎ가-힣]+")
    private val alternatives = Regex("[;/,]")
    private val parens = Regex("""\([^)]*\)""")
    private val placeholder = Regex("""\s+(?:doing\s+)?(?:sth|smth|something|someone|somebody|sb|sm1)\.?$""", RegexOption.IGNORE_CASE)

    fun norm(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().replace(notWord, " ").replace(spaces, " ").trim()

    fun isGrounded(answer: String, source: String): Boolean {
        val a = norm(answer); val s = norm(source)
        if (a.isEmpty()) return false
        if (" $s ".contains(" $a ")) return true
        val words = s.split(" ").toSet()
        val tokens = a.split(" ").filter { it.length >= 2 || hangul.containsMatchIn(it) }
        if (tokens.isEmpty()) return false
        return tokens.all { if (hangul.containsMatchIn(it)) s.contains(it) else it in words }
    }

    fun leaksAnswer(prompt: String, answer: String): Boolean {
        val a = norm(answer); val p = norm(prompt)
        if (a.isEmpty() || !(a.length >= 3 || hangul.containsMatchIn(a))) return false
        return " $p ".contains(" $a ")
    }

    private fun dropPrefix(text: String, prefixes: List<String>): String =
        prefixes.firstOrNull { text.startsWith(it) && text.length > it.length }?.let { text.substring(it.length) } ?: text

    private fun dropArticle(t: String) = dropPrefix(t, listOf("a ", "an ", "the "))

    private fun englishForms(text: String): String {
        var t = dropArticle(norm(text.replace(parens, " ")))
        t = dropArticle(dropPrefix(t, listOf("to be ", "to ", "be ")))
        return t.replace(placeholder, "").trim().ifEmpty { t }
    }

    private fun englishAnswers(correct: String): List<String> =
        (listOf(correct) + correct.replace(parens, " ").split(alternatives)).map { it.trim() }.distinct().filter { norm(it).isNotEmpty() }

    /** Korean words in [out] that are not in [source] (invented Korean). */
    fun inventedKorean(out: String, source: String): List<String> =
        hangulWord.findAll(out).map { it.value }.filterNot { KoreanForms.fromNotes(it, source) }.toList()

    // --- Jobs.
    class Job(val prompt: String, val maxTokens: Int, val temperature: Float?, val validate: (String) -> Pair<Boolean, String>)

    fun coach(c: JSONObject): Job {
        val words = c.getJSONArray("words").let { a -> List(a.length()) { a.getString(it) } }
        val facts = "Weak topic: ${c.getString("weakTopic")}\nWords from that topic: ${words.joinToString(", ")}\n" +
            "Cards due today: ${c.getInt("dueCards")}\nAccuracy last 7 days: ${c.getInt("accuracyPct")}%\nStudy streak: ${c.getInt("streakDays")} days"
        val prompt = "You are a friendly Korean study coach. Write 2 or 3 short sentences in English for the learner, " +
            "using only these facts. Do not use any Korean word that is not listed in the facts.\n\nFacts:\n$facts"
        return Job(prompt, 120, null) { out ->
            val bad = inventedKorean(out, facts)
            (out.isNotBlank() && bad.isEmpty()) to if (bad.isEmpty()) "" else "invented_korean=${bad.size}"
        }
    }

    fun explain(c: JSONObject): Job {
        val entry = c.getString("entry")
        // v2: v1 ("in simple English") made Nano answer in Korean.
        val prompt = "You are an English-speaking tutor. Write your whole answer in English. " +
            "Explain the Korean grammar point below to a beginner in 3 or 4 short English sentences. " +
            "You may quote the Korean pattern and the Korean examples from the entry, but write no other Korean.\n\nEntry:\n$entry"
        return Job(prompt, 256, null) { out ->
            val bad = inventedKorean(out, entry)
            (out.isNotBlank() && bad.isEmpty()) to if (bad.isEmpty()) "" else "invented_korean=${bad.size}"
        }
    }

    private val option = Regex("""^\s*([A-D])[).:]\s*(.+?)\s*$""")
    private val answerLine = Regex("""ANSWER\s*:\s*\(?([A-D])""", RegexOption.IGNORE_CASE)

    fun mcq(c: JSONObject): Job {
        val ko = c.getString("korean"); val en = c.getString("english"); val source = c.getString("source")
        val prompt = "Write one multiple choice question for a Korean learner. The question asks what '$ko' means. " +
            "The correct option is \"$en\". Take the 3 wrong options from other English meanings in the notes below. " +
            "Answer in exactly this format and nothing else:\nQUESTION: ...\nA) ...\nB) ...\nC) ...\nD) ...\nANSWER: <letter>\n\nNotes:\n$source"
        return Job(prompt, 160, 0f) { out ->
            val lines = out.lines()
            val question = lines.firstOrNull { it.trim().startsWith("QUESTION", ignoreCase = true) }?.substringAfter(":")?.trim()
            val opts = lines.mapNotNull { option.find(it) }.associate { it.groupValues[1] to it.groupValues[2] }
            val letter = answerLine.find(out)?.groupValues?.get(1)?.uppercase()
            val valid = englishAnswers(en).map { englishForms(it) }.toSet()
            val correct = opts.filterValues { englishForms(it) in valid }.keys
            val bad = inventedKorean(out, source)
            when {
                question == null || opts.size != 4 || letter == null -> false to "parse"
                opts.values.map { norm(it) }.toSet().size != 4 -> false to "duplicate_options"
                correct != setOf(letter) -> false to "correct_count=${correct.size}"
                !isGrounded(opts.getValue(letter), source) -> false to "not_grounded"
                leaksAnswer(question, opts.getValue(letter)) -> false to "leaks_answer"
                else -> true to if (bad.isEmpty()) "" else "invented_korean=${bad.size}"
            }
        }
    }

    fun grade(c: JSONObject): Job {
        val prompt = "A Korean learner was asked what '${c.getString("korean")}' means. The correct answer is " +
            "\"${c.getString("correct")}\". The learner wrote \"${c.getString("given")}\". " +
            "Does the learner's answer mean the same thing as the correct answer? " +
            "Reply with YES or NO first, then one short reason."
        val label = c.getBoolean("label")
        return Job(prompt, 32, 0f) { out ->
            val said = out.trim().uppercase().let { if (it.startsWith("YES")) true else if (it.startsWith("NO")) false else null }
            when (said) {
                null -> false to "parse"
                label -> true to if (label) "tp" else "tn"
                else -> false to if (said) "fp" else "fn"
            }
        }
    }
}
