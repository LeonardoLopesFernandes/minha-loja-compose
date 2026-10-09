package io.loja.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.loja.R
import io.loja.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.Socket

class PdfViewerActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private var currentPage = 0
    private var filePath: String? = null
    private var overlayBitmap: Bitmap? = null
    private var overlaySize: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { carregarImagemSobreposicao(it) }
    }

    private lateinit var imageView: ImageView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnSelectImage: Button
    private lateinit var btnClearOverlay: Button
    private lateinit var btnPrint: Button
    private lateinit var btnSharePdf: Button
    private lateinit var tvPageInfo: TextView
    private lateinit var tvOverlayStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        overlaySize = intent.getStringExtra(EXTRA_OVERLAY_SIZE)
        val semOverlay = intent.getBooleanExtra(EXTRA_SEM_OVERLAY, false)

        if (filePath == null) {
            ToastUtils.show(this, "Arquivo não encontrado")
            finish()
            return
        }

        val file = File(filePath!!)
        if (!file.exists()) {
            ToastUtils.show(this, "Arquivo não encontrado")
            finish()
            return
        }

        imageView = findViewById(R.id.ivPdfPage)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnClearOverlay = findViewById(R.id.btnClearOverlay)
        btnPrint = findViewById(R.id.btnPrint)
        btnSharePdf = findViewById(R.id.btnSharePdf)
        tvPageInfo = findViewById(R.id.tvPageInfo)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)

        if (!semOverlay) carregarOverlayDoAssets()
        abrirPdf(file)

        btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnClearOverlay.setOnClickListener {
            overlayBitmap?.recycle()
            overlayBitmap = null
            tvOverlayStatus.visibility = View.GONE
            btnClearOverlay.visibility = View.GONE
            mostrarPagina(currentPage)
        }

        btnPrint.setOnClickListener {
            imprimirPdfDireto()
        }

        btnSharePdf.setOnClickListener {
            compartilharPdf()
        }

        btnPrev.setOnClickListener { mostrarPagina(currentPage - 1) }
        btnNext.setOnClickListener { mostrarPagina(currentPage + 1) }
    }

    private fun abrirPdf(file: File) {
        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            mostrarPagina(0)
        } catch (e: Exception) {
            ToastUtils.show(this, "Erro ao abrir PDF: ${e.message}")
            finish()
        }
    }

    private fun mostrarPagina(index: Int) {
        val r = renderer ?: return
        if (index < 0 || index >= r.pageCount) return

        btnPrev.isEnabled = false
        btnNext.isEnabled = false
        tvPageInfo.text = "Carregando..."
        val is6x1 = overlaySize?.uppercase() == "6X1"

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                r.openPage(index).use { page ->
                    val renderWidth = (page.width * 4).coerceAtMost(3600)
                    val renderHeight = (page.height.toFloat() * (renderWidth.toFloat() / page.width.toFloat())).toInt()

                    val pdfBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val output = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)

                    if (is6x1 && overlayBitmap != null) {
                        val cols = 3
                        val rows = 2
                        val halfW = renderWidth / cols.toFloat()
                        val halfH = renderHeight / rows.toFloat()
                        for (c in 0 until cols) {
                            for (r2 in 0 until rows) {
                                val left = c * halfW
                                val top = r2 * halfH
                                val tile = Bitmap.createScaledBitmap(overlayBitmap!!, halfW.toInt(), halfH.toInt(), true)
                                canvas.drawBitmap(tile, left, top, null)
                                tile.recycle()
                            }
                        }
                        val semBranco = removerBranco(pdfBitmap)
                        canvas.drawBitmap(semBranco, 0f, 0f, null)
                        semBranco.recycle()
                    } else {
                        overlayBitmap?.let { overlay ->
                            val bg = Bitmap.createScaledBitmap(overlay, renderWidth, renderHeight, true)
                            canvas.drawBitmap(bg, 0f, 0f, null)
                            val semBranco = removerBranco(pdfBitmap)
                            canvas.drawBitmap(semBranco, 0f, 0f, null)
                            semBranco.recycle()
                            bg.recycle()
                        } ?: run {
                            canvas.drawBitmap(pdfBitmap, 0f, 0f, null)
                        }
                    }

                    pdfBitmap.recycle()
                    output
                }
            }

            imageView.setImageBitmap(bitmap)
            currentPage = index
            tvPageInfo.text = "${index + 1} / ${r.pageCount}"
            btnPrev.isEnabled = index > 0
            btnNext.isEnabled = index < r.pageCount - 1
        }
    }

    private fun carregarOverlayDoAssets() {
        val size = overlaySize ?: return
        val nomeArquivo = if (size.uppercase() == "6X1") "1x1" else size.lowercase()
        var inputStream: java.io.InputStream? = null
        try {
            inputStream = assets.open("overlays/$nomeArquivo.png")
        } catch (_: Exception) {}
        if (inputStream == null) {
            try {
                inputStream = assets.open("overlays/$nomeArquivo.jpg")
            } catch (_: Exception) {}
        }
        if (inputStream != null) {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = false
            }
            overlayBitmap = BitmapFactory.decodeStream(inputStream, null, opts)
            try { inputStream.close() } catch (_: Exception) {}
            if (overlayBitmap != null) {
                tvOverlayStatus.text = "✅ Modelo: $nomeArquivo"
                tvOverlayStatus.visibility = View.VISIBLE
                btnClearOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun carregarImagemSobreposicao(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            overlayBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (overlayBitmap != null) {
                tvOverlayStatus.text = "✅ Modelo carregado"
                tvOverlayStatus.visibility = View.VISIBLE
                btnClearOverlay.visibility = View.VISIBLE
                ToastUtils.show(this, "Modelo carregado com sucesso!")
                mostrarPagina(currentPage)
            }
        } catch (e: Exception) {
            ToastUtils.show(this, "Erro ao carregar imagem: ${e.message}")
        }
    }

    private fun desenharFundo(canvas: Canvas, pdfWidth: Int, pdfHeight: Int, overlay: Bitmap) {
        val bg = Bitmap.createScaledBitmap(overlay, pdfWidth, pdfHeight, true)
        canvas.drawBitmap(bg, 0f, 0f, null)
    }

    private fun removerBranco(bitmap: Bitmap, tolerance: Int = 200): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            if (a > tolerance && r > tolerance && g > tolerance && b > tolerance) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun imprimirPdfDireto() {
        val path = filePath ?: return
        btnPrint.text = "IMPRIMINDO..."
        btnPrint.isEnabled = false

        val is6x1 = overlaySize?.uppercase() == "6X1"
        val isLandscape = overlaySize?.lowercase() != "1x1"
        val renderW = if (isLandscape) 3508 else 2480
        val renderH = if (isLandscape) 2480 else 3508
        val pageW = if (isLandscape) 842 else 595
        val pageH = if (isLandscape) 595 else 842

        lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    // Se o PDF já vem com o overlay embutido (papeletas diárias
                    // abrem o viewer com semOverlay=true), envia como está, sem
                    // re-renderizar (evita distorção de orientação/estouro de RAM).
                    if (overlayBitmap == null) {
                        return@withContext File(path!!)
                    }
                    val srcFile = File(path!!)
                    val fd = ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(fd)
                    val pdfDoc = PdfDocument()

                    for (i in 0 until r.pageCount) {
                        r.openPage(i).use { page ->
                            val pdfBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val output = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(output)

                            if (is6x1 && overlayBitmap != null) {
                                val cols = 3
                                val rows = 2
                                val halfW = renderW / cols.toFloat()
                                val halfH = renderH / rows.toFloat()
                                for (c in 0 until cols) {
                                    for (r2 in 0 until rows) {
                                        val left = c * halfW
                                        val top = r2 * halfH
                                        val tile = Bitmap.createScaledBitmap(overlayBitmap!!, halfW.toInt(), halfH.toInt(), true)
                                        canvas.drawBitmap(tile, left, top, null)
                                        tile.recycle()
                                    }
                                }
                                val semBranco = removerBranco(pdfBitmap)
                                canvas.drawBitmap(semBranco, 0f, 0f, null)
                                semBranco.recycle()
                            } else {
                                overlayBitmap?.let { overlay ->
                                    val bg = Bitmap.createScaledBitmap(overlay, renderW, renderH, true)
                                    canvas.drawBitmap(bg, 0f, 0f, null)
                                    val semBranco = removerBranco(pdfBitmap)
                                    canvas.drawBitmap(semBranco, 0f, 0f, null)
                                    semBranco.recycle()
                                    bg.recycle()
                                } ?: canvas.drawBitmap(pdfBitmap, 0f, 0f, null)
                            }

                            pdfBitmap.recycle()

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                            val docPage = pdfDoc.startPage(pageInfo)
                            docPage.canvas.drawBitmap(output, null, android.graphics.RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), null)
                            pdfDoc.finishPage(docPage)
                            output.recycle()
                        }
                    }

                    r.close()
                    fd.close()

                    val resultFile = File(cacheDir, "print_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(resultFile).use { pdfDoc.writeTo(it) }
                    pdfDoc.close()
                    resultFile
                }

                withContext(Dispatchers.IO) {
                    val printerIp = try {
                        io.loja.network.SessionManager(this@PdfViewerActivity).getPrinterIp()
                    } catch (_: Exception) {
                        io.loja.network.SessionManager.DEFAULT_PRINTER_IP
                    }
                    val socket = Socket()
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 256 * 1024
                    socket.connect(
                        java.net.InetSocketAddress(
                            printerIp,
                            io.loja.network.SessionManager.PRINTER_PORT
                        ),
                        3000
                    )
                    socket.soTimeout = 30000
                    val out: OutputStream = java.io.BufferedOutputStream(
                        socket.getOutputStream(), 64 * 1024)
                    out.write(pdfFile.readBytes())
                    out.flush()
                    // Aguarda o TCP transmitir tudo antes de encerrar
                    // (fechar imediato podia truncar o PDF -> startxref ausente).
                    try { Thread.sleep(500) } catch (_: InterruptedException) {}
                    try { out.close() } catch (_: Exception) {}
                    try { socket.shutdownOutput() } catch (_: Exception) {}
                    try { Thread.sleep(200) } catch (_: InterruptedException) {}
                    socket.close()
                }

                withContext(Dispatchers.Main) {
                    if (overlayBitmap != null) pdfFile.delete()
                    btnPrint.text = "🖨️ Imprimir"
                    btnPrint.isEnabled = true
                    ToastUtils.show(this@PdfViewerActivity, "✅ Enviado para impressora!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnPrint.text = "🖨️ Imprimir"
                    btnPrint.isEnabled = true
                    ToastUtils.show(this@PdfViewerActivity, "Erro: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun compartilharPdf() {
        val path = filePath ?: return
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar PDF com"))
        } catch (e: Exception) {
            ToastUtils.show(this, "Erro ao compartilhar: ${e.message}")
        }
    }

    override fun onDestroy() {
        try { renderer?.close() } catch (_: Exception) {}
        overlayBitmap?.recycle()
        overlayBitmap = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_OVERLAY_SIZE = "extra_overlay_size"
        const val EXTRA_SEM_OVERLAY = "extra_sem_overlay"
    }
}
