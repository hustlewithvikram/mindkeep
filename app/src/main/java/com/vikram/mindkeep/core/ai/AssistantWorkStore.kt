package com.vikram.mindkeep.core.ai
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PendingAssistantWork(val id:String,val prompt:String,val status:String,val result:String,val createdAt:Long)
object AssistantWorkStore {
 private const val PREFS="mindkeep_pending_work"
 private const val KEY="items"
 private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 @Synchronized fun all(c:Context):List<PendingAssistantWork> = runCatching {
  val a=JSONArray(prefs(c).getString(KEY,"[]"))
  List(a.length()){i->val j=a.getJSONObject(i);PendingAssistantWork(j.getString("id"),j.getString("prompt"),j.optString("status","queued"),j.optString("result"),j.optLong("createdAt"))}
 }.getOrDefault(emptyList())
 @Synchronized fun enqueue(c:Context,prompt:String):String {
  val id=UUID.randomUUID().toString()
  save(c,(all(c)+PendingAssistantWork(id,prompt,"queued","",System.currentTimeMillis())).takeLast(100))
  return id
 }
 @Synchronized fun update(c:Context,id:String,status:String,result:String="") {
  save(c,all(c).map{if(it.id==id)it.copy(status=status,result=result) else it})
 }
 @Synchronized fun pending(c:Context)=all(c).filter{it.status=="queued" || it.status=="running"}
 private fun save(c:Context,items:List<PendingAssistantWork>) {
  val a=JSONArray()
  items.forEach{a.put(JSONObject().put("id",it.id).put("prompt",it.prompt).put("status",it.status).put("result",it.result).put("createdAt",it.createdAt))}
  prefs(c).edit().putString(KEY,a.toString()).apply()
 }
}
