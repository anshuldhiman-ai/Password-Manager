package com.family.pswdmngr.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.family.pswdmngr.data.*
import com.family.pswdmngr.ui.cards.CardCatalog
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

// ── Tab definitions ─────────────────────────────────────────────────────

private data class NavTab(val label: String, val icon: ImageVector, val activeIcon: ImageVector)

private val TABS = listOf(
    NavTab("Home", Icons.Rounded.Home, Icons.Rounded.Home),
    NavTab("Search", Icons.Rounded.Search, Icons.Rounded.Search),
    NavTab("Cards", Icons.Rounded.CreditCard, Icons.Rounded.CreditCard),
    NavTab("Notes", Icons.Rounded.StickyNote2, Icons.Rounded.StickyNote2),
    NavTab("More", Icons.Rounded.MoreHoriz, Icons.Rounded.MoreHoriz),
)

private const val TAB_VAULT = 0
private const val TAB_SEARCH = 1
private const val TAB_CARDS = 2
private const val TAB_NOTES = 3
private const val TAB_MORE = 4

// ── Main screen with bottom nav ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) {
        LaunchedEffect(Unit) { nav.navigate("unlock") { popUpTo(0) { inclusive = true } } }
        return
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(TAB_VAULT) }
    var showAddSheet by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // Pending recovery key from v1→v2 migration (checks both volatile + persisted flag)
    var showPendingRecovery by remember { mutableStateOf(false) }
    var pendingRecoveryValue by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val volatileKey = VaultSession.pendingRecoveryKey
        if (volatileKey != null) {
            pendingRecoveryValue = volatileKey
            showPendingRecovery = true
        } else if (VaultSession.hasPendingRecoveryDisplay(ctx)) {
            // Process was killed after migration, but flag survived in EncryptedSharedPreferences
            pendingRecoveryValue = VaultSession.getRecoveryKey(ctx)
            showPendingRecovery = true
        }
    }

    // Back press on tabs stays on current tab; second back press exits the app
    BackHandler(enabled = selectedTab != TAB_VAULT) {
        selectedTab = TAB_VAULT
    }
    BackHandler(enabled = selectedTab == TAB_VAULT) {
        // Pop to unlock or exit
        nav.popBackStack("unlock", inclusive = false)
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = Surface1,
                tonalElevation = 0.dp,
            ) {
                TABS.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        icon = {
                            Icon(
                                if (selectedTab == i) tab.activeIcon else tab.icon,
                                tab.label,
                            )
                        },
                        label = {
                            Text(tab.label, style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Violet,
                            selectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Violet.copy(alpha = 0.12f),
                        ),
                        alwaysShowLabel = true,
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddSheet = true
                },
                containerColor = Violet,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
            ) {
                Icon(Icons.Rounded.Add, "Quick add", tint = TextPrimary)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (selectedTab) {
                TAB_VAULT -> VaultDashboard(nav, snackbar, onShowMore = { selectedTab = TAB_MORE }, onSettings = { nav.navigate("settings") })
                TAB_SEARCH -> UnifiedSearchTab(nav, snackbar)
                TAB_CARDS -> CardsHub(nav, snackbar)
                TAB_NOTES -> NotesHub(nav, snackbar)
                TAB_MORE -> MoreTab(nav, snackbar)
            }
        }
    }

    // ── Quick-add bottom sheet ──
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = Surface1,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            AddEntrySheet(
                onSelect = { route ->
                    showAddSheet = false
                    nav.navigate(route)
                },
            )
        }
    }

    // ── Pending recovery key dialog (migration — survives process kill) ──
    // Same strictness as onboarding: forces user to retype a random group.
    if (showPendingRecovery && pendingRecoveryValue != null) {
        val migrationKey = pendingRecoveryValue!!
        val allGroups = remember(migrationKey) {
            migrationKey.split('-').mapIndexed { idx, g -> idx + 1 to g }
        }
        val verifyGroupIdx = remember { if (allGroups.isNotEmpty()) (0 until allGroups.size).random() else -1 }
        val verifyGroup = allGroups.getOrNull(verifyGroupIdx)
        var confirmInput by remember { mutableStateOf("") }
        var confirmError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { /* intentional no-op — user must confirm */ },
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
                    Text("Your vault has been upgraded with a Recovery Key:",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(migrationKey, color = Amber,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 3.sp,
                        ))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "This is the ONLY backup if you forget your master password. " +
                                "Write it down and keep it safe offline.",
                        color = Coral, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Divider(color = Surface2)
                    Spacer(Modifier.height(12.dp))
                    Text("Confirm you saved it".uppercase(),
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    if (verifyGroup != null) {
                        Text("Type group ${verifyGroup.first}:",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = { confirmInput = it.uppercase(); confirmError = false },
                            label = { Text("e.g. ${verifyGroup.second.take(2)}••") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (confirmError) Coral else Violet,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                cursorColor = Cyan,
                                focusedContainerColor = Surface2.copy(alpha = 0.6f),
                                unfocusedContainerColor = Surface2.copy(alpha = 0.3f),
                            ),
                        )
                        if (confirmError) {
                            Spacer(Modifier.height(4.dp))
                            Text("That doesn't match group ${verifyGroup.first}. Check what you saved.",
                                color = Coral, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmInput.trim().uppercase() == verifyGroup?.second,
                    onClick = {
                        if (confirmInput.trim().uppercase() == verifyGroup?.second) {
                            showPendingRecovery = false
                            VaultSession.dismissPendingRecoveryDisplay(ctx)
                        } else {
                            confirmError = true
                        }
                    },
                ) { Text("I've saved it", color = Cyan) }
            },
        )
    }
}

// ── Quick-add bottom sheet ──────────────────────────────────────────────

@Composable
private fun AddEntrySheet(onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Quick add", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(16.dp))

        val items = listOf(
            AddItem("Login", Icons.Rounded.Language, Violet) { onSelect("edit/-1") },
            AddItem("Card", Icons.Rounded.CreditCard, Cyan) { onSelect("cardEdit/-1") },
            AddItem("Bank", Icons.Rounded.AccountBalance, Mint) { onSelect("bankEdit/-1") },
            AddItem("Document", Icons.Rounded.Description, Amber) { onSelect("docEdit/-1") },
            AddItem("Note", Icons.Rounded.StickyNote2, Coral) { onSelect("noteEdit/-1") },
            AddItem("Task", Icons.Rounded.TaskAlt, Color(0xFF7C5CFF)) { onSelect("tasks") },
        )

        // 3x2 grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(item.color.copy(alpha = 0.08f))
                                .clickable { item.onClick() }
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape)
                                    .background(item.color.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center) {
                                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.label, color = TextPrimary,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private data class AddItem(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

// ── Tab 1: Vault Dashboard ──────────────────────────────────────────────

/** Time-of-day greeting. */
private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" }
}

@Composable
private fun VaultDashboard(nav: NavController, snackbar: SnackbarHostState, onShowMore: () -> Unit = {}, onSettings: () -> Unit = {}) {
    val ctx = LocalContext.current
    val entries by VaultSession.dao().observeAll().collectAsState(initial = emptyList())
    val cards by VaultSession.cardDao().observeAll().collectAsState(initial = emptyList())
    val banks by VaultSession.bankDao().observeAll().collectAsState(initial = emptyList())
    val docs by VaultSession.docDao().observeAll().collectAsState(initial = emptyList())
    val notes by VaultSession.noteDao().observeAll().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val visible = entries.filter { e ->
        q.isBlank() || e.title.contains(q, true) || e.username.contains(q, true) || e.url.contains(q, true)
    }

    val totalItems = entries.size + cards.size + banks.size + docs.size + notes.size
    val favorites = entries.filter { it.favorite }.take(8)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // ── Header ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.labelMedium, color = Cyan)
                    Text("My Vault", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("$totalItems items secured", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                IconButton(onClick = { nav.navigate("generator") }) {
                    Icon(Icons.Rounded.AutoAwesome, "Password generator", tint = Cyan)
                }
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Rounded.Settings, "Settings", tint = TextSecondary)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, "Settings", tint = TextSecondary)
                }
            }
        }

        // ── Favorites row ──
        if (favorites.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionLabel("FAVORITES")
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(favorites, key = { it.id }) { entry ->
                        FavoriteCard(entry) { nav.navigate("entry/${entry.id}") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Pinned search ──
        item {
            VaultTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search vault",
                modifier = Modifier.padding(horizontal = 22.dp),
                trailingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
            )
            Spacer(Modifier.height(14.dp))
        }

        // ── Category tiles ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel("CATEGORIES")
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            CategoryGrid(nav)
            Spacer(Modifier.height(16.dp))
        }

        // ── Entry list ──
        if (visible.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(
                        if (entries.isEmpty()) Icons.Rounded.Inventory2 else Icons.Rounded.SearchOff,
                        if (entries.isEmpty()) TextSecondary else Coral,
                        size = 64,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (entries.isEmpty()) "Your vault is empty — tap + below to add your first item"
                        else "Nothing matches your search.",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (entries.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        GradientButton(
                            "Add a login",
                            icon = Icons.Rounded.Add,
                        ) { nav.navigate("edit/-1") }
                    }
                }
            }
        } else {
            itemsIndexed(visible, key = { _, it -> it.id }) { i, entry ->
                Box(Modifier.animatedListItem(i)) {
                    EntryRow(entry) { nav.navigate("entry/${entry.id}") }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(entry: com.family.pswdmngr.data.VaultEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "favScale")
    Surface(
        modifier = Modifier.scale(scale).width(140.dp).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Surface2.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.Star, null, tint = Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(entry.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.username.isNotBlank()) {
                Text(entry.username, color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CategoryGrid(nav: NavController) {
    val categories = listOf(
        CategoryTile("Cards", Icons.Rounded.CreditCard, Cyan) { nav.navigate("cards") },
        CategoryTile("Banks", Icons.Rounded.AccountBalance, Mint) { nav.navigate("banks") },
        CategoryTile("Documents", Icons.Rounded.Description, Amber) { nav.navigate("docs") },
        CategoryTile("Notes", Icons.Rounded.StickyNote2, Coral) { nav.navigate("notes") },
        CategoryTile("Tasks", Icons.Rounded.TaskAlt, Violet) { nav.navigate("tasks") },
        CategoryTile("Generator", Icons.Rounded.AutoAwesome, Cyan) { nav.navigate("generator") },
    )
    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cat ->
                    Box(Modifier.weight(1f)) {
                        CategoryTileComposable(cat, nav)
                    }
                }
            }
        }
    }
}

private data class CategoryTile(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

@Composable
private fun CategoryTileComposable(cat: CategoryTile, nav: NavController) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "catScale")
    Surface(
        modifier = Modifier.scale(scale).clip(RoundedCornerShape(18.dp)).clickable(
            interactionSource = interaction, indication = null, onClick = cat.onClick),
        color = cat.color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cat.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center) {
                Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(cat.label, color = TextPrimary, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EntryRow(entry: com.family.pswdmngr.data.VaultEntry, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryBadge(entry)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, null, tint = Amber, modifier = Modifier.size(14.dp))
                    }
                }
                if (entry.username.isNotBlank()) {
                    Text(entry.username, style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
        }
    }
}

@Composable
private fun EntryBadge(entry: com.family.pswdmngr.data.VaultEntry) {
    val hint = (entry.title + " " + entry.url + " " + entry.username).lowercase()
    val isGoogle = "google" in hint || "gmail" in hint || entry.username.endsWith("@gmail.com", true)
    val bankKey = CardCatalog.bankKeyFor(hint)
    when {
        isGoogle -> Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center) {
            com.family.pswdmngr.ui.cards.GoogleLogo(size = 24.dp)
        }
        bankKey != null ->
            com.family.pswdmngr.ui.cards.BankLogoChip(bankKey, entry.title, size = 46.dp)
        else -> IconBadge(categoryIcon(entry.category), categoryColor(entry.category))
    }
}

// ── Tab 2: Cards Hub ────────────────────────────────────────────────────

@Composable
private fun CardsHub(nav: NavController, snackbar: SnackbarHostState) {
    val cards by VaultSession.cardDao().observeAll().collectAsState(initial = emptyList())
    val bankCards = cards.filter { it.cardType != com.family.pswdmngr.data.CardType.CSD }.size
    val csdCards = cards.filter { it.cardType == com.family.pswdmngr.data.CardType.CSD }.size

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Cards", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
        }
        if (cards.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.CreditCard, Cyan, "No cards yet",
                    "Tap + to add a debit or credit card with real bank design.")
            }
        } else {
            item {
                CardHubTile("Bank cards", "$bankCards card${if (bankCards != 1) "s" else ""}",
                    Icons.Rounded.CreditCard, Cyan) { nav.navigate("cards") }
            }
            if (csdCards > 0) {
                item {
                    CardHubTile("CSD Canteen cards", "$csdCards card${if (csdCards != 1) "s" else ""}",
                        Icons.Rounded.ShoppingBag, Amber) { nav.navigate("csd") }
                }
            }
        }
    }
}

@Composable
private fun CardHubTile(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
        }
    }
}

// ── Tab 3: Notes & Tasks Hub ────────────────────────────────────────────

@Composable
private fun NotesHub(nav: NavController, snackbar: SnackbarHostState) {
    val notes by VaultSession.noteDao().observeAll().collectAsState(initial = emptyList())
    val pendingTasks by VaultSession.taskDao().observeStarred().collectAsState(initial = emptyList())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Notes & Tasks", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
        }
        item {
            GlassCard(onClick = { nav.navigate("notes") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.StickyNote2, Coral)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notes", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("${notes.size} note${if (notes.size != 1) "s" else ""}", color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
        item {
            GlassCard(onClick = { nav.navigate("tasks") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.TaskAlt, Violet)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tasks", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("${pendingTasks.size} starred", color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Tab 4: Tasks Hub ────────────────────────────────────────────────────

@Composable
private fun TasksHub(nav: NavController, snackbar: SnackbarHostState) {
    val pendingTasks by VaultSession.taskDao().observeStarred().collectAsState(initial = emptyList())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Tasks", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
        }
        item {
            GlassCard(onClick = { nav.navigate("tasks") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.TaskAlt, Violet)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("All tasks", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("${pendingTasks.size} starred", color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Unified Search (integrated into Home dashboard) ─────────────────────

@Composable
private fun UnifiedSearchTab(nav: NavController, snackbar: SnackbarHostState) {
    val entries by VaultSession.dao().observeAll().collectAsState(initial = emptyList())
    val cards by VaultSession.cardDao().observeAll().collectAsState(initial = emptyList())
    val banks by VaultSession.bankDao().observeAll().collectAsState(initial = emptyList())
    val docs by VaultSession.docDao().observeAll().collectAsState(initial = emptyList())
    val notes by VaultSession.noteDao().observeAll().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val results = remember(q, entries, cards, banks, docs, notes) {
        if (q.isBlank()) emptyList()
        else buildList {
            entries.filter { it.title.contains(q, true) || it.username.contains(q, true) || it.url.contains(q, true) }
                .forEach { add(SearchResult("Login", it.title, it.username, "entry/${it.id}", Icons.Rounded.Language, Violet)) }
            cards.filter { it.label.contains(q, true) || it.bankName.contains(q, true) || it.number.contains(q, true) }
                .forEach { add(SearchResult("Card", it.label, it.bankName, "cardDetail/${it.id}", Icons.Rounded.CreditCard, Cyan)) }
            banks.filter { it.bankName.contains(q, true) || it.accountNumber.contains(q, true) }
                .forEach { add(SearchResult("Bank", it.bankName, it.accountNumber.takeLast(4), "bankDetail/${it.id}", Icons.Rounded.AccountBalance, Mint)) }
            docs.filter { it.title.contains(q, true) || it.number.contains(q, true) }
                .forEach { add(SearchResult("Doc", it.title.ifBlank { com.family.pswdmngr.data.DocType.label(it.docType) }, it.number, "docDetail/${it.id}", Icons.Rounded.Description, Amber)) }
            notes.filter { it.title.contains(q, true) || it.body.contains(q, true) }
                .forEach { add(SearchResult("Note", it.title, "", "noteEdit/${it.id}", Icons.Rounded.StickyNote2, Coral)) }
        }.take(50)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
    ) {
        item {
            Text("Search", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            VaultTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search all entries, cards, banks, docs, notes…",
                trailingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (q.isBlank()) {
            item {
                EmptyState(Icons.Rounded.Search, TextSecondary, "Tap the search bar above",
                    "Find any login, card, bank account, document, or note instantly.")
            }
        } else if (results.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.SearchOff, Coral, "No results for \"$q\"",
                    "Try a different search term.")
            }
        } else {
            item {
                Text("${results.size} result${if (results.size != 1) "s" else ""}",
                    color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(results.size, key = { "sr_$it" }) { i ->
                val r = results[i]
                GlassCard(onClick = { nav.navigate(r.route) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(r.icon, r.color, size = 38)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (r.subtitle + if (r.subtitle.isNotBlank()) "  •  " else "") + r.type,
                                color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class SearchResult(
    val type: String, val title: String, val subtitle: String,
    val route: String, val icon: ImageVector, val color: Color,
)

// ── Tab 5: More ─────────────────────────────────────────────────────────

@Composable
private fun MoreTab(nav: NavController, snackbar: SnackbarHostState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("More", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
        }
        item { SectionLabel("TOOLS") }
        item { MoreTile("Password generator", Icons.Rounded.AutoAwesome, Cyan) { nav.navigate("generator") } }
        item { MoreTile("Password health", Icons.Rounded.HealthAndSafety, Mint) { nav.navigate("passwordHealth") } }
        item { MoreTile("Recycle bin", Icons.Rounded.DeleteSweep, Coral) { nav.navigate("recycleBin") } }
        item { MoreTile("Argon2id benchmark", Icons.Rounded.Speed, Violet) { nav.navigate("argon2benchmark") } }

        item { Spacer(Modifier.height(4.dp)); SectionLabel("VAULT SECTIONS") }
        item { MoreTile("Bank accounts", Icons.Rounded.AccountBalance, Mint) { nav.navigate("banks") } }
        item { MoreTile("Documents", Icons.Rounded.Description, Amber) { nav.navigate("docs") } }
        item { MoreTile("Google accounts", Icons.Rounded.AccountCircle, Color(0xFF4285F4)) { nav.navigate("googleAccounts") } }
        item { MoreTile("SBI Rewardz", Icons.Rounded.CardGiftcard, Amber) { nav.navigate("sbiRewardz") } }
        item { MoreTile("CSD Canteen cards", Icons.Rounded.ShoppingBag, Amber) { nav.navigate("csd") } }

        item { Spacer(Modifier.height(4.dp)); SectionLabel("SETTINGS") }
        item { MoreTile("Settings", Icons.Rounded.Settings, TextSecondary) { nav.navigate("settings") } }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MoreTile(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Surface2.copy(alpha = 0.4f),
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(label, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
        }
    }
}
