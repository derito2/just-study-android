package com.dero.juststudykorean.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.korean.Scheduling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { System, Light, Dark }
enum class CardDirection { KoToEn, EnToKo, Both }

/** design/ajustes.md AJ-32: the most questions a quiz can ask for; a larger saved count reads as this. */
const val QUIZ_MAX_COUNT = 25

/** design/ajustes.md AJ-25: how Flashcards shows the decks; stored as `grid` | `list`. */
enum class DecksLayout { Grid, List }

/** The New quiz form (design/quiz.md A.2), kept between sessions. An empty [documentIds] means all documents. */
data class QuizForm(
    val count: Int = 5,
    val documentIds: Set<Long> = emptySet(),
    val types: Set<String> = setOf("ko_to_en", "write_ko", "vocab_recognition", "en_to_ko", "fill_blank", "multiple_choice", "grammar_selection"),
    val levels: Set<Int> = setOf(1, 2),
)

/** The user's choices, in DataStore Preferences (app private storage). */
@Singleton
class UserPrefs @Inject constructor(private val store: DataStore<Preferences>) {
    val themeMode: Flow<ThemeMode> = store.data.map { it[THEME].toEnum(ThemeMode.System) }
    val cardDirection: Flow<CardDirection> = store.data.map { it[CARD_DIRECTION].toEnum(CardDirection.KoToEn) }
    /** P-30: after a quiz is generated the form's Question types and Difficulty sections start closed. */
    val quizFormCollapsed: Flow<Boolean> = store.data.map { it[QUIZ_FORM_COLLAPSED] ?: false }
    suspend fun setQuizFormCollapsed(v: Boolean) { store.edit { it[QUIZ_FORM_COLLAPSED] = v } }
    val typeAnswer: Flow<Boolean> = store.data.map { it[TYPE_ANSWER] ?: false }
    /** Dashboard: the chart's period, and the streak seen on the last visit (for the bump when it grows). */
    val chartUnit: Flow<TimelineUnit> = store.data.map { it[CHART_UNIT].toEnum(TimelineUnit.Day) }
    val lastStreak: Flow<Int> = store.data.map { it[LAST_STREAK] ?: 0 }
    val quizForm: Flow<QuizForm> = store.data.map {
        val d = QuizForm()
        QuizForm(
            (it[QUIZ_COUNT] ?: d.count).coerceIn(1, QUIZ_MAX_COUNT),
            it[QUIZ_DOCS]?.split(",")?.mapNotNull(String::toLongOrNull)?.toSet() ?: d.documentIds,
            it[QUIZ_TYPES]?.split(",")?.filter(String::isNotEmpty)?.toSet() ?: d.types,
            it[QUIZ_LEVELS]?.split(",")?.mapNotNull(String::toIntOrNull)?.toSet() ?: d.levels,
        )
    }
    /** Review options (Again in minutes; Hard, Good and Easy in hours): the web kept them in data/flashcard_options.json. */
    val scheduling: Flow<Scheduling> = store.data.map {
        val d = Scheduling()
        Scheduling(it[AGAIN] ?: d.againMinutes, it[HARD] ?: d.hardHours, it[GOOD] ?: d.goodHours, it[EASY] ?: d.easyHours)
    }

    /** Games (design/games.md 9): the clock of the typing games; on by default. */
    val decksLayout: Flow<DecksLayout> = store.data.map { if (it[DECKS_LAYOUT] == "list") DecksLayout.List else DecksLayout.Grid }
    suspend fun setDecksLayout(layout: DecksLayout) { store.edit { it[DECKS_LAYOUT] = layout.name.lowercase() } }
    val typingTimer: Flow<Boolean> = store.data.map { it[TYPING_TIMER] ?: true }
    suspend fun setTypingTimer(on: Boolean) { store.edit { it[TYPING_TIMER] = on } }

    /** D10-3: the Korean voice says the word at the end of each Games round. On by default. */
    val gamesSpeakWord: Flow<Boolean> = store.data.map { it[GAMES_SPEAK_WORD] ?: true }
    suspend fun setGamesSpeakWord(on: Boolean) { store.edit { it[GAMES_SPEAK_WORD] = on } }

    /** R1-14: repeats you skipped ("{document}:{kind}:{text}"), not asked again when the document is reprocessed. */
    val skippedDuplicates: Flow<Set<String>> = store.data.map { it[SKIPPED_DUPLICATES].orEmpty() }
    suspend fun addSkippedDuplicates(keys: Collection<String>) { store.edit { it[SKIPPED_DUPLICATES] = it[SKIPPED_DUPLICATES].orEmpty() + keys } }

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    suspend fun setCardDirection(direction: CardDirection) { store.edit { it[CARD_DIRECTION] = direction.name } }
    suspend fun setTypeAnswer(on: Boolean) { store.edit { it[TYPE_ANSWER] = on } }
    /** F2 (design/beta.md 2.1): Fill the Gap and Listen Only in a review session; both off, the session is as before. */
    val gapCards: Flow<Boolean> = store.data.map { it[GAP_CARDS] ?: false }
    val listenCards: Flow<Boolean> = store.data.map { it[LISTEN_CARDS] ?: false }
    suspend fun setGapCards(on: Boolean) { store.edit { it[GAP_CARDS] = on } }
    suspend fun setListenCards(on: Boolean) { store.edit { it[LISTEN_CARDS] = on } }
    /** Grammar Lab (design/beta.md 3): the system keyboard instead of HangulKeys. */
    val labSystemKeyboard: Flow<Boolean> = store.data.map { it[LAB_SYSTEM_KEYBOARD] ?: false }
    suspend fun setLabSystemKeyboard(on: Boolean) { store.edit { it[LAB_SYSTEM_KEYBOARD] = on } }
    /** Listening Lab (design/beta.md 4): the mode (Dictation, Shadowing, HandsFree) and the speed, remembered. */
    val listeningMode: Flow<String> = store.data.map { it[LISTENING_MODE] ?: "Dictation" }
    val listeningRate: Flow<Float> = store.data.map { it[LISTENING_RATE] ?: 1f }
    suspend fun setListeningMode(mode: String) { store.edit { it[LISTENING_MODE] = mode } }
    suspend fun setListeningRate(rate: Float) { store.edit { it[LISTENING_RATE] = rate } }
    /** Phase 11 (H-03 B): word_state was written from the sources on v6; until then WordProgress computes on the fly. */
    val wordStateBuiltV6: Flow<Boolean> = store.data.map { it[WORD_STATE_BUILT_V6] ?: false }
    suspend fun setWordStateBuiltV6(built: Boolean) { store.edit { it[WORD_STATE_BUILT_V6] = built } }
    suspend fun setChartUnit(unit: TimelineUnit) { store.edit { it[CHART_UNIT] = unit.name } }
    suspend fun setLastStreak(days: Int) { store.edit { it[LAST_STREAK] = days } }
    suspend fun setQuizForm(f: QuizForm) {
        store.edit {
            it[QUIZ_COUNT] = f.count; it[QUIZ_DOCS] = f.documentIds.joinToString(",")
            it[QUIZ_TYPES] = f.types.joinToString(","); it[QUIZ_LEVELS] = f.levels.joinToString(",")
        }
    }
    suspend fun setScheduling(s: Scheduling) {
        store.edit { it[AGAIN] = s.againMinutes; it[HARD] = s.hardHours; it[GOOD] = s.goodHours; it[EASY] = s.easyHours }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DECKS_LAYOUT = stringPreferencesKey("decks_layout")
        val SKIPPED_DUPLICATES = stringSetPreferencesKey("skipped_duplicates")
        val GAMES_SPEAK_WORD = booleanPreferencesKey("games_speak_word")
        val TYPING_TIMER = booleanPreferencesKey("typing_timer")
        val CARD_DIRECTION = stringPreferencesKey("card_direction")
        val TYPE_ANSWER = booleanPreferencesKey("type_answer")
        val GAP_CARDS = booleanPreferencesKey("gap_cards")
        val LISTEN_CARDS = booleanPreferencesKey("listen_cards")
        val LAB_SYSTEM_KEYBOARD = booleanPreferencesKey("lab_system_keyboard")
        val LISTENING_MODE = stringPreferencesKey("listening_mode")
        val LISTENING_RATE = floatPreferencesKey("listening_rate")
        val WORD_STATE_BUILT_V6 = booleanPreferencesKey("wordStateBuiltV6")
        val CHART_UNIT = stringPreferencesKey("chart_unit")
        val LAST_STREAK = intPreferencesKey("last_streak")
        val QUIZ_FORM_COLLAPSED = booleanPreferencesKey("quiz_form_collapsed")
        val QUIZ_COUNT = intPreferencesKey("quiz_count")
        val QUIZ_DOCS = stringPreferencesKey("quiz_documents")
        val QUIZ_TYPES = stringPreferencesKey("quiz_types")
        val QUIZ_LEVELS = stringPreferencesKey("quiz_levels")
        val AGAIN = intPreferencesKey("again_minutes")
        val HARD = intPreferencesKey("hard_hours")
        val GOOD = intPreferencesKey("good_hours")
        val EASY = intPreferencesKey("easy_hours")
    }
}

// An unknown stored name (an enum renamed later) falls back to the default instead of crashing.
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default
