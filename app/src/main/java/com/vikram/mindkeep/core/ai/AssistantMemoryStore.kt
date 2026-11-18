package com.vikram.mindkeep.core.ai
import android.content.Context
import org.json.JSONObject
import java.io.File

data class MemoryTurn(val role:String,val text:String,val timestamp:Long)
object AssistantMemoryStore {
 private const val FILE_NAME="mindkeep_conversation.jsonl"
 private const val MAX_FILE_BYTES=4L*1024L*1024L
 private fun file(c:Context)=File(c.filesDir,FILE_NAME)
 @Synchronized fun append(c:Context,role:String,text:String) {
  if(text.isBlank())return
  val f=file(c)
  f.appendText(JSONObject().put("role",role).put("text",text.take(12000)).put("timestamp",System.currentTimeMillis()).toString()+"\n")
  compactIfNeeded(f)
 }
 @Synchronized fun recent(c:Context,limit:Int=24):List<MemoryTurn> = readAll(file(c)).takeLast(limit)
 fun buildContext(c:Context,userPrompt:String,maxChars:Int=12000):String {
  val turns=readAll(file(c));if(turns.isEmpty())return ""
  val recent=turns.takeLast(10)
  val keywords=userPrompt.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>=4}.toSet()
  val relevant=turns.dropLast(10).asReversed().filter{turn->keywords.any{turn.text.lowercase().contains(it)}}.take(8)
  return (relevant+recent).distinctBy{it.timestamp}.sortedBy{it.timestamp}.joinToString("\n"){"${it.role}: ${it.text}"}.takeLast(maxChars)
 }
 private fun readAll(f:File):List<MemoryTurn> {
  if(!f.exists())return emptyList()
  return runCatching{f.readLines().mapNotNull{line->runCatching{val j=JSONObject(line);MemoryTurn(j.getString("role"),j.getString("text"),j.optLong("timestamp"))}.getOrNull()}}.getOrDefault(emptyList())
 }
 private fun compactIfNeeded(f:File) {
  if(f.length()<=MAX_FILE_BYTES)return
  val keep=readAll(f).takeLast(1000)
  f.writeText(keep.joinToString("\n"){JSONObject().put("role",it.role).put("text",it.text).put("timestamp",it.timestamp).toString()}+"\n")
 }
}
