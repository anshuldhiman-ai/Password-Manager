# ================================================================
# PSWD MNGR — ProGuard / R8 obfuscation rules
# ================================================================

## SQLCipher (JNI bridge — must stay)
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

## Argon2 KDF (JNI binding — must stay)
-keep class com.lambdapioneer.argon2kt.** { *; }

## Libsodium / Lazysodium (JNI + JNA — uncomment if lazysodium is added back)
# -keep class com.goterl.lazysodium.** { *; }
# -keep class com.sun.jna.** { *; }
# -dontwarn com.goterl.lazysodium.**
# -dontwarn com.sun.jna.**

## Room entities (accessed via reflection)
-keep class com.family.pswdmngr.data.** { *; }

## Keep entry points (Activity, Service, Application, BroadcastReceiver)
-keep class com.family.pswdmngr.MainActivity { *; }
-keep class com.family.pswdmngr.VaultApp { *; }
-keep class com.family.pswdmngr.autofill.VaultAutofillService { *; }

## Keep custom views and composables used from XML or navigation
-keep class com.family.pswdmngr.ui.** { *; }

## Jetpack Compose — needed for runtime metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.** { *; }

## Never obfuscate enum values (used as type discriminators)
-keepclassmembers enum * { *; }

## Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

## Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

## Obfuscate everything else aggressively
-optimizationpasses 5
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-useuniqueclassmembernames
