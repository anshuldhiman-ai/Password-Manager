package com.family.pswdmngr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.crypto.PasswordGenerator
import com.family.pswdmngr.data.*
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val items by VaultSession.trashDao().observeAll().collectAsState(initial = emptyList())
    val count by VaultSession.trashDao().observeCount().collectAsState(initial = 0)
    val snackbar = remember { SnackbarHostState() }
    var confirmEmpty by remember { mutableStateOf(false) }

    // Purge expired items
    LaunchedEffect(Unit) { RecycleBinManager.purgeExpired() }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Rounded.DeleteSweep, "Empty trash", tint = Coral)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
    ) { pad ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.DeleteSweep, TextSecondary,
                "Trash is empty",
                "Deleted items appear here and are automatically purged after 30 days.",
                modifier = Modifier.padding(pad),
            )
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("$count item${if (count != 1) "s" else ""} — auto-purged after 30 days",
                        color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }
                items(items, key = { it.id }) { trashItem ->
                    val daysLeft = ((trashItem.expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                    val icon = trashIcon(trashItem.itemType)
                    val color = trashColor(trashItem.itemType)

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Surface2.copy(alpha = 0.4f),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(trashItem.title, color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium)
                                Row {
                                    Text(formatDate(trashItem.deletedAt), color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (daysLeft > 0) "$daysLeft days left" else "Expiring soon",
                                        color = if (daysLeft > 3) TextSecondary else Coral,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    RecycleBinManager.restore(trashItem)
                                    scope.launch { snackbar.showSnackbar("Item restored") }
                                }
                            }) {
                                Icon(Icons.Rounded.RestoreFromTrash, "Restore", tint = Mint)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    VaultSession.trashDao().delete(trashItem)
                                    scope.launch { snackbar.showSnackbar("Permanently deleted") }
                                }
                            }) {
                                Icon(Icons.Rounded.DeleteForever, "Delete permanently", tint = Coral)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = Surface1,
            title = { Text("Empty trash?", color = TextPrimary) },
            text = { Text("All deleted items will be permanently removed. This cannot be undone.",
                color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { RecycleBinManager.emptyAll() }
                    confirmEmpty = false
                }) { Text("Empty", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

private fun trashIcon(type: String): ImageVector = when (type) {
    TrashType.LOGIN -> Icons.Rounded.Language
    TrashType.CARD -> Icons.Rounded.CreditCard
    TrashType.BANK -> Icons.Rounded.AccountBalance
    TrashType.DOC -> Icons.Rounded.Description
    TrashType.NOTE -> Icons.Rounded.StickyNote2
    TrashType.TASK -> Icons.Rounded.TaskAlt
    else -> Icons.Rounded.Help
}

private fun trashColor(type: String): Color = when (type) {
    TrashType.LOGIN -> Violet; TrashType.CARD -> Cyan
    TrashType.BANK -> Mint; TrashType.DOC -> Amber
    TrashType.NOTE -> Coral; TrashType.TASK -> Violet
    else -> TextSecondary
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))
