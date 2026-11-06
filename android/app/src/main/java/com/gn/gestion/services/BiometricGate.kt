package com.gn.gestion.services;

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Envoltura de BiometricPrompt para el desbloqueo del refresh token.
 *
 * El prompt usa únicamente BIOMETRIC_STRONG porque la clave del Keystore que protege el
 * token está autorizada con `AUTH_BIOMETRIC_STRONG` por cada uso: ofrecer DEVICE_CREDENTIAL
 * dejaría visible un botón que el sistema no puede autorizar, y además combinar
 * `setNegativeButtonText()` con DEVICE_CREDENTIAL es una combinación prohibida por
 * BiometricPrompt. Si el usuario no quiere usar huella o rostro, vuelve al login con contraseña.
 */
object BiometricGate {

    private var contexto: Context? = null

    fun registrar(ctx: Context) {
        contexto = ctx.applicationContext
    }

    /**
     * El acceso rápido solo se ofrece si hay biometría de verdad registrada.
     *
     * No basta con que exista PIN/patrón: la clave del Keystore que protege el
     * refresh token exige autenticación biométrica *por cada uso*, y Android no
     * permite crear esa clave si no hay ninguna huella o rostro enrolled. Con solo
     * credencial del dispositivo, el token quedaría protegido por un PIN que el
     * usuario puede olvidar o compartir, así que no se habilita el atajo.
     */
    fun disponible(): Boolean {
        val ctx = contexto ?: return false
        return BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Abre el registro de biometría del sistema, como hacen las apps bancarias.
     *
     * Se usa `ACTION_BIOMETRIC_ENROLL` para que el sistema ofrezca el alta de huella o
     * rostro sin que la app tenga que implementarlo. En versiones anteriores a
     * Android 11 no existe esa acción y se cae a la pantalla de seguridad.
     */
    fun abrirRegistroBiometrico(activity: FragmentActivity): Boolean {
        val intentos = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intentos += Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BIOMETRIC_STRONG
                )
            }
        }
        intentos += Intent(Settings.ACTION_SECURITY_SETTINGS)
        intentos += Intent(Settings.ACTION_SETTINGS)
        for (intento in intentos) {
            try {
                activity.startActivity(intento)
                return true
            } catch (_: ActivityNotFoundException) {
                // Se prueba con la siguiente opción.
            }
        }
        return false
    }

    /** Motivo por el que no se puede activar, para explicarlo en la interfaz. */
    fun motivoNoDisponible(): String {
        val ctx = contexto ?: return "No se pudo consultar el estado de la biometría"
        return when (BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Registra tu huella o tu rostro en Ajustes > Seguridad para activar el acceso rápido"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "Este dispositivo no tiene sensor de biometría"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "El sensor de biometría no está disponible ahora"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "Actualiza la seguridad del dispositivo para usar la biometría"
            else -> "La biometría no está disponible en este dispositivo"
        }
    }

    /**
     * Lanza el prompt con un CryptoObject: el descifrado solo se completa si la
     * autenticación fue exitosa, de modo que el refresh token nunca queda expuesto
     * en claro antes de validar la identidad.
     */
    fun autenticar(
        activity: FragmentActivity,
        titulo: String,
        subtitulo: String,
        negativo: String,
        cipher: Cipher,
        onOk: (Cipher) -> Unit,
        onFallo: (String) -> Unit
    ) {
        registrar(activity)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .setNegativeButtonText(negativo)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val autorizado = result.cryptoObject?.cipher
                    if (autorizado == null) {
                        onFallo("No se pudo validar tu identidad")
                    } else {
                        onOk(autorizado)
                    }
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    onFallo(msg.toString())
                }
            }
        )
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
