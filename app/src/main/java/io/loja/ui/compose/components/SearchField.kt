package io.loja.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.loja.ui.compose.MinhaLojaRed

private val ALTURA_BUSCA = 56.dp

/** Campo de busca + botão BUSCAR, com a MESMA altura. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    numerico: Boolean,
    buscando: Boolean,
    onBuscar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(ALTURA_BUSCA),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            placeholder = { Text(placeholder, color = Color(0xFF8C9BA5)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
            ),
        )
        Button(
            onClick = onBuscar,
            enabled = !buscando,
            modifier = Modifier.height(ALTURA_BUSCA),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MinhaLojaRed, contentColor = Color.White),
        ) {
            Text(if (buscando) "BUSCANDO..." else "BUSCAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
