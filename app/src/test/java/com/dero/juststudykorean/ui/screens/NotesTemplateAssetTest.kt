package com.dero.juststudykorean.ui.screens

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shipped Notes template is exactly the file the owner approved. */
class NotesTemplateAssetTest {
    @Test
    fun assetIsTheApprovedTemplate() {
        val bytes = File("src/main/assets/Korean_Notes_Template.docx").readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("bca4ab0a6e7a1ae82b18bf298bc209485f25ed5c2777ae11e8905d1311c73189", sha)
    }
}

class NotesTemplateCopyTest {
    private val asset = File("src/main/assets/Korean_Notes_Template.docx")

    @Test
    fun copyIsByteIdenticalToTheAsset() {
        val out = java.io.ByteArrayOutputStream()
        org.junit.Assert.assertTrue(copyTemplate(asset.inputStream(), out))
        org.junit.Assert.assertArrayEquals(asset.readBytes(), out.toByteArray())
    }

    @Test
    fun aFailingStreamReportsFailureAndClosesBoth() {
        var closed = false
        val bad = object : java.io.OutputStream() {
            override fun write(b: Int) = throw java.io.IOException("disk full")
            override fun close() { closed = true }
        }
        org.junit.Assert.assertFalse(copyTemplate(asset.inputStream(), bad))
        org.junit.Assert.assertTrue(closed)
    }
}
