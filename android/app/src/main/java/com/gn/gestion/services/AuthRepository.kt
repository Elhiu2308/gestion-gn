package com.gn.gestion.services

import android.content.Context
import com.gn.gestion.models.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.Cipher

class AuthRepository(private val context: Context) {

    class ResultadoError(mensaje: String) : Exception(mensaje)

    @Volatile
    var ultimoRefresh: String? = null
        private set

    suspend fun login(usuario: String, contrasena: String): String = withContext(Dispatchers.IO) {
        if (!ApiConfig.SYNC_ENABLED) {

            TokenProvider.guardarSesion(context, "", usuario, usuario, "LOCAL")
            return@withContext "LOCAL"
        }
        try {
            val resp = ApiFactory.api(context).login(LoginRequest(usuario.trim(), contrasena))
            val token = resp.token ?: resp.accessToken
            if (!resp.ok || token.isNullOrBlank()) {
                throw ResultadoError("Usuario o contraseña incorrectos")
            }
            val usr = resp.usuario
            TokenProvider.guardarSesion(
                context,
                token,
                usr?.usuario ?: usuario,
                usr?.nombre ?: usuario,
                usr?.rol ?: "",
                usr?.permisos ?: emptyList(),
                usr?.nombreCompleto ?: "",
                usr?.cargo ?: "",
                usr?.grado ?: "",
                usr?.area ?: "",
                usr?.subdireccion ?: "",
                usr?.correo ?: ""
            )
            ultimoRefresh = resp.refreshToken
            usr?.rol ?: ""
        } catch (e: ResultadoError) {
            throw e
        } catch (e: HttpException) {
            throw ResultadoError(mensajeSegunHttp(e))
        } catch (e: SocketTimeoutException) {
            throw ResultadoError("El servidor no responde (tiempo agotado)")
        } catch (e: UnknownHostException) {
            throw ResultadoError("Servidor no encontrado. Revisa la conexión")
        } catch (e: ConnectException) {
            throw ResultadoError("No se pudo conectar al servidor. Revisa tu conexión")
        } catch (e: IOException) {
            throw ResultadoError("No se pudo conectar al servidor. Revisa que esté encendido")
        } catch (e: Exception) {
            throw ResultadoError("Error inesperado: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun activarBiometria(cipher: Cipher): ResultadoError? = withContext(Dispatchers.IO) {
        val refresh = ultimoRefresh
        if (refresh.isNullOrBlank()) {
            return@withContext ResultadoError("Inicia sesión con tu contraseña para activar la biometría")
        }
        try {
            val (iv, cifrado) = BiometricKeyStore.cifrar(cipher, refresh)
            TokenProvider.guardarRefreshBiometrico(context, refresh, iv, cifrado)
            null
        } catch (e: Exception) {
            ResultadoError("No se pudo guardar el acceso biométrico: ${e.message ?: "error"}")
        }
    }

    fun biometriaDisponible(): Boolean = BiometricGate.disponible()

    fun biometriaActivada(): Boolean =
        TokenProvider.biometriaActivada(context) && BiometricKeyStore.claveExiste()

    fun biometriaUtilizable(): Boolean {
        if (!biometriaActivada()) return false
        val iv = TokenProvider.ivBiometrico(context) ?: return false
        if (TokenProvider.refreshCifrado(context) == null) return false
        return runCatching { BiometricKeyStore.cipherDescifrado(iv) }.isSuccess
    }

    fun hayRefreshPendiente(): Boolean = !ultimoRefresh.isNullOrBlank()

    suspend fun loginBiometrico(refreshToken: String): String = withContext(Dispatchers.IO) {
        if (!ApiConfig.SYNC_ENABLED) {
            throw ResultadoError("La biometría requiere conexión con el servidor")
        }
        try {
            val resp = ApiFactory.apiSinToken(context).refresh("Bearer $refreshToken")
            val token = resp.accessToken ?: resp.token
            if (token.isNullOrBlank()) {
                throw ResultadoError("Tu sesión expiró. Ingresa con tu contraseña.")
            }

            TokenProvider.guardarAccessToken(context, token)
            val usr = try {
                ApiFactory.api(context).me()
            } catch (e: Exception) {

                TokenProvider.limpiarToken(context)
                throw e
            }
            TokenProvider.guardarSesion(
                context,
                token,
                usr.usuario,
                usr.nombre,
                usr.rol,
                usr.permisos,
                usr.nombreCompleto,
                usr.cargo,
                usr.grado,
                usr.area,
                usr.subdireccion,
                usr.correo
            )
            usr.rol
        } catch (e: ResultadoError) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401) {
                TokenProvider.desactivarBiometria(context)
                throw ResultadoError("Tu sesión expiró. Ingresa con tu contraseña.")
            }
            throw ResultadoError(mensajeSegunHttp(e))
        } catch (e: Exception) {
            throw ResultadoError("No se pudo iniciar sesión: ${e.message ?: "error de red"}")
        }
    }

    private fun mensajeSegunHttp(e: HttpException): String {
        val delServidor = extraerErrorDelServidor(e)
        if (!delServidor.isNullOrBlank()) return delServidor
        return when (e.code()) {
            401, 400 -> "Usuario o contraseña incorrectos"
            403 -> "Sin permisos para ingresar"
            404 -> "Servicio de acceso no encontrado"
            429 -> "Demasiados intentos. Espera un momento"
            in 500..599 -> "Error interno del servidor (${e.code()})"
            else -> "Error del servidor (${e.code()})"
        }
    }

    private fun extraerErrorDelServidor(e: HttpException): String? {
        return try {
            val raw = e.response()?.errorBody()?.string() ?: return null
            val obj = JsonParser.parseString(raw)
            if (!obj.isJsonObject) return null
            val g = obj.asJsonObject
            (g.get("error") ?: g.get("mensaje"))
                ?.takeIf { it.isJsonPrimitive && !it.isJsonNull && it.asString.isNotBlank() }
                ?.asString
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cerrarSesion() {
        try {
            if (ApiConfig.SYNC_ENABLED && TokenProvider.sessionActiva(context)) {
                ApiFactory.api(context).logout()
            }
        } catch (_: Exception) {
        }
        TokenProvider.cerrarSesion(context)
    }

    fun cerrarSesionBiometrica() {
        TokenProvider.desactivarBiometria(context)
        ultimoRefresh = null
    }

    fun sesionActiva(): Boolean =
        !ApiConfig.SYNC_ENABLED || TokenProvider.sessionActiva(context)
}
