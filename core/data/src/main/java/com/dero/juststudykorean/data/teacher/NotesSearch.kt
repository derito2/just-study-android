package com.dero.juststudykorean.data.teacher

import androidx.room3.useReaderConnection
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.aeoForms
import com.dero.juststudykorean.korean.detectLanguage
import com.dero.juststudykorean.korean.teacher.Hit
import com.dero.juststudykorean.korean.teacher.LEXICAL_FIND_LIMIT
import com.dero.juststudykorean.korean.teacher.LEXICAL_K
import com.dero.juststudykorean.korean.teacher.NotesStore
import com.dero.juststudykorean.korean.teacher.Retrieval
import com.dero.juststudykorean.korean.teacher.defines
import com.dero.juststudykorean.korean.teacher.keywords
import com.dero.juststudykorean.korean.teacher.koreanStems
import com.dero.juststudykorean.korean.teacher.whereContains
import kotlinx.coroutines.runBlocking

/**
 * Teacher's search over notes_fts (replaces the web's Chroma; R-014). unicode61 splits on spaces, so `학교에서` is one
 * token: the question's roots go as prefix terms (`"학교*" OR "공부해*"`), and a fragment only counts for a Korean root if
 * one of its words, without its ending or particle, is that root (`학` does not match `학생`). No meaning search on the
 * phone: hits are ranked by how many different roots they have, then by rowid. Blocking: call it off the main thread.
 */
class NotesSearch(private val db: JustStudyDatabase) : NotesStore {
    override val maxDistance = 0.55

    private fun <T> read(block: suspend (androidx.room3.Transactor) -> T): T = runBlocking { db.useReaderConnection { block(it) } }

    private fun rows(where: String, args: List<String>, limit: Int): List<Hit> = read { c ->
        c.usePrepared(
            "SELECT n.document_id, n.chroma_id, n.page, n.section, n.topic, n.content_type, n.text, d.original_name " +
                "FROM notes_fts n LEFT JOIN documents d ON d.id = n.document_id $where ORDER BY n.rowid LIMIT $limit",
        ) { st ->
            args.forEachIndexed { i, a -> st.bindText(i + 1, a) }
            buildList {
                while (st.step()) {
                    val id = st.getText(1)
                    val text = st.getText(6)
                    val chunk = Chunk(
                        st.getLong(0), if (st.isNull(7)) "" else st.getText(7), st.getLong(2).toInt(), id.substringAfterLast(':').toIntOrNull() ?: 0,
                        if (st.isNull(3)) null else st.getText(3), st.getText(4), st.getText(5), detectLanguage(text), text,
                    )
                    add(Hit(id, text, 0.0, chunk))
                }
            }
        }
    }

    /** Fragments with any of [terms] as the start of a word, in rowid order. */
    private fun match(terms: Collection<String>, limit: Int): List<Hit> {
        val query = terms.map { t -> WORD.findAll(t).joinToString(" ") { it.value } }.filter { it.isNotEmpty() }.distinct()
            .joinToString(" OR ") { "\"$it*\"" }
        if (query.isEmpty()) return emptyList()
        return rows("WHERE notes_fts MATCH ?", listOf(query), limit)
    }

    /** The roots to search for a word: as written and without its ending; a dictionary verb also as its stem and -아/-어 form. */
    private fun roots(word: String): Set<String> = buildSet {
        addAll(koreanStems(word).take(2))
        if (word.endsWith("다") && word.length >= 2) { add(word.dropLast(1)); addAll(aeoForms(word)) }
    }

    /** A Korean root counts when a word of the text, without its ending or particle, is it; other words by prefix. */
    private fun has(text: String, root: String): Boolean =
        if (!HANGUL.containsMatchIn(root)) WORD.findAll(text.lowercase()).any { it.value.startsWith(root.lowercase()) }
        else HANGUL_WORD.findAll(text).any { root in koreanStems(it.value) }

    override fun retrieve(question: String, k: Int): Retrieval {
        val roots = keywords(question).flatMap { if (HANGUL.containsMatchIn(it)) roots(it) else setOf(it) }.distinct()
        if (roots.isEmpty()) return Retrieval(emptyList(), null, false)
        val hits = match(roots, CANDIDATES)
            .map { h -> h to roots.count { has(h.text, it) } }.filter { it.second > 0 }
            .sortedByDescending { it.second }.take(k + LEXICAL_K).map { it.first.copy(lexical = true) }
        return Retrieval(hits, if (hits.isEmpty()) null else 0.0, hits.isNotEmpty())
    }

    override fun lexicalOnly(question: String): Retrieval? {
        val words = keywords(question)
        val contains = whereContains(words) ?: return null
        val hits = find(contains, LEXICAL_FIND_LIMIT).sortedBy { !defines(it.text, words) }.map { it.copy(lexical = true) }
        return Retrieval(hits, if (hits.isEmpty()) null else 0.0, hits.isNotEmpty())
    }

    /** Like the web's store: fragments that contain one of [contains] as written (FTS only narrows the candidates). */
    override fun find(contains: List<String>?, limit: Int): List<Hit> {
        if (contains == null) return rows("", emptyList(), limit)
        return match(contains, CANDIDATES).filter { h -> contains.any { it in h.text } }.take(limit)
    }

    override fun count(): Int = read { c -> c.usePrepared("SELECT COUNT(*) FROM notes_fts") { it.step(); it.getLong(0).toInt() } }

    /** Changes whenever a fragment is added or removed (rowids only grow): Teacher rebuilds its index then. */
    fun version(): Long = read { c -> c.usePrepared("SELECT COALESCE(MAX(rowid), 0) * 100000 + COUNT(*) FROM notes_fts") { it.step(); it.getLong(0) } }

    private companion object {
        const val CANDIDATES = 2000
        val WORD = Regex("[\\p{L}\\p{N}]+")
        val HANGUL = Regex("[가-힣]")
        val HANGUL_WORD = Regex("[가-힣]+")
    }
}
