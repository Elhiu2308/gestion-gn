package com.gn.gestion.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    @Volatile
    private var instancia: ApiService? = null
    @Volatile
    private var okHttp: OkHttpClient? = null

    private fun baseUrl(context: Context): String {
        val url = ServidorManager.activo(context).trimEnd('/')
        return "$url/"
    }

    fun httpClient(context: Context): OkHttpClient {
        okHttp?.let { return it }
        synchronized(this) {
            okHttp?.let { return it }
            val client = construirCliente(conToken = true, context = context)
            okHttp = client
            return client
        }
    }

    private fun logging() = HttpLoggingInterceptor().apply {
        level = if (ApiConfig.LOGS) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private fun errores() = Interceptor { chain ->
        val respuesta = chain.proceed(chain.request())
        if (!respuesta.isSuccessful) {
            val cuerpo = runCatching { respuesta.peekBody(4096).string() }.getOrDefault("")
            Log.e(
                "GN_HTTP",
                "HTTP ${respuesta.code} ${chain.request().method} ${chain.request().url} -> $cuerpo"
            )
        }
        respuesta
    }

    private fun construirCliente(conToken: Boolean, context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        if (conToken) {
            builder.addInterceptor { chain ->
                val original = chain.request()
                val token = TokenProvider.token(context)
                val reqBuilder = original.newBuilder()
                    .header("ngrok-skip-browser-warning", "true")
                    .header("X-Device", "android-gestion")
                if (!token.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(reqBuilder.build())
            }
        } else {
            builder.addInterceptor { chain ->
                val original = chain.request()
                val reqBuilder = original.newBuilder()
                    .header("ngrok-skip-browser-warning", "true")
                    .header("X-Device", "android-gestion")
                chain.proceed(reqBuilder.build())
            }
        }
        return builder
            .addInterceptor(errores())
            .addInterceptor(logging())
            .build()
    }

    fun gson(): Gson = GsonBuilder().create()

    fun api(context: Context): ApiService {
        instancia?.let { return it }
        synchronized(this) {
            instancia?.let { return it }
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl(context))
                .client(httpClient(context))
                .addConverterFactory(GsonConverterFactory.create(gson()))
                .build()
            instancia = retrofit.create(ApiService::class.java)
            return instancia!!
        }
    }

    fun apiSinToken(context: Context): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl(context))
            .client(construirCliente(conToken = false, context = context))
            .addConverterFactory(GsonConverterFactory.create(gson()))
            .build()
            .create(ApiService::class.java)

    fun limpiar() {
        instancia = null
        okHttp = null
    }
}