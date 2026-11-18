package com.vikram.mindkeep

import android.app.Application
import com.vikram.mindkeep.core.diagnostics.CrashLogStore

class MindKeepApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { CrashLogStore.record(this, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
