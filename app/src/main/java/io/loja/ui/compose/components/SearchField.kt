package io.loja.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.loja.ui.compose.MinhaLojaRed

private val ALTURA_BUSCA = 52.dp

/** Campo de busca com o botão BUSCAR DENTRO (à direita), altura única. */
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
        modifier = modifier
            .fillMaxWidth()
            .height(ALTURA_BUSCA)
            .border(1.dp, Color(0xFF9AA5B1), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF1E293B), fontSize = 15.sp),
            cursorBrush = SolidColor(MinhaLojaRed),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
            ),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFF8C9BA5), fontSize = 15.sp)
                }
                inner()
            },
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(MinhaLojaRed)
                .clickable(enabled = !buscando) { onBuscar() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (buscando) "..." else "BUSCAR",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}
