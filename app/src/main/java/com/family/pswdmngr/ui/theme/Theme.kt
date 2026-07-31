package com.family.pswdmngr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Reference UI: deep navy base, cyan/teal primary accents
val Midnight = Color(0xFF0B0E1A)
val Surface1 = Color(0xFF141829)
val Surface2 = Color(0xFF1C2138)
val SurfaceGlass = Color(0x14FFFFFF)
val Violet = Color(0xFF7C5CFF)
val VioletDeep = Color(0xFF5B3DF5)
val Cyan = Color(0xFF4DD0E1)
val Mint = Color(0xFF34D399)
val Coral = Color(0xFFFF6B81)
val Amber = Color(0xFFFFC46B)
val TextPrimary = Color(0xFFF2F4FF)
val TextSecondary = Color(0xFF8A91B4)

val AccentGradient = Brush.linearGradient(listOf(Cyan, Mint))
val CardGradient = Brush.linearGradient(listOf(Surface2, Surface1))
val HeroGradient = Brush.verticalGradient(listOf(Color(0xFF0F1528), Midnight))

// Surface + hero tokens
val Stroke = Color(0x1AFFFFFF)
val SurfaceGlassBright = Color(0x1FFFFFFF)
val GlowCyan = Color(0x334DD0E1)
val GlowViolet = Color(0x337C5CFF)

private val DarkScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Midnight,
    secondary = Mint,
    background = Midnight,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    error = Coral,
)

@Composable
fun PswdMngrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content,
    )
}
