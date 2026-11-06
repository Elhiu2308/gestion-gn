package com.gn.gestion.services;

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda el token JWT y los datos de sesión en almacenamiento cifrado
 * (EncryptedSharedPreferences respaldado por el Android Keystore).
 */
object TokenProvider {

    private const val PREF_NOMBRE = "gestion_secure"
    private const val K_TOKEN = "jwt_token"
    private const val K_USUARIO = "jwt_usuario"
    private const val K_NOMBRE = "jwt_nombre"
    private const val K_NOMBRE_COMPLETO = "jwt_nombre_completo"
    private const val K_ROL = "jwt_rol"
    private const val K_CARGO = "jwt_cargo"
    private const val K_GRADO = "jwt_grado"
    private const val K_AREA = "jwt_area"
    private const val K_SUBDIRECCION = "jwt_subdireccion"
    private const val K_CORREO = "jwt_correo"
    private const val K_PERMISOS = "jwt_permisos"
    private const val K_LAST_SYNC = "last_sync"
    private const val K_BIOMETRIA_ACTIVADA = "biometria_activada"
    private const val K_BIOMETRIA_IV = "biometria_iv"
    private const val K_BIOMETRIA_REFRESH = "biometria_refresh"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun obtenerPrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val p = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREF_NOMBRE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs = p
            return p
        }
    }

    fun token(context: Context): String? =
        obtenerPrefs(context).getString(K_TOKEN, null)

    /**
     * Guarda solo el access token renovado por `/api/auth/refresh`.
     *
     * La sesión biométrica arranca sin token: primero se obtiene uno nuevo con el refresh
     * descifrado, se persiste para que el interceptor lo adjunte y recién entonces se
     * consulta `/api/auth/me`, que exige `Authorization`.
     */
    fun guardarAccessToken(context: Context, token: String) {
        obtenerPrefs(context).edit().putString(K_TOKEN, token).apply()
    }

    fun guardarSesion(
        context: Context,
        token: String,
        usuario: String,
        nombre: String,
        rol: String = "",
        permisos: List<String> = emptyList(),
        nombreCompleto: String = "",
        cargo: String = "",
        grado: String = "",
        area: String = "",
        subdireccion: String = "",
        correo: String = ""
    ) {
        obtenerPrefs(context).edit()
            .putString(K_TOKEN, token)
            .putString(K_USUARIO, usuario)
            .putString(K_NOMBRE, nombre)
            .putString(K_NOMBRE_COMPLETO, nombreCompleto)
            .putString(K_ROL, rol)
            .putString(K_CARGO, cargo)
            .putString(K_GRADO, grado)
            .putString(K_AREA, area)
            .putString(K_SUBDIRECCION, subdireccion)
            .putString(K_CORREO, correo)
            .putString(K_PERMISOS, persistirPermisos(permisos))
            .apply()
    }

    private fun persistirPermisos(permisos: List<String>): String {
        val sb = StringBuilder()
        for (p in permisos) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(p)
        }
        return sb.toString()
    }

    fun usuario(context: Context): String? =
        obtenerPrefs(context).getString(K_USUARIO, null)

    fun nombre(context: Context): String =
        obtenerPrefs(context).getString(K_NOMBRE, "").orEmpty()

    fun nombreCompleto(context: Context): String =
        obtenerPrefs(context).getString(K_NOMBRE_COMPLETO, "").orEmpty().ifBlank { nombre(context) }

    fun rol(context: Context): String =
        obtenerPrefs(context).getString(K_ROL, "").orEmpty()

    fun cargo(context: Context): String =
        obtenerPrefs(context).getString(K_CARGO, "").orEmpty()

    fun grado(context: Context): String =
        obtenerPrefs(context).getString(K_GRADO, "").orEmpty()

    fun area(context: Context): String =
        obtenerPrefs(context).getString(K_AREA, "").orEmpty()

    fun subdireccion(context: Context): String =
        obtenerPrefs(context).getString(K_SUBDIRECCION, "").orEmpty()

    fun correo(context: Context): String =
        obtenerPrefs(context).getString(K_CORREO, "").orEmpty()

    fun permisos(context: Context): Set<String> =
        obtenerPrefs(context).getString(K_PERMISOS, "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** El rol ADMINISTRADOR lo tiene todo por diseño; el resto se valida contra la lista. */
    fun puede(context: Context, codigo: String): Boolean =
        rol(context) == "ADMINISTRADOR" || codigo in permisos(context)

    /**
     * Guarda el refresh token cifrado con la clave del Keystore ligada a biometría,
     * junto con el IV necesario para descifrarlo.
     */
    fun guardarRefreshBiometrico(context: Context, refreshToken: String, iv: ByteArray, cifrado: ByteArray) {
        obtenerPrefs(context).edit()
            .putBoolean(K_BIOMETRIA_ACTIVADA, true)
            .putString(K_BIOMETRIA_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .putString(K_BIOMETRIA_REFRESH, android.util.Base64.encodeToString(cifrado, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun biometriaActivada(context: Context): Boolean =
        obtenerPrefs(context).getBoolean(K_BIOMETRIA_ACTIVADA, false) &&
            !obtenerPrefs(context).getString(K_BIOMETRIA_REFRESH, null).isNullOrBlank()

    fun ivBiometrico(context: Context): ByteArray? =
        obtenerPrefs(context).getString(K_BIOMETRIA_IV, null)?.let {
            runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull()
        }

    fun refreshCifrado(context: Context): ByteArray? =
        obtenerPrefs(context).getString(K_BIOMETRIA_REFRESH, null)?.let {
            runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull()
        }

    /** Desactiva el acceso biométrico y borra el refresh token cifrado. */
    fun desactivarBiometria(context: Context) {
        obtenerPrefs(context).edit()
            .remove(K_BIOMETRIA_ACTIVADA)
            .remove(K_BIOMETRIA_IV)
            .remove(K_BIOMETRIA_REFRESH)
            .apply()
        BiometricKeyStore.eliminarClave()
    }

    /**
     * Cierra la sesión dejando intacto el acceso biométrico: solo se borran las claves
     * de sesión. Para desactivarlo por completo está `desactivarBiometria()`.
     */
    fun cerrarSesion(context: Context) {
        obtenerPrefs(context).edit()
            .remove(K_TOKEN)
            .remove(K_USUARIO)
            .remove(K_NOMBRE)
            .remove(K_NOMBRE_COMPLETO)
            .remove(K_ROL)
            .remove(K_CARGO)
            .remove(K_GRADO)
            .remove(K_AREA)
            .remove(K_SUBDIRECCION)
            .remove(K_CORREO)
            .remove(K_PERMISOS)
            .remove(K_LAST_SYNC)
            .apply()
    }

    fun sessionActiva(context: Context): Boolean =
        !token(context).isNullOrBlank()

    /** Descarta el access token sin tocar el refresh token biométrico ya cifrado. */
    fun limpiarToken(context: Context) {
        obtenerPrefs(context).edit().remove(K_TOKEN).apply()
    }

    /** Última marca de tiempo sincronizada (para el pull incremental). */
    fun lastSync(context: Context): Long =
        obtenerPrefs(context).getLong(K_LAST_SYNC, 0L)

    fun setLastSync(context: Context, ts: Long) {
        obtenerPrefs(context).edit().putLong(K_LAST_SYNC, ts).apply()
    }
}