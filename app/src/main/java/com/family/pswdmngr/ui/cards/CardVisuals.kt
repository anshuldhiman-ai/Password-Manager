package com.family.pswdmngr.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.pswdmngr.data.CardEntry
import com.family.pswdmngr.data.CardType
import com.family.pswdmngr.ui.theme.DisplayFamily

/* ---------- Network detection ---------- */

enum class CardNetwork(val display: String) {
    RUPAY("RuPay"), VISA("Visa"), MASTERCARD("Mastercard"),
    AMEX("American Express"), DINERS("Diners Club"), MAESTRO("Maestro"),
    UNKNOWN("Card");

    companion object {
        val selectable = listOf(RUPAY, VISA, MASTERCARD, AMEX, DINERS, MAESTRO)

        /**
         * IIN-range detection. RuPay is checked before Discover-style ranges since
         * Indian issuers share the 60/65/81/82 space.
         */
        fun detect(number: String): CardNetwork {
            val d = number.filter { it.isDigit() }
            if (d.length < 2) return UNKNOWN
            val p2 = d.take(2).toIntOrNull() ?: return UNKNOWN
            val p3 = d.take(3).toIntOrNull() ?: p2 * 10
            val p4 = d.take(4).toIntOrNull() ?: p2 * 100
            val p6 = d.take(6).toIntOrNull() ?: p2 * 10000
            return when {
                p2 == 34 || p2 == 37 -> AMEX
                p2 == 36 || p2 == 38 || (p3 in 300..305) -> DINERS
                p2 == 60 || p2 == 65 || p2 == 81 || p2 == 82 || p3 == 508 ||
                    (p6 in 353800..353899) -> RUPAY // 60/65/81/82/508 + JCB co-brand 3538
                d.startsWith("4") -> VISA
                p2 in 51..55 || (p4 in 2221..2720) -> MASTERCARD
                p4 == 5018 || p4 == 5020 || p4 == 5038 || p4 == 6304 -> MAESTRO
                else -> UNKNOWN
            }
        }

        fun resolve(card: CardEntry): CardNetwork {
            if (card.network == "AUTO") {
                val det = detect(card.number)
                if (det != UNKNOWN) return det
                // fall back to the catalog product's native network
                CardCatalog.byId(card.productId)?.let { p ->
                    entries.firstOrNull { it.name == p.networkHint }?.let { return it }
                }
                return UNKNOWN
            }
            return entries.firstOrNull { it.name == card.network } ?: UNKNOWN
        }
    }
}

/* ---------- Bank brand palettes (fallback when no catalog product chosen) ---------- */

data class BankBrand(val top: Color, val bottom: Color, val accent: Color)

fun bankBrand(bankName: String): BankBrand {
    val n = bankName.lowercase()
    return when {
        "sbi" in n || "state bank" in n -> BankBrand(Color(0xFF15619E), Color(0xFF07253F), Color(0xFF33C1F0))
        "icici" in n -> BankBrand(Color(0xFF8A3324), Color(0xFF3A0E08), Color(0xFFF7931E))
        "hdfc" in n -> BankBrand(Color(0xFF0B3D91), Color(0xFF061C40), Color(0xFFED1C24))
        "axis" in n -> BankBrand(Color(0xFF97144D), Color(0xFF3B0A20), Color(0xFFE8608C))
        "kotak" in n -> BankBrand(Color(0xFF16336B), Color(0xFF0A1730), Color(0xFFED1C24))
        "punjab" in n || "pnb" in n -> BankBrand(Color(0xFF7B1B4E), Color(0xFF2E0A1E), Color(0xFFF9B233))
        "bank of baroda" in n || "bob" in n -> BankBrand(Color(0xFFDD6B20), Color(0xFF4A2008), Color(0xFFFFD08A))
        "canara" in n -> BankBrand(Color(0xFF00594C), Color(0xFF00251F), Color(0xFFF9B233))
        "union" in n -> BankBrand(Color(0xFFC8102E), Color(0xFF430812), Color(0xFF1D6FB8))
        "idfc" in n -> BankBrand(Color(0xFF9D1D27), Color(0xFF37090D), Color(0xFFF3C13A))
        "indusind" in n -> BankBrand(Color(0xFF98002E), Color(0xFF320010), Color(0xFFD4AF37))
        "yes" in n -> BankBrand(Color(0xFF00448C), Color(0xFF001B38), Color(0xFFE31E24))
        "csd" in n || "canteen" in n -> BankBrand(Color(0xFF2F4F2F), Color(0xFF14210F), Color(0xFFC9B458))
        "amex" in n || "american express" in n -> BankBrand(Color(0xFF006FCF), Color(0xFF00294D), Color(0xFF7FBFFF))
        else -> BankBrand(Color(0xFF3A3F58), Color(0xFF15172A), Color(0xFF8FA0FF))
    }
}

/* ---------- Bank logo marks (drawn to match the real logos) ---------- */

/**
 * HDFC Bank house mark: red square band → white band → solid navy centre.
 */
@Composable
fun HdfcLogo(size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRect(Color(0xFFED232A), size = Size(s, s))
        val w1 = s * 0.68f
        drawRect(Color.White, topLeft = Offset((s - w1) / 2, (s - w1) / 2), size = Size(w1, w1))
        val w2 = s * 0.34f
        drawRect(Color(0xFF004C8F), topLeft = Offset((s - w2) / 2, (s - w2) / 2), size = Size(w2, w2))
    }
}

/**
 * ICICI Bank "i-man" mark: orange wave over the vertical body.
 */
@Composable
fun IciciLogo(size: androidx.compose.ui.unit.Dp = 22.dp, onLight: Boolean = false) {
    val body = if (onLight) Color(0xFFAE282E) else Color.White
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        // body: vertical capsule, slightly tilted feel via rounded ends
        drawRoundRect(
            body,
            topLeft = Offset(s * 0.38f, s * 0.34f),
            size = Size(s * 0.24f, s * 0.62f),
            cornerRadius = CornerRadius(s * 0.12f),
        )
        // the orange wave (tilde) above
        val wave = Path().apply {
            moveTo(s * 0.10f, s * 0.26f)
            cubicTo(s * 0.28f, s * 0.02f, s * 0.50f, s * 0.30f, s * 0.72f, s * 0.16f)
            lineTo(s * 0.88f, s * 0.02f)
            cubicTo(s * 0.62f, s * 0.40f, s * 0.34f, s * 0.14f, s * 0.16f, s * 0.34f)
            close()
        }
        drawPath(wave, Color(0xFFF06321))
    }
}

/**
 * SBI mark: blue disc with the white keyhole (centre hole + slot opening downward).
 */
@Composable
fun SbiLogo(size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2, s / 2)
        drawCircle(Color(0xFF00B5EF), radius = s / 2, center = c)
        drawCircle(Color.White, radius = s * 0.16f, center = c)
        drawRect(
            Color.White,
            topLeft = Offset(c.x - s * 0.075f, c.y),
            size = Size(s * 0.15f, s * 0.5f),
        )
    }
}

/** Bank mark for a catalog bank key on the card face (Canvas — renders white on dark plastic). */
@Composable
fun BankLogo(bankKey: String?, size: androidx.compose.ui.unit.Dp = 22.dp, onLight: Boolean = false) {
    when (bankKey) {
        CardCatalog.BANK_HDFC -> HdfcLogo(size)
        CardCatalog.BANK_ICICI -> IciciLogo(size, onLight)
        CardCatalog.BANK_SBI -> SbiLogo(size)
    }
}

/** Drawable resource for the real full-colour bank logo (for use on white/light chips). */
fun bankLogoRes(bankKey: String?): Int? = when (bankKey) {
    CardCatalog.BANK_HDFC -> com.family.pswdmngr.R.drawable.logo_hdfc
    CardCatalog.BANK_ICICI -> com.family.pswdmngr.R.drawable.icici
    CardCatalog.BANK_SBI -> com.family.pswdmngr.R.drawable.logo_sbi
    else -> null
}

/**
 * Real bank logo inside a white circle chip — the genuine, full-colour mark
 * used in lists, detail headers and the home hub. Falls back to the Canvas
 * mark (then bank initials) when no image exists for the key.
 */
@Composable
fun BankLogoChip(
    bankKey: String?,
    bankName: String,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val res = bankLogoRes(bankKey)
        if (res != null) {
            com.family.pswdmngr.ui.screens.LogoImage(res, size * 0.78f, bankName) {}
        } else if (bankKey != null) {
            BankLogo(bankKey, size * 0.70f, onLight = true)
        } else {
            Text(
                bankName.trim().split(Regex("\\s+"))
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""),
                color = bankBrand(bankName).top,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp,
            )
        }
    }
}

/**
 * The Google "G" — real four-colour mark, with the drawn version as fallback.
 */
@Composable
fun GoogleLogo(size: androidx.compose.ui.unit.Dp = 22.dp) {
    com.family.pswdmngr.ui.screens.LogoImage(
        com.family.pswdmngr.R.drawable.google_logo, size, "Google") {
        GoogleLogoCanvas(size)
    }
}

@Composable
private fun GoogleLogoCanvas(size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.20f
        val inset = stroke / 2
        val arc = Size(s - stroke, s - stroke)
        fun seg(color: Color, start: Float, sweep: Float) = drawArc(
            color, startAngle = start, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = arc,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        seg(Color(0xFFEA4335), 165f, 125f)  // red: top-left → top
        seg(Color(0xFFFBBC05), 125f, 40f)   // yellow: bottom-left
        seg(Color(0xFF34A853), 45f, 80f)    // green: bottom
        seg(Color(0xFF4285F4), 0f, 45f)     // blue: bottom-right up to the bar
        // blue crossbar
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(s / 2, s / 2 - stroke / 2),
            size = Size(s / 2 - inset / 2, stroke),
        )
    }
}

/* ---------- Network marks (drawn to the official artwork) ---------- */

@Composable
fun NetworkMark(network: CardNetwork, compact: Boolean = false, onLight: Boolean = false) {
    val h = if (compact) 22.dp else 30.dp
    when (network) {
        CardNetwork.MASTERCARD, CardNetwork.MAESTRO -> {
            // Mastercard's real mark reads on any plastic colour; Maestro keeps the drawn blend.
            if (network == CardNetwork.MASTERCARD) {
                com.family.pswdmngr.ui.screens.LogoImage(
                    com.family.pswdmngr.R.drawable.mark_mastercard, h * 1.25f, "Mastercard") {}
            } else {
                val right = Color(0xFF0099DF)
                val lens = Color(0xFF6C6BBD)
                Canvas(Modifier.height(h).width(h * 1.6f)) {
                    val r = size.height / 2
                    val left = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(r, r), r)) }
                    val rite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(size.width - r, r), r)) }
                    drawPath(left, Color(0xFFEB001B), style = Fill)
                    drawPath(rite, right, style = Fill)
                    drawPath(Path.combine(PathOperation.Intersect, left, rite), lens, style = Fill)
                }
            }
        }
        CardNetwork.VISA -> Text(
            "VISA",
            color = if (onLight) Color(0xFF1A1F71) else Color.White, // official Visa blue on light plastic
            fontSize = if (compact) 15.sp else 21.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = 1.sp,
        )
        CardNetwork.RUPAY -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "RuPay",
                color = if (onLight) Color(0xFF14275A) else Color.White, // official navy on light plastic
                fontSize = if (compact) 14.sp else 19.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.width(4.dp))
            // the RuPay fast-forward arrow: orange stroke over green stroke
            Canvas(Modifier.height(if (compact) 13.dp else 17.dp).width(if (compact) 9.dp else 12.dp)) {
                val w = size.width; val hh = size.height
                val upper = Path().apply { // orange arm: top-left → tip
                    moveTo(0f, 0f); lineTo(w * 0.45f, 0f); lineTo(w, hh / 2)
                    lineTo(w * 0.55f, hh / 2); close()
                }
                val lower = Path().apply { // green arm: tip → bottom-left
                    moveTo(w, hh / 2); lineTo(w * 0.45f, hh); lineTo(0f, hh)
                    lineTo(w * 0.55f, hh / 2); close()
                }
                drawPath(upper, Color(0xFFF7981D))
                drawPath(lower, Color(0xFF01A64F))
            }
        }
        CardNetwork.AMEX -> Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF006FCF))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                "AMEX",
                color = Color.White,
                fontSize = if (compact) 11.sp else 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        }
        CardNetwork.DINERS -> Canvas(Modifier.size(h)) {
            drawCircle(if (onLight) Color(0xFF8A9BA8) else Color(0xFFB8C4CE), size.minDimension / 2)
            drawCircle(Color(0xFF004A97), size.minDimension / 2 * 0.72f)
            drawRect(
                Color.White,
                topLeft = Offset(size.width * 0.42f, size.height * 0.18f),
                size = Size(size.width * 0.16f, size.height * 0.64f),
            )
        }
        CardNetwork.UNKNOWN -> Spacer(Modifier.height(h))
    }
}

/* ---------- EMV chip + contactless glyphs ---------- */

@Composable
fun EmvChip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 44.dp, height = 33.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE8C56A), Color(0xFFB78A2E)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 2f)
            val line = Color(0xFF6B4F13).copy(alpha = 0.7f)
            drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
            drawLine(line, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 2f)
            drawLine(line, Offset(0f, 2 * size.height / 3), Offset(size.width, 2 * size.height / 3), 2f)
            drawRoundRect(
                line,
                topLeft = Offset(size.width * 0.28f, size.height * 0.22f),
                size = Size(size.width * 0.44f, size.height * 0.56f),
                cornerRadius = CornerRadius(6f),
                style = stroke,
            )
        }
    }
}

@Composable
fun ContactlessGlyph(tint: Color = Color.White.copy(alpha = 0.8f)) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 3f, cap = StrokeCap.Round)
        for (i in 0..3) {
            val r = size.minDimension * (0.18f + i * 0.24f)
            drawArc(
                tint, startAngle = -35f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(-r + 2f, size.height / 2 - r),
                size = Size(r * 2, r * 2),
                style = stroke,
            )
        }
    }
}

/* ---------- The card face ---------- */

fun groupDigits(number: String): String {
    val d = number.filter { it.isDigit() }
    return if (CardNetwork.detect(d) == CardNetwork.AMEX && d.length > 10)
        listOf(d.take(4), d.drop(4).take(6), d.drop(10)).filter { it.isNotEmpty() }.joinToString("  ")
    else d.chunked(4).joinToString("  ")
}

fun maskedNumber(number: String): String {
    val d = number.filter { it.isDigit() }
    if (d.length <= 4) return d
    return "••••  ••••  ••••  " + d.takeLast(4)
}

@Composable
fun CardFace(
    card: CardEntry,
    revealNumber: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val product = CardCatalog.byId(card.productId)
    val bankKey = product?.bank ?: CardCatalog.bankKeyFor(card.bankName)
    val brand = bankBrand(card.bankName)
    val top = product?.top ?: brand.top
    val bottom = product?.bottom ?: brand.bottom
    val accent = product?.accent ?: brand.accent
    val light = product?.dark == false // light plastic → dark ink
    val ink = if (light) Color(0xFF17233B) else Color.White
    val inkSoft = ink.copy(alpha = if (light) 0.55f else 0.55f)
    val network = CardNetwork.resolve(card)
    val isCsd = card.cardType == CardType.CSD
    // Official artwork already carries the bank logo, product name, chip and
    // contactless glyph — so we only overlay the dynamic data on top of it.
    val hasArt = product?.artworkRes != null
    val artPainter = product?.artworkRes?.let { painterResource(it) }

    // Always use the standard 1.586:1 ISO/IEC 7810 ID-1 aspect ratio.
    // This guarantees every card in a list/grid renders at identical box dimensions
    // regardless of source image orientation. Artwork is then cropped (not letterboxed)
    // to fill this container — the bundled images should have safe-zone padding so the
    // design's critical elements (logo, chip, contactless glyph) are never cut off.
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(top, bottom))),
    ) {
        // When an official issuer image is available, it replaces the generic palette.
        // Use Crop so the artwork fills the entire 1.586:1 container with no letterboxing.
        // Bundled images are pre-processed with safe-zone padding so critical elements
        // (logo, chip) survive cropping.
        artPainter?.let { p ->
            Image(
                painter = p, contentDescription = "${product?.name} official card design",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        // soft decorative arcs like real card art
        if (product?.artworkRes == null) Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                accent.copy(alpha = 0.13f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.95f, size.height * 0.05f),
            )
            drawCircle(
                (if (light) Color.Black else Color.White).copy(alpha = 0.05f),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.05f, size.height * 1.05f),
            )
        }

        Column(Modifier.fillMaxSize().padding(18.dp)) {
            if (!hasArt) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    if (bankKey != null && !isCsd) {
                        BankLogo(bankKey, size = 24.dp, onLight = light)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                isCsd -> "CSD CANTEEN SMART CARD"
                                bankKey != null -> CardCatalog.bankDisplay(bankKey).uppercase()
                                else -> card.bankName.uppercase().ifBlank { "BANK" }
                            },
                            color = ink,
                            fontFamily = DisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                        )
                        Text(
                            when {
                                product != null ->
                                    product.name.uppercase() + " " + CardType.label(card.cardType).uppercase()
                                card.label.isNotBlank() ->
                                    CardType.label(card.cardType).uppercase() + " • " + card.label.uppercase()
                                else -> CardType.label(card.cardType).uppercase()
                            },
                            color = if (product != null) accent else inkSoft,
                            fontFamily = DisplayFamily,
                            fontWeight = if (product != null) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                        if (product?.tagline?.isNotBlank() == true) {
                            Text(
                                product.tagline, color = inkSoft,
                                fontFamily = DisplayFamily, fontSize = 8.sp,
                                letterSpacing = 0.5.sp, maxLines = 1,
                            )
                        }
                    }
                    ContactlessGlyph(tint = ink.copy(alpha = 0.8f))
                }

                Spacer(Modifier.height(8.dp))
                EmvChip()
            }
            Spacer(Modifier.weight(1f))

            // On official artwork the number/holder sit in a legibility scrim strip.
            // Reduce font and remove letter-spacing on artwork cards so all 4 digit
            // groups fit inside the scrim without truncation.
            val overlayInk = Color.White
            val numberDisplay = if (revealNumber) groupDigits(card.number).ifBlank { "••••  ••••  ••••  ••••" }
                else maskedNumber(card.number).ifBlank { "••••  ••••  ••••  ••••" }
            Text(
                text = numberDisplay,
                color = if (hasArt) overlayInk else ink,
                fontSize = if (hasArt) 15.sp else 17.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = if (hasArt) 0.sp else 0.5.sp,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                modifier = if (hasArt)
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                else Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row {
                        if (isCsd && card.serialNo.isNotBlank()) {
                            Column {
                                Text("SERIAL NO", color = inkSoft, fontFamily = DisplayFamily,
                                    fontSize = 7.sp, letterSpacing = 1.sp)
                                Text(card.serialNo, color = ink, fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                        if (card.expiry.isNotBlank()) {
                            Column {
                                Text("VALID THRU", color = if (hasArt) overlayInk.copy(alpha = 0.7f) else inkSoft,
                                    fontFamily = DisplayFamily, fontSize = 7.sp, letterSpacing = 1.sp)
                                Text(card.expiry, color = if (hasArt) overlayInk else ink, fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        card.holder.uppercase().ifBlank { " " },
                        color = if (hasArt) overlayInk.copy(alpha = 0.95f) else ink.copy(alpha = 0.92f),
                        fontFamily = DisplayFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                    )
                }
                // Network mark is baked into official artwork, so only draw it on generic faces
                if (isCsd) {
                    Text("CSD", color = Color(0xFFC9B458), fontFamily = DisplayFamily,
                        fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                } else if (!hasArt) {
                    NetworkMark(network, onLight = light)
                }
            }
        }
    }
}
