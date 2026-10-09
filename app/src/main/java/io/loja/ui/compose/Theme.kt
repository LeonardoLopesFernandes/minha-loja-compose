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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    // App sempre no tema claro (o design é claro): garante texto branco no
    // fundo vermelho e dialogs brancos com texto escuro, mesmo em dark mode.
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
