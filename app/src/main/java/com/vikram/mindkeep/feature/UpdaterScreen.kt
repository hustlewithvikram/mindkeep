package com.vikram.mindkeep.feature
import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vikram.mindkeep.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val API = "https://api.github.com/repos/hustlewithvikram/mindkeep"
private data class ReleaseInfo(val tag:String,val commit:String,val apk:String)

@Composable
fun UpdaterScreen(topPadding:Dp) {
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var channel by remember { mutableStateOf("Development") }
 var release by remember { mutableStateOf<ReleaseInfo?>(null) }
 var checking by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("Checking for updates…") }
 var downloadId by remember { mutableStateOf<Long?>(null) }
 var ready by remember { mutableStateOf(false) }
 var installDialog by remember { mutableStateOf(false) }
 val apk=remember { File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"mindkeep-update.apk") }
 fun check() {
  checking=true
  scope.launch {
   val result=withContext(Dispatchers.IO) { runCatching {
    val endpoint=if(channel=="Development") API+"/releases/tags/debug-latest" else API+"/releases/latest"
    val conn=URL(endpoint).openConnection() as HttpURLConnection
    conn.connectTimeout=12000; conn.readTimeout=12000
    conn.setRequestProperty("Accept","application/vnd.github+json")
    conn.setRequestProperty("User-Agent","MindKeep-Updater")
    try {
     if(conn.responseCode !in 200..299) error("No "+channel.lowercase()+" release is published.")
     val json=JSONObject(conn.inputStream.bufferedReader().use{it.readText()})
     val assets=json.getJSONArray("assets")
     var apkUrl:String?=null
     for(i in 0 until assets.length()) { val a=assets.getJSONObject(i); if(a.getString("name").endsWith(".apk")) apkUrl=a.getString("browser_download_url") }
     val body=json.optString("body")
     val commit=Regex("(?im)^- Commit:\\s*([0-9a-f]{7,40})").find(body)?.groupValues?.get(1) ?: json.optString("tag_name")
     ReleaseInfo(json.optString("tag_name"),commit,apkUrl ?: error("No APK asset in release."))
    } finally { conn.disconnect() }
   } }
   release=result.getOrNull()
   status=result.fold({ if(it.commit.startsWith(BuildConfig.GIT_COMMIT.take(7),true)) "This build is up to date." else "An update is available." },{it.message ?: "Could not check for updates."})
   checking=false
  }
 }
 fun install() {
  if(!apk.exists()) { ready=false; status="Downloaded APK not found."; return }
  if(!context.packageManager.canRequestPackageInstalls()) {
   context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)))
   status="Allow installs from MindKeep, then tap Install APK again."; return
  }
  val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",apk)
  context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
 }
 DisposableEffect(context,downloadId) {
  val receiver=object:BroadcastReceiver(){ override fun onReceive(ctx:Context?,intent:Intent?) {
   val id=intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1L) ?: -1L
   if(id!=downloadId) return
   val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
   dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
    ready=cur.moveToFirst() && cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL && apk.exists()
   }
   status=if(ready) "APK downloaded. Ready to install." else "Download failed."
   installDialog=ready
  }}
  ContextCompat.registerReceiver(context,receiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),ContextCompat.RECEIVER_NOT_EXPORTED)
  onDispose { runCatching{context.unregisterReceiver(receiver)} }
 }
 LaunchedEffect(Unit) { ready=apk.exists() && apk.length()>0; check() }
 LaunchedEffect(channel) { check() }
 Column(Modifier.fillMaxSize().padding(start=20.dp,end=20.dp,top=topPadding,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Text("App updater",style=MaterialTheme.typography.headlineLarge)
  Text("Install updates without leaving MindKeep.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
   listOf("Development","Production").forEachIndexed { i,label -> SegmentedButton(channel==label,{channel=label},SegmentedButtonDefaults.itemShape(i,2)){Text(label)} }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   UpdatePanel("CURRENT BUILD",BuildConfig.VERSION_NAME,BuildConfig.GIT_COMMIT.take(10),Modifier.weight(1f))
   UpdatePanel("AVAILABLE UPDATE",release?.tag ?: "—",release?.commit?.take(10) ?: "Not checked",Modifier.weight(1f))
  }
  Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("Update status",style=MaterialTheme.typography.titleMedium)
    if(checking) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(status,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
   OutlinedButton(onClick={check()},enabled=!checking,modifier=Modifier.weight(1f)){Text("Check again")}
   OutlinedButton(onClick={apk.delete();ready=false;installDialog=false;status="Downloaded APK discarded."},enabled=ready,modifier=Modifier.weight(1f)){Text("Discard APK")}
   Button(onClick={install()},enabled=ready,modifier=Modifier.weight(1f)){Text("Install APK")}
  }
  Button(onClick={
   val item=release ?: return@Button
   apk.delete()
   val req=DownloadManager.Request(Uri.parse(item.apk)).setTitle("MindKeep update").setDescription("Downloading "+item.tag)
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"mindkeep-update.apk")
   downloadId=(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
   status="Downloading update…"
  },enabled=release!=null && !ready,modifier=Modifier.fillMaxWidth()){Text("Download update")}
  Text("The downloaded APK is removed after a successful update on the next app launch. Android may ask once for install permission.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline)
 }
 if(installDialog) AlertDialog(onDismissRequest={installDialog=false},title={Text("Update downloaded")},text={Text("A MindKeep update is ready. Install it now?")},confirmButton={Button(onClick={installDialog=false;install()}){Text("Install")}},dismissButton={TextButton(onClick={installDialog=false;apk.delete();ready=false;status="Install cancelled and APK discarded."){Text("Cancel")}})
}

@Composable
private fun UpdatePanel(label:String,version:String,commit:String,modifier:Modifier) {
 Card(modifier,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
  Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
   Text(version,style=MaterialTheme.typography.titleLarge)
   Text("Commit",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text(commit,style=MaterialTheme.typography.bodySmall)
  }
 }
}
