package io.loja.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import io.loja.network.PriceTag
import java.io.File
import java.io.FileOutputStream

/**
 * Gera um PDF em formato de relatório/tabela com os itens da lista de etiquetas.
 * Colunas: Quantidade, EAN, SAP, Descrição. Depois oferece o compartilhamento
 * (equivalente ao ListaPdfService do Flutter).
 */
object RelatorioPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val M = 24f
    private const val ROW_H = 22f

    // Larguras das colunas (Quantidade, EAN, SAP, Descrição-flextível)
    private const val COL_QTD = 80f
    private const val COL_EAN = 120f
    private const val COL_SAP = 90f

    private const val HEADER_COLOR = 0xFFD32F2F.toInt()
    private const val BORDER_COLOR = 0xFFE0E0E0.toInt()

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
        }
        val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 10f
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

        val larguraFlex = (PAGE_W - 2 * M) - COL_QTD - COL_EAN - COL_SAP
        fun colX(i: Int): Float = when (i) {
            0 -> M
            1 -> M + COL_QTD
            2 -> M + COL_QTD + COL_EAN
            else -> M + COL_QTD + COL_EAN + COL_SAP
        }
        fun colW(i: Int): Float = when (i) {
            0 -> COL_QTD
            1 -> COL_EAN
            2 -> COL_SAP
            else -> larguraFlex
        }

        // Título
        canvas.drawText("Relatório de Etiquetas", M, y + 14f, paintTitulo)
        val sub = "${itens.size} ${if (itens.size == 1) "item" else "itens"}"
        canvas.drawText(sub, PAGE_W - M - paintSub.measureText(sub), y + 12f, paintSub)
        y += 30f

        // Cabeçalho da tabela
        canvas.drawRect(M, y, M + (PAGE_W - 2 * M), y + ROW_H, paintHeaderBg)
        val headers = arrayOf("Quantidade", "EAN", "SAP", "Descrição")
        for (i in headers.indices) {
            canvas.drawText(headers[i], colX(i) + 6f, y + ROW_H - 7f, paintHeader)
        }
        y += ROW_H

        // Linhas
        for (i in itens.indices) {
            if (y + ROW_H > contentBottom) novaPagina()
            val valores = arrayOf(
                getQtd(i).toString(),
                itens[i].ean,
                itens[i].sap,
                itens[i].description
            )
            for (c in 0 until 4) {
                val text = truncar(valores[c], colW(c) - 12f, paintCell)
                canvas.drawText(text, colX(c) + 6f, y + ROW_H - 7f, paintCell)
                canvas.drawRect(colX(c), y, colX(c) + colW(c), y + ROW_H, paintBorder)
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

    private fun truncar(texto: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(texto) <= maxWidth) return texto
        var t = texto
        while (t.length > 1 && paint.measureText(t + "…") > maxWidth) {
            t = t.dropLast(1)
        }
        return t + "…"
    }
}