package com.vikram.mindkeep.feature

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vikram.mindkeep.core.ai.AssistantMemoryStore
import com.vikram.mindkeep.core.ai.AssistantWorkStore
import com.vikram.mindkeep.core.ai.AssistantWorkWorker
import com.vikram.mindkeep.core.ai.LocalAiPreferences
import com.vikram.mindkeep.core.ai.LocalModelRuntime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantPage(topPadding: Dp, onOpenModels: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("mindkeep_assistant_ui", android.content.Context.MODE_PRIVATE) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var turns by remember { mutableStateOf(runCatching { AssistantMemoryStore.recent(context, 200) }.getOrDefault(emptyList())) }
    var response by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var loadingModel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableIntStateOf(runCatching { AssistantWorkStore.pending(context).size }.getOrDefault(0)) }
    var showHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(preferences.getBoolean("chat_pinned", false)) }
    val activeModel = remember { runCatching { LocalAiPreferences.activeModel(context) }.getOrNull() }
    val messages = turns

    LaunchedEffect(activeModel?.id) {
        if (activeModel != null) {
            runCatching { AssistantWorkWorker.resumePending(context) }
            pendingCount = runCatching { AssistantWorkStore.pending(context).size }.getOrDefault(0)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            pendingCount = runCatching { AssistantWorkStore.pending(context).size }.getOrDefault(0)
            kotlinx.coroutines.delay(1800)
        }
    }

    fun clearConversation() {
        runCatching { AssistantMemoryStore.clear(context) }
        turns = emptyList()
        response = ""
        error = null
    }

    fun sendMessage(value: String) {
        val userText = value.trim()
        if (userText.isEmpty() || activeModel == null || generating) return
        prompt = ""
        response = ""
        error = null
        generating = true
        scope.launch {
            try {
                loadingModel = true
                LocalModelRuntime.load(activeModel.file(context))
                loadingModel = false
                val memory = AssistantMemoryStore.buildContext(context, userText, activeModel.contextTokens * 3)
                AssistantMemoryStore.append(context, "user", userText)
                turns = AssistantMemoryStore.recent(context, 200)
                val fullPrompt = if (memory.isBlank()) userText else "Relevant conversation memory:\n$memory\n\nCurrent request:\n$userText"
                LocalModelRuntime.resetConversation()
                LocalModelRuntime.generate(fullPrompt).collect { response += it }
                AssistantMemoryStore.append(context, "assistant", response)
                turns = AssistantMemoryStore.recent(context, 200)
            } catch (t: Throwable) {
                error = t.message ?: "The local model could not complete this response."
            } finally {
                loadingModel = false
                generating = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = topPadding + 66.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (messages.isEmpty() && response.isBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(66.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                        }
                        Text("What’s on your mind?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Ask a question, organise a thought, or work through an idea.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Help me plan my day", "Summarise a thought", "Break a task into steps").forEach { suggestion ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { sendMessage(suggestion) },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(18.dp)
                            ) { Text(suggestion, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
            itemsIndexed(messages, key = { index, turn -> "${turn.timestamp}-${turn.role}-${turn.text.hashCode()}-$index" }) { _, turn ->
                ChatMessageBubble(turn.role, turn.text, turn.timestamp)
            }
            if (generating && response.isBlank()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(if (loadingModel) "Loading your local model…" else "Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (response.isNotBlank() && generating) item { ChatMessageBubble("assistant", response, System.currentTimeMillis()) }
            if (error != null) item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(error!!, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Floating top controls; the conversation remains scrollable beneath them.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AssistantFloatingIcon(Icons.Default.Menu, "Chat history") { showHistory = true }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onOpenModels() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(activeModel?.name ?: "Choose model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(if (activeModel != null) "On-device · ${activeModel.contextTokens} ctx" else "No model selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Box {
                AssistantFloatingIcon(Icons.Default.MoreVert, "More chat actions") { showMenu = true }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "Unpin chat" else "Pin chat") },
                        leadingIcon = { Icon(Icons.Default.PushPin, null) },
                        onClick = { pinned = !pinned; preferences.edit().putBoolean("chat_pinned", pinned).apply(); showMenu = false }
                    )
                    DropdownMenuItem(text = { Text("New chat") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { clearConversation(); showMenu = false })
                    DropdownMenuItem(text = { Text("Delete chat") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { clearConversation(); showMenu = false })
                    DropdownMenuItem(text = { Text("Select model") }, leadingIcon = { Icon(Icons.Default.Tune, null) }, onClick = { showMenu = false; onOpenModels() })
                    DropdownMenuItem(text = { Text("Share conversation") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                        showMenu = false
                        val transcript = messages.joinToString("\n\n") { "${if (it.role == "user") "You" else "MindKeep"}: ${it.text}" }
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, transcript)
                        context.startActivity(Intent.createChooser(share, "Share conversation"))
                    })
                }
            }
        }

        // Floating composer without a card, footer label, or elevation.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message MindKeep") },
                minLines = 1,
                maxLines = 4,
                shape = CircleShape,
                enabled = !generating,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                )
            )
            FilledIconButton(
                onClick = { sendMessage(prompt) },
                enabled = activeModel != null && prompt.isNotBlank() && !generating,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Send, contentDescription = "Send message")
            }
        }
    }

    if (showHistory) {
        Dialog(onDismissRequest = { showHistory = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showHistory = false }) { Icon(Icons.Default.Close, contentDescription = "Close history") }
                        Text("Chat history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showHistory = false; clearConversation() }) { Icon(Icons.Default.Add, contentDescription = "New chat") }
                    }
                    if (messages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Your conversations will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(if (pinned) "PINNED CONVERSATION" else "RECENT CONVERSATION", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(messages.lastOrNull { it.role == "user" }?.text?.take(60) ?: "Conversation", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                            if (pinned) Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        Text("${messages.size} messages · This device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            itemsIndexed(messages.asReversed(), key = { index, turn -> "history-${turn.timestamp}-${turn.role}-${turn.text.hashCode()}-$index" }) { _, turn ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { prompt = turn.text; showHistory = false },
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (turn.role == "user") "You" else "MindKeep", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        Text(turn.text, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                        Text(SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()).format(Date(turn.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantFloatingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ChatMessageBubble(role: String, text: String, timestamp: Long) {
    val isUser = role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.fillMaxWidth(if (isUser) 0.88f else 1f), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 22.dp, topEnd = 22.dp,
                    bottomStart = if (isUser) 22.dp else 7.dp,
                    bottomEnd = if (isUser) 7.dp else 22.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
