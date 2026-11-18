package com.vikram.mindkeep.feature

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.vikram.mindkeep.core.ai.AssistantMemoryStore
import com.vikram.mindkeep.core.ai.AssistantWorkStore
import com.vikram.mindkeep.core.ai.AssistantWorkWorker
import com.vikram.mindkeep.core.ai.LocalAiCatalog
import com.vikram.mindkeep.core.ai.LocalAiModel
import com.vikram.mindkeep.core.ai.LocalAiPreferences
import com.vikram.mindkeep.core.ai.LocalModelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun AssistantPage(topPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoSelect by remember { mutableStateOf(LocalAiPreferences.autoSelectEnabled(context)) }
    var defaultId by remember { mutableStateOf(LocalAiPreferences.defaultModelId(context)) }
    var downloadId by remember { mutableStateOf<Long?>(context.getSharedPreferences("mindkeep_model_downloads", 0).getLong("active_id", -1L).takeIf { it > 0 }) }
    var downloadModelId by remember { mutableStateOf(context.getSharedPreferences("mindkeep_model_downloads", 0).getString("active_model", null)) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var loadingModel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var memoryCount by remember { mutableIntStateOf(AssistantMemoryStore.recent(context, 10000).size) }
    var recentTurns by remember { mutableStateOf(AssistantMemoryStore.recent(context, 8)) }
    var pendingCount by remember { mutableIntStateOf(AssistantWorkStore.pending(context).size) }
    val installed = LocalAiCatalog.models.filter { it.isInstalled(context) }
    val activeModel = if (autoSelect) LocalAiCatalog.autoSelect(context, installed) else LocalAiCatalog.byId(defaultId)?.takeIf { it.isInstalled(context) }
    val availableRam = LocalAiCatalog.availableRamGb(context)
    val availableStorage = LocalAiCatalog.availableStorageGb(context)

    LaunchedEffect(activeModel?.id) {
        if (activeModel != null) {
            AssistantWorkWorker.resumePending(context)
            pendingCount = AssistantWorkStore.pending(context).size
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            pendingCount = AssistantWorkStore.pending(context).size
            kotlinx.coroutines.delay(1600)
        }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val state = withContext(Dispatchers.IO) {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) null else {
                        val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        Triple(cursor.getInt(statusIndex), downloaded, total)
                    }
                }
            }
            if (state == null) {
                LocalAiCatalog.byId(downloadModelId)?.file(context)?.delete()
                downloadModelId = null
                downloadId = null
                context.getSharedPreferences("mindkeep_model_downloads", 0).edit().remove("active_id").remove("active_model").apply()
                downloadStatus = "Download no longer exists. The incomplete file was removed."
                break
            }
            if (state.third > 0) downloadPercent = ((state.second * 100) / state.third).toInt().coerceIn(0, 100)
            when (state.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val downloadedModel = LocalAiCatalog.byId(downloadModelId)
                    if (downloadedModel?.isInstalled(context) == true) {
                        downloadStatus = "Model download completed."
                        AssistantWorkWorker.resumePending(context)
                        pendingCount = AssistantWorkStore.pending(context).size
                    } else {
                        downloadedModel?.file(context)?.delete()
                        downloadStatus = "The downloaded file was incomplete and has been removed. Try again."
                    }
                    downloadId = null
                    downloadModelId = null
                    context.getSharedPreferences("mindkeep_model_downloads", 0).edit().remove("active_id").remove("active_model").apply()
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    LocalAiCatalog.byId(downloadModelId)?.file(context)?.delete()
                    downloadModelId = null
                    downloadStatus = "Download failed. The incomplete model file was removed."
                    downloadId = null
                    context.getSharedPreferences("mindkeep_model_downloads", 0).edit().remove("active_id").remove("active_model").apply()
                    break
                }
            }
            delay(900)
        }
    }

    fun startDownload(model: LocalAiModel) {
        if (LocalAiCatalog.availableStorageBytes(context) < (model.sizeBytes * 1.1).toLong()) {
            downloadStatus = "Not enough free storage. Free up at least ${model.sizeLabel} plus temporary download space."
            return
        }
        val target = model.file(context)
        target.parentFile?.mkdirs()
        target.delete()
        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("MindKeep · ${model.name}")
            .setDescription("Downloading on-device AI model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "models", model.fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        context.getSharedPreferences("mindkeep_model_downloads", 0).edit()
            .putLong("active_id", id).putString("active_model", model.id).apply()
        downloadModelId = model.id
        downloadPercent = 0
        downloadStatus = "Downloading ${model.name}…"
        downloadId = id
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Assistant", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Your private AI runs on this device. No cloud fallback.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(9.dp))
                        Text("LOCAL INTELLIGENCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
                            Text(if (activeModel != null) "Ready to load" else "No model", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(activeModel?.name ?: if (installed.isNotEmpty()) "Free up device memory" else "Choose your first model", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    Text(activeModel?.description ?: if (installed.isNotEmpty()) "None of the installed models currently fit the free-memory estimate. Close other apps or switch to manual model selection." else "Download a model below. MindKeep will run it locally and keep your conversations on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModelMetric("Free RAM", "${availableRam} GB")
                        ModelMetric("Model storage", "${availableStorage} GB free")
                        ModelMetric("Saved turns", memoryCount.toString())
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Auto-select model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Choose the largest installed model under a conservative free-RAM estimate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoSelect, onCheckedChange = {
                            if (!it && defaultId == null && activeModel != null) {
                                defaultId = activeModel.id
                                LocalAiPreferences.setDefaultModelId(context, activeModel.id)
                            }
                            autoSelect = it
                            LocalAiPreferences.setAutoSelectEnabled(context, it)
                        })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("Selected model", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activeModel?.name ?: "No compatible installed model", style = MaterialTheme.typography.titleMedium)
                    Text(if (autoSelect) "Automatic selection is active. Turn it off to pin a default model." else "Your selected model will be used whenever it is installed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Model library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Download only the models you want to keep.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text("${installed.size} installed") }, leadingIcon = { Icon(Icons.Default.Memory, null) })
            }
        }
        items(LocalAiCatalog.models, key = { it.id }) { model ->
            ModelCard(
                model = model,
                installed = model.isInstalled(context),
                selected = activeModel?.id == model.id,
                recommended = model.minimumFreeRamGb <= availableRam,
                downloading = downloadModelId == model.id && downloadId != null,
                downloadEnabled = downloadId == null,
                progress = if (downloadModelId == model.id) downloadPercent else 0,
                onDownload = { if (downloadId == null) startDownload(model) },
                onSelect = {
                    LocalAiPreferences.setDefaultModelId(context, model.id)
                    defaultId = model.id
                    autoSelect = false
                    LocalAiPreferences.setAutoSelectEnabled(context, false)
                    downloadStatus = "${model.name} set as the default model."
                },
                onDelete = {
                    model.file(context).delete()
                    if (defaultId == model.id) {
                        defaultId = null
                        context.getSharedPreferences("mindkeep_ai_preferences", 0).edit().remove("default_model_id").apply()
                    }
                    scope.launch { LocalModelRuntime.unload() }
                }
            )
        }
        if (downloadStatus != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(downloadStatus!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (downloadId != null) LinearProgressIndicator(progress = { downloadPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Try your assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your conversation history is stored locally. Relevant older turns are retrieved into each prompt so the model can remember beyond a single context window.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    recentTurns.forEach { turn ->
                        Surface(shape = RoundedCornerShape(16.dp), color = if (turn.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(turn.text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = if (turn.role == "user") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (answer.isNotBlank() && generating) Text(answer, style = MaterialTheme.typography.bodyMedium)
                    if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                        placeholder = { Text("Ask something…") }, shape = RoundedCornerShape(18.dp)
                    )
                    Button(
                        onClick = {
                            val userText = prompt.trim()
                            if (userText.isEmpty() || activeModel == null || generating) return@Button
                            prompt = ""
                            answer = ""
                            error = null
                            generating = true
                            scope.launch {
                                try {
                                    loadingModel = true
                                    LocalModelRuntime.load(activeModel.file(context))
                                    loadingModel = false
                                    val memory = AssistantMemoryStore.buildContext(context, userText, activeModel.contextTokens * 3)
                                    AssistantMemoryStore.append(context, "user", userText)
                                    recentTurns = AssistantMemoryStore.recent(context, 8)
                                    val fullPrompt = if (memory.isBlank()) userText else "Relevant conversation memory:\n$memory\n\nCurrent request:\n$userText"
                                    LocalModelRuntime.resetConversation()
                                    LocalModelRuntime.generate(fullPrompt).collect { answer += it }
                                    AssistantMemoryStore.append(context, "assistant", answer)
                                    recentTurns = AssistantMemoryStore.recent(context, 8)
                                    memoryCount = AssistantMemoryStore.recent(context, 10000).size
                                } catch (t: Throwable) {
                                    error = t.message ?: "The local model could not run on this device."
                                } finally {
                                    loadingModel = false
                                    generating = false
                                }
                            }
                        },
                        enabled = activeModel != null && prompt.isNotBlank() && !generating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (generating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (loadingModel) "Loading model…" else if (generating) "Thinking…" else "Ask locally")
                    }
                    OutlinedButton(
                        onClick = {
                            val queuedPrompt = prompt.trim()
                            if (queuedPrompt.isNotEmpty()) {
                                val workId = AssistantWorkStore.enqueue(context, queuedPrompt)
                                if (LocalAiPreferences.activeModel(context) != null) AssistantWorkWorker.enqueue(context, workId)
                                prompt = ""
                                pendingCount = AssistantWorkStore.pending(context).size
                                downloadStatus = "Saved to background queue. It will resume when a compatible local model is ready."
                            }
                        },
                        enabled = prompt.isNotBlank() && !generating,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue in background") }
                    if (pendingCount > 0) Text("$pendingCount background task(s) waiting or processing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    if (activeModel == null) Text("Install a model to start chatting. Requests are not sent to a cloud service.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item {
            Text("Models are downloaded from the LiteRT Community on Hugging Face. Check each model’s licence and device requirements before downloading.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ModelMetric(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModelCard(
    model: LocalAiModel, installed: Boolean, selected: Boolean, recommended: Boolean,
    downloading: Boolean, downloadEnabled: Boolean, progress: Int, onDownload: () -> Unit, onSelect: () -> Unit, onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${model.family} · ${model.tier}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (selected) AssistChip(onClick = {}, label = { Text("Default") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) })
                else if (recommended) AssistChip(onClick = {}, label = { Text("Fits RAM") })
            }
            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ModelSpec("Download", model.sizeLabel)
                ModelSpec("Suggested free RAM", "${model.minimumFreeRamGb}+ GB")
                ModelSpec("Context", "${model.contextTokens / 1000}K")
            }
            Text("Licence: ${model.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (downloading) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Downloading · $progress%", style = MaterialTheme.typography.labelMedium)
            } else if (installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSelect, modifier = Modifier.weight(1f)) { Text(if (selected) "Selected" else "Set as default") }
                    OutlinedButton(onClick = onDelete) { Text("Remove") }
                }
            } else {
                Button(onClick = onDownload, enabled = downloadEnabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download model")
                }
            }
        }
    }
}

@Composable
private fun ModelSpec(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
