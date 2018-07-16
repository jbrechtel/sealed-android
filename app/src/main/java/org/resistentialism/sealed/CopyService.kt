package org.resistentialism.sealed

import android.app.IntentService
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

class CopyService : IntentService("CopyService") {

    override fun onHandleIntent(intent: Intent?) {
        val text = intent!!.getStringExtra("text")
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.primaryClip = ClipData.newPlainText("some text", text)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.COPY_NOTIFICATION_ID)
    }
}
