package com.family.pswdmngr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette tokens are exposed as composable getters so a single token name (e.g.
 * [TextPrimary]) resolves per theme. They deliberately are NOT `val`s holding one
 * fixed colour — that is what previously hardcoded the app to dark mode.
 *
 * Because they are @Composable, they can only be read from composable scope.
 * Colour-mapping helpers that need to run outside it (e.g. a `remember`ed list
 * built in a lambda) must take the colours as parameters instead.
 */
data class AppPalette(
    val midnight: Color,
    val surface1: Color,
    val surface2: Color,
    val surfaceGlass: Color,
    val surfaceGlassBright: Color,
    val stroke: Color,
    val violet: Color,
    val violetDeep: Color,
    val cyan: Color,
    val mint: Color,
    val coral: Color,
    val amber: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Foreground that sits on top of a filled accent (buttons, FABs). */
    val onAccent: Color,
    val glowCyan: Color,
    val glowViolet: Color,
    val isDark: Boolean,
)

// ── Dark (the original Midnight palette) ────────────────────────────────

private val DarkPalette = AppPalette(
    midnight = Color(0xFF0B0E1A),
    surface1 = Color(0xFF141829),
    surface2 = Color(0xFF1C2138),
    surfaceGlass = Color(0x14FFFFFF),
    surfaceGlassBright = Color(0x1FFFFFFF),
    stroke = Color(0x1AFFFFFF),
    // Lifted from #7C5CFF, which sat at only 4.42:1 on the page and 4.05:1 on a card —
    // under AA wherever violet is used as an icon or label tint. #8F79FF clears 4.5:1 on
    // all three dark surfaces (worst 4.79:1 on Surface2) and still yields a 5.81:1 navy
    // glyph where it is used as a filled container.
    violet = Color(0xFF8F79FF),
    // Also lifted from #5B3DF5: a navy glyph on it was 3.14:1, so the first screen to
    // use it as a filled container would have shipped an unreadable button. #8168FF
    // keeps it visibly deeper than [violet] while giving a 4.91:1 glyph.
    violetDeep = Color(0xFF8168FF),
    cyan = Color(0xFF4DD0E1),
    mint = Color(0xFF34D399),
    coral = Color(0xFFFF6B81),
    amber = Color(0xFFFFC46B),
    textPrimary = Color(0xFFF2F4FF),
    textSecondary = Color(0xFF8A91B4),
    onAccent = Color(0xFF0B0E1A),
    glowCyan = Color(0x334DD0E1),
    glowViolet = Color(0x338F79FF),
    isDark = true,
)

// ── Light ───────────────────────────────────────────────────────────────
// Accents are darkened from the dark-theme values: #4DD0E1 cyan on a white
// surface is roughly 2:1 contrast, well under WCAG AA. These shades clear 4.5:1
// against [LightPalette.midnight] (the page background) while staying the same hue.

private val LightPalette = AppPalette(
    midnight = Color(0xFFF5F6FC),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEDEFF9),
    surfaceGlass = Color(0x0A000000),
    surfaceGlassBright = Color(0x14000000),
    stroke = Color(0x1A0B0E1A),
    violet = Color(0xFF5B3DF5),
    violetDeep = Color(0xFF4026D6),
    cyan = Color(0xFF0E7C8C),
    mint = Color(0xFF0F7A57),
    coral = Color(0xFFC2334A),
    amber = Color(0xFF8A6212),
    textPrimary = Color(0xFF14172B),
    textSecondary = Color(0xFF5A6180),
    onAccent = Color(0xFFFFFFFF),
    glowCyan = Color(0x2E0E7C8C),
    glowViolet = Color(0x2E5B3DF5),
    isDark = false,
)

private val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Midnight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.midnight
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface1
val Surface2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface2
val SurfaceGlass: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceGlass
val SurfaceGlassBright: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceGlassBright
val Stroke: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.stroke
val Violet: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.violet
val VioletDeep: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.violetDeep
val Cyan: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cyan
val Mint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.mint
val Coral: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.coral
val Amber: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.amber
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
val GlowCyan: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.glowCyan
val GlowViolet: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.glowViolet
val isDarkTheme: Boolean @Composable @ReadOnlyComposable get() = LocalPalette.current.isDark

val AccentGradient: Brush
    @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(Cyan, Mint))

val CardGradient: Brush
    @Composable @ReadOnlyComposable get() = Brush.linearGradient(listOf(Surface2, Surface1))

val HeroGradient: Brush
    @Composable @ReadOnlyComposable get() =
        Brush.verticalGradient(listOf(if (isDarkTheme) Color(0xFF0F1528) else Color(0xFFE7EAFA), Midnight))

private fun schemeFor(p: AppPalette) = if (p.isDark) {
    darkColorScheme(
        primary = p.cyan,
        onPrimary = p.onAccent,
        secondary = p.mint,
        background = p.midnight,
        onBackground = p.textPrimary,
        surface = p.surface1,
        onSurface = p.textPrimary,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.textSecondary,
        error = p.coral,
    )
} else {
    lightColorScheme(
        primary = p.cyan,
        onPrimary = p.onAccent,
        secondary = p.mint,
        background = p.midnight,
        onBackground = p.textPrimary,
        surface = p.surface1,
        onSurface = p.textPrimary,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.textSecondary,
        error = p.coral,
    )
}

@Composable
fun PswdMngrTheme(
    mode: ThemeMode = ThemePrefs.mode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = AppTypography,
            content = content,
        )
    }
}
