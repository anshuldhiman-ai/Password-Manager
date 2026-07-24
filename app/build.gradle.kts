plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.family.pswdmngr"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.family.pswdmngr"
        minSdk = 28 // Android 9
        targetSdk = 34
        versionCode = 6
        versionName = "5.0.0"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Signing credentials — NEVER hardcode passwords directly.
    // Priority: env vars > keystore.properties file (read with Kotlin stdlib).
    signingConfigs {
        val envStore = System.getenv("PSWD_MNGR_KEYSTORE")
        val envStorePass = System.getenv("PSWD_MNGR_KEYSTORE_PASS")
        val envAlias = System.getenv("PSWD_MNGR_KEY_ALIAS")
        val envKeyPass = System.getenv("PSWD_MNGR_KEY_PASS")

        // Read properties file with pure Kotlin stdlib (no Java stdlib dependency)
        val props = run {
            val f = rootProject.file("keystore.properties")
            if (!f.exists()) null
            else try {
                f.readLines().mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) null
                    else {
                        val eq = trimmed.indexOf('=')
                        if (eq < 0) null else trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1).trim()
                    }
                }.toMap()
            } catch (_: Exception) { null }
        }

        create("release") {
            storeFile = rootProject.file(
                envStore ?: props?.get("storeFile") ?: "release-keystore.jks"
            )
            storePassword = envStorePass ?: props?.get("storePassword") ?: ""
            keyAlias = envAlias ?: props?.get("keyAlias") ?: ""
            keyPassword = envKeyPass ?: props?.get("keyPassword") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            // Only package the architectures likely in use — drops x86/x86_64 (~20 MB)
            include("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Vault storage: Room + SQLCipher
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Crypto: Argon2id KDF (libsodium binding)
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.5.0")

    // Secure memory via direct ByteBuffer (native memory, no JNI dependency).
    // If adding lazysodium back via jitpack.io, add here.

    // EncryptedSharedPreferences — defense-in-depth for vault metadata on disk
    implementation("androidx.security:security-crypto:1.0.0")

    // Biometric unlock
    implementation("androidx.biometric:biometric:1.1.0")

    // TOTP QR scanning
    implementation("io.github.g00fy2.quickie:quickie-bundled:1.9.0")

    // QR code generation for recovery key export
    implementation("com.google.zxing:core:3.5.3")

    // Autofill inline presentations
    implementation("androidx.autofill:autofill:1.1.0")

    // Card scanner: CameraX + ML Kit text recognition (on-device, no internet)
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.mlkit:text-recognition:16.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
