package com.oyeai.control

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.oyeai.AppVisibility
import com.oyeai.R
import com.oyeai.voice.ActionNotifier

/**
 * Opens a screen or app for a command. Android blocks background services from starting screens,
 * so when OYE is not on screen the request becomes a notification that the user taps.
 */
object Launcher {

    /**
     * @param what short description used in the notification, such as "YouTube" or "Wi-Fi settings"
     * @param directText what to say when the screen was opened directly
     * @param pendingText what to say when the user must tap the notification
     * @param fallback an intent to try if [intent] has no handler on this phone
     */
    fun launch(
        context: Context,
        intent: Intent,
        what: String,
        directText: String,
        pendingText: String,
        fallback: Intent? = null
    ): String {
        if (!AppVisibility.resumed) {
            ActionNotifier.offerLaunch(context, what, intent)
            return pendingText
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            directText
        } catch (e: ActivityNotFoundException) {
            val alternative = fallback ?: return context.getString(R.string.control_no_handler)
            try {
                context.startActivity(alternative.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                directText
            } catch (e2: ActivityNotFoundException) {
                context.getString(R.string.control_no_handler)
            }
        }
    }
}
