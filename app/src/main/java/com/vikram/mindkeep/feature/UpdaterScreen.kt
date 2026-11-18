package com.vikram.mindkeep.feature
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private const val RELEASE_API="https://api.github.com/repos/hustlewithvikram/mindkeep/releases"
private data class UpdateRelease(val tag:String,val commit:String,val apkUrl:String,val notes:String)

@Composable
fun UpdaterScreen(topPadding:Dp) {
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var channel by remember{mutableStateOf("Development")}
 var release by remember{mutableStateOf<UpdateRelease?>(null)}
 var checking by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("Checking the selected channel…")}
 var downloadId by remember{mutableStateOf<Long?>(null)}
 var downloadProgress by remember{mutableIntStateOf(0)}
 var apkReady by remember{mutableStateOf(false)}
 var installDialog by remember{mutableStateOf(false)}
 val apkFile=remember{File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"mindkeep-update.apk")}

 fun checkAgain() {
  release=null
  checking=true
  scope.launch {
   val result=withContext(Dispatchers.IO){runCatching{
    val endpoint=if(channel=="Development") "$RELEASE_API/tags/debug-latest" else "$RELEASE_API/latest"
    val connection=URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout=15000;connection.readTimeout=15000
    connection.setRequestProperty("Accept","application/vnd.github+json")
    connection.setRequestProperty("User-Agent","MindKeep-Updater")
    try {
     if(connection.responseCode !in 200..299)error("No published ${channel.lowercase()} release was found.")
     val json=JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
     val assets=json.getJSONArray("assets")
     var apkUrl:String?=null
     for(i in 0 until assets.length()){val a=assets.getJSONObject(i);if(a.getString("name").endsWith(".apk"))apkUrl=a.getString("browser_download_url")}
     val notes=json.optString("body")
     val commit=Regex("(?im)^- Commit:\\s*([0-9a-f]{7,40})").find(notes)?.groupValues?.get(1) ?: json.optString("target_commitish",json.optString("tag_name"))
     UpdateRelease(json.optString("tag_name"),commit,apkUrl ?: error("This release does not contain an APK."),notes)
    }finally{connection.disconnect()}
   }}
   release=result.getOrNull()
   status=result.fold({if(it.commit.startsWith(BuildConfig.GIT_COMMIT.take(7),true))"You're already on the latest build." else "A newer build is available."},{it.message ?: "Could not check for updates. Check your connection."})
   checking=false
  }
 }
 fun installApk() {
  if(!apkFile.exists()){apkReady=false;status="Downloaded APK is missing.";return}
  if(!context.packageManager.canRequestPackageInstalls()){
   context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
   status="Allow MindKeep to install apps, then tap Install APK again.";return
  }
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",apkFile)
  context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
 }

 DisposableEffect(context,downloadId) {
  val receiver=object:BroadcastReceiver(){override fun onReceive(ctx:Context?,intent:Intent?){
   val id=intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1L) ?: -1L
   if(id!=downloadId)return
   val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
   dm.query(DownloadManager.Query().setFilterById(id)).use{cursor->
    apkReady=cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL && apkFile.exists() && apkFile.length()>0
   }
   status=if(apkReady)"Download complete. Ready to install." else "Download failed. Please try again."
   installDialog=apkReady
   downloadId=null
  }}
  ContextCompat.registerReceiver(context,receiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),ContextCompat.RECEIVER_EXPORTED)
  onDispose{runCatching{context.unregisterReceiver(receiver)}}
 }
 LaunchedEffect(Unit){apkReady=apkFile.exists() && apkFile.length()>0;checkAgain()}
 LaunchedEffect(channel){checkAgain()}
 LaunchedEffect(downloadId){
  val id=downloadId ?: return@LaunchedEffect
  while(true){
   val progress=withContext(Dispatchers.IO){
    val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.query(DownloadManager.Query().setFilterById(id)).use{cursor->
     if(!cursor.moveToFirst())null else {
      val got=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
      val total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
      if(total>0)((got*100)/total).toInt().coerceIn(0,100) else 0
     }
    }
   } ?: break
   downloadProgress=progress
   delay(700)
  }
 }

 Column(Modifier.fillMaxSize().padding(start=20.dp,end=20.dp,top=topPadding,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text("Updates",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
   Text("Choose where MindKeep gets its next build.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){
   listOf("Development","Production").forEachIndexed{i,label->SegmentedButton(channel==label,{channel=label},SegmentedButtonDefaults.itemShape(i,2)){Text(label)}}
  }
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   UpdateInfoPanel("INSTALLED BUILD",BuildConfig.VERSION_NAME,BuildConfig.GIT_COMMIT.take(10),"This device",Modifier.weight(1f))
   UpdateInfoPanel("AVAILABLE BUILD",release?.tag ?: "Not found",release?.commit?.take(10) ?: "—",channel,Modifier.weight(1f))
  }
  Card(shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
   Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
     Icon(if(apkReady)Icons.Default.CheckCircle else Icons.Default.CloudDownload,null,tint=if(apkReady)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
     Text("Update status",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
     Spacer(Modifier.weight(1f))
     if(checking)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
    }
    Text(status,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(downloadId!=null)LinearProgressIndicator(progress={downloadProgress/100f},modifier=Modifier.fillMaxWidth())
    if(release?.notes?.isNotBlank()==true){
     HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
     Text("Release notes",style=MaterialTheme.typography.labelLarge)
     Text(release!!.notes.take(700),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=6)
    }
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedButton(onClick={checkAgain()},enabled=!checking,modifier=Modifier.weight(1f)){Text("Check again",maxLines=1)}
   OutlinedButton(onClick={apkFile.delete();apkReady=false;installDialog=false;status="Downloaded APK discarded."},enabled=apkReady,modifier=Modifier.weight(1f)){Icon(Icons.Default.Storage,null);Spacer(Modifier.width(4.dp));Text("Discard")}
   Button(onClick={installApk()},enabled=apkReady,modifier=Modifier.weight(1f)){Text("Install")}
  }
  Button(onClick={
   val item=release ?: return@Button
   apkFile.delete()
   val req=DownloadManager.Request(Uri.parse(item.apkUrl)).setTitle("MindKeep update").setDescription("Downloading ${item.tag}")
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"mindkeep-update.apk")
    .setAllowedOverMetered(true).setAllowedOverRoaming(false)
   downloadId=(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
   status="Downloading ${item.tag}…"
  },enabled=release!=null && !apkReady && downloadId==null,modifier=Modifier.fillMaxWidth()){
   Icon(Icons.Default.CloudDownload,null);Spacer(Modifier.width(8.dp));Text("Download update")
  }
  Text("Android handles the installer. After successful package replacement, MindKeep removes the downloaded APK automatically.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline)
 }
 if(installDialog)AlertDialog(onDismissRequest={installDialog=false},title={Text("Update downloaded")},text={Text("MindKeep ${release?.tag ?: ""} is ready. Install it now?")},confirmButton={Button(onClick={installDialog=false;installApk()}){Text("Install")}},dismissButton={TextButton(onClick={installDialog=false}){Text("Cancel")}})
}

@Composable
private fun UpdateInfoPanel(label:String,version:String,commit:String,detail:String,modifier:Modifier){
 Card(modifier,shape=RoundedCornerShape(23.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
  Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
   Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
   Text(version,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=2)
   Text("COMMIT",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline)
   Text(commit,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
   Text(detail,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
