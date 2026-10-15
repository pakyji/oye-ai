package com.oyeai.control

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.oyeai.MainActivity

/** Shows a reminder when its alarm fires. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        val text = intent.getStringExtra(Reminders.EXTRA_TEXT).orEmpty()
        Reminders.remove(context, id)
        Reminders.createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("OYE reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), notification)
    }
}
