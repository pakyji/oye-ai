package com.oyeai.social

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.oyeai.AppSettings
import com.oyeai.AppVisibility
import com.oyeai.R
import com.oyeai.ai.Action
import com.oyeai.voice.ActionNotifier

/**
 * Carries out a social command and returns the sentence OYE will say or show.
 * The text describes only what was done. Every claim comes from a real result.
 */
object SocialActions {

    suspend fun run(context: Context, command: SocialCommand): String = when (command) {
        is SocialCommand.Open -> open(context, command)
        SocialCommand.Start -> start(context)
        SocialCommand.Pause -> if (SocialControl.pause(context)) {
            context.getString(R.string.social_paused)
        } else {
            context.getString(R.string.social_not_running)
        }
        SocialCommand.Stop -> if (SocialControl.stop(context)) {
            context.getString(R.string.social_stopped)
        } else {
            context.getString(R.string.social_not_running)
        }
        is SocialCommand.StopAfter -> if (SocialControl.limitMinutes(command.minutes)) {
            context.getString(R.string.social_stop_after, command.minutes)
        } else {
            context.getString(R.string.social_not_running)
        }
        SocialCommand.Next -> scrollOnce(context, Direction.Forward, R.string.social_next_done)
        SocialCommand.Previous -> scrollOnce(context, Direction.Backward, R.string.social_prev_done)
        SocialCommand.Slower -> changeSpeed(context, SessionEngine.SPEED_STEP_MS)
        SocialCommand.Faster -> changeSpeed(context, -SessionEngine.SPEED_STEP_MS)
    }

    /** Speed label for the current interval, for use in messages. */
    fun speedName(context: Context, intervalMs: Long): String = when (intervalMs) {
        SessionEngine.SPEED_SLOW_MS -> context.getString(R.string.speed_slow)
        SessionEngine.SPEED_NORMAL_MS -> context.getString(R.string.speed_normal)
        SessionEngine.SPEED_FAST_MS -> context.getString(R.string.speed_fast)
        else -> "${intervalMs / 1000} s"
    }

    private fun open(context: Context, command: SocialCommand.Open): String {
        val label = command.app.label
        val launch = context.packageManager.getLaunchIntentForPackage(command.app.packageName)
            ?: return context.getString(R.string.social_not_installed, label)

        val message = if (AppVisibility.resumed) {
            // OYE is on screen, so Android allows the app to be opened directly.
            try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                context.getString(R.string.social_opened, label)
            } catch (e: ActivityNotFoundException) {
                return context.getString(R.string.social_not_installed, label)
            }
        } else {
            // In the background Android blocks this, so the user taps a notification.
            ActionNotifier.offer(context, Action.OpenApp(label))
            context.getString(R.string.social_open_pending, label)
        }
        return if (command.reels) "$message ${context.getString(R.string.social_reels_hint)}" else message
    }

    private suspend fun start(context: Context): String {
        val settings = AppSettings(context)
        return when (SocialControl.start(context)) {
            StartResult.Started -> context.getString(
                R.string.social_started,
                speedName(context, SocialControl.intervalMs()),
                settings.maxSessionMinutes
            )
            StartResult.Resumed -> context.getString(R.string.social_resumed)
            StartResult.AlreadyRunning -> context.getString(R.string.social_already)
            StartResult.NeedAccessibility -> context.getString(R.string.social_need_accessibility)
        }
    }

    private suspend fun scrollOnce(context: Context, direction: Direction, doneRes: Int): String =
        when (SocialControl.scrollOnce(direction)) {
            is ScrollResult.Done -> context.getString(doneRes)
            ScrollResult.NotConnected -> context.getString(R.string.social_need_accessibility)
            ScrollResult.NoActiveWindow -> context.getString(R.string.social_no_window)
            ScrollResult.UnsupportedApp -> context.getString(R.string.social_unsupported)
            ScrollResult.Failed -> context.getString(R.string.social_failed)
        }

    private fun changeSpeed(context: Context, delta: Long): String {
        SocialControl.setSpeed(context, SocialControl.intervalMs() + delta)
        return context.getString(R.string.social_speed_set, speedName(context, SocialControl.intervalMs()))
    }
}
