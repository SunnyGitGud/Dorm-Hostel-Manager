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

// Existing Light and Dark ColorSchemes
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
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

// ADDED: Custom ColorSchemes (Placeholders - customize these colors)
private val ForestLightColorScheme = lightColorScheme(
    primary = Color(0xFF4CAF50), // Green
    secondary = Color(0xFF8BC34A),
    tertiary = Color(0xFFCDDC39),
    background = Color(0xFFF1F8E9), // Light Greenish background
    surface = Color(0xFFE8F5E9)
)

private val CatppuccinLatteColorScheme = lightColorScheme(
    primary = Color(0xFFE91E63), // Pinkish
    secondary = Color(0xFFFF9800),
    tertiary = Color(0xFF795548),
    background = Color(0xFFFAF3E0), // Creamy background
    surface = Color(0xFFFFF8E1)
)

private val MidnightBlueColorScheme = darkColorScheme(
    primary = Color(0xFF3F51B5), // Indigo / Deep Blue
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF7986CB),
    background = Color(0xFF1A237E), // Very dark blue background
    surface = Color(0xFF283593)
)

@Composable
fun MyApplication3Theme(
    appTheme: AppTheme = AppTheme.SYSTEM_DEFAULT, // ADDED appTheme parameter
    dynamicColor: Boolean = true, // Dynamic color can still be an option
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (appTheme) {
        AppTheme.SYSTEM_DEFAULT -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.FOREST_LIGHT -> ForestLightColorScheme
        AppTheme.CATPPUCCIN_LATTE -> CatppuccinLatteColorScheme
        AppTheme.MIDNIGHT_BLUE -> MidnightBlueColorScheme
        // Add cases for other themes if any
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
