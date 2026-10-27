package io.loja.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import io.loja.R
import io.loja.network.ApiClient
import io.loja.network.ApiService
import io.loja.network.PriceTag
import io.loja.network.PrintingData
import io.loja.network.SessionManager
import io.loja.utils.Constants
import io.loja.utils.LogHelper
import io.loja.utils.SessionExpiredHandler
import io.loja.utils.ToastUtils
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ItemScanner(
    val ean: String,
    val description: String,
    val quantity: Int,
    val tipo: String,
    val valor: String,
)

class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var tvInstrucao: TextView
    private lateinit var tvContador: TextView
    private var itensAdicionadosSessao = 0
    private var isScanning = false

    // ---- Modo contínuo: fica na câmera adicionando itens ----
    private var modoContinuo = false
    // Qual lista o modo contínuo alimenta: etiquetas ou papeletas avulsas
    private var tipoLista = TIPO_LISTA_ETIQUETAS
    @Volatile private var processandoLeitura = false
    private var ultimoCodigo = ""
    private var ultimoCodigoTempo = 0L
    private var tomBip: ToneGenerator? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var listaViewModel: ListaViewModel
    private lateinit var apiService: ApiService
    private var continuoPronto = false
    private var camera: androidx.camera.core.Camera? = null
    private var lanternaOn = false

    companion object {
        const val EXTRA_EAN = "EAN"
        const val EXTRA_MODO_CONTINUO = "MODO_CONTINUO"
        const val EXTRA_TIPO_LISTA = "TIPO_LISTA"
        const val TIPO_LISTA_ETIQUETAS = "ETIQUETAS"
        const val TIPO_LISTA_PAPELETAS = "PAPELETAS"
        private const val TAG = "BarcodeScanner"
        private const val DEBOUNCE_MS = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        // 🔥 FORÇA O FUNDO DA TELA PARA GARANTIR QUE A CÂMERA NÃO FIQUE PRETA
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        // Criando o PreviewView (a tela da câmera)
        previewView = PreviewView(this)
        previewView.id = View.generateViewId()
        previewView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val density = resources.displayMetrics.density

        val frameLayout = FrameLayout(this)
        frameLayout.addView(previewView)

        // Escurece ao redor do viewfinder (janela transparente no campo de leitura)
        val dimOverlay = DimOverlayView(this)
        frameLayout.addView(
            dimOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Botão voltar (canto superior esquerdo)
        val btnVoltar = TextView(this)
        btnVoltar.text = "‹"
        btnVoltar.setTextColor(android.graphics.Color.WHITE)
        btnVoltar.textSize = 34f
        btnVoltar.setPadding(
            (12 * density).toInt(), 0, (12 * density).toInt(), (8 * density).toInt()
        )
        btnVoltar.setOnClickListener { finish() }
        val voltarParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        voltarParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        voltarParams.setMargins(
            (4 * density).toInt(), (32 * density).toInt(), 0, 0
        )
        frameLayout.addView(btnVoltar, voltarParams)

        // View do viewfinder (borda branca)
        val viewfinder = View(this)
        viewfinder.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke((2 * density).toInt(), android.graphics.Color.WHITE)
        }
        frameLayout.addView(viewfinder)

        // Título (logo acima do viewfinder)
        val tvTitulo = TextView(this)
        tvTitulo.text = "Escanear código de barras"
        tvTitulo.setTextColor(android.graphics.Color.WHITE)
        tvTitulo.textSize = 18f
        tvTitulo.textAlignment = View.TEXT_ALIGNMENT_CENTER
        frameLayout.addView(
            tvTitulo,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Subtítulo (logo abaixo do viewfinder)
        val textView = TextView(this)
        textView.text = "Centralize o código para escanear"
        tvInstrucao = textView
        textView.setTextColor(0xB3FFFFFF.toInt())
        textView.textSize = 14f
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        frameLayout.addView(
            textView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        frameLayout.post {
            val vfW = (frameLayout.width * 0.85f).toInt()
            val vfH = (200 * density).toInt()
            val vfLeft = (frameLayout.width - vfW) / 2
            val vfTop = (frameLayout.height - vfH) / 2 - (30 * density).toInt()
            viewfinder.layoutParams = FrameLayout.LayoutParams(vfW, vfH).apply {
                leftMargin = vfLeft
                topMargin = vfTop
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            (tvTitulo.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = vfTop - (28 * density).toInt()
                gravity = android.view.Gravity.TOP
            }
            (textView.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = vfTop + vfH + (10 * density).toInt()
                gravity = android.view.Gravity.TOP
            }
        }

        // Badge inferior com o acumulado da sessão (clique volta para a lista)
        val badgeContador = TextView(this)
        badgeContador.text = "0 itens adicionados"
        badgeContador.setTextColor(0xFFD7193F.toInt())
        badgeContador.textSize = 14f
        badgeContador.setTypeface(null, android.graphics.Typeface.BOLD)
        badgeContador.textAlignment = View.TEXT_ALIGNMENT_CENTER
        badgeContador.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(0xFFFFFFFF.toInt())
        }
        badgeContador.setPadding(
            (16 * density).toInt(), (10 * density).toInt(),
            (16 * density).toInt(), (10 * density).toInt()
        )
        badgeContador.setOnClickListener { finish() }
        tvContador = badgeContador

        // Botão de envio direto da câmera
        val btnEnviarCamera = TextView(this)
        btnEnviarCamera.text = "ENVIAR IMPRESSÃO"
        btnEnviarCamera.setTextColor(android.graphics.Color.WHITE)
        btnEnviarCamera.textSize = 13f
        btnEnviarCamera.setTypeface(null, android.graphics.Typeface.BOLD)
        btnEnviarCamera.textAlignment = View.TEXT_ALIGNMENT_CENTER
        btnEnviarCamera.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(0xFFD7193F.toInt())
        }
        btnEnviarCamera.setPadding(
            (16 * density).toInt(), (10 * density).toInt(),
            (16 * density).toInt(), (10 * density).toInt()
        )
        btnEnviarCamera.setOnClickListener {
            if (tipoLista == TIPO_LISTA_PAPELETAS) {
                abrirPreviewPapeleta()
            } else {
                escolherEtiquetasEEnviar()
            }
        }

        val btnPapeletaCamera = TextView(this)
        btnPapeletaCamera.text = "IMPRIMIR PAPELETA"
        btnPapeletaCamera.setTextColor(android.graphics.Color.WHITE)
        btnPapeletaCamera.textSize = 13f
        btnPapeletaCamera.setTypeface(null, android.graphics.Typeface.BOLD)
        btnPapeletaCamera.textAlignment = View.TEXT_ALIGNMENT_CENTER
        btnPapeletaCamera.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(0xFFD7193F.toInt())
        }
        btnPapeletaCamera.setPadding(
            (16 * density).toInt(), (10 * density).toInt(),
            (16 * density).toInt(), (10 * density).toInt()
        )
        btnPapeletaCamera.setOnClickListener { abrirPreviewPapeleta() }

        // Badge para digitar o EAN manualmente (branco com texto vermelho)
        val btnDigitarEan = TextView(this)
        btnDigitarEan.text = "DIGITAR EAN"
        btnDigitarEan.setTextColor(0xFFD7193F.toInt())
        btnDigitarEan.textSize = 14f
        btnDigitarEan.setTypeface(null, android.graphics.Typeface.BOLD)
        btnDigitarEan.textAlignment = View.TEXT_ALIGNMENT_CENTER
        btnDigitarEan.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(0xFFFFFFFF.toInt())
        }
        btnDigitarEan.setPadding(
            (16 * density).toInt(), (10 * density).toInt(),
            (16 * density).toInt(), (10 * density).toInt()
        )
        btnDigitarEan.setOnClickListener { digitarEanManual() }

        // Layout 2x2: contador + digitar EAN (topo); enviar + imprimir (baixo)
        fun linhaBadges(esq: View, dir: View): android.widget.LinearLayout {
            val linha = android.widget.LinearLayout(this)
            linha.orientation = android.widget.LinearLayout.HORIZONTAL
            linha.gravity = android.view.Gravity.CENTER
            val lpEsq = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            lpEsq.marginEnd = (4 * density).toInt()
            val lpDir = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            linha.addView(esq, lpEsq)
            linha.addView(dir, lpDir)
            return linha
        }

        val btnLanterna = TextView(this)
        btnLanterna.text = "Lanterna"
        btnLanterna.setTextColor(android.graphics.Color.WHITE)
        btnLanterna.textSize = 16f
        btnLanterna.setTypeface(null, android.graphics.Typeface.BOLD)
        btnLanterna.textAlignment = View.TEXT_ALIGNMENT_CENTER
        btnLanterna.gravity = android.view.Gravity.CENTER
        fun estiloLanterna() {
            btnLanterna.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(
                    if (lanternaOn) android.graphics.Color.WHITE
                    else android.graphics.Color.TRANSPARENT
                )
                setStroke((1.5f * density).toInt(), android.graphics.Color.WHITE)
            }
            btnLanterna.setTextColor(
                if (lanternaOn) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            )
        }
        estiloLanterna()
        btnLanterna.setOnClickListener {
            val cam = camera
            if (cam != null) {
                lanternaOn = !lanternaOn
                cam.cameraControl.enableTorch(lanternaOn)
                estiloLanterna()
            }
        }

        val barraInferior = android.widget.LinearLayout(this)
        barraInferior.orientation = android.widget.LinearLayout.VERTICAL
        barraInferior.gravity = android.view.Gravity.CENTER_HORIZONTAL
        barraInferior.setPadding(
            (16 * density).toInt(), 0, (16 * density).toInt(), 0
        )
        barraInferior.addView(
            btnLanterna,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * density).toInt()
            ).apply { bottomMargin = (12 * density).toInt() }
        )
        barraInferior.addView(
            linhaBadges(badgeContador, btnDigitarEan),
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val baixoParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        baixoParams.topMargin = (8 * density).toInt()
        barraInferior.addView(
            linhaBadges(btnEnviarCamera, btnPapeletaCamera),
            baixoParams
        )

        val barraParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        barraParams.gravity = android.view.Gravity.BOTTOM
        barraParams.bottomMargin = (56 * density).toInt()
        frameLayout.addView(barraInferior, barraParams)

        setContentView(frameLayout)

        // Modo contínuo: permanece na câmera adicionando itens à lista
        modoContinuo = intent.getBooleanExtra(EXTRA_MODO_CONTINUO, false)
        tipoLista = intent.getStringExtra(EXTRA_TIPO_LISTA) ?: TIPO_LISTA_ETIQUETAS
        if (modoContinuo) {
            try {
                sessionManager = SessionManager(this)
                listaViewModel = ViewModelProvider(this)[ListaViewModel::class.java]
                apiService = ApiClient.getSlApiService(sessionManager)
                continuoPronto = true
                tvInstrucao.text = "Centralize o código para escanear"
            } catch (e: Exception) {
                LogHelper.e("BarcodeScanner: falha modo contínuo, usando tiro único", e)
                modoContinuo = false
            }
        }

        // 🔥 INICIALIZA A CÂMERA E O SCANNER
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            tomBip?.release()
        } catch (_: Exception) {
        }
        tomBip = null
        cameraExecutor.shutdown()
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                ToastUtils.show(this, "Permissão da câmera necessária", Toast.LENGTH_LONG)
                finish()
            }
        }
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        // 🔥 FORÇA O PREVIEW DA CÂMERA COM UM PROCESSO ASSÍNCRONO
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Inicializa o Preview da câmera
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider) // 🔥 ESSA LINHA É MUITO IMPORTANTE!

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient()

            // Analisador de imagem para leitura do código
            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val raw = barcodes.first()?.rawValue
                                if (raw.isNullOrEmpty()) return@addOnSuccessListener
                                val rawValue = normalizarEan(raw)
                                if (modoContinuo && continuoPronto) {
                                    val agora = System.currentTimeMillis()
                                    if (!processandoLeitura &&
                                        (rawValue != ultimoCodigo || agora - ultimoCodigoTempo > DEBOUNCE_MS)) {
                                        processandoLeitura = true
                                        ultimoCodigo = rawValue
                                        ultimoCodigoTempo = agora
                                        processarLeituraContinua(rawValue)
                                    }
                                } else if (!isScanning) {
                                    isScanning = true
                                    Log.d(TAG, "EAN Encontrado: $rawValue")
                                    val intent = Intent().putExtra(EXTRA_EAN, rawValue)
                                    setResult(RESULT_OK, intent)
                                    finish()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Erro no scanner ML Kit", e)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar a câmera", e)
                ToastUtils.show(this, "Erro ao iniciar a câmera")
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---- Leitura contínua: busca o EAN e adiciona direto na lista ----
    // Bip curto de confirmação quando o item entra na lista
    private fun biparAdicionado() {
        try {
            if (tomBip == null) {
                tomBip = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            }
            tomBip?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            LogHelper.e("BarcodeScanner: erro ao bipar", e)
        }
    }

    // Bip de erro (leitura falhou): tom de erro, mais longo que o de sucesso
    private fun biparErro() {
        try {
            if (tomBip == null) {
                tomBip = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            }
            tomBip?.startTone(ToneGenerator.TONE_SUP_ERROR, 400)
        } catch (e: Exception) {
            LogHelper.e("BarcodeScanner: erro ao bipar erro", e)
        }
    }

    private fun atualizarContadorScanner() {
        tvContador.text = if (itensAdicionadosSessao == 1) "1 item adicionado"
        else "$itensAdicionadosSessao itens adicionados"
    }

    private fun processarLeituraContinua(ean: String) {
        if (tipoLista == TIPO_LISTA_PAPELETAS) {
            processarLeituraContinuaPapeleta(ean)
            return
        }
        lifecycleScope.launch {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val response = withContext(Dispatchers.IO) {
                    apiService.getSingleLabelByEan(
                        storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                        ean = ean,
                        description = null,
                        sapId = null,
                        startDate = currentDate
                    )
                }
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null && data.items?.isNotEmpty() == true) {
                        withContext(Dispatchers.IO) {
                            val lista = listaViewModel.getEtiquetas()
                            data.items.forEach { item ->
                                val idx = lista.indexOfFirst { it.ean == item.ean }
                                if (idx >= 0) {
                                    lista[idx] = lista[idx].copy(quantity = lista[idx].quantity + 1)
                                } else {
                                    val printingData = item.printingData?.let { singleData ->
                                        PrintingData(
                                            ean = singleData.ean,
                                            description = singleData.description,
                                            department = singleData.department,
                                            displayPrice = singleData.displayPrice,
                                            price = singleData.price,
                                            promotionalPrice = singleData.promotionalPrice,
                                            quantity = 1,
                                            movementType = singleData.movementType,
                                            unit = singleData.unit,
                                            unitQty = singleData.unitQty,
                                            unitValue = singleData.unitValue,
                                            printUnitValue = singleData.printUnitValue,
                                            codSap = singleData.codSap,
                                            takeAndWin = singleData.takeAndWin,
                                            referenceDate = singleData.referenceDate,
                                            template = singleData.template ?: if (singleData.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO else null,
                                            productName = null,
                                            promotionPrice = null,
                                            takeAndWinQuantity = null,
                                            takeAndWinPrice = null,
                                            takeAndWinPercent = null,
                                            installments = singleData.installments
                                        )
                                    }
                                    lista.add(
                                        PriceTag(
                                            item.id, item.sap, item.department, item.ean, item.description,
                                            item.startDate, item.endDate, item.duration, item.price, item.movement,
                                            item.status, false, 1, printingData
                                        )
                                    )
                                }
                            }
                            listaViewModel.saveEtiquetas(lista)
                            listaViewModel.setColagem(ean)
                        }
                        itensAdicionadosSessao += data.items.size
                        atualizarContadorScanner()
                        biparAdicionado()
                    } else {
                        biparErro()
                    }
                } else {
                    if (response.code() == 401) {
                        SessionExpiredHandler.handleSessionExpired(this@BarcodeScannerActivity)
                        finish()
                        return@launch
                    }
                    biparErro()
                }
            } catch (e: Exception) {
                LogHelper.e("BarcodeScanner: erro na leitura contínua", e)
                biparErro()
            } finally {
                processandoLeitura = false
            }
        }
    }

    // ---- Leitura contínua (papeletas avulsas): busca o EAN e adiciona na lista ----
    private fun processarLeituraContinuaPapeleta(ean: String) {
        lifecycleScope.launch {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDate = dateFormat.format(Date())
                val storeId = sessionManager.getUserStore() ?: Constants.DEFAULT_STORE
                val responses = withContext(Dispatchers.IO) {
                    listOf(Constants.TIPO_COMUM, Constants.TIPO_PROMOCIONAL).map { type ->
                        try {
                            apiService.getPriceSignStandalone(
                                storeId = storeId,
                                type = type,
                                ean = ean,
                                sapId = null,
                                description = null,
                                startDate = currentDate
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                if (responses.any { it?.code() == 401 }) {
                    withContext(Dispatchers.Main) {
                        SessionExpiredHandler.handleSessionExpired(this@BarcodeScannerActivity)
                        finish()
                    }
                    return@launch
                }
                val adicionados = withContext(Dispatchers.IO) {
                    var count = 0
                    val lista = listaViewModel.getPapeletas()
                    responses.forEach { response ->
                        val data = response?.takeIf { it.isSuccessful }?.body()
                        data?.items?.forEach { item ->
                            val idx = lista.indexOfFirst { it.ean == item.ean }
                            if (idx >= 0) {
                                lista[idx].quantity += 1
                            } else {
                                lista.add(item.copy(quantity = 1))
                            }
                            count++
                        }
                    }
                    if (count > 0) {
                        listaViewModel.savePapeletas(lista)
                        listaViewModel.setColagem(ean)
                    }
                    count
                }
                if (adicionados > 0) {
                    itensAdicionadosSessao += adicionados
                    atualizarContadorScanner()
                    biparAdicionado()
                } else {
                    biparErro()
                }
            } catch (e: Exception) {
                LogHelper.e("BarcodeScanner: erro na leitura contínua (papeletas)", e)
                biparErro()
            } finally {
                processandoLeitura = false
            }
        }
    }

    private fun valorEtiqueta(item: PriceTag): String {
        val pd = item.printingData
        val tipo = tipoEtiqueta(item)
        val normal = pd?.price?.value ?: 0.0
        val promo = pd?.promotionalPrice?.value
        val take = pd?.takeAndWin
        val inst = pd?.installments
        return when {
            tipo == "Leve e Ganhe" &&
                take != null &&
                take.qty > 0 &&
                take.totalValue > 0 -> {
                val total =
                    if (pd?.template == Constants.SIGN_TEMPLATE_LEVE_GANHE_CADA &&
                        (promo ?: 0.0) > 0
                    ) promo!! else take.totalValue
                "Leve ${take.qty} por ${formatarValor(total)}"
            }
            tipo == "Parcelado" &&
                inst != null &&
                inst.installmentQuantity > 0 -> {
                val parcela =
                    if (inst.installmentValue > 0) inst.installmentValue
                    else (pd?.displayPrice ?: 0.0)
                val base = if (normal > 0) "${formatarValor(normal)} | " else ""
                "$base${inst.installmentQuantity}x de ${formatarValor(parcela)}"
            }
            tipo == "De/Por" && (promo ?: 0.0) > 0 ->
                "De ${formatarValor(normal)} | Por ${formatarValor(promo!!)}"
            normal > 0 -> formatarValor(normal)
            (promo ?: 0.0) > 0 -> formatarValor(promo!!)
            else -> normalizarValor(item.price)
        }
    }

    private fun valorPapeleta(item: io.loja.network.PriceSign): String {
        val pd = item.printingData
        val tipo = tipoPapeleta(item)
        val normal = pd?.price ?: 0.0
        val promo = pd?.promotionPrice
        val takeQty = pd?.takeAndWinQuantity ?: 0
        val instQty = pd?.installmentQuantity ?: 0
        return when {
            tipo == "Leve e Ganhe" && takeQty > 0 -> {
                val total = Constants.totalLeveGanhePorTemplate(
                    pd?.template,
                    pd?.promotionPrice,
                    pd?.takeAndWinPrice,
                    takeQty,
                )
                "Leve $takeQty por ${formatarValor(total)}"
            }
            tipo == "Parcelado" && instQty > 0 -> {
                val parcela = pd?.installmentPrice ?: 0.0
                val base = if (normal > 0) "${formatarValor(normal)} | " else ""
                "$base${instQty}x de ${formatarValor(parcela)}"
            }
            tipo == "De/Por" && (promo ?: 0.0) > 0 ->
                "De ${formatarValor(normal)} | Por ${formatarValor(promo!!)}"
            normal > 0 -> formatarValor(normal)
            (promo ?: 0.0) > 0 -> formatarValor(promo!!)
            else -> normalizarValor(item.price)
        }
    }

    private fun formatarValor(value: Double): String =
        "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)

    private fun normalizarValor(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "R$ --"
        return if (value.uppercase(Locale.getDefault()).startsWith("R$")) {
            value
        } else {
            "R$ $value"
        }
    }

    private fun tipoEtiqueta(item: PriceTag): String {
        val pd = item.printingData
        val take = pd?.takeAndWin
        return classificarTipo(
            template = pd?.template,
            movement = item.movement,
            takeAndWin = (take != null && take.qty > 0 && take.totalValue > 0) ||
                (pd?.takeAndWinQuantity ?: 0) > 0,
            installmentQty = pd?.installments?.installmentQuantity,
            promoPrice = pd?.promotionalPrice?.value,
            normalPrice = pd?.price?.value ?: 0.0,
        )
    }

    private fun tipoPapeleta(item: io.loja.network.PriceSign): String {
        val pd = item.printingData
        return classificarTipo(
            template = pd?.template,
            movement = item.movement,
            takeAndWin = (pd?.takeAndWinQuantity ?: 0) > 0,
            installmentQty = pd?.installmentQuantity,
            promoPrice = pd?.promotionPrice,
            normalPrice = pd?.price ?: 0.0,
        )
    }

    private fun classificarTipo(
        template: String?,
        movement: String,
        takeAndWin: Boolean,
        installmentQty: Int?,
        promoPrice: Double?,
        normalPrice: Double,
    ): String {
        val t = template?.lowercase(Locale.getDefault()) ?: ""
        val m = movement.lowercase(Locale.getDefault())
        if (t.contains("parcelad") ||
            m.contains("parcelad") ||
            (installmentQty ?: 0) > 0
        ) {
            return "Parcelado"
        }
        if (t.contains("leve") || m.contains("leve") || takeAndWin) {
            return "Leve e Ganhe"
        }
        val promo = promoPrice ?: 0.0
        if ((promo > 0 && (normalPrice <= 0 || promo < normalPrice)) ||
            t.contains("de/por") ||
            t.contains("de_por") ||
            t.contains("depor") ||
            t.contains("por_de") ||
            m.contains("de/por") ||
            m.contains("depor") ||
            m.contains("promocional")
        ) {
            return "De/Por"
        }
        return "Normal"
    }

    private fun corTipo(tipo: String): Int {
        return when (tipo) {
            "Leve e Ganhe" -> 0xFF2E7D32.toInt()
            "De/Por", "Parcelado" -> 0xFFFF9800.toInt()
            else -> 0xFF9E9E9E.toInt()
        }
    }

    private fun adicionarBadgeValor(
        parent: LinearLayout,
        item: ItemScanner,
        dp: (Int) -> Int,
    ) {
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(0xFF4CAF50.toInt())
                cornerRadius = dp(6).toFloat()
            }
        }
        val valor = TextView(this).apply {
            text = item.valor
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), dp(6), dp(4))
        }
        badge.addView(
            valor,
            LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        )
        val tipo = TextView(this).apply {
            text = item.tipo.uppercase(Locale.getDefault())
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(corTipo(item.tipo))
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    dp(6).toFloat(), dp(6).toFloat(),
                    dp(6).toFloat(), dp(6).toFloat(),
                    0f, 0f
                )
                setStroke(dp(1), 0xFF4CAF50.toInt())
            }
        }
        badge.addView(
            tipo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        parent.addView(
            badge,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)
            ).apply { topMargin = dp(4) }
        )
    }

    private fun escolherEtiquetasEEnviar() {
        if (!modoContinuo) {
            ToastUtils.show(this, "Envio disponível no modo contínuo")
            return
        }
        val etiquetas = listaViewModel.getEtiquetas()
        if (etiquetas.isEmpty()) {
            ToastUtils.show(this, "Nenhuma etiqueta adicionada")
            return
        }
        val itens = etiquetas.map {
            ItemScanner(
                it.ean,
                it.description,
                it.quantity,
                tipoEtiqueta(it),
                valorEtiqueta(it),
            )
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val qtds = IntArray(itens.size) {
            itens[it].quantity.coerceIn(1, 999)
        }
        val selecionados = BooleanArray(itens.size) { true }

        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(),
                    dp(16).toFloat(), dp(16).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
        }
        container.addView(TextView(this).apply {
            text = "ENVIAR ETIQUETAS"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1E293B.toInt())
        })
        container.addView(TextView(this).apply {
            text = "Escolha quais etiquetas enviar e a quantidade de cada uma"
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })

        val linhas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        for ((i, item) in itens.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val dados = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            dados.addView(TextView(this).apply {
                text = item.description.ifEmpty { item.ean }
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF1E293B.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            dados.addView(TextView(this).apply {
                text = item.ean
                textSize = 11f
                setTextColor(0xFF64748B.toInt())
            })
            adicionarBadgeValor(dados, item) { v -> dp(v) }

            val input = EditText(this).apply {
                setText(qtds[i].toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(3))
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF1E293B.toInt())
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setSingleLine(true)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), 0xFFC8C6C4.toInt())
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val v = s?.toString()?.toIntOrNull()
                        if (v != null && v > 0) {
                            qtds[i] = v.coerceAtMost(999)
                        }
                    }
                })
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val v = (text.toString().toIntOrNull() ?: 1)
                            .coerceIn(1, 999)
                        qtds[i] = v
                        setText(v.toString())
                        setSelection(text.length)
                    }
                }
            }
            val check = CheckBox(this).apply {
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    selecionados[i] = checked
                    input.isEnabled = checked
                    input.alpha = if (checked) 1f else 0.4f
                }
            }
            row.addView(check)
            row.addView(
                dados,
                LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            )
            row.addView(
                input,
                LinearLayout.LayoutParams(dp(52), dp(38)).apply {
                    marginStart = dp(6)
                }
            )
            linhas.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            )
        }

        val scroll = ScrollView(this).apply {
            addView(linhas)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)
            )
        }
        container.addView(scroll)

        val btnEnviar = MaterialButton(this).apply {
            text = "ENVIAR"
            setTextColor(Color.WHITE)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFD7193F.toInt())
            cornerRadius = dp(8)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnClickListener {
                val selItens = mutableListOf<ItemScanner>()
                val selQtds = mutableListOf<Int>()
                for (idx in itens.indices) {
                    if (selecionados[idx]) {
                        selItens.add(itens[idx])
                        selQtds.add(qtds[idx].coerceIn(1, 999))
                    }
                }
                if (selItens.isEmpty()) {
                    ToastUtils.show(
                        this@BarcodeScannerActivity,
                        "Selecione ao menos uma etiqueta"
                    )
                    return@setOnClickListener
                }
                sheet.dismiss()
                escolherImpressora(selItens, selQtds)
            }
        }
        container.addView(
            btnEnviar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(16) }
        )

        sheet.setContentView(container)
        sheet.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        sheet.show()
        sheet.behavior.isDraggable = false
        sheet.behavior.state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun escolherImpressora(itens: List<ItemScanner>, qtds: List<Int>) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enviar impressão")
            .setItems(arrayOf("Zebra 1", "Zebra 2")) { _, which ->
                val printer = if (which == 0) Constants.PRINTER_ZEBRA_1
                else Constants.PRINTER_ZEBRA_2
                enviarImpressao(printer, itens, qtds)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarImpressao(
        printerId: String,
        itens: List<ItemScanner>,
        qtds: List<Int>,
    ) {
        val qtdPorEan = itens.mapIndexed { i, item ->
            item.ean to qtds.getOrElse(i) { 1 }.coerceIn(1, 999)
        }.toMap()
        val etiquetas = listaViewModel.getEtiquetas()
        val lista = mutableListOf<io.loja.network.PrintingData>()
        etiquetas.forEach { item ->
            val qtd = qtdPorEan[item.ean] ?: return@forEach
            val pd = item.printingData ?: return@forEach
            val template =
                if (pd.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO
                else Constants.templateNormalizado(pd.template)
            repeat(qtd) {
                lista.add(pd.copy(quantity = 1, template = template))
            }
        }
        if (lista.isEmpty()) {
            ToastUtils.show(this, "Dados de impressão não disponíveis")
            return
        }
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    apiService.sendPriceTagsToPrinter(
                        sessionManager.getUserStore() ?: Constants.DEFAULT_STORE,
                        printerId,
                        Constants.TAG_GONDOLA,
                        io.loja.network.SendPriceTagsRequest(lista),
                    )
                }
                if (resp.isSuccessful) {
                    ToastUtils.show(
                        this@BarcodeScannerActivity,
                        "${lista.size} etiqueta(s) enviada(s) para impressão!",
                        Toast.LENGTH_LONG
                    )
                    finish()
                } else {
                    ToastUtils.show(
                        this@BarcodeScannerActivity,
                        "Erro ao enviar: ${resp.code()}",
                        Toast.LENGTH_LONG
                    )
                }
            } catch (e: Exception) {
                ToastUtils.show(
                    this@BarcodeScannerActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                )
            }
        }
    }

    private fun normalizarEan(raw: String): String {
        if (!raw.startsWith("0")) return raw
        val semZeros = raw.trimStart('0')
        return if (semZeros.isEmpty()) raw else semZeros
    }

    private fun digitarEanManual() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Digite o EAN"
            setPadding(32, 24, 32, 24)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Digitar EAN")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val ean = normalizarEan(input.text.toString().trim())
                if (ean.isEmpty()) return@setPositiveButton
                if (modoContinuo) {
                    processarLeituraContinua(ean)
                } else {
                    val intent = Intent().putExtra(EXTRA_EAN, ean)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirPreviewPapeleta() {
        if (!modoContinuo) {
            ToastUtils.show(this, "Disponível no modo contínuo")
            return
        }
        // O scanner de papeletas grava em getPapeletas(); o de etiquetas em
        // getEtiquetas(). Usa a lista correta para a quantidade bater.
        val itens: List<ItemScanner> =
            if (tipoLista == TIPO_LISTA_PAPELETAS) {
                listaViewModel.getPapeletas().map {
                    ItemScanner(
                        it.ean,
                        it.description,
                        it.quantity,
                        tipoPapeleta(it),
                        valorPapeleta(it),
                    )
                }
            } else {
                listaViewModel.getEtiquetas().map {
                    ItemScanner(
                        it.ean,
                        it.description,
                        it.quantity,
                        tipoEtiqueta(it),
                        valorEtiqueta(it),
                    )
                }
            }
        if (itens.isEmpty()) {
            ToastUtils.show(this, "Nenhum item adicionado")
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        // Quantidade inicial = a quantidade da lista (input/escaneada).
        val qtds = IntArray(itens.size) {
            itens[it].quantity.coerceIn(1, 999)
        }
        val selecionados = BooleanArray(itens.size) { true }
        val tamanhos = listOf(
            "1×1" to Constants.SIGN_SIZE_1X1,
            "2×1" to Constants.SIGN_SIZE_2X1,
            "4×1" to Constants.SIGN_SIZE_4X1,
            "6×1" to Constants.SIGN_SIZE_6X1,
        )
        var sizeIdx = 0

        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(),
                    dp(16).toFloat(), dp(16).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
        }
        container.addView(TextView(this).apply {
            text = "IMPRIMIR PAPELETA"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1E293B.toInt())
        })
        container.addView(TextView(this).apply {
            text = "Escolha quais itens, a quantidade e o tamanho"
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })

        val linhas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        for ((i, item) in itens.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val dados = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            dados.addView(TextView(this).apply {
                text = item.description.ifEmpty { item.ean }
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF1E293B.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            dados.addView(TextView(this).apply {
                text = item.ean
                textSize = 11f
                setTextColor(0xFF64748B.toInt())
            })
            adicionarBadgeValor(dados, item) { v -> dp(v) }

            val input = EditText(this).apply {
                setText(qtds[i].toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(3))
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF1E293B.toInt())
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setSingleLine(true)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), 0xFFC8C6C4.toInt())
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val v = s?.toString()?.toIntOrNull()
                        if (v != null && v > 0) {
                            qtds[i] = v.coerceAtMost(999)
                        }
                    }
                })
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val v = (text.toString().toIntOrNull() ?: 1)
                            .coerceIn(1, 999)
                        qtds[i] = v
                        setText(v.toString())
                        setSelection(text.length)
                    }
                }
            }
            val check = CheckBox(this).apply {
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    selecionados[i] = checked
                    input.isEnabled = checked
                    input.alpha = if (checked) 1f else 0.4f
                }
            }
            row.addView(check)
            row.addView(
                dados,
                LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            )
            row.addView(
                input,
                LinearLayout.LayoutParams(dp(52), dp(38)).apply {
                    marginStart = dp(6)
                }
            )
            linhas.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            )
        }
        val scroll = ScrollView(this).apply {
            addView(linhas)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)
            )
        }
        container.addView(scroll)

        container.addView(TextView(this).apply {
            text = "Tamanho"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1E293B.toInt())
            setPadding(0, dp(8), 0, 0)
        })
        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(0, dp(8), 0, 0)
        }
        val fundoChip = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFFD7193F.toInt(), 0xFFF1F5F9.toInt())
        )
        val textoChip = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.WHITE, 0xFF334155.toInt())
        )
        for ((idx, t) in tamanhos.withIndex()) {
            val chip = Chip(this).apply {
                text = t.first
                isCheckable = true
                isChecked = idx == 0
                isClickable = true
                chipBackgroundColor = fundoChip
                setTextColor(textoChip)
                chipStrokeWidth = 0f
                checkedIcon = ContextCompat.getDrawable(
                    this@BarcodeScannerActivity,
                    R.drawable.ic_check_circle_dark
                )
                isCheckedIconVisible = true
                checkedIconTint = null
                setOnCheckedChangeListener { _, checked ->
                    if (checked) sizeIdx = idx
                }
            }
            chipGroup.addView(chip)
        }
        container.addView(chipGroup)

        val btnGerar = MaterialButton(this).apply {
            text = "VISUALIZAR PAPELETA"
            setTextColor(Color.WHITE)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFD7193F.toInt())
            cornerRadius = dp(8)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnClickListener {
                val size = tamanhos[sizeIdx].second
                val selItens = itens.filterIndexed { i, _ -> selecionados[i] }
                val selQtds = qtds
                    .filterIndexed { i, _ -> selecionados[i] }
                    .map { it.coerceIn(1, 999) }
                if (selItens.isEmpty()) {
                    ToastUtils.show(
                        this@BarcodeScannerActivity,
                        "Selecione ao menos um item"
                    )
                    return@setOnClickListener
                }
                sheet.dismiss()
                buscarPapeletasEAbrirPreview(selItens, selQtds, size)
            }
        }
        container.addView(
            btnGerar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(16) }
        )

        sheet.setContentView(container)
        sheet.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        sheet.show()
        sheet.behavior.isDraggable = false
        sheet.behavior.state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun buscarPapeletasEAbrirPreview(
        itens: List<ItemScanner>,
        qtds: List<Int>,
        size: String,
    ) {
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("Buscando papeletas...")
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            try {
                val data = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date())
                val respostas = withContext(Dispatchers.IO) {
                    itens.flatMap { item ->
                        listOf(
                            Constants.TIPO_COMUM,
                            Constants.TIPO_PROMOCIONAL
                        ).map { type ->
                            async {
                                try {
                                    apiService.getPriceSignStandalone(
                                        storeId = sessionManager.getUserStore()
                                            ?: Constants.DEFAULT_STORE,
                                        type = type,
                                        ean = item.ean,
                                        startDate = data,
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                }
                val vistos = mutableSetOf<String>()
                val lista =
                    mutableListOf<io.loja.network.PapeletaPrintingData>()
                val quantidades = mutableListOf<Int>()
                respostas.forEachIndexed { idx, response ->
                    if (response != null && response.isSuccessful) {
                        response.body()?.items?.forEach { item ->
                            val pd = item.printingData ?: return@forEach
                            if (!vistos.add(item.ean)) return@forEach
                            lista.add(
                                pd.copy(
                                    quantity = 1,
                                    size = Constants.SIGN_SIZE_1X1
                                )
                            )
                            quantidades.add(
                                qtds[(idx / 2).coerceIn(0, qtds.size - 1)]
                            )
                        }
                    }
                }
                progress.dismiss()
                if (lista.isEmpty()) {
                    ToastUtils.show(
                        this@BarcodeScannerActivity,
                        "Dados de papeleta não disponíveis"
                    )
                    return@launch
                }
                ModeloEditavelActivity.dadosPendentesList =
                    lista.toMutableList()
                val i = Intent(
                    this@BarcodeScannerActivity,
                    ModeloEditavelActivity::class.java
                ).apply {
                    putExtra(ModeloEditavelActivity.EXTRA_SEM_OVERLAY, false)
                    putExtra(ModeloEditavelActivity.EXTRA_SIZE, size)
                    putExtra(ModeloEditavelActivity.EXTRA_ENVIAR_API, false)
                    putExtra(ModeloEditavelActivity.EXTRA_API_SEM_OVERLAY, true)
                    putExtra(ModeloEditavelActivity.EXTRA_MODO_EDITAVEL, false)
                    putExtra(
                        ModeloEditavelActivity.EXTRA_MODO_VENCIMENTOS,
                        false
                    )
                    putExtra(
                        ModeloEditavelActivity.EXTRA_HIDE_GERAR_PREVIEW,
                        false
                    )
                    putExtra(
                        ModeloEditavelActivity.EXTRA_MOSTRAR_CHECKBOX_OVERLAY,
                        true
                    )
                    putExtra(
                        ModeloEditavelActivity.EXTRA_QUANTIDADES,
                        quantidades.joinToString(",")
                    )
                }
                startActivity(i)
            } catch (e: Exception) {
                progress.dismiss()
                ToastUtils.show(
                    this@BarcodeScannerActivity,
                    "Erro: ${e.message}"
                )
            }
        }
    }
}
private class DimOverlayView(context: android.content.Context) : View(context) {
    private val dimPaint = android.graphics.Paint().apply {
        color = 0xB3000000.toInt()
        isAntiAlias = true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val vfW = width * 0.85f
        val vfH = 200 * d
        val left = (width - vfW) / 2f
        val top = (height - vfH) / 2f - 30 * d
        val path = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
            addRoundRect(
                android.graphics.RectF(left, top, left + vfW, top + vfH),
                16 * d, 16 * d, android.graphics.Path.Direction.CW
            )
        }
        canvas.drawPath(path, dimPaint)
    }
}
