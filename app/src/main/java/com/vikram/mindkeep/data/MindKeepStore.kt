package com.vikram.mindkeep.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MindNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class MindTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class MindReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dueAt: Long,
    val isDone: Boolean = false
)

/** Small, offline-only persistence layer. No account, network or analytics required. */
object MindKeepStore {
    private const val PREFS = "mindkeep_local_v1"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasCompletedIntro(context: Context) = prefs(context).getBoolean("intro_complete", false)
    fun setIntroCompleted(context: Context) {
        prefs(context).edit().putBoolean("intro_complete", true).apply()
    }

    fun loadNotes(context: Context): List<MindNote> = runCatching {
        val array = JSONArray(prefs(context).getString("notes", "[]"))
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            MindNote(item.getString("id"), item.optString("title"), item.optString("body"), item.optLong("createdAt"))
        }
    }.getOrDefault(emptyList())

    fun saveNotes(context: Context, notes: List<MindNote>) {
        val array = JSONArray()
        notes.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("createdAt", it.createdAt)) }
        prefs(context).edit().putString("notes", array.toString()).apply()
    }

    fun loadTasks(context: Context): List<MindTask> = runCatching {
        val array = JSONArray(prefs(context).getString("tasks", "[]"))
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            MindTask(item.getString("id"), item.optString("title"), item.optBoolean("isDone"), item.optLong("createdAt"))
        }
    }.getOrDefault(emptyList())

    fun saveTasks(context: Context, tasks: List<MindTask>) {
        val array = JSONArray()
        tasks.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("isDone", it.isDone).put("createdAt", it.createdAt)) }
        prefs(context).edit().putString("tasks", array.toString()).apply()
    }

    fun loadReminders(context: Context): List<MindReminder> = runCatching {
        val array = JSONArray(prefs(context).getString("reminders", "[]"))
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            MindReminder(item.getString("id"), item.optString("title"), item.optLong("dueAt"), item.optBoolean("isDone"))
        }
    }.getOrDefault(emptyList())

    fun saveReminders(context: Context, reminders: List<MindReminder>) {
        val array = JSONArray()
        reminders.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("dueAt", it.dueAt).put("isDone", it.isDone)) }
        prefs(context).edit().putString("reminders", array.toString()).apply()
    }
}
