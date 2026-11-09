package com.dero.juststudykorean.data.grammar

import com.dero.juststudykorean.data.db.JustStudyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

// Port of backend/app/services/reference_service.grammar (+ grammar/reference.sort_key) and the web's
// Grammar page filter, checked against golden/db/grammar.json (GrammarGoldenTest).

/** The filter pills: All · Particles · Endings · Tenses · Others (the `category` of each point). */
enum class GrammarFilter(val category: String?) { All(null), Particles("particles"), Endings("endings"), Tenses("tenses"), Others("others") }

data class GrammarSource(val documentId: Long, val filename: String, val pageNumber: Long)

data class GrammarPointUi(
    val id: Long,
    val kind: String,
    val category: String,
    val pattern: String,
    val meaning: String,
    val structure: List<String>,
    val examples: List<Pair<String, String>>,
    val explanation: List<String>,
    val notes: List<String>,
    val source: GrammarSource,
)

private val lead = Regex("^(?:[\\s\\-–~·.,]|\\([^)]*\\))+")
private val jamoStart = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".zip("가까나다따라마바빠사싸아자짜차카타파하").toMap()

/** Korean dictionary order: without the leading "-" or "(으)"; a bare consonant goes just before its syllable. */
internal fun sortKey(text: String): String {
    var t = Normalizer.normalize(text, Normalizer.Form.NFC).trim()
    t = lead.replaceFirst(t, "").ifEmpty { t }
    jamoStart[t.firstOrNull()]?.let { t = (it - 1).toString() + "￿" + t.substring(1) }
    return t.uppercase().lowercase()
}

@Singleton
class GrammarRepository @Inject constructor(private val db: JustStudyDatabase) {
    private fun strings(json: String?): List<String> = json?.let { Json.parseToJsonElement(it) as? JsonArray }?.map { (it as JsonPrimitive).content }.orEmpty()

    /** Every point of your ready documents, in Korean dictionary order, with the document it comes from. */
    suspend fun all(): List<GrammarPointUi> = withContext(Dispatchers.IO) {
        val docs = db.documentsDao().all().filter { it.status == "ready" }.associateBy { it.id }
        db.grammarPointsDao().all().filter { it.documentId in docs }.map { p ->
            GrammarPointUi(
                p.id, p.kind, p.category, p.pattern, p.meaning, strings(p.structure),
                p.examples?.let { Json.parseToJsonElement(it).jsonArray.map { e -> e.jsonArray.let { a -> a[0].jsonPrimitive.content to a[1].jsonPrimitive.content } } }.orEmpty(),
                strings(p.explanation), strings(p.notes), GrammarSource(p.documentId, docs.getValue(p.documentId).originalName, p.pageNumber),
            )
        }.sortedWith(compareBy({ sortKey(it.pattern) }, { it.pattern }, { it.id }))
    }

    /** The points of a filter that match [query] (in the pattern or the meaning), as you type. */
    fun points(filter: GrammarFilter, query: String): Flow<List<GrammarPointUi>> =
        db.invalidationTracker.createFlow("grammar_points", "documents").map {
            val q = query.trim().lowercase()
            all().filter { (filter.category == null || it.category == filter.category) && (q.isEmpty() || q in it.pattern.lowercase() || q in it.meaning.lowercase()) }
        }
}
