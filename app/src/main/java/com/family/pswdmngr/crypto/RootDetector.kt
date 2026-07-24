package com.family.pswdmngr.crypto

import android.content.Context
import android.content.pm.PackageManager

/**
 * Root/jailbreak detection.
 * Does NOT block the app — it surfaces a persistent warning banner and
 * forces password-only unlock (disables biometric) on rooted devices.
 */
object RootDetector {

    /** Common paths where the `su` binary may be installed on a rooted device. */
    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/system/xbin/busybox",
        "/system/bin/busybox",
    )

    /** Root-management package names. */
    private val ROOT_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "com.topjohnwu.magisk.core",
        "io.magisk.manager",
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.zachspong.tempsu",
        "com.kingroot.master",
        "com.kingo.root",
        "com.superuser.xmod",
        "com.keramidas.TitaniumBackup",
    )

    private var _isRooted: Boolean? = null
    private var _reason: String = ""

    /** True if the device shows signs of root/jailbreak. Result is cached. */
    fun isRooted(ctx: Context): Boolean {
        _isRooted?.let { return it }

        val checks = mutableListOf<String>()

        // 1. Check for su binaries
        for (path in SU_PATHS) {
            if (java.io.File(path).exists()) {
                checks.add("su binary: $path")
            }
        }

        // 2. Check for root management apps
        val pm = ctx.packageManager
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                checks.add("root app: $pkg")
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        // 3. Check for test-keys build
        val tags = android.os.Build.TAGS
        if (tags != null && tags.contains("test-keys")) {
            checks.add("build with test-keys")
        }

        // 4. Check if we can run su (dangerous, might trigger superuser prompt)
        // Skip this check — it can cause unwanted popups

        _isRooted = checks.isNotEmpty()
        _reason = checks.joinToString(", ")
        return _isRooted!!
    }

    /** Human-readable reason describing what root indicators were found. */
    fun reason(): String = _reason

    fun resetCache() { _isRooted = null; _reason = "" }
}
