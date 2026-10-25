package io.loja.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VERDE_CONTAINER = Color(0xFF4CAF50)

/** Container verde (badge_green_container): fundo #4CAF50, cantos levemente arredondados. */
@Composable
fun GreenBadgeContainer(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(VERDE_CONTAINER, RoundedCornerShape(6.dp))
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Badge branco (badge_white_container): fundo branco, raio 30, texto colorido 10sp. */
@Composable
fun WhiteTypeBadge(texto: String, cor: Color, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(30.dp)) {
    Box(
        modifier = modifier
            .background(Color.White, shape)
            .border(1.dp, VERDE_CONTAINER, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = cor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Status badge (Impressa / Não impressa): fundo colorido, texto branco 9sp. */
@Composable
fun StatusBadge(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(18.dp)
            .background(cor, RoundedCornerShape(30.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** Campo de quantidade (avulsas): 28dp, branco com borda vermelha, raio 6. */
@Composable
fun QtyBoxAvulsa(valor: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    QtyBox(valor, onChange, 28.dp, Color.White, Color(0xFFD32F2F), modifier)
}

/** Campo de quantidade (diárias): 26dp, fundo #EDEFF2, raio 6. */
@Composable
fun QtyBoxDiaria(valor: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    QtyBox(valor, onChange, 26.dp, Color(0xFFEDEFF2), null, modifier)
}

@Composable
private fun QtyBox(
    valor: Int,
    onChange: (Int) -> Unit,
    tamanho: Dp,
    fundo: Color,
    borda: Color?,
    modifier: Modifier = Modifier,
) {
    var texto by remember { mutableStateOf(valor.toString()) }
    LaunchedEffect(valor) {
        if (texto != valor.toString()) texto = valor.toString()
    }
    Box(
        modifier = modifier
            .width(tamanho)
            .height(tamanho)
            .then(if (borda != null) Modifier.border(1.dp, borda, RoundedCornerShape(6.dp)) else Modifier)
            .background(fundo, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = texto,
            onValueChange = { t: String ->
                val limpo = t.filter { it.isDigit() }.take(3)
                texto = limpo
                onChange(limpo.toIntOrNull() ?: 1)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFF212121),
                fontSize = if (tamanho > 27.dp) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(tamanho),
        )
    }
}
