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
        assertEquals("3b9ead7188607d3362455e1e9917f41e3355a1b1efab8b8e425041fe08a44dde", sha)
    }
}
