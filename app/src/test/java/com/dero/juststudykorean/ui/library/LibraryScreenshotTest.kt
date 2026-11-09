package com.dero.juststudykorean.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.documents.Duplicate
import com.dero.juststudykorean.data.documents.DuplicateInfo
import com.dero.juststudykorean.data.documents.Extracted
import com.dero.juststudykorean.data.documents.ExtractedGrammar
import com.dero.juststudykorean.data.documents.ExtractedWord
import com.dero.juststudykorean.data.documents.Fragment
import com.dero.juststudykorean.data.documents.LibraryDocument
import com.dero.juststudykorean.data.documents.STAGE_CHUNKING
import com.dero.juststudykorean.data.documents.STAGE_PENDING
import com.dero.juststudykorean.data.documents.VocabularyWord
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/** Library with fixed fake data, at 411 dp (OPPO Find X8 Pro). Follows design/library.md. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class LibraryScreenshotTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val nov8 = 1_762_646_400_000L // 2025-11-09 00:00 UTC: Nov 8 in Mexico City

    private fun doc(id: Long, name: String, status: String, pages: Long = 0, tables: Long = 0, chars: Long = 0, error: String? = null) =
        DocumentsEntity(id, name, "$id.pdf", "sha$id", pages, status, status == "ready", 0, tables, chars, null, error, nov8, null)

    private val ready = LibraryDocument(doc(3, "Gramatica_semana_14.pdf", "ready", 2, 0, 1766), emptyList(), null)
    private val warned = LibraryDocument(doc(2, "Vocab_cafe_y_comida.pdf", "ready", 3, 1, 2840), listOf("Page 3 has no text and was skipped."), null)
    private val processing = LibraryDocument(doc(4, "Korean notes week 15.pdf", "processing"), emptyList(), STAGE_CHUNKING)
    private val queued = LibraryDocument(doc(5, "Frases_dramas_y_redes.pdf", "uploaded"), emptyList(), STAGE_PENDING)
    private val failed = LibraryDocument(
        doc(1, "Scan of my notebook.pdf", "unsupported_scanned", error = "This document doesn't contain enough digital text to be processed automatically. OCR is not enabled in this application."),
        emptyList(), null,
    )
    private val corrupt = LibraryDocument(doc(6, "notes (1).pdf", "failed", error = "The file is corrupt or is not a valid PDF."), emptyList(), null)

    private val extracted = Extracted(
        words = listOf(
            ExtractedWord("메뉴판", "Menu", null, "메뉴판 좀 주세요.", "Can I see the menu?", emptyList(), emptyList(), 1),
            ExtractedWord("계산하다", "To pay (the bill)", null, null, null, emptyList(), emptyList(), 1),
            ExtractedWord("포장", "Takeout", null, null, null, emptyList(), emptyList(), 2),
            ExtractedWord("아이스 아메리카노", "Iced americano", null, null, null, emptyList(), emptyList(), 2),
        ),
        grammar = listOf(ExtractedGrammar("pattern", "-(으)ㄹ게요", "I will (a promise)", emptyList(), listOf("제가 먼저 연락할게요." to "I'll contact you first"), null, 1)),
        fragments = listOf(Fragment(1, 1, "Vocabulary", "Vocabulary", "vocabulary", "Vocabulary\n1. 메뉴판 = Menu")),
    )

    private fun snap(name: String, fontScale: Float = 1f, bar: Boolean = true, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustStudyTheme(darkTheme = false) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content() }
                        if (bar) JsBottomBar(current = TopLevel.Library, onSelect = {})
                    }
                }
            }
        }

    @Composable
    private fun Library(
        docs: List<LibraryDocument>?,
        open: Long? = null,
        duplicates: DuplicatesShown? = null,
        deleteAtStart: LibraryDocument? = null,
        error: Boolean = false,
    ) = LibraryContent(
        DocumentsState(docs, error), false, open, open?.let { mapOf(it to ExtractedState.Loaded(extracted)) }.orEmpty(), duplicates,
        remember { SnackbarHostState() }, zone, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, deleteAtStart = deleteAtStart,
    )

    @Test fun libraryEmpty() = snap("library_empty") { Library(emptyList()) }

    @Test fun libraryProcessing() = snap("library_processing") { Library(listOf(queued, processing, ready)) }

    @Test fun libraryReady() = snap("library_ready") { Library(listOf(ready, warned)) }

    @Test fun libraryFailed() = snap("library_failed") { Library(listOf(corrupt, failed)) }

    @Test fun detailsWords() = snap("library_details_words") { Library(listOf(warned, ready), open = warned.document.id) }

    @Test fun duplicateWord() = snap("library_duplicate_word") {
        val info = { file: String -> DuplicateInfo("To pay (the bill)", "오늘은 제가 계산할게요.", "I'll pay today", file, 1) }
        Library(
            listOf(ready, warned),
            duplicates = DuplicatesShown(
                "Copy_of_Vocab_cafe_y_comida.pdf",
                listOf(
                    Duplicate("word", "계산하다", listOf(info("Vocab_cafe_y_comida.pdf")), info("Copy_of_Vocab_cafe_y_comida.pdf"), "To pay (the bill)"),
                    Duplicate("word", "메뉴판", listOf(info("Vocab_cafe_y_comida.pdf")), info("Copy_of_Vocab_cafe_y_comida.pdf"), "Menu"),
                ),
            ),
        )
    }

    @Test fun deleteDialog() = snap("library_delete_dialog") { Library(listOf(ready, warned), deleteAtStart = ready) }

    private val words = listOf(
        VocabularyWord("가방", "Bag"), VocabularyWord("계산하다", "To pay (the bill)"), VocabularyWord("근처", "Nearby"),
        VocabularyWord("메뉴판", "Menu"), VocabularyWord("아이스 아메리카노", "Iced americano"), VocabularyWord("포장", "Takeout"),
    )

    @Test fun allVocabulary() = snap("library_all_vocabulary", bar = false) { AllVocabularyContent(VocabularyState(words, ""), "", {}, {}, {}) }

    @Test fun allVocabularyNoMatch() = snap("library_all_vocabulary_no_match", bar = false) {
        AllVocabularyContent(VocabularyState(emptyList(), "zebra"), "zebra", {}, {}, {})
    }

    @Test fun libraryAt130Percent() = snap("library_130", fontScale = 1.3f) { Library(listOf(processing, warned, failed)) }
}
