package io.loja.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/**
 * Lixeira com efeito de "desenho" (stroke surgindo), espelhando o SVG:
 * corpo (0-0.5s), tampa (0.5-0.8s) e detalhes (0.8-1.0s).
 */
@Composable
fun TrashDrawIcon(
    animate: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(animate) {
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(1000))
        }
    }

    val body1 = remember {
        Path().apply {
            moveTo(12f, 20f); lineTo(17f, 20f)
            cubicTo(17.5f, 20f, 18f, 19.5f, 18f, 19f)
            lineTo(18f, 5f)
        }
    }
    val body2 = remember {
        Path().apply {
            moveTo(12f, 20f); lineTo(7f, 20f)
            cubicTo(6.5f, 20f, 6f, 19.5f, 6f, 19f)
            lineTo(6f, 5f)
        }
    }
    val lid = remember { Path().apply { moveTo(4f, 5f); lineTo(20f, 5f) } }
    val det1 = remember { Path().apply { moveTo(10f, 4f); lineTo(14f, 4f) } }
    val det2 = remember { Path().apply { moveTo(10f, 9f); lineTo(10f, 16f) } }
    val det3 = remember { Path().apply { moveTo(14f, 9f); lineTo(14f, 16f) } }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        scale(s, pivot = Offset(size.width / 2f, size.height / 2f)) {
            val p = progress.value
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            fun drawFrom(path: Path, fraction: Float) {
                if (fraction <= 0f) return
                val pm = PathMeasure()
                pm.setPath(path, false)
                val dest = Path()
                pm.getSegment(0f, pm.length * fraction, dest, true)
                drawPath(dest, color = tint, style = stroke)
            }

            val bodyFrac = (p / 0.5f).coerceIn(0f, 1f)
            val lidFrac = ((p - 0.5f) / 0.3f).coerceIn(0f, 1f)
            val detFrac = ((p - 0.8f) / 0.2f).coerceIn(0f, 1f)

            drawFrom(body1, bodyFrac)
            drawFrom(body2, bodyFrac)
            drawFrom(lid, lidFrac)
            drawFrom(det1, detFrac)
            drawFrom(det2, detFrac)
            drawFrom(det3, detFrac)
        }
    }
}
