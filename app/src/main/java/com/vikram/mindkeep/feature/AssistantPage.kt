package com.vikram.mindkeep.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Send
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
import com.vikram.mindkeep.core.ai.AssistantMemoryStore
import com.vikram.mindkeep.core.ai.AssistantWorkStore
import com.vikram.mindkeep.core.ai.AssistantWorkWorker
import com.vikram.mindkeep.core.ai.LocalAiPreferences
import com.vikram.mindkeep.core.ai.LocalModelRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantPage(topPadding: Dp, onOpenModels: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf("") }
    var turns by remember { mutableStateOf(runCatching { AssistantMemoryStore.recent(context, 100) }.getOrDefault(emptyList())) }
    var response by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var loadingModel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableIntStateOf(runCatching { AssistantWorkStore.pending(context).size }.getOrDefault(0)) }
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
                turns = AssistantMemoryStore.recent(context, 100)
                val fullPrompt = if (memory.isBlank()) userText else "Relevant conversation memory:\n$memory\n\nCurrent request:\n$userText"
                LocalModelRuntime.resetConversation()
                LocalModelRuntime.generate(fullPrompt).collect { response += it }
                AssistantMemoryStore.append(context, "assistant", response)
                turns = AssistantMemoryStore.recent(context, 100)
            } catch (t: Throwable) {
                error = t.message ?: "The local model could not complete this response."
            } finally {
                loadingModel = false
                generating = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = topPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Assistant", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("A private conversation, kept on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenModels) { Icon(Icons.Default.Tune, contentDescription = "Manage local models") }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(activeModel?.name ?: "No downloaded model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (activeModel != null) "On-device model · Offline" else "Download a model to start chatting", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onOpenModels) { Text("Models") }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            reverseLayout = false
        ) {
            if (messages.isEmpty() && response.isBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(66.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                        }
                        Text("What’s on your mind?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Ask a question, organise a thought, or work through an idea.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Help me plan my day", "Summarise a thought", "Break a task into steps").forEach { suggestion ->
                            OutlinedButton(
                                onClick = { sendMessage(suggestion) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
                            ) { Text(suggestion, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
            items(messages, key = { "${it.timestamp}-${it.role}-${it.text.hashCode()}" }) { turn ->
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
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
            shadowElevation = 3.dp
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (activeModel == null) {
                    Text("No model is ready. Open Local AI models in Settings to download one.", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message MindKeep") },
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(26.dp),
                        enabled = !generating
                    )
                    FilledIconButton(
                        onClick = { sendMessage(prompt) },
                        enabled = activeModel != null && prompt.isNotBlank() && !generating,
                        modifier = Modifier.size(52.dp)
                    ) {
                        if (generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Send, contentDescription = "Send message")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (pendingCount > 0) "$pendingCount background task(s) waiting or processing" else "Local AI · Your messages stay on this device",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pendingCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    )
                    if (prompt.isNotBlank()) {
                        TextButton(
                            onClick = {
                                val queuedPrompt = prompt.trim()
                                val workId = AssistantWorkStore.enqueue(context, queuedPrompt)
                                if (LocalAiPreferences.activeModel(context) != null) AssistantWorkWorker.enqueue(context, workId)
                                prompt = ""
                                pendingCount = AssistantWorkStore.pending(context).size
                            },
                            enabled = !generating
                        ) { Text("Queue for later") }
                    }
                }
            }
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
            Text(
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
