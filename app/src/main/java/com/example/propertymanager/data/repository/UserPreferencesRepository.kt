package com.example.propertymanager.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.propertymanager.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define DataStore instance at the top level
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
    }

    val selectedTheme: Flow<AppTheme> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[PreferencesKeys.SELECTED_THEME] ?: AppTheme.SYSTEM_DEFAULT.name
            try {
                AppTheme.valueOf(themeName)
            } catch (e: IllegalArgumentException) {
                AppTheme.SYSTEM_DEFAULT // Fallback if the stored name is invalid
            }
        }

    suspend fun saveThemePreference(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_THEME] = theme.name
        }
    }
}
