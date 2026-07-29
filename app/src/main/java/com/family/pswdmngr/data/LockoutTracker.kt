package com.family.pswdmngr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Persistent exponential-backoff lockout tracker for failed vault-unlock
 * attempts. Also manages the optional "wipe vault after 10 failed attempts"
 * setting.
 *
 * Lockout durations (per the requirement):
 *   Fails 1-2  → no lockout
 *   Fail  3    → 30s
 *   Fail  4    → 60s
 *   Fail  5    → 120s
 *   Fail  6    → 240s
 *   Fail  7+   → 480s (capped)
 *
 * The counter resets to 0 on a successful unlock.
 */
object LockoutTracker {

    private const val PREFS_NAME = "lockout_enc"
    private const val KEY_COUNT = "fail_count"
    private const val KEY_LAST_FAIL = "last_fail_ms"
    private const val KEY_WIPE_SETTING = "wipe_after_10"

    private const val BASE_LOCKOUT_MS = 30_000L    // 30 seconds at fail 3
    private const val MAX_LOCKOUT_MS = 480_000L    // 8 minutes cap

    @Volatile private var cachedPrefs: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        val existing = cachedPrefs
        if (existing != null) return existing
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            ctx.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { cachedPrefs = it }
    }

    /** Number of consecutive failed unlock attempts. */
    fun failCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_COUNT, 0)

    /** Record a failed unlock attempt. Returns true if the vault should auto-wipe. */
    fun recordFailedAttempt(ctx: Context): Boolean {
        val count = failCount(ctx) + 1
        prefs(ctx).edit()
            .putInt(KEY_COUNT, count)
            .putLong(KEY_LAST_FAIL, System.currentTimeMillis())
            .apply()
        // Auto-wipe if setting is on and we hit 10+ failures
        return count >= 10 && wipeEnabled(ctx)
    }

    /** Call after a successful unlock — resets the fail counter. */
    fun recordSuccessfulUnlock(ctx: Context) {
        prefs(ctx).edit()
            .putInt(KEY_COUNT, 0)
            .putLong(KEY_LAST_FAIL, 0L)
            .apply()
    }

    /**
     * Remaining lockout time in milliseconds.
     * Returns 0 if the user can attempt unlock again now.
     */
    fun remainingLockoutMs(ctx: Context): Long {
        val count = failCount(ctx)
        if (count < 3) return 0L

        val lastFail = prefs(ctx).getLong(KEY_LAST_FAIL, 0L)
        val elapsed = System.currentTimeMillis() - lastFail

        val lockoutDuration = when (count) {
            3 -> BASE_LOCKOUT_MS
            4 -> BASE_LOCKOUT_MS * 2
            5 -> BASE_LOCKOUT_MS * 4
            6 -> BASE_LOCKOUT_MS * 8
            else -> MAX_LOCKOUT_MS
        }
        return (lockoutDuration - elapsed).coerceAtLeast(0)
    }

    /** Human-readable lockout time (e.g. "2min 30s"). */
    fun remainingLabel(ctx: Context): String {
        val ms = remainingLockoutMs(ctx)
        if (ms <= 0) return ""
        val secs = (ms + 999) / 1000
        return when {
            secs >= 60 -> "${secs / 60}min ${secs % 60}s"
            else -> "${secs}s"
        }
    }

    /* ---------- Wipe-after-10 setting ---------- */

    fun wipeEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WIPE_SETTING, false)

    fun setWipeEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WIPE_SETTING, enabled).apply()
    }
}
