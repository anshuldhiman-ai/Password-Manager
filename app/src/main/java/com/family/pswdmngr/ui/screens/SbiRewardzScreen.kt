package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.family.pswdmngr.VaultApp
import com.family.pswdmngr.data.EntryCategory
import com.family.pswdmngr.data.VaultEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.cards.BankLogoChip
import com.family.pswdmngr.ui.cards.CardCatalog
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

/** Marker title so the Rewardz record is found again on next open. */
private const val REWARDZ_TITLE = "SBI Rewardz"

/**
 * SBI Rewardz — just the two login fields (User ID + Password),
 * saved as a single vault entry that this screen always re-opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SbiRewardzScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<VaultEntry?>(null) }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        original = VaultSession.dao().allOnce().firstOrNull {
            it.title.equals(REWARDZ_TITLE, ignoreCase = true)
        }
        original?.let { userId = it.username; password = it.password }
        loaded = true
    }
    if (!loaded) return

    fun copy(label: String, v: String) {
        (ctx.applicationContext as VaultApp).copySecret(label, v)
        scope.launch { snackbar.showSnackbar("$label copied — clears in 30 s") }
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("SBI Rewardz", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header card with the real SBI mark
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankLogoChip(CardCatalog.BANK_SBI, "SBI", size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("SBI Rewardz", color = TextPrimary,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("rewardz.sbi login", color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            VaultTextField(userId, { userId = it }, "User ID",
                trailingIcon = {
                    if (userId.isNotBlank()) IconButton(onClick = { copy("User ID", userId) }) {
                        Icon(Icons.Rounded.ContentCopy, "Copy", tint = Cyan)
                    }
                })
            VaultTextField(
                password, { password = it }, "Password",
                visualTransformation = if (showPw) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(
                                if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                null, tint = TextSecondary,
                            )
                        }
                        if (password.isNotBlank()) IconButton(onClick = { copy("Password", password) }) {
                            Icon(Icons.Rounded.ContentCopy, "Copy", tint = Cyan)
                        }
                    }
                },
            )

            Spacer(Modifier.weight(1f))
            GradientButton(
                "Save", modifier = Modifier.fillMaxWidth(),
                enabled = userId.isNotBlank() || password.isNotBlank(),
            ) {
                scope.launch {
                    val now = System.currentTimeMillis()
                    VaultSession.dao().upsert(
                        (original ?: VaultEntry(title = REWARDZ_TITLE, createdAt = now, updatedAt = now))
                            .copy(
                                title = REWARDZ_TITLE,
                                category = EntryCategory.LOGIN,
                                username = userId.trim(),
                                password = password,
                                url = "https://www.rewardz.sbi",
                                updatedAt = now,
                            )
                    )
                    snackbar.showSnackbar("SBI Rewardz login saved")
                    original = VaultSession.dao().allOnce().firstOrNull {
                        it.title.equals(REWARDZ_TITLE, ignoreCase = true)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
