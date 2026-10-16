package io.loja.ui

import android.content.Intent
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.loja.R
import io.loja.network.SessionManager
import io.loja.utils.ToastUtils

class LoginActivity : AppCompatActivity() {

    private var autoLoginDisparado = false
    private var salvarCredenciaisMarcado = false
    private var reconnectEmAndamento = false
    private var webViewAberta = false
    private lateinit var imgCheckboxLogin: ImageView
    private lateinit var btnEntrar: Button
    private lateinit var logoGrande: ImageView
    private lateinit var tvTitulo: TextView
    private lateinit var tvDescricao: TextView
    private lateinit var containerLogin: LinearLayout
    private lateinit var btnSalvarCredenciais: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        findViewById<android.widget.TextView>(io.loja.R.id.tvVersaoLogin).text =
            "versão ${io.loja.BuildConfig.VERSION_NAME}"

        val sessionManager = SessionManager(this)

        // Se o usuário já tiver logado antes, pula direto para a tela principal
        if (sessionManager.isLoggedIn()) {
            val token = sessionManager.getToken()
            if (!token.isNullOrEmpty()) {
                val intent = Intent(this, io.loja.ui.compose.ComposeMainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            }
        }

        logoGrande = findViewById(R.id.logoGrande)
        tvTitulo = findViewById(R.id.tvTitulo)
        tvDescricao = findViewById(R.id.tvDescricao)
        containerLogin = findViewById(R.id.containerLogin)
        btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etSenha = findViewById<EditText>(R.id.etSenha)
        btnSalvarCredenciais = findViewById(R.id.btnSalvarCredenciais)
        imgCheckboxLogin = findViewById(R.id.imgCheckboxLogin)

        etEmail.setText(sessionManager.getUserEmail() ?: "")
        etSenha.setText(sessionManager.getSavedPassword() ?: "")
        configurarOlhoSenha(etSenha)

        salvarCredenciaisMarcado = sessionManager.hasSavedCredentials()
        imgCheckboxLogin.setImageResource(
            if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
        )

        if (sessionManager.hasSavedCredentials()) {
            // Sessão expirada: reconexão automática silenciosa, sem passar pela tela de login
            reconnectEmAndamento = true
            containerLogin.visibility = View.INVISIBLE
            btnSalvarCredenciais.visibility = View.INVISIBLE
            tvDescricao.text = "Sessão expirada. Reconectando automaticamente..."
            animarSplash(mostrarFormulario = false)
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && reconnectEmAndamento) {
                    webViewAberta = true
                    abrirLoginMicrosoft(
                        sessionManager.getUserEmail() ?: "",
                        sessionManager.getSavedPassword() ?: "",
                        silencioso = true
                    )
                }
            }, 1000)
        } else {
            animarSplash(mostrarFormulario = true)
        }

        // Botão ENTRAR: abre a página da Microsoft com email e senha já preenchidos
        btnEntrar.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val senha = etSenha.text.toString()
            if (email.isEmpty() || senha.isEmpty()) {
                ToastUtils.show(this, "Preencha email e senha para entrar", Toast.LENGTH_LONG)
                return@setOnClickListener
            }
            abrirLoginMicrosoft(email, senha, salvar = salvarCredenciaisMarcado)
        }

        btnSalvarCredenciais.setOnClickListener {
            salvarCredenciaisMarcado = !salvarCredenciaisMarcado
            animarCheckbox()
            if (!salvarCredenciaisMarcado) {
                ToastUtils.show(this, "Login automático desativado")
                return@setOnClickListener
            }
            val email = etEmail.text.toString().trim()
            val senha = etSenha.text.toString()
            if (email.isEmpty() || senha.isEmpty()) {
                ToastUtils.show(this, "Preencha email e senha para salvar")
                return@setOnClickListener
            }
            sessionManager.saveCredentials(email, senha)
            ToastUtils.show(this, "Credenciais salvas! Iniciando login automático...")
            abrirLoginMicrosoft(email, senha, salvar = true)
        }
    }

    private fun animarSplash(mostrarFormulario: Boolean) {
        logoGrande.post {
            val loc = IntArray(2)
            logoGrande.getLocationOnScreen(loc)
            val finalCenterY = loc[1] + logoGrande.height / 2f
            val screenCenterY = resources.displayMetrics.heightPixels / 2f
            logoGrande.translationY = (screenCenterY - finalCenterY).coerceAtLeast(0f)
            logoGrande.scaleX = 2.0f
            logoGrande.scaleY = 2.0f

            // Executa a transição elegante após 400ms de Splash ativa
            Handler(Looper.getMainLooper()).postDelayed({
                logoGrande.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationY(0f)
                    .setDuration(450)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {

                        // Torna o texto "minha loja" visível com Fade-In
                        tvTitulo.visibility = View.VISIBLE
                        tvTitulo.alpha = 0f
                        tvTitulo.animate()
                            .alpha(1f)
                            .setDuration(250)
                            .start()

                        tvDescricao.visibility = View.VISIBLE
                        tvDescricao.alpha = 0f
                        tvDescricao.animate()
                            .alpha(0.90f)
                            .setDuration(250)
                            .start()

                        if (mostrarFormulario) {
                            // Faz as informações de login surgirem de baixo para cima
                            containerLogin.translationY = 50f
                            containerLogin.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(300)
                                .setInterpolator(OvershootInterpolator(0.8f))
                                .start()

                            // O checkbox surge junto com o card
                            btnSalvarCredenciais.translationY = 50f
                            btnSalvarCredenciais.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(300)
                                .setInterpolator(OvershootInterpolator(0.8f))
                                .start()
                        }
                    }
                    .start()
            }, 400)
        }
    }

    override fun onResume() {
        super.onResume()
        // Voltou do WebView sem concluir o login: mostra o formulário manual
        if (reconnectEmAndamento && webViewAberta) {
            reconnectEmAndamento = false
            containerLogin.visibility = View.VISIBLE
            containerLogin.alpha = 1f
            containerLogin.translationY = 0f
            btnSalvarCredenciais.visibility = View.VISIBLE
            btnSalvarCredenciais.alpha = 1f
            btnSalvarCredenciais.translationY = 0f
            tvTitulo.visibility = View.VISIBLE
            tvTitulo.alpha = 1f
            tvDescricao.visibility = View.VISIBLE
            tvDescricao.alpha = 0.9f
            tvDescricao.text = "Para ter acesso ao portal, vamos manter os fluxos de autenticação segura."
            ToastUtils.show(this, "Login automático falhou. Faça login manualmente.", Toast.LENGTH_LONG)
        }
    }

    private fun abrirLoginMicrosoft(email: String, senha: String, salvar: Boolean = false, silencioso: Boolean = false) {
        if (autoLoginDisparado || isFinishing || isDestroyed) return
        autoLoginDisparado = true
        try {
            val sessionManager = SessionManager(applicationContext)
            if (salvar) sessionManager.saveCredentials(email, senha)
            val intent = Intent(this, LoginWebViewActivity::class.java).apply {
                putExtra(LoginWebViewActivity.EXTRA_AUTO_LOGIN, true)
                putExtra(LoginWebViewActivity.EXTRA_EMAIL, email)
                putExtra(LoginWebViewActivity.EXTRA_SENHA, senha)
                putExtra(LoginWebViewActivity.EXTRA_SILENCIOSO, silencioso)
            }
            startActivity(intent)
        } catch (e: Exception) {
            autoLoginDisparado = false
            ToastUtils.show(this, "Erro ao abrir login: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    private fun animarCheckbox() {
        try {
            val de = if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_unchecked else R.drawable.ic_checkbox_checked
            val para = if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
            val transition = TransitionDrawable(
                arrayOf(
                    ContextCompat.getDrawable(this, de),
                    ContextCompat.getDrawable(this, para)
                )
            )
            transition.isCrossFadeEnabled = true
            imgCheckboxLogin.setImageDrawable(transition)
            transition.startTransition(250)
        } catch (e: Exception) {
            imgCheckboxLogin.setImageResource(
                if (salvarCredenciaisMarcado) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
            )
        }
    }

    private fun configurarOlhoSenha(et: EditText) {
        var visivel = false
        fun atualizarIcone() {
            et.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0,
                if (visivel) R.drawable.ic_eye_hide else R.drawable.ic_eye_show,
                0
            )
        }
        atualizarIcone()
        et.setOnTouchListener { _, event ->
            val drawables = et.getCompoundDrawablesRelative()
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP && drawables[2] != null) {
                val icone = drawables[2]
                val dentroIcone = event.x >= et.width - et.totalPaddingRight - icone.intrinsicWidth - 8
                if (dentroIcone) {
                    visivel = !visivel
                    val selection = et.selectionEnd.coerceAtLeast(0)
                    et.inputType = if (visivel) {
                        android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    et.setSelection(selection)
                    atualizarIcone()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}