package io.loja.ui

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.loja.network.SessionManager
import io.loja.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val imagemHttp by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/** Abre o diálogo de imagem do produto pelo EAN (Americanas + SI5 Global). */
fun mostrarImagemProduto(
    context: Context,
    ean: String,
    descricao: String,
    estoque: Double? = null,
    sap: String = "",
) {
    if (ean.isBlank()) return
    val density = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()

    val dialog = Dialog(context)
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
        }
    }
    val fecharTopo = TextView(context).apply {
        text = "×   FECHAR"
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF6B7280.toInt())
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(0, dp(8), 0, dp(12))
        setOnClickListener { dialog.dismiss() }
    }
    val subtitulo = TextView(context).apply {
        text = if (descricao.isBlank()) ean else descricao
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFD32F2F.toInt())
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, dp(2))
    }
    val eanView = TextView(context).apply {
        text = "EAN: $ean (toque para copiar)"
        textSize = 11f
        setTextColor(0xFF64748B.toInt())
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(2), 0, dp(10))
        setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("EAN", ean)
            )
            android.widget.Toast.makeText(
                context,
                "EAN copiado: $ean",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    val estoqueView = TextView(context).apply {
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(2), 0, dp(4))
        visibility = View.GONE
    }
    val sapTitulo = TextView(context).apply {
        text = "Informações do produto no SAP"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF1A1A1A.toInt())
        setPadding(0, dp(18), 0, dp(6))
    }
    val sapCode = TextView(context).apply {
        text = "Código SAP: " + if (sap.isBlank()) "-" else sap
        textSize = 13f
        setTextColor(0xFF666666.toInt())
        setPadding(0, 0, 0, dp(4))
    }
    val progress = ProgressBar(context).apply {
        isIndeterminate = true
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }
    val imagem = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(360)
        )
        visibility = View.GONE
    }
    val fonte = TextView(context).apply {
        textSize = 10f
        setTextColor(0xFF94A3B8.toInt())
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        visibility = View.GONE
    }
    val dica = TextView(context).apply {
        text = "Toque na imagem para salvar em Download/Imagem do produto"
        textSize = 10f
        setTextColor(0xFF94A3B8.toInt())
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(6), 0, 0)
        visibility = View.GONE
    }
    val fechar = TextView(context).apply {
        text = "FECHAR"
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(0xFFD32F2F.toInt())
            cornerRadius = dp(10).toFloat()
        }
        setPadding(0, dp(12), 0, dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
        setOnClickListener { dialog.dismiss() }
    }
    container.addView(fecharTopo)
    container.addView(subtitulo)
    container.addView(progress)
    container.addView(imagem)
    container.addView(estoqueView)
    container.addView(sapTitulo)
    container.addView(sapCode)
    container.addView(eanView)
    dialog.setContentView(container)
    dialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    dialog.show()

    val store = SessionManager(context).getUserStore() ?: Constants.DEFAULT_STORE
    CoroutineScope(Dispatchers.Main).launch {
        val saldo = estoque ?: withContext(Dispatchers.IO) {
            buscarEstoqueAmericanas(ean, store)
        }
        if (saldo != null) {
            estoqueView.text = "Estoque estimado: ${saldo.toInt()}"
            val cor = if (saldo < 0) 0xFFD81B3A.toInt() else 0xFF2E7D32.toInt()
            estoqueView.setTextColor(cor)
            estoqueView.background = GradientDrawable().apply {
                setColor(
                    if (saldo < 0) 0xFFFDECEC.toInt() else 0xFFE7F4E8.toInt()
                )
                cornerRadius = dp(16).toFloat()
            }
            estoqueView.setPadding(dp(12), dp(6), dp(12), dp(6))
            estoqueView.visibility = View.VISIBLE
        }

        val resultado = withContext(Dispatchers.IO) {
            buscarImagemProduto(ean, descricao, store)
        }
        progress.visibility = View.GONE
        if (resultado == null) {
            Toast.makeText(context, "Imagem não encontrada", Toast.LENGTH_LONG).show()
            return@launch
        }
        val bitmap = withContext(Dispatchers.IO) {
            baixarBitmap(resultado.first)
        }
        if (bitmap != null) {
            imagem.setImageBitmap(bitmap)
            imagem.visibility = View.VISIBLE
            imagem.setOnLongClickListener {
                android.app.AlertDialog.Builder(context)
                    .setTitle("Salvar imagem")
                    .setMessage("Deseja salvar a imagem em Download/Imagem do produto?")
                    .setPositiveButton("SALVAR") { _, _ ->
                        salvarImagem(context, ean, bitmap)
                    }
                    .setNegativeButton("CANCELAR", null)
                    .show()
                true
            }
        } else {
            Toast.makeText(
                context,
                "Não foi possível carregar a imagem",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun buscarEstoqueAmericanas(ean: String, store: String): Double? {
    val json = getJson(
        "https://sl-bff-report.americanas.io/web/price-scanner/store/$store/ean/$ean"
    ) ?: return null
    return when {
        json.has("stock") -> json.optDouble("stock", Double.NaN).takeIf { !it.isNaN() }
        json.has("saldo") -> json.optDouble("saldo", Double.NaN).takeIf { !it.isNaN() }
        json.has("quantidade") -> json.optDouble("quantidade", Double.NaN).takeIf { !it.isNaN() }
        else -> null
    }
}

private fun buscarImagemProduto(
    ean: String,
    descricao: String,
    store: String,
): Pair<String, String>? {
    buscarImagemAmericanas(ean, store)?.let { return it to "Americanas" }
    buscarImagemSi5(ean)?.let { return it to "SI5 Global" }
    buscarImagemBluesoftEan(ean)?.let { return it to "Bluesoft" }
    if (descricao.isNotBlank()) {
        buscarImagemBluesoftDescricao(descricao)?.let {
            return it to "Bluesoft"
        }
    }
    buscarImagemGoogleImages(ean, descricao)?.let {
        return it to "Google Imagens"
    }
    buscarImagemGoogleShopping(ean, descricao)?.let {
        return it to "Google Shopping"
    }
    return null
}

private fun getJson(
    url: String,
    headers: Map<String, String> = emptyMap(),
): JSONObject? {
    return try {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        imagemHttp.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) null else JSONObject(body)
        }
    } catch (_: Exception) {
        null
    }
}

private fun primeiraImagem(json: JSONObject?): String? {
    val images = json?.optJSONArray("images") ?: return null
    for (i in 0 until images.length()) {
        val s = images.optString(i).trim()
        if (s.startsWith("http")) {
            return s.replace("http://", "https://")
        }
    }
    return null
}

private fun buscarImagemAmericanas(ean: String, store: String): String? {
    val url =
        "https://sl-bff-report.americanas.io/web/price-scanner/store/$store/ean/$ean"
    val json = getJson(url)
    val img = json?.optString("imageUrl").orEmpty().trim()
    return img.takeIf { it.startsWith("http") }
}

private fun buscarImagemSi5(ean: String): String? {
    val codigo = ean.filter { it.isDigit() }
    if (codigo.isEmpty()) return null
    val json = getJson(
        "https://global.si5.com.br/api/produtos/$codigo",
        mapOf(
            "Authorization" to "Bearer ${Constants.SI5_TOKEN}",
            "Accept" to "application/json",
        ),
    )
    val img = json?.optString("imagem_url").orEmpty().trim()
    return img.takeIf { it.startsWith("http") }
}

private fun buscarImagemBluesoftEan(ean: String): String? {
    if (ean.isBlank()) return null
    val json = getJson(
        "https://api.cosmos.bluesoft.com.br/gtins/$ean",
        mapOf("X-API-KEY" to Constants.BLUESOFT_API_KEY),
    )
    return primeiraImagem(json)
}

private fun buscarImagemBluesoftDescricao(descricao: String): String? {
    val query = URLEncoder.encode(descricao, "UTF-8")
    val json = getJson(
        "https://api.cosmos.bluesoft.com.br/products?query=$query",
        mapOf("X-API-KEY" to Constants.BLUESOFT_API_KEY),
    )
    val produtos = json?.optJSONArray("products") ?: return null
    val primeiro = produtos.optJSONObject(0) ?: return null
    return primeiraImagem(primeiro)
}

private fun buscarImagemGoogleImages(
    ean: String,
    descricao: String,
): String? {
    val termo = listOf(descricao.trim(), ean).filter { it.isNotEmpty() }
        .joinToString(" ")
    if (termo.isEmpty()) return null
    val query = URLEncoder.encode("$termo produto", "UTF-8")
    val json = getJson(
        "https://serpapi.com/search?engine=google&q=$query&tbm=isch&api_key=${Constants.SERP_API_KEY}&ijn=0",
    )
    val images = json?.optJSONArray("images_results") ?: return null
    for (i in 0 until minOf(3, images.length())) {
        val item = images.optJSONObject(i) ?: continue
        var url = item.optString("original").trim()
        if (url.isEmpty()) url = item.optString("thumbnail").trim()
        if (url.startsWith("http") &&
            !url.contains("placeholder") &&
            !url.contains("default") &&
            !url.contains("no-image")
        ) {
            return url
        }
    }
    return null
}

private fun buscarImagemGoogleShopping(
    ean: String,
    descricao: String,
): String? {
    val termo = descricao.trim().ifEmpty { ean }
    if (termo.isEmpty()) return null
    val query = URLEncoder.encode(termo, "UTF-8")
    val json = getJson(
        "https://serpapi.com/search.json?engine=google&q=$query&tbm=shop&api_key=${Constants.SERP_API_KEY}&gl=br&hl=pt",
    )
    val shopping = json?.optJSONArray("shopping_results") ?: return null
    for (i in 0 until minOf(5, shopping.length())) {
        val item = shopping.optJSONObject(i) ?: continue
        val url = item.optString("thumbnail").trim()
            .replace("http://", "https://")
        if (url.startsWith("http")) return url
    }
    return null
}

private fun salvarImagem(context: Context, ean: String, bitmap: Bitmap) {
    val nome = "${ean.ifBlank { "produto" }}.png"
    try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Imagem do produto"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("insert")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("open")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val dir = File(downloads, "Imagem do produto")
            dir.mkdirs()
            val file = File(dir, nome)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        Toast.makeText(
            context,
            "Imagem salva em Download/Imagem do produto",
            Toast.LENGTH_LONG
        ).show()
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Não foi possível salvar a imagem",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun baixarBitmap(url: String): Bitmap? {
    return try {
        val request = Request.Builder().url(url).build()
        imagemHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (_: Exception) {
        null
    }
}
