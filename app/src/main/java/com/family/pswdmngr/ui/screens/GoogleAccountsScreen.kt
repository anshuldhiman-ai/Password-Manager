package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.Totp
import com.family.pswdmngr.data.EntryCategory
import com.family.pswdmngr.data.VaultEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.cards.GoogleLogo
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

private const val GOOGLE_URL = "https://accounts.google.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAccountsScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val entries by VaultSession.dao().observeAll().collectAsState(initial = emptyList())
    val accounts = entries.filter { it.url == GOOGLE_URL }
    Scaffold(containerColor = Midnight,
        topBar = { TopAppBar(title = { Text("Google accounts", color = TextPrimary) }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight)) },
        floatingActionButton = { FloatingActionButton(onClick = { nav.navigate("googleEdit/-1") }, containerColor = Color.White,
            shape = CircleShape) { GoogleLogo(size = 24.dp) } }) { pad ->
        if (accounts.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { GoogleLogo(size = 36.dp) }
                Spacer(Modifier.height(16.dp)); Text("No Google accounts yet", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp)); Text("Save a Google email, password and optional 2FA code — shown with the real Google logo.", color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionLabel("YOUR GOOGLE ACCOUNTS") }
            items(accounts.size, key = { accounts[it].id }) { i ->
                val a = accounts[i]
                GlassCard(onClick = { nav.navigate("googleEdit/${a.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { GoogleLogo(size = 24.dp) }
                        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                            Text(a.title.ifBlank { "Google account" }, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(a.username, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (a.totpSecret.isNotBlank()) Icon(Icons.Rounded.Key, "2FA enabled", tint = Mint, modifier = Modifier.size(18.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAccountEditor(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == -1L) }
    var original by remember { mutableStateOf<VaultEntry?>(null) }
    var label by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var totp by remember { mutableStateOf("") }; var showPassword by remember { mutableStateOf(false) }
    LaunchedEffect(id) { if (id != -1L) { original = VaultSession.dao().byId(id); original?.let { label = it.title; email = it.username; password = it.password; totp = it.totpSecret }; loaded = true } }
    if (!loaded) return
    Scaffold(containerColor = Midnight, topBar = { TopAppBar(title = { Text(if (id == -1L) "Add Google account" else "Edit Google account", color = TextPrimary) }, navigationIcon = {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary) }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight)) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard { Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { GoogleLogo(size = 27.dp) }
                Spacer(Modifier.width(14.dp)); Column { Text("Google", color = TextPrimary, style = MaterialTheme.typography.titleMedium); Text("Account credentials", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
            } }
            VaultTextField(label, { label = it }, "Account label (e.g. Personal)")
            VaultTextField(email, { email = it.trim() }, "Google email address")
            VaultTextField(password, { password = it }, "Password", visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show", color = Cyan) }
            })
            VaultTextField(totp, { totp = it.trim().uppercase() }, "2FA secret (optional)")
            if (totp.isNotBlank() && !Totp.isValidSecret(totp)) Text("Enter a valid Base32 TOTP secret.", color = Coral, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f)); GradientButton("Save Google account", Modifier.fillMaxWidth(), enabled = email.isNotBlank() && (totp.isBlank() || Totp.isValidSecret(totp))) {
                scope.launch { val now = System.currentTimeMillis(); VaultSession.dao().upsert((original ?: VaultEntry(title = "Google account", createdAt = now, updatedAt = now)).copy(title = label.trim().ifBlank { "Google account" }, category = EntryCategory.LOGIN, username = email, password = password, totpSecret = totp, url = GOOGLE_URL, updatedAt = now)); nav.popBackStack() }
            }
        }
    }
}
