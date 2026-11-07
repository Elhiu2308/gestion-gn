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

object BiometricGate {

    private var contexto: Context? = null

    fun registrar(ctx: Context) {
        contexto = ctx.applicationContext
    }

    fun disponible(): Boolean {
        val ctx = contexto ?: return false
        return BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

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

            }
        }
        return false
    }

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
