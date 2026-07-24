package com.family.pswdmngr

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.family.pswdmngr.data.VaultSession
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class VaultApp : Application(), DefaultLifecycleObserver {

    companion object {
        /** Auto-lock delay once app leaves foreground. */
        const val LOCK_DELAY_MS = 30_000L
        const val CLIPBOARD_CLEAR_MS = 30_000L
        @Volatile var onLocked: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable {
        VaultSession.lock()
        onLocked?.invoke()
    }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Global crash handler — writes stack trace to file so we can debug
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashLog = File(filesDir, "crash_log.txt")
                FileWriter(crashLog).use { w ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    w.write("=== CRASH at $date ===\n")
                    w.write("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})\n")
                    w.write("Version: ${Build.VERSION.RELEASE}\n\n")
                    w.write("${throwable.javaClass.name}: ${throwable.message}\n\n")
                    throwable.stackTrace.forEach { w.write("  at $it\n") }
                    throwable.cause?.let { cause ->
                        w.write("\nCaused by: ${cause.javaClass.name}: ${cause.message}\n")
                        cause.stackTrace.forEach { w.write("  at $it\n") }
                    }
                }
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // App went to background — start lock countdown
        if (VaultSession.isUnlocked) handler.postDelayed(lockRunnable, LOCK_DELAY_MS)
    }

    override fun onStart(owner: LifecycleOwner) {
        handler.removeCallbacks(lockRunnable)
    }

    /** Copy a secret; auto-clears after CLIPBOARD_CLEAR_MS and hides from clipboard preview. */
    fun copySecret(label: String, value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= 24) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
        handler.postDelayed({
            val cur = cm.primaryClip
            if (cur != null && cur.itemCount > 0 && cur.getItemAt(0).text?.toString() == value) {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, CLIPBOARD_CLEAR_MS)
    }
}
