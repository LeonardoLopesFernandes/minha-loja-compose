package io.loja.network

import io.loja.BuildConfig
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.loja.utils.LogHelper
import java.util.concurrent.TimeUnit

data class TokenInfo(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long = 0
)

data class DeviceCodeInfo(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val interval: Long,
    val expiresIn: Long,
    val mensagem: String = ""
)

object MicrosoftOAuth {

    const val TENANT = "e316d1ac-42c8-4d30-817c-12c7a71f8ab2"
    const val CLIENT_ID = "16021f31-43f8-4f7a-8af4-5e47efe7db8a"
    val CLIENT_SECRET: String get() = BuildConfig.MICROSOFT_CLIENT_SECRET
    const val SCOPES = "openid profile offline_access"
    const val REDIRECT_URI = "https://apimobile.brasilrisk.com.br/Validar/SamlResponseConsumer"

    private const val AUTHORIZE_URL =
        "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/authorize"
    private const val TOKEN_URL =
        "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/token"
    private const val DEVICE_CODE_URL =
        "https://login.microsoftonline.com/$TENANT/oauth2/v2.0/devicecode"
    private const val VALIDAR_URL =
        "https://apimobile.brasilrisk.com.br/validar/LoginMobileDeliveryClientMicrosoft"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    var ultimoErro: String? = null
        private set

    @Volatile
    var ultimoCodigoDispositivo: String? = null
        private set

    fun getAuthorizeUrl(state: String = "mloja"): String {
        return "$AUTHORIZE_URL?" +
            "client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&response_mode=query" +
            "&state=$state" +
            "&nonce=mloja$state"
    }

    fun isRedirectUrl(url: String): Boolean {
        return url.startsWith(REDIRECT_URI)
    }

    fun extrairCodigo(url: String): String? {
        val match = Regex("[?&]code=([^&]+)").find(url)
        return match?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    fun extrairErro(url: String): String? {
        val match = Regex("[?&]error=([^&]+)").find(url)
        return match?.groupValues?.get(1)
    }

    fun trocarCodigoPorToken(accessCode: String): TokenInfo? {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .add("code", accessCode)
            .add("redirect_uri", REDIRECT_URI)
            .add("scope", SCOPES)
            .build()
        return postToken(form)
    }

    fun renovarToken(refreshToken: String): TokenInfo? {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", SCOPES)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        return postToken(form)
    }

    /**
     * Login nativo (ROPC): troca email + senha por token diretamente no endpoint
     * da Microsoft, sem abrir WebView. Usa o client_secret do app base.
     */
    fun loginComCredenciais(email: String, senha: String): TokenInfo? {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "password")
            .add("username", email)
            .add("password", senha)
            .add("scope", SCOPES)
            .build()
        return postToken(form)
    }

    /**
     * Device Code Flow: solicita na Microsoft um código para autenticação
     * em outro aparelho/navegador (funciona com MFA, sem WebView no app).
     */
    fun iniciarFluxoDispositivo(): DeviceCodeInfo? {
        return try {
            val form = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPES)
                .build()
            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .post(form)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    ultimoErro = extrairDescricaoErro(body) ?: "HTTP ${response.code}"
                    LogHelper.e("OAuth: devicecode HTTP ${response.code} body=$body")
                    return null
                }
                val json = JSONObject(body)
                val deviceCode = json.optString("device_code")
                val userCode = json.optString("user_code")
                if (deviceCode.isEmpty() || userCode.isEmpty()) {
                    ultimoErro = extrairDescricaoErro(body) ?: "resposta sem device_code"
                    return null
                }
                ultimoErro = null
                DeviceCodeInfo(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUri = json.optString("verification_uri").ifEmpty { "https://microsoft.com/devicelogin" },
                    interval = json.optLong("interval", 5),
                    expiresIn = json.optLong("expires_in"),
                    mensagem = json.optString("message")
                )
            }
        } catch (e: Exception) {
            ultimoErro = e.message
            LogHelper.e("OAuth: exceção em iniciarFluxoDispositivo", e)
            null
        }
    }

    /**
     * Consulta se o usuário já autorizou o código. Retorna o token quando
     * autorizado; se ainda estiver pendente, ultimoCodigoDispositivo fica
     * "authorization_pending" e a tela deve continuar aguardando.
     */
    fun verificarAutorizacaoDispositivo(deviceCode: String): TokenInfo? {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", deviceCode)
            .build()
        return try {
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(form)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val codigo = extrairCodigoErro(body)
                    ultimoCodigoDispositivo = codigo
                    ultimoErro = extrairDescricaoErro(body) ?: "HTTP ${response.code}"
                    return null
                }
                val json = JSONObject(body)
                val access = json.optString("access_token")
                if (access.isEmpty()) {
                    ultimoCodigoDispositivo = extrairCodigoErro(body)
                    ultimoErro = extrairDescricaoErro(body) ?: "sem access_token"
                    return null
                }
                ultimoErro = null
                ultimoCodigoDispositivo = null
                TokenInfo(
                    accessToken = access,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
                    expiresIn = json.optLong("expires_in")
                )
            }
        } catch (e: Exception) {
            ultimoErro = e.message
            LogHelper.e("OAuth: exceção em verificarAutorizacaoDispositivo", e)
            null
        }
    }

    private fun extrairCodigoErro(body: String): String? {
        return try {
            JSONObject(body).optString("error").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Envia o access_token da Microsoft para a API de validação e recebe
     * o token de sessão do portal (newToken).
     */
    fun validarSessao(accessToken: String): String? {
        return try {
            val json = JSONObject()
                .put("accessToken", accessToken)
                .put("token", accessToken)
            val request = Request.Builder()
                .url(VALIDAR_URL)
                .post(json.toString().toRequestBody())
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    ultimoErro = extrairDescricaoErro(body) ?: "HTTP ${response.code}"
                    LogHelper.e("OAuth: validarSessao HTTP ${response.code} body=$body")
                    return null
                }
                val parsed = try { JSONObject(body) } catch (e: Exception) { null }
                val sessionToken = parsed?.let {
                    it.optString("newToken")
                        .takeIf { v -> v.isNotEmpty() }
                        ?: it.optString("session_token").takeIf { v -> v.isNotEmpty() }
                        ?: it.optString("sessionToken").takeIf { v -> v.isNotEmpty() }
                        ?: it.optString("token").takeIf { v -> v.isNotEmpty() }
                } ?: body.trim().takeIf { it.isNotEmpty() && !it.startsWith("{") }
                if (sessionToken.isNullOrEmpty()) {
                    ultimoErro = "resposta sem token de sessão"
                    LogHelper.e("OAuth: validarSessao sem token body=$body")
                    return null
                }
                ultimoErro = null
                sessionToken
            }
        } catch (e: Exception) {
            ultimoErro = e.message
            LogHelper.e("OAuth: exceção em validarSessao", e)
            null
        }
    }

    private fun String.toRequestBody() =
        okhttp3.RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            this
        )

    private fun postToken(form: FormBody): TokenInfo? {
        return try {
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(form)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    ultimoErro = extrairDescricaoErro(body) ?: "HTTP ${response.code}"
                    return null
                }
                val json = JSONObject(body)
                val access = json.optString("access_token")
                if (access.isEmpty()) {
                    ultimoErro = extrairDescricaoErro(body) ?: "sem access_token"
                    return null
                }
                ultimoErro = null
                TokenInfo(
                    accessToken = access,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
                    expiresIn = json.optLong("expires_in")
                )
            }
        } catch (e: Exception) {
            ultimoErro = e.message
            null
        }
    }

    private fun extrairDescricaoErro(body: String): String? {
        return try {
            JSONObject(body).optString("error_description").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
