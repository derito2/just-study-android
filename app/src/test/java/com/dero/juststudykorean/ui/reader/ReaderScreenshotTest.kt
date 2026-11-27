package com.dero.juststudykorean.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.reader.ReaderLine
import com.dero.juststudykorean.data.reader.ReaderPage
import com.dero.juststudykorean.korean.reader.Gloss
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/beta.md 5: the Reader's list, a page and its sheets, at 411 dp (over a tab: no bottom bar). Synthetic text only. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class ReaderScreenshotTest {
    private fun doc(id: Long, name: String, pages: Long) = DocumentsEntity(id, name, "$id.pdf", "sha$id", pages, "ready", true, 0, 0, 100, null, null, 0, null)
    private val docs = listOf(doc(2, "Vocab_cafe_y_comida.pdf", 1), doc(1, "Gramatica_semana_14.pdf", 3))
    private val page = ReaderPage(1, listOf(
        ReaderLine("Vocabulary: cafés and food", true),
        ReaderLine("1. 메뉴판 = Menu", false),
        ReaderLine("메뉴판 좀 주세요. = Can I have the menu, please?", false),
        ReaderLine("저는 학교에서 커피를 마셨어요.", false),
    ))
    private val school = Gloss.Entry("학교", "School", "Vocab_cafe_y_comida.pdf", listOf("학교에 가요." to "I go to school."))
    private val none = SheetActions({}, {}, {}, {}, {}, {}, {})

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { JustStudyTheme(darkTheme = dark) { content() } }

    @Composable
    private fun Reader(ui: ReaderUi) = ReaderContent(ui, {}, {}, {}, {}, none)

    @Composable
    private fun Sheet(content: @Composable () -> Unit) = Column(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }

    private val list = ReaderUi(loading = false, documents = docs)
    private val reading = list.copy(document = docs[0], pages = listOf(page, page.copy(page = 2)))
    private val gloss = ReaderUi(gloss = Gloss.Inflected("학교에서", "학교", school, "에서"), added = "Added to Reader.")
    private val ai = SentenceUi("저는 학교에서 커피를 마셨어요.", explanation = "저는 is the topic, 학교에서 the place and 마셨어요 the verb in the past, at the end.")

    @Test fun documents() = snap("reader_documents", false) { Reader(list) }
    @Test fun documentsDark() = snap("reader_documents_dark", true) { Reader(list) }
    @Test fun page() = snap("reader_page", false) { Reader(reading) }
    @Test fun pageDark() = snap("reader_page_dark", true) { Reader(reading) }
    @Test fun word() = snap("reader_word", false) { Sheet { GlossSheet(gloss, none) } }
    @Test fun wordDark() = snap("reader_word_dark", true) { Sheet { GlossSheet(gloss, none) } }
    @Test fun notFound() = snap("reader_notfound", false) {
        Sheet { GlossSheet(ReaderUi(gloss = Gloss.NotFound("비행기", listOf("비행기가 늦었어요. = The plane was late."))), none) }
    }
    @Test fun decks() = snap("reader_decks", false) {
        Sheet { GlossSheet(gloss.copy(added = null, decks = listOf(Deck(-1, true, false, emptyList(), "Reader", 3, 3, 0, 3, 3, null, null, null))), none) }
    }
    @Test fun sentence() = snap("reader_sentence", false) { Sheet { SentenceSheet(ai, none) } }
    @Test fun sentenceDark() = snap("reader_sentence_dark", true) { Sheet { SentenceSheet(ai, none) } }
    @Test fun sentenceWords() = snap("reader_sentence_words", false) {
        Sheet { SentenceSheet(ai.copy(explanation = null, glosses = listOf("저는" to NOT_IN, "학교에서" to "학교 · School", "커피를" to "커피 · Coffee")), none) }
    }

    private companion object { const val NOT_IN = "Not in your notes." }
}
