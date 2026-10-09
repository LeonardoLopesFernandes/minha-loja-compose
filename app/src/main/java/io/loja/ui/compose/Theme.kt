package io.loja.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta fiel ao app nativo atual (e ao Flutter).
val MinhaLojaRed = Color(0xFFC61033)
val MinhaLojaDarkRed = Color(0xFF8E0000)
val MinhaLojaGreen = Color(0xFF4CAF50)
val MinhaLojaBg = Color(0xFFF4F6F8)

private val LightColors = lightColorScheme(
    primary = MinhaLojaRed,
    onPrimary = Color.White,
    secondary = MinhaLojaDarkRed,
    onSecondary = Color.White,
    tertiary = MinhaLojaGreen,
    background = MinhaLojaBg,
    surface = Color.White,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B),
)

private val DarkColors = darkColorScheme(
    primary = MinhaLojaRed,
    secondary = MinhaLojaDarkRed,
    tertiary = MinhaLojaGreen,
)

@Composable
fun MinhaLojaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
