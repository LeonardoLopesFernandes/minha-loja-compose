package io.loja.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class GondolaPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MODO_COMUM = 0
        const val MODO_PROMOCIONAL = 1

        private const val TW = 746f
        private const val TH = 335f

        // Caixa preta (promocional): x 361..710, y 141..275
        private const val BOX_LEFT = 361f
        private const val BOX_RIGHT = 710f
        private const val BOX_CY = 208f
        private const val BOX_CX = (BOX_LEFT + BOX_RIGHT) / 2f

        // Área amarela à esquerda da caixa preta
        private const val LEFT_CX = BOX_LEFT / 2f

        // Larguras máximas (template px) para auto-ajuste
        private const val LEFT_MAX = 330f
        private const val COMUM_MAX = 700f
        private const val BOX_MAX = 325f
    }

    private var modo = MODO_COMUM
    private var template: Bitmap? = null
    private var codigo = ""
    private var ean = ""
    private var descricao = ""
    private var cifrao = ""
    private var valor = ""
    private var unidade = ""
    private var promoLinha1 = ""
    private var promoLinha2 = ""
    // Quando true, a linha1 (de cima) sai pequena e a linha2 (de baixo) grande
    // (usado no "EM ATÉ" pequeno + parcelamento grande).
    private var promoInvertido = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()

    // Arial Bold (fallback: sans-serif bold)
    private val fonte = Typeface.create("arial", Typeface.BOLD)

    fun setTemplate(bmp: Bitmap?, modo: Int) {
        this.template = bmp
        this.modo = modo
        invalidate()
    }

    fun setData(
        codigo: String,
        ean: String,
        descricao: String,
        cifrao: String,
        valor: String,
        unidade: String,
        promoLinha1: String,
        promoLinha2: String,
        promoInvertido: Boolean = false
    ) {
        this.codigo = codigo
        this.ean = ean
        this.descricao = descricao
        this.cifrao = cifrao
        this.valor = valor
        this.unidade = unidade
        this.promoLinha1 = promoLinha1
        this.promoLinha2 = promoLinha2
        this.promoInvertido = promoInvertido
        invalidate()
    }

    private fun ajustarValor(cifrao: String, valor: String, maxTam: Float, minTam: Float, maxLargura: Float): Float {
        var tam = maxTam
        val gap = 6f
        while (tam > minTam) {
            paint.textSize = tam
            val w = paint.measureText(cifrao) + gap + paint.measureText(valor)
            if (w <= maxLargura) return tam
            tam -= 1f
        }
        return tam
    }

    private fun ajustarTexto(texto: String, maxTam: Float, minTam: Float, maxLargura: Float): Float {
        var tam = maxTam
        while (tam > minTam) {
            paint.textSize = tam
            if (paint.measureText(texto) <= maxLargura) return tam
            tam -= 1f
        }
        return tam
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = template ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return

        val scale = min(vw / TW, vh / TH)
        val dw = TW * scale
        val dh = TH * scale
        val ox = (vw - dw) / 2f
        val oy = (vh - dh) / 2f

        src.set(0, 0, t.width, t.height)
        dst.set(ox, oy, ox + dw, oy + dh)
        canvas.drawBitmap(t, src, dst, paint)

        fun px(p: Float) = p * scale
        fun tx(x: Float) = ox + x * scale
        fun ty(y: Float) = oy + y * scale

        var larguraValorPx = 0f

        // Código lateral (SAP) na vertical (promocional: mais ao topo)
        if (codigo.isNotEmpty()) {
            paint.typeface = fonte
            paint.textSize = px(22f)
            paint.color = 0xFF000000.toInt()
            paint.textAlign = Paint.Align.CENTER
            val sapY = if (modo == MODO_PROMOCIONAL) 100f else TH / 2f
            canvas.save()
            canvas.rotate(-90f, tx(18f), ty(sapY))
            canvas.drawText(codigo, tx(18f), ty(sapY), paint)
            canvas.restore()
        }

        // EAN
        if (ean.isNotEmpty()) {
            paint.typeface = fonte
            paint.textSize = px(23f)
            paint.color = 0xFF000000.toInt()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(ean, tx(TW / 2f), ty(82f), paint)
        }

        // Descrição
        if (descricao.isNotEmpty()) {
            paint.typeface = fonte
            paint.textSize = px(30f)
            paint.color = 0xFF000000.toInt()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(descricao, tx(TW / 2f), ty(114f), paint)
        }

        // Preço à vista (fora da caixa preta) — Arial Extra Bold
        if (valor.isNotEmpty()) {
            val maxTam = if (modo == MODO_COMUM) 85f else 54f
            val minTam = if (modo == MODO_COMUM) 40f else 26f
            val maxLargura = if (modo == MODO_COMUM) COMUM_MAX else LEFT_MAX
            val tam = ajustarValor(cifrao, valor, maxTam, minTam, maxLargura)
            val ehDepor = cifrao.startsWith("De R$")
            // De/Por: alinha na mesma linha da caixa preta (Por R$)
            val baseline = if (modo == MODO_COMUM) 220f else if (ehDepor) BOX_CY else 230f
            val centroX = if (modo == MODO_COMUM) TW / 2f else LEFT_CX

            paint.typeface = fonte
            paint.isFakeBoldText = true
            paint.color = 0xFF000000.toInt()

            if (modo == MODO_PROMOCIONAL) {
                // R$ perto do SAP (esquerda) e valor grande quase na caixa preta (direita).
                // Milhar (X.XXX,XX) ou De/Por ficam menores para não estourar.
                val temMilhar = valor.contains('.')
                val parteInteira = valor.substringBefore(',').replace(".", "")
                // De/Por e valores longos ficam menores: milhar em 60, 5 dígitos/De/Por em 75
                val maxValor = when {
                    temMilhar -> 60f
                    ehDepor || parteInteira.length >= 3 -> 75f
                    else -> 110f
                }
                val tamValor = ajustarTexto(valor, maxValor, 50f, 280f)
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = px(tamValor)
                canvas.drawText(valor, tx(345f), ty(baseline), paint)
                larguraValorPx = paint.measureText(valor)

                val maxCifrao = if (ehDepor && (temMilhar || parteInteira.length >= 3)) 30f else 45f
                val tamCifrao = ajustarTexto(cifrao, maxCifrao, 26f, 100f)
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = px(tamCifrao)
                canvas.drawText(cifrao, tx(20f), ty(baseline), paint)
            } else {
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = px(tam)
                val cw = paint.measureText(cifrao)
                val vw2 = paint.measureText(valor)
                val gap = px(6f)
                val total = cw + gap + vw2
                val startX = tx(centroX) - total / 2f

                canvas.drawText(cifrao, startX, ty(baseline), paint)
                canvas.drawText(valor, startX + cw + gap, ty(baseline), paint)
            }
            paint.isFakeBoldText = false
        }

        // R$/PCE + valor unitário (promocional: separados)
        if (unidade.isNotEmpty()) {
            paint.typeface = fonte
            paint.textSize = px(15f)
            paint.color = 0xFF000000.toInt()
            if (modo == MODO_PROMOCIONAL) {
                val numero = unidade.substringAfter("R$/PCE ").trim()
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("R$/PCE", tx(30f), ty(262f), paint)
                canvas.drawText("R$" + numero, tx(345f) - larguraValorPx, ty(262f), paint)
            } else {
                paint.textAlign = Paint.Align.CENTER
                val ux = tx(TW / 2f)
                canvas.drawText(unidade, ux, ty(252f), paint)
            }
        }

        // Caixa preta (promocional)
        if (modo == MODO_PROMOCIONAL) {
            if (promoLinha1.isNotEmpty()) {
                val tam = if (promoInvertido) ajustarTexto(promoLinha1, 17f, 10f, BOX_MAX)
                else ajustarTexto(promoLinha1, 54f, 24f, BOX_MAX)
                paint.typeface = fonte
                paint.isFakeBoldText = true
                paint.textSize = px(tam)
                paint.color = 0xFFFFE600.toInt()
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(promoLinha1, tx(BOX_CX), ty(BOX_CY), paint)
                paint.isFakeBoldText = false
            }
            if (promoLinha2.isNotEmpty()) {
                val tam = ajustarTexto(promoLinha2, 17f, 10f, BOX_MAX)
                paint.typeface = fonte
                paint.textSize = px(tam)
                paint.color = 0xFFFFE600.toInt()
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(promoLinha2, tx(BOX_CX), ty(252f), paint)
            }
        }
    }
}
