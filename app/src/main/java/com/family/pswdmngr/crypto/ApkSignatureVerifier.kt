package com.family.pswdmngr.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

/**
 * Runtime APK signature check (basic anti-tamper).
 *
 * ## Self-healing model
 *
 * On the very first launch after a legitimate install, the app records the
 * SHA-256 hash of the APK's signing certificate into EncryptedSharedPreferences.
 * Every subsequent launch compares the live signature against that recorded hash.
 *
 * If the APK is re-signed with a different key (tampered build), the hash won't
 * match and [isTampered] returns true, triggering a persistent warning.
 *
 * ## First-install trust
 *
 * The first-seen signature is trusted as genuine because a tampered APK would
 * need to also strip or modify this verification code itself to bypass the check.
 * This creates a trust-on-first-use (TOFU) model that is immune to the
 * "forgot to set the placeholder" problem of hardcoded hashes.
 *
 * ## Debug builds
 *
 * Android Studio's debug keystore auto-signs every debug APK. The first debug
 * install records *that* hash, and subsequent debug installs from the same
 * machine pass. Different machines produce different debug certs, so a debug
 * APK shared between developers would trigger a tamper warning — expected and
 * correct behavior.
 */
object ApkSignatureVerifier {

    private const val PREFS_NAME = "sig_verifier_enc"
    private const val KEY_RECORDED_HASH = "recorded_apk_sig_sha256"

    private var _verified: Boolean? = null

    /** True if the APK's signature matches the first-recorded value. Cached. */
    fun isSignatureValid(ctx: Context): Boolean {
        _verified?.let { return it }

        return try {
            val currentHash = computeCurrentHash(ctx) ?: return false
            val recordedHash = getRecordedHash(ctx)

            if (recordedHash == null) {
                // ── TOFU recording ─────────────────────────────────────────
                // Distribution model: this app is built and installed personally
                // on each family member's device. It is never distributed as a
                // shareable APK or through a third-party store to people who
                // aren't handed the phone after install.
                //
                // TOFU protects against POST-install tampering (someone obtains
                // the device and replaces the APK with a re-signed malicious
                // version). It does NOT protect against a FORGED first install
                // (someone sends a tampered APK to an unwitting user before the
                // genuine app has ever been opened on that device).
                //
                // If distribution ever changes to "share this APK file for
                // others to install themselves," replace this TOFU recording
                // with a hardcoded EXPECTED_SIG_HASH comparison — generate the
                // hash with:
                //   keytool -list -v -keystore your.keystore -alias your_alias
                // then Base64-encode the SHA-256 hex string.
                // ─────────────────────────────────────────────────────────────
                recordHash(ctx, currentHash)
                _verified = true
                true
            } else {
                val match = currentHash == recordedHash
                _verified = match
                match
            }
        } catch (e: Exception) {
            _verified = false
            false
        }
    }

    /** Only the first failure matters — subsequent calls return cached result. */
    fun isTampered(ctx: Context): Boolean = !isSignatureValid(ctx)

    fun resetCache() { _verified = null }

    // ── internals ──

    private fun computeCurrentHash(ctx: Context): String? {
        return try {
            val info = ctx.packageManager.getPackageInfo(
                ctx.packageName,
                PackageManager.GET_SIGNATURES,
            )
            val cert = info.signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun prefs(ctx: Context) = run {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            ctx.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun getRecordedHash(ctx: Context): String? =
        prefs(ctx).getString(KEY_RECORDED_HASH, null)

    private fun recordHash(ctx: Context, hash: String) {
        prefs(ctx).edit().putString(KEY_RECORDED_HASH, hash).apply()
    }
}
