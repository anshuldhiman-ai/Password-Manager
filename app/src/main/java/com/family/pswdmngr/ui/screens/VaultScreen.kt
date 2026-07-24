package com.family.pswdmngr.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.ApkSignatureVerifier
import com.family.pswdmngr.crypto.RootDetector
import com.family.pswdmngr.data.EntryCategory
import com.family.pswdmngr.data.VaultEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*

/** Time-of-day greeting shown above the vault title. */
private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) {
        LaunchedEffect(Unit) { nav.navigate("unlock") { popUpTo(0) { inclusive = true } } }
        return
    }

    val ctx = LocalContext.current
    val entries by VaultSession.dao().observeAll().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val isRooted = remember { RootDetector.isRooted(ctx) }
    val isTampered = remember { ApkSignatureVerifier.isTampered(ctx) }
    var showPendingRecovery by remember { mutableStateOf(false) }
    val pendingKey = VaultSession.pendingRecoveryKey

    // Show pending recovery key from v1→v2 migration
    LaunchedEffect(pendingKey) {
        if (pendingKey != null) showPendingRecovery = true
    }

    // trim so a trailing space ("google ") still matches "google"
    val q = query.trim()
    val visible = entries.filter { e ->
        q.isBlank() || e.title.contains(q, true) ||
                e.username.contains(q, true) || e.url.contains(q, true)
    }

    Scaffold(
        containerColor = Midnight,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.navigate("edit/-1") },
                containerColor = Violet,
                shape = CircleShape,
            ) { Icon(Icons.Rounded.Add, "Add", tint = TextPrimary) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.labelMedium, color = Cyan)
                    Text("My Vault", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(
                        "${entries.size} items secured",
                        style = MaterialTheme.typography.labelMedium, color = TextSecondary
                    )
                }
                IconButton(onClick = { nav.navigate("generator") }) {
                    Icon(Icons.Rounded.AutoAwesome, "Generator", tint = Cyan)
                }
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Rounded.Settings, "Settings", tint = TextSecondary)
                }
            }

            // Search
            VaultTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search vault",
                modifier = Modifier.padding(horizontal = 22.dp),
                trailingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
            )

            Spacer(Modifier.height(14.dp))

            // Security warning banners (root detection, APK tamper)
            if (isRooted) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp).clip(RoundedCornerShape(14.dp))
                        .background(Amber.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rooted device", color = Amber, style = MaterialTheme.typography.labelMedium)
                        Text("Biometric unlock disabled for your safety", color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (isTampered) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp).clip(RoundedCornerShape(14.dp))
                        .background(Coral.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = Coral, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("APK signature mismatch — app may be tampered with",
                        color = Coral, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Hub: the other vault sections — single row, no extra chip bar below
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HubTile("Cards", Icons.Rounded.CreditCard, Cyan) { nav.navigate("cards") } }
                item { HubTile("CSD", Icons.Rounded.ShoppingBag, Amber) { nav.navigate("csd") } }
                item { HubTile("Google", Icons.Rounded.AccountCircle, Color(0xFF4285F4)) { nav.navigate("googleAccounts") } }
                item { HubTile("Banks", Icons.Rounded.AccountBalance, Mint) { nav.navigate("banks") } }
                item { HubTile("SBI Rewardz", Icons.Rounded.CardGiftcard, Amber) { nav.navigate("sbiRewardz") } }
                item { HubTile("Documents", Icons.Rounded.Badge, Amber) { nav.navigate("docs") } }
                item { HubTile("Notes", Icons.Rounded.StickyNote2, Coral) { nav.navigate("notes") } }
                item { HubTile("Tasks", Icons.Rounded.TaskAlt, Violet) { nav.navigate("tasks") } }
            }

            Spacer(Modifier.height(16.dp))

            if (visible.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconBadge(Icons.Rounded.Inventory2, TextSecondary, size = 64)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (entries.isEmpty()) "Your vault is empty.\nTap + to add your first login."
                        else "Nothing matches your search.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(visible, key = { _, it -> it.id }) { i, entry ->
                        Box(Modifier.animatedListItem(i)) {
                            EntryRow(entry) { nav.navigate("entry/${entry.id}") }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ── Pending recovery key dialog (after v1→v2 migration) ──
    if (showPendingRecovery && pendingKey != null) {
        AlertDialog(
            onDismissRequest = {
                showPendingRecovery = false
                VaultSession.pendingRecoveryKey = null
            },
            containerColor = Surface1,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Your Recovery Key", color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        "Your vault has been upgraded with a Recovery Key:",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pendingKey,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 3.sp,
                        ),
                        color = Amber,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This is the ONLY backup if you ever forget your master password. " +
                                "Write it down and keep it safe offline. " +
                                "It will not be shown again.",
                        color = Coral, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPendingRecovery = false
                    VaultSession.pendingRecoveryKey = null
                }) { Text("I've saved it", color = Cyan) }
            },
        )
    }
}

/** Tappable tile linking to another vault section — premium glass style. */
@Composable
private fun HubTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "hubScale")
    Column(
        Modifier
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = tint.copy(alpha = 0.12f),
                spotColor = tint.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EntryRow(entry: VaultEntry, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryBadge(entry)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                    }
                }
                if (entry.username.isNotBlank()) {
                    Text(
                        entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
        }
    }
}

/**
 * Icon for a login row: authentic Google "G" for Google accounts,
 * real bank marks for HDFC/ICICI/SBI logins, category icon otherwise.
 */
@Composable
private fun EntryBadge(entry: com.family.pswdmngr.data.VaultEntry) {
    val hint = (entry.title + " " + entry.url + " " + entry.username).lowercase()
    val isGoogle = "google" in hint || "gmail" in hint || entry.username.endsWith("@gmail.com", true)
    val bankKey = com.family.pswdmngr.ui.cards.CardCatalog.bankKeyFor(hint)
    when {
        isGoogle -> Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) { com.family.pswdmngr.ui.cards.GoogleLogo(size = 24.dp) }
        bankKey != null ->
            com.family.pswdmngr.ui.cards.BankLogoChip(bankKey, entry.title, size = 46.dp)
        else -> IconBadge(categoryIcon(entry.category), categoryColor(entry.category))
    }
}
