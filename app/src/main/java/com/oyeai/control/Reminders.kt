package com.oyeai.control

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Reminders the user asked for. They are kept on the phone and an alarm is set for each one.
 * Alarms are lost on restart, so BootReceiver calls [rescheduleAll].
 */
object Reminders {
    private const val PREFS = "oyeai_reminders"
    private const val KEY = "items"
    const val CHANNEL_ID = "oyeai_reminders"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TEXT = "reminder_text"

    data class Item(val id: String, val text: String, val at: Long)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Item> {
        val array = JSONArray(prefs(context).getString(KEY, "[]") ?: "[]")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Item(o.getString("id"), o.getString("text"), o.getLong("at"))
        }
    }

    private fun save(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at)) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    /** Stores the reminder and sets its alarm. Returns the time it will fire, in milliseconds. */
    fun add(context: Context, minutes: Int, text: String): Long {
        val item = Item(UUID.randomUUID().toString(), text, System.currentTimeMillis() + minutes * 60_000L)
        save(context, all(context) + item)
        schedule(context, item)
        return item.at
    }

    private fun schedule(context: Context, item: Item) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pendingIntent(context, item))
    }

    /** After a restart, set the alarms again for reminders that have not fired yet. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val live = all(context).filter { it.at > now }
        save(context, live)
        live.forEach { schedule(context, it) }
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filter { it.id != id })
    }

    private fun pendingIntent(context: Context, item: Item): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, item.id)
            .putExtra(EXTRA_TEXT, item.text)
        return PendingIntent.getBroadcast(
            context, item.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
