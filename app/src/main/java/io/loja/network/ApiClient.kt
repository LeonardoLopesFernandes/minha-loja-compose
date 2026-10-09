package io.loja.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Log

object ApiClient {
    private const val TAG = "ApiClient"
    
    // URLs base
    const val BASE_URL_SL = "https://sl-bff.americanas.io/"
    const val BASE_URL_MINHALOJA = "https://minhaloja-bff.americanas.io/"
    const val BASE_URL_MINHALOJA_WEB = "https://minhaloja.americanas.io/"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, "OkHttp: $message")
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private fun getOkHttpClient(sessionManager: SessionManager): OkHttpClient {
        return try {
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(sessionManager))
                .addInterceptor(CookieInterceptor())
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "getOkHttpClient: Erro ao criar cliente", e)
            throw e
        }
    }

    fun <T> createApiService(
        serviceClass: Class<T>,
        baseUrl: String,
        sessionManager: SessionManager
    ): T {
        return try {
            val client = getOkHttpClient(sessionManager)
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(serviceClass)
        } catch (e: Exception) {
            Log.e(TAG, "createApiService: Erro ao criar serviço", e)
            throw e
        }
    }

    fun getSlApiService(sessionManager: SessionManager): ApiService {
        return try {
            createApiService(ApiService::class.java, BASE_URL_SL, sessionManager)
        } catch (e: Exception) {
            Log.e(TAG, "getSlApiService: Erro", e)
            throw e
        }
    }

    fun getMinhaLojaApiService(sessionManager: SessionManager): ApiService {
        return try {
            createApiService(ApiService::class.java, BASE_URL_MINHALOJA, sessionManager)
        } catch (e: Exception) {
            Log.e(TAG, "getMinhaLojaApiService: Erro", e)
            throw e
        }
    }

    fun getSlApiServiceWithToken(token: String): ApiService {
        return try {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            Retrofit.Builder()
                .baseUrl(BASE_URL_SL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getSlApiServiceWithToken: Erro", e)
            throw e
        }
    }
}