package com.oyeai.voice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oyeai.R
import com.oyeai.ai.Action

/**
 * Shows an action from a voice command as a notification with a Run or Confirm button.
 * Android does not let a background service open screens, so the user taps the button.
 */
object ActionNotifier {

    const val EXTRA_ID = "action_id"
    private const val CHANNEL_ID = "oyeai_actions"
    private const val MAX_PENDING = 50

    private val pending = LinkedHashMap<Int, Action>()
    private val launches = LinkedHashMap<Int, Intent>()
    private var nextId = 2000

    fun offer(context: Context, action: Action) {
        val id = synchronized(this) {
            val id = nextId++
            pending[id] = action
            while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
            id
        }
        ensureChannel(context)

        val intent = Intent(context, ActionActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = context.getString(
            if (action.needsConfirmation) R.string.action_confirm else R.string.action_run
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.voice_offer, summarize(action)))
            .setAutoCancel(true)
            .addAction(0, label, tap)
            .build()
        post(context, id, notification)
    }

    /** Takes the action for a notification button, once. */
    fun take(id: Int): Action? = synchronized(this) { pending.remove(id) }

    /**
     * Offers to open [intent] (a screen in another app or in OYE's settings). Used when OYE is in the
     * background, because Android blocks background apps from starting screens. Returns the notification id.
     */
    fun offerLaunch(context: Context, what: String, intent: Intent): Int {
        val id = synchronized(this) {
            val id = nextId++
            launches[id] = intent
            while (launches.size > MAX_PENDING) launches.remove(launches.keys.first())
            id
        }
        ensureChannel(context)
        val tapIntent = Intent(context, ActionActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(
            context, id, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = context.getString(R.string.launch_offer, what)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_open), tap)
            .build()
        post(context, id, notification)
        return id
    }

    /** Takes the screen to open for a launch notification, once. */
    fun takeLaunch(id: Int): Intent? = synchronized(this) { launches.remove(id) }

    /**
     * Cancels every pending action and launch notification. Returns how many were pending.
     * A notification that was already tapped cannot be recalled.
     */
    fun cancelAll(context: Context): Int {
        val ids = synchronized(this) {
            val ids = pending.keys.toList() + launches.keys.toList()
            pending.clear()
            launches.clear()
            ids
        }
        val manager = NotificationManagerCompat.from(context)
        ids.forEach { manager.cancel(it) }
        return ids.size
    }

    /** Replaces the button notification with the outcome of running it. */
    fun showResult(context: Context, id: Int, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        post(context, id, notification)
    }

    fun summarize(action: Action): String = when (action) {
        is Action.OpenApp -> "open ${action.app}"
        is Action.OpenUrl -> "open ${action.url}"
        is Action.CallContact -> "call ${action.name}"
        is Action.SendWhatsApp -> "WhatsApp to ${action.name}: \"${action.message}\""
        is Action.CloseApp -> "close ${action.app}"
        is Action.Unknown -> "unsupported action: ${action.type}"
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications were revoked between the check and the call.
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.actions_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
