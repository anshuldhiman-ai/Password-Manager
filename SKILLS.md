# 🛠 Skills & Technologies Used

This document catalogs the technologies, libraries, and skills demonstrated in the PSWD MNGR project.

---

## 🎯 Core Skills

### Android Development
| Skill | Application |
|-------|------------|
| **Jetpack Compose** | 100% declarative UI — no XML layouts beyond splash screen |
| **Material3 Design** | Modern Material You components: DatePicker, TimePicker, Bottom Sheets, Navigation |
| **Navigation Compose** | Type-safe navigation with argument passing and SavedStateHandle |
| **Room Database** | 6 entities, 7 DAOs, type converters, schema migrations |
| **CameraX** | Camera preview, image analysis for ML Kit OCR |
| **Lifecycle Management** | ProcessLifecycleObserver, auto-lock, StateFlow integration |

### Cryptography & Security
| Skill | Application |
|-------|------------|
| **Argon2id KDF** | Memory-hard key derivation resistant to GPU/ASIC attacks |
| **AES-256-GCM** | Authenticated encryption for all vault data |
| **Android Keystore** | Biometric key unwrapping for fingerprint/face unlock |
| **SQLCipher** | Transparent AES-256 encrypted SQLite at rest |
| **TOTP (RFC 6238)** | Time-based one-time password generation for 2FA |
| **Secure Clipboard** | Auto-clearing clipboard with sensitive-data flag |
| **FLAG_SECURE** | Screenshot/screen-recording protection |

### Kotlin & Coroutines
| Skill | Application |
|-------|------------|
| **Kotlin Coroutines** | async DB operations, lifecycle-aware scopes |
| **StateFlow / Flow** | Reactive UI updates from Room queries |
| **Compose State** | `remember`, `mutableStateOf`, `collectAsState` |
| **LaunchedEffect** | Side-effect management for navigation, camera, ML Kit |

### UI/UX Design
| Skill | Application |
|-------|------------|
| **Dark Theme Design** | Consistent dark palette with electric accents |
| **Glassmorphism** | Frosted glass card components with hairline borders |
| **Animation** | Press-scale, fade, slide transitions, animated list items |
| **Custom Canvas Drawing** | Bank logos, network marks, EMV chips, Google "G" |
| **Typography System** | Custom Sora font family with hierarchy |
| **Responsive Layout** | LazyColumn, LazyVerticalStaggeredGrid, aspect-ratio cards |

---

## 📚 Libraries & Tools

### UI & Compose
| Library | Purpose |
|---------|---------|
| `compose-bom:2024.02.00` | Compose Bill of Materials |
| `material3` | Material3 design system |
| `material-icons-extended` | Full Material icon set |
| `activity-compose:1.8.2` | Activity Compose integration |
| `navigation-compose:2.7.7` | Screen navigation |

### Data Storage
| Library | Purpose |
|---------|---------|
| `room-runtime:2.6.1` | SQLite ORM |
| `room-ktx:2.6.1` | Coroutines integration |
| `room-compiler:2.6.1` | Annotation processor (KSP) |

### Security & Crypto
| Library | Purpose |
|---------|---------|
| `android-database-sqlcipher:4.5.4` | Encrypted SQLite |
| `argon2kt:1.5.0` | Argon2id key derivation |
| `biometric:1.1.0` | Biometric authentication |

### Camera & Vision
| Library | Purpose |
|---------|---------|
| `camera-core:1.3.1` | CameraX core |
| `camera-camera2:1.3.1` | Camera2 backend |
| `camera-lifecycle:1.3.1` | Lifecycle-aware camera |
| `camera-view:1.3.1` | PreviewView |
| `play-services-mlkit-text-recognition:18.0.0` | OCR text recognition |

### Utilities
| Library | Purpose |
|---------|---------|
| `quickie-bundled:1.9.0` | QR code scanner (TOTP setup) |
| `autofill:1.1.0` | Android Autofill framework integration |
| `fragment-ktx:1.6.2` | Fragment extensions |

### Build Tools
| Tool | Purpose |
|------|---------|
| **Gradle KTS** | Kotlin DSL build scripts |
| **KSP** | Kotlin Symbol Processing (Room) |
| **R8** | ProGuard minification for release builds |
| **compileSdk 34** | Android 14 target |
| **minSdk 28** | Android 9 minimum |

---

## 🧠 Concepts Implemented

- ✅ **Air-gapped security model** — no internet permission
- ✅ **Zero-knowledge architecture** — all encryption happens on-device
- ✅ **Memory-safe key handling** — keys zeroed on lock
- ✅ **Biometric grace policy** — time-limited biometric re-authentication
- ✅ **Clipboard hygiene** — auto-clear sensitive data
- ✅ **Room DB migrations** — schema evolution without data loss
- ✅ **CameraX + ML Kit pipeline** — real-time OCR card scanning
- ✅ **IIN-based payment network detection** — Visa, MC, RuPay, Amex, Diners
- ✅ **TOTP 2FA** — RFC 6238 compatible code generation
- ✅ **Glassmorphic UI** — modern design system from scratch
- ✅ **Custom Canvas graphics** — bank logos, network marks
- ✅ **Multi-format attachments** — encrypted image/PDF storage
