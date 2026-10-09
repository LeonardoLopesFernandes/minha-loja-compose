package io.loja.ui.compose

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import io.loja.network.SessionManager
import io.loja.ui.LoginActivity
import io.loja.ui.ProfileActivity

/**
 * Home do app em Jetpack Compose: header branco + menu lateral trocando o
 * conteúdo, igual ao MainScreen do Flutter. As telas atuais seguem acessíveis
 * pela ponte "Abrir versão atual" até serem migradas.
 */
class ComposeMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val session = SessionManager(this)
        session.setCurrentActivity(this)
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        setContent {
            HomeScreen(
                onPerfil = { startActivity(Intent(this, ProfileActivity::class.java)) },
            )
        }
    }
}
