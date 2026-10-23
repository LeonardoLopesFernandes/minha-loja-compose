package io.loja.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
import kotlin.math.min

/**
 * Ícone desenhado por partes (barras + "a"), preenchendo de branco cada parte
 * assim que termina de ser traçada. O quadrado externo não é desenhado.
 */
class LogoAnimadoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val partes: List<Path>
    private val medidas: List<PathMeasure>
    private val comprimentos: List<Float>
    private val total: Float
    private var progresso = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val animator: ValueAnimator

    init {
        val matriz = Matrix().apply {
            postTranslate(0f, 2048f)
            postScale(0.1f, -0.1f)
        }
        partes = listOf(BARRA_TOPO, LETRA_A, BARRA_BAIXO).map { d ->
            PathParser.createPathFromPathData(d).apply {
                transform(matriz)
                fillType = Path.FillType.EVEN_ODD
            }
        }
        medidas = partes.map { PathMeasure(it, false) }
        comprimentos = medidas.map { it.length }
        total = comprimentos.sum()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progresso = it.animatedValue as Float
                invalidate()
            }
        }
    }

    fun iniciar() {
        if (!animator.isStarted) animator.start()
    }

    fun parar() {
        if (animator.isStarted) animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        iniciar()
    }

    override fun onDetachedFromWindow() {
        parar()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (total <= 0f || width == 0 || height == 0) return
        val bounds = RectF()
        var primeiro = true
        for (p in partes) {
            val b = RectF()
            p.computeBounds(b, true)
            if (primeiro) {
                bounds.set(b)
                primeiro = false
            } else {
                bounds.union(b)
            }
        }
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val s = min(width / bounds.width(), height / bounds.height())
        val dx = (width - bounds.width() * s) / 2f - bounds.left * s
        val dy = (height - bounds.height() * s) / 2f - bounds.top * s

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(s, s)

        val drawP = (progresso / 0.85f).coerceIn(0f, 1f)
        var restante = total * drawP
        for (i in partes.indices) {
            val len = comprimentos[i]
            if (restante >= len) {
                canvas.drawPath(partes[i], fillPaint)
            }
            if (restante > 0f) {
                val destino = Path()
                medidas[i].getSegment(0f, min(restante, len), destino, true)
                canvas.drawPath(destino, strokePaint)
            }
            restante -= len
        }
        canvas.restore()
    }

    companion object {
        private const val BARRA_TOPO =
            "M16674 19124 c8 -20 8 -2398 0 -2418 -6 -15 -556 -16 -6135 -16 l-6129 0 0 1218 c0 670 3 1222 7 1225 3 4 2761 7 6129 7 5573 0 6122 -1 6128 -16z"
        private const val LETRA_A =
            "M11350 14527 c400 -31 679 -64 970 -115 194 -35 468 -115 690 -204 41 -16 88 -35 105 -41 126 -50 444 -232 541 -310 262 -211 431 -391 547 -584 167 -276 266 -569 297 -882 6 -67 10 -932 10 -2475 0 -2260 6 -2817 30 -3076 14 -145 38 -372 50 -470 5 -41 12 -100 15 -130 6 -61 30 -186 45 -240 44 -161 87 -275 146 -393 19 -37 34 -69 34 -72 0 -3 -552 -4 -1226 -3 -1079 3 -1228 5 -1234 18 -7 13 -51 166 -79 275 -27 107 -66 342 -77 468 -3 42 -9 77 -13 77 -4 0 -25 -18 -46 -41 -61 -63 -241 -218 -330 -285 -335 -252 -692 -424 -1110 -534 -66 -18 -140 -38 -165 -45 -60 -18 -268 -62 -355 -75 -348 -53 -530 -70 -830 -77 -419 -9 -742 23 -1130 113 -38 9 -81 18 -95 21 -55 11 -287 83 -335 104 -11 5 -43 17 -71 28 -63 24 -279 135 -354 181 -185 116 -330 231 -470 376 -338 350 -537 763 -624 1294 -47 286 -47 771 0 1040 28 161 93 390 144 515 34 82 45 106 85 185 50 102 151 259 224 350 150 187 402 408 586 516 111 64 136 78 290 153 194 95 334 147 535 201 210 56 270 71 360 90 58 12 168 35 245 51 191 41 382 74 590 104 55 8 116 17 135 20 19 3 114 16 210 30 170 23 237 31 520 60 247 26 324 35 390 44 36 6 88 13 115 16 60 7 293 50 390 71 166 37 204 47 275 70 203 67 431 188 534 283 70 65 137 180 173 296 25 83 24 398 -1 530 -57 287 -175 493 -364 634 -242 179 -530 244 -1134 258 -223 5 -285 3 -385 -12 -259 -38 -485 -117 -705 -247 -125 -74 -299 -279 -385 -453 -79 -161 -138 -382 -145 -545 l-3 -55 -555 -6 c-305 -3 -856 -7 -1223 -8 l-668 -1 6 78 c34 470 158 880 378 1257 169 289 441 580 757 812 85 62 243 163 256 163 2 0 23 12 47 27 23 15 103 57 177 94 187 92 252 120 425 183 140 50 175 61 395 123 232 64 702 137 1125 173 265 23 947 21 1265 -3z M12036 9781 c-122 -96 -289 -163 -579 -235 -236 -58 -502 -106 -662 -121 -38 -3 -99 -10 -135 -16 -36 -5 -130 -18 -210 -29 -329 -44 -432 -63 -615 -110 -66 -18 -133 -35 -150 -39 -92 -24 -294 -101 -383 -147 -197 -102 -307 -191 -417 -338 -154 -205 -215 -407 -215 -707 0 -336 93 -582 291 -776 83 -80 129 -113 236 -167 253 -126 532 -178 983 -180 268 -1 417 16 645 75 132 34 190 55 365 136 301 138 583 426 728 743 89 195 128 343 168 635 27 201 32 1317 5 1314 -3 0 -28 -17 -55 -38z"
        private const val BARRA_BAIXO =
            "M16675 3148 c3 -13 4 -513 3 -1113 l-3 -1090 -6132 -3 -6133 -2 0 1115 0 1115 6130 0 6130 0 5 -22z"
    }
}
