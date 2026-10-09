package io.loja.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Ponto de entrada da UI em Jetpack Compose.
 * A migração das telas (Etiquetas/Papeletas/Diárias/Modelo Editável) será
 * feita de forma incremental para cá, mantendo scanner/PDF/socket nativos.
 */
@Composable
fun MinhaLojaApp() {
    MinhaLojaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Minha Loja — Jetpack Compose")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MinhaLojaAppPreview() {
    MinhaLojaApp()
}
