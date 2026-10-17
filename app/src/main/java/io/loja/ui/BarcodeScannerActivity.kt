package io.loja.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Texto de instrução
        val textView = TextView(this)
        textView.text = "Aproxime a câmera do código"
        tvInstrucao = textView
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 18f
        textView.setPadding(16, 16, 16, 16)
        textView.setBackgroundColor(0x80000000.toInt()) // Fundo preto semi-transparente
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        params.topMargin = 80
        textView.layoutParams = params

        val frameLayout = FrameLayout(this)
        frameLayout.addView(previewView)
        frameLayout.addView(textView)

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
        val density = resources.displayMetrics.density
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
        btnEnviarCamera.setOnClickListener { escolherImpressoraEEnviar() }

        val barraInferior = android.widget.LinearLayout(this)
        barraInferior.orientation = android.widget.LinearLayout.HORIZONTAL
        barraInferior.gravity = android.view.Gravity.CENTER_VERTICAL
        val badgeParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        badgeParams.marginEnd = (8 * density).toInt()
        barraInferior.addView(badgeContador, badgeParams)
        barraInferior.addView(
            btnEnviarCamera,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val barraParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        barraParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
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
                tvInstrucao.text = "Escaneie os itens — volte para concluir"
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
                                val rawValue = barcodes.first()?.rawValue
                                if (rawValue.isNullOrEmpty()) return@addOnSuccessListener
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
                cameraProvider.bindToLifecycle(
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

    private fun escolherImpressoraEEnviar() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enviar impressão")
            .setItems(arrayOf("Zebra 1", "Zebra 2")) { _, which ->
                val printer = if (which == 0) Constants.PRINTER_ZEBRA_1 else Constants.PRINTER_ZEBRA_2
                enviarImpressao(printer)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarImpressao(printerId: String) {
        if (!modoContinuo) {
            ToastUtils.show(this, "Envio disponível no modo contínuo")
            return
        }
        val etiquetas = listaViewModel.getEtiquetas()
        if (etiquetas.isEmpty()) {
            ToastUtils.show(this, "Nenhuma etiqueta adicionada")
            return
        }
        val lista = mutableListOf<io.loja.network.PrintingData>()
        etiquetas.forEach { item ->
            val pd = item.printingData ?: return@forEach
            lista.add(
                pd.copy(
                    quantity = 1,
                    template = if (pd.takeAndWin != null) Constants.SIGN_TEMPLATE_MODELO
                    else Constants.templateNormalizado(pd.template),
                )
            )
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
                    ToastUtils.show(this@BarcodeScannerActivity, "${lista.size} etiqueta(s) enviada(s) para impressão!", Toast.LENGTH_LONG)
                    finish()
                } else {
                    ToastUtils.show(this@BarcodeScannerActivity, "Erro ao enviar: ${resp.code()}", Toast.LENGTH_LONG)
                }
            } catch (e: Exception) {
                ToastUtils.show(this@BarcodeScannerActivity, "Erro: ${e.message}", Toast.LENGTH_LONG)
            }
        }
    }
}