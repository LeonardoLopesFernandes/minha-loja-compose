package io.loja.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.loja.network.SessionManager
import io.loja.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val imagemHttp by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/** Abre o diálogo de imagem do produto pelo EAN (Americanas + SI5 Global). */
fun mostrarImagemProduto(context: Context, ean: String, descricao: String) {
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
    val titulo = TextView(context).apply {
        text = "Imagem do produto"
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF1E293B.toInt())
        gravity = Gravity.CENTER_HORIZONTAL
    }
    val subtitulo = TextView(context).apply {
        text = if (descricao.isBlank()) ean else descricao
        textSize = 11f
        setTextColor(0xFF64748B.toInt())
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, dp(10))
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
    container.addView(titulo)
    container.addView(subtitulo)
    container.addView(progress)
    container.addView(imagem)
    container.addView(fonte)
    container.addView(fechar)
    dialog.setContentView(container)
    dialog.window?.setLayout(
        (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    dialog.show()

    val store = SessionManager(context).getUserStore() ?: Constants.DEFAULT_STORE
    CoroutineScope(Dispatchers.Main).launch {
        val resultado = withContext(Dispatchers.IO) {
            buscarImagemProduto(ean, store)
        }
        progress.visibility = View.GONE
        if (resultado == null) {
            fonte.text = "Imagem não encontrada"
            fonte.visibility = View.VISIBLE
            return@launch
        }
        val bitmap = withContext(Dispatchers.IO) {
            baixarBitmap(resultado.first)
        }
        if (bitmap != null) {
            imagem.setImageBitmap(bitmap)
            imagem.visibility = View.VISIBLE
            fonte.text = "Fonte: ${resultado.second}"
            fonte.visibility = View.VISIBLE
        } else {
            fonte.text = "Não foi possível carregar a imagem"
            fonte.visibility = View.VISIBLE
        }
    }
}

private fun buscarImagemProduto(ean: String, store: String): Pair<String, String>? {
    buscarImagemAmericanas(ean, store)?.let { return it to "Americanas" }
    buscarImagemSi5(ean)?.let { return it to "SI5 Global" }
    return null
}

private fun buscarImagemAmericanas(ean: String, store: String): String? {
    return try {
        val url =
            "https://sl-bff-report.americanas.io/web/price-scanner/store/$store/ean/$ean"
        val request = Request.Builder().url(url).build()
        imagemHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            json.optString("imageUrl").trim().takeIf { it.startsWith("http") }
        }
    } catch (_: Exception) {
        null
    }
}

private fun buscarImagemSi5(ean: String): String? {
    return try {
        val codigo = ean.filter { it.isDigit() }
        if (codigo.isEmpty()) return null
        val request = Request.Builder()
            .url("https://global.si5.com.br/api/produtos/$codigo")
            .header("Authorization", "Bearer ${Constants.SI5_TOKEN}")
            .header("Accept", "application/json")
            .build()
        imagemHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            json.optString("imagem_url").trim().takeIf { it.startsWith("http") }
        }
    } catch (_: Exception) {
        null
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
