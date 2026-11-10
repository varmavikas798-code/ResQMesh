package com.resqnet.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Red = Color(0xFFCF3347)
val RedDark = Color(0xFFB02538)
val RedLight = Color(0xFFFDEEF0)
val RedBorder = Color(0xFFF6C9D0)
val Navy = Color(0xFF1B2A4E)
val Slate = Color(0xFF6B7488)
val Mist = Color(0xFFF7F8FB)
val Line = Color(0xFFE4E7EE)
val GreenBg = Color(0xFFDDF6E6)
val GreenText = Color(0xFF1F7A4A)
val GreenBorder = Color(0xFFA9E6C1)
val BlueBg = Color(0xFFE0E9FF)
val BlueText = Color(0xFF2A4FC4)

private val Scheme = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    secondary = Navy,
    background = Color.White,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = Mist,
    outline = Line,
    error = RedDark
)

@Composable
fun ResQNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
