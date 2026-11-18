package com.vikram.mindkeep.core.ai
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.collect
import java.util.concurrent.TimeUnit

class AssistantWorkWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result {
  val id=inputData.getString(KEY_ID) ?: return Result.failure()
  val item=AssistantWorkStore.all(applicationContext).firstOrNull{it.id==id} ?: return Result.success()
  val model=LocalAiPreferences.activeModel(applicationContext) ?: run {
   AssistantWorkStore.update(applicationContext,id,"queued")
   return Result.retry()
  }
  return try {
   AssistantWorkStore.update(applicationContext,id,"running")
   LocalModelRuntime.load(model.file(applicationContext))
   val memory=AssistantMemoryStore.buildContext(applicationContext,item.prompt,model.contextTokens * 3)
   AssistantMemoryStore.append(applicationContext,"user",item.prompt)
   val prompt=if(memory.isBlank())item.prompt else "Relevant conversation memory:\n$memory\n\nCurrent request:\n${item.prompt}"
   val output=StringBuilder()
   LocalModelRuntime.generate(prompt).collect{output.append(it)}
   AssistantMemoryStore.append(applicationContext,"assistant",output.toString())
   AssistantWorkStore.update(applicationContext,id,"completed",output.toString())
   Result.success()
  }catch(t:Throwable){
   AssistantWorkStore.update(applicationContext,id,"queued",t.message ?: "Waiting to resume")
   Result.retry()
  }
 }
 companion object {
  const val KEY_ID="assistant_work_id"
  fun enqueue(context:Context,id:String) {
   val request=OneTimeWorkRequestBuilder<AssistantWorkWorker>()
    .setInputData(Data.Builder().putString(KEY_ID,id).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
    .build()
   WorkManager.getInstance(context).enqueueUniqueWork("mindkeep-assistant-$id",ExistingWorkPolicy.REPLACE,request)
  }
  fun resumePending(context:Context) { AssistantWorkStore.queued(context).forEach{enqueue(context,it.id)} }
 }
}
