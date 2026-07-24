package com.family.pswdmngr.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.data.NoteEntry
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.screens.*
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch

/** Keep-style tinted note cards; everything lives inside the encrypted vault. */
val NoteTints = listOf(
    Color(0xFF1E2340), // default surface
    Color(0xFF3A2A4D), // plum
    Color(0xFF1F3A3D), // teal
    Color(0xFF413320), // sand
    Color(0xFF3D2530), // rose
    Color(0xFF25381F), // moss
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val notes by VaultSession.noteDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Secret Notes", color = TextPrimary) },
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
                onClick = { nav.navigate("noteEdit/-1") },
                containerColor = Violet, shape = CircleShape,
            ) { Icon(Icons.Rounded.Add, "Add note", tint = TextPrimary) }
        },
    ) { pad ->
        if (notes.isEmpty()) {
            EmptyState(
                Icons.Rounded.StickyNote2, Amber,
                "No notes yet",
                "Anything you write here is encrypted inside the vault — perfect for things too sensitive for a normal notes app.",
                modifier = Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(notes, key = { it.id }) { n ->
                Surface(
                    onClick = { nav.navigate("noteEdit/${n.id}") },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = NoteTints[n.colorIdx.coerceIn(0, NoteTints.lastIndex)],
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (n.pinned) {
                            Icon(Icons.Rounded.PushPin, null, tint = Amber, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.height(4.dp))
                        }
                        if (n.title.isNotBlank()) {
                            Text(n.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            n.body, color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 8, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(nav: NavController, id: Long) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == -1L) }
    var original by remember { mutableStateOf<NoteEntry?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var colorIdx by remember { mutableStateOf(0) }
    var pinned by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != -1L) {
            VaultSession.noteDao().byId(id)?.let { n ->
                original = n
                title = n.title; body = n.body; colorIdx = n.colorIdx; pinned = n.pinned
            }
            loaded = true
        }
    }
    if (!loaded) return

    fun save(then: () -> Unit) {
        scope.launch {
            val now = System.currentTimeMillis()
            if (title.isBlank() && body.isBlank()) {
                original?.let { VaultSession.noteDao().delete(it) } // empty note = discard
            } else {
                VaultSession.noteDao().upsert(
                    (original ?: NoteEntry(createdAt = now)).copy(
                        id = original?.id ?: 0,
                        title = title.trim(), body = body, colorIdx = colorIdx, pinned = pinned,
                        updatedAt = now, createdAt = original?.createdAt ?: now,
                    )
                )
            }
            then()
        }
    }

    // System back must save too (Keep-style) — otherwise gesture-back silently drops edits
    androidx.activity.compose.BackHandler { save { nav.popBackStack() } }

    Scaffold(
        containerColor = NoteTints[colorIdx.coerceIn(0, NoteTints.lastIndex)],
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { save { nav.popBackStack() } }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Save and go back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            Icons.Rounded.PushPin,
                            "Pin", tint = if (pinned) Amber else TextSecondary,
                        )
                    }
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, "Delete", tint = Coral)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NoteTints[colorIdx.coerceIn(0, NoteTints.lastIndex)]),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NoteTints.forEachIndexed { i, tint ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(tint)
                            .then(
                                if (i == colorIdx) Modifier.border(2.dp, Cyan, CircleShape)
                                else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            )
                            .clickable { colorIdx = i },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
            TextField(
                value = title, onValueChange = { title = it },
                placeholder = { Text("Title", color = TextSecondary) },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = TextPrimary),
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = body, onValueChange = { body = it },
                placeholder = { Text("Write something secret…", color = TextSecondary) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Delete this note?", color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        original?.let { VaultSession.noteDao().delete(it) }
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
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Cyan,
)
