package com.oyeai.social

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Stop button on the auto-scroll notification. */
class SocialReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            SocialControl.stop(context, StopReason.UserStopped)
        }
    }

    companion object {
        const val ACTION_STOP = "com.oyeai.social.STOP_SCROLLING"
    }
}
