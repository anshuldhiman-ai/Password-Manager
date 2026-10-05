package com.family.pswdmngr.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Premium 1:1 Recovery Key Screen matching Screen 14 of design sample.
 * Features:
 * - Red "Done" CTA in the top-right header for instant frictionless progression
 * - Glowing Amber key icon badge
 * - Monospace key display card with mask/unmask toggle
 * - 2x2 action buttons: Copy, Export, QR Code, Print
 * - Clear warning banner
 */
@Composable
fun RecoveryKeyScreen(nav: NavController) {
    val ctx = LocalContext.current
    val recoveryKey = remember {
        VaultSession.pendingRecoveryKey ?: VaultSession.getRecoveryKey(ctx) ?: "VAULT-RECOVERY-KEY"
    }

    var isRevealed by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showPrintCard by remember { mutableStateOf(false) }

    fun finishAndEnterVault() {
        VaultSession.pendingRecoveryKey = null
        VaultSession.dismissPendingRecoveryDisplay(ctx)
        nav.navigate("vault") {
            popUpTo(0) { inclusive = true }
        }
    }

    fun copyToClipboard() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Recovery Key", recoveryKey))
        Toast.makeText(ctx, "Recovery key copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareKey() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PSWD MNGR Recovery Key")
            putExtra(
                Intent.EXTRA_TEXT,
                "PSWD MNGR Vault Recovery Key:\n$recoveryKey\n\nKeep offline! Without this key and master password, vault cannot be restored."
            )
        }
        ctx.startActivity(Intent.createChooser(intent, "Export Recovery Key"))
    }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { finishAndEnterVault() }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                TextButton(onClick = { finishAndEnterVault() }) {
                    Text(
                        "Done",
                        color = Coral,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            // Glowing Amber Key Badge per Screen 14
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Amber.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Surface2,
                    border = BorderStroke(1.5.dp, Amber.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Key,
                            contentDescription = "Recovery Key",
                            tint = Amber,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Recovery Key",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your last key to the vault.\n24-character key.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Recovery Key Card with Mask/Reveal toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { isRevealed = !isRevealed },
                shape = RoundedCornerShape(18.dp),
                color = Surface2.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Stroke),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (isRevealed) {
                        Text(
                            text = recoveryKey,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp,
                                fontSize = 15.sp,
                            ),
                            color = Amber,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = "••••••••••••••••••••••••",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isRevealed) "Hide" else "Show",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 2x2 Action Buttons matching Screen 14
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RecoveryActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    modifier = Modifier.weight(1f),
                    onClick = { copyToClipboard() }
                )
                RecoveryActionButton(
                    icon = Icons.Rounded.Share,
                    label = "Export",
                    modifier = Modifier.weight(1f),
                    onClick = { shareKey() }
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RecoveryActionButton(
                    icon = Icons.Rounded.QrCode2,
                    label = "QR Code",
                    modifier = Modifier.weight(1f),
                    onClick = { showQr = true }
                )
                RecoveryActionButton(
                    icon = Icons.Rounded.Print,
                    label = "Print",
                    modifier = Modifier.weight(1f),
                    onClick = { showPrintCard = true }
                )
            }

            Spacer(Modifier.height(28.dp))

            // Warning Callout matching Screen 14 bottom
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Coral.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Coral.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = "Warning",
                        tint = Coral,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Warning: If you lose your master password and your recovery key, your vault cannot be recovered.",
                        color = Coral,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }

    // QR Code Dialog
    if (showQr) {
        RecoveryQrDialog(recoveryKey = recoveryKey, onDismiss = { showQr = false })
    }

    // Printable Card Dialog
    if (showPrintCard) {
        RecoveryCardDialog(recoveryKey = recoveryKey, onDismiss = { showPrintCard = false })
    }
}

@Composable
private fun RecoveryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Surface2.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Stroke),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}

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
            Text(
                "Recovery Key QR Code",
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
                    "Scan this QR code to recover your vault. Keep it offline only.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    qrBitmap?.let { bmp ->
                        runCatching {
                            val filename = "PSWD-MNGR-Recovery-${System.currentTimeMillis()}.png"
                            if (Build.VERSION.SDK_INT >= 29) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                }
                                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let { ctx.contentResolver.openOutputStream(it)?.use { s -> bmp.compress(Bitmap.CompressFormat.PNG, 100, s) } }
                            } else {
                                @Suppress("DEPRECATION")
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).apply { mkdirs() }
                                val file = File(dir, filename)
                                FileOutputStream(file).use { s -> bmp.compress(Bitmap.CompressFormat.PNG, 100, s) }
                            }
                            Toast.makeText(ctx, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Save to Gallery", color = Mint) }

                TextButton(onClick = {
                    qrBitmap?.let { bmp ->
                        runCatching {
                            val file = File(ctx.cacheDir, "recovery_qr.png")
                            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "Share Recovery QR"))
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

@Composable
private fun RecoveryCardDialog(recoveryKey: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val groups = recoveryKey.split('-')

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Text(
                "Printable Vault Card",
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131722),
                    border = BorderStroke(1.5.dp, Amber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = Amber, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "PSWD MNGR",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Amber
                        )
                        Text("Vault Recovery Key", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Amber.copy(alpha = 0.2f))
                        Spacer(Modifier.height(12.dp))

                        groups.forEachIndexed { i, g ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${i + 1}.", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    g,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Amber.copy(alpha = 0.2f))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Keep offline. Without this key, vault data is lost.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = "PSWD MNGR Recovery Key:\n" + groups.joinToString("-") + "\n\nStore offline."
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Share Card Text"))
                }
            ) {
                Text("Share", color = Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        }
    )
}
