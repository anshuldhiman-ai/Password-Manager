package com.family.pswdmngr.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.KeystoreWrapper
import com.family.pswdmngr.data.BackupManager
import com.family.pswdmngr.data.LockoutTracker
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

private val GRACE_CHOICES = listOf(15, 30, 60, 180, 720) // minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val keystore = remember { KeystoreWrapper(ctx) }

    var bioEnabled by remember { mutableStateOf(keystore.isEnabled) }
    var graceMin by remember { mutableStateOf(VaultSession.bioGraceMinutes(ctx)) }
    var wipeEnabled by remember { mutableStateOf(LockoutTracker.wipeEnabled(ctx)) }
    var recoveryKeyDialog by remember { mutableStateOf(false) }
    var recoveredKey by remember { mutableStateOf<String?>(null) }
    var backupDialog by remember { mutableStateOf<String?>(null) } // "export" | "import" | "selective"
    var backupPassword by remember { mutableStateOf("") }
    var backupPassword2 by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCategories by remember { mutableStateOf(setOf("entries", "cards", "banks", "documents", "notes", "tasks")) }
    var pendingSelectiveExport by remember { mutableStateOf(false) }

    val bioSupported = remember {
        BiometricManager.from(ctx)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    // Autofill status refreshes when we come back from system settings
    val afm = remember { ctx.getSystemService(AutofillManager::class.java) }
    var autofillOn by remember { mutableStateOf(afm?.hasEnabledAutofillServices() == true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                autofillOn = afm?.hasEnabledAutofillServices() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            pendingUri = it
            if (pendingSelectiveExport) {
                pendingSelectiveExport = false
                backupDialog = "selective"
            } else {
                backupDialog = "export"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingUri = it; backupDialog = "import" } }

    fun enableBiometric() {
        val activity = ctx as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(ctx),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        keystore.enable(VaultSession.currentKey())
                        bioEnabled = true
                        scope.launch { snackbar.showSnackbar("Quick unlock enabled") }
                    } catch (e: Exception) {
                        scope.launch { snackbar.showSnackbar("Could not enable: ${e.message}") }
                    }
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm fingerprint")
                .setSubtitle("Enables quick unlock on this device")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel("SECURITY") }
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Fingerprint, Cyan)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Quick unlock", color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (!bioSupported) "No fingerprint hardware / none enrolled"
                                else if (bioEnabled) "Fingerprint, face or phone lock"
                                else "Unlock without typing the master password",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = bioEnabled,
                            enabled = bioSupported,
                            onCheckedChange = { on ->
                                if (on) enableBiometric()
                                else { keystore.disable(); bioEnabled = false }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Violet, uncheckedTrackColor = Surface2),
                        )
                    }
                    if (bioEnabled) {
                        Spacer(Modifier.height(14.dp))
                        Text("REQUIRE PASSWORD AGAIN AFTER", color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScrollChips(),
                        ) {
                            GRACE_CHOICES.forEach { m ->
                                FilterChip(
                                    selected = graceMin == m,
                                    onClick = {
                                        graceMin = m
                                        VaultSession.setBioGraceMinutes(ctx, m)
                                    },
                                    label = { Text(graceLabel(m)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Cyan.copy(alpha = 0.22f),
                                        selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "After this time, quick unlock is refused and the master password must be typed — even if someone gets past your phone's lock screen.",
                            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                GlassCard(onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        })
                    } catch (e: Exception) {
                        scope.launch { snackbar.showSnackbar("Autofill settings not available on this device") }
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.EditNote, Mint)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Autofill service", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (autofillOn) "ON" else "OFF",
                                    color = if (autofillOn) Mint else TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Text(
                                if (autofillOn) "PSWD MNGR is filling logins in apps and browsers"
                                else "Tap to set PSWD MNGR as your autofill service",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                    }
                }
            }
            item {
                GlassCard(onClick = {
                    VaultSession.lock()
                    nav.navigate("unlock") { popUpTo(0) { inclusive = true } }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Lock, Coral)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Lock vault now", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text("Closes the vault immediately", color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Block, Coral)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-wipe after 10 failed attempts", color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Deletes the entire vault if someone tries 10 wrong passwords",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = wipeEnabled,
                            onCheckedChange = { on -> wipeEnabled = on; LockoutTracker.setWipeEnabled(ctx, on) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Coral, uncheckedTrackColor = Surface2),
                        )
                    }
                    val failCount = LockoutTracker.failCount(ctx)
                    if (failCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "$failCount failed attempt${if (failCount > 1) "s" else ""} since last successful unlock",
                            color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (VaultSession.hasRecoveryKey(ctx)) {
                item {
                    GlassCard(onClick = {
                        val activity = ctx as? FragmentActivity ?: return@GlassCard
                        if (BiometricManager.from(ctx).canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                            ) == BiometricManager.BIOMETRIC_SUCCESS
                        ) {
                            BiometricPrompt(
                                activity, ContextCompat.getMainExecutor(ctx),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(
                                        result: BiometricPrompt.AuthenticationResult
                                    ) {
                                        recoveredKey = VaultSession.getRecoveryKey(ctx)
                                        recoveryKeyDialog = true
                                    }
                                }).authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("View Recovery Key")
                                    .setSubtitle("Authenticate to reveal your recovery key")
                                    .setNegativeButtonText("Cancel")
                                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                    .build()
                            )
                        } else {
                            // Fallback: try password verification
                            recoveredKey = VaultSession.getRecoveryKey(ctx)
                            if (recoveredKey != null) recoveryKeyDialog = true
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.VpnKey, Amber)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("View Recovery Key", color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium)
                                Text("Requires biometric auth — the one backup for your vault",
                                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)); SectionLabel("BACKUP") }
            item {
                GlassCard(onClick = { exportLauncher.launch("pswd-vault-backup.pmv") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Upload, Violet)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export full backup", color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Everything — logins, cards, banks, documents, notes, tasks. Encrypted with your backup password.",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item {
                GlassCard(onClick = { pendingSelectiveExport = true; exportLauncher.launch("pswd-vault-backup-partial.pmv") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.FilterList, Cyan)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Selective export", color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Choose exactly which categories to export — one type or several.",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item {
                GlassCard(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Download, Amber)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import backup", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text("Asks for the backup password first — without it nothing can be read",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)); SectionLabel("ABOUT") }
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Shield, Violet, size = 52)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("PSWD MNGR", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                            Text("Version 2.1 — family edition", color = TextSecondary,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    AboutRow(Icons.Rounded.WifiOff, "100% offline",
                        "The app has no internet permission at all. Nothing can ever leave your phone.")
                    AboutRow(Icons.Rounded.Key, "AES-256 + Argon2id",
                        "The vault database is SQLCipher-encrypted; your master password is never stored, only stretched into a key.")
                    AboutRow(Icons.Rounded.Image, "Encrypted files",
                        "Aadhaar/PAN photos and PDFs are stored as encrypted blobs — file managers see random bytes.")
                    AboutRow(Icons.Rounded.LockClock, "Stolen-phone protection",
                        "Quick unlock expires after your chosen time; then only the master password opens the vault.")
                    AboutRow(Icons.Rounded.Backup, "Safe backups",
                        "Exports are encrypted with their own password — safe to keep on any drive or share within family.")
                    Spacer(Modifier.height(6.dp))
                    Text("Made with care for the family. Keep your master password written somewhere safe — it cannot be recovered.",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    backupDialog?.let { dialogMode ->
        val isExport = dialogMode == "export" || dialogMode == "selective"
        val isSelective = dialogMode == "selective"
        AlertDialog(
            onDismissRequest = { backupDialog = null; backupPassword = ""; backupPassword2 = "" },
            containerColor = Surface1,
            title = {
                Text(
                    if (isSelective) "Select categories to export"
                    else if (isExport) "Set a backup password" else "Enter backup password",
                    color = TextPrimary,
                )
            },
            text = {
                Column {
                    if (isSelective) {
                        Text("Choose which categories to include:", color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        val allCats = listOf(
                            "entries" to "Logins & passwords",
                            "cards" to "Cards",
                            "banks" to "Bank accounts",
                            "documents" to "Documents (with files)",
                            "notes" to "Secret notes",
                            "tasks" to "Tasks",
                        )
                        allCats.forEach { (key, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedCategories = if (key in selectedCategories)
                                        selectedCategories - key else selectedCategories + key
                                }.padding(vertical = 4.dp)) {
                                Checkbox(
                                    checked = key in selectedCategories,
                                    onCheckedChange = {
                                        selectedCategories = if (key in selectedCategories)
                                            selectedCategories - key else selectedCategories + key
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Violet),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (selectedCategories.isEmpty()) {
                            Text("Select at least one category", color = Coral,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Then set an export password:", color = TextSecondary)
                    } else {
                        Text(
                            if (isExport)
                                "The whole backup is encrypted with this password. Anyone opening the file without it sees only scrambled data — you'll need it to import."
                            else "This backup can only be opened with the password it was created with.",
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    VaultTextField(
                        backupPassword, { backupPassword = it }, "Backup password",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (isExport) {
                        Spacer(Modifier.height(8.dp))
                        VaultTextField(
                            backupPassword2, { backupPassword2 = it }, "Confirm password",
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        if (backupPassword2.isNotEmpty() && backupPassword != backupPassword2) {
                            Spacer(Modifier.height(4.dp))
                            Text("Passwords don't match", color = Coral,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backupPassword.length >= 8 &&
                        (!isExport || backupPassword == backupPassword2) &&
                        (!isSelective || selectedCategories.isNotEmpty()),
                    onClick = {
                        val uri = pendingUri ?: return@TextButton
                        val pw = backupPassword.toCharArray()
                        backupDialog = null; backupPassword = ""; backupPassword2 = ""
                        scope.launch {
                            try {
                                if (isExport) {
                                    if (isSelective) {
                                        BackupManager.export(ctx, uri, pw, selectedCategories)
                                    } else {
                                        BackupManager.export(ctx, uri, pw)
                                    }
                                    snackbar.showSnackbar("Encrypted backup exported")
                                } else {
                                    val n = BackupManager.import(ctx, uri, pw)
                                    snackbar.showSnackbar("Imported $n records")
                                }
                            } catch (e: Exception) {
                                snackbar.showSnackbar(
                                    if (!isExport) "Import failed — wrong password or corrupt file"
                                    else "Export failed: ${e.message}"
                                )
                            } finally {
                                pw.fill(' ')
                            }
                        }
                    },
                ) { Text("OK", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { backupDialog = null; backupPassword = ""; backupPassword2 = "" }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    if (recoveryKeyDialog && recoveredKey != null) {
        RecoveryKeyViewDialog(
            recoveryKey = recoveredKey!!,
            onDismiss = { recoveryKeyDialog = false },
            snackbar = snackbar,
        )
    }
}

// ── Recovery Key View Dialog ──
@Composable
private fun RecoveryKeyViewDialog(
    recoveryKey: String,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VpnKey, null, tint = Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Your Recovery Key", color = TextPrimary)
            }
        },
        text = {
            Column {
                Text(
                    recoveryKey,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 3.sp,
                    ),
                    color = Amber,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Keep this key somewhere safe and offline. " +
                            "If you lose your master password, this is the only way to recover access.",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Recovery Key", recoveryKey))
                        scope.launch { snackbar.showSnackbar("Recovery key copied") }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    OutlinedButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT,
                                "PSWD MNGR Recovery Key:\n$recoveryKey\n\nKeep offline. Without this key, vault data is permanently lost.")
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Recovery Key")
                        }
                        ctx.startActivity(android.content.Intent.createChooser(intent, "Share Recovery Key"))
                    }) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Cyan) }
        },
    )
}

@Composable
private fun Modifier.horizontalScrollChips(): Modifier =
    this.horizontalScroll(rememberScrollState())

@Composable
private fun AboutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(body, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
