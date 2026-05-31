package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FactoryDarkColorScheme,
        typography = Typography,
        content = content
    )
}
