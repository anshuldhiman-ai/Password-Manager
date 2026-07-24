package com.family.pswdmngr.ui.docs

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.family.pswdmngr.VaultApp
import com.family.pswdmngr.data.*
import com.family.pswdmngr.ui.cards.CustomFieldsEditor
import com.family.pswdmngr.ui.cards.SecretRow
import com.family.pswdmngr.ui.cards.horizontalScrollRow
import com.family.pswdmngr.ui.screens.*
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun docIcon(t: String): ImageVector = when (t) {
    "AADHAAR" -> Icons.Rounded.Fingerprint
    "PAN" -> Icons.Rounded.Badge
    "PASSPORT" -> Icons.Rounded.FlightTakeoff
    "DRIVING_LICENSE" -> Icons.Rounded.DirectionsCar
    "VOTER_ID" -> Icons.Rounded.HowToVote
    "TRADING" -> Icons.Rounded.TrendingUp
    "INSURANCE" -> Icons.Rounded.HealthAndSafety
    "VEHICLE" -> Icons.Rounded.TwoWheeler
    "PROPERTY" -> Icons.Rounded.Home
    else -> Icons.Rounded.Description
}

fun docColor(t: String) = when (t) {
    "AADHAAR" -> Amber
    "PAN" -> Cyan
    "PASSPORT" -> Violet
    "TRADING" -> Mint
    "INSURANCE" -> Coral
    else -> TextSecondary
}

/* ================= Documents list ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val docs by VaultSession.docDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Documents", color = TextPrimary) },
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
                onClick = { nav.navigate("docEdit/-1") },
                containerColor = Violet, shape = CircleShape,
            ) { Icon(Icons.Rounded.Add, "Add document", tint = TextPrimary) }
        },
    ) { pad ->
        if (docs.isEmpty()) {
            EmptyState(
                Icons.Rounded.Description, Amber,
                "No documents yet",
                "Store Aadhaar, PAN (both sides), trading and demat details — with encrypted photos and PDFs.",
                modifier = Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(docs.size, key = { docs[it].id }) { i ->
                val d = docs[i]
                GlassCard(onClick = { nav.navigate("docDetail/${d.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(docIcon(d.docType), docColor(d.docType))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title.ifBlank { DocType.label(d.docType) }, color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                DocType.label(d.docType) +
                                    if (d.number.isNotBlank()) "  •  ${maskDocNumber(d.docType, d.number)}" else "",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

fun maskDocNumber(type: String, number: String): String {
    val n = number.replace(" ", "")
    return when {
        type == "AADHAAR" && n.length >= 4 -> "•••• •••• " + n.takeLast(4)
        n.length > 4 -> "•••" + n.takeLast(4)
        else -> n
    }
}

/**
 * Enforces the real format of each document number as it is typed:
 *  - Aadhaar: digits only, max 12, grouped 4-4-4
 *  - PAN: uppercase, max 10 (ABCDE1234F)
 *  - Voter ID (EPIC): uppercase, max 10 (ABC1234567)
 *  - Passport: uppercase, max 8 (A1234567)
 */
fun formatDocNumber(type: String, input: String): String = when (type) {
    "AADHAAR" -> input.filter { it.isDigit() }.take(12)
        .chunked(4).joinToString(" ")
    "PAN" -> input.filter { it.isLetterOrDigit() }.uppercase().take(10)
    "VOTER_ID" -> input.filter { it.isLetterOrDigit() }.uppercase().take(10)
    "PASSPORT" -> input.filter { it.isLetterOrDigit() }.uppercase().take(8)
    else -> input
}

/** Gentle inline warning when a partly-typed number can't be right. */
fun docNumberHint(type: String, number: String): String? {
    if (number.isBlank()) return null
    val n = number.replace(" ", "")
    return when (type) {
        "AADHAAR" -> if (n.length != 12) "Aadhaar has 12 digits — ${n.length} entered" else null
        "PAN" -> when {
            n.length != 10 -> "PAN has 10 characters — ${n.length} entered"
            !Regex("^[A-Z]{5}[0-9]{4}[A-Z]$").matches(n) -> "Format: 5 letters, 4 digits, 1 letter"
            else -> null
        }
        "VOTER_ID" -> when {
            n.length != 10 -> "EPIC number has 10 characters — ${n.length} entered"
            !Regex("^[A-Z]{3}[0-9]{7}$").matches(n) -> "Format: 3 letters then 7 digits"
            else -> null
        }
        "PASSPORT" -> when {
            n.length != 8 -> "Passport number has 8 characters — ${n.length} entered"
            !Regex("^[A-Z][0-9]{7}$").matches(n) -> "Format: 1 letter then 7 digits"
            else -> null
        }
        else -> null
    }
}

/* ================= Document detail with attachment viewer ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocDetailScreen(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var doc by remember { mutableStateOf<DocumentEntry?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Attachment?>(null) }

    LaunchedEffect(id) { doc = VaultSession.docDao().byId(id) }
    val d = doc ?: return

    val attachments by VaultSession.attachmentDao()
        .observeForOwner("DOC", d.id).collectAsState(initial = emptyList())

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val added = withContext(Dispatchers.IO) { AttachmentStore.add(ctx, "DOC", d.id, it) }
                snackbar.showSnackbar(
                    if (added != null) "Added ${added.displayName} (encrypted)" else "Could not add file"
                )
            }
        }
    }

    fun copy(label: String, v: String) {
        (ctx.applicationContext as VaultApp).copySecret(label, v)
        scope.launch { snackbar.showSnackbar("$label copied — clears in 30 s") }
    }

    Scaffold(
        containerColor = Midnight,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(d.title.ifBlank { DocType.label(d.docType) }, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("docEdit/${d.id}") }) {
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
            if (d.number.isNotBlank()) {
                item {
                    val docLabel = when (d.docType) {
                        "AADHAAR" -> "Aadhaar number"; "PAN" -> "PAN"
                        "TRADING" -> "Client ID"; else -> "Number"
                    }
                    val isSensitive = d.docType == "AADHAAR"
                    if (isSensitive) {
                        RevealLockField(docLabel, d.number, mono = true) { copy("Number", d.number) }
                    } else {
                        SecretRow(docLabel, d.number, secret = false, mono = true) { copy("Number", d.number) }
                    }
                }
            }
            if (d.holder.isNotBlank()) item { SecretRow("Name", d.holder) { copy("Name", d.holder) } }

            val extras = parseFields(d.fieldsJson)
            if (extras.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("DETAILS") }
                items(extras.size) { i ->
                    val f = extras[i]
                    if (f.secret) {
                        RevealLockField(f.label, f.value) { copy(f.label, f.value) }
                    } else {
                        SecretRow(f.label, f.value, secret = f.secret) { copy(f.label, f.value) }
                    }
                }
            }

            if (d.notes.isNotBlank()) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("NOTES") }
                item { GlassCard { Text(d.notes, color = TextPrimary, style = MaterialTheme.typography.bodyMedium) } }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("FILES (ENCRYPTED)")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { pickLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Cyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add photo / PDF", color = Cyan)
                    }
                }
            }
            if (attachments.isEmpty()) {
                item {
                    Text(
                        "Add the front & back photos or the official PDF. Files are encrypted with your vault key — nothing readable is stored on the phone.",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(attachments.size, key = { "att" + attachments[it].id }) { i ->
                val a = attachments[i]
                GlassCard(onClick = { viewer = a }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            if (a.mime.startsWith("image")) Icons.Rounded.Image else Icons.Rounded.PictureAsPdf,
                            if (a.mime.startsWith("image")) Mint else Coral,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.displayName, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                                maxLines = 1)
                            Text(
                                (if (a.mime.startsWith("image")) "Photo" else "PDF") +
                                    "  •  ${a.size / 1024} KB  •  AES-256 encrypted",
                                color = TextSecondary, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AttachmentStore.delete(ctx, a) }
                                VaultSession.attachmentDao().delete(a)
                            }
                        }) { Icon(Icons.Rounded.Delete, "Remove", tint = TextSecondary) }
                    }
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    viewer?.let { att ->
        AttachmentViewerDialog(att, onDismiss = { viewer = null })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Delete this document?", color = TextPrimary) },
            text = { Text("The record and all its encrypted files will be removed.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { AttachmentStore.deleteForOwner(ctx, "DOC", d.id) }
                        VaultSession.docDao().delete(d)
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

/** Full-screen in-app viewer — decrypts to memory only; nothing hits disk unencrypted (PDF via cache, wiped). */
@Composable
private fun AttachmentViewerDialog(att: Attachment, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(att.id) {
        loading = true
        pages = withContext(Dispatchers.IO) {
            if (att.mime.startsWith("image"))
                listOfNotNull(AttachmentStore.decodeBitmap(ctx, att))
            else
                AttachmentStore.renderPdfPages(ctx, att)
        }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Midnight.copy(alpha = 0.97f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = TextPrimary)
                }
                Text(att.displayName, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1)
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Violet)
                }
            } else if (pages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not open file", color = Coral)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pages.size) { i ->
                        Image(
                            pages[i].asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}

/* ================= Document editor ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDocScreen(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == -1L) }
    var original by remember { mutableStateOf<DocumentEntry?>(null) }

    var title by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf("AADHAAR") }
    var number by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(listOf<CustomField>()) }

    LaunchedEffect(id) {
        if (id != -1L) {
            VaultSession.docDao().byId(id)?.let { d ->
                original = d
                title = d.title; docType = d.docType; number = d.number
                holder = d.holder; notes = d.notes
                fields = parseFields(d.fieldsJson)
            }
            loaded = true
        }
    }
    if (!loaded) return

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text(if (id == -1L) "Add document" else "Edit document", color = TextPrimary) },
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
            item { SectionLabel("DOCUMENT TYPE") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScrollRow()) {
                    DocType.ALL.forEach { t ->
                        FilterChip(
                            selected = docType == t, onClick = { docType = t },
                            label = { Text(DocType.label(t)) },
                            leadingIcon = if (docType == t) {
                                { Icon(docIcon(t), null, modifier = Modifier.size(15.dp), tint = docColor(t)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = docColor(t).copy(alpha = 0.22f),
                                selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
            item { VaultTextField(title, { title = it }, "Title (e.g. Papa's Aadhaar)") }
            item {
                VaultTextField(
                    number,
                    { input -> number = formatDocNumber(docType, input) },
                    when (docType) {
                        "AADHAAR" -> "Aadhaar number (12 digits)"
                        "PAN" -> "PAN (e.g. ABCDE1234F)"
                        "VOTER_ID" -> "EPIC number (e.g. ABC1234567)"
                        "TRADING" -> "Client ID"
                        "PASSPORT" -> "Passport number (e.g. A1234567)"
                        else -> "Number / ID"
                    },
                    keyboardOptions = if (docType == "AADHAAR")
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    else androidx.compose.foundation.text.KeyboardOptions.Default,
                )
                docNumberHint(docType, number)?.let { hint ->
                    Text(hint, color = Coral, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            item { VaultTextField(holder, { holder = it }, "Name on document") }

            item { Spacer(Modifier.height(4.dp)); SectionLabel("DETAILS") }
            item {
                CustomFieldsEditor(fields, onChange = { fields = it },
                    suggestions = DocType.suggestedFields(docType))
            }

            item { Spacer(Modifier.height(4.dp)); SectionLabel("NOTES") }
            item { VaultTextField(notes, { notes = it }, "Notes", singleLine = false) }

            item {
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    "Save document", modifier = Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank() || number.isNotBlank(),
                ) {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val savedId = VaultSession.docDao().upsert(
                            (original ?: DocumentEntry(createdAt = now)).copy(
                                id = original?.id ?: 0,
                                title = title.trim(), docType = docType,
                                number = number.trim(), holder = holder.trim(), notes = notes,
                                fieldsJson = fieldsToJson(fields.filter { it.label.isNotBlank() }),
                                updatedAt = now, createdAt = original?.createdAt ?: now,
                            )
                        )
                        // land on the detail page so photos/PDFs can be added right away
                        if (id == -1L) {
                            nav.popBackStack()
                            nav.navigate("docDetail/$savedId")
                        } else nav.popBackStack()
                    }
                }
                Text(
                    "Tip: after saving, open the document to attach front/back photos or a PDF — they are stored encrypted.",
                    color = TextSecondary, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}
