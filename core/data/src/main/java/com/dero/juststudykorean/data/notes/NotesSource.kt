package com.dero.juststudykorean.data.notes

import android.content.Context
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.ExtractedPage
import com.dero.juststudykorean.korean.chunkDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import javax.inject.Inject

/**
 * The extracted pages of each document (`extracted/<id>.json`, kept by the backup import; phase 4's reader
 * writes there too) and their chunks, like the web's `read_extracted` + `chunk_document`.
 */
class NotesSource @Inject constructor(@param:ApplicationContext private val context: Context) {
    private fun file(documentId: Long) = File(context.filesDir, "${BackupFormat.FILES_DIR}/${BackupFormat.extractedEntry(documentId.toString())}")

    fun pages(documentId: Long): List<ExtractedPage>? {
        val f = file(documentId).takeIf { it.isFile } ?: return null
        val pages = Json.parseToJsonElement(f.readText()).jsonObject["pages"]?.jsonArray ?: return null
        return pages.map { p ->
            val o = p.jsonObject
            ExtractedPage(
                o.getValue("page_number").jsonPrimitive.int,
                o["section"]?.jsonPrimitive?.contentOrNull,
                o["text"]?.jsonPrimitive?.contentOrNull ?: "",
                o["tables"]?.jsonArray?.map { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }.orEmpty(),
            )
        }
    }

    fun chunks(doc: DocumentsEntity): List<Chunk> = pages(doc.id)?.let { chunkDocument(doc.id, doc.originalName, it) }.orEmpty()
}
