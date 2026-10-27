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
            larguraMax: Float,
            sy: Float = 1f
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
            canvas.scale(sx, sy)
            canvas.drawText(texto, left, 0f, paint)
            canvas.restore()
            return largura * sx
        }

        fun drawTexto(
            texto: String,
            x: Float,
            y: Float,
            tamanho: Float,
            alinhamento: Paint.Align,
            cor: Int
        ) {
            paint.typeface = fonte
            paint.isFakeBoldText = true
            paint.textSize = px(tamanho)
            paint.color = cor
            paint.textAlign = alinhamento
            canvas.drawText(texto, x, y, paint)
        }

        // Valor com a vírgula em commaScale e escala vertical sy.
        fun drawValor(
            texto: String,
            x: Float,
            y: Float,
            tamanho: Float,
            alinhamento: Paint.Align,
            cor: Int,
            larguraMax: Float,
            sy: Float = 1f,
            commaScale: Float = 1f
        ): Float {
            paint.typeface = fonte
            paint.isFakeBoldText = true
            paint.color = cor
            paint.textAlign = Paint.Align.LEFT
            val partes = texto.split(",")
            fun medida(s: String, size: Float): Float {
                paint.textSize = px(size)
                return paint.measureText(s)
            }
            val wAntes = medida(if (partes.size == 2) partes[0] else texto, tamanho)
            val wVirgula = if (partes.size == 2) medida(",", tamanho * commaScale) else 0f
            val wDepois = if (partes.size == 2) medida(partes[1], tamanho) else 0f
            val total = wAntes + wVirgula + wDepois
            val sx = if (total > larguraMax) larguraMax / total else 1f
            val left = when (alinhamento) {
                Paint.Align.RIGHT -> -total
                Paint.Align.CENTER -> -total / 2f
                else -> 0f
            }
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(sx, sy)
            var cx = left
            if (partes.size == 2) {
                paint.textSize = px(tamanho)
                canvas.drawText(partes[0], cx, 0f, paint)
                cx += wAntes
                paint.textSize = px(tamanho * commaScale)
                canvas.drawText(",", cx, 0f, paint)
                cx += wVirgula
                paint.textSize = px(tamanho)
                canvas.drawText(partes[1], cx, 0f, paint)
            } else {
                paint.textSize = px(tamanho)
                canvas.drawText(texto, cx, 0f, paint)
            }
            canvas.restore()
            return total * sx
        }

        // Força a largura exata (para igualar as linhas da caixa preta).
        fun drawLarguraFixa(
            texto: String,
            x: Float,
            y: Float,
            tamanho: Float,
            alinhamento: Paint.Align,
            cor: Int,
            larguraAlvo: Float
        ) {
            if (larguraAlvo <= 0f) return
            paint.typeface = fonte
            paint.isFakeBoldText = true
            paint.textSize = px(tamanho)
            paint.color = cor
            paint.textAlign = Paint.Align.LEFT
            val largura = paint.measureText(texto)
            val sx = larguraAlvo / largura
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
        }

        var larguraValorPx = 0f
        val ehDepor = cifrao.startsWith("De R$")
        val leve = promoLinha1.contains(" por R$")

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
            val cifraoDraw = cifrao
            val tam = if (modo == MODO_COMUM) 110f else 54f
            // De/Por: alinha na mesma linha da caixa preta (Por R$)
            val baseline = if (modo == MODO_COMUM) 240f
            else if (ehDepor) BOX_CY + 38f
            else if (leve) 220f
            else 230f
            val centroX = if (modo == MODO_COMUM) TW / 2f - 80f else LEFT_CX

            if (modo == MODO_PROMOCIONAL) {
                if (leve) {
                    // R$ e valor na horizontal, esticados só na vertical (1.5x)
                    // e com a vírgula em 60% apenas no valor unitário maior.
                    paint.typeface = fonte
                    paint.textSize = px(110f)
                    val cifraoW = paint.measureText(cifraoDraw) / scale
                    val maxValor = 345f - (20f + cifraoW + 8f)
                    larguraValorPx = drawValor(
                        valor, tx(345f), ty(baseline), 110f,
                        Paint.Align.RIGHT, 0xFF000000.toInt(), px(maxValor),
                        sy = 1.5f, commaScale = 0.6f
                    )
                    drawEscalado(
                        cifraoDraw, tx(20f), ty(baseline), 110f,
                        Paint.Align.LEFT, 0xFF000000.toInt(), px(cifraoW),
                        sy = 1.5f
                    )
                } else {
                    larguraValorPx = drawValor(
                        valor, tx(345f), ty(baseline), 110f,
                        Paint.Align.RIGHT, 0xFF000000.toInt(), px(243f)
                    )
                    if (ehDepor) {
                        drawTexto("De", tx(20f), ty(baseline - 45f), 45f,
                            Paint.Align.LEFT, 0xFF000000.toInt())
                        drawTexto("R$", tx(20f), ty(baseline), 45f,
                            Paint.Align.LEFT, 0xFF000000.toInt())
                    } else {
                        drawTexto(cifraoDraw, tx(20f), ty(baseline), 45f,
                            Paint.Align.LEFT, 0xFF000000.toInt())
                    }
                }
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
            var larguraPromo1 = 0f
            if (promoLinha0.isNotEmpty()) {
                val tam = ajustarTexto(promoLinha0, 17f, 10f, BOX_MAX)
                drawTexto(promoLinha0, tx(BOX_CX), ty(BOX_CY - 44f), tam,
                    Paint.Align.CENTER, 0xFFFFFFFF.toInt())
            }
            if (ehDepor && promoLinha1.isNotEmpty()) {
                val por = promoLinha1.replaceFirst("Por R$", "").trim()
                if (por.isNotEmpty()) {
                    val esquerda = BOX_LEFT + 12f
                    val direita = BOX_RIGHT - 10f
                    val baseBox = BOX_CY + 38f
                    drawValor(por, tx(direita), ty(baseBox), 110f,
                        Paint.Align.RIGHT, 0xFFFFFFFF.toInt(), px(243f))
                    drawTexto("Por", tx(esquerda), ty(baseBox - 45f), 45f,
                        Paint.Align.LEFT, 0xFFFFFFFF.toInt())
                    drawTexto("R$", tx(esquerda), ty(baseBox), 45f,
                        Paint.Align.LEFT, 0xFFFFFFFF.toInt())
                }
            } else if (promoLinha1.isNotEmpty()) {
                larguraPromo1 = drawEscalado(
                    promoLinha1,
                    tx(BOX_CX),
                    ty(if (ehParc) BOX_CY + 22f else BOX_CY + 12f),
                    75f,
                    Paint.Align.CENTER,
                    0xFFFFFFFF.toInt(),
                    px(310f)
                )
            }
            if (promoLinha2.isNotEmpty()) {
                val tam = ajustarTexto(promoLinha2, 17f, 10f, BOX_MAX)
                if (leve && larguraPromo1 > 0f) {
                    // Mesma largura do "N por R$ ..." e um pouco mais abaixo.
                    drawLarguraFixa(promoLinha2, tx(BOX_CX), ty(256f), tam,
                        Paint.Align.CENTER, 0xFFFFFFFF.toInt(), larguraPromo1)
                } else {
                    drawTexto(promoLinha2, tx(BOX_CX),
                        ty(if (ehParc) 268f else 252f), tam,
                        Paint.Align.CENTER, 0xFFFFFFFF.toInt())
                }
            }
        }
    }
}
