package com.vikram.mindkeep.feature

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vikram.mindkeep.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASE_API = "https://api.github.com/repos/hustlewithvikram/mindkeep/releases"
private data class UpdateRelease(val tag: String, val commit: String, val apkUrl: String, val notes: String, val sizeBytes: Long)

@Composable
fun UpdaterScreen(topPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf("Development") }
    var release by remember { mutableStateOf<UpdateRelease?>(null) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking the selected channel…") }
    var upToDate by remember { mutableStateOf(false) }
    val apkFile = remember { File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "mindkeep-update.apk") }
    val updatePrefs = remember { context.getSharedPreferences("mindkeep_updater", Context.MODE_PRIVATE) }
    var downloadId by remember { mutableStateOf(updatePrefs.getLong("download_id", -1L).takeIf { it > 0 }) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadedBytes by remember { mutableLongStateOf(if (apkFile.exists()) apkFile.length() else 0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadSpeed by remember { mutableLongStateOf(0L) }
    var apkReady by remember { mutableStateOf(updatePrefs.getBoolean("apk_ready", false) && apkFile.exists()) }
    var installDialog by remember { mutableStateOf(false) }
    var lastBytes by remember { mutableLongStateOf(0L) }
    var lastSampleAt by remember { mutableLongStateOf(0L) }

    fun checkAgain() {
        checking = true
        upToDate = false
        release = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val endpoint = if (channel == "Development") "$RELEASE_API/tags/debug-latest" else "$RELEASE_API/latest"
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "MindKeep-Updater")
                try {
                    if (connection.responseCode !in 200..299) error("No published ${channel.lowercase()} release was found.")
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val assets = json.getJSONArray("assets")
                    var apkUrl: String? = null
                    var size = 0L
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            size = asset.optLong("size", 0L)
                        }
                    }
                    val notes = json.optString("body")
                    val commit = Regex("(?im)^- Commit:\\s*([0-9a-f]{7,40})").find(notes)?.groupValues?.get(1)
                        ?: json.optString("target_commitish", json.optString("tag_name"))
                    UpdateRelease(json.optString("tag_name"), commit, apkUrl ?: error("This release does not contain an APK."), notes, size)
                } finally { connection.disconnect() }
            } }
            release = result.getOrNull()
            val item = release
            val installedCommit = BuildConfig.GIT_COMMIT
            upToDate = item != null && item.commit.isNotBlank() &&
                (item.commit.startsWith(installedCommit.take(7), true) || installedCommit.startsWith(item.commit.take(7), true))
            status = when {
                result.isFailure -> result.exceptionOrNull()?.message ?: "Could not check for updates. Check your connection."
                upToDate -> "App is up to date"
                else -> "A newer build is available."
            }
            checking = false
        }
    }

    fun installApk() {
        if (!apkFile.exists()) { apkReady = false; status = "Downloaded APK is missing."; return }
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            status = "Allow MindKeep to install apps, then tap Install APK again."
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    DisposableEffect(context, downloadId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id != downloadId) return
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    apkReady = cursor.moveToFirst() &&
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL &&
                        apkFile.exists() && apkFile.length() > 0
                }
                downloadedBytes = if (apkFile.exists()) apkFile.length() else 0L
                status = if (apkReady) "Download complete. Ready to install." else "Download failed. Please try again."
                updatePrefs.edit().putBoolean("apk_ready", apkReady).remove("download_id").apply()
                if (!apkReady) apkFile.delete()
                installDialog = apkReady
                downloadId = null
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(Unit) { if (!apkReady && downloadId == null) apkFile.delete() }
    LaunchedEffect(channel) { checkAgain() }
    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        lastBytes = 0L
        lastSampleAt = SystemClock.elapsedRealtime()
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
                apkFile.delete(); apkReady = false; downloadId = null
                updatePrefs.edit().putBoolean("apk_ready", false).remove("download_id").apply()
                status = "Download record not found. The incomplete APK was removed."
                break
            }
            downloadedBytes = state.second
            if (state.third > 0) {
                totalBytes = state.third
                downloadProgress = ((state.second * 100) / state.third).toInt().coerceIn(0, 100)
            }
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastSampleAt
            if (elapsed >= 800) {
                downloadSpeed = ((state.second - lastBytes).coerceAtLeast(0L) * 1000L) / elapsed
                lastBytes = state.second
                lastSampleAt = now
            }
            when (state.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    apkReady = apkFile.exists() && apkFile.length() > 0
                    downloadedBytes = if (apkFile.exists()) apkFile.length() else 0L
                    status = if (apkReady) "Download complete. Ready to install." else "Downloaded APK is missing."
                    updatePrefs.edit().putBoolean("apk_ready", apkReady).remove("download_id").apply()
                    installDialog = apkReady; downloadId = null
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    apkFile.delete(); apkReady = false; downloadId = null
                    updatePrefs.edit().putBoolean("apk_ready", false).remove("download_id").apply()
                    status = "Download failed. Please try again."
                    break
                }
            }
            delay(700)
        }
    }

    val updateAvailable = release != null && !upToDate
    val blue = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF8AB4F8) else Color(0xFF2869C7)

    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("App updates", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Keep MindKeep current, or choose a release channel.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Development", "Production").forEachIndexed { index, label ->
                SegmentedButton(channel == label, { channel = label }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
            }
        }

        if (checking) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Checking ${channel.lowercase()} releases…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (upToDate) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
                    Text("App is up to date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UpdateInfoPanel("INSTALLED", BuildConfig.VERSION_NAME, BuildConfig.GIT_COMMIT.take(10), Modifier.weight(1f))
                UpdateInfoPanel("AVAILABLE", release?.tag ?: "Not found", release?.commit?.take(10) ?: "—", Modifier.weight(1f))
            }
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (apkReady) Icons.Default.CheckCircle else Icons.Default.CloudDownload, null, tint = if (apkReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        Text("Update status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    if (downloadId != null || apkReady) {
                        LinearProgressIndicator(
                            progress = { if (apkReady) 1f else downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DownloadMetric("Progress", "${if (apkReady) 100 else downloadProgress}%")
                            DownloadMetric("Speed", if (downloadId != null) "${formatBytes(downloadSpeed)}/s" else "Complete")
                            DownloadMetric("Update size", formatBytes(if (totalBytes > 0) totalBytes else release?.sizeBytes ?: 0L))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DownloadMetric("Downloaded", formatBytes(downloadedBytes))
                            DownloadMetric("APK on device", formatBytes(if (apkFile.exists()) apkFile.length() else downloadedBytes))
                        }
                    } else {
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (release?.notes?.isNotBlank() == true) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        Text("What’s new", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(release!!.notes.take(700), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 7)
                    }
                }
            }
            Button(
                onClick = {
                    val item = release ?: return@Button
                    apkFile.delete()
                    val request = DownloadManager.Request(Uri.parse(item.apkUrl))
                        .setTitle("MindKeep update")
                        .setDescription("Downloading ${item.tag}")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "mindkeep-update.apk")
                        .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                    downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    totalBytes = item.sizeBytes; downloadedBytes = 0L; downloadProgress = 0; downloadSpeed = 0L
                    updatePrefs.edit().putLong("download_id", downloadId!!).putBoolean("apk_ready", false).apply()
                    status = "Downloading ${item.tag}…"
                },
                enabled = updateAvailable && !apkReady && downloadId == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp))
                Text(if (downloadId != null) "Downloading update…" else "Download update")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { checkAgain() },
                    enabled = !checking && downloadId == null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Check", maxLines = 1) }
                Button(
                    onClick = {
                        apkFile.delete(); apkReady = false; downloadedBytes = 0L; installDialog = false
                        updatePrefs.edit().putBoolean("apk_ready", false).remove("download_id").apply()
                        status = "Downloaded APK discarded."
                    },
                    enabled = apkReady,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(5.dp)); Text("Discard", maxLines = 1) }
                Button(
                    onClick = { installApk() },
                    enabled = apkReady,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = Color.White)
                ) { Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(5.dp)); Text("Install", maxLines = 1) }
            }
            Text("The installer is handled by Android. MindKeep removes the downloaded APK after a successful update.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
    if (installDialog) AlertDialog(
        onDismissRequest = { installDialog = false },
        title = { Text("Update downloaded") },
        text = { Text("MindKeep ${release?.tag ?: ""} is ready to install.") },
        confirmButton = { Button(onClick = { installDialog = false; installApk() }) { Text("Install") } },
        dismissButton = { TextButton(onClick = { installDialog = false }) { Text("Later") } }
    )
}

@Composable
private fun UpdateInfoPanel(label: String, version: String, commit: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(version, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(commit, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0)
    else String.format(Locale.US, "%.1f MB", mb)
}
