package com.vikram.mindkeep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import java.io.File

/** Remove the cached installer after Android confirms this package was replaced. */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        File(directory, "mindkeep-update.apk").delete()
        context.getSharedPreferences("mindkeep_updater", Context.MODE_PRIVATE).edit()
            .putBoolean("apk_ready", false).remove("download_id").apply()
    }
}
