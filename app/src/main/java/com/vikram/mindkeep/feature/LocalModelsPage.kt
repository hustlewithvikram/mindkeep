package com.vikram.mindkeep.feature

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikram.mindkeep.core.ai.LocalAiCatalog
import com.vikram.mindkeep.core.ai.LocalAiModel
import com.vikram.mindkeep.core.ai.LocalAiPreferences
import com.vikram.mindkeep.core.ai.LocalModelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalModelsPage(topPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("mindkeep_model_downloads", Context.MODE_PRIVATE) }
    var autoSelect by remember { mutableStateOf(LocalAiPreferences.autoSelectEnabled(context)) }
    var defaultId by remember { mutableStateOf(LocalAiPreferences.defaultModelId(context)) }
    var downloadId by remember { mutableStateOf(prefs.getLong("active_id", -1L).takeIf { it > 0 }) }
    var downloadModelId by remember { mutableStateOf(prefs.getString("active_model", null)) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val installed = remember(refresh) { LocalAiCatalog.models.filter { it.isInstalled(context) } }
    val activeModel = remember(autoSelect, defaultId, refresh, installed) { if (autoSelect) LocalAiCatalog.autoSelect(context, installed) else LocalAiCatalog.byId(defaultId)?.takeIf { it.isInstalled(context) } }
    val availableRam = LocalAiCatalog.availableRamGb(context)
    val availableStorage = LocalAiCatalog.availableStorageGb(context)

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val state = withContext(Dispatchers.IO) {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) null else Triple(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    )
                }
            }
            if (state == null) {
                LocalAiCatalog.byId(downloadModelId)?.file(context)?.delete()
                downloadStatus = "Download record not found. The incomplete model was removed."
                finishModelDownload(prefs); downloadId = null; downloadModelId = null; refresh++
                break
            }
            if (state.third > 0) downloadPercent = ((state.second * 100) / state.third).toInt().coerceIn(0, 100)
            when (state.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val model = LocalAiCatalog.byId(downloadModelId)
                    if (model?.isInstalled(context) == true) {
                        downloadStatus = "${model.name} is downloaded and ready."
                        refresh++
                    } else {
                        model?.file(context)?.delete()
                        downloadStatus = "The model file was incomplete and has been removed."
                    }
                    finishModelDownload(prefs); downloadId = null; downloadModelId = null
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    LocalAiCatalog.byId(downloadModelId)?.file(context)?.delete()
                    downloadStatus = "Download failed. The incomplete file was removed."
                    finishModelDownload(prefs); downloadId = null; downloadModelId = null
                    break
                }
            }
            delay(850)
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
        prefs.edit().putLong("active_id", id).putString("active_model", model.id).apply()
        downloadModelId = model.id; downloadPercent = 0; downloadStatus = "Downloading ${model.name}…"; downloadId = id
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPadding, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Choose, download and manage the intelligence that powers your assistant.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(9.dp))
                        Text("YOUR ON-DEVICE AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .55f)) {
                            Text(if (activeModel != null) "Ready" else "Not configured", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(activeModel?.name ?: "Choose your first model", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    Text(activeModel?.description ?: "Download a model below. Inference and chat history stay on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModelMetric("Free RAM", "${availableRam} GB")
                        ModelMetric("Free storage", "${availableStorage} GB")
                        ModelMetric("Installed", installed.size.toString())
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Automatically select a model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Use the most capable installed model that fits the available RAM estimate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoSelect, onCheckedChange = {
                            if (!it && defaultId == null && activeModel != null) {
                                defaultId = activeModel.id
                                LocalAiPreferences.setDefaultModelId(context, activeModel.id)
                            }
                            autoSelect = it
                            LocalAiPreferences.setAutoSelectEnabled(context, it)
                            refresh++
                        })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    Text("Assistant model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activeModel?.name ?: "No compatible downloaded model", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Model library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Only download what you need.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text("${installed.size} installed") })
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
                    downloadStatus = "${model.name} is now the default assistant model."
                    refresh++
                },
                onDelete = {
                    model.file(context).delete()
                    if (defaultId == model.id) {
                        defaultId = null
                        context.getSharedPreferences("mindkeep_ai_preferences", 0).edit().remove("default_model_id").apply()
                    }
                    scope.launch { LocalModelRuntime.unload(); refresh++ }
                }
            )
        }
        if (downloadStatus != null) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(downloadStatus!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    if (downloadId != null) {
                        LinearProgressIndicator(progress = { downloadPercent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("$downloadPercent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        item {
            Text("Models are downloaded from their published repositories. MindKeep does not send prompts to a cloud AI service.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun finishModelDownload(prefs: android.content.SharedPreferences) {
    prefs.edit().remove("active_id").remove("active_model").apply()
}

@Composable
private fun ModelCard(
    model: LocalAiModel, installed: Boolean, selected: Boolean, recommended: Boolean,
    downloading: Boolean, downloadEnabled: Boolean, progress: Int,
    onDownload: () -> Unit, onSelect: () -> Unit, onDelete: () -> Unit
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
                ModelSpec("Suggested RAM", "${model.minimumFreeRamGb}+ GB")
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

@Composable
private fun ModelMetric(label: String, value: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .48f)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}
