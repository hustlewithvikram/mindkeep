package com.vikram.mindkeep.feature

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vikram.mindkeep.data.MindKeepStore
import com.vikram.mindkeep.data.MindNote
import com.vikram.mindkeep.data.MindReminder
import com.vikram.mindkeep.data.MindTask
import com.vikram.mindkeep.data.MindReminderWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class MindPage { HOME, ASSISTANT, SEARCH, TASKS, REMINDERS, CONNECTORS, UPDATER, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindKeepApp() {
    val context = LocalContext.current
    var introDone by remember { mutableStateOf(MindKeepStore.hasCompletedIntro(context)) }
    var splashVisible by remember { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf(MindPage.HOME) }
    var notes by remember { mutableStateOf(MindKeepStore.loadNotes(context)) }
    var tasks by remember { mutableStateOf(MindKeepStore.loadTasks(context)) }
    var reminders by remember { mutableStateOf(MindKeepStore.loadReminders(context)) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(650)
        splashVisible = false
    }

    if (splashVisible) {
        SplashScreen()
        return
    }
    if (!introDone) {
        IntroScreen(onContinue = {
            MindKeepStore.setIntroCompleted(context)
            introDone = true
        })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(selected = page == MindPage.ASSISTANT, onClick = { page = MindPage.ASSISTANT }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("Assistant") })
                NavigationBarItem(selected = page in setOf(MindPage.HOME, MindPage.SEARCH, MindPage.TASKS, MindPage.REMINDERS, MindPage.CONNECTORS), onClick = { page = MindPage.HOME }, icon = { Icon(Icons.Default.GraphicEq, null) }, label = { Text("Activity") })
                NavigationBarItem(selected = page in setOf(MindPage.SETTINGS, MindPage.UPDATER), onClick = { page = MindPage.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
        floatingActionButton = {
            if (page == MindPage.HOME || page == MindPage.TASKS || page == MindPage.REMINDERS) {
                FloatingActionButton(onClick = { when (page) {
                    MindPage.HOME -> showNoteDialog = true
                    MindPage.TASKS -> showTaskDialog = true
                    MindPage.REMINDERS -> showReminderDialog = true
                    else -> Unit
                } }) { Icon(Icons.Default.Add, contentDescription = "Add") }
            }
        }
    ) { padding ->
        AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mindkeep-page", modifier = Modifier.padding(padding)) { target ->
            val edgeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp
            when (target) {
                MindPage.HOME -> HomePage(notes, tasks, reminders, { showNoteDialog = true }, { page = MindPage.TASKS }, { page = MindPage.REMINDERS }, { page = MindPage.SEARCH }, { askForNotifications() }, { page = MindPage.CONNECTORS }, edgeTop)
                MindPage.ASSISTANT -> AssistantPage({ page = MindPage.SETTINGS }, edgeTop)
                MindPage.SEARCH -> SearchPage(notes, tasks, edgeTop)
                MindPage.TASKS -> TasksPage(tasks, { task -> tasks = tasks.map { if (it.id == task.id) it.copy(isDone = !it.isDone) else it }; MindKeepStore.saveTasks(context, tasks) }, { task -> tasks = tasks.filterNot { it.id == task.id }; MindKeepStore.saveTasks(context, tasks) }, { page = MindPage.REMINDERS }, edgeTop)
                MindPage.REMINDERS -> RemindersPage(reminders, { reminder -> reminders = reminders.map { if (it.id == reminder.id) it.copy(isDone = !it.isDone) else it }; MindKeepStore.saveReminders(context, reminders); WorkManager.getInstance(context).cancelUniqueWork(reminder.id) }, { reminder -> reminders = reminders.filterNot { it.id == reminder.id }; MindKeepStore.saveReminders(context, reminders); WorkManager.getInstance(context).cancelUniqueWork(reminder.id) }, edgeTop)
                MindPage.CONNECTORS -> ConnectorsPage(edgeTop)
                MindPage.UPDATER -> UpdaterScreen(edgeTop)
                MindPage.SETTINGS -> SettingsPage({ askForNotifications() }, {
                    context.getSharedPreferences("mindkeep_local_v1", 0).edit().putBoolean("intro_complete", false).apply()
                    introDone = false
                }, { page = MindPage.UPDATER }, { page = MindPage.CONNECTORS }, edgeTop)
            }
        }
    }

    if (showNoteDialog) {
        CreateItemDialog(title = "Capture a thought", hint = "What do you want to remember?", confirmLabel = "Save note", onDismiss = { showNoteDialog = false }) { title, body ->
            notes = listOf(MindNote(title = title, body = body)) + notes
            MindKeepStore.saveNotes(context, notes)
            showNoteDialog = false
        }
    }
    if (showTaskDialog) {
        CreateItemDialog(title = "New task", hint = "What needs to get done?", confirmLabel = "Add task", singleLine = true, onDismiss = { showTaskDialog = false }) { title, _ ->
            tasks = listOf(MindTask(title = title)) + tasks
            MindKeepStore.saveTasks(context, tasks)
            showTaskDialog = false
        }
    }
    if (showReminderDialog) {
        ReminderDialog(onDismiss = { showReminderDialog = false }) { title, delayMinutes ->
            val reminder = MindReminder(title = title, dueAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong()))
            reminders = listOf(reminder) + reminders
            MindKeepStore.saveReminders(context, reminders)
            val input = Data.Builder().putString(MindReminderWorker.KEY_TITLE, title).putString(MindReminderWorker.KEY_ID, reminder.id).build()
            val request = OneTimeWorkRequestBuilder<MindReminderWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(input)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, request)
            askForNotifications()
            showReminderDialog = false
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(42.dp))
            }
            Text("MindKeep", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("A little more room in your mind.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntroScreen(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("MindKeep", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(36.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(58.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Make space for\nwhat matters.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text("A private place for your thoughts, plans and reminders. Capture now, find them whenever you need.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IntroPoint("Works offline", "Your essentials stay available without internet.")
                    IntroPoint("Private by default", "Your notes and tasks stay on this device.")
                    IntroPoint("One place for everything", "Collect ideas, tasks and reminders together.")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
                    Text("Get started", style = MaterialTheme.typography.titleMedium)
                }
                Text("No account required. Your space is yours.", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IntroPoint(title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomePage(
    notes: List<MindNote>, tasks: List<MindTask>, reminders: List<MindReminder>,
    onAddNote: () -> Unit, onOpenTasks: () -> Unit, onOpenReminders: () -> Unit,
    onOpenSearch: () -> Unit, onRequestNotifications: () -> Unit,
    onOpenConnectors: () -> Unit, topPadding: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val pendingTasks = tasks.count { !it.isDone }
    val upcoming = reminders.filter { !it.isDone }.minByOrNull { it.dueAt }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("I’m listening.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Your personal context, quietly organised.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                        Text("BACKGROUND LISTENER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("Setup pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("MindKeep is ready to connect", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Choose what MindKeep can listen to and which services it can use. Background listening and AI are not active until their services are implemented and enabled.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onOpenConnectors, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Hub, null); Spacer(Modifier.width(8.dp)); Text("Manage integrations") }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAddNote),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick capture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Save an idea before it slips away.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        FilledTonalButton(onClick = onAddNote) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Write a note") }
                    }
                    Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Open tasks", pendingTasks.toString(), Icons.Default.TaskAlt, Modifier.weight(1f), onOpenTasks)
                SummaryCard("Saved notes", notes.size.toString(), Icons.Default.Bookmark, Modifier.weight(1f), onOpenSearch)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Your next reminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenReminders) { Text("See all") }
            }
            if (upcoming == null) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Nothing waiting for you", fontWeight = FontWeight.SemiBold)
                            Text("Add a reminder when something needs your attention.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onOpenReminders) { Text("Add") }
                    }
                }
            } else {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(upcoming.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Due " + SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(upcoming.dueAt)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recently captured", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenSearch) { Text("View all") }
            }
        }
        if (notes.isEmpty()) {
            item { EmptyState("Your thoughts will live here", "Capture your first note to start building your personal library.", Icons.Default.Bookmark) }
        } else {
            items(notes.take(3), key = { it.id }) { note -> NoteCard(note) }
        }
        item {
            val notificationEnabled = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!notificationEnabled) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Turn on reminders", fontWeight = FontWeight.SemiBold)
                            Text("Allow notifications so MindKeep can alert you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onRequestNotifications) { Text("Enable") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteCard(note: MindNote) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()).format(Date(note.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SearchPage(notes: List<MindNote>, tasks: List<MindTask>, topPadding: androidx.compose.ui.unit.Dp) {
    var query by rememberSaveable { mutableStateOf("") }
    val matchesNotes = notes.filter { (it.title + " " + it.body).contains(query, true) }
    val matchesTasks = tasks.filter { it.title.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
            }, placeholder = { Text("Search notes and tasks") }, shape = RoundedCornerShape(20.dp))
        }
        if (query.isBlank()) {
            item { EmptyState("Find anything in your mind", "Search across the notes and tasks you have saved on this device.", Icons.Default.Search) }
        } else {
            item { Text("Notes · ${matchesNotes.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (matchesNotes.isEmpty()) item { Text("No matching notes", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(matchesNotes, key = { "n${it.id}" }) { NoteCard(it) }
            item { Text("Tasks · ${matchesTasks.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (matchesTasks.isEmpty()) item { Text("No matching tasks", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(matchesTasks, key = { "t${it.id}" }) { task -> TaskRow(task, {}, {}) }
        }
    }
}

@Composable
private fun TasksPage(tasks: List<MindTask>, onToggle: (MindTask) -> Unit, onDelete: (MindTask) -> Unit, onOpenReminders: () -> Unit, topPadding: androidx.compose.ui.unit.Dp) {
    val open = tasks.filterNot { it.isDone }
    val done = tasks.filter { it.isDone }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${open.size} things to focus on", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Small steps count. Keep moving.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Default.TaskAlt, null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenReminders), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Manage reminders", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Text("To do · ${open.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (open.isEmpty()) item { EmptyState("All clear for now", "Add a task whenever something needs doing.", Icons.Default.CheckCircle) }
        items(open, key = { it.id }) { TaskRow(it, { onToggle(it) }, { onDelete(it) }) }
        if (done.isNotEmpty()) {
            item { Text("Completed · ${done.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(done, key = { it.id }) { TaskRow(it, { onToggle(it) }, { onDelete(it) }) }
        }
    }
}

@Composable
private fun TaskRow(task: MindTask, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
            Text(task.title, Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.bodyLarge, color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RemindersPage(reminders: List<MindReminder>, onToggle: (MindReminder) -> Unit, onDelete: (MindReminder) -> Unit, topPadding: androidx.compose.ui.unit.Dp) {
    val upcoming = reminders.filterNot { it.isDone }.sortedBy { it.dueAt }
    val completed = reminders.filter { it.isDone }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Don't keep it all in your head.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { Text("Reminders are stored on this device and scheduled locally.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (upcoming.isEmpty()) item { EmptyState("No reminders yet", "Create a reminder and MindKeep will notify you at the chosen time.", Icons.Default.Notifications) }
        items(upcoming, key = { it.id }) { reminder -> ReminderRow(reminder, onToggle = { onToggle(reminder) }, onDelete = { onDelete(reminder) }) }
        if (completed.isNotEmpty()) {
            item { Text("Completed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(completed, key = { it.id }) { reminder -> ReminderRow(reminder, onToggle = { onToggle(reminder) }, onDelete = { onDelete(reminder) }) }
        }
    }
}

@Composable
private fun ReminderRow(reminder: MindReminder, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(reminder.isDone, { onToggle() })
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(reminder.dueAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun SettingsPage(onRequestNotifications: () -> Unit, onResetIntro: () -> Unit, onOpenUpdater: () -> Unit, onOpenConnectors: () -> Unit, topPadding: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MindKeep", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Your personal space. Stored locally, designed to work offline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    AssistChip(onClick = {}, label = { Text("Offline-first") }, leadingIcon = { Icon(Icons.Default.Check, null) })
                }
            }
        }
        item { Text("Integrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { SettingRow(Icons.Default.Hub, "Connected services", "Google apps and MCP connectors", "Manage", onOpenConnectors) }
        item { Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item {
            SettingRow(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = if (notificationsAllowed) "Allowed — reminders can alert you" else "Not allowed — reminders will stay in the app",
                action = if (notificationsAllowed) "Allowed" else "Enable",
                onClick = onRequestNotifications
            )
        }
        item { Text("Your data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Stored on this device", fontWeight = FontWeight.SemiBold)
                    Text("Notes, tasks and reminders use local app storage. MindKeep does not need an account or internet connection for these features.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { SettingRow(Icons.Default.AutoAwesome, "App updater", "Development and production channels", "Open", onOpenUpdater) }
        item { Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { SettingRow(Icons.Default.Bookmark, "Welcome screen", "Show the introduction again", "Open", onResetIntro) }
        item { Text("MindKeep 0.1.0 · Built for your mind.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CreateItemDialog(
    title: String, hint: String, confirmLabel: String, singleLine: Boolean = false,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title") }, placeholder = { Text(hint) })
                if (!singleLine) OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5, label = { Text("Details (optional)") })
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), body.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReminderDialog(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var delay by rememberSaveable { mutableStateOf(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Remind me to…") })
                Text("When should we remind you?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 1440).forEach { minutes ->
                        FilterChip(
                            selected = delay == minutes,
                            onClick = { delay = minutes },
                            label = { Text(when (minutes) { 15 -> "15 min"; 30 -> "30 min"; 60 -> "1 hour"; else -> "Tomorrow" }) }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(title.trim(), delay) }, enabled = title.isNotBlank()) { Text("Set reminder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun AssistantPage(onOpenSettings: () -> Unit, topPadding: androidx.compose.ui.unit.Dp) {
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Your AI, always within reach.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("MindKeep is designed to observe permitted context, organise what matters and act through the tools you connect.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                    Text("On-device AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Text("The background listener, on-device model runtime and tool execution are not wired yet. No background monitoring is happening yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(onClick = {}, enabled = false, label = { Text("Model not installed") })
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("AI and app settings") }
            }
        }
        Text("What MindKeep will help with", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        FeaturePreviewRow("Listen", "Capture context only from sources you explicitly enable.")
        FeaturePreviewRow("Connect", "Use Google services and MCP tools with your permission.")
        FeaturePreviewRow("Act", "Create reminders, calendar events and tasks when asked.")
        Text("Your content stays on this device. Cloud AI is not used as a fallback.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun FeaturePreviewRow(title: String, description: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
