package io.loja.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.loja.R
import io.loja.network.SessionManager
import io.loja.utils.LogHelper
import io.loja.utils.ToastUtils

class LoginWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutCarregamentoCustom: LinearLayout
    private lateinit var imgLogoPreenchimento: LogoAnimadoView
    private lateinit var tvStatusCarregamento: android.widget.TextView
    private lateinit var tvMarcaLogo: android.widget.TextView
    
    private var tokenEncontrado = false
    private var loginConcluido = false
    private var autoLogin = false
    private var silencioso = false
    private var handlerTimeout: Runnable? = null
    private var emailCredencial = ""
    private var senhaCredencial = ""
    private var ultimaInjecao = 0L
    private var tentativasMesmaPagina = 0
    private var ultimaUrlPreenchida = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_webview)

        autoLogin = intent.getBooleanExtra(EXTRA_AUTO_LOGIN, false)
        if (autoLogin) {
            val sessionManager = SessionManager(applicationContext)
            emailCredencial = intent.getStringExtra(EXTRA_EMAIL) ?: sessionManager.getUserEmail() ?: ""
            senhaCredencial = intent.getStringExtra(EXTRA_SENHA) ?: sessionManager.getSavedPassword() ?: ""
            if (emailCredencial.isBlank() || senhaCredencial.isBlank()) autoLogin = false
        }

        webView = findViewById(R.id.webView)
        layoutCarregamentoCustom = findViewById(R.id.layoutCarregamentoCustom)
        imgLogoPreenchimento = findViewById(R.id.imgLogoPreenchimento)
        tvStatusCarregamento = findViewById(R.id.tvStatusCarregamento)
        tvMarcaLogo = findViewById(R.id.tvMarcaLogo)
        tvMarcaLogo.animate().alpha(1f).setStartDelay(2800).setDuration(400).start()

        silencioso = autoLogin && intent.getBooleanExtra(EXTRA_SILENCIOSO, false)

        if (silencioso) {
            // Reconexão automática: WebView oculto, mostra apenas a logo pulsando
            webView.visibility = View.INVISIBLE
            layoutCarregamentoCustom.visibility = View.VISIBLE
            tvStatusCarregamento.text = "Reconectando automaticamente..."
            iniciarAnimacaoPulse()
            iniciarTimeoutReconnect()
        }

        configurarWebView()
        carregarLogin()
    }

    private fun iniciarTimeoutReconnect() {
        handlerTimeout = Runnable {
            if (!loginConcluido && !isFinishing && !isDestroyed) {
                LogHelper.d("Reconnect: tempo esgotado, mostrando WebView para conclusão manual")
                revelarWebViewManual("Não foi possível reconectar automaticamente. Conclua o login manualmente.")
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(handlerTimeout!!, TIMEOUT_RECONNECT_MS)
    }

    private fun cancelarTimeoutReconnect() {
        handlerTimeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        handlerTimeout = null
    }

    private fun revelarWebViewManual(mensagem: String) {
        if (!silencioso || loginConcluido || isFinishing || isDestroyed) return
        silencioso = false
        runOnUiThread {
            webView.visibility = View.VISIBLE
            layoutCarregamentoCustom.visibility = View.GONE
            pararAnimacaoPulse()
            ToastUtils.show(applicationContext, mensagem, Toast.LENGTH_LONG)
        }
    }

    private fun iniciarAnimacaoPulse() {
        runOnUiThread { imgLogoPreenchimento.iniciar() }
    }

    private fun pararAnimacaoPulse() {
        runOnUiThread { imgLogoPreenchimento.parar() }
    }

    private fun exibirCarregamento(exibir: Boolean) {
        if (isFinishing || isDestroyed) return
        if (silencioso && !loginConcluido) {
            // No modo silencioso o overlay fica sempre visível até concluir ou revelar o WebView
            return
        }
        runOnUiThread {
            if (loginConcluido || tokenEncontrado) {
                layoutCarregamentoCustom.visibility = View.VISIBLE
                iniciarAnimacaoPulse()
                return@runOnUiThread
            }
            
            if (exibir) {
                layoutCarregamentoCustom.visibility = View.VISIBLE
                iniciarAnimacaoPulse()
            } else {
                layoutCarregamentoCustom.visibility = View.GONE
                pararAnimacaoPulse()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    exibirCarregamento(true)

                    if (url != null && !loginConcluido) {
                        verificarCookies()
                        verificarTokenNaUrl(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    if (!loginConcluido && !tokenEncontrado) {
                        exibirCarregamento(false)
                        verificarCookies()
                        
                        if (url != null && url.startsWith("https://minhaloja.americanas.io")) {
                            verificarTokenViaJavaScript()
                        }
                        preencherLoginAutomatico()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (!loginConcluido) {
                        verificarCookies()
                        verificarTokenNaUrl(url)
                    }
                    return loginConcluido
                }
            }
        } catch (e: Exception) {
            LogHelper.e("WebView: Erro config", e)
        }
    }

    private fun verificarTokenNaUrl(url: String) {
        if (loginConcluido || tokenEncontrado) return
        val patterns = listOf(Regex("[?&]newToken=([^&]+)"), Regex("[?&]token=([^&]+)"))
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                val token = match.groupValues[1]
                if (token.isNotEmpty() && token.length > 50) {
                    salvarToken(token)
                    return
                }
            }
        }
    }

    private fun verificarCookies() {
        if (loginConcluido || tokenEncontrado) return
        try {
            val cookies = CookieManager.getInstance().getCookie("https://minhaloja.americanas.io")
            if (cookies != null) {
                val cookieList = cookies.split(";")
                for (cookie in cookieList) {
                    val cookieTrim = cookie.trim()
                    if (cookieTrim.startsWith("newToken=") || cookieTrim.startsWith("token=")) {
                        val token = cookieTrim.substring(cookieTrim.indexOf("=") + 1)
                        if (token.isNotEmpty() && token.length > 50) {
                            salvarToken(token)
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.e("WebView: Erro cookies", e)
        }
    }

    private fun verificarTokenViaJavaScript() {
        if (tokenEncontrado || loginConcluido) return
        val script = "(function() { return localStorage.getItem('newToken') || localStorage.getItem('token') || sessionStorage.getItem('newToken') || sessionStorage.getItem('token') || window.newToken || window.token; })();"
        
        webView.evaluateJavascript(script) { result ->
            if (result != "null" && result.length > 10 && !tokenEncontrado && !loginConcluido) {
                var token = result.trim().replace("\"", "")
                if (token.length > 50) {
                    salvarToken(token)
                }
            }
        }
    }

    private fun extrairInfoToken(
        token: String,
        sessionManager: SessionManager
    ): Triple<String, String, String> {
        val lojaAtual = sessionManager.getUserStore() ?: "L291"
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                var p = parts[1]
                while (p.length % 4 != 0) p += "="
                val json = org.json.JSONObject(
                    String(
                        android.util.Base64.decode(
                            p.replace("-", "+").replace("_", "/"),
                            android.util.Base64.DEFAULT
                        ),
                        Charsets.UTF_8
                    )
                )
                val user = if (json.has("user") && !json.isNull("user")) {
                    try { json.getJSONObject("user") } catch (_: Exception) { null }
                } else null
                var email = user?.optString("email")
                    ?: json.optString("email",
                        json.optString("preferred_username",
                            json.optString("upn", "")))
                var nome = user?.optString("nome")
                    ?: user?.optString("name")
                    ?: json.optString("name",
                        json.optString("given_name", ""))
                var loja = user?.optString("loja")
                    ?: json.optString("loja", "")
                if (loja.isNullOrEmpty()) {
                    val stores = user?.optJSONArray("stores")
                        ?: json.optJSONArray("stores")
                    loja = if (stores != null && stores.length() > 0)
                        stores.optString(0) else lojaAtual
                }
                if (email.isNullOrEmpty()) email = "usuario@americanas.io"
                if (nome.isNullOrEmpty()) {
                    nome = email.split("@").first()
                        .replace(".", " ").replace("_", " ")
                        .split(" ").joinToString(" ") {
                            if (it.isNotEmpty()) it[0].uppercase() + it.substring(1) else it
                        }
                }
                if (loja.isNullOrEmpty()) loja = lojaAtual
                return Triple(email, nome, loja)
            }
        } catch (e: Exception) {
            LogHelper.e("Login: erro ao decodificar JWT", e)
        }
        return Triple("usuario@americanas.io", "Usuário", lojaAtual)
    }

    // Lê o claim "exp" (epoch em segundos) do JWT, igual ao Flutter.
    private fun expDoToken(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            var p = parts[1]
            while (p.length % 4 != 0) p += "="
            val json = org.json.JSONObject(
                String(
                    android.util.Base64.decode(
                        p.replace("-", "+").replace("_", "/"),
                        android.util.Base64.DEFAULT
                    ),
                    Charsets.UTF_8
                )
            )
            if (json.has("exp") && !json.isNull("exp")) json.getLong("exp") else null
        } catch (e: Exception) {
            LogHelper.e("Login: erro ao ler exp do JWT", e)
            null
        }
    }

    private fun salvarToken(token: String) {
        if (loginConcluido) return
        loginConcluido = true
        tokenEncontrado = true
        cancelarTimeoutReconnect()

        exibirCarregamento(true)

        try {
            val sessionManager = SessionManager(applicationContext)
            sessionManager.saveTokenWithExpiry(token, expDoToken(token))
            val (email, nome, loja) = extrairInfoToken(token, sessionManager)
            sessionManager.saveUserInfo(email, nome, loja)
            
            runOnUiThread {
                ToastUtils.show(applicationContext, "✅ Login realizado!")
                val intent = Intent(this, io.loja.ui.compose.ComposeMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            loginConcluido = false
            tokenEncontrado = false
            exibirCarregamento(false)
        }
    }

    private fun preencherLoginAutomatico() {
        if (!autoLogin || loginConcluido || tokenEncontrado || isFinishing || isDestroyed) return
        val urlAtual = webView.url ?: return
        if (!urlAtual.contains("login.microsoftonline.com")) return
        if (System.currentTimeMillis() - ultimaInjecao < 1200) return

        if (urlAtual == ultimaUrlPreenchida) {
            tentativasMesmaPagina++
        } else {
            tentativasMesmaPagina = 0
            ultimaUrlPreenchida = urlAtual
        }
        if (tentativasMesmaPagina >= 8) {
            autoLogin = false
            if (silencioso) {
                revelarWebViewManual("Login automático falhou. Preencha manualmente.")
            } else {
                ToastUtils.show(applicationContext, "Login automático falhou. Preencha manualmente.", Toast.LENGTH_LONG)
            }
            return
        }

        ultimaInjecao = System.currentTimeMillis()
        val script = montarScriptPreenchimento(emailCredencial, senhaCredencial)
        webView.evaluateJavascript(script) { resultado ->
            val r = resultado?.trim()?.trim('"') ?: ""
            if (r == "no-field" || r == "email-no-btn" || r == "pwd-no-btn" || r == "kmsi-no-btn") {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) preencherLoginAutomatico()
                }, 1000)
            }
        }
    }

    private fun montarScriptPreenchimento(email: String, senha: String): String {
        val e = jsString(email)
        val s = jsString(senha)
        return "(function(){" +
            "var setV=function(el,v){var d=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');" +
            "if(d&&d.set){d.set.call(el,v);}else{el.value=v;}" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "el.dispatchEvent(new Event('change',{bubbles:true}));};" +
            "var btn=document.getElementById('idSIButton9');" +
            "var em=document.getElementById('i0116');" +
            "if(em){setV(em,$e);if(btn){btn.click();return 'email-clicked';}return 'email-no-btn';}" +
            "var pw=document.getElementById('i0118');" +
            "if(pw){setV(pw,$s);if(btn){btn.click();return 'pwd-clicked';}return 'pwd-no-btn';}" +
            "var km=document.getElementById('KmsiCheckboxField');" +
            "if(km){if(btn){btn.click();return 'kmsi-clicked';}return 'kmsi-no-btn';}" +
            "return 'no-field';" +
            "})()"
    }

    private fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""

    private fun carregarLogin() {
        val loginUrl = "https://login.microsoftonline.com/e316d1ac-42c8-4d30-817c-12c7a71f8ab2/saml2?SAMLRequest=nVPLjhoxEPyVke%2Beh4fdYS1gRUBRkDYJApJDLlGPp2dx4gdxezYkXx8xQMIhy4Gru1RVXdUePe6tSV4wkPZuzIo0Z4%2BTEYE1Oznt4tat8EeHFJO9NY5kPxizLjjpgTRJBxZJRiXX0%2FdPUqS53AUfvfKGJYv5mH29K%2B%2BwflDQVrlohVICy5Yln8%2BCIs1ZsiDqcOEogotjJnJxz%2FN7LgYbUcqikqJMi7z4wpLlifqNdo12z9d91EcQyXebzZIvP643LJkjRe0g9tLbGHcks8z4Z%2B1Sq1Xw5NvondEOU%2BVthmVx3xSg%2BECoIR80Zc6HRaV4IVQFVdEOoRbZIRLBkikRhgPxzDvqLIY1hhet8NPq6Z8UGQ5d3Pqgf%2FcmUrAYtAIHlGqfWe22wI3%2FBpkCY2pQ39mxDNlHFC5auL48nN2wCcKwaaui4YhNyQfDhwEHqEtegWhzUau6yttRdiFyrv8DWFzMl95o9euW%2Bt%2F6YCG%2Bji7Son%2FRDW97qEQL2kybJiARS6bG%2BJ%2BzgBBxzGLokGVna6ejxKY%2F0Zl3Efc3nejM2x0ETYd7wD2oeM77knhmgGiF7S3pX4UpqQ7USLJztEOlW43NqYv%2FGZgcZ6%2Fs%2F3d6%2BW8nfwA%3D&sso_reload=true"
        webView.loadUrl(loginUrl)
    }

    override fun onBackPressed() {
        if (!silencioso && webView.canGoBack() && !loginConcluido) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        cancelarTimeoutReconnect()
        pararAnimacaoPulse()
        try { webView.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_LOGIN = "extra_auto_login"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_SENHA = "extra_senha"
        const val EXTRA_SILENCIOSO = "extra_silencioso"
        private const val TIMEOUT_RECONNECT_MS = 60_000L
    }
}
