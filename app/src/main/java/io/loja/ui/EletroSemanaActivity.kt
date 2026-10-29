package io.loja.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.loja.R
import io.loja.network.PapeletaPrintingData
import io.loja.network.SessionManager
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.ToastUtils
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EletroSemanaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SIZE = "eletro_size"
        var dadosPendentesList: MutableList<PapeletaPrintingData>? = null
        private const val PRINTER_PORT = 9100
    }

    private data class FormEletro(
        val data: PapeletaPrintingData,
        val descricao: EditText,
        val parcelas: EditText,
        val valorParcela: EditText,
        val de: EditText,
        val por: EditText,
        val inicio: EditText,
        val fim: EditText,
    )

    private lateinit var sessionManager: SessionManager
    private lateinit var ivPreview: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var layoutForm: LinearLayout
    private lateinit var btnPreview: Button
    private lateinit var btnImprimir: Button
    private lateinit var btnShare: Button

    private val forms = mutableListOf<FormEletro>()
    private val paginas = mutableListOf<Bitmap>()
    private var overlay: Bitmap? = null
    private var cols = 2
    private var rows = 2
    private var sizeLabel = "4X1"

    private val typefaceBold: Typeface by lazy {
        try {
            Typeface.createFromAsset(assets, "fonts/SFProDisplay-Bold.otf")
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    private val typefaceRegular: Typeface by lazy {
        try {
            Typeface.createFromAsset(assets, "fonts/SFProDisplay-Regular.otf")
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eletro_semana)
        sessionManager = SessionManager(this)
        sessionManager.setCurrentActivity(this)

        ivPreview = findViewById(R.id.ivPreviewEletro)
        tvStatus = findViewById(R.id.tvStatusEletro)
        layoutForm = findViewById(R.id.layoutEletroForm)
        btnPreview = findViewById(R.id.btnGerarPreviewEletro)
        btnImprimir = findViewById(R.id.btnImprimirEletro)
        btnShare = findViewById(R.id.btnShareEletro)

        findViewById<TextView>(R.id.btnVoltarEletro).setOnClickListener { finish() }

        sizeLabel = intent.getStringExtra(EXTRA_SIZE)?.uppercase(Locale.getDefault()) ?: "4X1"
        when (sizeLabel) {
            "1X1" -> { cols = 1; rows = 1 }
            "2X1" -> { cols = 2; rows = 1 }
            "4X1" -> { cols = 2; rows = 2 }
            else -> { cols = 2; rows = 2; sizeLabel = "4X1" }
        }

        overlay = try {
            assets.open("overlays/semana.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
        if (overlay == null) {
            ToastUtils.show(this, "Overlay semana.png não encontrado")
            finish()
            return
        }

        val lista = dadosPendentesList
        dadosPendentesList = null
        if (lista.isNullOrEmpty()) {
            ToastUtils.show(this, "Dados não disponíveis")
            finish()
            return
        }
        lista.forEachIndexed { index, data -> adicionarForm(index, data) }

        btnPreview.setOnClickListener { gerarPreview() }
        btnImprimir.setOnClickListener { imprimir() }
        btnShare.setOnClickListener { compartilhar() }

        gerarPreview()
    }

    override fun onDestroy() {
        paginas.forEach { if (!it.isRecycled) it.recycle() }
        paginas.clear()
        overlay?.recycle()
        overlay = null
        super.onDestroy()
    }

    private fun adicionarForm(index: Int, data: PapeletaPrintingData) {
        val view = layoutInflater.inflate(R.layout.item_eletro_semana, layoutForm, false)
        view.findViewById<TextView>(R.id.tvItemEletro).text =
            "ITEM ${index + 1} - ELETRO DA SEMANA"
        val descricao = view.findViewById<EditText>(R.id.etDescricaoEletro)
        val parcelas = view.findViewById<EditText>(R.id.etParcelasEletro)
        val valorParcela = view.findViewById<EditText>(R.id.etValorParcelaEletro)
        val de = view.findViewById<EditText>(R.id.etDeEletro)
        val por = view.findViewById<EditText>(R.id.etPorEletro)
        val inicio = view.findViewById<EditText>(R.id.etInicioEletro)
        val fim = view.findViewById<EditText>(R.id.etFimEletro)

        descricao.setText(data.productName)
        parcelas.setText(((data.installmentQuantity ?: 0).takeIf { it > 0 } ?: 10).toString())
        parcelas.inputType = InputType.TYPE_CLASS_NUMBER
        valorParcela.setText(formatInput(data.installmentPrice))
        valorParcela.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        de.setText(formatInput(data.price))
        de.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        por.setText(formatInput(data.promotionPrice))
        por.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        aplicarMascaraData(inicio)
        aplicarMascaraData(fim)

        forms.add(
            FormEletro(data, descricao, parcelas, valorParcela, de, por, inicio, fim)
        )
        layoutForm.addView(view)
    }

    private fun aplicarMascaraData(et: EditText) {
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        et.addTextChangedListener(object : TextWatcher {
            private var formatando = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null || formatando) return
                val digits = s.toString().filter { it.isDigit() }.take(8)
                formatando = true
                s.clear()
                when (digits.length) {
                    in 1..2 -> s.append(digits)
                    in 3..4 -> s.append(digits.substring(0, 2)).append("/").append(digits.substring(2))
                    else -> s.append(digits.substring(0, 2)).append("/")
                        .append(digits.substring(2, 4)).append("/")
                        .append(digits.substring(4))
                }
                formatando = false
            }
        })
    }

    private fun coletarDados(): List<PapeletaPrintingData> = forms.map { f ->
        f.data.copy(
            template = Constants.SIGN_TEMPLATE_ELETRO_SEMANA,
            productName = f.descricao.text.toString().trim(),
            price = parseMoney(f.de.text.toString()) ?: 0.0,
            promotionPrice = parseMoney(f.por.text.toString()),
            installmentQuantity = f.parcelas.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 10,
            installmentPrice = parseMoney(f.valorParcela.text.toString()),
            quantity = 1,
            size = Constants.SIGN_SIZE_1X1,
        )
    }

    private fun parseMoney(value: String): Double? {
        val cleaned = value.replace("R\$", "").replace(" ", "").trim()
        if (cleaned.isEmpty()) return null
        val normalized =
            if (cleaned.contains(",")) cleaned.replace(".", "").replace(",", ".")
            else cleaned
        return normalized.toDoubleOrNull()
    }

    private fun formatInput(value: Double?): String {
        if (value == null || value <= 0) return ""
        return String.format(Locale("pt", "BR"), "%.2f", value)
    }

    private fun money(value: Double?): String {
        if (value == null || value <= 0) return "R$ --"
        return "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)
    }

    private fun dataOuPlaceholder(value: String): String =
        value.trim().ifEmpty { "--/--/----" }

    private fun gerarPreview() {
        val dados = coletarDados()
        if (dados.isEmpty()) return
        btnPreview.isEnabled = false
        tvStatus.text = "Gerando pré-visualização..."
        lifecycleScope.launch {
            val novas = withContext(Dispatchers.Default) { renderizarPaginas(dados, false) }
            withContext(Dispatchers.Main) {
                btnPreview.isEnabled = true
                if (novas.isEmpty()) {
                    tvStatus.text = "Não foi possível gerar a pré-visualização"
                    return@withContext
                }
                paginas.forEach { if (!it.isRecycled) it.recycle() }
                paginas.clear()
                paginas.addAll(novas)
                ivPreview.setImageBitmap(paginas.first())
                tvStatus.text = "Pré-visualização ${sizeLabel} · página 1/${paginas.size}"
            }
        }
    }

    private fun renderizarPaginas(
        dados: List<PapeletaPrintingData>,
        impressao: Boolean,
    ): List<Bitmap> {
        val overlayBmp = overlay ?: return emptyList()
        val landscape = cols > rows
        val pageW = if (impressao) { if (landscape) 3508 else 2480 } else { if (landscape) 1200 else 850 }
        val pageH = if (impressao) { if (landscape) 2480 else 3508 } else { if (landscape) 850 else 1200 }
        val cellW = pageW / cols
        val cellH = pageH / rows
        val gridCap = cols * rows
        val totalPages = ceil(dados.size / gridCap.toDouble()).toInt().coerceAtLeast(1)
        val result = mutableListOf<Bitmap>()
        for (p in 0 until totalPages) {
            val page = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(page)
            canvas.drawColor(Color.WHITE)
            val start = p * gridCap
            val end = minOf(start + gridCap, dados.size)
            for (idx in start until end) {
                val local = idx - start
                val col = local % cols
                val row = local / cols
                val left = col * cellW
                val top = row * cellH
                val ov = Bitmap.createScaledBitmap(overlayBmp, cellW, cellH, true)
                canvas.drawBitmap(ov, left.toFloat(), top.toFloat(), null)
                ov.recycle()
                val f = forms[idx]
                desenharConteudo(
                    canvas,
                    dados[idx],
                    dataOuPlaceholder(f.inicio.text.toString()),
                    dataOuPlaceholder(f.fim.text.toString()),
                    left.toFloat(),
                    top.toFloat(),
                    cellW.toFloat(),
                    cellH.toFloat(),
                )
            }
            result.add(page)
        }
        return result
    }

    private fun desenharConteudo(
        canvas: Canvas,
        data: PapeletaPrintingData,
        inicio: String,
        fim: String,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
    ) {
        val cx = left + w / 2f
        val contentW = w * 0.80f
        val dark = Color.rgb(38, 39, 41)
        val titleColor = Color.rgb(49, 51, 53)

        desenharTitulo(
            canvas,
            data.productName.ifBlank { "PRODUTO" }.uppercase(Locale.getDefault()),
            cx,
            top + h * 0.315f,
            contentW,
            h * 0.14f,
            w * 0.095f,
            titleColor,
        )

        val qtd = (data.installmentQuantity ?: 0).takeIf { it > 0 } ?: 10
        val paintQtd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            typeface = typefaceBold
            textSize = w * 0.15f
        }
        val paintPreco = Paint(paintQtd)
        val gap = w * 0.035f
        val qtdTexto = "${qtd}x"
        val precoTexto = money(data.installmentPrice)
        var instSize = w * 0.15f
        while (true) {
            paintQtd.textSize = instSize
            paintPreco.textSize = instSize
            val total = paintQtd.measureText(qtdTexto) + gap + paintPreco.measureText(precoTexto)
            if (total <= contentW * 0.95f || instSize <= 8f) break
            instSize -= 1f
        }
        val qtdW = paintQtd.measureText(qtdTexto)
        val precoW = paintPreco.measureText(precoTexto)
        val groupW = qtdW + gap + precoW
        val qtdX = cx - groupW / 2f
        val precoX = qtdX + qtdW + gap
        val instY = top + h * 0.49f
        desenharTextoY(canvas, qtdTexto, qtdX, instY, paintQtd)
        desenharTextoY(canvas, precoTexto, precoX, instY, paintPreco)

        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            typeface = typefaceBold
            textSize = max(8f, instSize * 0.35f)
        }
        while (paintSub.measureText("sem juros") > qtdW && paintSub.textSize > 6f) {
            paintSub.textSize -= 1f
        }
        desenharTextoY(
            canvas,
            "sem juros",
            qtdX + (qtdW - paintSub.measureText("sem juros")) / 2f,
            top + h * 0.545f,
            paintSub,
        )

        val dividerY = top + h * 0.61f
        canvas.drawLine(
            left + w * 0.13f,
            dividerY,
            left + w * 0.87f,
            dividerY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(91, 92, 94)
                strokeWidth = w * 0.002f
            },
        )

        val deTexto = "De " + money(data.price)
        val porTexto = "Por " + money(data.promotionPrice)
        val paintCash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            typeface = typefaceBold
            textSize = w * 0.06f
        }
        var cashSize = w * 0.06f
        while (paintCash.measureText(deTexto) + w * 0.04f + paintCash.measureText(porTexto) > contentW &&
            cashSize > 6f
        ) {
            cashSize -= 1f
            paintCash.textSize = cashSize
        }
        val gapCash = w * 0.04f
        val deW = paintCash.measureText(deTexto)
        val porW = paintCash.measureText(porTexto)
        val cashGroupW = deW + gapCash + porW
        val deX = cx - cashGroupW / 2f
        val cashY = top + h * 0.68f
        desenharTextoY(canvas, deTexto, deX, cashY, paintCash)
        val strikeY = cashY + (paintCash.fontMetrics.ascent + paintCash.fontMetrics.descent) / 2f
        canvas.drawLine(deX, strikeY, deX + deW, strikeY, Paint(paintCash).apply { strokeWidth = max(1f, cashSize * 0.08f) })
        desenharTextoY(canvas, porTexto, deX + deW + gapCash, cashY, paintCash)

        val validade = "*Oferta válida de $inicio até $fim"
        val paintVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(74, 75, 77)
            typeface = typefaceRegular
            textSize = w * 0.03f
            textAlign = Paint.Align.CENTER
        }
        while (paintVal.measureText(validade) > contentW && paintVal.textSize > 6f) {
            paintVal.textSize -= 1f
        }
        val validadeY = top + h * 0.84f
        val fm = paintVal.fontMetrics
        canvas.drawText(validade, cx, validadeY - (fm.ascent + fm.descent) / 2f, paintVal)
    }

    private fun desenharTitulo(
        canvas: Canvas,
        texto: String,
        cx: Float,
        cy: Float,
        maxWidth: Float,
        maxHeight: Float,
        startSize: Float,
        color: Int,
    ) {
        var size = startSize
        lateinit var layout: StaticLayout
        while (true) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                typeface = typefaceBold
                textSize = size
            }
            layout = StaticLayout.Builder.obtain(
                texto, 0, texto.length, paint, maxWidth.toInt()
            ).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(3)
                .build()
            if ((layout.height <= maxHeight && layout.lineCount <= 3) || size <= 6f) break
            size -= 1f
        }
        canvas.save()
        canvas.translate(cx - maxWidth / 2f, cy - layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun desenharTextoY(canvas: Canvas, texto: String, x: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(texto, x, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun gerarPdf(): File? {
        val dados = coletarDados()
        if (dados.isEmpty()) return null
        val pages = renderizarPaginas(dados, true)
        if (pages.isEmpty()) return null
        val doc = PdfDocument()
        val landscape = cols > rows
        val pageW = if (landscape) 842 else 595
        val pageH = if (landscape) 595 else 842
        pages.forEachIndexed { index, bitmap ->
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
            val page = doc.startPage(info)
            val m = 14f
            page.canvas.drawBitmap(
                bitmap,
                null,
                RectF(m, m, pageW - m, pageH - m),
                null,
            )
            doc.finishPage(page)
            bitmap.recycle()
        }
        val file = File(cacheDir, "eletro_semana_${System.currentTimeMillis()}.pdf")
        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            file
        } catch (e: Exception) {
            LogHelper.e("EletroSemana: erro PDF", e)
            null
        } finally {
            doc.close()
        }
    }

    private fun imprimir() {
        btnImprimir.isEnabled = false
        tvStatus.text = "Gerando PDF..."
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { gerarPdf() }
            if (file == null) {
                withContext(Dispatchers.Main) {
                    btnImprimir.isEnabled = true
                    tvStatus.text = "Erro ao gerar PDF"
                    ToastUtils.show(this@EletroSemanaActivity, "Erro ao gerar PDF")
                }
                return@launch
            }
            try {
                enviarParaImpressora(file.readBytes())
                withContext(Dispatchers.Main) {
                    btnImprimir.isEnabled = true
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnImprimir.isEnabled = true
                    tvStatus.text = "Erro ao imprimir"
                    ToastUtils.show(this@EletroSemanaActivity, "Erro: ${e.message}")
                }
            }
        }
    }

    private suspend fun enviarParaImpressora(pdfBytes: ByteArray) {
        val printerIp = try {
            sessionManager.getPrinterIp()
        } catch (_: Exception) {
            SessionManager.DEFAULT_PRINTER_IP
        }
        withContext(Dispatchers.IO) {
            val socket = java.net.Socket()
            socket.tcpNoDelay = true
            socket.connect(java.net.InetSocketAddress(printerIp, PRINTER_PORT), 3000)
            socket.soTimeout = 10000
            val out = java.io.BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            out.write(pdfBytes)
            out.flush()
            Thread.sleep(500)
            try { out.close() } catch (_: Exception) {}
            try { socket.shutdownOutput() } catch (_: Exception) {}
            Thread.sleep(200)
            socket.close()
        }
        withContext(Dispatchers.Main) {
            ToastUtils.show(this@EletroSemanaActivity, "PDF enviado para impressora")
        }
    }

    private fun compartilhar() {
        btnShare.isEnabled = false
        tvStatus.text = "Gerando PDF..."
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { gerarPdf() }
            withContext(Dispatchers.Main) {
                btnShare.isEnabled = true
                if (file == null) {
                    tvStatus.text = "Erro ao gerar PDF"
                    return@withContext
                }
                try {
                    val uri = FileProvider.getUriForFile(
                        this@EletroSemanaActivity,
                        "${packageName}.fileprovider",
                        file,
                    )
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Compartilhar PDF com",
                        )
                    )
                } catch (e: Exception) {
                    ToastUtils.show(this@EletroSemanaActivity, "Erro: ${e.message}")
                }
            }
        }
    }
}
