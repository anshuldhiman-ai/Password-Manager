package com.family.pswdmngr.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Platform / app logo engine (offline, vector-drawn) ----------
 *
 * Recognises a login's URL or title ("netflix.com", "Instagram",
 * "facebook.com/login"…) and draws the brand's real mark on the fly.
 * Pure Canvas — no network, no bundled images, nothing to download.
 * Falls back to initials so every entry always gets a genuine badge.
 */

/** Brands whose mark we can draw. Ordered by recognition value. */
private data class Platform(
    val id: String,
    val keywords: List<String>,
    val bg: Color,
    val draw: DrawScope.(Dp) -> Unit,
)

private val PLATFORM_LOGOS: List<Platform> = listOf(
    Platform("netflix", listOf("netflix"), Color(0xFF221F1F)) { d ->
        val s = size.minDimension
        val r = s * 0.30f
        drawArc(
            Color(0xFFB20710), startAngle = -40f, sweepAngle = -100f, useCenter = false,
            topLeft = Offset(s * 0.18f - r, s * 0.24f - r), size = Size(r * 2, r * 2),
            style = Stroke(width = s * 0.11f, cap = StrokeCap.Round),
        )
        drawArc(
            Color(0xFFB20710), startAngle = -140f, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(s * 0.18f - r, s * 0.76f - r), size = Size(r * 2, r * 2),
            style = Stroke(width = s * 0.11f, cap = StrokeCap.Round),
        )
    },
    Platform("instagram", listOf("instagram"), Color(0xFFFFFFFF)) { d ->
        val s = size.minDimension
        val inset = s * 0.10f
        val corner = s * 0.22f
        val stroke = s * 0.08f
        // rounded-square outline (gradient approximated by violet)
        drawRoundRect(
            color = Color(0xFF833AB4), topLeft = Offset(inset, inset),
            size = Size(s - inset * 2, s - inset * 2),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        // lens (camera ring)
        drawCircle(
            color = Color(0xFFC13584), radius = s * 0.17f,
            center = Offset(s / 2, s / 2), style = Stroke(width = stroke),
        )
        // viewfinder dot
        drawCircle(Color(0xFFF77737), s * 0.055f, Offset(s * 0.71f, s * 0.29f))
    },
    Platform("facebook", listOf("facebook", "fb.com", "meta"), Color(0xFF1877F2)) { d ->
        val s = size.minDimension
        val w = s * 0.52f
        val h = s * 0.78f
        val x = (s - w) / 2
        val y = (s - h) / 2 - s * 0.04f
        val path = Path().apply {
            moveTo(x + w * 0.92f, y)                    // top of stem
            lineTo(x + w * 0.92f, y + h * 0.32f)        // down to crossbar
            lineTo(x + w, y + h * 0.32f)                // right tip of crossbar
            lineTo(x + w, y + h * 0.52f)                // down right side of crossbar
            lineTo(x + w * 0.92f, y + h * 0.52f)
            lineTo(x + w * 0.92f, y + h * 0.86f)        // long tail
            lineTo(x + w * 0.70f, y + h * 0.86f)
            lineTo(x + w * 0.70f, y + h * 0.52f)
            lineTo(x + w * 0.48f, y + h * 0.52f)
            lineTo(x + w * 0.48f, y + h * 0.32f)
            lineTo(x + w * 0.70f, y + h * 0.32f)
            lineTo(x + w * 0.70f, y)                    // back up the hook
            close()
        }
        drawPath(path, Color.White, style = Fill)
    },
    Platform("youtube", listOf("youtube", "youtu.be"), Color(0xFFFF0000)) { d ->
        val s = size.minDimension
        val w = s * 0.88f
        val h = s * 0.62f
        val x = (s - w) / 2
        val y = (s - h) / 2
        drawRoundRect(
            color = Color.White, topLeft = Offset(x, y), size = Size(w, h),
            cornerRadius = CornerRadius(h * 0.45f, h * 0.45f),
        )
        // play triangle
        val tri = Path().apply {
            moveTo(x + w * 0.40f, y + h * 0.24f)
            lineTo(x + w * 0.64f, y + h * 0.50f)
            lineTo(x + w * 0.40f, y + h * 0.76f)
            close()
        }
        drawPath(tri, Color(0xFFFF0000), style = Fill)
    },
    Platform("twitter", listOf("twitter", "x.com", "twimg"), Color(0xFF000000)) { d ->
        val s = size.minDimension
        val w = s * 0.62f
        val h = s * 0.50f
        val x = (s - w) / 2
        val y = (s - h) / 2
        // X = two crossing strokes
        val stroke = s * 0.13f
        drawLine(Color.White, Offset(x, y), Offset(x + w, y + h), stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(x + w, y), Offset(x, y + h), stroke, cap = StrokeCap.Round)
    },
    Platform("whatsapp", listOf("whatsapp", "wa.me"), Color(0xFF25D366)) { d ->
        val s = size.minDimension
        drawCircle(Color.White, s * 0.44f, Offset(s / 2, s / 2))
        // handset arc
        val arcR = s * 0.30f
        drawArc(
            Color(0xFF25D366), startAngle = 200f, sweepAngle = -140f, useCenter = false,
            topLeft = Offset(s / 2 - arcR, s / 2 - arcR), size = Size(arcR * 2, arcR * 2),
            style = Stroke(width = s * 0.10f, cap = StrokeCap.Round),
        )
        // speech tail
        drawCircle(Color(0xFF25D366), s * 0.07f, Offset(s * 0.32f, s * 0.74f))
    },
    Platform("discord", listOf("discord"), Color(0xFF5865F2)) { d ->
        val s = size.minDimension
        // gamepad face: two eyes + smile
        drawCircle(Color.White, s * 0.09f, Offset(s * 0.36f, s * 0.46f))
        drawCircle(Color.White, s * 0.09f, Offset(s * 0.64f, s * 0.46f))
        drawArc(
            Color.White, startAngle = 30f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(s * 0.24f, s * 0.30f), size = Size(s * 0.52f, s * 0.52f),
            style = Stroke(width = s * 0.07f, cap = StrokeCap.Round),
        )
    },
    Platform("reddit", listOf("reddit"), Color(0xFFFF4500)) { d ->
        val s = size.minDimension
        // antenna
        drawCircle(Color.White, s * 0.07f, Offset(s * 0.50f, s * 0.22f))
        drawLine(Color.White, Offset(s * 0.50f, s * 0.24f), Offset(s * 0.50f, s * 0.34f), s * 0.05f)
        // head
        drawCircle(Color.White, s * 0.30f, Offset(s / 2, s * 0.52f))
        // eyes
        drawCircle(Color(0xFFFF4500), s * 0.07f, Offset(s * 0.40f, s * 0.48f))
        drawCircle(Color(0xFFFF4500), s * 0.07f, Offset(s * 0.60f, s * 0.48f))
    },
    Platform("linkedin", listOf("linkedin"), Color(0xFF0A66C2)) { d ->
        val s = size.minDimension
        val bar = s * 0.11f
        // "in": two vertical bars + dot + n-arch
        drawRoundRect(
            color = Color.White, topLeft = Offset(s * 0.20f, s * 0.30f),
            size = Size(bar, s * 0.44f), cornerRadius = CornerRadius(bar / 2, bar / 2),
        )
        drawCircle(Color.White, bar * 0.62f, Offset(s * 0.36f, s * 0.30f))
        // n
        drawLine(Color.White, Offset(s * 0.50f, s * 0.74f), Offset(s * 0.50f, s * 0.36f), bar, cap = StrokeCap.Round)
        drawArc(
            Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(s * 0.50f, s * 0.36f), size = Size(s * 0.26f, s * 0.26f),
            style = Stroke(width = bar, cap = StrokeCap.Round),
        )
        drawLine(Color.White, Offset(s * 0.76f, s * 0.36f), Offset(s * 0.76f, s * 0.74f), bar, cap = StrokeCap.Round)
    },
    Platform("spotify", listOf("spotify"), Color(0xFF1DB954)) { d ->
        val s = size.minDimension
        drawCircle(Color.White, s * 0.44f, Offset(s / 2, s / 2))
        // sound waves
        for (i in 0..2) {
            val r = s * (0.32f - i * 0.09f)
            drawArc(
                Color(0xFF1DB954), startAngle = 215f, sweepAngle = 110f, useCenter = false,
                topLeft = Offset(s / 2 - r, s / 2 - r), size = Size(r * 2, r * 2),
                style = Stroke(width = s * 0.06f, cap = StrokeCap.Round),
            )
        }
    },
    Platform("apple", listOf("apple", "icloud"), Color(0xFF000000)) { d ->
        val s = size.minDimension
        // apple body (two lobes)
        val lobeR = s * 0.26f
        drawCircle(Color.White, lobeR, Offset(s * 0.40f, s * 0.58f))
        drawCircle(Color.White, lobeR, Offset(s * 0.60f, s * 0.58f))
        // leaf
        drawOval(
            color = Color.White,
            topLeft = Offset(s * 0.52f, s * 0.20f),
            size = Size(s * 0.14f, s * 0.10f),
        )
    },
    Platform("microsoft", listOf("microsoft", "office.com", "outlook", "live.com", "hotmail"), Color(0xFF00A4EF)) { d ->
        val s = size.minDimension
        val q = s * 0.36f
        val gap = s * 0.04f
        val x0 = (s - q * 2 - gap) / 2
        val y0 = (s - q * 2 - gap) / 2
        drawRect(Color(0xFFF25022), Offset(x0, y0), Size(q, q))
        drawRect(Color(0xFF7FBA00), Offset(x0 + q + gap, y0), Size(q, q))
        drawRect(Color(0xFF00A4EF), Offset(x0, y0 + q + gap), Size(q, q))
        drawRect(Color(0xFFFFB900), Offset(x0 + q + gap, y0 + q + gap), Size(q, q))
    },
    Platform("amazon", listOf("amazon", "aws", "primevideo", "prime"), Color(0xFFFFFFFF)) { d ->
        val s = size.minDimension
        // smile arrow
        drawArc(
            Color(0xFFFF9900), startAngle = 10f, sweepAngle = 160f, useCenter = false,
            topLeft = Offset(s * 0.16f, s * 0.14f), size = Size(s * 0.68f, s * 0.68f),
            style = Stroke(width = s * 0.09f, cap = StrokeCap.Round),
        )
        // arrowhead
        val ah = s * 0.14f
        val tip = Offset(s * 0.86f, s * 0.60f)
        val tri = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - ah, tip.y - ah * 0.9f)
            lineTo(tip.x - ah * 0.3f, tip.y - ah * 0.2f)
            close()
        }
        drawPath(tri, Color(0xFFFF9900), style = Fill)
    },
    Platform("github", listOf("github"), Color(0xFF24292F)) { d ->
        val s = size.minDimension
        drawCircle(Color.White, s * 0.42f, Offset(s / 2, s / 2))
        // cat ears
        val ear = Path().apply {
            moveTo(s * 0.30f, s * 0.34f)
            lineTo(s * 0.38f, s * 0.16f)
            lineTo(s * 0.46f, s * 0.32f)
            close()
            moveTo(s * 0.70f, s * 0.34f)
            lineTo(s * 0.62f, s * 0.16f)
            lineTo(s * 0.54f, s * 0.32f)
            close()
        }
        drawPath(ear, Color(0xFF24292F), style = Fill)
        // eyes
        drawCircle(Color(0xFF24292F), s * 0.06f, Offset(s * 0.40f, s * 0.44f))
        drawCircle(Color(0xFF24292F), s * 0.06f, Offset(s * 0.60f, s * 0.44f))
    },
    Platform("paytm", listOf("paytm"), Color(0xFF00B9F1)) { d ->
        val s = size.minDimension
        drawRoundRect(
            color = Color.White, topLeft = Offset(s * 0.12f, s * 0.30f),
            size = Size(s * 0.76f, s * 0.40f), cornerRadius = CornerRadius(s * 0.10f, s * 0.10f),
        )
        // "paytm" wordmark as rounded bars
        val w = s * 0.14f
        val y = s * 0.44f
        for (i in 0..5) {
            drawRoundRect(
                color = Color(0xFF002E6E),
                topLeft = Offset(s * (0.20f + i * 0.115f), y + if (i == 1 || i == 4) -s * 0.05f else 0f),
                size = Size(w, s * 0.16f),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    },
    Platform("phonepe", listOf("phonepe"), Color(0xFF5F259F)) { d ->
        val s = size.minDimension
        drawCircle(Color.White, s * 0.42f, Offset(s / 2, s / 2))
        // stylised P
        drawLine(Color(0xFF5F259F), Offset(s * 0.40f, s * 0.32f), Offset(s * 0.40f, s * 0.68f), s * 0.09f, cap = StrokeCap.Round)
        drawArc(
            Color(0xFF5F259F), startAngle = 270f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(s * 0.40f, s * 0.32f), size = Size(s * 0.24f, s * 0.24f),
            style = Stroke(width = s * 0.09f, cap = StrokeCap.Round),
        )
    },
    Platform("gpay", listOf("google pay", "gpay", "tez"), Color(0xFFFFFFFF)) { d ->
        val s = size.minDimension
        // four-colour G (Google palette)
        val stroke = s * 0.16f
        val inset = stroke / 2
        val arc = Size(s - stroke, s - stroke)
        fun seg(color: Color, start: Float, sweep: Float) = drawArc(
            color, startAngle = start, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = arc,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        seg(Color(0xFFEA4335), 165f, 125f)
        seg(Color(0xFFFBBC05), 125f, 40f)
        seg(Color(0xFF34A853), 45f, 80f)
        seg(Color(0xFF4285F4), 0f, 45f)
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(s / 2, s / 2 - stroke / 2),
            size = Size(s / 2 - inset / 2, stroke),
        )
    },
    Platform("airtel", listOf("airtel"), Color(0xFFED1C24)) { d ->
        val s = size.minDimension
        // lowercase a: circle + stem
        drawCircle(Color.White, s * 0.26f, Offset(s * 0.42f, s * 0.50f))
        drawLine(Color.White, Offset(s * 0.62f, s * 0.24f), Offset(s * 0.62f, s * 0.74f), s * 0.10f, cap = StrokeCap.Round)
    },
    Platform("jio", listOf("jio", "reliance jio"), Color(0xFF0A2885)) { d ->
        val s = size.minDimension
        drawCircle(Color(0xFF0A2885), s * 0.44f, Offset(s / 2, s / 2))
        drawCircle(Color(0xFFFF6B00), s * 0.30f, Offset(s / 2, s / 2))
    },
    Platform("steam", listOf("steam"), Color(0xFF1B2838)) { d ->
        val s = size.minDimension
        // piston arm + wheel
        drawLine(Color.White, Offset(s * 0.24f, s * 0.70f), Offset(s * 0.56f, s * 0.42f), s * 0.08f, cap = StrokeCap.Round)
        drawCircle(Color.White, s * 0.20f, Offset(s * 0.66f, s * 0.36f))
        drawCircle(Color(0xFF1B2838), s * 0.10f, Offset(s * 0.66f, s * 0.36f))
        drawCircle(Color.White, s * 0.12f, Offset(s * 0.30f, s * 0.72f))
    },
    Platform("gmail", listOf("gmail", "mail.google"), Color(0xFFFFFFFF)) { d ->
        val s = size.minDimension
        // envelope: M-shaped flap over body
        val w = s * 0.80f
        val h = s * 0.56f
        val x = (s - w) / 2
        val y = (s - h) / 2
        val flap = Path().apply {
            moveTo(x, y)
            lineTo(x + w / 2, y + h * 0.55f)
            lineTo(x + w, y)
            lineTo(x + w, y + h * 0.28f)
            lineTo(x + w / 2, y + h * 0.82f)
            lineTo(x, y + h * 0.28f)
            close()
        }
        drawPath(flap, Color(0xFFEA4335), style = Fill)
    },
)

/**
 * Draws the platform's own logo for [hint] (URL, title or username),
 * inside a tinted rounded tile. Returns true when the platform was recognised.
 */
@Composable
fun PlatformLogo(hint: String, size: Dp = 46.dp): Boolean {
    val h = hint.lowercase()
    val p = PLATFORM_LOGOS.firstOrNull { it.keywords.any { k -> k in h } } ?: return false
    val draw = p.draw
    Box(
        Modifier.size(size).clip(CircleShape).background(p.bg),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) { draw(size) }
    }
    return true
}

/** Initial-letter fallback badge for unknown platforms. */
@Composable
fun InitialBadge(name: String, size: Dp = 46.dp, bg: Color = Color(0xFF1C2138)) {
    val initials = name.trim().split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}
