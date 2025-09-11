package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.propertymanager.data.repository.UserPreferencesRepository
import com.example.propertymanager.ui.theme.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    // Function to update the selected theme
    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemePreference(theme)
        }
    }
}
