package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FactoryDarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    secondary = PanelBg,
    background = MidnightBg,
    surface = PanelBg,
    onPrimary = Color.Black,
    onSecondary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

private val FactoryLightColorScheme = lightColorScheme(
    primary = Color(0xFF0091EA),      // Bright brand blue
    secondary = Color(0xFFF3F4F6),    // Elegant off-white
    background = Color(0xFFF8FAFC),   // Cozy slate canvas
    surface = Color(0xFFFFFFFF),      // Pure white panels
    onPrimary = Color.White,
    onSecondary = Color(0xFF111827),  // Dark carbon text
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FactoryDarkColorScheme else FactoryLightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
