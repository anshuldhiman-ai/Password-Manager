package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.PasswordGenerator
import com.family.pswdmngr.crypto.Totp
import com.family.pswdmngr.data.EntryCategory
import com.family.pswdmngr.data.VaultEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(nav: NavController, id: Long) {
    val scope = rememberCoroutineScope()
    val isNew = id < 0

    var loaded by remember { mutableStateOf(isNew) }
    var original by remember { mutableStateOf<VaultEntry?>(null) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EntryCategory.LOGIN) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(isNew) }
    var totpError by remember { mutableStateOf(false) }

    val qrScanner = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        if (result is QRResult.QRSuccess) {
            Totp.secretFromUri(result.content.rawValue ?: "")?.let { totp = it; totpError = false }
                ?: run { totpError = true }
        }
    }

    LaunchedEffect(id) {
        if (!isNew && VaultSession.isUnlocked) {
            VaultSession.dao().byId(id)?.let { e ->
                original = e
                title = e.title; category = e.category; username = e.username
                password = e.password; url = e.url; notes = e.notes
                totp = e.totpSecret; favorite = e.favorite
            }
        }
        loaded = true
    }

    if (!loaded) return

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New item" else "Edit item", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Rounded.Close, "Cancel", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { favorite = !favorite }) {
                        Icon(
                            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            "Favorite", tint = if (favorite) Amber else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
        bottomBar = {
            Box(Modifier.padding(22.dp)) {
                GradientButton(
                    "Save",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank(),
                    icon = Icons.Rounded.Check,
                ) {
                    val now = System.currentTimeMillis()
                    val entry = (original ?: VaultEntry(title = "", createdAt = now, updatedAt = now))
                        .copy(
                            title = title.trim(), category = category,
                            username = username.trim(), password = password,
                            url = url.trim(), notes = notes,
                            totpSecret = totp.trim(), favorite = favorite,
                            updatedAt = now,
                        )
                    scope.launch {
                        VaultSession.dao().upsert(entry)
                        nav.popBackStack()
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EntryCategory.entries.toList()) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingIcon = {
                                Icon(categoryIcon(c), null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = categoryColor(c).copy(alpha = 0.22f),
                                selectedLabelColor = categoryColor(c),
                                selectedLeadingIconColor = categoryColor(c),
                                labelColor = TextSecondary,
                                iconColor = TextSecondary,
                            ),
                        )
                    }
                }
            }

            item { VaultTextField(title, { title = it }, "Title *") }
            item { VaultTextField(username, { username = it }, "Username / email") }
            item {
                VaultTextField(
                    password, { password = it }, "Password",
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { password = PasswordGenerator.generate() }) {
                                Icon(Icons.Rounded.AutoAwesome, "Generate", tint = Cyan)
                            }
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    null, tint = TextSecondary
                                )
                            }
                        }
                    },
                )
            }
            item { VaultTextField(url, { url = it }, "Website / app") }
            item {
                VaultTextField(
                    totp, { totp = it; totpError = false }, "2FA secret (TOTP)",
                    trailingIcon = {
                        IconButton(onClick = { qrScanner.launch(null) }) {
                            Icon(Icons.Rounded.QrCodeScanner, "Scan QR", tint = Cyan)
                        }
                    },
                )
                if (totpError) {
                    Text(
                        "QR code is not a valid authenticator code",
                        color = Coral, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (totp.isNotBlank() && !Totp.isValidSecret(totp)) {
                    Text(
                        "Invalid TOTP secret",
                        color = Coral, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item { VaultTextField(notes, { notes = it }, "Notes", singleLine = false) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
