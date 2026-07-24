package com.family.pswdmngr.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "biometric_wrap_enc",
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val ALIAS = "pswdmngr_bio_wrap_v1"
        private const val PREF_BLOB = "wrapped_vault_key"
    }

    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        keystore().getKey(ALIAS, null)?.let { return it as SecretKey }
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
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply { init(spec) }.generateKey()
    }

    val isEnabled: Boolean get() = prefs.contains(PREF_BLOB)

    /** Call right after a successful master-password unlock + BiometricPrompt auth. */
    fun enable(vaultKey: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ct = cipher.doFinal(vaultKey)
        val blob = cipher.iv + ct
        prefs.edit().putString(PREF_BLOB, android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP)).apply()
    }

    /** Call right after BiometricPrompt success. Returns the vault key, or null if unavailable/invalidated. */
    fun unwrap(): ByteArray? {
        val b64 = prefs.getString(PREF_BLOB, null) ?: return null
        return try {
            val blob = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, getOrCreateKey(),
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
        prefs.edit().remove(PREF_BLOB).apply()
        try { keystore().deleteEntry(ALIAS) } catch (_: Exception) {}
    }
}
