package com.family.pswdmngr.ui.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.family.pswdmngr.data.TaskItem
import com.family.pswdmngr.data.TaskList
import com.family.pswdmngr.data.VaultSession
import com.family.pswdmngr.ui.screens.*
import com.family.pswdmngr.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dueFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

fun dueLabel(dueAt: Long): Pair<String, Boolean> {
    if (dueAt == 0L) return "" to false
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    // overdue once the exact deadline time passes
    val overdue = dueAt < System.currentTimeMillis()
    val day = when {
        dueAt in today until today + 86_400_000L -> "Today"
        dueAt in today + 86_400_000L until today + 2 * 86_400_000L -> "Tomorrow"
        else -> dueFmt.format(Date(dueAt))
    }
    return "$day, ${timeFmt.format(Date(dueAt))}" to overdue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(nav: NavController) {
    if (!VaultSession.isUnlocked) return
    val scope = rememberCoroutineScope()
    val dao = VaultSession.taskDao()
    val lists by dao.observeLists().collectAsState(initial = emptyList())
    var selectedListId by remember { mutableStateOf(0L) } // 0 = starred view until lists load

    // Seed a default list on first open
    LaunchedEffect(lists) {
        if (lists.isEmpty()) {
            dao.upsertList(TaskList(name = "My Tasks", position = 0, createdAt = System.currentTimeMillis()))
        } else if (selectedListId == 0L || lists.none { it.id == selectedListId }) {
            if (selectedListId != -1L) selectedListId = lists.first().id
        }
    }

    val starred by dao.observeStarred().collectAsState(initial = emptyList())
    val tasks by remember(selectedListId) {
        if (selectedListId > 0) dao.observeTasks(selectedListId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var newListDialog by remember { mutableStateOf(false) }
    var renameList by remember { mutableStateOf<TaskList?>(null) }
    var deleteList by remember { mutableStateOf<TaskList?>(null) }
    var editorTask by remember { mutableStateOf<TaskItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val currentList = lists.firstOrNull { it.id == selectedListId }

    Scaffold(
        containerColor = Midnight,
        topBar = {
            TopAppBar(
                title = { Text("Tasks", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    currentList?.let { l ->
                        IconButton(onClick = { renameList = l }) {
                            Icon(Icons.Rounded.Edit, "Rename list", tint = TextSecondary)
                        }
                        IconButton(onClick = { deleteList = l }) {
                            Icon(Icons.Rounded.Delete, "Delete list", tint = TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Midnight),
            )
        },
        floatingActionButton = {
            if (selectedListId > 0) {
                FloatingActionButton(
                    onClick = { editorTask = null; showEditor = true },
                    containerColor = Violet, shape = CircleShape,
                ) { Icon(Icons.Rounded.Add, "Add task", tint = TextPrimary) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // List tabs: ⭐ | each list | +
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedListId == -1L,
                    onClick = { selectedListId = -1L },
                    label = { Icon(Icons.Rounded.Star, "Starred", tint = if (selectedListId == -1L) Amber else TextSecondary, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Amber.copy(alpha = 0.18f)),
                )
                lists.forEach { l ->
                    FilterChip(
                        selected = selectedListId == l.id,
                        onClick = { selectedListId = l.id },
                        label = { Text(l.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Violet.copy(alpha = 0.25f),
                            selectedLabelColor = TextPrimary, labelColor = TextSecondary,
                        ),
                    )
                }
                AssistChip(
                    onClick = { newListDialog = true },
                    label = { Text("New list") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(15.dp), tint = Cyan) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = TextSecondary),
                )
            }

            val shown = if (selectedListId == -1L) starred else tasks
            val (open, done) = shown.partition { !it.completed }

            // Progress bar for the current list (hidden in the starred view)
            if (selectedListId > 0 && shown.isNotEmpty()) {
                val progress by animateFloatAsState(
                    if (shown.isEmpty()) 0f else done.size.toFloat() / shown.size,
                    label = "taskProgress",
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${done.size} of ${shown.size} done", color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Violet.copy(alpha = 0.15f),
                        ) {
                            Text(
                                "${shown.size} total",
                                color = TextSecondary.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                        color = Mint, trackColor = Surface2,
                    )
                }
            }

            if (shown.isEmpty()) {
                EmptyState(
                    if (selectedListId == -1L) Icons.Rounded.Star else Icons.Rounded.TaskAlt,
                    if (selectedListId == -1L) Amber else Mint,
                    if (selectedListId == -1L) "No starred tasks" else "All clear!",
                    if (selectedListId == -1L) "Star a task to see it here."
                    else "Tap + to add your first task.",
                )
            } else {
                // Group open tasks by urgency; keep the user's order within each bucket
                val nowMs = System.currentTimeMillis()
                val startOfTomorrow = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
                val overdue = open.filter { it.dueAt in 1 until nowMs }
                val todayTasks = open.filter { it.dueAt in nowMs until startOfTomorrow }
                val upcoming = open.filter { it.dueAt >= startOfTomorrow }
                val noDate = open.filter { it.dueAt == 0L }

                fun toggle(t: TaskItem) = scope.launch {
                    dao.upsertTask(t.copy(completed = !t.completed,
                        completedAt = if (!t.completed) System.currentTimeMillis() else 0,
                        updatedAt = System.currentTimeMillis()))
                }
                fun star(t: TaskItem) = scope.launch {
                    dao.upsertTask(t.copy(starred = !t.starred, updatedAt = System.currentTimeMillis()))
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fun section(label: String, items: List<TaskItem>, tint: Color? = null) {
                        if (items.isEmpty()) return
                        item(key = "hdr_$label") {
                            Spacer(Modifier.height(4.dp))
                            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                                color = tint ?: TextSecondary)
                        }
                        items(items.size, key = { items[it].id }) { i ->
                            TaskRow(items[i], onToggle = { toggle(it) }, onStar = { star(it) },
                                onClick = { t -> editorTask = t; showEditor = true })
                        }
                    }
                    section("Overdue", overdue, Coral)
                    section("Today", todayTasks)
                    section("Upcoming", upcoming)
                    section("No due date", noDate)

                    if (done.isNotEmpty()) {
                        item { Spacer(Modifier.height(6.dp)); SectionLabel("COMPLETED (${done.size})") }
                        items(done.size, key = { done[it].id }) { i ->
                            TaskRow(done[i], onToggle = { toggle(it) }, onStar = { star(it) },
                                onClick = { t -> editorTask = t; showEditor = true })
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorSheet(
            listId = if (selectedListId > 0) selectedListId else (editorTask?.listId ?: lists.firstOrNull()?.id ?: 0L),
            existing = editorTask,
            onDismiss = { showEditor = false },
        )
    }

    if (newListDialog) {
        NameDialog("New list", "", onDismiss = { newListDialog = false }) { name ->
            scope.launch {
                val id = dao.upsertList(TaskList(name = name, position = lists.size,
                    createdAt = System.currentTimeMillis()))
                selectedListId = id
            }
            newListDialog = false
        }
    }
    renameList?.let { l ->
        NameDialog("Rename list", l.name, onDismiss = { renameList = null }) { name ->
            scope.launch { dao.upsertList(l.copy(name = name)) }
            renameList = null
        }
    }
    deleteList?.let { l ->
        AlertDialog(
            onDismissRequest = { deleteList = null },
            containerColor = Surface1,
            title = { Text("Delete \"${l.name}\"?", color = TextPrimary) },
            text = { Text("All tasks in this list will be deleted too.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.deleteTasksInList(l.id)
                        dao.deleteList(l)
                        deleteList = null
                    }
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { deleteList = null }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: (TaskItem) -> Unit,
    onStar: (TaskItem) -> Unit,
    onClick: (TaskItem) -> Unit,
) {
    GlassCard(onClick = { onClick(task) }, modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onToggle(task) }) {
                Icon(
                    if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    if (task.completed) "Mark not done" else "Mark done",
                    tint = if (task.completed) Mint else TextSecondary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = if (task.completed) TextSecondary else TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                )
                if (task.details.isNotBlank()) {
                    Text(task.details, color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                if (task.dueAt != 0L) {
                    val (label, overdue) = dueLabel(task.dueAt)
                    val pillColor = if (overdue && !task.completed) Coral else Cyan
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.clip(CircleShape)
                            .background(pillColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (overdue && !task.completed) Icons.Rounded.Warning else Icons.Rounded.Schedule,
                            null, tint = pillColor, modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = pillColor, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = { onStar(task) }) {
                Icon(
                    if (task.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    "Star", tint = if (task.starred) Amber else TextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(listId: Long, existing: TaskItem?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = VaultSession.taskDao()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var details by remember { mutableStateOf(existing?.details ?: "") }
    var dueAt by remember { mutableStateOf(existing?.dueAt ?: 0L) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(if (existing == null) "New task" else "Edit task",
                style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            VaultTextField(title, { title = it }, "Task")
            Spacer(Modifier.height(10.dp))
            VaultTextField(details, { details = it }, "Details (optional)", singleLine = false)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(if (dueAt == 0L) "Add due date" else dueLabel(dueAt).first) },
                    leadingIcon = { Icon(Icons.Rounded.Event, null, modifier = Modifier.size(16.dp), tint = Cyan) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (dueAt == 0L) TextSecondary else Cyan),
                )
                if (dueAt != 0L) {
                    IconButton(onClick = { dueAt = 0L }) {
                        Icon(Icons.Rounded.Close, "Clear date", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (existing != null) {
                    TextButton(onClick = {
                        scope.launch {
                            dao.deleteSubtasks(existing.id)
                            dao.deleteTask(existing)
                        }
                        onDismiss()
                    }) { Text("Delete", color = Coral) }
                }
            }
            Spacer(Modifier.height(16.dp))
            GradientButton("Save", modifier = Modifier.fillMaxWidth(), enabled = title.isNotBlank()) {
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.upsertTask(
                        (existing ?: TaskItem(listId = listId, createdAt = now)).copy(
                            id = existing?.id ?: 0,
                            listId = existing?.listId ?: listId,
                            title = title.trim(), details = details.trim(), dueAt = dueAt,
                            updatedAt = now, createdAt = existing?.createdAt ?: now,
                        )
                    )
                }
                onDismiss()
            }
        }
    }

    if (showDatePicker) {
        PremiumCalendarPicker(
            initial = dueAt,
            onDismiss = { showDatePicker = false },
            onPick = { millis -> dueAt = millis; showDatePicker = false },
        )
    }
}

/**
 * Clean Material3 date + time picker — replaces the old custom calendar.
 * Uses the platform's own DatePickerDialog and TimePicker for a polished,
 * familiar UX across Android versions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumCalendarPicker(
    initial: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val today0 = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val initialCal = Calendar.getInstance().apply {
        if (initial > System.currentTimeMillis()) timeInMillis = initial
    }

    // Date picker state — initialise to initial date or today
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = if (initial > System.currentTimeMillis()) initial else today0.timeInMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= today0.timeInMillis
        },
    )

    var showTimePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true,
    )

    // Show time picker after date is selected
    if (!showTimePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = { showTimePicker = true },
                ) { Text("Next", color = if (dateState.selectedDateMillis != null) Cyan else TextSecondary) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Surface1,
                titleContentColor = TextPrimary,
                headlineContentColor = TextPrimary,
                weekdayContentColor = TextSecondary,
                subheadContentColor = TextSecondary,
                yearContentColor = TextPrimary,
                currentYearContentColor = Cyan,
                selectedYearContentColor = Color.White,
                dayContentColor = TextPrimary,
                selectedDayContainerColor = Violet,
                todayContentColor = Cyan,
                todayDateBorderColor = Cyan,
                dayInSelectionRangeContainerColor = Violet.copy(alpha = 0.2f),
            ),
        ) {
            DatePicker(state = dateState)
        }
    } else {
        // Time picker step
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Surface1,
            title = {
                Column {
                    Text("Pick a time", color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge)
                    val selDate = dateState.selectedDateMillis ?: today0.timeInMillis
                    Text(dueFmt.format(Date(selDate)), color = Cyan,
                        style = MaterialTheme.typography.labelLarge)
                }
            },
            text = {
                Box(contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        dateState.selectedDateMillis?.let { timeInMillis = it }
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPick(cal.timeInMillis)
                }) { Text("Set deadline", color = Mint) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Back", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text(title, color = TextPrimary) },
        text = { VaultTextField(name, { name = it }, "List name") },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text("OK", color = Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}
