package io.loja.network

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            val originalRequest = chain.request()
            val token = sessionManager.getToken()
            val store = sessionManager.getUserStore() ?: "L291"

            Log.d(TAG, "intercept: Token presente: ${token != null}, Store: $store")

            val requestBuilder = originalRequest.newBuilder().apply {
                if (!token.isNullOrEmpty()) {
                    header("Authorization", "Bearer $token")
                }

                header("Accept", "application/json, text/plain, */*")
                header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                header("Platform-Version", "minhaloja/4.0.5")
                header("User-Store", "minhaloja/$store")
            }

            val response = chain.proceed(requestBuilder.build())
            Log.d(TAG, "intercept: Response Code: ${response.code}")

            if (response.code == 401) {
                Log.e(TAG, "intercept: 401 Não autorizado - sessão expirada")
                sessionManager.clearToken()

                val activity = sessionManager.getCurrentActivity()
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    Handler(Looper.getMainLooper()).post {
                        val intent = Intent(activity, io.loja.ui.LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("session_expired", true)
                        }
                        activity.startActivity(intent)
                        activity.finish()
                    }
                }
            }

            return response

        } catch (e: Exception) {
            Log.e(TAG, "intercept: ERRO", e)
            throw e
        }
    }
}