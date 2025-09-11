package com.example.propertymanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Renamed for clarity and consistency
private val SystemDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Renamed for clarity and consistency
private val SystemLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

// Catppuccin Themes (as defined previously)
private val CatppuccinLatteColorScheme = lightColorScheme(
    primary = Color(0xFFd20f39), // Flamingo
    secondary = Color(0xFFfe640b), // Marigold
    tertiary = Color(0xFFdc8a78), // Peach
    background = Color(0xFFeff1f5), // Base
    surface = Color(0xFFe6e9ef), // Mantle
    onPrimary = Color(0xFF4c4f69), // Text on Flamingo
    onSecondary = Color(0xFF4c4f69), // Text on Marigold
    onTertiary = Color(0xFF4c4f69), // Text on Peach
    onBackground = Color(0xFF4c4f69), // Text on Base
    onSurface = Color(0xFF4c4f69) // Text on Mantle
)

private val CatppuccinMochaColorScheme = darkColorScheme(
    primary = Color(0xFFf2cdcd), // Flamingo
    secondary = Color(0xFFf5e0dc), // Marigold (using a lighter tone for dark theme)
    tertiary = Color(0xFFe6bdf8), // Mauve
    background = Color(0xFF1e1e2e), // Base
    surface = Color(0xFF181825), // Mantle
    onPrimary = Color(0xFF1e1e2e), // Text on Flamingo (Base color)
    onSecondary = Color(0xFF1e1e2e), // Text on Marigold
    onTertiary = Color(0xFF1e1e2e), // Text on Mauve
    onBackground = Color(0xFFcdd6f4), // Text on Base
    onSurface = Color(0xFFcdd6f4) // Text on Mantle
)

// Everforest Themes (as defined previously)
private val EverforestLightColorScheme = lightColorScheme(
    primary = Color(0xFF6f8949), 
    secondary = Color(0xFFd6995b), 
    tertiary = Color(0xFF819b67), 
    background = Color(0xFFf2efdc), 
    surface = Color(0xFFe8e5d0)
)

private val EverforestDarkColorScheme = darkColorScheme(
    primary = Color(0xFFa7c080), 
    secondary = Color(0xFFdbbc7f), 
    tertiary = Color(0xFF83c092), 
    background = Color(0xFF2d353b), 
    surface = Color(0xFF353f47)
)

// Kanagawa Themes (as defined previously)
private val KanagawaLightColorScheme = lightColorScheme(
    primary = Color(0xFFc574dd), 
    secondary = Color(0xFFe67e80), 
    tertiary = Color(0xFF87a869), 
    background = Color(0xFFf2e9de), 
    surface = Color(0xFFece1d7)
)

private val KanagawaDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7e9cd8), 
    secondary = Color(0xFFe8a288), 
    tertiary = Color(0xFF98bb6c), 
    background = Color(0xFF1f1f28), 
    surface = Color(0xFF2a2a37)
)

@Composable
fun MyApplication3Theme(
    appTheme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    dynamicColor: Boolean = true, // Dynamic color can still be an option
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme() // Renamed for clarity

    val colorScheme = when (appTheme) {
        AppTheme.SYSTEM_DEFAULT -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (systemIsDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemIsDark) SystemDarkColorScheme else SystemLightColorScheme
            }
        }
        AppTheme.CATPPUCCIN_LATTE -> CatppuccinLatteColorScheme
        AppTheme.CATPPUCCIN_MOCHA -> CatppuccinMochaColorScheme
        AppTheme.EVERFOREST_LIGHT -> EverforestLightColorScheme
        AppTheme.EVERFOREST_DARK -> EverforestDarkColorScheme
        AppTheme.KANAGAWA_LIGHT -> KanagawaLightColorScheme
        AppTheme.KANAGAWA_DARK -> KanagawaDarkColorScheme
        else -> { // Fallback for any unexpected AppTheme state
            if (systemIsDark) SystemDarkColorScheme else SystemLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Assuming Typography is defined in Type.kt
        content = content
    )
}
