package com.vikram.mindkeep.listener

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CapturedNotification(
    val id:String,
    val packageName:String,
    val appName:String,
    val title:String,
    val body:String,
    val postedAt:Long
)

object CapturedNotificationStore {
    private const val PREFS="mindkeep_notification_capture"
    private const val ITEMS="captured_items"
    private fun prefs(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun isEnabled(context:Context)=prefs(context).getBoolean("capture_enabled",false)
    fun setEnabled(context:Context,enabled:Boolean){prefs(context).edit().putBoolean("capture_enabled",enabled).apply()}
    @Synchronized fun all(context:Context):List<CapturedNotification> = runCatching {
        val array=JSONArray(prefs(context).getString(ITEMS,"[]"))
        List(array.length()){i->
            val j=array.getJSONObject(i)
            CapturedNotification(j.getString("id"),j.getString("package"),j.getString("app"),j.getString("title"),j.getString("body"),j.getLong("postedAt"))
        }
    }.getOrDefault(emptyList())
    @Synchronized fun add(context:Context,packageName:String,appName:String,title:String,body:String,postedAt:Long) {
        if(title.isBlank() && body.isBlank())return
        val old=all(context)
        if(old.any{it.packageName==packageName && it.postedAt==postedAt && it.title==title})return
        val updated=(old+CapturedNotification(UUID.randomUUID().toString(),packageName,appName,title,body,postedAt)).takeLast(500)
        val array=JSONArray()
        updated.forEach{array.put(JSONObject().put("id",it.id).put("package",it.packageName).put("app",it.appName).put("title",it.title).put("body",it.body).put("postedAt",it.postedAt))}
        prefs(context).edit().putString(ITEMS,array.toString()).apply()
    }
}
