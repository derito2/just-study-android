package com.dero.juststudykorean.korean.teacher

import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.aeoForms
import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.py
import com.dero.juststudykorean.korean.pyMatch
import com.dero.juststudykorean.korean.pySplit
import com.dero.juststudykorean.korean.pySplitlines
import com.dero.juststudykorean.korean.pyStrip

// Port of backend/app/rag/selfcheck.py: Teacher checked against YOUR notes. Questions made from what your notes have
// (grammar names, "Example with…", words, your sentences with 맞아요?, "How do I say…?", conjugated verbs) and rules
// that must always hold: no model answer, no citation to a source that doesn't exist, no Korean that isn't in your notes.

/** A check that failed; [problem] is its kind (the report never carries the question or the answer). */
data class Failure(val check: String, val question: String, val problem: String)

class SelfCheckReport(val asked: Int, val byCheck: Map<String, Int>, val failures: List<Failure>) {
    val ok get() = failures.isEmpty()

    /** How many failures of each kind and check: what may be logged or printed (no text of the notes). */
    fun counts(): Map<String, Int> = failures.groupingBy { "${it.check}: ${it.problem}" }.eachCount().toSortedMap()
}

private val CITE = py("\\[(\\d{1,4})\\]")
private val BULLET = py("^\\s*•\\s*(.+?)\\s*(?:\\[\\d{1,4}\\])?\\s*$")
private val CLOSE_COLON = py("\\]:\\s*")
private val LEAD = py("^[\\s\\d.)●•]*")
private val SHOWN = py("^(.+?) = .*\\[\\d+\\]", multiline = true)
private val WORD_LINE = py("^\\s*\\d{1,3}[.)]\\s*([가-힣]{1,8})\\s*=\\s*\\S")
private val HANGUL_ONLY = py("[가-힣]+")
private val KO_SENTENCE = py("[가-힣 ,]+")
private val PARENS = py("\\(.*?\\)")
private val SIMPLE_ENGLISH = py("[A-Za-z' ,.!?-]+")
private val TWO_HANGUL = py("[가-힣]{2,}")
private val CITE_NUMBER = py("\\[\\d+\\]")

/** The Korean of an answer line: "가방 = Bag [1]: 가방이 커요. — …" -> the example. */
private fun korean(line: String): String =
    line.split(CLOSE_COLON).last().split(" — ", limit = 2)[0].split("=", limit = 2)[0].replaceFirst(LEAD, "").pyStrip()

private fun plain(text: String) = plainEnglish(text.replace(CITE_NUMBER, ""))

private fun bullet(line: String): String? = BULLET.pyMatch(line)?.groupValues?.get(1)

/** [timings]: if given, how long each answer took (ns), for the phone's p50/p90. */
class SelfCheck(private val teacher: Teacher, private val sample: Int = 40, seed: Long = 7, private val timings: MutableList<Long>? = null) {
    private val index = teacher.index()
    private val flatNotes = index.hits.joinToString("\n") { it.text.pySplit().joinToString(" ") }.replace(" ", "")
    private val rng = PyRandom(seed)
    private var asked = 0
    private val byCheck = LinkedHashMap<String, Int>()
    private val failures = mutableListOf<Failure>()

    private fun fail(check: String, question: String, problem: String) { failures += Failure(check, question, problem) }

    private fun ask(check: String, question: String): TeacherAnswer {
        val start = System.nanoTime()
        val result = teacher.ask(question)
        timings?.add(System.nanoTime() - start)
        asked++
        byCheck[check] = (byCheck[check] ?: 0) + 1
        if (result.model == NEEDS_MODEL) fail(check, question, "the model would answer (it should come from your notes)")
        val valid = result.sources.map { it.n }.toSet()
        if (CITE.findAll(result.answer).any { it.groupValues[1].toInt() !in valid }) fail(check, question, "cites a source that doesn't exist")
        for (line in result.answer.pySplitlines()) {
            val b = bullet(line)?.takeIf(::hasHangul) ?: continue
            val k = korean(b)
            if (k.isNotEmpty() && k.replace(" ", "") !in flatNotes) fail(check, question, "shows Korean that is not in your notes")
        }
        return result
    }

    private fun <T> some(items: List<T>): List<T> = if (items.size <= sample) items else rng.sample(items, sample)

    private fun otherNames(pattern: String): List<String> {
        val forms = patternForms(pattern).sortedWith(compareBy({ -it.length }, { it }))
        val main = forms.firstOrNull { it.length >= 2 && HANGUL_ONLY.matches(it) } ?: return emptyList()
        val out = when {
            main.endsWith("요") -> listOf("-" + main.dropLast(1))
            !main.endsWith("다") -> listOf("-" + main + "요")
            else -> aeoForms(main).sorted().take(1).map { "-" + it + "요" }
        }
        return out.filter { it.length > 2 }
    }

    private fun grammar() {
        val seen = mutableSetOf<String>()
        for ((_, point) in index.points) {
            if (!seen.add(point.pattern)) continue
            val names = listOf(point.pattern) + otherNames(point.pattern)
            for (name in names) {
                val q = "What does $name mean?"
                val result = ask("grammar_name", q)
                val shown = SHOWN.findAll(result.answer).map { it.groupValues[1] }
                if (result.promptVersion != "grammar.v1" || shown.none { it == point.pattern || sameGrammar(name, it) }) fail("grammar_name", q, "didn't show your point")
            }
            val q = "Example with ${names.last()}"
            val result = ask("grammar_example", q)
            if (result.promptVersion !in setOf("grammar.v1", "examples.v1")) fail("grammar_example", q, "unexpected path")
            val forms = patternForms(point.pattern).filter { it.length >= 2 }
            val hasAny = index.sentences.any { (line, _) -> forms.any { hasForm(korean(line), it, index.aeo) } }
            if (hasAny && "couldn't find a sentence" in result.answer) fail("grammar_example", q, "said there are no sentences, but your notes have some")
            if (result.promptVersion == "examples.v1") {
                for (line in result.answer.pySplitlines()) {
                    val b = bullet(line) ?: continue
                    if (forms.none { hasForm(korean(b), it, index.aeo) }) fail("grammar_example", q, "an example doesn't use the grammar")
                }
            }
        }
    }

    private fun words() {
        val lines = LinkedHashMap<String, String>()
        for (hit in index.hits) for (line in hit.text.pySplitlines()) WORD_LINE.pyMatch(line)?.let { lines[it.groupValues[1]] = line.pyStrip() }
        for (word in some(lines.keys.toList())) {
            val q = "What does $word mean?"
            if (word !in ask("word", q).answer) fail("word", q, "didn't show the entry")
        }
    }

    private fun sentences() {
        val pairs = index.sentences.filter { "=" in it.first }.map { (line, _) ->
            korean(line).trimEnd('.', '?', '!') to line.split("=", limit = 2)[1].pyStrip()
        }.filter { (ko, _) -> KO_SENTENCE.matches(ko) && ko.pySplit().size >= 2 }
        for ((ko, en) in some(pairs)) {
            var q = "$ko 맞아요?"
            if ("exactly like that" !in ask("sentence_check", q).answer) fail("sentence_check", q, "didn't recognise your own sentence")
            val changed = ko.dropLast(1) + if (!ko.endsWith("요")) "요" else "어"
            q = "$changed 맞아요?"
            val variant = ask("sentence_variant", q)
            if (variant.promptVersion != "check.v1") fail("sentence_variant", q, "unexpected path")
            if ("Grammar in your sentence" in variant.answer) {
                for (line in variant.answer.split("Grammar in your sentence, from your notes:", limit = 2)[1].pySplitlines()) {
                    val pattern = (bullet(line) ?: continue).split(" = ", limit = 2)[0]
                    val forms = patternForms(pattern).filter { it.length >= 2 }
                    val words = TWO_HANGUL.findAll(pattern).map { it.value }
                    if (forms.none { hasForm(changed, it, index.aeo) } && words.none { it in changed.pySplit() }) {
                        fail("sentence_variant", q, "shows a rule your sentence doesn't use")
                    }
                }
            }
            val english = en.replace(PARENS, "").trim { it in " .!?\"'" }
            if (english.pySplit().size in 2..12 && SIMPLE_ENGLISH.matches(english)) {
                q = "How do I say '$english'?"
                val phrase = ask("phrase", q)
                if (ko.replace(" ", "") !in phrase.answer.replace(" ", "")) fail("phrase", q, "didn't show your sentence")
                if (phrase.answer.startsWith("Your notes say")) {
                    for (line in phrase.answer.pySplitlines()) {
                        val b = bullet(line)?.takeIf { "=" in it } ?: continue
                        if (plain(b.split("=", limit = 2)[1]) != plain(english)) fail("phrase", q, "filler")
                    }
                }
            }
        }
    }

    private fun conjugated() {
        val cases = index.verbs.sorted().mapNotNull { verb ->
            val forms = aeoForms(verb).sortedWith(compareBy({ it.length }, { it }))
            val word = forms.firstOrNull()?.let { it + "요" } ?: ""
            if (word.isNotEmpty() && word !in flatNotes) verb to word else null
        }
        for ((verb, word) in some(cases)) {
            val q = "What does $word mean?"
            if (verb !in ask("conjugated", q).answer) fail("conjugated", q, "didn't lead to the verb")
        }
    }

    fun run(): SelfCheckReport {
        grammar(); words(); sentences(); conjugated()
        return SelfCheckReport(asked, byCheck, failures)
    }
}
