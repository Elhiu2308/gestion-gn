package com.gn.gestion.services;

import android.content.Context
import com.gn.gestion.BuildConfig
import java.net.URI

/**
 * Dirección del servidor de Gestión, configurable desde la app.
 *
 * Si el usuario guarda una dirección propia, esta tiene prioridad sobre la
 * URL compilada (BuildConfig.SERVER_URL). Así un cambio de IP de la PC no
 * obliga a recompilar la app.
 */
object ServidorManager {

    private const val PREF_NOMBRE = "gestion_servidor"
    private const val K_URL = "server_url"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NOMBRE, Context.MODE_PRIVATE)

    /** Dirección efectiva del servidor (la guardada por el usuario o la compilada). */
    fun activo(context: Context): String {
        val guardada = prefs(context).getString(K_URL, null)
            ?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
        return guardada ?: BuildConfig.SERVER_URL
    }

    /** Guarda una nueva dirección. Devuelve false si no es una URL válida. */
    fun guardar(context: Context, url: String): Boolean {
        val normalizada = normalizar(url) ?: return false
        prefs(context).edit().putString(K_URL, normalizada).apply()
        return true
    }

    /** Quita la dirección personalizada y vuelve a la compilada en la APK. */
    fun restablecer(context: Context) {
        prefs(context).edit().remove(K_URL).apply()
    }

    /**
     * Normaliza y valida una dirección. Acepta "192.168.0.3:5000",
     * "http://192.168.0.3:5000/", hostname, etc. Devuelve null si es inválida.
     */
    fun normalizar(url: String): String? {
        var s = url.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://$s"
        }
        s = s.trimEnd('/')
        if (s.endsWith(":/")) return null
        val uri = try {
            URI(s)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host
        if (host.isNullOrBlank()) return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        return s
    }
}