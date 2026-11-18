package com.vikram.mindkeep.core.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogStore {
    private fun directory(context: Context) = File(context.filesDir, "crash-logs").apply { mkdirs() }

    fun record(context: Context, thread: Thread, throwable: Throwable): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(directory(context), "mindkeep-crash-$stamp.txt")
        file.bufferedWriter().use { out ->
            out.appendLine("MindKeep crash report")
            out.appendLine("Time: ${Date()}")
            out.appendLine("Thread: ${thread.name} (${thread.id})")
            out.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            out.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            out.appendLine()
            throwable.printStackTrace(java.io.PrintWriter(out))
        }
        return file
    }

    fun all(context: Context): List<File> =
        directory(context).listFiles()?.filter { it.isFile && it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File): Boolean {
        val parent = file.parentFile ?: return false
        if (!parent.name.equals("crash-logs", true)) return false
        return file.delete()
    }
}
