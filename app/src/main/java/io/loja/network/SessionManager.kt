package io.loja.network

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
    private var currentActivityRef: WeakReference<Activity>? = null

    fun setCurrentActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    fun getCurrentActivity(): Activity? {
        return currentActivityRef?.get()
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val KEY_BEARER_TOKEN = "BEARER_TOKEN"
        private const val KEY_TOKEN_EXPIRY = "TOKEN_EXPIRY"
        private const val KEY_USER_EMAIL = "USER_EMAIL"
        private const val KEY_USER_NAME = "USER_NAME"
        private const val KEY_USER_STORE = "USER_STORE"
        private const val KEY_REMEMBER_LOGIN = "REMEMBER_LOGIN"
        private const val KEY_MS_PASSWORD = "MS_PASSWORD"
        private const val KEY_PRINTER_IP = "PRINTER_IP"
        private const val KEY_PROFILE_PHOTO = "PROFILE_PHOTO"
        private const val TOKEN_EXPIRY_DAYS = 14
        const val DEFAULT_PRINTER_IP = "10.25.168.24"
        const val PRINTER_PORT = 9100
    }

    fun getPrinterIp(): String {
        return try {
            prefs.getString(KEY_PRINTER_IP, null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_PRINTER_IP
        } catch (e: Exception) {
            Log.e(TAG, "getPrinterIp: Erro", e)
            DEFAULT_PRINTER_IP
        }
    }

    fun savePrinterIp(ip: String) {
        try {
            prefs.edit().putString(KEY_PRINTER_IP, ip.trim()).apply()
            Log.d(TAG, "savePrinterIp: $ip")
        } catch (e: Exception) {
            Log.e(TAG, "savePrinterIp: Erro", e)
        }
    }

    fun saveProfilePhoto(path: String) {
        try {
            prefs.edit().putString(KEY_PROFILE_PHOTO, path).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveProfilePhoto: Erro", e)
        }
    }

    fun getProfilePhoto(): String? {
        return try {
            prefs.getString(KEY_PROFILE_PHOTO, null)?.takeIf { it.isNotBlank() && File(it).exists() }
        } catch (e: Exception) {
            Log.e(TAG, "getProfilePhoto: Erro", e)
            null
        }
    }

    fun clearProfilePhoto() {
        try {
            prefs.edit().remove(KEY_PROFILE_PHOTO).apply()
        } catch (e: Exception) {
            Log.e(TAG, "clearProfilePhoto: Erro", e)
        }
    }

    fun saveToken(token: String) {
        saveTokenWithExpiry(token, null)
    }

    // Usa a expiração REAL do token (claim "exp" do JWT), igual ao Flutter.
    // Sem isso o app achava a sessão válida por 14 dias fixos e nunca
    // disparava a reconexão automática quando o token do servidor expirava.
    fun saveTokenWithExpiry(token: String, expiryEpochSeconds: Long?) {
        try {
            Log.d(TAG, "saveTokenWithExpiry: Salvando token (${token.take(30)}...)")

            val editor = prefs.edit()
            editor.putString(KEY_BEARER_TOKEN, token)

            val agora = System.currentTimeMillis()
            val maxExpiry = agora + (365L * 24 * 60 * 60 * 1000L)
            val expiry = if (expiryEpochSeconds != null && expiryEpochSeconds > 0) {
                (expiryEpochSeconds * 1000L).coerceIn(agora, maxExpiry)
            } else {
                // Sem "exp": mantém o fallback de 14 dias
                agora + (TOKEN_EXPIRY_DAYS * 24 * 60 * 60 * 1000L)
            }
            editor.putLong(KEY_TOKEN_EXPIRY, expiry)
            editor.putBoolean(KEY_REMEMBER_LOGIN, true)
            editor.apply()
            
            Log.d(TAG, "saveToken: Token salvo com sucesso!")
            Log.d(TAG, "saveToken: Expira em: ${Date(expiry)}")
            Log.d(TAG, "saveToken: Dias restantes: $TOKEN_EXPIRY_DAYS")
            
            // Verificar se salvou
            val savedToken = prefs.getString(KEY_BEARER_TOKEN, null)
            val savedExpiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
            Log.d(TAG, "saveToken: Verificação - Token salvo: ${savedToken?.take(30)}")
            Log.d(TAG, "saveToken: Verificação - Expiry salvo: ${Date(savedExpiry)}")
            
        } catch (e: Exception) {
            Log.e(TAG, "saveToken: Erro ao salvar token", e)
        }
    }

    fun getToken(): String? {
        return try {
            val token = prefs.getString(KEY_BEARER_TOKEN, null)
            
            if (token.isNullOrEmpty()) {
                Log.d(TAG, "getToken: Nenhum token encontrado")
                return null
            }
            
            // Verificar se expirou
            if (isTokenExpired()) {
                Log.e(TAG, "getToken: Token EXPIRADO! Removendo...")
                clearToken()
                return null
            }
            
            val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
            val remaining = expiry - System.currentTimeMillis()
            val daysRemaining = remaining / (24 * 60 * 60 * 1000)
            
            Log.d(TAG, "getToken: Token VÁLIDO (${token.take(30)}...)")
            Log.d(TAG, "getToken: Dias restantes: $daysRemaining")
            
            token
            
        } catch (e: Exception) {
            Log.e(TAG, "getToken: Erro ao obter token", e)
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            val remember = prefs.getBoolean(KEY_REMEMBER_LOGIN, false)
            val token = getToken()
            val isLogged = remember && !token.isNullOrEmpty()
            
            Log.d(TAG, "isLoggedIn: remember=$remember, hasToken=${!token.isNullOrEmpty()}, result=$isLogged")
            
            isLogged
        } catch (e: Exception) {
            Log.e(TAG, "isLoggedIn: Erro", e)
            false
        }
    }

    private fun isTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        if (expiry == 0L) {
            Log.d(TAG, "isTokenExpired: Sem data de expiração definida")
            return false
        }
        
        val now = System.currentTimeMillis()
        val expired = now > expiry
        
        Log.d(TAG, "isTokenExpired: Agora: ${Date(now)}")
        Log.d(TAG, "isTokenExpired: Expira: ${Date(expiry)}")
        Log.d(TAG, "isTokenExpired: Expirado: $expired")
        
        return expired
    }

    fun clearToken() {
        try {
            prefs.edit().remove(KEY_BEARER_TOKEN).apply()
            prefs.edit().remove(KEY_TOKEN_EXPIRY).apply()
            prefs.edit().putBoolean(KEY_REMEMBER_LOGIN, false).apply()
            Log.d(TAG, "clearToken: Token removido")
        } catch (e: Exception) {
            Log.e(TAG, "clearToken: Erro ao remover token", e)
        }
    }

    fun saveUserInfo(email: String, name: String, store: String) {
        try {
            prefs.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_NAME, name)
                putString(KEY_USER_STORE, store)
            }.apply()
            Log.d(TAG, "saveUserInfo: Info salva com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "saveUserInfo: Erro ao salvar info", e)
        }
    }

    fun getUserEmail(): String? {
        return try {
            prefs.getString(KEY_USER_EMAIL, null)
        } catch (e: Exception) {
            Log.e(TAG, "getUserEmail: Erro", e)
            null
        }
    }

    fun getUserName(): String? {
        return try {
            prefs.getString(KEY_USER_NAME, null)
        } catch (e: Exception) {
            Log.e(TAG, "getUserName: Erro", e)
            null
        }
    }

    fun getUserStore(): String? {
        return try {
            prefs.getString(KEY_USER_STORE, "L291")
        } catch (e: Exception) {
            Log.e(TAG, "getUserStore: Erro", e)
            "L291"
        }
    }

    fun saveCredentials(email: String, password: String) {
        try {
            prefs.edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_MS_PASSWORD, password)
            }.apply()
            Log.d(TAG, "saveCredentials: Credenciais salvas")
        } catch (e: Exception) {
            Log.e(TAG, "saveCredentials: Erro", e)
        }
    }

    fun getSavedPassword(): String? {
        return try {
            prefs.getString(KEY_MS_PASSWORD, null)
        } catch (e: Exception) {
            Log.e(TAG, "getSavedPassword: Erro", e)
            null
        }
    }

    fun hasSavedCredentials(): Boolean {
        val email = getUserEmail()
        val senha = getSavedPassword()
        return !email.isNullOrBlank() && !senha.isNullOrBlank()
    }

    fun clearCredentials() {
        try {
            prefs.edit().remove(KEY_MS_PASSWORD).apply()
            Log.d(TAG, "clearCredentials: Credenciais removidas")
        } catch (e: Exception) {
            Log.e(TAG, "clearCredentials: Erro", e)
        }
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "clearAll: Todas as preferências limpas")
        } catch (e: Exception) {
            Log.e(TAG, "clearAll: Erro ao limpar preferências", e)
        }
    }

    fun getExpiryTime(): Long {
        return prefs.getLong(KEY_TOKEN_EXPIRY, 0)
    }

    fun getRemainingTimeFormatted(): String {
        val expiry = getExpiryTime()
        if (expiry <= 0) return "Não definido"
        
        val now = System.currentTimeMillis()
        val diff = expiry - now
        
        if (diff <= 0) return "Expirado"
        
        val days = diff / (24 * 60 * 60 * 1000)
        val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)
        
        return if (days > 0) {
            "$days dias, $hours horas e $minutes minutos"
        } else {
            "$hours horas e $minutes minutos"
        }
    }
}