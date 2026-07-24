package com.family.pswdmngr.ui.cards

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.family.pswdmngr.VaultApp
import com.family.pswdmngr.data.*
import com.family.pswdmngr.ui.screens.*
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

/* ================= Cards list, grouped by bank ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val cards by VaultSession.cardDao().observeAll().collectAsState(initial = emptyList())
    val grouped = cards.filter { it.cardType != CardType.CSD }
        .groupBy { it.bankName.trim().ifBlank { "Bank card" } }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Cards", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.navigate("cardEdit/-1") },
                containerColor = Violet, shape = CircleShape,
            ) { Icon(Icons.Rounded.Add, "Add card", tint = TextPrimary) }
        },
    ) { pad ->
        if (cards.isEmpty()) {
            EmptyState(
                Icons.Rounded.CreditCard, Cyan,
                "No cards yet",
                "Tap + to add a debit or credit card with its real bank design.",
                modifier = Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            grouped.forEach { (bank, bankCards) ->
                item(key = "hdr_$bank") {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceGlass,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val bk = CardCatalog.bankKeyFor(bank)
                            val brand = bankBrand(bank)
                            if (bk != null) {
                                BankLogoChip(bk, bank, size = 26.dp)
                            } else {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape)
                                        .background(brand.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.AccountBalance, null,
                                        tint = brand.accent, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bank, color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium)
                                Text("${bankCards.size} card${if (bankCards.size > 1) "s" else ""}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(
                                shape = CircleShape,
                                color = brand.accent.copy(alpha = 0.15f),
                            ) {
                                Text("${bankCards.size}",
                                    color = brand.accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                items(bankCards.size, key = { i -> "card_${bankCards[i].id}" }) { i ->
                    val card = bankCards[i]
                    CardFace(
                        card,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { nav.navigate("cardDetail/${card.id}") },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

/* ================= Card detail ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var card by remember { mutableStateOf<CardEntry?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) { card = VaultSession.cardDao().byId(id) }
    val c = card ?: return

    fun copy(label: String, v: String) {
        (ctx.applicationContext as VaultApp).copySecret(label, v)
        scope.launch { snackbar.showSnackbar("$label copied — clears in 30 s") }
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(c.label.ifBlank { CardType.label(c.cardType) }, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("cardEdit/${c.id}") }) {
                        Icon(Icons.Rounded.Edit, "Edit", tint = Cyan)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, "Delete", tint = Coral)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardFace(c, revealNumber = reveal,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { reveal = !reveal })
                Spacer(Modifier.height(4.dp))
                Text(
                    if (reveal) "Tap card to hide number" else "Tap card to reveal number",
                    color = TextSecondary, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SecretRow("Card number", groupDigits(c.number), mono = true) { copy("Card number", c.number.filter { it.isDigit() }) }
            }
            if (c.holder.isNotBlank()) item { SecretRow("Name on card", c.holder) { copy("Name", c.holder) } }
            if (c.expiry.isNotBlank()) item { SecretRow("Valid thru", c.expiry, mono = true) { copy("Expiry", c.expiry) } }
            if (c.cvv.isNotBlank()) item {
                RevealLockField("CVV", c.cvv, mono = true) { copy("CVV", c.cvv) }
            }
            if (c.pin.isNotBlank()) item {
                RevealLockField("ATM PIN", c.pin, mono = true) { copy("PIN", c.pin) }
            }
            if (c.serialNo.isNotBlank()) item { SecretRow("Serial no", c.serialNo, mono = true) { copy("Serial no", c.serialNo) } }

            val extras = parseFields(c.fieldsJson)
            if (extras.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("MORE DETAILS") }
                items(extras.size) { i ->
                    val f = extras[i]
                    // Use reveal lock for any custom field marked as secret
                    if (f.secret) {
                        RevealLockField(f.label, f.value) { copy(f.label, f.value) }
                    } else {
                        SecretRow(f.label, f.value, secret = f.secret) { copy(f.label, f.value) }
                    }
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Delete this card?", color = TextPrimary) },
            text = { Text("This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        VaultSession.cardDao().delete(c)
                        nav.popBackStack()
                    }
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

/** A labeled row with hide/show for secrets and one-tap copy — premium glass style. */
@Composable
fun SecretRow(
    label: String,
    value: String,
    secret: Boolean = false,
    mono: Boolean = false,
    onCopy: () -> Unit,
) {
    var shown by remember { mutableStateOf(!secret) }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Cyan.copy(alpha = 0.10f),
                    ) {
                        Text(label, color = Cyan, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (shown) value else "•".repeat(value.length.coerceIn(4, 10)),
                    color = TextPrimary,
                    style = (if (mono) MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp)
                    else MaterialTheme.typography.titleMedium),
                )
            }
            if (secret) {
                IconButton(onClick = { shown = !shown }) {
                    Icon(
                        if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        null, tint = TextSecondary, modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, "Copy", tint = Cyan)
            }
        }
    }
}

/* ================= Card editor ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(nav: NavController, id: Long, fixedType: String? = null) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == -1L) }

    var label by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var cardType by remember { mutableStateOf(fixedType ?: CardType.DEBIT) }
    var network by remember { mutableStateOf("AUTO") }
    var productId by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var serialNo by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(listOf<CustomField>()) }
    var original by remember { mutableStateOf<CardEntry?>(null) }
    var showExpiryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(fixedType) {
        if (fixedType == CardType.CSD && id == -1L) bankName = "CSD Canteen"
    }

    LaunchedEffect(id) {
        if (id != -1L) {
            VaultSession.cardDao().byId(id)?.let { c ->
                original = c
                label = c.label; bankName = c.bankName; cardType = fixedType ?: c.cardType
                network = c.network; productId = c.productId
                number = c.number.filter { it.isDigit() }.chunked(4).joinToString(" ")
                holder = c.holder
                expiry = c.expiry; cvv = c.cvv; pin = c.pin; serialNo = c.serialNo
                fields = parseFields(c.fieldsJson)
            }
            loaded = true
        }
    }
    if (!loaded) return

    // Handle scanned card data from CardScannerScreen (runs on every recomposition
    // so it catches data when coming back from the scanner)
    var scanHandled by remember { mutableStateOf(false) }
    if (!scanHandled) {
        val handle = nav.currentBackStackEntry?.savedStateHandle
        val scanned = handle?.get<String>("scanned_number")
        if (scanned != null) {
            scanHandled = true
            LaunchedEffect(scanHandled) {
                number = scanned.filter { it.isDigit() }.chunked(4).joinToString(" ")
                handle.get<String>("scanned_expiry")?.let { if (it.isNotBlank()) expiry = it }
                handle.get<String>("scanned_bank")?.let { if (it.isNotBlank()) bankName = it }
                handle.get<String>("scanned_network")?.let { if (it.isNotBlank()) network = it }
                handle.remove<String>("scanned_number")
                handle.remove<String>("scanned_expiry")
                handle.remove<String>("scanned_bank")
                handle.remove<String>("scanned_network")
            }
        }
    }

    // Save handler shared between the tick mark and CSD flow
    fun saveCard() {
        scope.launch {
            val now = System.currentTimeMillis()
            VaultSession.cardDao().upsert(
                (original ?: CardEntry(createdAt = now)).copy(
                    id = original?.id ?: 0,
                    label = label.trim(), bankName = bankName.trim(),
                    cardType = cardType, network = network, productId = productId,
                    number = number.filter { it.isDigit() },
                    holder = holder.trim().uppercase(), expiry = expiry, cvv = cvv,
                    pin = pin, serialNo = serialNo.trim(),
                    fieldsJson = fieldsToJson(fields.filter { it.label.isNotBlank() }),
                    updatedAt = now, createdAt = original?.createdAt ?: now,
                )
            )
            nav.popBackStack()
        }
    }

    // Which catalog bank is selected (null = other / free-typed bank)
    val selectedBankKey = if (fixedType == null)
        CardCatalog.byId(productId)?.bank ?: CardCatalog.bankKeyFor(bankName) else null
    val products = selectedBankKey?.let { CardCatalog.productsFor(it, cardType) } ?: emptyList()

    val preview = CardEntry(
        label = label, bankName = bankName, cardType = cardType, network = network,
        productId = productId, number = number, holder = holder, expiry = expiry,
        serialNo = serialNo,
    )

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text(
                    if (fixedType == CardType.CSD) if (id == -1L) "Add CSD card" else "Edit CSD card"
                    else if (id == -1L) "Add card" else "Edit card",
                    color = TextPrimary,
                ) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (fixedType != CardType.CSD) {
                        IconButton(onClick = { nav.navigate("cardScan") }) {
                            Icon(Icons.Rounded.PhotoCamera, "Scan card", tint = Cyan)
                        }
                    }
                    IconButton(
                        onClick = { saveCard() },
                        enabled = number.isNotBlank() ||
                            (fixedType == CardType.CSD && serialNo.isNotBlank()),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            "Save",
                            tint = if (number.isNotBlank() || (fixedType == CardType.CSD && serialNo.isNotBlank()))
                                Mint else TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // live preview updates as you type / pick a product
                AnimatedContent(
                    targetState = preview,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "cardPreview",
                ) { p -> CardFace(p, revealNumber = true) }
            }

            if (fixedType == null) {
            item { SectionLabel("SELECT BANK") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScrollRow()) {
                    CardCatalog.banks.forEach { bk ->
                        FilterChip(
                            selected = selectedBankKey == bk,
                            onClick = {
                                bankName = CardCatalog.bankDisplay(bk)
                                if (CardCatalog.byId(productId)?.bank != bk) productId = ""
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BankLogoChip(bk, CardCatalog.bankDisplay(bk), size = 28.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(CardCatalog.bankDisplay(bk))
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Violet.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
            }

            if (fixedType == null) {
            item { SectionLabel("CARD TYPE") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScrollRow()) {
                    CardType.ALL.forEach { t ->
                        FilterChip(
                            selected = cardType == t,
                            onClick = {
                                cardType = t
                                // product belongs to the old type — clear it
                                if (CardCatalog.byId(productId)?.type != t) productId = ""
                            },
                            label = { Text(CardType.label(t)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Violet.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
            }

            // Product picker: the real cards this bank issues for this type
            if (fixedType == null && products.isNotEmpty()) {
                item { SectionLabel("WHICH CARD?") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScrollRow()) {
                        products.forEach { p ->
                            FilterChip(
                                selected = productId == p.id,
                                onClick = { productId = if (productId == p.id) "" else p.id },
                                label = { Text(p.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = p.accent.copy(alpha = 0.28f),
                                    selectedLabelColor = TextPrimary,
                                    labelColor = TextSecondary,
                                ),
                            )
                        }
                    }
                }
            }

            item { VaultTextField(label, { label = it }, "Nickname (optional, e.g. Salary card)") }
            item {
                VaultTextField(
                    number,
                    { input ->
                        // digits only, max 16, auto-grouped in blocks of 4
                        val digits = input.filter { ch -> ch.isDigit() }.take(16)
                        number = digits.chunked(4).joinToString(" ")
                    },
                    "Card number (16 digits)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                val det = CardNetwork.detect(number)
                if (network == "AUTO" && det != CardNetwork.UNKNOWN) {
                    Spacer(Modifier.height(4.dp))
                    Text("Detected: ${det.display}", color = Mint,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            item { SectionLabel("NETWORK") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScrollRow()) {
                    FilterChip(
                        selected = network == "AUTO", onClick = { network = "AUTO" },
                        label = { Text("Auto-detect") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan.copy(alpha = 0.25f),
                            selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                        ),
                    )
                    CardNetwork.selectable.forEach { n ->
                        FilterChip(
                            selected = network == n.name, onClick = { network = n.name },
                            label = { Text(n.display) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }

            item {
                // embossed names are always capitals — force it while typing
                VaultTextField(holder, { holder = it.uppercase() }, "Name on card",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Valid-thru is picked from a month/year sheet — no manual typing
                    Box(Modifier.weight(1f)) {
                        VaultTextField(expiry, { }, "Valid thru", readOnly = true,
                            trailingIcon = {
                                Icon(Icons.Rounded.EditCalendar, null, tint = Cyan)
                            })
                        Box(
                            Modifier.matchParentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showExpiryPicker = true })
                    }
                    VaultTextField(
                        cvv, { cvv = it.filter { ch -> ch.isDigit() }.take(4) },
                        "CVV", modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    )
                }
            }
            item {
                if (fixedType == CardType.CSD) {
                    // CSD-only fields: serial number lives here, never in the bank-card editor
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VaultTextField(
                            pin, { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                            "PIN", modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        )
                        VaultTextField(serialNo, { serialNo = it }, "Serial no", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showExpiryPicker) {
        ExpiryPickerDialog(
            initial = expiry,
            onDismiss = { showExpiryPicker = false },
            onPick = { mmYY -> expiry = mmYY; showExpiryPicker = false },
        )
    }
}

/** Valid-thru picker: choose the year, then its month — no manual entry. */
@Composable
fun ExpiryPickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val cal = java.util.Calendar.getInstance()
    val thisYear = cal.get(java.util.Calendar.YEAR)
    val initYear = initial.substringAfter('/', "").toIntOrNull()?.let { 2000 + it }
    var year by remember { mutableStateOf(initYear ?: thisYear) }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val selMonth = if (initYear == year) initial.substringBefore('/').toIntOrNull() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Valid thru", color = TextPrimary) },
        text = {
            Column {
                // year strip: cards are valid up to ~10 years out
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScrollRow()) {
                    (thisYear..thisYear + 10).forEach { y ->
                        FilterChip(
                            selected = year == y, onClick = { year = y },
                            label = { Text(y.toString()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Violet.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // month grid for the chosen year
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.chunked(4).forEachIndexed { rowIdx, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { colIdx, m ->
                                val mNo = rowIdx * 4 + colIdx + 1
                                FilterChip(
                                    selected = selMonth == mNo,
                                    onClick = {
                                        onPick("%02d/%02d".format(mNo, year % 100))
                                    },
                                    label = { Text(m) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Cyan.copy(alpha = 0.25f),
                                        selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

/** Reusable add-your-own-fields editor with one-tap suggestion chips. */
@Composable
fun CustomFieldsEditor(
    fields: List<CustomField>,
    onChange: (List<CustomField>) -> Unit,
    suggestions: List<String> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fields.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultTextField(
                    f.label,
                    { new -> onChange(fields.toMutableList().also { it[i] = f.copy(label = new) }) },
                    "Field", modifier = Modifier.weight(0.85f),
                )
                VaultTextField(
                    f.value,
                    { new -> onChange(fields.toMutableList().also { it[i] = f.copy(value = new) }) },
                    "Value", modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onChange(fields.toMutableList().also { it[i] = f.copy(secret = !f.secret) }) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (f.secret) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            if (f.secret) "Secret field" else "Visible field",
                            tint = if (f.secret) Amber else TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onChange(fields.toMutableList().also { it.removeAt(i) }) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Rounded.Close, "Remove field", tint = Coral,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        val unused = suggestions.filter { s -> fields.none { it.label.equals(s, true) } }
        if (unused.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScrollRow(),
            ) {
                unused.forEach { s ->
                    AssistChip(
                        onClick = { onChange(fields + CustomField(s, "", secret = "password" in s.lowercase() || "pin" in s.lowercase())) },
                        label = { Text(s) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(15.dp), tint = Cyan) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = TextSecondary),
                    )
                }
            }
        }

        TextButton(onClick = { onChange(fields + CustomField("", "", false)) }) {
            Icon(Icons.Rounded.Add, null, tint = Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add custom field", color = Cyan)
        }
    }
}

/** Horizontally scrollable row modifier used by chip rows. */
@Composable
fun Modifier.horizontalScrollRow(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))
