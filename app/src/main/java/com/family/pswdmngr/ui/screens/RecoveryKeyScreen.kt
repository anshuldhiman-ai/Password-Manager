package com.family.pswdmngr.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.family.pswdmngr.crypto.KeystoreWrapper
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.RecoveryKeyGenerator
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Mandatory "save your Recovery Key" screen shown immediately after vault creation.
 * Forces the user to confirm they have saved the key before proceeding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryKeyScreen(nav: NavController) {
    val ctx = LocalContext.current
    val recoveryKey = remember { VaultSession.pendingRecoveryKey }
    var saved by remember { mutableStateOf(false) }
    var confirmInput by remember { mutableStateOf("") }
    var confirmError by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    var showBioEnroll by remember { mutableStateOf(false) }

    // Pick a random group to verify
    val allGroups = remember {
        recoveryKey?.let { key ->
            key.split('-').mapIndexed { idx, g -> idx + 1 to g }
        } ?: emptyList()
    }
    val verifyGroupIdx = remember { if (allGroups.isNotEmpty()) (0 until allGroups.size).random() else -1 }
    val verifyGroup = allGroups.getOrNull(verifyGroupIdx)

    if (recoveryKey == null) {
        // No recovery key — shouldn't happen, go to vault
        LaunchedEffect(Unit) { nav.navigate("vault") { popUpTo(0) { inclusive = true } } }
        return
    }

    fun confirmSave() {
        if (confirmInput.trim().uppercase() == verifyGroup?.second) {
            saved = true
            confirmError = false
            VaultSession.pendingRecoveryKey = null // clear so it's not shown again
        } else {
            confirmError = true
        }
    }

    Scaffold(
        containerColor = Midnight,
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            // Shield icon
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Amber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, null, tint = Amber, modifier = Modifier.size(36.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Your Recovery Key",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This key is the ONLY way to unlock your vault if you forget your master password. " +
                        "Keep it somewhere safe — offline, not in the cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            // ── Recovery key display ──
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Surface2.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Amber.copy(alpha = 0.3f)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(recoveryKey, style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Bold,
                    ), color = Amber)

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("Recovery Key", recoveryKey)
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.4f)),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = { showQr = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Violet),
                            border = BorderStroke(1.dp, Violet.copy(alpha = 0.4f)),
                        ) {
                            Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Show QR")
                        }
                        OutlinedButton(
                            onClick = { showCard = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint),
                            border = BorderStroke(1.dp, Mint.copy(alpha = 0.4f)),
                        ) {
                            Icon(Icons.Rounded.CreditCard, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Card")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Warning callout ──
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Coral.copy(alpha = 0.10f),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = Coral, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Losing your master password AND your Recovery Key means " +
                                "permanent data loss. There is no other way in. " +
                                "Write it down, print a card, or save the QR code — offline only.",
                        color = Coral,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Confirmation challenge ──
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Surface2.copy(alpha = 0.4f),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "Confirm you saved it".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (verifyGroup != null) {
                        Text(
                            "Type group ${verifyGroup.first} of your recovery key:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(10.dp))
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
                            Text(
                                "That doesn't match group ${verifyGroup.first}. Check what you saved.",
                                color = Coral, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Proceed button ──
            GradientButton(
                text = "I've saved my Recovery Key — enter vault",
                modifier = Modifier.fillMaxWidth(),
                enabled = saved || confirmInput.trim().uppercase() == verifyGroup?.second,
                icon = Icons.Rounded.Shield,
            ) {
                if (!saved) confirmSave()
                if (saved || confirmInput.trim().uppercase() == verifyGroup?.second) {
                    VaultSession.pendingRecoveryKey = null
                    // Offer biometric enrollment if hardware is available and not already enabled
                    val keystore = KeystoreWrapper(ctx)
                    if (!keystore.isEnabled && BiometricManager.from(ctx)
                            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                    ) {
                        showBioEnroll = true
                    } else {
                        nav.navigate("vault") { popUpTo(0) { inclusive = true } }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Biometric enrollment dialog ──
    if (showBioEnroll) {
        val keystore = remember { KeystoreWrapper(ctx) }
        AlertDialog(
            onDismissRequest = { showBioEnroll = false; nav.navigate("vault") { popUpTo(0) { inclusive = true } } },
            containerColor = Surface1,
            icon = { Icon(Icons.Rounded.Fingerprint, null, tint = Cyan) },
            title = { Text("Enable fingerprint unlock?", color = TextPrimary) },
            text = {
                Text("You can unlock the vault with your fingerprint instead of typing your master password every time. This is optional — you can change it later in Settings.",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = {
                    showBioEnroll = false
                    val activity = ctx as? FragmentActivity
                    if (activity != null) {
                        BiometricPrompt(
                            activity, ContextCompat.getMainExecutor(ctx),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    keystore.enable(VaultSession.currentKey())
                                    nav.navigate("vault") { popUpTo(0) { inclusive = true } }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    nav.navigate("vault") { popUpTo(0) { inclusive = true } }
                                }
                            }).authenticate(
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Enable fingerprint unlock")
                                .setSubtitle("Authenticate to set up quick unlock")
                                .setNegativeButtonText("Skip")
                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                .build()
                        )
                    } else {
                        nav.navigate("vault") { popUpTo(0) { inclusive = true } }
                    }
                }) { Text("Enable", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBioEnroll = false
                    nav.navigate("vault") { popUpTo(0) { inclusive = true } }
                }) { Text("Skip", color = TextSecondary) }
            },
        )
    }

    // ── QR Code Dialog ──
    if (showQr) {
        RecoveryQrDialog(
            recoveryKey = recoveryKey,
            onDismiss = { showQr = false },
        )
    }

    // ── Printable Card Dialog ──
    if (showCard) {
        RecoveryCardDialog(
            recoveryKey = recoveryKey,
            onDismiss = { showCard = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// QR Code export dialog
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun RecoveryQrDialog(recoveryKey: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val qrBitmap = remember {
        runCatching {
            val size = 512
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(recoveryKey, BarcodeFormat.QR_CODE, size, size)
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
            }
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Text("Recovery Key QR Code", color = TextPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Recovery Key QR Code",
                        modifier = Modifier.size(240.dp),
                        contentScale = ContentScale.Fit,
                    )
                } ?: Text("Could not generate QR code", color = Coral)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Scan this QR to recover your vault. Keep it offline — print it, don't screenshot.",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    qrBitmap?.let { bmp ->
                        runCatching {
                            // Save to device gallery via MediaStore
                            val filename = "PSWD-MNGR-Recovery-Key-${System.currentTimeMillis()}.png"
                            val fos: java.io.OutputStream?
                            if (Build.VERSION.SDK_INT >= 29) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                }
                                val uri = ctx.contentResolver.insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                                )
                                fos = uri?.let { ctx.contentResolver.openOutputStream(it) }
                            } else {
                                val dir = Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES
                                ).apply { mkdirs() }
                                val file = java.io.File(dir, filename)
                                fos = java.io.FileOutputStream(file)
                            }
                            fos?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }
                }) { Text("Save to Gallery", color = Mint) }
                TextButton(onClick = {
                    qrBitmap?.let { bmp ->
                        runCatching {
                            val file = File(ctx.cacheDir, "recovery_qr.png")
                            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(intent, "Share Recovery QR"))
                        }
                    }
                }) { Text("Share", color = Cyan) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Printable Recovery Card dialog
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun RecoveryCardDialog(recoveryKey: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val groups = recoveryKey.split('-')

    // Card composable — rendered as a bitmap for sharing
    val cardContent = @Composable {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A2E),
            border = BorderStroke(2.dp, Amber.copy(alpha = 0.5f)),
        ) {
            Column(
                Modifier.width(320.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Shield, null, tint = Amber, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("PSWD MNGR", style = MaterialTheme.typography.titleLarge, color = Amber,
                    fontWeight = FontWeight.Bold)
                Text("Vault Recovery Key", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(20.dp))
                Divider(color = Amber.copy(alpha = 0.2f))
                Spacer(Modifier.height(20.dp))
                groups.forEachIndexed { i, g ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${i + 1}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text(g, color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Divider(color = Amber.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Keep this card somewhere safe and offline.\n" +
                            "Without it and your master password, data is unrecoverable.",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, usePlatformDefaultWidth = false),
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                cardContent()
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            // Capture card as bitmap + share
                            runCatching {
                                val view = ctx.cacheDir
                                val file = File(ctx.cacheDir, "recovery_card.png")
                                // For simplicity, share the key text as a plain-text card
                                val text = "PSWD MNGR Recovery Key\n\n" + groups.mapIndexed { i, g ->
                                    "${i + 1}: $g"
                                }.joinToString("\n") +
                                        "\n\nKeep offline. Without this key and your master password, vault data is permanently lost."
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Password Manager Recovery Key")
                                }
                                ctx.startActivity(android.content.Intent.createChooser(intent, "Share Recovery Card"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber),
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share card", color = Color(0xFF1A1A2E))
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    ) {
                        Text("Done")
                    }
                }
            }
        },
        confirmButton = {},
    )
}
