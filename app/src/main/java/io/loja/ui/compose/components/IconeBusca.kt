package io.loja.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.loja.R

/**
 * Ícone do botão BUSCAR:
 * - EAN/SAP -> ícone de leitor (SVG).
 * - Descrição -> lupa desenhada com animação (traço que se desenha).
 */
@Composable
fun IconeBusca(porDescricao: Boolean, tint: Color, modifier: Modifier = Modifier) {
    if (!porDescricao) {
        Icon(
            painter = painterResource(R.drawable.ic_leitor),
            contentDescription = "Buscar",
            tint = tint,
            modifier = modifier,
        )
    } else {
        LupaAnimada(tint = tint, modifier = modifier)
    }
}

@Composable
private fun LupaAnimada(tint: Color, modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        // 500ms desenhando o círculo + 200ms a alça.
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = LinearEasing))
    }
    val strokeW = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(modifier = modifier) {
        val p = progress.value
        val circleFrac = (p / 0.714f).coerceIn(0f, 1f)
        val handleFrac = ((p - 0.714f) / 0.286f).coerceIn(0f, 1f)
        val s = size.minDimension / 24f

        val circle = Path().apply {
            moveTo(10.76f * s, 13.24f * s)
            relativeCubicTo(-2.34f * s, -2.34f * s, -2.34f * s, -6.14f * s, 0f, -8.49f * s)
            relativeCubicTo(2.34f * s, -2.34f * s, 6.14f * s, -2.34f * s, 8.49f * s, 0f)
            relativeCubicTo(2.34f * s, 2.34f * s, 2.34f * s, 6.14f * s, 0f, 8.49f * s)
            relativeCubicTo(-2.34f * s, 2.34f * s, -6.14f * s, 2.34f * s, -8.49f * s, 0f)
            close()
        }
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val pmCircle = PathMeasure().apply { setPath(circle, false) }
        val segCircle = Path()
        pmCircle.getSegment(0f, pmCircle.length * circleFrac, segCircle, true)
        drawPath(segCircle, color = tint, style = stroke)

        if (handleFrac > 0f) {
            val handle = Path().apply {
                moveTo(10.5f * s, 13.5f * s)
                relativeLineTo(-7.5f * s, 7.5f * s)
            }
            val pmHandle = PathMeasure().apply { setPath(handle, false) }
            val segHandle = Path()
            pmHandle.getSegment(0f, pmHandle.length * handleFrac, segHandle, true)
            drawPath(segHandle, color = tint, style = stroke)
        }
    }
}
