package io.loja.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.loja.R
import io.loja.network.ApiClient
import io.loja.network.PapeletaPrintingData
import io.loja.network.SendPriceSignRequest
import kotlin.math.ceil
import io.loja.network.SessionManager
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.SessionExpiredHandler
import io.loja.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ModeloEditavelActivity : AppCompatActivity() {

    private val helveticaBold by lazy {
        try {
            android.graphics.Typeface.createFromAsset(assets, "fonts/HelveticaNeue-Bold.ttf")
        } catch (_: Exception) { android.graphics.Typeface.DEFAULT_BOLD }
    }

    private val helveticaBlack by lazy {
        try {
            android.graphics.Typeface.createFromAsset(assets, "fonts/HelveticaNeue-Black.ttf")
        } catch (_: Exception) { android.graphics.Typeface.DEFAULT_BOLD }
    }

    private lateinit var ivPreview: ImageView
    private lateinit var tvPreviewStatus: TextView
    private lateinit var layoutPageIndicator: android.widget.LinearLayout
    private lateinit var tvPageIndicator: TextView
    private lateinit var btnGerarPreview: Button
    private lateinit var btnCompartilhar: Button
    private lateinit var btnSharePdf: Button
    private lateinit var btnVoltar: Button

    private lateinit var etCopiaQuantidade: EditText

    private lateinit var sessionManager: SessionManager
    private var overlayBitmap: Bitmap? = null
    private var semOverlay = false
    private var mostrarCheckboxOverlay = false

    private var multiMode = false
    private var multiDataList: MutableList<PapeletaPrintingData> = mutableListOf()
    private var multiPreviewReady = false
    private var colsQuadrantes = 2
    private var rowsQuadrantes = 2
    private var sizeLabel = "4x1"
    private var enviarApi = false
    private var apiSemOverlay = false
    private var modoEditavel = false
    private var modoVencimentos = false
    private var hideGerarPreview = false
    private var previewHeight = 0
    private val multiValidades: MutableList<String> = mutableListOf()
    private val multiQuantidades: MutableList<Int> = mutableListOf()
    private val paginasPreview = mutableListOf<Bitmap>()
    private var paginaPreviewAtual = 0
    private var statusPreviewBase = ""
    private var apiQualidadeInfo: String? = null
    private var gestureDetector: android.view.GestureDetector? = null
    private val previewPdfCache: HashMap<Int, ByteArray> = HashMap()
    private val previewCardCache: HashMap<String, Bitmap> = HashMap()
    private val previewBitmapCache: HashMap<String, List<Bitmap>> = HashMap()
    private var lastPreviewKey: String? = null

    companion object {
        @JvmStatic var dadosPendentesList: MutableList<PapeletaPrintingData>? = null
        const val EXTRA_SIZE = "size"
        const val EXTRA_SEM_OVERLAY = "sem_overlay"
        const val EXTRA_ENVIAR_API = "enviar_api"
        const val EXTRA_MODO_EDITAVEL = "modo_editavel"
        const val EXTRA_MODO_VENCIMENTOS = "modo_vencimentos"
        const val EXTRA_HIDE_GERAR_PREVIEW = "hide_gerar_preview"
        const val EXTRA_QUANTIDADES = "quantidades"
        const val EXTRA_MOSTRAR_CHECKBOX_OVERLAY = "mostrar_checkbox_overlay"
        const val EXTRA_API_SEM_OVERLAY = "api_sem_overlay"
        private const val PRINTER_PORT = 9100
    }

    // Suspend: precisa concluir o envio ANTES de a activity dar finish(),
    // senão a coroutine do lifecycleScope é cancelada e nada é impresso.
    private suspend fun enviarParaImpressora(pdfBytes: ByteArray) {
        val printerIp = try {
            sessionManager.getPrinterIp()
        } catch (_: Exception) {
            SessionManager.DEFAULT_PRINTER_IP
        }
        try {
            val socket = java.net.Socket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = 256 * 1024
            socket.connect(java.net.InetSocketAddress(printerIp, PRINTER_PORT), 3000)
            socket.soTimeout = 10000
            val outputStream = java.io.BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            outputStream.write(pdfBytes)
            outputStream.flush()
            // Aguarda o TCP transmitir todos os bytes antes de encerrar:
            // fechar imediatamente podia truncar o PDF (startxref ausente).
            try { Thread.sleep(500) } catch (_: InterruptedException) {}
            try { outputStream.close() } catch (_: Exception) {}
            try { socket.shutdownOutput() } catch (_: Exception) {}
            try { Thread.sleep(200) } catch (_: InterruptedException) {}
            socket.close()
            withContext(Dispatchers.Main) { ToastUtils.show(this@ModeloEditavelActivity, "PDF enviado para impressora") }
        } catch (e: Exception) {
            LogHelper.e("ModeloEditavel: erro impressora", e)
            withContext(Dispatchers.Main) { ToastUtils.show(this@ModeloEditavelActivity, "Erro ao enviar para impressora: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_modelo_editavel)

        sessionManager = SessionManager(this)
        sessionManager.setCurrentActivity(this)

        ivPreview = findViewById(R.id.ivPreview)
        tvPreviewStatus = findViewById(R.id.tvPreviewStatus)
        layoutPageIndicator = findViewById(R.id.layoutPageIndicator)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        btnGerarPreview = findViewById(R.id.btnGerarPreview)
        btnCompartilhar = findViewById(R.id.btnCompartilhar)
        btnSharePdf = findViewById(R.id.btnSharePdf)
        btnVoltar = findViewById(R.id.btnVoltar)

        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 80 && kotlin.math.abs(velocityX) > 250) {
                    if (dx > 0) navegarPaginaPreview(1) else navegarPaginaPreview(-1)
                    return true
                }
                return false
            }
        })
        ivPreview.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> (v.parent as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> (v.parent as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(false)
            }
            gestureDetector?.onTouchEvent(event); true
        }

        etCopiaQuantidade = findViewById(R.id.etCopiaQuantidade)

        semOverlay = intent.getBooleanExtra(EXTRA_SEM_OVERLAY, false)
        enviarApi = intent.getBooleanExtra(EXTRA_ENVIAR_API, false)
        apiSemOverlay = intent.getBooleanExtra(EXTRA_API_SEM_OVERLAY, false)
        modoEditavel = intent.getBooleanExtra(EXTRA_MODO_EDITAVEL, false)
        modoVencimentos = intent.getBooleanExtra(EXTRA_MODO_VENCIMENTOS, false)
        hideGerarPreview = intent.getBooleanExtra(EXTRA_HIDE_GERAR_PREVIEW, false)
        mostrarCheckboxOverlay = intent.getBooleanExtra(EXTRA_MOSTRAR_CHECKBOX_OVERLAY, false)
        val cbOverlay = findViewById<android.widget.CheckBox>(R.id.cbOverlay)
        val tvOverlayLabel = findViewById<android.widget.TextView>(R.id.tvOverlayLabel)
        if (mostrarCheckboxOverlay) {
            findViewById<View>(R.id.layoutOverlay)?.visibility = View.VISIBLE
            cbOverlay.isChecked = !semOverlay
            tvOverlayLabel.text = if (semOverlay) "Sem modelo" else "Com modelo"
            tvOverlayLabel.setOnClickListener { cbOverlay.isChecked = !cbOverlay.isChecked }
            cbOverlay.setOnCheckedChangeListener { _, isChecked ->
                tvOverlayLabel.text = if (isChecked) "Com modelo" else "Sem modelo"
                if (semOverlay == !isChecked) return@setOnCheckedChangeListener
                semOverlay = !isChecked
                if (semOverlay) {
                    overlayBitmap?.recycle()
                    overlayBitmap = null
                } else {
                    carregarOverlayAssets()
                }
                gerarPreviewMulti()
            }
        }

        val list = dadosPendentesList
        if (list != null && list.isNotEmpty()) {
            multiMode = true
            dadosPendentesList = null
            multiDataList = list
            multiValidades.clear()
            multiValidades.addAll(List(list.size) { "" })
            multiQuantidades.clear()
            val qtdsExtra = intent.getStringExtra(EXTRA_QUANTIDADES)?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            if (qtdsExtra.size == list.size) multiQuantidades.addAll(qtdsExtra.map { it.coerceAtLeast(1) })
            else multiQuantidades.addAll(List(list.size) { 1 })
        } else {
            ToastUtils.show(this, "Dados não disponíveis")
            finish()
            return
        }

        carregarOverlayAssets()
        val sizeExtra = intent.getStringExtra(EXTRA_SIZE)?.uppercase()
        when (sizeExtra) {
            "1X1" -> { colsQuadrantes = 1; rowsQuadrantes = 1 }
            "2X1" -> { colsQuadrantes = 2; rowsQuadrantes = 1 }
            "4X1" -> { colsQuadrantes = 2; rowsQuadrantes = 2 }
            "6X1" -> { colsQuadrantes = 3; rowsQuadrantes = 2 }
            else -> { colsQuadrantes = 2; rowsQuadrantes = 2 }
        }
        sizeLabel = sizeExtra?.lowercase() ?: "4x1"

        findViewById<TextView>(R.id.tvTituloModelo).text =
            if (modoVencimentos) "PAPELETA DE VENCIMENTOS" else "PAPELETA ${sizeLabel.uppercase()}"

        // Mesmo preview do Flutter: ~55% da tela.
        previewHeight = (resources.displayMetrics.heightPixels * 0.55).toInt()
        ivPreview.layoutParams.height = previewHeight

        if (multiMode) {
            findViewById<View>(R.id.tvEditarItens)?.visibility = if (modoEditavel) View.VISIBLE else View.GONE
            btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
            if (modoEditavel) {
                findViewById<View>(R.id.layoutMultiForm)?.visibility = View.VISIBLE
                setupMultiForm()
                btnGerarPreview.setOnClickListener { gerarPreviewMulti() }
                btnCompartilhar.text = "IMPRIMIR"
                btnCompartilhar.setOnClickListener { compartilharPdfMulti() }
                btnSharePdf.visibility = View.VISIBLE
                btnSharePdf.isEnabled = false
                btnSharePdf.setOnClickListener { compartilharPdfMulti(shareMode = true) }
                btnVoltar.setOnClickListener { finish() }
            } else {
                btnSharePdf.visibility = View.VISIBLE
                btnSharePdf.isEnabled = false
                if (hideGerarPreview) {
                    btnGerarPreview.visibility = View.GONE
                } else {
                    btnGerarPreview.setOnClickListener { gerarPreviewMulti() }
                }
                btnCompartilhar.text = "IMPRIMIR"
                btnCompartilhar.setOnClickListener { compartilharPdfMulti() }
                btnSharePdf.setOnClickListener { compartilharPdfMulti(shareMode = true) }
                btnVoltar.setOnClickListener { finish() }
                gerarPreviewMulti()
            }
        }
    }

    override fun onDestroy() {
        overlayBitmap?.recycle()
        overlayBitmap = null
        super.onDestroy()
    }


    private fun carregarOverlayAssets() {
        if (semOverlay && !modoVencimentos) return
        val size = intent.getStringExtra(EXTRA_SIZE) ?: "4X1"
        val nome = size.lowercase().replace("x", "x")
        var inputStream: java.io.InputStream? = null
        try { inputStream = assets.open("overlays/$nome.png") } catch (_: Exception) {}
        if (inputStream == null) { try { inputStream = assets.open("overlays/$nome.jpg") } catch (_: Exception) {} }
        if (inputStream != null) {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inDither = false }
            overlayBitmap = BitmapFactory.decodeStream(inputStream, null, opts)
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    // ─── Multi-mode Editável: dynamic form sections per item ──────

     private fun formatarPrecoEdit(valor: Double): String {
        return if (valor == valor.toLong().toDouble()) valor.toLong().toString()
        else String.format(java.util.Locale("pt", "BR"), "%.2f", valor)
    }

    private fun setupMultiForm() {
        val container = findViewById<android.widget.LinearLayout>(R.id.layoutMultiForm)
        container.removeAllViews()

        multiDataList.forEachIndexed { index, data ->
            container.addView(criarFormSection(index, data))
        }
    }

    private fun criarFormSection(index: Int, data: PapeletaPrintingData): View {
        val hasInstallment = data.installmentQuantity != null && data.installmentQuantity > 0
        val hasPromo = data.promotionPrice != null && data.promotionPrice > 0
        val hasTake = data.takeAndWinQuantity != null && data.takeAndWinQuantity > 0
        val ehComum = !hasPromo && !hasTake && !hasInstallment
        val tipoLabel = if (ehComum) "Normal" else "Promocional"

        val card = layoutInflater.inflate(R.layout.item_editavel_card, null)

        card.findViewById<TextView>(R.id.tv_item_header).text = "ITEM ${index + 1} - $tipoLabel"

        val etNome = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_nome)
        etNome.setText(data.productName)
        etNome.aoAlterarTexto { multiDataList[index] = multiDataList[index].copy(productName = it.ifEmpty { data.productName }) }

        val tilValidade = card.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_validade)
        val etValidade = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_validade)
        if (modoVencimentos) {
            tilValidade.visibility = View.VISIBLE
            etValidade.setText(multiValidades.getOrElse(index) { "" })
            etValidade.inputType = android.text.InputType.TYPE_CLASS_TEXT
            etValidade.filters = arrayOf(android.text.InputFilter.LengthFilter(10))
            etValidade.addTextChangedListener(object : android.text.TextWatcher {
                private var isFormatting = false
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s == null || isFormatting) return
                    val digits = s.toString().filter { it.isDigit() }.take(8)
                    isFormatting = true
                    s.clear()
                    when (digits.length) {
                        in 1..2 -> s.append(digits)
                        in 3..4 -> s.append(digits.substring(0, 2)).append("/").append(digits.substring(2))
                        in 5..8 -> s.append(digits.substring(0, 2)).append("/").append(digits.substring(2, 4)).append("/").append(digits.substring(4))
                    }
                    isFormatting = false
                }
            })
            etValidade.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val t = etValidade.text?.toString() ?: ""
                    val digits = t.filter { it.isDigit() }.take(8)
                    if (digits.length == 8) etValidade.setText("${digits.substring(0,2)}/${digits.substring(2,4)}/${digits.substring(4,8)}")
                }
            }
            etValidade.aoAlterarTexto { if (index < multiValidades.size) multiValidades[index] = it }
        }

        val rowPrecos = card.findViewById<android.widget.LinearLayout>(R.id.row_precos)
        val tilPrecoDe = card.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_preco_de)
        val etPrecoDe = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_preco_de)
        val tilPrecoPromo = card.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_preco_promo)
        val etPrecoPromo = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_preco_promo)
        val rowLeve = card.findViewById<android.widget.LinearLayout>(R.id.row_leve)
        val tilQtdLeve = card.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_qtd_leve)
        val etQtdLeve = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_qtd_leve)
        val tilPrecoLeve = card.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_preco_leve)
        val etPrecoLeve = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_preco_leve)
        val tilPrecoComum = card.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_preco_comum)
        val etPrecoComum = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_preco_comum)
        val etEan = card.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_ean)

        listOf(etPrecoDe, etPrecoPromo, etPrecoLeve, etPrecoComum).forEach { et ->
            et.inputType = android.text.InputType.TYPE_CLASS_TEXT
            et.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789,.")
        }

        if (ehComum) {
            tilPrecoComum.visibility = View.VISIBLE
            etPrecoComum.setText(formatarPrecoEdit(data.price))
            etPrecoComum.aoAlterarTexto { multiDataList[index] = multiDataList[index].copy(price = it.toDoubleBr() ?: data.price) }
        } else {
            rowPrecos.visibility = View.VISIBLE
            val labelPreco = when {
                modoVencimentos && hasPromo && !hasTake -> "Preço de"
                hasPromo -> "Preço De"
                else -> "Preço Unitário"
            }
            tilPrecoDe.hint = labelPreco
            etPrecoDe.setText(formatarPrecoEdit(data.price))
            etPrecoDe.aoAlterarTexto { multiDataList[index] = multiDataList[index].copy(price = it.toDoubleBr() ?: data.price) }
            tilPrecoPromo.hint = if (modoVencimentos) "Preço promocional" else "Preço Promocional"
            etPrecoPromo.setText(data.promotionPrice?.let { formatarPrecoEdit(it) } ?: "")
            etPrecoPromo.aoAlterarTexto { multiDataList[index] = multiDataList[index].copy(promotionPrice = it.toDoubleBr() ?: data.promotionPrice) }
        }

        if (!ehComum && hasTake || (hasInstallment && !ehComum)) {
            rowLeve.visibility = View.VISIBLE
            val labelQtd = when {
                modoVencimentos && hasTake -> "Qtd leve"
                hasInstallment -> "Parcela (Qtd)"
                else -> "Leve (Qtd)"
            }
            val labelValor = when {
                modoVencimentos && hasTake -> "Preço Leve e ganhe"
                hasInstallment -> "Valor da parcela"
                else -> "Preço Leve"
            }
            tilQtdLeve.hint = labelQtd
            etQtdLeve.setText(if (hasInstallment) data.installmentQuantity?.toString() ?: "" else data.takeAndWinQuantity?.toString() ?: "")
            etQtdLeve.aoAlterarTexto { v ->
                multiDataList[index] = if (hasInstallment) multiDataList[index].copy(installmentQuantity = v.toIntOrNull()) else multiDataList[index].copy(takeAndWinQuantity = v.toIntOrNull())
            }
            tilPrecoLeve.hint = labelValor
            etPrecoLeve.setText(if (hasInstallment) data.installmentPrice?.let { formatarPrecoEdit(it) } ?: "" else data.takeAndWinPrice?.let { formatarPrecoEdit(it) } ?: "")
            etPrecoLeve.aoAlterarTexto { v ->
                multiDataList[index] = if (hasInstallment) multiDataList[index].copy(installmentPrice = v.toDoubleBr()) else multiDataList[index].copy(takeAndWinPrice = v.toDoubleBr())
            }
            if (hasInstallment) {
                // Ao digitar o nº de parcelas, calcula o valor da parcela.
                etQtdLeve.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val parcelas = s.toString().toIntOrNull() ?: return
                        if (parcelas <= 0) return
                        val base = data.promotionPrice?.takeIf { it > 0 } ?: data.price
                        if (base > 0) etPrecoLeve.setText(formatarPrecoEdit(base / parcelas))
                    }
                })
            }
        }

        etEan.setText(data.ean)
        etEan.aoAlterarTexto { multiDataList[index] = multiDataList[index].copy(ean = it) }

        return card
    }

    private fun criarCampoEditavel(label: String, valor: String,
                                   inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
                                   maxLines: Int = 1): com.google.android.material.textfield.TextInputLayout {
        val til = com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = Color.WHITE
            setBoxStrokeColorStateList(ContextCompat.getColorStateList(context, R.color.tint_editavel_stroke)!!)
            setBoxStrokeWidth(dpInt(1))
            setBoxStrokeWidthFocused(dpInt(2))
            hintTextColor = ContextCompat.getColorStateList(context, R.color.tint_editavel_hint)
            hint = label
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpInt(10) }
        }
        val et = com.google.android.material.textfield.TextInputEditText(this).apply {
            id = View.generateViewId()
            setText(valor)
            this.inputType = inputType
            setTextColor(Color.parseColor("#111111"))
            textSize = 14f
            if (maxLines > 1) {
                setSingleLine(false)
                setMaxLines(maxLines)
            } else {
                setSingleLine(true)
            }
        }
        til.addView(et)
        return til
    }

    private fun criarGrupo(label: String, valor: String, isDecimal: Boolean, onChange: (String) -> Unit): View {
        val til = com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = Color.WHITE
            setBoxStrokeColorStateList(ContextCompat.getColorStateList(context, R.color.tint_editavel_stroke)!!)
            setBoxStrokeWidth(dpInt(1))
            setBoxStrokeWidthFocused(dpInt(2))
            hintTextColor = ContextCompat.getColorStateList(context, R.color.tint_editavel_hint)
            hint = label
        }
        val et = com.google.android.material.textfield.TextInputEditText(this).apply {
            id = View.generateViewId(); setText(valor)
            setTextColor(Color.parseColor("#111111"))
            textSize = 14f
            if (isDecimal) {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789,.")
            } else inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        et.aoAlterarTexto { onChange(it) }
        til.addView(et)
        return til
    }

    private fun dpInt(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun String.toDoubleBr(): Double? =
        replace(",", ".").toDoubleOrNull()

    private fun EditText.aoAlterarTexto(callback: (String) -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                callback(s?.toString() ?: "")
            }
        })
    }

    private var multiPdfBytesCache: MutableList<ByteArray>? = null

    // ─── Multi-mode (4×1 — API preview per item, composited on overlay) ──────

    private fun removerBrancoSuave(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width; val h = result.height
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)
        for (j in pixels.indices) {
            val a = (pixels[j] shr 24) and 0xFF
            if (a < 200) continue
            val r = (pixels[j] shr 16) and 0xFF
            val g = (pixels[j] shr 8) and 0xFF
            val b = pixels[j] and 0xFF
            val minRGB = minOf(r, g, b)
            if (minRGB >= 200) {
                val t = ((minRGB - 200) / 55f).coerceIn(0f, 1f)
                val newA = (a * (1f - t * 0.95f)).toInt().coerceIn(0, 255)
                if (newA < 4) pixels[j] = Color.TRANSPARENT
                else pixels[j] = Color.argb(newA, r, g, b)
            }
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun renderPdfToBitmap(pdfBytes: ByteArray, targetW: Int, targetH: Int, maxScale: Int = 8): Bitmap? {
        return try {
            val pdfFile = File(cacheDir, "preview_multi_${System.currentTimeMillis()}_${pdfBytes.hashCode()}.pdf")
            pdfFile.outputStream().use { it.write(pdfBytes) }
            val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            var result: Bitmap? = null
            if (renderer.pageCount > 0) {
                renderer.openPage(0).use { page ->
                    val scale = minOf(
                        ceil(targetW.toFloat() / page.width).toInt(),
                        ceil(targetH.toFloat() / page.height).toInt(),
                        maxScale
                    ).coerceAtLeast(1)
                    val renderWidth = (page.width * scale).coerceAtMost(7200)
                    val renderHeight = (page.height.toFloat() * (renderWidth.toFloat() / page.width.toFloat())).toInt()
                    val pdfBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    result = removerBrancoSuave(pdfBitmap)
                    pdfBitmap.recycle()
                }
            }
            renderer.close(); fd.close(); pdfFile.delete()
            result
        } catch (e: Exception) {
            LogHelper.e("renderPdfToBitmap: erro", e)
            null
        }
    }

    /**
     * Lê o PDF retornado pela API e estima a qualidade real do conteúdo:
     * - se houver imagem embutida (raster), devolve a resolução em px e o DPI efetivo;
     * - se o conteúdo for vetorial, indica que não há perda de qualidade.
     */
    private fun medirQualidadeApiPdf(pdfBytes: ByteArray): String? {
        var resultado: String? = null
        val tmp = File(cacheDir, "qualidade_api_${System.currentTimeMillis()}.pdf")
        try {
            tmp.outputStream().use { it.write(pdfBytes) }
            val fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(fd)
            if (r.pageCount > 0) {
                val page = r.openPage(0)
                val pageW = page.width.toFloat(); val pageH = page.height.toFloat()
                page.close()

                val raw = String(pdfBytes, Charsets.ISO_8859_1)
                val larguras = Regex("/Width\\s+(\\d+)").findAll(raw)
                    .mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { v -> v in 16..12000 } }
                val alturas = Regex("/Height\\s+(\\d+)").findAll(raw)
                    .mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { v -> v in 16..12000 } }
                val maxW = larguras.maxOrNull()
                val maxH = alturas.maxOrNull()

                if (maxW != null && maxH != null) {
                    val dpiX = (maxW / (pageW / 72f)).toInt()
                    val dpiY = (maxH / (pageH / 72f)).toInt()
                    val dpi = (dpiX + dpiY) / 2
                    resultado = "API: ${maxW}×${maxH}px · ~${dpi} DPI (página ${pageW.toInt()}×${pageH.toInt()}pt)"
                } else {
                    resultado = "API: vetorial (sem perda de qualidade)"
                }
            }
            r.close(); fd.close()
        } catch (e: Throwable) {
            LogHelper.e("ModeloEditavel: erro medir qualidade", e)
        } finally {
            tmp.delete()
        }
        return resultado
    }

    private fun computePreviewKey(): String {
        val sb = StringBuilder()
        sb.append("ov=").append(semOverlay).append("|")
        sb.append("sz=").append(sizeLabel).append("|")
        sb.append("venc=").append(modoVencimentos).append("|")
        multiDataList.forEachIndexed { i, d ->
            sb.append("d${i}=").append(d.productName).append(":").append(d.price).append(":")
                .append(d.promotionPrice).append(":").append(d.installmentQuantity).append(":")
                .append(d.installmentPrice).append(":").append(d.takeAndWinQuantity).append(":")
                .append(d.takeAndWinPrice).append(":").append(d.ean).append(";")
        }
        multiValidades.forEachIndexed { i, v -> sb.append("v$i=$v;") }
        return sb.toString()
    }

    private fun limparPaginasPreview() {
        ivPreview.setImageBitmap(null)
        paginasPreview.forEach { it.recycle() }
        paginasPreview.clear()
        paginaPreviewAtual = 0
        layoutPageIndicator.visibility = View.GONE
    }

    private fun navegarPaginaPreview(delta: Int) {
        if (paginasPreview.size <= 1 || isFinishing) return
        val total = paginasPreview.size
        var novo = paginaPreviewAtual + delta
        if (novo < 0) novo = total - 1
        else if (novo >= total) novo = 0
        if (novo == paginaPreviewAtual) return
        paginaPreviewAtual = novo
        ivPreview.setImageBitmap(paginasPreview[paginaPreviewAtual])
        atualizarPageIndicator()
    }

    private fun setPaginaPreview(indice: Int) {
        if (paginasPreview.isEmpty()) return
        paginaPreviewAtual = indice.coerceIn(0, paginasPreview.size - 1)
        ivPreview.setImageBitmap(paginasPreview[paginaPreviewAtual])
        atualizarPageIndicator()
    }

    private fun atualizarPageIndicator() {
        val total = paginasPreview.size
        if (total <= 1) {
            layoutPageIndicator.visibility = View.GONE
        } else {
            tvPageIndicator.text = "${paginaPreviewAtual + 1} / $total"
            layoutPageIndicator.visibility = View.VISIBLE
        }
        tvPreviewStatus.text = "$statusPreviewBase · página ${paginaPreviewAtual + 1}/$total"
    }

    // Porta fiel do Flutter (_ehComum): comum = SEM overlay.
    // Overlay só em Leve/Ganhe (leve_ganhe_total), PAPELETA_PROMOCIONAL, De/Por,
    // e em itens com preço promocional / leve-e-ganhe.
    private fun ehPapeletaComum(data: PapeletaPrintingData): Boolean {
        if (data.template == Constants.SIGN_TEMPLATE_MODELO) return false
        if (data.template == Constants.SIGN_TYPE_PAPELETA_PROMOCIONAL) return false
        if (data.template == Constants.SIGN_TEMPLATE_DEPOR) return false
        if (data.template == Constants.SIGN_TEMPLATE_DEPOR_PARCELADO) return true
        val hasPromo = (data.promotionPrice ?: 0.0) > 0
        val hasTakeAndWin = (data.takeAndWinQuantity ?: 0) > 0
        return !(hasPromo || hasTakeAndWin)
    }

    private fun gerarPreviewMulti() {
        if (multiDataList.isEmpty()) { ToastUtils.show(this, "Nenhum item para preview"); return }
        if (modoVencimentos) { gerarPreviewVencimentos(); return }
        apiQualidadeInfo = null
        val overlayBmp = if (!semOverlay) overlayBitmap else null
        if (!semOverlay && overlayBmp == null) { ToastUtils.show(this, "Overlay não encontrado"); return }

        val cacheKey = computePreviewKey()
        val cached = previewBitmapCache[cacheKey]
        if (cached != null && cached.isNotEmpty() && !cached.any { it.isRecycled }) {
            limparPaginasPreview()
            paginasPreview.addAll(cached)
            ivPreview.layoutParams.height = previewHeight
            statusPreviewBase = "Preview $sizeLabel (cache) · ${cached.size} item(ns)"
            setPaginaPreview(0)
            tvPreviewStatus.visibility = View.GONE
            btnCompartilhar.isEnabled = true
            btnSharePdf.isEnabled = true
            multiPreviewReady = true
            return
        }

        btnGerarPreview.text = "GERANDO..."
        btnGerarPreview.isEnabled = false
        tvPreviewStatus.apply { text = "Buscando preview..."; visibility = View.VISIBLE }
        btnCompartilhar.isEnabled = false

        val apiService = ApiClient.getSlApiService(sessionManager)
        val isLandscapePreview = colsQuadrantes > rowsQuadrantes
        val defaultW = if (isLandscapePreview) 1200 else 850
        val defaultH = if (isLandscapePreview) 850 else 1200
        val ovW = overlayBmp?.width ?: defaultW
        val ovH = overlayBmp?.height ?: defaultH
        val halfW = ovW / colsQuadrantes.toFloat(); val halfH = ovH / rowsQuadrantes.toFloat()
        lifecycleScope.launch {
            try {
                val results = multiDataList

                // Fetch + render PDFs to bitmaps (parallel)
                val cardBitmaps: List<Pair<Int, Bitmap>> = withContext(Dispatchers.IO) {
                    results.mapIndexed { idx, data ->
                        async {
                            try {
                                val key = "prev-$idx-${halfW.toInt()}x${halfH.toInt()}"
                                val cachedCard = previewCardCache[key]
                                if (cachedCard != null && !cachedCard.isRecycled) {
                                    return@async idx to cachedCard
                                }
                                val pdfBytes = previewPdfCache[idx] ?: run {
                                    val response = apiService.previewPriceSign(data.copy(size = Constants.SIGN_SIZE_1X1))
                                    if (!response.isSuccessful) {
                                        LogHelper.e("PreviewMulti: HTTP ${response.code()} item $idx")
                                        return@async null
                                    }
                                    val bytes = response.body()?.bytes()
                                    if (bytes == null || bytes.isEmpty()) return@async null
                                    previewPdfCache[idx] = bytes
                                    bytes
                                }
                                if (apiQualidadeInfo == null) {
                                    apiQualidadeInfo = medirQualidadeApiPdf(pdfBytes)
                                }
                                val bmp = renderPdfToBitmap(pdfBytes, halfW.toInt(), halfH.toInt(), maxScale = 2)
                                if (bmp != null) {
                                    previewCardCache[key] = bmp
                                    idx to bmp
                                } else null
                            } catch (e: Exception) {
                                LogHelper.e("PreviewMulti api", e); null
                            }
                        }
                    }.map { it.await() }.filterNotNull()
                }

                withContext(Dispatchers.Main) {
                    if (cardBitmaps.isEmpty()) {
                        ToastUtils.show(this@ModeloEditavelActivity, "Erro ao carregar preview da API")
                        btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
                        btnGerarPreview.isEnabled = true
                        tvPreviewStatus.text = "Falha ao carregar preview"
                        return@withContext
                    }
                    limparPaginasPreview()
                }

                val gridCap = colsQuadrantes * rowsQuadrantes
                val pages = ceil(cardBitmaps.size / gridCap.toFloat()).toInt().coerceAtLeast(1)
                val paginas: List<Bitmap> = withContext(Dispatchers.IO) {
                    val pinturaBranca = android.graphics.Paint().apply { color = Color.WHITE }
                    val temp = mutableListOf<Bitmap>()
                    for (p in 0 until pages) {
                        val pageBmp = Bitmap.createBitmap(ovW, ovH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(pageBmp)
                        canvas.drawColor(Color.WHITE)
                        if (overlayBmp != null) {
                            canvas.drawBitmap(overlayBmp, 0f, 0f, null)
                        }

                        for ((idx, bmp) in cardBitmaps) {
                            if (idx / gridCap != p) continue
                            val local = idx % gridCap
                            val col = local % colsQuadrantes; val row = local / colsQuadrantes
                            val left = col * halfW; val top = row * halfH
                            val ehComum = ehPapeletaComum(multiDataList[idx])
                            val base = removerBrancoSuave(bmp)
                            val fitComumSeisX1 = colsQuadrantes == 6 && rowsQuadrantes == 1
                            val scaled = if (fitComumSeisX1) base else Bitmap.createScaledBitmap(base, halfW.toInt(), halfH.toInt(), true)
                            if (base !== bmp) base.recycle()
                            if (ehComum && overlayBmp != null) {
                                canvas.drawRect(left, top, left + halfW, top + halfH, pinturaBranca)
                            }
                            if (fitComumSeisX1) {
                                desenharComProporcao(canvas, scaled, halfW, halfH, left, top, if (ehComum) 0.03f else 0f, if (ehComum) 0f else if (colsQuadrantes > rowsQuadrantes) 0.015f else 0.03f)
                            } else {
                                centralizarConteudo(canvas, scaled, halfW, halfH, left, top, 1f, if (ehComum) 0.03f else 0f, if (ehComum) 0f else if (colsQuadrantes > rowsQuadrantes) 0.015f else 0.03f)
                            }
                            if (!fitComumSeisX1) scaled.recycle()
                        }
                        temp.add(pageBmp)
                    }
                    temp
                }

                withContext(Dispatchers.Main) {
                    cardBitmaps.forEach { it.second.recycle() }
                    paginasPreview.addAll(paginas)
                    val newKey = computePreviewKey()
                    previewBitmapCache[newKey] = paginasPreview.toList()
                    lastPreviewKey = newKey
                    ivPreview.layoutParams.height = previewHeight
                    statusPreviewBase = "Preview $sizeLabel (API) · ${cardBitmaps.size} item(ns) em $pages página(s)" +
                        (apiQualidadeInfo?.let { " · Qualidade: $it" } ?: "")
                    setPaginaPreview(0)
                    tvPreviewStatus.visibility = View.GONE
                    btnCompartilhar.isEnabled = true
                    btnSharePdf.isEnabled = true
                    btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
                    btnGerarPreview.isEnabled = true
                    multiPreviewReady = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
                    btnGerarPreview.isEnabled = true
                    tvPreviewStatus.text = "Erro: ${e.message}"
                    ToastUtils.show(this@ModeloEditavelActivity, "Erro: ${e.message}")
                }
                LogHelper.e("ModeloEditavel: erro preview multi", e)
            }
        }
    }

    private fun formatPrice(value: Double?): String {
        if (value == null) return ""
        val nf = java.text.NumberFormat.getNumberInstance(Locale("pt", "BR"))
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return nf.format(value)
    }

    private fun gerarPreviewVencimentos() {
        if (multiDataList.isEmpty()) { ToastUtils.show(this, "Nenhum item para preview"); return }
        apiQualidadeInfo = null
        btnGerarPreview.text = "GERANDO..."
        btnGerarPreview.isEnabled = false
        tvPreviewStatus.apply { text = "Buscando preview..."; visibility = View.VISIBLE }
        btnCompartilhar.isEnabled = false

        val apiService = ApiClient.getSlApiService(sessionManager)
        val isLandscapePreview = colsQuadrantes > rowsQuadrantes
        val defaultW = if (isLandscapePreview) 1200 else 850
        val defaultH = if (isLandscapePreview) 850 else 1200
        val ovW = overlayBitmap?.width ?: defaultW
        val ovH = overlayBitmap?.height ?: defaultH
        val halfW = ovW / colsQuadrantes.toFloat(); val halfH = ovH / rowsQuadrantes.toFloat()
        lifecycleScope.launch {
            try {
                val results = multiDataList
                val cardBitmaps: List<Pair<Int, Bitmap>> = withContext(Dispatchers.IO) {
                    results.mapIndexed { idx, data ->
                        async {
                            try {
                                val pdfBytes = previewPdfCache[idx] ?: run {
                                    val response = apiService.previewPriceSign(data.copy(size = Constants.SIGN_SIZE_1X1))
                                    if (!response.isSuccessful) {
                                        LogHelper.e("PreviewVenc: falha item $idx")
                                        return@async null
                                    }
                                    val bytes = response.body()?.bytes()
                                    if (bytes == null || bytes.isEmpty()) return@async null
                                    previewPdfCache[idx] = bytes
                                    bytes
                                }
                                if (apiQualidadeInfo == null) {
                                    apiQualidadeInfo = medirQualidadeApiPdf(pdfBytes)
                                }
                                val bmp = renderPdfToBitmap(pdfBytes, halfW.toInt(), halfH.toInt(), maxScale = 2)
                                if (bmp != null) {
                                    desenharValidadeCard(bmp, multiValidades.getOrElse(idx) { "" }, multiDataList[idx])
                                    idx to bmp
                                } else null
                            } catch (e: Exception) {
                                LogHelper.e("PreviewVenc api", e); null
                            }
                        }
                    }.map { it.await() }.filterNotNull()
                }

                val expandedCells = mutableListOf<Int>()
                for ((i, q) in multiQuantidades.withIndex()) {
                    repeat(q.coerceAtLeast(1)) { expandedCells.add(i) }
                }
                val totalEtiquetas = expandedCells.size

                withContext(Dispatchers.Main) {
                    if (cardBitmaps.isEmpty()) {
                        ToastUtils.show(this@ModeloEditavelActivity, "Erro ao carregar preview da API")
                        btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
                        btnGerarPreview.isEnabled = true
                        tvPreviewStatus.text = "Falha ao carregar preview"
                        return@withContext
                    }
                    limparPaginasPreview()
                }

                val gridCap = colsQuadrantes * rowsQuadrantes
                val pages = ceil(totalEtiquetas / gridCap.toFloat()).toInt().coerceAtLeast(1)
                val bitmapPorItem = cardBitmaps.associate { it.first to it.second }
                val paginas: List<Bitmap> = withContext(Dispatchers.IO) {
                    val pinturaBranca = android.graphics.Paint().apply { color = Color.WHITE }
                    val overlayCell = if (!semOverlay) overlayBitmap else null
                    val shiftYVenc = if (colsQuadrantes > rowsQuadrantes) 0.015f else if (colsQuadrantes == 2 && rowsQuadrantes == 2) 0.01f else 0.03f
                    val temp = mutableListOf<Bitmap>()
                    for (p in 0 until pages) {
                        val pageBmp = Bitmap.createBitmap(ovW, ovH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(pageBmp)
                        canvas.drawColor(Color.WHITE)
                        for (cellPos in expandedCells.indices) {
                            if (cellPos / gridCap != p) continue
                            val idx = expandedCells[cellPos]
                            val bmp = bitmapPorItem[idx] ?: continue
                            val local = cellPos % gridCap
                            val col = local % colsQuadrantes; val row = local / colsQuadrantes
                            val left = col * halfW; val top = row * halfH
                            val data = multiDataList[idx]
                            val ehComum = ehPapeletaComum(data)
                            if (overlayCell != null && !ehComum) {
                                val src = android.graphics.Rect(
                                    left.toInt(), top.toInt(),
                                    left.toInt() + halfW.toInt(), top.toInt() + halfH.toInt()
                                )
                                canvas.drawBitmap(overlayCell, src, android.graphics.RectF(left, top, left + halfW, top + halfH), null)
                                centralizarConteudo(canvas, bmp, halfW, halfH, left, top, 0.35f, 0f, shiftYVenc)
                            } else {
                                if (overlayCell != null) {
                                    canvas.drawRect(left, top, left + halfW, top + halfH, pinturaBranca)
                                }
                                val scaled = Bitmap.createScaledBitmap(bmp, halfW.toInt(), halfH.toInt(), true)
                                centralizarConteudo(canvas, scaled, halfW, halfH, left, top, 1f, 0.03f)
                                scaled.recycle()
                            }
                        }
                        temp.add(pageBmp)
                    }
                    temp
                }

                withContext(Dispatchers.Main) {
                    cardBitmaps.forEach { it.second.recycle() }
                    paginasPreview.addAll(paginas)
                    ivPreview.layoutParams.height = previewHeight
                    statusPreviewBase = "Preview Vencimentos $sizeLabel com $totalEtiquetas etiqueta(s) em $pages página(s)" +
                        (apiQualidadeInfo?.let { " · Qualidade: $it" } ?: "")
                    setPaginaPreview(0)
                    tvPreviewStatus.visibility = View.GONE
                    btnCompartilhar.isEnabled = true
                    btnSharePdf.isEnabled = true
                    btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
                    btnGerarPreview.isEnabled = true
                    multiPreviewReady = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnGerarPreview.text = "GERAR PRÉ-VISUALIZAÇÃO $sizeLabel"
                    btnGerarPreview.isEnabled = true
                    tvPreviewStatus.text = "Erro: ${e.message}"
                    ToastUtils.show(this@ModeloEditavelActivity, "Erro: ${e.message}")
                }
                LogHelper.e("ModeloEditavel: erro preview vencimentos", e)
            }
        }
    }

    private fun amostrarLinhas(card: Bitmap): BooleanArray? {
        return try {
            val w = card.width; val h = card.height
            val targetW = minOf(w, 640)
            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceAtLeast(1)
            val small = if (targetW < w) Bitmap.createScaledBitmap(card, targetW, targetH, true) else card
            val sw = small.width; val sh = small.height
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== card) small.recycle()
            val rows = BooleanArray(sh)
            for (y in 0 until sh) {
                var has = false
                for (x in 0 until sw) {
                    if (((px[y * sw + x] shr 24) and 0xFF) > 8) { has = true; break }
                }
                rows[y] = has
            }
            rows
        } catch (e: Exception) {
            LogHelper.e("amostrarLinhas: erro", e)
            null
        }
    }

    private fun localizarSegundaBanda(card: Bitmap): Pair<Int, Int>? {
        val rows = amostrarLinhas(card) ?: return null
        val h = card.height; val sh = rows.size
        val scale = h.toFloat() / sh
        var bandsFound = 0
        var i = 0
        while (i < sh) {
            while (i < sh && !rows[i]) i++
            val start = i
            while (i < sh && rows[i]) i++
            if ((i - start) * scale >= h * 0.03f) {
                bandsFound++
                if (bandsFound == 2) return Pair((start * scale).toInt(), (i * scale).toInt())
            }
        }
        return null
    }

    private fun localizarPrimeiraBanda(card: Bitmap): Pair<Int, Int>? {
        val rows = amostrarLinhas(card) ?: return null
        val h = card.height; val sh = rows.size
        val scale = h.toFloat() / sh
        var i = 0
        while (i < sh) {
            while (i < sh && !rows[i]) i++
            val start = i
            while (i < sh && rows[i]) i++
            if ((i - start) * scale >= h * 0.03f) {
                return Pair((start * scale).toInt(), (i * scale).toInt())
            }
        }
        return null
    }

    private fun extrairBandasConteudo(card: Bitmap): List<Pair<Int, Int>>? {
        val rows = amostrarLinhas(card) ?: return null
        val h = card.height; val sh = rows.size
        val scale = h.toFloat() / sh
        val bandas = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < sh) {
            while (i < sh && !rows[i]) i++
            val start = i
            while (i < sh && rows[i]) i++
            val alt = (i - start) * scale
            if (alt >= h * 0.02f) bandas.add(Pair((start * scale).toInt(), (i * scale).toInt()))
        }
        return bandas
    }

    private fun riscarPrecoDe(card: Bitmap, data: PapeletaPrintingData) {
        val hasDePor = data.promotionPrice != null && data.promotionPrice > 0 && data.promotionPrice < data.price
        if (!hasDePor) return
        val w = card.width; val h = card.height
        val bandas = extrairBandasConteudo(card) ?: return
        if (bandas.size < 2) return
        // DE fica logo após a descrição (2ª banda); com fallback para a penúltima (antes do POR)
        val segunda = bandas[1]
        val banda = if (segunda.second - segunda.first <= h * 0.2f || bandas.size < 3) segunda else bandas[bandas.size - 2]
        if (banda.second - banda.first > h * 0.3f) return
        val s = w / 320f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * s
        }
        val canvas = Canvas(card)
        val y = (banda.first + banda.second) / 2f
        canvas.drawLine(w * 0.04f, y, w * 0.96f, y, paint)
    }

    private fun centroXConteudo(card: Bitmap): Float {
        return try {
            val w = card.width; val h = card.height
            val targetW = minOf(w, 640)
            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceAtLeast(1)
            val small = if (targetW < w) Bitmap.createScaledBitmap(card, targetW, targetH, true) else card
            val sw = small.width; val sh = small.height
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== card) small.recycle()
            var minX = sw; var maxX = -1
            for (y in 0 until sh step 2) {
                for (x in 0 until sw step 2) {
                    val p = px[y * sw + x]
                    if (((p shr 24) and 0xFF) > 8) {
                        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                        if (r < 250 || g < 250 || b < 250) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                        }
                    }
                }
            }
            if (maxX < 0) w / 2f
            else ((minX + maxX) / 2f) * (w.toFloat() / sw)
        } catch (e: Exception) {
            card.width / 2f
        }
    }

    private fun desenharValidadeCard(card: Bitmap, validade: String, data: PapeletaPrintingData) {
        riscarPrecoDe(card, data)
        val w = card.width; val h = card.height
        val s = w / 320f
        val canvas = Canvas(card)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = helveticaBold
            textAlign = android.graphics.Paint.Align.CENTER
        }
        paint.color = Color.parseColor("#D32F2F")
        val centroX = centroXConteudo(card)

        // Validade logo acima da descrição (primeira banda de conteúdo; ignora cabeçalho fino no topo)
        val validadeTexto = if (validade.length == 8 && validade.all { it.isDigit() }) {
            "${validade.substring(0, 2)}/${validade.substring(2, 4)}/${validade.substring(4)}"
        } else validade.trim()
        if (validadeTexto.isNotBlank()) {
            val banda1 = localizarPrimeiraBanda(card)
            val banda2 = localizarSegundaBanda(card)
            val topoDesc = when {
                banda1 != null && banda1.first < h * 0.05f && banda2 != null -> banda2.first.toFloat()
                else -> maxOf(banda1?.first?.toFloat() ?: h * 0.14f, 24f * s)
            }
            paint.textSize = 16f * s
            val fmVal = paint.fontMetrics
            var yVal = topoDesc + 4f * s - fmVal.bottom
            val alturaTexto = (fmVal.bottom - fmVal.top) * 1.2f
            yVal = yVal.coerceIn(h * 0.28f, h * 0.45f)
            canvas.drawText("VAL.: $validadeTexto".uppercase(), centroX, yVal, paint)
        } else {
            LogHelper.d("desenharValidadeCard: validade vazia para item ${data.productName}")
        }

        // Rodapé: mesma posição em todos os tamanhos (onde a validade ficava antes)
        paint.textSize = 9f * s
        val fmFooter = paint.fontMetrics
        val fmValRef = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = helveticaBold
            textSize = 16f * s
        }.fontMetrics
        val footerY = (h * 0.93f + fmFooter.top) - 4f * s - fmValRef.bottom + fmFooter.top
        canvas.drawText("PRÓXIMO DA VALIDADE. CONSUMO RÁPIDO", centroX, footerY - fmFooter.top, paint)
    }

    private fun centralizarConteudo(canvas: Canvas, bmp: Bitmap, cellW: Float, cellH: Float, left: Float, top: Float, maxShiftFrac: Float = 0.35f, ignorarBordasFrac: Float = 0f, shiftYFrac: Float = 0f) {
        try {
            val w = bmp.width; val h = bmp.height
            val targetW = minOf(w, 800)
            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceAtLeast(1)
            val small = if (targetW < w) Bitmap.createScaledBitmap(bmp, targetW, targetH, true) else bmp
            val sw = small.width; val sh = small.height
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== bmp) small.recycle()
            val bx0 = (sw * ignorarBordasFrac).toInt()
            val bx1 = (sw * (1f - ignorarBordasFrac)).toInt()
            val by0 = (sh * ignorarBordasFrac).toInt()
            val by1 = (sh * (1f - ignorarBordasFrac)).toInt()
            var minX = sw; var maxX = -1
            for (y in by0 until by1 step 2) {
                for (x in bx0 until bx1 step 2) {
                    val p = px[y * sw + x]
                    if (((p shr 24) and 0xFF) > 8) {
                        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                        if (r < 250 || g < 250 || b < 250) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                        }
                    }
                }
            }
            if (maxX <= minX) {
                canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top - cellH * shiftYFrac, left + cellW, top + cellH - cellH * shiftYFrac), null)
                return
            }
            val scaleX = cellW / sw
            val contentDrawW = (maxX - minX + 1) * scaleX
            val dx = ((cellW - contentDrawW) / 2f - minX * scaleX).coerceIn(-cellW * maxShiftFrac, cellW * maxShiftFrac)
            canvas.drawBitmap(bmp, null, android.graphics.RectF(left + dx, top - cellH * shiftYFrac, left + dx + cellW, top + cellH - cellH * shiftYFrac), null)
        } catch (e: Exception) {
            canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top - cellH * shiftYFrac, left + cellW, top + cellH - cellH * shiftYFrac), null)
        }
    }

    private fun centralizarComum(canvas: Canvas, bmp: Bitmap, cellW: Float, cellH: Float, left: Float, top: Float) {
        try {
            val w = bmp.width; val h = bmp.height
            val targetW = minOf(w, 800)
            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceAtLeast(1)
            val small = if (targetW < w) Bitmap.createScaledBitmap(bmp, targetW, targetH, true) else bmp
            val sw = small.width; val sh = small.height
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== bmp) small.recycle()
            var soma = 0.0; var pesoTotal = 0.0
            for (y in 0 until sh step 2) {
                for (x in 0 until sw step 2) {
                    val p = px[y * sw + x]
                    if (((p shr 24) and 0xFF) > 8) {
                        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                        val minRGB = minOf(r, g, b)
                        if (minRGB < 250) {
                            val peso = 250 - minRGB
                            soma += x * peso
                            pesoTotal += peso
                        }
                    }
                }
            }
            if (pesoTotal <= 0) {
                canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + cellW, top + cellH), null)
                return
            }
            val centro = soma / pesoTotal
            val scaleX = cellW / sw
            val dx = (cellW / 2f - (centro * scaleX).toFloat()).coerceIn(-cellW * 0.35f, cellW * 0.35f)
            canvas.drawBitmap(bmp, null, android.graphics.RectF(left + dx, top, left + dx + cellW, top + cellH), null)
        } catch (e: Exception) {
            canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + cellW, top + cellH), null)
        }
    }

    private fun desenharComProporcao(canvas: Canvas, bmp: Bitmap, cellW: Float, cellH: Float, left: Float, top: Float, ignorarBordasFrac: Float = 0f, shiftYFrac: Float = 0f) {
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val scale = minOf(cellW / w, cellH / h)
        val dw = w * scale; val dh = h * scale
        val dlBase = left + (cellW - dw) / 2f
        val dtBase = top - cellH * shiftYFrac + (cellH - dh) / 2f
        try {
            val targetW = minOf(bmp.width, 800)
            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceAtLeast(1)
            val small = if (targetW < bmp.width) Bitmap.createScaledBitmap(bmp, targetW, targetH, true) else bmp
            val sw = small.width; val sh = small.height
            val px = IntArray(sw * sh)
            small.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (small !== bmp) small.recycle()
            val bx0 = (sw * ignorarBordasFrac).toInt()
            val bx1 = (sw * (1f - ignorarBordasFrac)).toInt()
            val by0 = (sh * ignorarBordasFrac).toInt()
            val by1 = (sh * (1f - ignorarBordasFrac)).toInt()
            var minX = sw; var maxX = -1; var minY = sh; var maxY = -1
            for (y in by0 until by1 step 2) {
                for (x in bx0 until bx1 step 2) {
                    val p = px[y * sw + x]
                    if (((p shr 24) and 0xFF) > 8) {
                        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                        if (r < 250 || g < 250 || b < 250) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
            }
            if (maxX > minX && maxY > minY) {
                val scaleX = dw / sw; val scaleY = dh / sh
                val contentW = (maxX - minX + 1) * scaleX
                val contentH = (maxY - minY + 1) * scaleY
                val dx = ((dw - contentW) / 2f - minX * scaleX).coerceIn(-dw * 0.35f, dw * 0.35f)
                val dy = ((dh - contentH) / 2f - minY * scaleY).coerceIn(-dh * 0.35f, dh * 0.35f)
                canvas.drawBitmap(bmp, null, android.graphics.RectF(dlBase + dx, dtBase + dy, dlBase + dx + dw, dtBase + dy + dh), null)
                return
            }
        } catch (_: Exception) {}
        canvas.drawBitmap(bmp, null, android.graphics.RectF(dlBase, dtBase, dlBase + dw, dtBase + dh), null)
    }

    private fun renderCardBitmap(data: PapeletaPrintingData): Bitmap {
        val CW = 320f
        val CH = 350f
        val bmp = Bitmap.createBitmap(CW.toInt(), CH.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val hasDePor = data.promotionPrice != null && data.promotionPrice > 0 && data.promotionPrice < data.price
        val hasParcelado = data.installmentQuantity != null && data.installmentQuantity > 0 && data.installmentPrice != null && data.installmentPrice > 0

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK

        // ── Title: 14px, centered ──
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = helveticaBold
        paint.textSize = 14f
        val nome = data.productName.ifEmpty { "PRODUTO" }
        val nomeLines = quebrarTexto(nome, paint, CW * 0.88f)
        var y = 95f - paint.fontMetrics.top
        for (li in nomeLines.indices) {
            canvas.drawText(nomeLines[li].uppercase(), CW / 2f, y, paint)
            y += 14f * 1.2f
        }

        val labelX = 65f
        val priceX = 115f

        if (hasDePor && hasParcelado) {
            // ── White area ──
            // "de R$349,99" — small, left, below title
            paint.textSize = 10f
            paint.typeface = helveticaBold
            paint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText("de R$${formatPrice(data.price)}", 36f, 140f - paint.fontMetrics.top, paint)

            // "por R$" — small, left, immediately below "de R$"
            paint.textSize = 14f
            paint.typeface = helveticaBold
            canvas.drawText("por R$", 36f, 157f - paint.fontMetrics.top, paint)

            // Promo price — very large, bold, to the right, main highlight of white area
            paint.textSize = 36f
            paint.typeface = helveticaBold
            canvas.drawText(formatPrice(data.promotionPrice), 100f, 148f - paint.fontMetrics.top, paint)

            // ── Yellow area ──
            // "3x s/ juros de:" — upper left of yellow area
            paint.textSize = 14f
            paint.typeface = helveticaBold
            canvas.drawText("${data.installmentQuantity}x s/ juros de:", 28f, 215f - paint.fontMetrics.top, paint)

            // Parcel price — LARGEST number on entire label, to the right
            paint.textSize = 40f
            paint.typeface = helveticaBold
            canvas.drawText(formatPrice(data.installmentPrice), 70f, 240f - paint.fontMetrics.top, paint)

            // "R$" — left, below installment text, medium
            paint.textSize = 16f
            paint.typeface = helveticaBold
            canvas.drawText("R$", 28f, 258f - paint.fontMetrics.top, paint)

        } else if (hasDePor) {
            // ── White area: "de" + "R$" empilhados à esquerda, preço original grande à direita ──
            paint.textAlign = android.graphics.Paint.Align.LEFT
            paint.typeface = helveticaBold

            // "de" — very small, left
            paint.textSize = 10f
            canvas.drawText("de", 36f, 120f - paint.fontMetrics.top, paint)

            // "R$" — small, left, below "de"
            paint.textSize = 14f
            canvas.drawText("R$", 36f, 138f - paint.fontMetrics.top, paint)

            // Original price — large bold, right, stands out in white area
            paint.textSize = 36f
            canvas.drawText(formatPrice(data.price), 100f, 130f - paint.fontMetrics.top, paint)

            // ── Yellow area: "por" + "R$" empilhados à esquerda, preço promo enorme à direita ──
            paint.textSize = 14f
            val promLabelY = 210f
            canvas.drawText("por", 36f, promLabelY - paint.fontMetrics.top, paint)

            paint.textSize = 16f
            canvas.drawText("R$", 36f, (promLabelY + 18f) - paint.fontMetrics.top, paint)

            // Promo price — very large, absolute main highlight of the label
            paint.textSize = 40f
            canvas.drawText(formatPrice(data.promotionPrice), 100f, (promLabelY + 8f) - paint.fontMetrics.top, paint)

        } else if (hasParcelado) {
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.textSize = 18f
            paint.typeface = helveticaBold
            canvas.drawText("${data.installmentQuantity}x de ${java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(data.installmentPrice)}", CW / 2f, 215f - paint.fontMetrics.top, paint)

            paint.textSize = 28f
            paint.typeface = helveticaBold
            canvas.drawText("R$ ${formatPrice(data.price)}", CW / 2f, 295f - paint.fontMetrics.top, paint)

        } else {
            // Comum: big price centered
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.textSize = 38f
            paint.typeface = helveticaBold
            canvas.drawText("R$ ${formatPrice(data.price)}", CW / 2f, 255f - paint.fontMetrics.top, paint)
        }

        return bmp
    }

    private fun desenharCard(canvas: Canvas, data: PapeletaPrintingData, left: Int, top: Int, qw: Int, qh: Int) {
        val cardBmp = renderCardBitmap(data)
        val scaled = Bitmap.createScaledBitmap(cardBmp, qw, qh, true)
        canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
        scaled.recycle()
        cardBmp.recycle()
    }

    private fun desenharItemNoCanvas(canvas: Canvas, data: PapeletaPrintingData, q: Rect, f: java.text.NumberFormat) {
        val qw = q.width().toFloat()
        val qh = q.height().toFloat()
        val qLeft = q.left.toFloat()
        val qTop = q.top.toFloat()
        val cx = qLeft + qw / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK

        val hasDePor = data.promotionPrice != null && data.promotionPrice > 0 && data.promotionPrice < data.price
        val hasParcelado = data.installmentQuantity != null && data.installmentQuantity > 0 && data.installmentPrice != null && data.installmentPrice > 0

        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = qw * 0.035f
        val nome = data.productName.ifEmpty { "PRODUTO" }
        val nomeLines = quebrarTexto(nome, paint, qw * 0.88f)
        val nomeY = qTop + qh * 0.12f - (nomeLines.size - 1) * paint.textSize * 0.5f
        for (li in nomeLines.indices) {
            canvas.drawText(nomeLines[li].uppercase(), cx, nomeY + li * paint.textSize * 1.25f, paint)
        }

        val margin = qLeft + qw * 0.065f
        val gap = qw * 0.015f

        if (hasDePor && hasParcelado) {
            paint.textSize = qw * 0.022f
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textAlign = android.graphics.Paint.Align.LEFT
            val origStr = "de R$${formatPrice(data.price)}"
            val deY = qTop + qh * 0.33f
            canvas.drawText(origStr, margin, deY, paint)

            paint.textSize = qw * 0.035f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val porY = qTop + qh * 0.41f
            canvas.drawText("por R$", margin, porY, paint)

            paint.typeface = helveticaBold
            paint.textSize = qw * 0.10f
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText(formatPrice(data.promotionPrice), qLeft + qw * 0.90f, qTop + qh * 0.38f, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

            paint.textSize = qw * 0.038f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("${data.installmentQuantity}x s/ juros de:", cx, qTop + qh * 0.62f, paint)

            paint.textSize = qw * 0.045f
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText("R$", cx - qw * 0.03f, qTop + qh * 0.77f, paint)
            paint.typeface = helveticaBold
            paint.textSize = qw * 0.14f
            paint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText(formatPrice(data.installmentPrice), cx + qw * 0.02f, qTop + qh * 0.775f, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else if (hasDePor) {
            val rowBase = qTop + qh * 0.48f

            paint.textSize = qw * 0.038f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText("R$", margin, rowBase, paint)
            val rsW = paint.measureText("R$")

            paint.textSize = qw * 0.080f
            paint.typeface = helveticaBold
            canvas.drawText(formatPrice(data.price), margin + rsW + gap, rowBase, paint)

            paint.textSize = qw * 0.020f
            paint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText("de", margin, rowBase - qw * 0.038f * 1.3f, paint)

            val amareloTop = qTop + qh * 0.58f + qh * 0.05f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = qw * 0.038f
            paint.textAlign = android.graphics.Paint.Align.LEFT
            val porBase = amareloTop + qw * 0.038f * 0.85f
            canvas.drawText("por", margin, porBase, paint)
            canvas.drawText("R$", margin, porBase + qw * 0.038f * 1.2f, paint)
            val porColW = maxOf(paint.measureText("por"), paint.measureText("R$"))

            paint.textSize = qw * 0.10f
            paint.typeface = helveticaBold
            canvas.drawText(formatPrice(data.promotionPrice), margin + porColW + gap, porBase + qw * 0.038f * 1.2f, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else if (hasParcelado) {
            paint.textSize = qw * 0.065f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("${data.installmentQuantity}x de ${f.format(data.installmentPrice)}", cx, qTop + qh * 0.38f, paint)
            paint.textSize = qw * 0.10f
            val priceStr = formatPrice(data.price)
            canvas.drawText("R$ $priceStr", cx, qTop + qh * 0.60f, paint)
        } else {
            paint.textSize = qw * 0.12f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            val priceStr = formatPrice(data.price)
            canvas.drawText("R$ $priceStr", cx, qTop + qh * 0.55f, paint)
            paint.textSize = qw * 0.035f
            paint.color = Color.DKGRAY
            canvas.drawText("EAN: ${data.ean}", cx, qTop + qh * 0.78f, paint)
        }
    }

    private fun quebrarTexto(texto: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = texto.split(" ")
        var line = ""
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                lines.add(line)
                line = word
            } else {
                line = test
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.ifEmpty { listOf(texto) }
    }

    private suspend fun gerarPdfMultiArquivo(renderScale: Int = 12, copias: Int = 1, dpi: Int = 1200): File? {
        val overlay = if (!semOverlay) overlayBitmap else null
        if (!semOverlay && overlay == null) { return null }
        val apiService = ApiClient.getSlApiService(sessionManager)
        val allItems = multiDataList
        if (allItems.isEmpty()) return null
        return try {
            val a4W = 595; val a4H = 842
            val isLandscape = colsQuadrantes > rowsQuadrantes
            val pageW = if (isLandscape) a4H else a4W
            val pageH = if (isLandscape) a4W else a4H
            val MARGIN = 14f
            val TARGET_DPI = dpi.toFloat()
            val dpiScale = TARGET_DPI / 72f
            val usableW = pageW - 2 * MARGIN; val usableH = pageH - 2 * MARGIN
            val canvasW = (usableW * dpiScale).toInt(); val canvasH = (usableH * dpiScale).toInt()
            val cellW = canvasW / colsQuadrantes; val cellH = canvasH / rowsQuadrantes
            val gridCap = colsQuadrantes * rowsQuadrantes
            val expandedCells = mutableListOf<Int>()
            for ((i, q) in multiQuantidades.withIndex()) {
                repeat(q.coerceAtLeast(1)) { expandedCells.add(i) }
            }
            val totalEtiquetas = expandedCells.size
            val pages = ceil(totalEtiquetas / gridCap.toFloat()).toInt().coerceAtLeast(1)

            coroutineScope {
                val allIndices = (0 until allItems.size)
                val toFetch = allIndices.filter { previewPdfCache[it] == null }
                if (toFetch.isNotEmpty()) {
                    toFetch.map { idx ->
                        async(Dispatchers.IO) {
                            try {
                                val resp = apiService.previewPriceSign(allItems[idx].copy(size = Constants.SIGN_SIZE_1X1))
                                if (resp.isSuccessful) {
                                    resp.body()?.bytes()?.let { previewPdfCache[idx] = it }
                                } else {
                                    LogHelper.e("gerarPdfMulti prefetch http $idx ${resp.code()}")
                                }
                            } catch (e: Exception) {
                                LogHelper.e("gerarPdfMulti prefetch $idx", e)
                            }
                        }
                    }.forEach { it.await() }
                }
            }

            val pdfDoc = PdfDocument()
            var algumItem = false
            for (p in 0 until pages) {
                val items = allItems.subList(p * gridCap, minOf((p + 1) * gridCap, allItems.size))
                val baseIdx = p * gridCap

                val compositedBmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                val compCanvas = Canvas(compositedBmp)
                compCanvas.drawColor(Color.WHITE)
                if (overlay != null && !modoVencimentos) {
                    val ovScaled = Bitmap.createScaledBitmap(overlay, canvasW, canvasH, true)
                    compCanvas.drawBitmap(ovScaled, 0f, 0f, null); ovScaled.recycle()
                }

                if (modoVencimentos) {
                    val cellRange = (p * gridCap) until minOf((p + 1) * gridCap, totalEtiquetas)
                    val unicos = cellRange.map { expandedCells[it] }.distinct()
                    val bmpPorItem = HashMap<Int, Bitmap>()
                    for (idx in unicos) {
                        val data = multiDataList[idx]
                        val pdfBytes = previewPdfCache[idx]
                        if (pdfBytes == null || pdfBytes.isEmpty()) continue
                        val itemBmp = renderPdfToBitmap(pdfBytes, cellW, cellH)
                        if (itemBmp != null) {
                            desenharValidadeCard(itemBmp, multiValidades.getOrElse(idx) { "" }, data)
                            bmpPorItem[idx] = itemBmp
                        }
                    }
                    for (cellPos in cellRange) {
                        val idx = expandedCells[cellPos]
                        val itemBmp = bmpPorItem[idx] ?: continue
                        val local = cellPos % gridCap
                        val col = local % colsQuadrantes; val row = local / colsQuadrantes
                        val left = (col * cellW).toFloat(); val top = (row * cellH).toFloat()
                        val data = multiDataList[idx]
                        val ehComum = ehPapeletaComum(data)
                        val shiftYVenc = if (colsQuadrantes > rowsQuadrantes) 0.015f else if (colsQuadrantes == 2 && rowsQuadrantes == 2) 0.01f else 0.03f
                        if (overlay != null && !ehComum) {
                            val ovW = overlay.width; val ovH = overlay.height
                            val sx = (col * ovW) / colsQuadrantes
                            val sy = (row * ovH) / rowsQuadrantes
                            val ex = ((col + 1) * ovW) / colsQuadrantes
                            val ey = ((row + 1) * ovH) / rowsQuadrantes
                            val src = android.graphics.Rect(sx, sy, ex, ey)
                            compCanvas.drawBitmap(overlay, src, android.graphics.RectF(left, top, left + cellW, top + cellH), null)
                            centralizarConteudo(compCanvas, itemBmp, cellW.toFloat(), cellH.toFloat(), left, top, 0.35f, 0f, shiftYVenc)
                        } else {
                            val scaled = Bitmap.createScaledBitmap(itemBmp, cellW, cellH, true)
                            if (overlay != null) {
                                compCanvas.drawRect(
                                    left, top, left + cellW, top + cellH,
                                    android.graphics.Paint().apply { color = Color.WHITE }
                                )
                            }
                            centralizarConteudo(compCanvas, scaled, cellW.toFloat(), cellH.toFloat(), left, top, 1f, 0.03f)
                            scaled.recycle()
                        }
                        algumItem = true
                    }
                    bmpPorItem.values.forEach { it.recycle() }
                } else {
                    for (i in items.indices) {
                        val idx = baseIdx + i
                        val data = items[i]
                        val pdfBytes = previewPdfCache[idx]
                        if (pdfBytes == null || pdfBytes.isEmpty()) continue

                        val pdfFile = File(cacheDir, "share_multi_${System.currentTimeMillis()}_$idx.pdf")
                        pdfFile.outputStream().use { it.write(pdfBytes) }
                        val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(fd)
                        var itemBmp: Bitmap? = null
                        val ehComum = ehPapeletaComum(data)
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val itemScale = minOf(
                                    ceil(cellW.toFloat() / page.width).toInt(),
                                    ceil(cellH.toFloat() / page.height).toInt(),
                                    renderScale, 8
                                ).coerceAtLeast(1)
                                val renderWidth = (page.width * itemScale).coerceAtMost(7200)
                                val renderHeight = (page.height.toFloat() * (renderWidth.toFloat() / page.width.toFloat())).toInt()
                                val pdfBmp = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                                page.render(pdfBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                itemBmp = removerBrancoSuave(pdfBmp)
                                if (itemBmp !== pdfBmp) pdfBmp.recycle()
                            }
                        }
                        renderer.close(); fd.close(); pdfFile.delete()
                        if (itemBmp != null) {
                            val bmp = itemBmp!!
                            val col = i % colsQuadrantes; val row = i / colsQuadrantes
                            val fitComumSeisX1 = colsQuadrantes == 6 && rowsQuadrantes == 1
                            val scaled = if (fitComumSeisX1) bmp else Bitmap.createScaledBitmap(bmp, cellW, cellH, true)
                            if (ehComum && overlay != null) {
                                compCanvas.drawRect(
                                    (col * cellW).toFloat(), (row * cellH).toFloat(),
                                    ((col + 1) * cellW).toFloat(), ((row + 1) * cellH).toFloat(),
                                    android.graphics.Paint().apply { color = Color.WHITE }
                                )
                            }
                            if (fitComumSeisX1) {
                                desenharComProporcao(compCanvas, scaled, cellW.toFloat(), cellH.toFloat(), (col * cellW).toFloat(), (row * cellH).toFloat(), if (ehComum) 0.03f else 0f, if (ehComum) 0f else if (colsQuadrantes > rowsQuadrantes) 0.015f else 0.03f)
                            } else {
                                centralizarConteudo(compCanvas, scaled, cellW.toFloat(), cellH.toFloat(), (col * cellW).toFloat(), (row * cellH).toFloat(), 1f, if (ehComum) 0.03f else 0f, if (ehComum) 0f else if (colsQuadrantes > rowsQuadrantes) 0.015f else 0.03f)
                            }
                            if (!fitComumSeisX1) scaled.recycle()
                            bmp.recycle()
                            algumItem = true
                        }
                    }
                }

                for (c in 0 until copias) {
                    val docPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), p + 1).create())
                    docPage.canvas.drawBitmap(compositedBmp, null, android.graphics.RectF(MARGIN, MARGIN, pageW - MARGIN, pageH - MARGIN), null)
                    pdfDoc.finishPage(docPage)
                }
                compositedBmp.recycle()
            }

            if (!algumItem) { pdfDoc.close(); return null }
            val resultFile = File(cacheDir, "modelo_${sizeLabel}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(resultFile).use { pdfDoc.writeTo(it) }; pdfDoc.close()
            resultFile
        } catch (e: Throwable) {
            LogHelper.e("ModeloEditavel: erro gerar PDF multi", e); null
        }
    }

    private fun compartilharPdfMulti(shareMode: Boolean = false) {
        if (multiDataList.isEmpty()) { ToastUtils.show(this, "Nenhum item"); return }
        if (!semOverlay && overlayBitmap == null) { ToastUtils.show(this, "Overlay não encontrado"); return }
        val btnAlvo = if (shareMode) btnSharePdf else btnCompartilhar
        btnAlvo.text = "GERANDO PDF..."; btnAlvo.isEnabled = false
        val qtdCopias = etCopiaQuantidade.text.toString().toIntOrNull()?.coerceIn(1, 99) ?: 1

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!shareMode && (enviarApi || (semOverlay && apiSemOverlay))) {
                    val response = try {
                        ApiClient.getSlApiService(sessionManager).sendPriceSigns(Constants.DEFAULT_STORE, SendPriceSignRequest(multiDataList))
                    } catch (e: Exception) {
                        LogHelper.e("ModeloEditavel: erro enviar API", e)
                        null
                    }
                    withContext(Dispatchers.Main) {
                        btnCompartilhar.text = "IMPRIMIR"
                        btnCompartilhar.isEnabled = true
                        btnSharePdf.isEnabled = true
                        if (response?.isSuccessful == true) {
                            ToastUtils.show(this@ModeloEditavelActivity, "Papeletas enviadas para impressão!")
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else {
                            ToastUtils.show(this@ModeloEditavelActivity, "Erro ao enviar via API", Toast.LENGTH_LONG)
                        }
                    }
                    return@launch
                }
                val scales = if (shareMode) listOf(6, 4, 2) else listOf(12, 8, 4, 2)
                val dpis = if (shareMode) listOf(300, 600, 1200) else listOf(1200, 600, 300)
                var resultFile: File? = null
                for (dpi in dpis) {
                    for (s in scales) {
                        resultFile = gerarPdfMultiArquivo(renderScale = s, copias = qtdCopias, dpi = dpi)
                        if (resultFile != null) break
                    }
                    if (resultFile != null) break
                    System.gc()
                }
                if (resultFile == null) {
                    withContext(Dispatchers.Main) {
                        btnAlvo.text = if (shareMode) "COMPARTILHAR PDF" else "IMPRIMIR"
                        btnAlvo.isEnabled = true; btnCompartilhar.isEnabled = true
                        ToastUtils.show(this@ModeloEditavelActivity, "Erro ao gerar PDF")
                    }; return@launch
                }

                if (shareMode) {
                    withContext(Dispatchers.Main) {
                        btnSharePdf.text = "COMPARTILHAR PDF"; btnSharePdf.isEnabled = true
                        btnCompartilhar.isEnabled = true
                        try {
                            val uri = FileProvider.getUriForFile(this@ModeloEditavelActivity, "${packageName}.fileprovider", resultFile)
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Compartilhar PDF com"))
                        } catch (e: Exception) {
                            LogHelper.e("ModeloEditavel: erro share", e)
                            ToastUtils.show(this@ModeloEditavelActivity, "Erro ao compartilhar: ${e.message}")
                        }
                    }; return@launch
                }

                val pdfBytes = resultFile.readBytes()
                enviarParaImpressora(pdfBytes)

                withContext(Dispatchers.Main) {
                    btnCompartilhar.text = "IMPRIMIR"; btnCompartilhar.isEnabled = true
                    btnSharePdf.isEnabled = true
                    ToastUtils.show(this@ModeloEditavelActivity, "Papeletas enviadas para impressora!")
                    setResult(Activity.RESULT_OK); finish()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    btnAlvo.text = if (shareMode) "COMPARTILHAR PDF" else "IMPRIMIR"
                    btnAlvo.isEnabled = true; btnCompartilhar.isEnabled = true
                    LogHelper.e("ModeloEditavel: erro share", e)
                    ToastUtils.show(this@ModeloEditavelActivity, "Erro: ${e.message}")
                }
            }
        }
    }

    private fun enviarParaImpressaoMulti() {
        if (multiDataList.isEmpty()) { ToastUtils.show(this, "Nenhum item para imprimir"); return }
        compartilharPdfMulti()
    }
}
