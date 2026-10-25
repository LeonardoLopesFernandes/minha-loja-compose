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

        // Largura máxima (template px) para auto-ajuste
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
    private var promoLinha0 = ""
    private var promoLinha1 = ""
    private var promoLinha2 = ""

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
        promoLinha0: String = ""
    ) {
        this.codigo = codigo
        this.ean = ean
        this.descricao = descricao
        this.cifrao = cifrao
        this.valor = valor
        this.unidade = unidade
        this.promoLinha0 = promoLinha0
        this.promoLinha1 = promoLinha1
        this.promoLinha2 = promoLinha2
        invalidate()
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

        fun drawEscalado(
            texto: String,
            x: Float,
            y: Float,
            tamanho: Float,
            alinhamento: Paint.Align,
            cor: Int,
            larguraMax: Float
        ): Float {
            paint.typeface = fonte
            paint.isFakeBoldText = false
            paint.textSize = px(tamanho)
            paint.color = cor
            paint.textAlign = Paint.Align.LEFT
            val largura = paint.measureText(texto)
            val sx = if (largura > larguraMax) larguraMax / largura else 1f
            val left = when (alinhamento) {
                Paint.Align.RIGHT -> -largura
                Paint.Align.CENTER -> -largura / 2f
                else -> 0f
            }
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(sx, 1f)
            canvas.drawText(texto, left, 0f, paint)
            canvas.restore()
            return largura * sx
        }

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

        var comumValorW = 0f
        var comumCifraoX = TW / 2f

        // Preço à vista (fora da caixa preta)
        if (valor.isNotEmpty()) {
            val ehDepor = cifrao.startsWith("De R$")
            val cifraoDraw = if (ehDepor) cifrao.replaceFirst("De ", "") else cifrao
            val tam = if (modo == MODO_COMUM) 110f else 54f
            // De/Por: alinha na mesma linha da caixa preta (Por R$)
            val baseline = if (modo == MODO_COMUM) 240f else if (ehDepor) BOX_CY else 230f
            val centroX = if (modo == MODO_COMUM) TW / 2f - 80f else LEFT_CX

            if (modo == MODO_PROMOCIONAL) {
                larguraValorPx = drawEscalado(
                    valor, tx(345f), ty(baseline), 110f,
                    Paint.Align.RIGHT, 0xFF000000.toInt(), px(240f)
                )
                val maxCifrao = if (ehDepor) 30f else 45f
                val tamCifrao = ajustarTexto(cifraoDraw, maxCifrao, 26f, 100f)
                paint.typeface = fonte
                paint.textSize = px(tamCifrao)
                paint.color = 0xFF000000.toInt()
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(cifraoDraw, tx(20f), ty(baseline), paint)
            } else {
                paint.typeface = fonte
                paint.textSize = px(tam)
                paint.color = 0xFF000000.toInt()
                paint.textAlign = Paint.Align.LEFT
                val cifraoW = paint.measureText(cifraoDraw) / scale
                val valorWpx = drawEscalado(
                    valor, tx(TW - 30f), ty(baseline), tam,
                    Paint.Align.RIGHT, 0xFF000000.toInt(), px(240f)
                )
                val valorW = valorWpx / scale
                val valorLeft = (TW - 30f) - valorW
                val cifraoCenterMax = valorLeft - 10f - cifraoW / 2f
                val cifraoX = min(cifraoCenterMax, centroX)
                paint.textSize = px(tam)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(cifraoDraw, tx(cifraoX), ty(baseline), paint)
                comumValorW = valorW
                comumCifraoX = cifraoX
            }
        }

        // R$/PCE + valor unitário (promocional: separados)
        if (unidade.isNotEmpty()) {
            val numero = unidade.substringAfter("R$/PCE ").trim()
            paint.typeface = fonte
            paint.textSize = px(15f)
            paint.color = 0xFF000000.toInt()
            if (modo == MODO_PROMOCIONAL) {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("R$/PCE", tx(20f), ty(290f), paint)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    "R$" + numero, tx(345f) - larguraValorPx / 2f, ty(290f), paint
                )
            } else {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("R$/PCE", tx(comumCifraoX), ty(290f), paint)
                canvas.drawText(
                    "R$" + numero, tx(TW - 30f) - comumValorW / 2f, ty(290f), paint
                )
            }
        }

        // Caixa preta (promocional)
        if (modo == MODO_PROMOCIONAL) {
            val ehParc = promoLinha0 == "EM ATÉ"
            if (promoLinha0.isNotEmpty()) {
                val tam = ajustarTexto(promoLinha0, 17f, 10f, BOX_MAX)
                paint.typeface = fonte
                paint.isFakeBoldText = false
                paint.textSize = px(tam)
                paint.color = 0xFFFFE600.toInt()
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(promoLinha0, tx(BOX_CX), ty(BOX_CY - 44f), paint)
            }
            if (promoLinha1.isNotEmpty()) {
                drawEscalado(
                    promoLinha1,
                    tx(BOX_CX),
                    ty(if (ehParc) BOX_CY + 22f else BOX_CY + 12f),
                    75f,
                    Paint.Align.CENTER,
                    0xFFFFE600.toInt(),
                    px(310f)
                )
            }
            if (promoLinha2.isNotEmpty()) {
                val tam = ajustarTexto(promoLinha2, 17f, 10f, BOX_MAX)
                paint.typeface = fonte
                paint.isFakeBoldText = false
                paint.textSize = px(tam)
                paint.color = 0xFFFFE600.toInt()
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    promoLinha2, tx(BOX_CX), ty(if (ehParc) 268f else 252f), paint
                )
            }
        }
    }
}
