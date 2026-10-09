package io.loja.utils

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import io.loja.network.SessionManager
import io.loja.ui.LoginActivity

object SessionExpiredHandler {
    
    fun handleSessionExpired(activity: Activity) {
        try {
            LogHelper.d("SessionExpiredHandler: Redirecionando para login...")
            
            // Limpar sessão (mantém credenciais salvas para o auto-login)
            val sessionManager = SessionManager(activity)
            sessionManager.clearToken()
            
            // Redirecionar para Login
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activity.startActivity(intent)
            activity.finish()
            
            LogHelper.d("SessionExpiredHandler: Redirecionamento concluído")
        } catch (e: Exception) {
            LogHelper.e("SessionExpiredHandler: Erro ao redirecionar", e)
            // Fallback: tenta fechar a activity atual
            try {
                activity.finish()
            } catch (ex: Exception) {
                LogHelper.e("SessionExpiredHandler: Erro no fallback", ex)
            }
        }
    }
}