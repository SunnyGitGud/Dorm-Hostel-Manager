package com.example.propertymanager.utils


import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {

    private const val PREFERENCES_FILE_KEY = "com.example.propertymanager.LANGUAGE_PREFERENCES"
    private const val PREFERRED_LANGUAGE_KEY = "preferred_language"
    private const val DEFAULT_LANGUAGE_CODE = "en" // Default to English

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE)
    }

    fun getCurrentLanguage(context: Context): String {
        return getPreferences(context).getString(PREFERRED_LANGUAGE_KEY, DEFAULT_LANGUAGE_CODE) ?: DEFAULT_LANGUAGE_CODE
    }

    // This function seems like a duplicate or a rename, ensure it's what you intend.
    // If LanguagetCurrentLanguageCode is used elsewhere, it's fine. If not, it could be removed.
    fun LanguagetCurrentLanguageCode(context: Context): String { 
        return getCurrentLanguage(context)
    }


    fun setLanguage(context: Context, languageCode: String) {
        val currentLang = getCurrentLanguage(context)
        if (currentLang == languageCode) {
            return // No change needed
        }

        with(getPreferences(context).edit()) {
            putString(PREFERRED_LANGUAGE_KEY, languageCode)
            apply()
        }
        applyLocale(languageCode) 
        // The activity restart is signaled by the ViewModel and handled in PropertyScreen.
        // AppCompatDelegate.setApplicationLocales() itself will trigger necessary recreations.
    }

    fun applyLocale(languageCode: String) {
        val localeList = if (languageCode.isNotEmpty()) {
            LocaleListCompat.forLanguageTags(languageCode)
        } else {
            // Consider if LocaleListCompat.getEmptyLocaleList() or LocaleListCompat.getDefault() is more appropriate
            // if you want to revert to system default or have no specific locale.
            LocaleListCompat.getEmptyLocaleList() 
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    // Modified to return Context
    fun applyPersistedLanguage(context: Context): Context { 
        val languageCode = getCurrentLanguage(context)
        applyLocale(languageCode) // Ensures the application locale is set via AppCompatDelegate
        return context // Return the original context
    }
}
