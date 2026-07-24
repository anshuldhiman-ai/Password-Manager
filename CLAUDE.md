# CLAUDE.md — PSWD MNGR Codebase Guide

## Project Overview
Offline Android password manager. Kotlin + Jetpack Compose. Package: `com.family.pswdmngr`.
**FULLY OFFLINE** — never add INTERNET permission or network calls.

## Key Architecture Decisions

### Vault Key System (v2+ dual-wrap)
- **Vault master key** = random 256-bit key (NOT derived from password)
- Wrapped TWICE independently:
  1. `pwKey = Argon2id(password, pw_salt)` → encrypts vault master key → stored as `pw_wrapped_mk`
  2. `recKey = Argon2id(recovery_phrase, rec_salt)` → encrypts vault master key → stored as `recovery_wrapped_mk`
- Either credential alone can unwrap the same vault key
- Stored in **EncryptedSharedPreferences** `vault_meta` (not Room DB — needed to unlock the DB)
- Verification: `AES-GCM(vault_master_key, "vault-ok")` stored as `key_check`

### EncryptedSharedPreferences (defense-in-depth)
All three SharedPreferences stores use `EncryptedSharedPreferences` backed by Android Keystore `MasterKey(AES256_GCM)`:
| Store | Location | Contents |
|-------|----------|----------|
| `vault_meta_enc` | VaultSession.kt | Wrapped vault keys, salts, recovery key blob, bio grace, data version |
| `lockout_enc` | LockoutTracker.kt | Failed attempt count, lockout timestamps, wipe-after-10 setting |
| `biometric_wrap_enc` | KeystoreWrapper.kt | Biometric-wrapped vault key (already encrypted with Android Keystore) |

Even though the blob values stored inside are already cryptographically opaque, EncryptedSharedPreferences ensures the XML file on disk is fully AES-256 encrypted — protecting against file-system reads that could leak metadata like the number of stored keys or their sizes.

### Code Conventions
- **No Strings for secrets** — passwords/keys are `ByteArray` or `CharArray`, never `String`
- **Wipe after use** — always call `VaultCrypto.wipe()` on key material when done
- **SecureData** — wraps ByteArray with libsodium `sodium_mlock` + `sodium_memzero`
- **AAD tags** — dual-wrap uses AAD: `"pw".toByteArray()` for password wrap, `"rec".toByteArray()` for recovery wrap
- **Export format** — backup files use magic `PSWDMGR1` header + version byte + salt + AES-GCM blob
- **IS_SENSITIVE** — clipboard entries marked `android.content.extra.IS_SENSITIVE=true` (API 24+) to hide from clipboard preview

### Navigation
- **MainScreen** (bottom nav) handles 5 tabs: Vault, Cards, Notes, Search, More
- **Auth routes** (no bottom bar): onboarding, unlock, recoveryKey, forgotPassword
- **Detail routes** (no bottom bar): entry/{id}, cardDetail/{id}, bankDetail/{id}, docDetail/{id}, etc.
- Bottom bar shown only on tab routes; detail screens navigate outside it

### Database (Room + SQLCipher)
- 9 entities: VaultEntry, CardEntry, BankEntry, DocumentEntry, Attachment, NoteEntry, TaskList, TaskItem, TrashItem
- Version: **4** (migrations: 1→2, 2→3, 3→4)
- All entity classes in `Models.kt`
- VaultSession manages DB lifecycle — `openDb()` creates SupportFactory with vault master key

### Security Features (verified present)

| Feature | File | Status |
|---------|------|--------|
| AndroidManifest backup prevention | `allowBackup="false"`, `fullBackupContent="false"` (lines 17-18) | ✅ Confirmed |
| Screenshot blocking | `WindowManager.LayoutParams.FLAG_SECURE` in MainActivity.onCreate | ✅ Confirmed |
| Clipboard auto-clear | `copySecret()` in VaultApp.kt — 30s timer, `IS_SENSITIVE` flag | ✅ Confirmed |
| Auto-lock on background | `LOCK_DELAY_MS=30_000` in VaultApp.kt — key zeroed via SecureData.wipe() | ✅ Confirmed |
| Biometric grace policy | `bioGraceMinutes()`, `bioGraceActive()`, `bioGraceRemainingMs()` in VaultSession | ✅ Confirmed |
| Biometric key invalidation on new enrollment | `setInvalidatedByBiometricEnrollment(true)` in KeystoreWrapper.kt:54 — new fingerprint/face added to device invalidates the Keystore key, breaking biometric unlock | ✅ Confirmed |
| Root detection | RootDetector.kt — cached, runs at screen open, disables biometric | ✅ Confirmed |
| APK tamper check (TOFU) | ApkSignatureVerifier.kt — trust-on-first-use: records signing cert SHA-256 on first install, verifies against recorded hash on every subsequent launch. No placeholder constant to forget. **Distribution model:** built and installed personally on each device (not distributed as a shareable APK). TOFU protects against post-install tampering but does NOT protect against a forged first install. If distribution changes to shareable APK, switch to pinned EXPECTED_SIG_HASH. | ✅ Confirmed |
| Exponential backoff | LockoutTracker.kt — 3→30s, 4→60s, 5→120s, 6→240s, 7+→480s, persists across kills | ✅ Confirmed |
| Auto-wipe after 10 fails | LockoutTracker.wipeEnabled() — optional, toggled in Settings | ✅ Confirmed |
| Reveal lock for sensitive fields | RevealLock.kt — biometric gate + 30s auto-hide | ✅ Confirmed |
| Full ProGuard/R8 obfuscation | proguard-rules.pro — repackage, allowaccessmodification, overloadaggressively | ✅ Confirmed |

### Security Patterns
| Pattern | Where |
|---------|-------|
| **Exponential backoff** | LockoutTracker.kt — EncryptedSharedPreferences, reset on unlock |
| **Root detection** | RootDetector.kt — cached, runs at screen open |
| **APK tamper check (TOFU)** | ApkSignatureVerifier.kt — records first-seen cert SHA-256 in EncryptedSharedPreferences; self-healing, no placeholder. Personal distribution model only — see code comment at recording site for pinned-hash migration path |
| **Reveal lock** | RevealLock.kt — biometric gate + auto-hide 30s timer |
| **Recycle bin** | TrashItem table + RecycleBinManager.kt — 30-day auto-purge |
| **Password health** | PasswordHealthScreen.kt — fully offline analysis |
| **Migration recovery key display** | `KEY_PENDING_RECOVERY_DISPLAY` persistent flag — survives process kill after v1→v2 migration |

### Migration: v1→v2 Recovery Key Setup
When a legacy v1 vault is upgraded, `migrateToV2()` in VaultSession:
1. Generates a recovery key and wraps the vault master key with it
2. Stores the recovery key encrypted under the vault master key (for in-app viewing)
3. Sets **both** a volatile field (`pendingRecoveryKey`) AND a persistent flag (`KEY_PENDING_RECOVERY_DISPLAY=true`) in EncryptedSharedPreferences
4. MainScreen checks **both** on launch: volatile first (instant), then persistent flag (survives process kill)
5. User sees a non-dismissible dialog (back-press is a no-op) showing the recovery key
6. Dialog forces the user to **retype a randomly selected group** (e.g., "Type group 3:") — same strictness as onboarding
7. Confirm button is disabled until the typed group matches correctly
8. `dismissPendingRecoveryDisplay()` clears both the flag and volatile field — only called after successful confirmation

### UI Conventions
- **Base color**: `Midnight` (#0B0E1A), `Surface1` (#141829), `Surface2` (#1C2138)
- **Accents**: Violet (#7C5CFF), Cyan (#4DD0E1), Mint (#34D399), Coral (#FF6B81), Amber (#FFC46B)
- **GlassCard** — use sparingly (flatten most surfaces); reserved for unlock screen, top bar, emphasis
- **Dark theme only** — no light mode
- **Empty states** — every list needs an EmptyState composable, never a blank screen
- **Haptic feedback** — on copy, reveal, FAB press (`HapticFeedbackType.LongPress`)
- **WCAG AA** — verify coral (#FF6B81) and amber (#FFC46B) against Midnight (#0B0E1A) background meet contrast requirements

### Card Rendering (fixed aspect ratio)
- Every card face uses `Modifier.aspectRatio(1.586f)` — ISO/IEC 7810 ID-1; all cards in a list/grid render at **identical box dimensions**
- Rounded corners (20dp) are clipped on the **container Box**, not on the raw image
- Artwork images use `ContentScale.Crop` (not Fit) so the artwork fills the container with no letterboxing
- Missing artwork generates a fallback face: brand gradient + masked number + network logo inside the same aspect-ratio box
- `BankLogoChip` default size: 52dp with logo scaled to 78% of the container

### Build Output & APK Splits
- `release` build: R8 minification + resource shrinking enabled
- APK splits per ABI: `arm64-v8a` (47 MB), `armeabi-v7a` (41 MB), `universal` (58 MB)
- NDK abiFilter set to `arm64-v8a`, `armeabi-v7a` to exclude x86/x86_64/mips/riscv64

### Argon2id Parameters (Current)
Set in `VaultCrypto.kt`. Tune per-device by adjusting `ARGON_M_KIB`:
| Constant | Value | Notes |
|----------|-------|-------|
| `ARGON_M_KIB` | 49152 | 48 MiB memory |
| `ARGON_T` | 3 | 3 iterations |
| `ARGON_P` | 2 | 2 lanes (parallelism) |
| `KEY_LEN` | 32 | 256-bit output |
**Target**: ~400-600ms on Snapdragon 6-series / MediaTek Helio G-series (mid-range 2020+).

### Key Dependencies
```kotlin
// Core security
argon2kt:1.5.0              // Argon2id KDF (libsodium)
lazysodium-android:5.2.1    // libsodium secure memory (mlock/memzero/munlock)
android-database-sqlcipher:4.5.4 // Encrypted SQLite
biometric:1.1.0             // Fingerprint/face auth
security-crypto:1.1.0-alpha06 // EncryptedSharedPreferences (AES-256 at rest)

// QR & export
zxing-core:3.5.3            // Recovery key QR generation
quickie-bundled:1.9.0       // TOTP QR scanning (not for recovery key)
```

### Argon2id On-Device Benchmark
A dedicated screen (`Argon2idBenchmarkScreen.kt`) measures the actual KDF timing on hardware:
- Run from More tab → "Argon2id benchmark"
- Displays current parameters (ARGON_M_KIB, ARGON_T, ARGON_P) from VaultCrypto.kt
- Runs one Argon2id iteration with `System.nanoTime()`, reports elapsed ms
- Color-coded result: Mint (400-600ms), Amber (250-400 or 600-900), Coral (outside safe range)
- Rolling average of last 5 runs
- Tuning: edit `ARGON_M_KIB` in `VaultCrypto.kt`, rebuild, re-run

### Build Credentials (secrets management)
**Never hardcode passwords in `build.gradle.kts`**. The signing config reads credentials with this priority:
1. **Environment variables** (CI/CD): `PSWD_MNGR_KEYSTORE`, `PSWD_MNGR_KEYSTORE_PASS`, `PSWD_MNGR_KEY_ALIAS`, `PSWD_MNGR_KEY_PASS`
2. **Properties file** (local dev): copy `keystore.properties.example` → `keystore.properties`, fill in values
3. **File default**: falls back to `release-keystore.jks` in project root with empty passwords (will fail at signing time — safe default)

Both `keystore.properties` and `*.keystore` are in `.gitignore`.

### Notable Implementation Details
- **AAD on wrapped blobs**: Password-wrapped blob uses `aad="pw".toByteArray()`, recovery-wrapped uses `aad="rec".toByteArray()`. Any code that decrypts these must pass the matching AAD tag.
- **`currentKey()` vs `copyOf()`**: `VaultSession.currentKey()` returns a `ByteArray` copy (copyOf) of the SecureData. The caller **must** wipe this copy when done.
- **`openDb()` flow**: Receives raw key → copies into `SecureData` with sodium_mlock → wipes the raw key → creates SQLCipher `SupportFactory` with another copy.
- **Backup password**: Exports use their own Argon2id salt + derivation. The backup password is completely independent of the vault master password.

### To Ship
1. **Set up signing credentials** — see "Build Credentials" section above. No passwords in build.gradle.kts.
2. **APK signature** — no manual hash needed. See "APK tamper check (TOFU)" above.
3. **Test Argon2id timing** — see "Argon2id On-Device Benchmark" above. Run on target device, adjust `ARGON_M_KIB` in VaultCrypto.kt if outside 400-600ms.
4. Test migration v1→v2 from an existing vault — confirm recovery key dialog appears with retype challenge
5. Test process kill during migration — confirm recovery key dialog reappears on next launch (persistent flag test)
6. Test backup/restore (full + selective) end-to-end on a device
7. Confirm no INTERNET permission in AndroidManifest.xml
