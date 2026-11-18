package com.vikram.mindkeep.core.ai
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Local-only LiteRT-LM engine. No cloud fallback is implemented. */
object LocalModelRuntime {
 private const val SYSTEM_INSTRUCTION = "You are MindKeep, a private personal assistant running entirely on this device. Be accurate, concise, organise information clearly, and never claim to perform actions unless the app confirms them. Use supplied personal memory only when relevant."
 private val mutex=Mutex()
 @Volatile private var engine:Engine?=null
 @Volatile private var conversation:Conversation?=null
 @Volatile private var loadedPath:String?=null
 suspend fun load(modelFile:File)=withContext(Dispatchers.IO) {
  mutex.withLock {
   require(modelFile.isFile && modelFile.length()>0L){"Model file is missing or incomplete."}
   if(loadedPath==modelFile.absolutePath && engine!=null && conversation!=null)return@withLock
   closeLocked()
   val next=Engine(EngineConfig(modelPath=modelFile.absolutePath, backend=Backend.CPU()))
   try {
    next.initialize()
    val chat=next.createConversation(ConversationConfig(systemInstruction=Contents.of(SYSTEM_INSTRUCTION)))
    engine=next;conversation=chat;loadedPath=modelFile.absolutePath
   } catch(t:Throwable){runCatching{next.close()};throw t}
  }
 }
 suspend fun resetConversation()=withContext(Dispatchers.IO) {
  mutex.withLock {
   val activeEngine=engine ?: throw IllegalStateException("No local model is loaded.")
   runCatching{conversation?.close()}
   conversation=activeEngine.createConversation(ConversationConfig(systemInstruction=Contents.of(SYSTEM_INSTRUCTION)))
  }
 }
 fun generate(prompt:String):Flow<String> = flow {
  mutex.withLock {
   val active=conversation ?: throw IllegalStateException("No local model is loaded.")
   active.sendMessageAsync(prompt).collect { message -> if(message.text.isNotEmpty())emit(message.text) }
  }
 }.flowOn(Dispatchers.IO)
 suspend fun unload()=withContext(Dispatchers.IO){mutex.withLock{closeLocked()}}
 private fun closeLocked(){runCatching{conversation?.close()};runCatching{engine?.close()};conversation=null;engine=null;loadedPath=null}
}
