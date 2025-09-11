package com.example.propertymanager.ui.theme

enum class AppTheme(val displayName: String) {
    SYSTEM_DEFAULT("System Default"),

    CATPPUCCIN_LATTE("Catppuccin Latte"),
    CATPPUCCIN_MOCHA("Catppuccin Mocha"), // Dark variant

    EVERFOREST_LIGHT("Everforest Light"),
    EVERFOREST_DARK("Everforest Dark"),   // Dark variant

    KANAGAWA_LIGHT("Kanagawa Light"),
    KANAGAWA_DARK("Kanagawa Dark");      // Dark variant

    // Helper to get the base theme for the dialog (usually the light variant or system default)
    fun getBaseForDialog(): AppTheme {
        return when (this) {
            CATPPUCCIN_MOCHA -> CATPPUCCIN_LATTE
            EVERFOREST_DARK -> EVERFOREST_LIGHT
            KANAGAWA_DARK -> KANAGAWA_LIGHT
            else -> this // For base themes themselves or SYSTEM_DEFAULT
        }
    }

    // Helper to determine if this theme is a custom theme with a toggleable dark/light variant
    fun isCustomToggleable(): Boolean {
        return this in listOf(
            CATPPUCCIN_LATTE, CATPPUCCIN_MOCHA,
            EVERFOREST_LIGHT, EVERFOREST_DARK,
            KANAGAWA_LIGHT, KANAGAWA_DARK
        )
    }

    // Helper to get the opposite variant (light -> dark, dark -> light)
    fun oppositeVariant(): AppTheme? {
        return when (this) {
            CATPPUCCIN_LATTE -> CATPPUCCIN_MOCHA
            CATPPUCCIN_MOCHA -> CATPPUCCIN_LATTE
            EVERFOREST_LIGHT -> EVERFOREST_DARK
            EVERFOREST_DARK -> EVERFOREST_LIGHT
            KANAGAWA_LIGHT -> KANAGAWA_DARK
            KANAGAWA_DARK -> KANAGAWA_LIGHT
            else -> null // SYSTEM_DEFAULT or themes without a defined opposite
        }
    }
}

// List of themes to be shown in the ThemeSelectionDialog
val dialogThemes = listOf(
    AppTheme.SYSTEM_DEFAULT,
    AppTheme.CATPPUCCIN_LATTE, // Representing the Catppuccin family
    AppTheme.EVERFOREST_LIGHT,  // Representing the Everforest family
    AppTheme.KANAGAWA_LIGHT    // Representing the Kanagawa family
)
