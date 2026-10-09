package io.loja.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

class CookieInterceptor : Interceptor {
    private val cookies = ConcurrentHashMap<String, String>()

    companion object {
        private const val COOKIE_HEADER = "Cookie"
        private const val SET_COOKIE_HEADER = "Set-Cookie"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Adiciona cookies salvos
        val requestBuilder = originalRequest.newBuilder()
        if (cookies.isNotEmpty()) {
            val cookieString = cookies.values.joinToString("; ")
            requestBuilder.header(COOKIE_HEADER, cookieString)
        }

        val response = chain.proceed(requestBuilder.build())

        // Salva novos cookies
        response.headers(SET_COOKIE_HEADER).forEach { cookieHeader ->
            val cookieValue = cookieHeader.split(";")[0]
            val cookieParts = cookieValue.split("=")
            if (cookieParts.size >= 2) {
                val name = cookieParts[0].trim()
                val value = cookieParts[1].trim()
                cookies[name] = cookieValue
            }
        }

        return response
    }

    fun clearCookies() {
        cookies.clear()
    }

    fun getCookies(): Map<String, String> = cookies.toMap()
}