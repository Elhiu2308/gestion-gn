package com.gn.gestion.util

import android.util.Log
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val TAG_HTTP = "GN_HTTP"

fun Throwable.mensajeServidor(): String {
    if (this is HttpException) {
        val codigo = code()
        val crudo = runCatching { response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val url = runCatching { response()?.raw()?.request?.url?.toString().orEmpty() }.getOrDefault("")
        Log.e(TAG_HTTP, "HTTP $codigo $url | $crudo")
        val detalle = extraerMensaje(crudo)
        return when {
            detalle.isNotBlank() -> detalle
            codigo == 400 -> "El servidor rechazó la solicitud (400). Revisa los datos e inténtalo de nuevo."
            codigo == 401 -> "Tu sesión expiró. Vuelve a iniciar sesión."
            codigo == 403 -> "No tienes permisos para realizar esta acción."
            codigo == 404 -> "El recurso ya no existe en el servidor."
            codigo >= 500 -> "El servidor tuvo un problema (HTTP $codigo). Inténtalo más tarde."
            else -> "El servidor respondió HTTP $codigo."
        }
    }
    if (this is SocketTimeoutException) return "El servidor tardó demasiado en responder."
    if (this is UnknownHostException) return "No hay conexión con el servidor."
    if (this is IOException) return "No se pudo conectar con el servidor."
    return message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la operación."
}

private fun extraerMensaje(crudo: String): String {
    if (crudo.isBlank()) return ""
    return runCatching {
        val obj = JsonParser.parseString(crudo).asJsonObject
        for (clave in listOf("error", "mensaje", "message", "detail", "detalle")) {
            val el = obj.get(clave)
            if (el != null && el.isJsonPrimitive && el.asString.isNotBlank()) {
                return el.asString
            }
        }
        val campos = obj.getAsJsonObject("campos") ?: obj.getAsJsonObject("errores")
        if (campos != null) {
            val partes = campos.entrySet().mapNotNull { (k, v) ->
                val t = if (v.isJsonPrimitive) v.asString else v.toString()
                if (t.isBlank()) null else "$k: $t"
            }
            if (partes.isNotEmpty()) return partes.joinToString("\n")
        }
        ""
    }.getOrDefault("").ifBlank { crudo.take(300) }
}
