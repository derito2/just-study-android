package com.dero.juststudykorean.data.documents

import android.content.Context
import com.dero.juststudykorean.data.backup.BackupFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Your PDFs and their extracted text, in private storage with the backup's layout (pdfs/<stored name>,
 * extracted/<document id>.json) and their sha256 in files.json, so Export backup carries them like an imported one.
 */
@Singleton
class DocumentFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, BackupFormat.FILES_DIR)
    private val index get() = File(dir, BackupFormat.FILES_INDEX)

    fun pdf(storedName: String) = File(dir, BackupFormat.pdfEntry(storedName))
    fun extracted(documentId: Long) = File(dir, BackupFormat.extractedEntry(documentId.toString()))

    /** Copies [input] to a temporary file in private storage; returns it and its sha256. Never reads anything else. */
    fun receive(input: InputStream): Pair<File, String> {
        val tmp = File(dir, "pdfs/.incoming-${System.nanoTime()}").apply { parentFile!!.mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { i -> tmp.outputStream().use { i.copyTo(it, 1 shl 20) } }
        return tmp to BackupFormat.sha256Hex(digest)
    }

    /** Keeps a received PDF under its stored name. */
    fun keepPdf(tmp: File, storedName: String, sha256: String) {
        check(tmp.renameTo(pdf(storedName))) { "cannot keep the PDF" }
        setIndex("pdfs", storedName, sha256)
    }

    /** Writes extracted/<id>.json atomically. */
    fun writeExtracted(documentId: Long, json: String) {
        val target = extracted(documentId).apply { parentFile!!.mkdirs() }
        val bytes = json.toByteArray()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(target)) { "cannot write the extracted text" }
        setIndex("extracted", documentId.toString(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }

    fun deleteExtracted(documentId: Long) {
        extracted(documentId).delete()
        setIndex("extracted", documentId.toString(), null)
    }

    fun deletePdf(storedName: String) {
        if (BackupFormat.isSafeName(storedName)) pdf(storedName).delete()
        setIndex("pdfs", storedName, null)
    }

    @Synchronized
    private fun setIndex(map: String, key: String, sha256: String?) {
        val json = index.takeIf { it.isFile }?.let { JSONObject(it.readText()) } ?: JSONObject()
        val entries = json.optJSONObject(map) ?: JSONObject().also { json.put(map, it) }
        if (sha256 == null) entries.remove(key) else entries.put(key, sha256)
        if (!json.has("extracted")) json.put("extracted", JSONObject())
        if (!json.has("pdfs")) json.put("pdfs", JSONObject())
        dir.mkdirs()
        val tmp = File(dir, "${BackupFormat.FILES_INDEX}.tmp")
        tmp.writeText(json.toString())
        check(tmp.renameTo(index)) { "cannot update files.json" }
    }
}
