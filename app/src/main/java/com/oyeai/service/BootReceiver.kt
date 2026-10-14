package com.oyeai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oyeai.AppSettings

/** Starts the background service after the phone turns on, if the user chose that. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = AppSettings(context)
        if (settings.runInBackground && settings.startOnBoot) {
            AgentService.start(context)
        }
    }
}
