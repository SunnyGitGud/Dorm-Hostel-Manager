package com.example.propertymanager.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import android.content.res.Configuration

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

    fun setLanguage(context: Context, languageCode: String) {
        val currentLang = getCurrentLanguage(context)
        if (currentLang == languageCode) {
            return
        }

        with(getPreferences(context).edit()) {
            putString(PREFERRED_LANGUAGE_KEY, languageCode)
            apply()
        }
        applyLocale(languageCode)
    }

    fun applyLocale(languageCode: String) {
        val localeListCompat = if (languageCode.isNotEmpty()) {
            LocaleListCompat.forLanguageTags(languageCode)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeListCompat)
    }

    fun applyPersistedLanguage(context: Context): Context {
        val languageCode = getCurrentLanguage(context)
        applyLocale(languageCode) // Let AppCompatDelegate know

        val newLocale = Locale(languageCode)
        val newConfig = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(newLocale)
            newConfig.setLocales(localeList)
        } else {
            newConfig.setLocale(newLocale)
        }
        newConfig.setLayoutDirection(newLocale)

        return context.createConfigurationContext(newConfig)
    }
}
