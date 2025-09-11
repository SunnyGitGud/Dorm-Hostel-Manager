package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.repository.UserPreferencesRepository
import com.example.propertymanager.ui.theme.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first // ADDED for getting current value of StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val userPreferencesRepository: UserPreferencesRepository) : ViewModel() {

    // StateFlow to hold the currently selected theme
    val selectedTheme: StateFlow<AppTheme> = userPreferencesRepository.selectedTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Stop collecting after 5s of no subscribers
            initialValue = AppTheme.SYSTEM_DEFAULT // Default theme
        )

    // Function to update the selected theme (when chosen from dialog)
    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            // The dialog should always pass a "base" theme (like CATPPUCCIN_LATTE).
            // We save this directly. toggleDarkMode handles switching to its dark variant.
            userPreferencesRepository.saveThemePreference(theme)
        }
    }

    // ADDED: Function to toggle dark mode for custom themes
    fun toggleDarkMode() {
        viewModelScope.launch {
            val currentTheme = selectedTheme.first() // Get current value from StateFlow
            if (currentTheme.isCustomToggleable()) {
                currentTheme.oppositeVariant()?.let { newTheme ->
                    userPreferencesRepository.saveThemePreference(newTheme)
                }
            }
            // If currentTheme is SYSTEM_DEFAULT, this function does nothing,
            // as dark mode is controlled by the system.
            // The UI in SettingsDrawer already reflects this.
        }
    }
}
