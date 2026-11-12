package com.dero.juststudykorean.korean.teacher

import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.py
import java.security.MessageDigest
import kotlin.math.sqrt

// Port of backend/app/rag/retriever.py (keywords, defines, where_contains, Retriever) and of the in-memory store of
// rag/selfcheck.py, the one the Teacher goldens were made with. The app searches the FTS instead (:core:data NotesSearch).

/** A fragment of your notes found by a search (the web's vectorstore.Hit). [text] can be clipped; [chunk] has the rest. */
data class Hit(val id: String, val text: String, val distance: Double, val chunk: Chunk, val lexical: Boolean = false)

data class Retrieval(val hits: List<Hit>, val bestDistance: Double?, val enoughContext: Boolean) {
    companion object {
        val EMPTY = Retrieval(emptyList(), null, true)
    }
}

/** What Teacher needs from a search over your notes (the web's Retriever and its store). */
interface NotesStore {
    val maxDistance: Double

    /** By meaning and by word ([Retriever.retrieve]): the word hits first. */
    fun retrieve(question: String, k: Int): Retrieval

    /** Only by word, without meaning; null if the question has no content words. */
    fun lexicalOnly(question: String): Retrieval?

    /** Fragments containing any of [contains] (with its case variants); all of them if null. In the store's order. */
    fun find(contains: List<String>?, limit: Int): List<Hit>

    fun count(): Int
}

private val STOPWORDS = """a about an and are be can could did do does english example examples for from give
    how i in into is it korean me mean meaning means my note notes of on or please say said
    sentence sentences show tell that the this to translate translation use using was what
    when where which who why with word words write you your""".split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
private val ENGLISH = py("[A-Za-z][A-Za-z'-]{2,}")
private val HANGUL_RUN = py("[\\uac00-\\ud7a3]+")
const val MAX_KEYWORDS = 4
const val LEXICAL_K = 2
const val LEXICAL_CANDIDATES = 8
const val LEXICAL_FIND_LIMIT = 60

/** The chunk has a vocabulary entry for one of the words (not just an example that uses it). */
fun defines(text: String, words: List<String>): Boolean {
    for (word in words) {
        val w = Regex.escape(word)
        if (py("^[\\s\\d.)•*-]*[^\\n=]{1,40}=[^\\n]*\\b$w\\b", ignoreCase = true, multiline = true).containsMatchIn(text)) return true
        if (py("^[\\s\\d.)•*-]*$w[^\\n=]{0,20}=", multiline = true).containsMatchIn(text)) return true
        if (py("^[\\s\\d.)•*-]*[^\\n=/]{1,20}/(?:[^\\n=/]{1,20}/)*\\s*$w\\s*(?:/[^\\n=]*)?=", multiline = true).containsMatchIn(text)) return true
    }
    return false
}

/** Content words of a question: English without stop words, and the Korean as written and without its ending. */
fun keywords(question: String): List<String> {
    val words = ENGLISH.findAll(question).map { it.value }.filter { it.lowercase() !in STOPWORDS }.toMutableList()
    for (word in HANGUL_RUN.findAll(question)) words += koreanStems(word.value).take(2)
    return words.distinct().take(MAX_KEYWORDS)
}

/** The texts a chunk must contain: each word as written, in lower case and capitalized ($contains is case-sensitive). */
fun whereContains(words: List<String>): List<String>? =
    words.flatMap { listOf(it, it.lowercase(), it.lowercase().replaceFirstChar(Char::uppercaseChar)) }.distinct().ifEmpty { null }

private val TOKEN = py("[\\w가-힣]+")

/**
 * The web's self-check store: a bag-of-words "embedding" (md5 of each token into 64 dimensions) and a substring filter.
 * Deterministic and offline; used by the Teacher goldens and the self-check, not by the app.
 */
class MemoryStore(chunks: List<Chunk>, override val maxDistance: Double = 0.55) : NotesStore {
    private class Item(val chunk: Chunk, val vector: DoubleArray)

    private val items: List<Item> = LinkedHashMap<String, Item>().apply { for (c in chunks) put(c.chromaId, Item(c, vec(c.text))) }.values.toList()

    private fun vec(text: String): DoubleArray {
        val vector = DoubleArray(DIMS)
        for (token in TOKEN.findAll(text.lowercase())) {
            val digest = MessageDigest.getInstance("MD5").digest(token.value.toByteArray())
            vector[digest.last().toInt() and (DIMS - 1)] += 1.0 // int(md5 hex, 16) % 64: the low 6 bits
        }
        val norm = sqrt(vector.fold(0.0) { s, v -> s + v * v }).let { if (it == 0.0) 1.0 else it }
        return DoubleArray(DIMS) { vector[it] / norm }
    }

    private fun contains(text: String, contains: List<String>?) = contains == null || contains.any { it in text }

    private fun query(embedding: DoubleArray, k: Int, contains: List<String>?): List<Hit> = items
        .filter { contains(it.chunk.text, contains) }
        .map { item -> Hit(item.chunk.chromaId, item.chunk.text, 1 - (0 until DIMS).fold(0.0) { s, i -> s + embedding[i] * item.vector[i] }, item.chunk) }
        .sortedBy { it.distance }.take(k)

    override fun find(contains: List<String>?, limit: Int): List<Hit> =
        items.filter { contains(it.chunk.text, contains) }.take(limit).map { Hit(it.chunk.chromaId, it.chunk.text, 0.0, it.chunk) }

    override fun count() = items.size

    override fun retrieve(question: String, k: Int): Retrieval {
        val vector = vec(question)
        val semantic = query(vector, k, null)
        val words = keywords(question)
        val contains = whereContains(words)
        val lexical = if (contains == null) emptyList() else
            query(vector, LEXICAL_CANDIDATES, contains).sortedWith(compareBy({ !defines(it.text, words) }, { it.distance })).take(LEXICAL_K)
        val seen = lexical.map { it.id }.toSet()
        val hits = lexical.map { it.copy(lexical = true) } + semantic.filter { it.id !in seen }
        val best = hits.minOfOrNull { it.distance }
        return Retrieval(hits, best, lexical.isNotEmpty() || (best != null && best <= maxDistance))
    }

    override fun lexicalOnly(question: String): Retrieval? {
        val words = keywords(question)
        val contains = whereContains(words) ?: return null
        val hits = find(contains, LEXICAL_FIND_LIMIT).sortedBy { !defines(it.text, words) }.map { it.copy(lexical = true) }
        return Retrieval(hits, if (hits.isEmpty()) null else 0.0, hits.isNotEmpty())
    }

    private companion object {
        const val DIMS = 64
    }
}
