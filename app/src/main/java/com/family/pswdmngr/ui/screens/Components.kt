package com.family.pswdmngr.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.PasswordGenerator
import com.family.pswdmngr.data.EntryCategory
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.delay

/** Flat surface card — default list/detail container per reference UI. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.98f else 1f, label = "surfacePress")
    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2.copy(alpha = 0.72f))
            .border(1.dp, Stroke, RoundedCornerShape(16.dp))
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Glassmorphic card — reserved for unlock/hero emphasis only. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.98f else 1f, label = "cardPress")
    Column(
        modifier = modifier
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.28f))
            .clip(RoundedCornerShape(20.dp))
            .background(CardGradient)
            .border(1.dp, Stroke, RoundedCornerShape(20.dp))
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(20.dp),
        content = content,
    )
}

/** Primary CTA — solid cyan pill per reference UI. */
@Composable
fun AccentButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = if (enabled) Cyan else Surface2
    val fg = if (enabled) Midnight else TextSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** @see AccentButton */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) = AccentButton(text, modifier, enabled, icon, onClick)

/** Outlined secondary action button. */
@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Stroke, RoundedCornerShape(16.dp))
            .background(Surface2.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

/** Horizontal filter chips with counts — vault/search screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipRow(
    chips: List<Pair<String, Int>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips.size) { i ->
            val (label, count) = chips[i]
            val selected = selectedIndex == i
            FilterChip(
                selected = selected,
                onClick = { onSelect(i) },
                label = {
                    Text(
                        if (count >= 0) "$label  $count" else label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Cyan.copy(alpha = 0.18f),
                    selectedLabelColor = Cyan,
                    containerColor = Surface2.copy(alpha = 0.55f),
                    labelColor = TextSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = if (selected) Cyan.copy(alpha = 0.35f) else Stroke,
                    selectedBorderColor = Cyan.copy(alpha = 0.35f),
                ),
            )
        }
    }
}

fun categoryIcon(c: EntryCategory): ImageVector = when (c) {
    EntryCategory.WEBSITE_APP -> Icons.Rounded.Language
    EntryCategory.BANK -> Icons.Rounded.AccountBalance
    EntryCategory.CARD -> Icons.Rounded.CreditCard
    EntryCategory.NOTE -> Icons.Rounded.StickyNote2
    EntryCategory.IDENTITY -> Icons.Rounded.Badge
    EntryCategory.WIFI -> Icons.Rounded.Wifi
}

@Composable
@ReadOnlyComposable
fun categoryColor(c: EntryCategory): Color = when (c) {
    EntryCategory.WEBSITE_APP -> Violet
    EntryCategory.BANK -> Mint
    EntryCategory.CARD -> Cyan
    EntryCategory.NOTE -> Amber
    EntryCategory.IDENTITY -> Mint
    EntryCategory.WIFI -> Coral
}

/** Circular icon badge with soft tinted background. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Int = 46) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Shared empty state: badge + centered title/subtitle. */
@Composable
fun EmptyState(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(icon, tint, size = 64)
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center)
        }
    }
}

/**
 * Renders a bundled brand image when available, else the drawn fallback.
 * Multicolour marks are never tinted — the image is shown as-is.
 */
@Composable
fun LogoImage(
    resId: Int?,
    size: Dp,
    contentDescription: String? = null,
    fallback: @Composable () -> Unit,
) {
    if (resId != null) {
        androidx.compose.foundation.Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else fallback()
}

/** Standard screen chrome: themed TopAppBar + back arrow, used by most screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    nav: NavController,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Midnight,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * Staggered entrance for list items: fade + rise, ~35ms per index (capped
 * so long lists don't feel sluggish). Wrap list item content with this.
 */
@Composable
fun Modifier.animatedListItem(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * 35).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(240), label = "itemAlpha")
    val rise by animateFloatAsState(if (shown) 0f else 26f, tween(280), label = "itemRise")
    return this.then(Modifier.graphicsLayer { this.alpha = alpha; translationY = rise })
}

/**
 * Scrollable full-screen container for the auth screens (onboarding, unlock,
 * recovery key, forgot password).
 *
 * Automatically handles IME (software keyboard) and system bars insets so input
 * fields and action buttons are never obscured or pushed outside the viewport.
 */
@Composable
fun AuthScrollColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    padding: PaddingValues = PaddingValues(24.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(padding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * App Logo Badge matching sample.jpg design.
 * Features a glowing squircle with high-contrast PSWD MNGR branding.
 */
@Composable
fun AppLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    glow: Float = 1f,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            Modifier
                .size(size * 1.35f)
                .scale(glow)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Cyan.copy(alpha = 0.28f), Violet.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(size * 0.28f),
            color = Surface2,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.linearGradient(listOf(Cyan.copy(alpha = 0.7f), Violet.copy(alpha = 0.5f)))
            ),
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E2538), Color(0xFF101424))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PSWD",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontSize = (size.value * 0.22f).sp,
                        ),
                        color = Color.White,
                    )
                    Text(
                        "MNGR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontSize = (size.value * 0.16f).sp,
                        ),
                        color = Cyan,
                    )
                }
            }
        }
    }
}

/**
 * Password strength meter — a tinted bar plus a clear status message.
 */
@Composable
fun PasswordStrengthMeter(
    password: String,
    modifier: Modifier = Modifier,
) {
    val entropy = PasswordGenerator.entropy(password)
    val meterColor = when {
        password.isEmpty() -> TextSecondary
        password.length < 8 -> Coral
        entropy >= 60 -> Mint
        entropy >= 40 -> Cyan
        else -> Amber
    }
    val meterFraction = when {
        password.isEmpty() -> 0f
        password.length < 8 -> 0.25f
        entropy >= 60 -> 1.0f
        entropy >= 40 -> 0.70f
        else -> 0.45f
    }
    val label = when {
        password.isEmpty() -> "Must be at least 8 characters"
        password.length < 8 -> "Too short — minimum 8 characters (${password.length}/8)"
        entropy >= 60 -> "Very strong password"
        entropy >= 40 -> "Good password"
        else -> "Fair — add numbers or symbols for more security"
    }
    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { meterFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = meterColor,
            trackColor = Surface2,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = meterColor,
            modifier = Modifier.align(Alignment.Start),
        )
    }
}

/** True when [password] is at least 8 characters (standard for vault master passwords). */
fun isPasswordStrong(password: String): Boolean = password.length >= 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions =
        androidx.compose.foundation.text.KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        readOnly = readOnly,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Stroke,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Cyan,
            focusedContainerColor = Surface2.copy(alpha = 0.85f),
            unfocusedContainerColor = Surface2.copy(alpha = 0.55f),
        ),
    )
}

