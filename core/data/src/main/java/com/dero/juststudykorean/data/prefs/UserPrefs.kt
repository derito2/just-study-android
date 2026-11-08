package com.dero.juststudykorean.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { System, Light, Dark }
enum class CardDirection { KoToEn, EnToKo, Both }

/** The user's choices, in DataStore Preferences (app private storage). */
@Singleton
class UserPrefs @Inject constructor(private val store: DataStore<Preferences>) {
    val themeMode: Flow<ThemeMode> = store.data.map { it[THEME].toEnum(ThemeMode.System) }
    val cardDirection: Flow<CardDirection> = store.data.map { it[CARD_DIRECTION].toEnum(CardDirection.KoToEn) }
    val typeAnswer: Flow<Boolean> = store.data.map { it[TYPE_ANSWER] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    suspend fun setCardDirection(direction: CardDirection) { store.edit { it[CARD_DIRECTION] = direction.name } }
    suspend fun setTypeAnswer(on: Boolean) { store.edit { it[TYPE_ANSWER] = on } }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val CARD_DIRECTION = stringPreferencesKey("card_direction")
        val TYPE_ANSWER = booleanPreferencesKey("type_answer")
    }
}

// An unknown stored name (an enum renamed later) falls back to the default instead of crashing.
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default
