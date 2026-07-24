# 🔐 PSWD MNGR — Offline Password Vault

> **A secure, air-gapped password manager for Android** — zero internet, zero cloud, zero compromise.
> Package: `com.family.pswdmngr`

[![Platform](https://img.shields.io/badge/platform-Android-blue)]()
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.22-purple)]()
[![Compose](https://img.shields.io/badge/Compose-2024.02.00-teal)]()
[![Min SDK](https://img.shields.io/badge/minSdk-28-green)]()

---

## 📋 Feature Overview

| Section | Features |
|---------|----------|
| 🔑 **Logins** | Usernames, passwords, URLs, TOTP 2FA secrets, autofill integration |
| 💳 **Cards** | Debit/credit + CSD cards, real bank artwork (100+ card faces), camera scanner |
| 🏦 **Banks** | Account numbers, IFSC, MICR, CIF, netbanking passwords, UPI PIN |
| 📄 **Documents** | Aadhaar, PAN, Passport, Driving Licence, Insurance — encrypted photo/PDF attachments |
| 📝 **Notes** | Encrypted secret notes with color-coded cards |
| ✅ **Tasks** | To-do lists with due dates, subtasks, stars, progress tracking |
| 🔢 **Generator** | Strong password / passphrase generator with strength meter |
| ♻️ **Recycle Bin** | 30-day trash with restore — no more accidental permanent deletion |
| 🩺 **Password Health** | Offline scan for reused, weak, or old passwords |

---

## 🛡 Security — Phase 1 Hardened

### Core Encryption

```
Master Password / Recovery Key
        ↓
    Argon2id KDF  ← 48 MiB memory, 3 iterations, 2 lanes (~400-600ms on mid-range)
        ↓
    256-bit Vault Master Key (random, dual-wrapped)
        ↓
    AES-256-GCM → SQLCipher Encrypted Database
```

| Layer | Technology |
|-------|-----------|
| **Key Derivation** | Argon2id (libsodium via argon2kt) — 48 MiB (`ARGON_M_KIB=49152`), 3 iterations, 2 lanes, ~400-600ms on mid-range |
| **Vault Encryption** | SQLCipher — AES-256-CBC with per-page IV + HMAC integrity |
| **Key Storage** | Dual-wrap: password-derived key + recovery-derived key independently wrap vault master key |
| **Secure Memory** | libsodium `sodium_mlock` / `sodium_memzero` / `sodium_munlock` — prevents swap exposure, guarantees zeroing |
| **Biometric Key** | Android Keystore — non-extractable hardware-backed key, invalidated on fingerprint enrollment change |
| **At-Rest Metadata** | All SharedPreferences stores (vault meta, lockout, biometric wrap) use `EncryptedSharedPreferences` — AES-256 encrypted XML backed by Android Keystore |

### Security Features

| Feature | Detail |
|---------|--------|
| **No Internet** | `INTERNET` permission deliberately omitted — data never leaves the device |
| **Auto-Lock** | Vault locks 30s after app goes to background; master key zeroed with libsodium |
| **Clipboard Guard** | Copied secrets auto-clear after 30s; marked as `IS_SENSITIVE` to hide from clipboard preview |
| **Anti-Screenshot** | `FLAG_SECURE` blocks screenshots and screen recording |
| **Biometric Grace** | Fingerprint/face re-unlock within configurable window (15 min — 12 hours); past window → master password required |
| **Root Detection** | Detects `su` binaries, Magisk, SuperSU, KingRoot, test-keys build; persistent warning banner + disables biometric unlock |
| **APK Tamper Check** | Runtime SHA-256 verification of signing certificate; warns on mismatch |
| **Exponential Backoff** | 3 fails → 30s lockout, 4→60s, 5→120s, 6→240s, 7+→480s cap; persists across app restarts |
| **Auto-Wipe** | Optional setting: wipe entire vault after 10 consecutive failed unlock attempts |
| **Reveal Lock** | Sensitive fields (CVV, ATM PIN, UPI PIN, Aadhaar number) require fresh biometric auth — even within an unlocked session |
| **ProGuard / R8** | Full obfuscation (repackage, allowaccessmodification, overloadaggressively); logging stripped in release |
| **Backup Prevention** | `android:allowBackup="false"` and `android:fullBackupContent="false"` — `adb backup` cannot exfiltrate the DB |

### Recovery Mechanism — Phase 2

Every vault has a **Recovery Key** — a 24-character base32 key (no ambiguous 0/O/1/I/L) with ~120 bits of entropy:

- Generated on first vault setup (mandatory save screen with typed confirmation challenge)
- The vault master key is wrapped **twice independently**: once with a key derived from the master password, once with a key derived from the Recovery Key. Either alone can unlock the same vault.
- "Forgot master password?" flow: enter Recovery Key → set new master password → old password-wrapped blob discarded
- View/export Recovery Key in Settings (requires biometric or current password)
- Export as QR code (ZXing-generated, shareable PNG) or printable text card
- **No Shamir Secret Sharing, no trusted-contact recovery** — the Recovery Key is the sole backup

---

## 🎨 UI/UX — Phase 3 Redesign

### Navigation
- **Bottom Navigation Bar** — 5 tabs: Vault (dashboard), Cards, Notes (notes + tasks), Search (unified), More
- **Floating "+" Action Button** — available from every tab; opens a 3×2 grid bottom sheet for quick-add of any entry type
- **Dashboard home** — greeting header, favorites horizontal row, pinned search bar, category tiles in 3-column grid
- **Empty states** — every list has an icon + message + CTA instead of a blank screen

### Unified Search
- Single search bar queries all 6 entity types simultaneously (logins, cards, banks, documents, notes, tasks)
- Results show type badges with brand colors; each result is tappable to the detail screen

### Visual Design
- Dark navy (#0B0E1A) base with violet (#7C5CFF), cyan (#4DD0E1), mint (#34D399), coral (#FF6B81), amber (#FFC46B) accents
- Flat surfaces with glassmorphism reserved for emphasis (unlock screen, top bar)
- Press-scale animation on cards and tiles
- Haptic feedback on copy/reveal actions and FAB press
- TalkBack accessibility labels on icon-only buttons
- Respects system font-scaling settings

### Card Image Handling
- Card artwork containers use fixed 1.586:1 aspect ratio (`Modifier.aspectRatio(1.586f)`)
- Rounded corners (16dp) on the container Box, not the raw image
- Missing artwork produces a generated fallback card face (brand gradient + masked number + network logo)
- All cards in a list/grid render at identical box dimensions

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────┐
│              Jetpack Compose UI                      │
│  MainScreen (bottom nav)  │  Detail Screens          │
├─────────────────────────────────────────────────────┤
│         VaultSession (singleton, session state)       │
│     openDb() / lock() / currentKey() / changePassword │
├─────────────────────────────────────────────────────┤
│           Room Database (SQLCipher v4)                │
│  9 DAOs: VaultDao, CardDao, BankDao, DocDao,         │
│          AttachmentDao, NoteDao, TaskDao, TrashDao    │
│  Migrations: 1→2, 2→3, 3→4                           │
├─────────────────────────────────────────────────────┤
│              Crypto Layer                              │
│  Argon2id KDF │ AES-256-GCM │ libsodium secure memory│
│  KeystoreWrapper (biometric) │ TOTP │ PasswordGenerator│
├─────────────────────────────────────────────────────┤
│           SQLCipher Encrypted Storage                  │
│  vault.db (Room) │ attachments/ (encrypted blobs)     │
└─────────────────────────────────────────────────────┘
```

---

## 💻 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 1.9.22 |
| **UI** | Jetpack Compose (BOM 2024.02.00), Material3 |
| **Navigation** | Navigation Compose 2.7.7 |
| **Database** | Room 2.6.1 + SQLCipher 4.5.4 |
| **KDF** | Argon2id (argon2kt 1.5.0, libsodium) |
| **Secure Memory** | Lazysodium Android 5.2.1 (libsodium JNI) |
| **Biometrics** | AndroidX Biometric 1.1.0 |
| **Camera** | CameraX 1.3.1 (core, camera2, lifecycle, view) |
| **OCR** | ML Kit Text Recognition 16.0.0 |
| **QR Scanner** | Quickie 1.9.0 (TOTP setup) |
| **QR Generation** | ZXing core 3.5.3 (recovery key export) |
| **Autofill** | Android Autofill Framework 1.1.0 |
| **Build** | Gradle KTS, AGP 8.2.2, KSP, compileSdk 34, minSdk 28 |

---

## 📁 Project Structure

```
app/src/main/java/com/family/pswdmngr/
├── MainActivity.kt              # NavHost + FLAG_SECURE + all routes
├── VaultApp.kt                  # Application class + auto-lock timer + clipboard guard
├── autofill/
│   └── VaultAutofillService.kt  # Android Autofill framework integration
├── crypto/
│   ├── ApkSignatureVerifier.kt  # Runtime APK tamper detection
│   ├── KeystoreWrapper.kt       # Android Keystore for biometric unlock
│   ├── PasswordGenerator.kt     # Strong password + passphrase generator
│   ├── RecoveryKeyGenerator.kt  # 24-char base32 recovery key generation
│   ├── RootDetector.kt          # Root/jailbreak detection (su, Magisk, etc.)
│   ├── SecureMemory.kt          # libsodium mlock/memzero wrapper
│   ├── Totp.kt                  # TOTP 2FA (RFC 6238)
│   └── VaultCrypto.kt           # Argon2id KDF + AES-256-GCM + wipe utilities
├── data/
│   ├── AttachmentStore.kt       # Encrypted file storage for photos/PDFs
│   ├── BackupManager.kt         # Full + selective encrypted backup/restore
│   ├── LockoutTracker.kt        # Exponential backoff + wipe-after-10
│   ├── Models.kt                # All Room entities + DAOs (8 entity types)
│   ├── RecycleBinManager.kt     # Trash management + restore + auto-purge
│   ├── VaultDatabase.kt         # Room DB with migrations (v1→v4)
│   └── VaultSession.kt          # Dual-wrap key architecture + session management
└── ui/
    ├── theme/                   # Colors, Typography, Theme composable
    ├── screens/
    │   ├── Components.kt        # GlassCard, GradientButton, VaultTextField, etc.
    │   ├── MainScreen.kt        # Bottom nav + dashboard + all 5 tabs + FAB
    │   ├── ForgotPasswordScreen.kt  # Recovery key → new password flow
    │   ├── OnboardingScreen.kt  # First-time master password setup
    │   ├── UnlockScreen.kt      # Master password entry + root/tamper warnings
    │   ├── RecoveryKeyScreen.kt # Mandatory recovery key save + export
    │   ├── RecycleBinScreen.kt  # Trash list with restore + purge
    │   ├── PasswordHealthScreen.kt # Offline health analysis
    │   ├── EntryScreen.kt       # Login detail view
    │   ├── EditEntryScreen.kt   # Login editor
    │   ├── GeneratorScreen.kt   # Password generator
    │   ├── SettingsScreen.kt    # All settings + recovery key view + selective backup
    │   ├── VaultScreen.kt       # Legacy vault (replaced by MainScreen)
    │   └── RevealLock.kt        # Biometric-gated reveal for sensitive fields
    ├── cards/                   # Card list, detail, editor, scanner, catalog, visuals
    ├── banks/                   # Bank account list, detail, editor
    ├── docs/                    # Document list, detail, editor, attachment viewer
    ├── notes/                   # Secret notes screens
    └── tasks/                   # Task screens with lists
```

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Hedgehog (2023.1.1+) or later
- JDK 17
- Android SDK 34

### Build
```bash
# Debug
./gradlew assembleDebug

# Release (requires release-keystore.jks in project root)
./gradlew assembleRelease
```

### Before Shipping
1. Set up signing credentials — copy `keystore.properties.example` → `keystore.properties` and fill in your release keystore path and passwords. Or set env vars `PSWD_MNGR_KEYSTORE`, `PSWD_MNGR_KEYSTORE_PASS`, `PSWD_MNGR_KEY_ALIAS`, `PSWD_MNGR_KEY_PASS` (CI/CD).
2. APK tamper check needs no manual hash — `ApkSignatureVerifier` uses a trust-on-first-use model (first legitimate install records the cert hash automatically).
3. Test Argon2id timing on target device via More tab → "Argon2id benchmark". Adjust `ARGON_M_KIB` in `VaultCrypto.kt` if outside 400-600ms.

---

## 🔐 Security Checklist for Production

- [ ] **Release keystore** created and `signingConfigs` updated
- [ ] `EXPECTED_SIG_HASH` set to release cert SHA-256 Base64
- [ ] **No `INTERNET` permission** — verify manifest
- [ ] **ProGuard/R8** enabled for release build (already in config)
- [ ] **Clipboard auto-clear** tested (30s timer per copy)
- [ ] **Auto-lock** tested (30s after background)
- [ ] **Recovery Key** flow verified end-to-end
- [ ] **Recycle bin** auto-purge tested
- [ ] **Backup/restore** full + selective flows tested
- [ ] **Biometric grace** policy tested across boot, fingerprint enrollment change

---

## 📜 License

This project is licensed under All Rights Reserved — see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Anshul Dhiman**

---

*Built with ❤️ for security-conscious families. No telemetry, no ads, no cloud, no compromise.*
