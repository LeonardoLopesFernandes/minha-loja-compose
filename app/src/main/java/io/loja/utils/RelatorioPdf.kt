package io.loja.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import io.loja.network.PriceTag
import java.io.File
import java.io.FileOutputStream

/**
 * Gera um PDF em formato de relatório/tabela com os itens da lista de etiquetas.
 * Colunas: Quantidade, EAN, Descrição e Código de Barras (EAN-13). Depois oferece
 * o compartilhamento (equivalente ao ListaPdfService do Flutter).
 */
object RelatorioPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val M = 24f
    private const val ROW_H = 44f

    // Larguras das colunas (Quantidade, EAN, Descrição-flextível, Código de Barras)
    private const val COL_QTD = 44f
    private const val COL_EAN = 120f
    private const val COL_BAR = 190f

    private const val HEADER_COLOR = 0xFFD32F2F.toInt()
    private const val BORDER_COLOR = 0xFFE0E0E0.toInt()
    private const val BAR_W = 460
    private const val BAR_H = 120

    fun gerarRelatorioEtiquetas(
        context: Context,
        itens: List<PriceTag>,
        getQtd: (Int) -> Int
    ) {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas
        var y = M
        val contentBottom = PAGE_H - M

        val paintTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 16f; isFakeBoldText = true
        }
        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 12f
        }
        val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 10f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        val paintTotal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 11f; isFakeBoldText = true
        }
        val paintHeaderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HEADER_COLOR; style = Paint.Style.FILL
        }
        val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER_COLOR; style = Paint.Style.STROKE; strokeWidth = 0.5f
        }

        fun novaPagina() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            y = M
        }

        val larguraFlex = (PAGE_W - 2 * M) - COL_QTD - COL_EAN - COL_BAR
        fun colX(i: Int): Float = when (i) {
            0 -> M
            1 -> M + COL_QTD
            2 -> M + COL_QTD + COL_EAN
            else -> M + COL_QTD + COL_EAN + larguraFlex
        }
        fun colW(i: Int): Float = when (i) {
            0 -> COL_QTD
            1 -> COL_EAN
            2 -> larguraFlex
            else -> COL_BAR
        }

        // Título
        canvas.drawText("Relatório de Etiquetas", M, y + 14f, paintTitulo)
        val sub = "${itens.size} ${if (itens.size == 1) "item" else "itens"}"
        canvas.drawText(sub, PAGE_W - M - paintSub.measureText(sub), y + 12f, paintSub)
        y += 30f

        // Cabeçalho da tabela
        canvas.drawRect(M, y, M + (PAGE_W - 2 * M), y + ROW_H, paintHeaderBg)
        val headers = arrayOf("Qtd", "COD EAN", "DESCRIÇÃO", "CÓDIGO DE BARRAS")
        for (i in headers.indices) {
            canvas.drawText(headers[i], colX(i) + colW(i) / 2f, y + ROW_H - 7f, paintHeader)
        }
        y += ROW_H

        // Linhas
        for (i in itens.indices) {
            if (y + ROW_H > contentBottom) novaPagina()
            val valores = arrayOf(
                getQtd(i).toString(),
                itens[i].ean,
                itens[i].description
            )
            for (c in 0 until 3) {
                val text = truncar(valores[c], colW(c) - 12f, paintCell)
                canvas.drawText(text, colX(c) + colW(c) / 2f, y + ROW_H - 7f, paintCell)
                canvas.drawRect(colX(c), y, colX(c) + colW(c), y + ROW_H, paintBorder)
            }
            // Código de barras do EAN
            val bx = colX(3)
            val bw = colW(3)
            canvas.drawRect(bx, y, bx + bw, y + ROW_H, paintBorder)
            val barras = criarCodigoBarras(itens[i].ean)
            if (barras != null) {
                val dst = Rect(
                    (bx + 6f).toInt(),
                    (y + 4f).toInt(),
                    (bx + bw - 6f).toInt(),
                    (y + ROW_H - 4f).toInt()
                )
                canvas.drawBitmap(barras, null, dst, null)
            } else {
                canvas.drawText(truncar(itens[i].ean, bw - 12f, paintCell), bx + bw / 2f, y + ROW_H - 7f, paintCell)
            }
            y += ROW_H
        }

        // Total
        if (y + 30f > contentBottom) novaPagina()
        val total = itens.indices.sumOf { getQtd(it) }
        canvas.drawText("Total de etiquetas: $total", M, y + 16f, paintTotal)

        doc.finishPage(page)

        val outputFile = File(context.cacheDir, "relatorio_etiquetas_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Relatório de Etiquetas")
            putExtra(Intent.EXTRA_TEXT, "Relatório de Etiquetas")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(share, "Compartilhar relatório")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    /** Renderiza o código de barras do EAN (EAN-13; se inválido, tenta CODE_128). */
    private fun criarCodigoBarras(ean: String): Bitmap? {
        if (ean.isBlank()) return null
        val formatos = listOf(BarcodeFormat.EAN_13, BarcodeFormat.CODE_128)
        for (formato in formatos) {
            try {
                val matrix = MultiFormatWriter().encode(ean, formato, BAR_W, BAR_H)
                val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(matrix.width * matrix.height)
                for (py in 0 until matrix.height) {
                    val offset = py * matrix.width
                    for (px in 0 until matrix.width) {
                        pixels[offset + px] = if (matrix[px, py]) Color.BLACK else Color.WHITE
                    }
                }
                bmp.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
                return bmp
            } catch (_: Exception) {
                // tenta o próximo formato
            }
        }
        return null
    }

    private fun truncar(texto: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(texto) <= maxWidth) return texto
        var t = texto
        while (t.length > 1 && paint.measureText(t + "…") > maxWidth) {
            t = t.dropLast(1)
        }
        return t + "…"
    }
}
