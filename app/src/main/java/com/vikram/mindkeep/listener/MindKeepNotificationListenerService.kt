package com.vikram.mindkeep.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vikram.mindkeep.core.ai.AssistantMemoryStore

/**
 * Explicitly opt-in notification listener. It stores notification text locally
 * only after the user grants Android notification access and enables capture.
 */
class MindKeepNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn:StatusBarNotification?) {
        val item=sbn ?: return
        if(item.packageName==packageName || !CapturedNotificationStore.isEnabled(this))return
        val extras=item.notification.extras ?: return
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body=extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf{it.isNotBlank()}
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if(title.isBlank() && body.isBlank())return
        val appName=runCatching {
            val app=packageManager.getApplicationInfo(item.packageName,0)
            packageManager.getApplicationLabel(app).toString()
        }.getOrDefault(item.packageName)
        CapturedNotificationStore.add(this,item.packageName,appName,title,body,item.postTime)
        AssistantMemoryStore.append(this,"notification from $appName","$title $body".trim())
    }
}
