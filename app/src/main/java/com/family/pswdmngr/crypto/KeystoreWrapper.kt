package com.family.pswdmngr.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the vault key with a non-extractable hardware-backed Keystore key,
 * gated behind biometric auth. Enables fingerprint unlock without ever
 * persisting the vault key in plaintext.
 */
class KeystoreWrapper(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("biometric_wrap_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val ALIAS = "pswdmngr_bio_wrap_v1"
        private const val PREF_BLOB = "wrapped_vault_key"
    }

    private fun keystore(): KeyStore? = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (_: Throwable) {
        null
    }

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val ks = keystore() ?: return null
            ks.getKey(ALIAS, null)?.let { return it as? SecretKey }
            val spec = KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                // Android 9-10 API: auth valid briefly so we can use the key right after prompt
                .setUserAuthenticationValidityDurationSeconds(10)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            ).apply { init(spec) }.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    val isEnabled: Boolean get() = try { prefs.contains(PREF_BLOB) } catch (_: Throwable) { false }

    /** Call right after a successful master-password unlock + BiometricPrompt auth. */
    fun enable(vaultKey: ByteArray): Boolean {
        return try {
            val key = getOrCreateKey() ?: return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ct = cipher.doFinal(vaultKey)
            val blob = cipher.iv + ct
            prefs.edit().putString(PREF_BLOB, android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP)).apply()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Call right after BiometricPrompt success. Returns the vault key, or null if unavailable/invalidated. */
    fun unwrap(): ByteArray? {
        val b64 = try { prefs.getString(PREF_BLOB, null) } catch (_: Throwable) { null } ?: return null
        return try {
            val key = getOrCreateKey() ?: return null
            val blob = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(128, blob.copyOfRange(0, 12))
            )
            cipher.doFinal(blob.copyOfRange(12, blob.size))
        } catch (e: Exception) {
            // Key invalidated (new fingerprint enrolled) or tampered — force password unlock
            disable()
            null
        }
    }

    fun disable() {
        try { prefs.edit().remove(PREF_BLOB).apply() } catch (_: Throwable) {}
        try { keystore()?.deleteEntry(ALIAS) } catch (_: Throwable) {}
    }
}
