package com.oyeai.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.oyeai.voice.ListenService
import android.content.Context
import android.content.Intent
import com.oyeai.AppSettings

/** Starts the background service after the phone turns on, if the user chose that. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        com.oyeai.control.Reminders.rescheduleAll(context)
        val settings = AppSettings(context)
        if (settings.runInBackground && settings.startOnBoot) {
            AgentService.start(context)
        }
        // Restart the "Oye" listener too, if the user had it on before the restart.
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (settings.listenForOye && micOk) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11+ does not give a microphone service started from the background any microphone.
                // Ask the user to tap once instead of starting a listener that cannot hear.
                ListenService.offerResume(context)
            } else {
                try {
                    ListenService.start(context)
                } catch (e: Exception) {
                    ListenService.offerResume(context)
                }
            }
        }
    }
}
