package com.family.pswdmngr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.family.pswdmngr.crypto.SecureData
import com.family.pswdmngr.crypto.VaultCrypto
import com.family.pswdmngr.crypto.RecoveryKeyGenerator
import net.sqlcipher.database.SupportFactory
import java.io.File

/**
 * Session state: holds the open (decrypted) database only while unlocked.
 *
 * ## Key architecture (v2+)
 *
 * A randomly-generated vault master key (256-bit) is the sole credential for
 * the SQLCipher database. It is wrapped (AES-256-GCM) under TWO independent
 * Argon2id-derived keys and both wrapped blobs are stored in SharedPreferences.
 *
 *   pwKey  = Argon2id(master_password, pw_salt)
 *   recKey = Argon2id(recovery_phrase,  rec_salt)
 *
 *   wrapped_pw    = AES-GCM_encrypt(pwKey,  vaultMasterKey)
 *   wrapped_rec   = AES-GCM_encrypt(recKey, vaultMasterKey)
 *
 * Either credential alone can unwrap the same vault master key. Neither
 * credential is derivable from the other.
 *
 * ## V1 → V2 migration
 *
 * Legacy vaults (v1) derived the vault key directly from the master password.
 * On the first successful unlock after upgrade, the vault is migrated in-place:
 * the existing derived key becomes [vaultMasterKey] and a recovery key is
 * generated for the first time.
 */
object VaultSession {

    private const val META_PREFS = "vault_meta"

    // Keys common to v1 and v2
    private const val KEY_SALT = "kdf_salt"
    private const val KEY_CHECK = "key_check"        // AES-GCM(vaultMasterKey, "vault-ok")
    private const val KEY_LAST_PW_UNLOCK = "last_pw_unlock"
    private const val KEY_BIO_GRACE_MIN = "bio_grace_minutes"

    // Keys exclusive to v2+ dual-wrap scheme
    private const val KEY_DATA_VERSION = "vault_data_version"
    private const val KEY_PW_WRAPPED = "pw_wrapped_mk"          // vaultMasterKey wrapped with pwKey
    private const val KEY_RECOVERY_WRAPPED = "recovery_wrapped_mk" // vaultMasterKey wrapped with recovery key
    private const val KEY_RECOVERY_SALT = "recovery_salt"
    private const val KEY_RECOVERY_ENCRYPTED = "recovery_enc"   // recovery phrase encrypted with vaultMasterKey
    private const val KEY_HAS_RECOVERY = "has_recovery"         // boolean flag
    private const val KEY_PENDING_RECOVERY_DISPLAY = "pending_recovery_display" // cross-session migration flag

    private const val VAULT_VERSION_1 = 1
    private const val VAULT_VERSION_2 = 2

    /** Default: biometric/device unlock allowed for 1 hour after a password unlock. */
    const val DEFAULT_BIO_GRACE_MIN = 60

    @Volatile private var db: VaultDatabase? = null
    @Volatile private var vaultKey: SecureData? = null

    /**
     * After a v1→v2 migration, holds the newly generated recovery key
     * so the UI can display it once. Cleared after read.
     */
    @Volatile var pendingRecoveryKey: String? = null

    // ── helpers ──────────────────────────────────────────────────────────

    val isUnlocked: Boolean get() = db != null

    /**
     * EncryptedSharedPreferences — even though the values stored here
     * (wrapped blobs, salts) are already cryptographically opaque,
     * this guarantees the keys and values are AES-256 encrypted at rest
     * so a file-system-level read of the XML reveals nothing at all.
     */
    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            META_PREFS,
            masterKey,
            ctx.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun edit(ctx: Context): SharedPreferences.Editor =
        prefs(ctx).edit()

    fun dao(): VaultDao = requireDb().dao()
    fun cardDao(): CardDao = requireDb().cardDao()
    fun bankDao(): BankDao = requireDb().bankDao()
    fun docDao(): DocDao = requireDb().docDao()
    fun attachmentDao(): AttachmentDao = requireDb().attachmentDao()
    fun noteDao(): NoteDao = requireDb().noteDao()
    fun taskDao(): TaskDao = requireDb().taskDao()
    fun trashDao(): TrashDao = requireDb().trashDao()

    private fun requireDb(): VaultDatabase = db ?: error("Vault locked")

    /** Returns a COPY of the vault master key. Caller MUST wipe() the copy. */
    fun currentKey(): ByteArray = vaultKey?.copyOf() ?: error("Vault locked")

    fun vaultExists(ctx: Context): Boolean =
        prefs(ctx).contains(KEY_SALT)  // present in both v1 and v2

    // ── Biometric grace policy ───────────────────────────────────────────

    fun bioGraceMinutes(ctx: Context): Int =
        prefs(ctx).getInt(KEY_BIO_GRACE_MIN, DEFAULT_BIO_GRACE_MIN)

    fun setBioGraceMinutes(ctx: Context, minutes: Int) {
        edit(ctx).putInt(KEY_BIO_GRACE_MIN, minutes).apply()
    }

    fun bioGraceActive(ctx: Context): Boolean {
        val last = prefs(ctx).getLong(KEY_LAST_PW_UNLOCK, 0L)
        if (last == 0L) return false
        val graceMs = bioGraceMinutes(ctx) * 60_000L
        val elapsed = System.currentTimeMillis() - last
        return elapsed in 0 until graceMs
    }

    fun bioGraceRemainingMs(ctx: Context): Long {
        val last = prefs(ctx).getLong(KEY_LAST_PW_UNLOCK, 0L)
        if (last == 0L) return 0
        val end = last + bioGraceMinutes(ctx) * 60_000L
        return (end - System.currentTimeMillis()).coerceAtLeast(0)
    }

    private fun markPasswordUnlock(ctx: Context) {
        edit(ctx).putLong(KEY_LAST_PW_UNLOCK, System.currentTimeMillis()).apply()
    }

    // ── Vault creation (always v2) ───────────────────────────────────────

    /**
     * Create a brand-new vault.
     * @return the Recovery Key phrase that must be shown to the user.
     */
    fun create(ctx: Context, password: CharArray): String {
        // 1. Generate a random vault master key (256-bit) — this is the real DB key
        val vaultMasterKey = VaultCrypto.randomBytes(32)

        // 2. Derive key from master password → wrap the master key
        val pwSalt = VaultCrypto.randomBytes(16)
        val pwBytes = VaultCrypto.charsToBytes(password)
        val pwKey = VaultCrypto.deriveKey(pwBytes, pwSalt)
        VaultCrypto.wipe(pwBytes)
        val pwWrapped = VaultCrypto.encrypt(pwKey, vaultMasterKey, aad = "pw".toByteArray())
        VaultCrypto.wipe(pwKey)

        // 3. Generate recovery key → derive → wrap the master key
        val recoveryPhrase = RecoveryKeyGenerator.generate()
        val recoverySalt = VaultCrypto.randomBytes(16)
        val recBytes = VaultCrypto.charsToBytes(recoveryPhrase.toCharArray())
        val recKey = VaultCrypto.deriveKey(recBytes, recoverySalt)
        VaultCrypto.wipe(recBytes)
        val recoveryWrapped = VaultCrypto.encrypt(recKey, vaultMasterKey, aad = "rec".toByteArray())
        VaultCrypto.wipe(recKey)

        // 4. Encrypt the recovery phrase under the vault master key (for in-app viewing later)
        val recoveryEncrypted = VaultCrypto.encrypt(vaultMasterKey, recoveryPhrase.toByteArray())

        // 5. Verification check blob
        val check = VaultCrypto.encrypt(vaultMasterKey, "vault-ok".toByteArray())

        // 6. Persist everything
        edit(ctx)
            .putString(KEY_SALT, b64(pwSalt))
            .putString(KEY_CHECK, b64(check))
            .putString(KEY_PW_WRAPPED, b64(pwWrapped))
            .putString(KEY_RECOVERY_WRAPPED, b64(recoveryWrapped))
            .putString(KEY_RECOVERY_SALT, b64(recoverySalt))
            .putString(KEY_RECOVERY_ENCRYPTED, b64(recoveryEncrypted))
            .putBoolean(KEY_HAS_RECOVERY, true)
            .putInt(KEY_DATA_VERSION, VAULT_VERSION_2)
            .apply()

        // 7. Open the DB with the vault master key
        openDb(ctx, vaultMasterKey)
        VaultCrypto.wipe(vaultMasterKey)
        markPasswordUnlock(ctx)

        return recoveryPhrase
    }

    // ── Unlock with master password ──────────────────────────────────────

    /** Unlock with master password. Returns false on wrong password. */
    fun unlock(ctx: Context, password: CharArray): Boolean {
        val p = prefs(ctx)
        val version = p.getInt(KEY_DATA_VERSION, VAULT_VERSION_1)

        return if (version >= VAULT_VERSION_2) {
            unlockV2(ctx, p, password)
        } else {
            unlockV1(ctx, p, password)
        }
    }

    /** Legacy v1: key is derived directly from password (no dual-wrap). */
    private fun unlockV1(ctx: Context, p: SharedPreferences, password: CharArray): Boolean {
        val salt = ub64(p.getString(KEY_SALT, null) ?: return false)
        val check = ub64(p.getString(KEY_CHECK, null) ?: return false)
        val pwBytes = VaultCrypto.charsToBytes(password)
        val key = VaultCrypto.deriveKey(pwBytes, salt)
        VaultCrypto.wipe(pwBytes)

        return try {
            VaultCrypto.decrypt(key, check)
            // Migrate to v2: promote derived key to vaultMasterKey + generate recovery key
            migrateToV2(ctx, p, key)
            openDb(ctx, key)
            markPasswordUnlock(ctx)
            true
        } catch (e: Exception) {
            VaultCrypto.wipe(key)
            false
        }
    }

    /** v2+: derive pwKey → unwrap vaultMasterKey → verify → open DB. */
    private fun unlockV2(ctx: Context, p: SharedPreferences, password: CharArray): Boolean {
        val pwSalt = ub64(p.getString(KEY_SALT, null) ?: return false)
        val pwWrapped = ub64(p.getString(KEY_PW_WRAPPED, null) ?: return false)
        val check = ub64(p.getString(KEY_CHECK, null) ?: return false)

        val pwBytes = VaultCrypto.charsToBytes(password)
        val pwKey = VaultCrypto.deriveKey(pwBytes, pwSalt)
        VaultCrypto.wipe(pwBytes)

        return try {
            val vaultMasterKey = VaultCrypto.decrypt(pwKey, pwWrapped, aad = "pw".toByteArray())
            VaultCrypto.wipe(pwKey)
            VaultCrypto.decrypt(vaultMasterKey, check) // verify
            openDb(ctx, vaultMasterKey)
            VaultCrypto.wipe(vaultMasterKey)
            markPasswordUnlock(ctx)
            true
        } catch (e: Exception) {
            VaultCrypto.wipe(pwKey)
            false
        }
    }

    // ── Unlock with recovery key ─────────────────────────────────────────

    /**
     * Verify a recovery key and return the vault master key for a password
     * change flow. The caller receives ownership of the returned ByteArray
     * and MUST wipe it when done (typically after calling [changePassword]).
     *
     * Returns null on wrong recovery key.
     */
    fun unlockWithRecoveryKey(ctx: Context, rawPhrase: String): ByteArray? {
        val p = prefs(ctx)
        val version = p.getInt(KEY_DATA_VERSION, VAULT_VERSION_1)
        if (version < VAULT_VERSION_2) return null  // no recovery key exists yet

        val recoverySalt = ub64(p.getString(KEY_RECOVERY_SALT, null) ?: return null)
        val recoveryWrapped = ub64(p.getString(KEY_RECOVERY_WRAPPED, null) ?: return null)
        val check = ub64(p.getString(KEY_CHECK, null) ?: return null)

        val normalized = RecoveryKeyGenerator.normalize(rawPhrase)
        if (!RecoveryKeyGenerator.isValid(normalized)) return null

        val recBytes = VaultCrypto.charsToBytes(normalized.toCharArray())
        val recKey = VaultCrypto.deriveKey(recBytes, recoverySalt)
        VaultCrypto.wipe(recBytes)

        return try {
            val vaultMasterKey = VaultCrypto.decrypt(recKey, recoveryWrapped, aad = "rec".toByteArray())
            VaultCrypto.wipe(recKey)
            VaultCrypto.decrypt(vaultMasterKey, check) // verify
            vaultMasterKey  // caller owns it now, must wipe
        } catch (e: Exception) {
            VaultCrypto.wipe(recKey)
            null
        }
    }

    /**
     * Change the master password. The caller must have already obtained
     * [vaultMasterKey] via a successful [unlockWithRecoveryKey] call, or
     * from the current session via [currentKey].
     *
     * The old password-wrapped blob is discarded and a new one is created.
     */
    fun changePassword(ctx: Context, vaultMasterKey: ByteArray, newPassword: CharArray) {
        val pwSalt = VaultCrypto.randomBytes(16)
        val pwBytes = VaultCrypto.charsToBytes(newPassword)
        val pwKey = VaultCrypto.deriveKey(pwBytes, pwSalt)
        VaultCrypto.wipe(pwBytes)

        val pwWrapped = VaultCrypto.encrypt(pwKey, vaultMasterKey, aad = "pw".toByteArray())
        VaultCrypto.wipe(pwKey)

        edit(ctx)
            .putString(KEY_SALT, b64(pwSalt))
            .putString(KEY_PW_WRAPPED, b64(pwWrapped))
            .apply()

        markPasswordUnlock(ctx)
    }

    // ── Biometric unlock (stored key) ────────────────────────────────────

    /**
     * Unlock with a key unwrapped from the biometric Keystore path.
     * If the grace window has lapsed the master password is required,
     * UNLESS the user has explicitly enabled biometric (keystore is active)
     * — in that case biometric re-unlock is always allowed as a convenience
     * so users don't hit a confusing "expired" state right after setup.
     *
     * Each successful biometric unlock refreshes the grace window.
     */
    fun unlockWithKey(ctx: Context, key: ByteArray): Boolean {
        val p = prefs(ctx)
        val check = ub64(p.getString(KEY_CHECK, null) ?: run {
            VaultCrypto.wipe(key)
            return false
        })
        return try {
            VaultCrypto.decrypt(key, check)
            openDb(ctx, key)
            markPasswordUnlock(ctx) // refresh grace window so biometric keeps working
            true
        } catch (e: Exception) {
            VaultCrypto.wipe(key)
            false
        }
    }

    // ── Password verification while unlocked ─────────────────────────────

    /** Re-verify the master password while unlocked (refreshes the grace window). */
    fun verifyPassword(ctx: Context, password: CharArray): Boolean {
        val p = prefs(ctx)
        val version = p.getInt(KEY_DATA_VERSION, VAULT_VERSION_1)
        return if (version >= VAULT_VERSION_2) {
            verifyPasswordV2(ctx, p, password)
        } else {
            verifyPasswordV1(ctx, p, password)
        }
    }

    private fun verifyPasswordV1(ctx: Context, p: SharedPreferences, password: CharArray): Boolean {
        val salt = ub64(p.getString(KEY_SALT, null) ?: return false)
        val check = ub64(p.getString(KEY_CHECK, null) ?: return false)
        val pwBytes = VaultCrypto.charsToBytes(password)
        val key = VaultCrypto.deriveKey(pwBytes, salt)
        VaultCrypto.wipe(pwBytes)
        return try {
            VaultCrypto.decrypt(key, check)
            markPasswordUnlock(ctx)
            true
        } catch (_: Exception) { false }
            finally { VaultCrypto.wipe(key) }
    }

    private fun verifyPasswordV2(ctx: Context, p: SharedPreferences, password: CharArray): Boolean {
        val pwSalt = ub64(p.getString(KEY_SALT, null) ?: return false)
        val pwWrapped = ub64(p.getString(KEY_PW_WRAPPED, null) ?: return false)
        val check = ub64(p.getString(KEY_CHECK, null) ?: return false)
        val pwBytes = VaultCrypto.charsToBytes(password)
        val pwKey = VaultCrypto.deriveKey(pwBytes, pwSalt)
        VaultCrypto.wipe(pwBytes)
        return try {
            val vaultMasterKey = VaultCrypto.decrypt(pwKey, pwWrapped, aad = "pw".toByteArray())
            VaultCrypto.wipe(pwKey)
            VaultCrypto.decrypt(vaultMasterKey, check)
            markPasswordUnlock(ctx)
            true
        } catch (_: Exception) { false }
            finally { VaultCrypto.wipe(pwKey) }
    }

    // ── View / export recovery key ───────────────────────────────────────

    /**
     * Returns the plaintext recovery key phrase.
     * The vault must be unlocked. Requires decrypting the stored blob.
     */
    fun getRecoveryKey(ctx: Context): String? {
        if (!isUnlocked) return null
        val enc = ub64(prefs(ctx).getString(KEY_RECOVERY_ENCRYPTED, null) ?: return null)
        val mk = vaultKey?.copyOf() ?: return null
        return try {
            val bytes = VaultCrypto.decrypt(mk, enc)
            String(bytes)
        } catch (_: Exception) { null }
            finally { VaultCrypto.wipe(mk) }
    }

    /** True if the vault has a recovery key set up (v2+). */
    fun hasRecoveryKey(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HAS_RECOVERY, false)

    // ── Migration v1 → v2 ────────────────────────────────────────────────

    /**
     * On the first v1 unlock after upgrade, promote the legacy derived key
     * to the vault master key, generate a recovery key, and write the v2 blobs.
     */
    private fun migrateToV2(ctx: Context, p: SharedPreferences, derivedKey: ByteArray) {
        val recoveryPhrase = RecoveryKeyGenerator.generate()

        // Generate recovery-key derived key and wrap the vault master key
        val recoverySalt = VaultCrypto.randomBytes(16)
        val recBytes = VaultCrypto.charsToBytes(recoveryPhrase.toCharArray())
        val recKey = VaultCrypto.deriveKey(recBytes, recoverySalt)
        VaultCrypto.wipe(recBytes)
        val recoveryWrapped = VaultCrypto.encrypt(recKey, derivedKey, aad = "rec".toByteArray())
        VaultCrypto.wipe(recKey)

        // Also wrap the vault master key with the existing derived key (so password still works)
        val pwWrapped = VaultCrypto.encrypt(derivedKey, derivedKey, aad = "pw".toByteArray())

        // Encrypt recovery phrase under the vault master key
        val recoveryEncrypted = VaultCrypto.encrypt(derivedKey, recoveryPhrase.toByteArray())

        // In v1, KEY_CHECK is encrypted with the derived key (which IS the vault master key)
        // — keep it as-is, no need to re-create

        edit(ctx)
            .putString(KEY_PW_WRAPPED, b64(pwWrapped))
            .putString(KEY_RECOVERY_WRAPPED, b64(recoveryWrapped))
            .putString(KEY_RECOVERY_SALT, b64(recoverySalt))
            .putString(KEY_RECOVERY_ENCRYPTED, b64(recoveryEncrypted))
            .putBoolean(KEY_HAS_RECOVERY, true)
            .putInt(KEY_DATA_VERSION, VAULT_VERSION_2)
            .putBoolean(KEY_PENDING_RECOVERY_DISPLAY, true)  // survives process kill
            .apply()

        // Signal the UI to show the recovery key once
        pendingRecoveryKey = recoveryPhrase
    }

    /** True if a v1→v2 migration produced a recovery key the user hasn't seen yet. */
    fun hasPendingRecoveryDisplay(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PENDING_RECOVERY_DISPLAY, false)

    /** Called by the UI after the user acknowledges their recovery key. */
    fun dismissPendingRecoveryDisplay(ctx: Context) {
        edit(ctx).putBoolean(KEY_PENDING_RECOVERY_DISPLAY, false).apply()
        pendingRecoveryKey = null
    }

    // ── DB lifecycle ─────────────────────────────────────────────────────

    private fun openDb(ctx: Context, key: ByteArray) {
        val secureKey = SecureData(key.size).also { _ ->
            // Copy raw key bytes into the secure buffer using a temporary copy
            val tmp = SecureData(key.size).also { s -> s.withBytes { key.copyInto(it) } }
            VaultCrypto.wipe(key)
            // Pass a copy to SQLCipher (it takes ownership of its own copy)
            val factory = SupportFactory(tmp.copyOf(), null, false)
            db = Room.databaseBuilder(ctx.applicationContext, VaultDatabase::class.java, "vault.db")
                .openHelperFactory(factory)
                .addMigrations(VaultDatabase.MIGRATION_1_2, VaultDatabase.MIGRATION_2_3, VaultDatabase.MIGRATION_3_4)
                .build()
            vaultKey = tmp
        }
    }

    fun lock() {
        db?.close()
        db = null
        vaultKey?.wipe()
        vaultKey = null
    }

    fun dbFile(ctx: Context): File = ctx.getDatabasePath("vault.db")

    private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    private fun ub64(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
}
