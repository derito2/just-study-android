package com.dero.juststudykorean.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val NOTES_TEMPLATE_FILE = "Korean_Notes_Template.docx"
const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
const val TEMPLATE_SAVED = "Template saved."
const val TEMPLATE_FAILED = "Could not save the template."

/** Byte copy of the template into the file the user chose. False if either stream fails. */
internal fun copyTemplate(source: InputStream, target: OutputStream): Boolean = try {
    source.use { s -> target.use { s.copyTo(it) } }
    true
} catch (_: IOException) {
    false
}

/** Saves the shipped template to [target] (SAF). On failure the partial file is deleted. Off the main thread. */
suspend fun saveTemplate(context: Context, target: Uri): Boolean = withContext(Dispatchers.IO) {
    val ok = try {
        val out = context.contentResolver.openOutputStream(target)
        out != null && copyTemplate(context.assets.open(NOTES_TEMPLATE_FILE), out)
    } catch (_: Exception) {
        false
    }
    if (!ok) runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
    ok
}
