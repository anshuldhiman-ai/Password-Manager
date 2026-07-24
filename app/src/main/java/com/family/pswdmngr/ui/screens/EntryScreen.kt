package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.VaultApp
import com.family.pswdmngr.crypto.Totp
import com.family.pswdmngr.data.VaultEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as VaultApp
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<VaultEntry?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(id) {
        if (VaultSession.isUnlocked) entry = VaultSession.dao().byId(id)
        if (entry == null) nav.popBackStack()
    }

    val e = entry
    if (e == null) {
        // brief DB load — show the themed shell instead of a blank flash
        Scaffold(containerColor = Midnight) { pad ->
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Violet)
            }
        }
        return
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("edit/${e.id}") }) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    IconBadge(categoryIcon(e.category), categoryColor(e.category), size = 72)
                    Spacer(Modifier.height(12.dp))
                    Text(e.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    if (e.url.isNotBlank()) {
                        Text(e.url, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (e.username.isNotBlank()) item {
                FieldCard("USERNAME", e.username, Icons.Rounded.Person) {
                    app.copySecret("username", e.username)
                    scope.launch { snackbar.showSnackbar("Username copied — clears in 30s") }
                }
            }

            if (e.password.isNotBlank()) item {
                FieldCard(
                    "PASSWORD",
                    if (showPassword) e.password else "•".repeat(e.password.length.coerceAtMost(14)),
                    Icons.Rounded.Key,
                    mono = true,
                    extraIcon = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    onExtra = { showPassword = !showPassword },
                ) {
                    app.copySecret("password", e.password)
                    scope.launch { snackbar.showSnackbar("Password copied — clears in 30s") }
                }
            }

            if (e.totpSecret.isNotBlank()) item {
                TotpCard(e.totpSecret) { code ->
                    app.copySecret("totp", code)
                    scope.launch { snackbar.showSnackbar("Code copied — clears in 30s") }
                }
            }

            if (e.notes.isNotBlank()) item {
                GlassCard {
                    SectionLabel("NOTES")
                    Spacer(Modifier.height(6.dp))
                    Text(e.notes, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Delete \"${e.title}\"?", color = TextPrimary) },
            text = { Text("This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        VaultSession.dao().delete(e)
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

@Composable
private fun FieldCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    mono: Boolean = false,
    extraIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onExtra: (() -> Unit)? = null,
    onCopy: () -> Unit,
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(label)
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    color = TextPrimary,
                    style = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyLarge,
                )
            }
            extraIcon?.let {
                IconButton(onClick = { onExtra?.invoke() }) {
                    Icon(it, null, tint = TextSecondary)
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, "Copy", tint = Violet)
            }
        }
    }
}

@Composable
private fun TotpCard(secret: String, onCopy: (String) -> Unit) {
    // A hand-typed secret can be malformed base32 — never let it crash the screen
    var code by remember { mutableStateOf(runCatching { Totp.code(secret) }.getOrNull()) }
    var left by remember { mutableStateOf(Totp.secondsLeft()) }

    LaunchedEffect(secret) {
        while (true) {
            code = runCatching { Totp.code(secret) }.getOrNull()
            left = Totp.secondsLeft()
            delay(1000)
        }
    }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val c = code
            if (c == null) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Coral, modifier = Modifier.size(38.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel("2FA CODE")
                    Text("Invalid 2FA secret — edit this item to fix it",
                        color = Coral, style = MaterialTheme.typography.bodyMedium)
                }
                return@Row
            }
            CircularProgressIndicator(
                progress = { left / 30f },
                modifier = Modifier.size(38.dp),
                color = if (left <= 5) Coral else Mint,
                trackColor = Surface2,
                strokeWidth = 4.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel("2FA CODE")
                Text(
                    "${c.take(3)} ${c.drop(3)}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (left <= 5) Coral else TextPrimary,
                )
            }
            IconButton(onClick = { onCopy(c) }) {
                Icon(Icons.Rounded.ContentCopy, "Copy", tint = Violet)
            }
        }
    }
}
