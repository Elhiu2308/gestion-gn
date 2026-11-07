package com.gn.gestion.services

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object FcmRegistrador {

    private const val TAG = "FcmRegistrador"
    private const val PREFS = "gn_fcm"
    private const val K_TOKEN = "fcm_token"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val io = Executors.newSingleThreadExecutor()
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun registrar(context: Context) {
        val app = context.applicationContext
        if (!ApiConfig.SYNC_ENABLED || !TokenProvider.sessionActiva(app)) {
            Log.i(TAG, "registrar() omitido: sync=${ApiConfig.SYNC_ENABLED} sesionActiva=${TokenProvider.sessionActiva(app)}")
            return
        }
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
                if (!tarea.isSuccessful) {
                    Log.e(TAG, "No se pudo obtener el token FCM", tarea.exception)
                    return@addOnCompleteListener
                }
                registrarToken(app, tarea.result.orEmpty())
            }
        }.onFailure {
            Log.e(TAG, "Firebase no disponible (¿falta google-services.json o el plugin?)", it)
        }
    }

    fun registrarToken(context: Context, token: String) {
        val app = context.applicationContext
        if (token.isBlank()) return
        prefs(app).edit().putString(K_TOKEN, token).apply()
        Log.i(
            TAG,
            "Token FCM listo | huella=${token.take(8)}… | longitud=${token.length}"
        )
        val jwt = TokenProvider.token(app)
        if (jwt.isNullOrBlank()) {
            Log.i(TAG, "Token FCM guardado; se registrará al iniciar sesión")
            return
        }
        enviar(app, metodo = "POST", token = token, jwt = jwt)
    }

    fun eliminar(context: Context) {
        val app = context.applicationContext
        val token = prefs(app).getString(K_TOKEN, null)
        val jwt = TokenProvider.token(app)
        if (token.isNullOrBlank() || jwt.isNullOrBlank()) {
            Log.i(TAG, "eliminar() omitido: sin token FCM o sin sesión")
            return
        }
        enviar(app, metodo = "DELETE", token = token, jwt = jwt)
    }

    private fun enviar(app: Context, metodo: String, token: String, jwt: String) {
        val base = ServidorManager.activo(app).trim().trimEnd('/')
        if (base.isBlank()) {
            Log.e(TAG, "$metodo /api/fcm/token omitido: URL del servidor vacía")
            return
        }
        val cuerpo = JSONObject().apply {
            put("token", token)
            put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("plataforma", "android")
        }.toString().toRequestBody(JSON)

        val peticion = Request.Builder()
            .url("$base/api/fcm/token")
            .header("Authorization", "Bearer $jwt")
            .header("ngrok-skip-browser-warning", "true")
            .method(metodo, cuerpo)
            .build()

        io.execute {
            val huella = "${token.take(8)}…"
            try {
                http.newCall(peticion).execute().use { r ->
                    if (r.isSuccessful) {
                        Log.i(TAG, "$metodo /api/fcm/token OK (HTTP ${r.code}) token=$huella")
                        if (metodo == "DELETE") prefs(app).edit().remove(K_TOKEN).apply()
                    } else {
                        val detalle = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
                        Log.e(
                            TAG,
                            "$metodo /api/fcm/token falló (HTTP ${r.code}) " +
                                    "token=$huella | $detalle"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$metodo /api/fcm/token error de red token=$huella", e)
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
