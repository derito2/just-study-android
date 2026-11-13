package com.dero.juststudykorean.korean.teacher

import com.dero.juststudykorean.korean.ReferencePoint
import com.dero.juststudykorean.korean.casefold
import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.lemmas
import com.dero.juststudykorean.korean.py
import com.dero.juststudykorean.korean.pyMatch
import com.dero.juststudykorean.korean.pySplit
import com.dero.juststudykorean.korean.pySplitlines
import com.dero.juststudykorean.korean.pyStrip
import com.dero.juststudykorean.korean.pyTake

// Port of backend/app/rag/tutor.py (Tutor.ask_stream and the answers without the model) as an ordered list of
// responders (plan, point 7.2). The model's answer (and its checks) is phase 6: here a question that would need it
// gets the web's "not in your notes" message.

/** status: answered | not_found | no_context | no_entry (the web's TutorAnswer). */
data class TeacherAnswer(val status: String, val answer: String, val sources: List<Source>, val promptVersion: String, val model: String)

/** The web's no_context.v1 prompt: what Teacher says when your notes don't have it. */
const val NOT_IN_NOTES =
    "I couldn't find that in your documents. Try rephrasing the question, choosing a different document or topic, or uploading material that covers it."

private const val NOTES_MODEL = "none (your notes)"

/** [TeacherAnswer.model] of a question only the model can answer (phase 6): the self-check counts it as a failure. */
const val NEEDS_MODEL = "model (phase 6)"
private const val TUTOR_VERSION = "tutor.v1"
private const val MAX_INDEX_CHUNKS = 20000

/** One way of answering, tried in order; null passes the question to the next one. */
fun interface Responder {
    fun answer(turn: Teacher.Turn): TeacherAnswer?
}

class Teacher(private val store: NotesStore, private val k: Int = 4, private val contextChars: Int = 3500) {
    private var cached: Pair<Pair<Int, Int>, NotesIndex>? = null
    private var version = 0

    /** A PDF was indexed or deleted: rebuild the index on the next question (the web's invalidate_notes_index). */
    fun invalidate() { version++ }

    /** All your notes, cached until they change. */
    fun index(): NotesIndex {
        val key = version to store.count()
        cached?.takeIf { it.first == key }?.let { return it.second }
        return buildIndex(store.find(null, MAX_INDEX_CHUNKS)).also { cached = key to it }
    }

    /** What every responder may need from the question, computed once and only when asked for. */
    inner class Turn(val question: String) {
        val word: String? = askedWord(question)
        val quick: Retrieval? by lazy { store.lexicalOnly(question) }
        val retrieval: Retrieval by lazy { store.retrieve(question, k) }
        val sources: List<Source> by lazy { selectSources(retrieval.hits, store.maxDistance, contextChars) }
        val example: Boolean get() = asksExample(question) || asksSentence(question)
    }

    /** In the order of Tutor.ask_stream. */
    private val responders: List<Responder> = listOf(
        // A point named EXACTLY like the question, without "-" (particles: 이/가, 에서): your point before the word.
        Responder { t ->
            val named = t.word?.takeIf { !isGrammarWord(it) }?.let(::namedPoint) ?: return@Responder null
            grammar(named, t.quick ?: Retrieval.EMPTY, t.example, t.word)
        },
        // "Difference between 거절 and 사양하다": what your notes say about each one.
        Responder { t ->
            val compared = comparedWords(t.question) ?: return@Responder null
            if (t.word != null && isGrammarWord(t.word) && indexedPoint(t.word) != null) return@Responder null
            compareWords(compared)
        },
        // One word your notes define ("1. 가게 = Store"): that entry as written; else its verb or the examples it is in.
        Responder { t ->
            val word = t.word ?: return@Responder null
            val quick = t.quick?.takeIf { it.hits.isNotEmpty() } ?: return@Responder null
            val words = variants(word)
            var found = definingEntries(quick.hits, words)
            if (found.isNotEmpty() && asksExample(t.question)) found = found.filter { e -> e.details.any(::hasHangul) }
            if (found.isNotEmpty()) return@Responder entry(found, quick)
            fromVerb(word, quick) ?: exampleMentions(quick.hits, words)?.let { noEntry(word, it, quick) }
        },
        // "What does 바빠요 mean?": the entry of the verb of your notes (바쁘다).
        Responder { t -> t.word?.takeIf { !asksExample(t.question) }?.let { fromVerb(it, t.retrieval) } },
        // Grammar ("What does -네요 mean?"): your point as written; without one, your sentences that use it.
        Responder { t ->
            val word = t.word?.takeIf(::isGrammarWord) ?: return@Responder null
            val point = grammarPoint(t.retrieval.hits, word) ?: indexedPoint(word)
            if (point != null) grammar(point, t.retrieval, t.example, word) else grammarMissing(word, t.retrieval)
        },
        // "How do I say 'I'm tired'?": the sentences of your notes that say it, never one made up.
        Responder { t ->
            val phrase = (if (t.word != null && t.word.pySplit().size >= 2) t.word else askedPhrase(t.question)) ?: return@Responder null
            if (hasHangul(phrase) || phrase.pySplit().size < 2) return@Responder null
            saidInNotes(phrase, t.sources, t.retrieval) ?: refuse("not_found", t.retrieval, t.sources)
        },
        // "Is this sentence correct?": the closest examples of your notes (or yours, exactly like that).
        Responder { t ->
            val sentence = checkedSentence(t.question) ?: return@Responder null
            if (t.sources.isEmpty()) null else compare(sentence, t.sources, t.retrieval)
        },
        // A word that only appears in an example of another one.
        Responder { t -> t.word?.let { w -> exampleMentions(t.retrieval.hits, variants(w))?.let { noEntry(w, it, t.retrieval) } } },
        // A Korean word without an entry: the sentences where it appears, or "not in your notes". R1-01 (the app departs
        // from the web): "How do I say school?" also looks for the word in the translations of your sentences.
        Responder { t ->
            val word = t.word ?: return@Responder null
            when {
                isGrammarWord(word) || asksSentence(t.question) -> null
                hasHangul(word) -> wordInSentences(word, t.retrieval) ?: refuse("not_found", t.retrieval, emptyList())
                askedPhrase(t.question) != null -> wordInSentences(word, t.retrieval)
                else -> null
            }
        },
        // Nothing close enough: not even the model is asked.
        Responder { t -> if (!t.retrieval.enoughContext || t.sources.isEmpty()) refuse("no_context", t.retrieval, emptyList()) else null },
        // shortcut: the model's answer is phase 6; until then, the web's "not in your notes" with the sources it would have read.
        Responder { t -> refuse("not_found", t.retrieval, t.sources).copy(model = NEEDS_MODEL) },
    )

    fun ask(question: String): TeacherAnswer {
        val turn = Turn(question)
        return responders.firstNotNullOf { it.answer(turn) }
    }

    // ---------------------------------------------------------------- answers

    private fun notesAnswer(text: String, sources: List<Source>, version: String) = TeacherAnswer("answered", text, sources, version, NOTES_MODEL)

    private fun refuse(status: String, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval, sources: List<Source>) =
        TeacherAnswer(status, NOT_IN_NOTES, sources, TUTOR_VERSION, "none")

    private fun sourcesFor(hits: List<Hit>): List<Source> =
        hits.mapIndexed { i, hit -> Source(i + 1, hit.copy(text = hit.text.pyTake(contextChars)), cited = true) }

    private fun uniqueHits(hits: List<Hit>): List<Hit> = hits.distinctBy { it.id }

    private fun entry(found: List<Entry>, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval): TeacherAnswer {
        val hits = uniqueHits(found.map { it.hit })
        val numbers = hits.withIndex().associate { (i, h) -> h.id to i + 1 }
        return TeacherAnswer("answered", entryText(found, numbers), sourcesFor(hits), "entry.v1", "none (your entry)")
    }

    private fun noEntry(word: String, mentions: List<Mention>, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval): TeacherAnswer {
        val hits = uniqueHits(mentions.map { it.hit })
        val numbers = hits.withIndex().associate { (i, h) -> h.id to i + 1 }
        return TeacherAnswer("no_entry", noEntryText(word, mentions, numbers), sourcesFor(hits), TUTOR_VERSION, "none")
    }

    private fun indexedPoint(word: String): Pair<Hit, ReferencePoint>? {
        val points = index().points
        val wanted = word.pySplit().joinToString(" ")
        return points.firstOrNull { (_, p) -> p.pattern.pySplit().joinToString(" ") == wanted } ?: points.firstOrNull { (_, p) -> sameGrammar(word, p.pattern) }
    }

    private fun namedPoint(word: String): Pair<Hit, ReferencePoint>? {
        val wanted = word.pySplit().joinToString(" ")
        if (wanted.isEmpty() || !hasHangul(wanted) || wanted.length > 20) return null
        return index().points.firstOrNull { (_, p) -> p.pattern.pySplit().joinToString(" ") == wanted }
    }

    private fun find(texts: List<String>, limit: Int = 30): List<Hit> =
        whereContains(texts.filter { it.isNotEmpty() })?.let { store.find(it, limit) } ?: emptyList()

    private fun compareWords(words: List<String>): TeacherAnswer? {
        // ("entries", [Entry]), ("grammar", (hit, point)) or ("missing", (word, sentences))
        val blocks = mutableListOf<Pair<String, Any>>()
        for (word in words) {
            if (isGrammarWord(word)) {
                val found = store.retrieve("What does $word mean?", k)
                val point = grammarPoint(found.hits, word) ?: indexedPoint(word)
                blocks += if (point == null) "missing" to (word to sentencesWith(word)) else "grammar" to point
            } else {
                val quick = store.lexicalOnly("What does $word mean?")
                val found = quick?.let { definingEntries(it.hits, variants(word)) } ?: emptyList()
                if (found.isEmpty()) return null
                blocks += "entries" to found
            }
        }
        if (blocks.all { it.first == "missing" }) return null
        @Suppress("UNCHECKED_CAST")
        fun grammarOf(v: Any) = v as Pair<Hit, ReferencePoint>
        @Suppress("UNCHECKED_CAST")
        fun missingOf(v: Any) = v as Pair<String, List<Pair<String, Hit>>>
        @Suppress("UNCHECKED_CAST")
        fun entriesOf(v: Any) = v as List<Entry>
        val names = words + blocks.filter { it.first == "grammar" }.map { grammarOf(it.second).second.pattern }
        val about = if (blocks.any { it.first == "missing" }) notesAbout(names) else emptyList()
        val group = blocks.flatMap { (kind, v) ->
            when (kind) {
                "entries" -> entriesOf(v).map { it.hit }
                "grammar" -> listOf(grammarOf(v).first)
                else -> missingOf(v).second.map { it.second }
            }
        }
        val hits = uniqueHits(group + about.map { it.second })
        val numbers = hits.withIndex().associate { (i, h) -> h.id to i + 1 }
        val parts = blocks.map { (kind, v) ->
            when (kind) {
                "entries" -> entryText(entriesOf(v), numbers)
                "grammar" -> grammarOf(v).let { (hit, point) -> grammarLines(point, numbers.getValue(hit.id)).joinToString("\n") }
                else -> {
                    val (missing, sentences) = missingOf(v)
                    val block = mutableListOf("Your notes don't have a grammar point for $missing.")
                    if (sentences.isNotEmpty()) {
                        block += "Sentences in your notes that use it:"
                        block += sentences.map { (line, h) -> "• $line [${numbers.getValue(h.id)}]" }
                    }
                    block.joinToString("\n")
                }
            }
        }.toMutableList()
        if (about.isNotEmpty()) {
            parts += (listOf("What your notes say about it:") + about.map { (line, h) -> "• $line [${numbers.getValue(h.id)}]" }).joinToString("\n")
        }
        // An entry for both words ("곁 / 옆 = side") is shown once.
        val seen = mutableSetOf<String>()
        val kept = mutableListOf<String>()
        for (part in parts) {
            val lines = part.split("\n").filter { it.isBlank() || it !in seen }
            seen += lines.filter { it.isNotBlank() }
            if (lines.any { it.isNotBlank() }) kept += lines.joinToString("\n").trim('\n')
        }
        return notesAnswer("This is what your notes say about each one:\n\n" + kept.joinToString("\n\n"), sourcesFor(hits), "compare.v1")
    }

    private fun saidInNotes(phrase: String, sources: List<Source>, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval): TeacherAnswer? {
        val tokens = LOWER_WORD.findAll(casefold(phrase).replace("'", "")).map { it.value }
            .filter { it.length >= 2 && it !in STOP && it !in SHORT_STOP }.toList()
        if (tokens.isEmpty()) return null
        val wanted = plainEnglish(phrase)
        val hits = sources.map { it.hit }.toMutableList()
        for (hit in index().hits.ifEmpty { find(tokens, limit = 60) }) if (hits.none { it.id == hit.id }) hits += hit
        var sentences = mutableListOf<Pair<String, Hit>>()
        var words = mutableListOf<Pair<String, Hit>>()
        val tokenRx = tokens.map { py("\\b$it\\b") }
        for (hit in hits) {
            for (raw in hit.text.pySplitlines()) {
                val line = raw.pyStrip()
                if ("=" !in line || !hasHangul(line)) continue
                val english = casefold(line.split("=", limit = 2)[1]).replace("'", "")
                if (!tokenRx.all { it.containsMatchIn(english) }) continue
                val korean = line.split("=", limit = 2)[0].replaceFirst(NUMBERED, "").pyStrip()
                val isWord = NUMBER_START.pyMatch(line) != null && ' ' !in korean && !ONE_CONJUGATED.matches(korean)
                val target = if (isWord) words else sentences
                if (target.none { it.first == line }) target += line to hit
            }
        }
        val exact = (sentences + words).filter { (ln, _) -> plainEnglish(ln.split("=", limit = 2)[1]) == wanted }
        if (exact.isNotEmpty()) { sentences = exact.take(MAX_EXAMPLES).toMutableList(); words = mutableListOf() }
        sentences = sentences.take(MAX_EXAMPLES).toMutableList(); words = words.take(MAX_EXAMPLES).toMutableList()
        if (sentences.isEmpty() && words.isEmpty()) return null
        val used = uniqueHits((sentences + words).map { it.second })
        val n = used.withIndex().associate { (i, h) -> h.id to i + 1 }
        val parts = if (exact.isNotEmpty()) mutableListOf("Your notes say “$phrase” like this:")
        else mutableListOf("Your notes don't have “$phrase” as a word entry.", "They say it like this:")
        parts += sentences.map { (line, hit) -> "• $line [${n.getValue(hit.id)}]" }
        if (words.isNotEmpty()) {
            parts += "Words in your notes with that meaning (dictionary forms: they change in a sentence):"
            parts += words.map { (line, hit) -> "• $line [${n.getValue(hit.id)}]" }
        }
        return notesAnswer(parts.joinToString("\n"), sourcesFor(used), "phrase.v1")
    }

    private fun fromVerb(word: String, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval): TeacherAnswer? {
        if (!hasHangul(word) || ' ' in word.pyStrip() || isGrammarWord(word)) return null
        val index = index()
        val verbs = lemmas(word, index.verbs)
        if (verbs.isEmpty()) return null
        val found = definingEntries(index.hits, verbs)
        if (found.isEmpty()) return null
        val used = uniqueHits(found.map { it.hit })
        val numbers = used.withIndex().associate { (i, h) -> h.id to i + 1 }
        val text = "“${word.pyStrip()}” is a conjugated form of ${verbs.joinToString(" or ")} in your notes:\n\n" + entryText(found, numbers)
        return notesAnswer(text, sourcesFor(used), "entry.v1")
    }

    /** The sentences of your notes with [word]: in their Korean (학교 also as 학교가) or, for English, in their translation. */
    private fun wordInSentences(word: String, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval): TeacherAnswer? {
        val target = word.trim { it in " .?!\"'“”‘’" }
        val korean = hasHangul(target)
        val forms = variants(target)
        val english = forms.map { py("\\b${Regex.escape(it)}\\b", ignoreCase = true) }
        val found = index().sentences.filter { (ln, _) ->
            val parts = ln.split("=", limit = 2)
            if (korean) forms.any { it in parts[0] } else parts.size == 2 && english.any { it.containsMatchIn(parts[1]) }
        }.take(MAX_EXAMPLES)
        if (found.isEmpty()) return null
        val used = uniqueHits(found.map { it.second })
        val n = used.withIndex().associate { (i, h) -> h.id to i + 1 }
        val lines = listOf("Your notes don't have an entry for “$target”. It appears in these sentences:") +
            found.map { (line, h) -> "• $line [${n.getValue(h.id)}]" }
        return notesAnswer(lines.joinToString("\n"), sourcesFor(used), "entry.v1")
    }

    private fun grammarMissing(word: String, retrieval: Retrieval): TeacherAnswer {
        val sentences = sentencesWith(word)
        val about = notesAbout(listOf(word))
        if (sentences.isEmpty() && about.isEmpty()) return refuse("not_found", retrieval, emptyList())
        val used = uniqueHits((sentences + about).map { it.second })
        val n = used.withIndex().associate { (i, h) -> h.id to i + 1 }
        val lines = mutableListOf("Your notes don't have a grammar point for $word.")
        if (sentences.isNotEmpty()) {
            lines += "Sentences in your notes that use it:"
            lines += sentences.map { (line, h) -> "• $line [${n.getValue(h.id)}]" }
        }
        if (about.isNotEmpty()) {
            lines += "What your notes say about it:"
            lines += about.map { (line, h) -> "• $line [${n.getValue(h.id)}]" }
        }
        return notesAnswer(lines.joinToString("\n"), sourcesFor(used), "examples.v1")
    }

    /** Sentences of your notes that use that grammar, also conjugated (와서, 바빠서), under any of its names. */
    private fun sentencesWith(vararg words: String, limit: Int = MAX_EXAMPLES): List<Pair<String, Hit>> {
        val index = index()
        val forms = words.flatMap { w -> patternForms(w).filter { it.length >= 2 } }.toSet().ifEmpty { words.map(::plainForm).toSet() }
        return index.sentences.filter { (line, _) ->
            val korean = line.split("=", limit = 2)[0]
            forms.any { it.isNotEmpty() && hasForm(korean, it, index.aeo) }
        }.take(limit)
    }

    /** Explanations of your notes about that grammar (not sentences or entries), those naming more of it first. */
    private fun notesAbout(words: List<String>, limit: Int = 4): List<Pair<String, Hit>> {
        val plains = words.flatMap { w -> patternForms(w).filter { it.length >= 2 } }.toSet().sortedBy { it.length }
        if (plains.isEmpty()) return emptyList()
        val alone = plains.map { py("(?<![가-힣])(?:\\(?[으이]\\)?)?${Regex.escape(it)}(?![가-힣])") }
        val arrows = plains.map { py("(?<![가-힣])${Regex.escape(it)}\\s*(?:→|->)") }
        val scored = mutableListOf<Triple<Int, String, Hit>>()
        val seen = mutableSetOf<String>()
        for (hit in index().hits) {
            for (raw in hit.text.pySplitlines()) {
                val line = raw.pyStrip()
                if (line in seen || "=" in line || line.length > 220 || !LATIN.containsMatchIn(line)) continue
                if (HEADING_OR_NUMBER.pyMatch(line) != null) continue
                if (LABELED.pyMatch(line) != null && NOTE_LABEL.pyMatch(line) == null) continue
                if (arrows.any { it.containsMatchIn(line) }) continue
                val score = alone.count { it.containsMatchIn(line) }
                if (score > 0) {
                    seen += line
                    scored += Triple(score, line, hit)
                }
            }
        }
        return scored.sortedBy { -it.first }.take(limit).map { it.second to it.third }
    }

    private fun grammar(found: Pair<Hit, ReferencePoint>, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval, example: Boolean, asked: String?): TeacherAnswer {
        val (hit, point) = found
        val sources = sourcesFor(listOf(hit))
        val sentences = point.examples.map { it[0] to it[1] }
            .filter { (ko, _) -> isSentence(ko) || ko.length <= 12 }.filter { (ko, _) -> !RULE_BITS.containsMatchIn(ko) }
        val name = asked?.takeIf { it.isNotEmpty() } ?: point.pattern
        if (example && sentences.isNotEmpty()) {
            val lines = listOf("Examples of ${point.pattern} in your notes [1]:") + sentences.map { (ko, en) -> if (en.isNotEmpty()) "• $ko = $en" else "• $ko" }
            return notesAnswer(lines.joinToString("\n"), sources, "grammar.v1")
        }
        if (example) {
            // Sentences that use the pattern in ALL your notes, also conjugated; those of the point itself first.
            val withIt = sentencesWith(name, point.pattern, limit = 200).sortedBy { it.second.id != hit.id }.take(MAX_EXAMPLES)
            if (withIt.isNotEmpty()) {
                val used = uniqueHits((if (withIt.any { it.second.id == hit.id }) listOf(hit) else emptyList()) + withIt.map { it.second })
                val n = used.withIndex().associate { (i, h) -> h.id to i + 1 }
                val lines = listOf("Sentences in your notes that use $name:") + withIt.map { (ln, h) -> "• $ln [${n.getValue(h.id)}]" }
                return notesAnswer(lines.joinToString("\n"), sourcesFor(used), "examples.v1")
            }
        }
        var lines = grammarLines(point, 1)
        if (example) lines = listOf("I couldn't find a sentence with $name in your notes, but they explain the grammar:") + lines
        return notesAnswer(lines.joinToString("\n"), sources, "grammar.v1")
    }

    private fun compare(sentence: String, sources: List<Source>, @Suppress("UNUSED_PARAMETER") retrieval: Retrieval): TeacherAnswer {
        val flat = sentence.pySplit().joinToString(" ").trimEnd('.', '?', '!')
        val index = index()
        val hits = index.hits.ifEmpty { sources.map { it.hit } }
        val pool = index.sentences.ifEmpty {
            hits.flatMap { h -> h.text.pySplitlines().map { it.pyStrip() }.filter { isSentence(it) && it.length <= MAX_EXAMPLE_LINE }.map { it to h } }
        }
        fun bare(text: String) = text.replaceFirst(LEAD_NUMBER, "").replace(BARE_DROP, "")
        val mineBare = bare(flat)
        var exact = pool.firstOrNull { (line, _) -> mineBare.isNotEmpty() && bare(line.split("=", limit = 2)[0]) == mineBare }?.second
        if (exact == null && index.sentences.isEmpty()) exact = hits.firstOrNull { flat.isNotEmpty() && flat in it.text.pySplit().joinToString(" ") }
        if (exact != null) return notesAnswer("Your sentence is in your notes exactly like that [1].", sourcesFor(listOf(exact)), "check.v1")

        val words = flat.pySplit()
        val last = words.lastOrNull() ?: ""
        val endings = if (last.length >= 3 && last.last() in '가'..'힣') setOf(last.takeLast(2)) else emptySet()
        val wordSet = words.toSet()
        val rules = mutableListOf<Pair<String, Hit>>()
        val ruleForms = mutableSetOf<String>()
        for ((hit, point) in index.points.ifEmpty { hits.flatMap { h -> points(h).map { h to it } } }) {
            val forms = patternForms(point.pattern).filter { it.length >= 2 }
            val used = forms.filter { hasForm(flat, it, index.aeo) }
            val titleWords = TWO_HANGUL.findAll(point.pattern).map { it.value }.filter { it in wordSet }.toSet()
            // R1-12: a form listed in the point's structure ("+기 전에") whose word your sentence has (전에).
            val listed = point.structure.firstOrNull { f -> !LATIN.containsMatchIn(f) && TWO_HANGUL.findAll(f).any { it.value in wordSet } }
            if (used.isEmpty() && titleWords.isEmpty() && listed == null) continue
            ruleForms += used
            ruleForms += forms.filter { f -> f.length >= 3 && titleWords.any { it in f } }
            val rule = "${point.pattern}${listed?.let { " (${it.trimStart('-', ' ')})" } ?: ""} = ${point.meaning}"
            if (rules.none { it.first == rule }) rules += rule to hit
        }
        val mine = flat.filter { it in '가'..'힣' }.toSet() - COMMON
        val ranked = mutableListOf<Triple<Int, String, Hit>>()
        val half = (overlap(flat, flat) + 1) / 2
        val myVerbs = words.flatMap { index.lemmasOf(it) }.toSet()
        for ((line, hit) in pool) {
            val korean = line.split("=", limit = 2)[0]
            val usesRule = ruleForms.any { hasForm(korean, it, index.aeo) }
            val ending = endings.count { it in korean }
            val pairs = overlap(flat, korean)
            val inBare = mineBare in bare(korean)
            if (rules.isNotEmpty() && !(usesRule || ending > 0 || pairs >= maxOf(3, half)) && !(index.sharesVerb(korean, myVerbs) || inBare)) continue
            val theirs = HANGUL_WORDS.findAll(korean).map { it.value }.toSet()
            val sameVerb = theirs.flatMap { index.lemmasOf(it) }.any { it in myVerbs }
            val samePredicate = last.length >= 2 && last in theirs
            if (!(usesRule || sameVerb || samePredicate || inBare) && pairs < 2 + (if (ending > 0) 1 else 0)) continue
            val own = rules.any { it.second.id == hit.id }
            val inside = mineBare.length >= 4 && inBare
            val score = 20 * inside.toInt() + 2 * pairs + (mine intersect korean.toSet()).size + 3 * ending + 8 * usesRule.toInt() +
                4 * own.toInt() + 5 * (sameVerb || samePredicate).toInt()
            if (score >= 4) ranked += Triple(score, line, hit)
        }
        val sorted = ranked.sortedBy { -it.first }
        val best = sorted.firstOrNull()?.first ?: 0
        val shown = mutableListOf<Pair<String, Hit>>()
        for ((score, line, hit) in sorted) {
            if (score * 5 < best * 3) break
            if (shown.none { it.first == line }) shown += line to hit
            if (shown.size == MAX_EXAMPLES) break
        }
        val topRules = rules.take(2)
        val usedHits = uniqueHits((shown + topRules).map { it.second })
        val n = usedHits.withIndex().associate { (i, h) -> h.id to i + 1 }
        val lines = if (shown.isNotEmpty() || topRules.isNotEmpty()) {
            val out = mutableListOf(
                if (shown.isNotEmpty()) "I can't check a sentence reliably, and your notes don't have this one. Compare it with the closest examples in your notes:"
                else "I can't check a sentence reliably, and your notes don't have this one or an example close to it.",
            )
            out += shown.map { (line, h) -> "• $line [${n.getValue(h.id)}]" }
            if (topRules.isNotEmpty()) {
                out += "Grammar in your sentence, from your notes:"
                out += topRules.map { (rule, h) -> "• $rule [${n.getValue(h.id)}]" }
            }
            out
        } else listOf("I can't check a sentence reliably, and your notes have no example close to this one. Ask me about the grammar it uses instead.")
        return notesAnswer(lines.joinToString("\n"), sourcesFor(usedHits), "check.v1")
    }

    private companion object {
        val LOWER_WORD = py("[a-z]+")
        val STOP = "the and you are was were not his her its our their this that with".split(" ").toSet()
        val SHORT_STOP = "to do is am be it of in on at an im me my so or as by".split(" ").toSet()
        val NUMBERED = py("^\\s*\\d{1,3}[.)]\\s*")
        val NUMBER_START = py("^\\s*\\d{1,3}[.)]")
        val ONE_CONJUGATED = py("[가-힣]{2,}요[.?!]?")
        val LATIN = py("[A-Za-z]")
        val HEADING_OR_NUMBER = py("^\\s*(?:#|\\d{1,3}[.)])")
        val NOTE_LABEL = py("^\\s*(?:notes?|tip|rule)", ignoreCase = true)
        val LEAD_NUMBER = py("^[\\s\\d.)]*")
        val BARE_DROP = py("[\\s.,!?~]")
        val TWO_HANGUL = py("[가-힣]{2,}")
        val HANGUL_WORDS = py("[가-힣]+")
        val COMMON = "가이은는을를에요다고도의와과서".toSet()

        fun Boolean.toInt() = if (this) 1 else 0
    }
}

private val PLAIN_DROP = py("\\(.*?\\)|\\[\\d+\\]")
private val PLAIN_WORDS = py("[a-z]+")

/** "Go to the side." -> "go to the side": to see if your notes say it as written. */
fun plainEnglish(text: String): String =
    PLAIN_WORDS.findAll(casefold(text).replace("'", "").replace("’", "").replace(PLAIN_DROP, " ")).joinToString(" ") { it.value }

// "저는 학교에 가는데요 맞아요?", "자기 전에 이를 닦아요. Is this sentence correct?", "Is 먹는 전에 wrong?"
private val CHECK = py(
    "([가-힣][가-힣\\s.,!]{3,}?)\\s*[.?!]?\\s*(?:맞아요|맞나요|맞습니까|맞아|괜찮아요)\\s*\\??\\s*$" +
        "|([가-힣][가-힣\\s.,!]{3,}?)\\s*[.?!]?\\s*(?:is|was)\\s+(?:this|that|it|my)\\s*(?:sentence)?\\s*(?:correct|right|ok|okay|natural)\\b" +
        "|\\b(?:is|was)\\s+([가-힣][가-힣\\s]*?)\\s+(?:wrong|incorrect|correct|right|ok|okay)\\b",
    ignoreCase = true,
)

/** The Korean sentence you ask to check, or null if the question is not that. */
fun checkedSentence(question: String): String? =
    CHECK.find(question.pyStrip())?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.pyStrip()

/** How many syllable pairs two sentences share (to rank similar examples). */
private fun overlap(a: String, b: String): Int {
    fun pairs(t: String) = (0 until t.length - 1).filter { t[it] in '가'..'힣' }.map { t.substring(it, it + 2) }.toSet()
    return (pairs(a.replace(" ", "")) intersect pairs(b.replace(" ", ""))).size
}
