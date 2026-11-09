package com.dero.juststudykorean.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.korean.Scheduling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { System, Light, Dark }
enum class CardDirection { KoToEn, EnToKo, Both }

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
    val typeAnswer: Flow<Boolean> = store.data.map { it[TYPE_ANSWER] ?: false }
    /** Dashboard: the chart's period, and the streak seen on the last visit (for the bump when it grows). */
    val chartUnit: Flow<TimelineUnit> = store.data.map { it[CHART_UNIT].toEnum(TimelineUnit.Day) }
    val lastStreak: Flow<Int> = store.data.map { it[LAST_STREAK] ?: 0 }
    val quizForm: Flow<QuizForm> = store.data.map {
        val d = QuizForm()
        QuizForm(
            it[QUIZ_COUNT] ?: d.count,
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

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    suspend fun setCardDirection(direction: CardDirection) { store.edit { it[CARD_DIRECTION] = direction.name } }
    suspend fun setTypeAnswer(on: Boolean) { store.edit { it[TYPE_ANSWER] = on } }
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
        val CARD_DIRECTION = stringPreferencesKey("card_direction")
        val TYPE_ANSWER = booleanPreferencesKey("type_answer")
        val CHART_UNIT = stringPreferencesKey("chart_unit")
        val LAST_STREAK = intPreferencesKey("last_streak")
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
