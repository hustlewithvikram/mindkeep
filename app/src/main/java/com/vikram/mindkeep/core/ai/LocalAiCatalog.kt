package com.vikram.mindkeep.core.ai
import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

data class LocalAiModel(val id:String,val name:String,val family:String,val description:String,val fileName:String,val downloadUrl:String,val sizeLabel:String,val sizeBytes:Long,val minimumFreeRamGb:Int,val contextTokens:Int,val license:String,val tier:String) {
 fun file(context:Context)=File(context.getExternalFilesDir("models") ?: File(context.filesDir,"models"),fileName)
 fun isInstalled(context:Context)=file(context).exists() && file(context).length()>0
}
object LocalAiCatalog {
 val models=listOf(
  LocalAiModel("qwen3-0.6b","Qwen 3 · 0.6B","Qwen","Small, fast everyday assistant. Best starting point for most phones.","Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm","https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm","328 MB",344_000_000L,3,4096,"Apache 2.0","Recommended"),
  LocalAiModel("qwen3-1.7b","Qwen 3 · 1.7B","Qwen","Stronger reasoning and writing. Needs more memory and storage.","Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm","https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm","932 MB",977_000_000L,5,4096,"Apache 2.0","Advanced"),
  LocalAiModel("gemma4-e2b","Gemma 4 · E2B","Gemma","Larger multimodal-capable model for capable devices.","gemma-4-E2B-it-gpu.litertlm","https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm","2.34 GB",2_512_000_000L,8,8192,"Apache 2.0","Powerful")
 )
 fun byId(id:String?)=models.firstOrNull{it.id==id}
 fun availableRamGb(context:Context):Int { val m=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val i=ActivityManager.MemoryInfo();m.getMemoryInfo(i);return (i.availMem/(1024L*1024L*1024L)).toInt() }
 fun availableStorageBytes(context:Context):Long { val p=context.getExternalFilesDir("models") ?: context.filesDir;return StatFs(p.absolutePath).availableBytes }
 fun availableStorageGb(context:Context):Long = availableStorageBytes(context)/(1024L*1024L*1024L)
 fun autoSelect(context:Context,installed:List<LocalAiModel>):LocalAiModel? { if(installed.isEmpty())return null;val ram=availableRamGb(context);return installed.filter{ram>=it.minimumFreeRamGb}.maxByOrNull{it.minimumFreeRamGb} }
}
object LocalAiPreferences {
 private const val PREFS="mindkeep_ai_preferences"
 private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 fun defaultModelId(c:Context)=prefs(c).getString("default_model_id",null)
 fun setDefaultModelId(c:Context,id:String){prefs(c).edit().putString("default_model_id",id).apply()}
 fun autoSelectEnabled(c:Context)=prefs(c).getBoolean("auto_select_model",true)
 fun setAutoSelectEnabled(c:Context,enabled:Boolean){prefs(c).edit().putBoolean("auto_select_model",enabled).apply()}
 fun activeModel(c:Context):LocalAiModel? { val installed=LocalAiCatalog.models.filter{it.isInstalled(c)};if(autoSelectEnabled(c))return LocalAiCatalog.autoSelect(c,installed);return LocalAiCatalog.byId(defaultModelId(c))?.takeIf{it.isInstalled(c)} ?: installed.firstOrNull() }
}
