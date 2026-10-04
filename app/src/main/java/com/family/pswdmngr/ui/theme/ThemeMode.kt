package com.family.pswdmngr.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * User's colour-scheme choice: follow the system, or force light/dark.
 *
 * Stored in a plain (unencrypted) SharedPreferences file. This is a cosmetic
 * preference with no secret content, and it must be readable before the vault
 * is unlocked so the unlock screen itself renders in the right theme.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Holds the active [ThemeMode] as observable state rather than reading
 * SharedPreferences on every recomposition — `SharedPreferences` is not
 * observable, so a change made in Settings would otherwise never repaint the
 * tree. [load] is called once from `MainActivity.onCreate` *before* the first
 * composition, so the very first frame already uses the saved mode (no
 * dark-to-light flash when the user has overridden a dark system theme).
 */
object ThemePrefs {
    private const val FILE = "ui_prefs"
    private const val KEY_MODE = "theme_mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(ctx: Context) {
        val raw = prefs(ctx).getString(KEY_MODE, null)
        mode = raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    fun set(ctx: Context, m: ThemeMode) {
        mode = m
        prefs(ctx).edit().putString(KEY_MODE, m.name).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}