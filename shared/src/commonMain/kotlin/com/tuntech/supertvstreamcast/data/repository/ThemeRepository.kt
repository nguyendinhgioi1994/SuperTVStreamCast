package com.tuntech.supertvstreamcast.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tuntech.supertvstreamcast.theme.TvThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val THEME_MODE = stringPreferencesKey("themeMode")

/** The appearance chosen in Settings (System / Light / Dark), kept across launches. */
class ThemeRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(TvThemeMode.SYSTEM)
    val mode: StateFlow<TvThemeMode> = mutable.asStateFlow()

    init {
        scope.launch {
            val stored = dataStore.data.first()[THEME_MODE]
            // A choice made before the stored value arrived wins.
            mutable.compareAndSet(TvThemeMode.SYSTEM, parseThemeMode(stored))
        }
    }

    fun select(mode: TvThemeMode) {
        mutable.value = mode
        scope.launch { dataStore.edit { it[THEME_MODE] = mode.name } }
    }
}

/** Unknown or missing values follow the system. */
fun parseThemeMode(value: String?): TvThemeMode = TvThemeMode.entries.firstOrNull { it.name == value } ?: TvThemeMode.SYSTEM
