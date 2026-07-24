package com.family.pswdmngr.ui.banks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.VaultApp
import com.family.pswdmngr.data.*
import com.family.pswdmngr.ui.cards.CustomFieldsEditor
import com.family.pswdmngr.ui.cards.SecretRow
import com.family.pswdmngr.ui.cards.bankBrand
import com.family.pswdmngr.ui.cards.horizontalScrollRow
import com.family.pswdmngr.ui.screens.*
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanksScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val banks by VaultSession.bankDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Bank Accounts", color = TextPrimary) },
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
                onClick = { nav.navigate("bankEdit/-1") },
                containerColor = Violet, shape = CircleShape,
            ) { Icon(Icons.Rounded.Add, "Add bank", tint = TextPrimary) }
        },
    ) { pad ->
        if (banks.isEmpty()) {
            EmptyState(
                Icons.Rounded.AccountBalance, Mint,
                "No bank accounts yet",
                "Tap + to add account no, IFSC, netbanking and more.",
                modifier = Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(banks.size, key = { banks[it].id }) { i ->
                val b = banks[i]
                GlassCard(onClick = { nav.navigate("bankDetail/${b.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bk = com.family.pswdmngr.ui.cards.CardCatalog.bankKeyFor(b.bankName)
                        com.family.pswdmngr.ui.cards.BankLogoChip(bk, b.bankName, size = 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.bankName.ifBlank { "Bank" }, color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Surface(
                                    shape = CircleShape,
                                    color = (bk?.let { com.family.pswdmngr.ui.cards.bankBrand(b.bankName).accent }
                                        ?: Cyan).copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        AccountType.label(b.accountType),
                                        color = bk?.let { com.family.pswdmngr.ui.cards.bankBrand(b.bankName).accent } ?: Cyan,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            if (b.accountNumber.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text("••" + b.accountNumber.takeLast(4),
                                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailScreen(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var bank by remember { mutableStateOf<BankEntry?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) { bank = VaultSession.bankDao().byId(id) }
    val b = bank ?: return

    fun copy(label: String, v: String) {
        (ctx.applicationContext as VaultApp).copySecret(label, v)
        scope.launch { snackbar.showSnackbar("$label copied — clears in 30 s") }
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(b.bankName, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("bankEdit/${b.id}") }) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.family.pswdmngr.ui.cards.BankLogoChip(
                        com.family.pswdmngr.ui.cards.CardCatalog.bankKeyFor(b.bankName),
                        b.bankName, size = 52.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(b.bankName, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Text(AccountType.label(b.accountType), color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionLabel("ACCOUNT") }
            if (b.accountHolder.isNotBlank()) item { SecretRow("Account holder", b.accountHolder) { copy("Account holder", b.accountHolder) } }
            if (b.accountNumber.isNotBlank()) item { SecretRow("Account number", b.accountNumber, mono = true) { copy("Account number", b.accountNumber) } }
            item { SecretRow("Account type", AccountType.label(b.accountType)) { copy("Account type", AccountType.label(b.accountType)) } }
            if (b.ifsc.isNotBlank()) item { SecretRow("IFSC code", b.ifsc, mono = true) { copy("IFSC", b.ifsc) } }
            if (b.branch.isNotBlank()) item { SecretRow("Branch", b.branch) { copy("Branch", b.branch) } }
            if (b.micr.isNotBlank()) item { SecretRow("MICR code", b.micr, mono = true) { copy("MICR", b.micr) } }
            if (b.cif.isNotBlank()) item { SecretRow("CIF number", b.cif, mono = true) { copy("CIF", b.cif) } }
            if (b.customerId.isNotBlank()) item { SecretRow("Customer ID", b.customerId, mono = true) { copy("Customer ID", b.customerId) } }
            if (b.registeredMobile.isNotBlank()) item { SecretRow("Registered mobile", b.registeredMobile, mono = true) { copy("Mobile", b.registeredMobile) } }

            val hasNb = b.netbankingUserId.isNotBlank() || b.netbankingPassword.isNotBlank() ||
                b.profilePassword.isNotBlank() || b.transactionPassword.isNotBlank() || b.upiPin.isNotBlank()
            if (hasNb) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("NETBANKING & UPI") }
                if (b.netbankingUserId.isNotBlank()) item { SecretRow("Netbanking user ID", b.netbankingUserId) { copy("User ID", b.netbankingUserId) } }
                if (b.netbankingPassword.isNotBlank()) item { SecretRow("Login password", b.netbankingPassword, secret = true) { copy("Login password", b.netbankingPassword) } }
                if (b.profilePassword.isNotBlank()) item { SecretRow("Profile password", b.profilePassword, secret = true) { copy("Profile password", b.profilePassword) } }
                if (b.transactionPassword.isNotBlank()) item { SecretRow("Transaction password", b.transactionPassword, secret = true) { copy("Transaction password", b.transactionPassword) } }
                if (b.upiPin.isNotBlank()) item { RevealLockField("UPI PIN", b.upiPin, mono = true) { copy("UPI PIN", b.upiPin) } }
            }

            val extras = parseFields(b.fieldsJson)
            if (extras.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("MORE DETAILS") }
                items(extras.size) { i ->
                    val f = extras[i]
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
            title = { Text("Delete this bank account?", color = TextPrimary) },
            text = { Text("This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { VaultSession.bankDao().delete(b); nav.popBackStack() }
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankScreen(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == -1L) }
    var original by remember { mutableStateOf<BankEntry?>(null) }

    var bankName by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("SAVINGS") }
    var ifsc by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var micr by remember { mutableStateOf("") }
    var cif by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf("") }
    var nbUser by remember { mutableStateOf("") }
    var nbPass by remember { mutableStateOf("") }
    var profilePass by remember { mutableStateOf("") }
    var txnPass by remember { mutableStateOf("") }
    var upiPin by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(listOf<CustomField>()) }

    LaunchedEffect(id) {
        if (id != -1L) {
            VaultSession.bankDao().byId(id)?.let { b ->
                original = b
                bankName = b.bankName; accountHolder = b.accountHolder
                accountNumber = b.accountNumber; accountType = b.accountType
                ifsc = b.ifsc; branch = b.branch; micr = b.micr; cif = b.cif
                customerId = b.customerId; nbUser = b.netbankingUserId
                nbPass = b.netbankingPassword; profilePass = b.profilePassword
                txnPass = b.transactionPassword; upiPin = b.upiPin
                mobile = b.registeredMobile
                fields = parseFields(b.fieldsJson)
            }
            loaded = true
        }
    }
    if (!loaded) return

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text(if (id == -1L) "Add bank account" else "Edit bank account", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
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
            item { SectionLabel("SELECT BANK") }
            item { BankPickerRow(bankName) { bankName = it } }
            item { VaultTextField(accountHolder, { accountHolder = it }, "Account holder name") }
            item { VaultTextField(accountNumber, { accountNumber = it.filter { ch -> ch.isDigit() } }, "Account number") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScrollRow()) {
                    AccountType.ALL.forEach { t ->
                        FilterChip(
                            selected = accountType == t, onClick = { accountType = t },
                            label = { Text(AccountType.label(t)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Mint.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VaultTextField(ifsc, { ifsc = it.uppercase().take(11) }, "IFSC code", modifier = Modifier.weight(1f))
                    VaultTextField(micr, { micr = it.filter { ch -> ch.isDigit() }.take(9) }, "MICR", modifier = Modifier.weight(1f))
                }
            }
            item { VaultTextField(branch, { branch = it }, "Branch") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VaultTextField(cif, { cif = it }, "CIF number", modifier = Modifier.weight(1f))
                    VaultTextField(customerId, { customerId = it }, "Customer ID", modifier = Modifier.weight(1f))
                }
            }
            item { VaultTextField(mobile, { mobile = it.filter { ch -> ch.isDigit() || ch == '+' }.take(13) }, "Registered mobile") }

            item { Spacer(Modifier.height(4.dp)); SectionLabel("NETBANKING & UPI") }
            item { VaultTextField(nbUser, { nbUser = it }, "Netbanking user ID") }
            item { VaultTextField(nbPass, { nbPass = it }, "Login password") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VaultTextField(profilePass, { profilePass = it }, "Profile password", modifier = Modifier.weight(1f))
                    VaultTextField(txnPass, { txnPass = it }, "Transaction password", modifier = Modifier.weight(1f))
                }
            }
            item { VaultTextField(upiPin, { upiPin = it.filter { ch -> ch.isDigit() }.take(6) }, "UPI PIN") }

            item { Spacer(Modifier.height(4.dp)); SectionLabel("EXTRA FIELDS") }
            item {
                CustomFieldsEditor(
                    fields, onChange = { fields = it },
                    suggestions = listOf("SBI Rewardz password", "Debit card annual fee", "Nominee", "Locker no", "Cheque book series"),
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    "Save bank account", modifier = Modifier.fillMaxWidth(),
                    enabled = bankName.isNotBlank(),
                ) {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        VaultSession.bankDao().upsert(
                            (original ?: BankEntry(createdAt = now)).copy(
                                id = original?.id ?: 0,
                                bankName = bankName.trim(), accountHolder = accountHolder.trim(),
                                accountNumber = accountNumber, accountType = accountType,
                                ifsc = ifsc.trim(), branch = branch.trim(), micr = micr,
                                cif = cif.trim(), customerId = customerId.trim(),
                                netbankingUserId = nbUser.trim(), netbankingPassword = nbPass,
                                profilePassword = profilePass, transactionPassword = txnPass,
                                upiPin = upiPin, registeredMobile = mobile,
                                fieldsJson = fieldsToJson(fields.filter { it.label.isNotBlank() }),
                                updatedAt = now, createdAt = original?.createdAt ?: now,
                            )
                        )
                        nav.popBackStack()
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

/**
 * Bank chooser — three real-logo tiles (HDFC / ICICI / SBI), no manual typing.
 * A legacy account whose bankName isn't one of these keeps an "Other" tile so
 * old records still open and save without losing their original bank name.
 */
@Composable
fun BankPickerRow(selected: String, onSelect: (String) -> Unit) {
    val keys = com.family.pswdmngr.ui.cards.CardCatalog.banks // HDFC, ICICI, SBI
    val selectedKey = com.family.pswdmngr.ui.cards.CardCatalog.bankKeyFor(selected)
    val legacyOther = selected.isNotBlank() && selectedKey == null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.forEach { key ->
                val isSel = selectedKey == key
                Column(
                    Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(if (isSel) Violet.copy(alpha = 0.18f) else Surface2.copy(alpha = 0.4f))
                        .border(
                            1.dp,
                            if (isSel) Violet else Stroke,
                            androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelect(com.family.pswdmngr.ui.cards.CardCatalog.bankDisplay(key)) }
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.family.pswdmngr.ui.cards.BankLogoChip(key, key, size = 44.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        com.family.pswdmngr.ui.cards.CardCatalog.bankDisplay(key),
                        color = if (isSel) TextPrimary else TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        if (legacyOther) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(Amber.copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AccountBalance, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Other: $selected", color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
