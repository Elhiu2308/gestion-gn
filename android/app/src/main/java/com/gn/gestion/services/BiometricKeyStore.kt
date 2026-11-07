package com.gn.gestion.services;

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricKeyStore {

    private const val ALIAS = "gn_biometria_v1"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_TAG = 128

    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun clave(crearSiFalta: Boolean): SecretKey {
        val existente = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existente != null) {

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

    fun cipherCifrado(): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.ENCRYPT_MODE, clave(crearSiFalta = true))
        }

    fun cipherDescifrado(iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, clave(crearSiFalta = false), GCMParameterSpec(BITS_TAG, iv))
        }

    fun cifrar(cipher: Cipher, texto: String): Pair<ByteArray, ByteArray> =
        cipher.iv to cipher.doFinal(texto.toByteArray(Charsets.UTF_8))

    fun descifrar(cipher: Cipher, datosCifrados: ByteArray): String =
        String(cipher.doFinal(datosCifrados), Charsets.UTF_8)

    fun eliminarClave() {
        runCatching { keyStore.deleteEntry(ALIAS) }
    }
}
