package com.gn.gestion.services;

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado del refresh token con una única clave AES-GCM del Android Keystore.
 *
 * Android exige autenticación para *cualquier* uso de una clave marcada con
 * `setUserAuthenticationRequired(true)`, incluida la operación de cifrado. Por eso el
 * token se cifra dentro de un `BiometricPrompt`: el usuario confirma su identidad una vez
 * al activar el acceso biométrico y ese mismo `Cipher` autorizado es el que cifra.
 *
 * - La autorización es por uso (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`),
 *   así que cada descifrado posterior exige una autenticación nueva.
 * - `setInvalidatedByBiometricEnrollment(true)` invalida la clave si se agrega una huella o
 *   rostro nuevo, de modo que el acceso de otra persona queda anulado.
 *
 * El token nunca queda en claro fuera del Keystore y solo se libera frente a una
 * biometría válida.
 */
object BiometricKeyStore {

    private const val ALIAS = "gn_biometria_v1"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_TAG = 128

    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun clave(crearSiFalta: Boolean): SecretKey {
        val existente = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existente != null) {
            // Una clave invalidada por una inscripción nueva queda inutilizable: se reemplaza.
            val utilizable = runCatching {
                Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, existente) }
                true
            }.getOrDefault(false)
            if (utilizable) return existente
        }
        if (!crearSiFalta) {
            throw IllegalStateException("No existe una clave biométrica en el dispositivo")
        }
        return generar()
    }

    private fun generar(): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Autorización por uso: cada cifrado o descifrado exige autenticación.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generador.init(builder.build())
        return generador.generateKey()
    }

    fun claveExiste(): Boolean =
        runCatching { keyStore.getKey(ALIAS, null) as? SecretKey != null }.getOrDefault(false)

    /**
     * Cipher de cifrado para entregar como `CryptoObject` del `BiometricPrompt` al activar
     * el acceso biométrico. El `doFinal` solo concluye si la autenticación fue exitosa.
     */
    fun cipherCifrado(): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, clave(crearSiFalta = true))
        }

    /**
     * Cipher de descifrado para entregar como `CryptoObject` del `BiometricPrompt` al
     * iniciar sesión: el token solo se abre si la identidad quedó validada. No crea la
     * clave, para que un token sin clave no parezca un acceso biométrico disponible.
     */
    fun cipherDescifrado(iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, clave(crearSiFalta = false), GCMParameterSpec(BITS_TAG, iv))
        }

    /** Cifra con el cipher ya autorizado por el BiometricPrompt. */
    fun cifrar(cipher: Cipher, texto: String): Pair<ByteArray, ByteArray> =
        cipher.iv to cipher.doFinal(texto.toByteArray(Charsets.UTF_8))

    /** Descifra con el cipher ya autorizado por el BiometricPrompt. */
    fun descifrar(cipher: Cipher, datosCifrados: ByteArray): String =
        String(cipher.doFinal(datosCifrados), Charsets.UTF_8)

    fun eliminarClave() {
        runCatching { keyStore.deleteEntry(ALIAS) }
    }
}
