package com.oyeai.control

import android.accessibilityservice.AccessibilityService
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import com.oyeai.R
import com.oyeai.actions.Contacts
import com.oyeai.social.Direction
import com.oyeai.social.LabelResult
import com.oyeai.social.OyeAccessibilityService
import com.oyeai.social.ScrollResult
import com.oyeai.social.SocialActions
import com.oyeai.social.SocialControl
import com.oyeai.voice.ActionNotifier
import com.oyeai.voice.Speaker
import java.util.Locale

/**
 * Carries out a [PhoneCommand] and returns the sentence OYE says or shows.
 *
 * Android only lets an app hand a request to another app (an intent). A handoff is reported as
 * "opened", never as "done": the user finishes the task in the other app. Results that OYE can
 * check (brightness, volume, scrolling, clicks) are read back before a success is reported.
 */
object PhoneCommandRunner {

    suspend fun run(context: Context, command: PhoneCommand): String = when (command) {
        is PhoneCommand.Social -> SocialActions.run(context, command.command)
        is PhoneCommand.OpenApp -> openApp(context, command.name)
        is PhoneCommand.OpenSettings -> openSettings(context, command.target)
        is PhoneCommand.Brightness -> setBrightness(context, command.percent)
        is PhoneCommand.BrightnessStep -> stepBrightness(context, command.up)
        is PhoneCommand.MediaVolume -> setMediaVolume(context, command.percent)
        is PhoneCommand.VolumeStep -> stepVolume(context, command.up)
        PhoneCommand.VolumePanel -> volumePanel(context)
        is PhoneCommand.Media -> mediaKey(context, command.action)
        is PhoneCommand.Timer -> timer(context, command.minutes)
        is PhoneCommand.Remind -> remind(context, command.minutes, command.text)
        is PhoneCommand.Dial -> dial(context, command.target)
        is PhoneCommand.Message -> message(context, command.target, command.body)
        PhoneCommand.OpenDownloads -> openDownloads(context)
        PhoneCommand.DeviceStatus -> deviceStatus(context)
        PhoneCommand.Back -> globalAction(context, AccessibilityService.GLOBAL_ACTION_BACK, R.string.nav_back_done)
        PhoneCommand.Home -> globalAction(context, AccessibilityService.GLOBAL_ACTION_HOME, R.string.nav_home_done)
        is PhoneCommand.ClickLabel -> findLabel(context, command.label, click = true)
        is PhoneCommand.FindLabel -> findLabel(context, command.label, click = false)
        is PhoneCommand.ScrollScreen -> scrollScreen(context, command.up)
        PhoneCommand.StopAll -> stopAll(context)
    }

    // ---- Apps and settings -------------------------------------------------------------

    private fun openApp(context: Context, name: String): String {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0).map { info ->
            info.loadLabel(pm).toString() to info.activityInfo.packageName
        }
        val wanted = name.lowercase(Locale.ROOT).trim()
        val match = apps.firstOrNull { it.first.lowercase(Locale.ROOT) == wanted }
            ?: apps.firstOrNull { it.first.lowercase(Locale.ROOT).contains(wanted) }
            ?: return context.getString(R.string.control_app_not_found, name)
        val intent = pm.getLaunchIntentForPackage(match.second)
            ?: return context.getString(R.string.control_app_not_found, name)
        return Launcher.launch(
            context, intent, match.first,
            directText = context.getString(R.string.open_done, match.first),
            pendingText = context.getString(R.string.open_pending, match.first)
        )
    }

    private fun openSettings(context: Context, target: SettingsTarget): String {
        val (intent, labelRes) = settingsIntent(context, target)
        val label = context.getString(labelRes)
        return Launcher.launch(
            context, intent, label,
            directText = context.getString(R.string.open_done, label),
            pendingText = context.getString(R.string.open_pending, label),
            fallback = Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun settingsIntent(context: Context, target: SettingsTarget): Pair<Intent, Int> = when (target) {
        SettingsTarget.MAIN -> Intent(Settings.ACTION_SETTINGS) to R.string.set_main
        SettingsTarget.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS) to R.string.set_wifi
        SettingsTarget.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to R.string.set_bluetooth
        SettingsTarget.HOTSPOT -> Intent("android.settings.TETHER_SETTINGS") to R.string.set_hotspot
        SettingsTarget.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS) to R.string.set_display
        SettingsTarget.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS) to R.string.set_sound
        SettingsTarget.BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) to R.string.set_battery
        SettingsTarget.NOTIFICATIONS -> Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS) to R.string.set_notifications
        SettingsTarget.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) to R.string.set_accessibility
        SettingsTarget.APP_PERMISSIONS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)) to R.string.set_permissions
        SettingsTarget.DEFAULT_APPS -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) to R.string.set_default_apps
        SettingsTarget.LANGUAGE -> Intent(Settings.ACTION_LOCALE_SETTINGS) to R.string.set_language
        SettingsTarget.KEYBOARD -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS) to R.string.set_keyboard
        SettingsTarget.STORAGE -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) to R.string.set_storage
        SettingsTarget.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) to R.string.set_location
    }

    // ---- Brightness and volume ---------------------------------------------------------

    private fun setBrightness(context: Context, percent: Int): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            return Launcher.launch(
                context, intent, context.getString(R.string.set_permissions),
                directText = context.getString(R.string.bright_permission_opened),
                pendingText = context.getString(R.string.bright_permission_pending)
            )
        }
        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val value = (percent * 255 / 100).coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            // Read back what Android stored, so the reply matches the real setting.
            val actual = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            if (actual == value) context.getString(R.string.bright_set, percent.coerceIn(0, 100))
            else context.getString(R.string.bright_failed)
        } catch (e: SecurityException) {
            context.getString(R.string.bright_failed)
        }
    }

    private fun stepBrightness(context: Context, up: Boolean): String {
        if (!Settings.System.canWrite(context)) return setBrightness(context, 0)
        val current = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
        } catch (e: Settings.SettingNotFoundException) {
            50
        }
        return setBrightness(context, (current + if (up) 10 else -10).coerceIn(5, 100))
    }

    private fun audio(context: Context): AudioManager = context.getSystemService(AudioManager::class.java)

    private fun volumePercent(am: AudioManager): Int {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun setMediaVolume(context: Context, percent: Int): String {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = (percent.coerceIn(0, 100) * max / 100)
        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == index) {
                context.getString(R.string.vol_set, volumePercent(am))
            } else {
                context.getString(R.string.vol_failed)
            }
        } catch (e: SecurityException) {
            context.getString(R.string.vol_failed)
        }
    }

    private fun stepVolume(context: Context, up: Boolean): String {
        val am = audio(context)
        val before = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
        val after = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (after != before) context.getString(R.string.vol_set, volumePercent(am))
        else context.getString(R.string.vol_unchanged)
    }

    private fun volumePanel(context: Context): String {
        audio(context).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        return context.getString(R.string.vol_panel)
    }

    // ---- Media, timers, calls, messages, files -----------------------------------------

    private fun mediaKey(context: Context, action: MediaAction): String {
        val code = when (action) {
            MediaAction.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        return try {
            val am = audio(context)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            // The key is sent, but OYE cannot see whether the player reacted.
            context.getString(R.string.media_sent)
        } catch (e: SecurityException) {
            context.getString(R.string.media_failed)
        }
    }

    private fun remind(context: Context, minutes: Int, text: String): String {
        val at = Reminders.add(context, minutes, text)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
        return context.getString(R.string.reminder_set, minutes, time)
    }

    private fun timer(context: Context, minutes: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "OYE")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return Launcher.launch(
            context, intent, context.getString(R.string.control_timer),
            directText = context.getString(R.string.timer_sent, minutes),
            pendingText = context.getString(R.string.timer_pending, minutes)
        )
    }

    /** A phone number from the text, or the number saved for a contact. Null when neither exists. */
    private fun numberFor(context: Context, target: String): String? {
        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.length >= 3) return digits
        if (target.isBlank()) return null
        return try {
            Contacts.resolveNumber(context, target)
        } catch (e: SecurityException) {
            null
        }
    }

    private fun dial(context: Context, target: String): String {
        if (target.isBlank()) {
            return Launcher.launch(
                context, Intent(Intent.ACTION_DIAL), context.getString(R.string.control_dialer),
                directText = context.getString(R.string.dial_blank_opened),
                pendingText = context.getString(R.string.dial_blank_pending)
            )
        }
        val number = numberFor(context, target) ?: return context.getString(R.string.control_no_number, target)
        // ACTION_DIAL only fills in the number. The user presses the call button.
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        return Launcher.launch(
            context, intent, target,
            directText = context.getString(R.string.dial_opened, target),
            pendingText = context.getString(R.string.dial_pending, target)
        )
    }

    private fun message(context: Context, target: String, body: String): String {
        val number = numberFor(context, target) ?: return context.getString(R.string.control_no_number, target)
        // Compose only. The user reads the message and presses Send.
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body)
        return Launcher.launch(
            context, intent, target,
            directText = context.getString(R.string.sms_opened, target),
            pendingText = context.getString(R.string.sms_pending, target)
        )
    }

    private fun openDownloads(context: Context): String =
        Launcher.launch(
            context, Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), context.getString(R.string.control_downloads),
            directText = context.getString(R.string.downloads_opened),
            pendingText = context.getString(R.string.downloads_pending)
        )

    private fun deviceStatus(context: Context): String {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val network = when {
            caps == null -> context.getString(R.string.status_net_none)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> context.getString(R.string.status_net_wifi)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.status_net_mobile)
            else -> context.getString(R.string.status_net_other)
        }
        return context.getString(
            R.string.status_text,
            level,
            if (charging) context.getString(R.string.status_charging) else "",
            network
        )
    }

    // ---- Screen navigation and accessibility -------------------------------------------

    private fun globalAction(context: Context, action: Int, doneRes: Int): String {
        val service = OyeAccessibilityService.current() ?: return context.getString(R.string.control_need_accessibility)
        return if (service.performGlobal(action)) context.getString(doneRes)
        else context.getString(R.string.control_nav_failed)
    }

    /** Labels that must never be tapped by voice: they send, buy, share, delete or like things. */
    private val SENSITIVE = listOf(
        "send", "invia", "buy", "acquist", "pay", "paga", "purchas", "delete", "elimin", "remove", "rimuov",
        "share", "condivid", "post", "pubblic", "like", "mi piace", "follow", "segui", "comment", "commenta",
        "confirm", "conferma", "call", "chiama", "logout", "log out", "esci", "reset", "format",
        "بھیجیں", "خریدیں", "حذف", "ادائیگی", "بھیج", "भेजें", "हटाएं", "खरीद"
    )

    private suspend fun findLabel(context: Context, label: String, click: Boolean): String {
        val service = OyeAccessibilityService.current() ?: return context.getString(R.string.control_need_accessibility)
        val lower = label.lowercase(Locale.ROOT)
        if (click && SENSITIVE.any { lower.contains(it) }) {
            return context.getString(R.string.click_blocked, label)
        }
        return when (service.findLabel(label, click)) {
            LabelResult.Clicked -> context.getString(R.string.click_done, label)
            LabelResult.Found -> context.getString(R.string.find_found, label)
            LabelResult.NotClickable -> context.getString(R.string.click_not_clickable, label)
            LabelResult.NotFound -> context.getString(R.string.find_not_found, label)
            LabelResult.Unsupported -> context.getString(R.string.control_screen_unsupported)
            LabelResult.NoWindow -> context.getString(R.string.social_no_window)
        }
    }

    /** Scrolls through the same checked path as the social commands (Instagram and Facebook only). */
    private suspend fun scrollScreen(context: Context, up: Boolean): String {
        val direction = if (up) Direction.Backward else Direction.Forward
        return when (SocialControl.scrollOnce(direction)) {
            is ScrollResult.Done -> context.getString(if (up) R.string.scroll_up_done else R.string.scroll_down_done)
            ScrollResult.NotConnected -> context.getString(R.string.control_need_accessibility)
            ScrollResult.NoActiveWindow -> context.getString(R.string.social_no_window)
            ScrollResult.UnsupportedApp -> context.getString(R.string.control_screen_unsupported)
            else -> context.getString(R.string.social_failed)
        }
    }

    // ---- Stop All ----------------------------------------------------------------------

    /**
     * Stops what OYE controls: scrolling, speech and pending notification actions. It reports
     * each one, and says what it cannot undo (apps and timers the user already opened or set).
     */
    private fun stopAll(context: Context): String {
        val stopped = mutableListOf<String>()
        if (SocialControl.stop(context)) stopped += context.getString(R.string.stopped_scrolling)
        if (Speaker.stopAllSpeech()) stopped += context.getString(R.string.stopped_speech)
        val actions = ActionNotifier.cancelAll(context)
        if (actions > 0) stopped += context.getString(R.string.stopped_actions, actions)
        val report = if (stopped.isEmpty()) {
            context.getString(R.string.stop_nothing)
        } else {
            context.getString(R.string.stop_result, stopped.joinToString(", "))
        }
        return "$report ${context.getString(R.string.stop_cannot)}"
    }
}
