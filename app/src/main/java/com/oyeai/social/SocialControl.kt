package com.oyeai.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.oyeai.AppSettings
import com.oyeai.MainActivity
import com.oyeai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What the UI shows. Built from the engine after every change. */
data class SocialState(
    val phase: Phase = Phase.Idle,
    val endsAt: Long = 0L,
    val intervalMs: Long = SessionEngine.DEFAULT_INTERVAL_MS,
    val scrolls: Int = 0,
    val lastResult: ScrollResult? = null,
    val lastStop: StopReason? = null
)

enum class StartResult { Started, Resumed, AlreadyRunning, NeedAccessibility }

enum class AccessibilityStatus { On, Starting, Off }

/**
 * Runs auto-scroll for OYE. Commands from voice, the chat and the card all go through here.
 *
 * Nothing starts on its own: scrolling begins only after [start] is called, and [start] refuses
 * unless the accessibility service is connected. The ongoing notification shows while scrolling
 * and has a Stop button.
 */
object SocialControl {

    private const val TICK_MS = 250L
    private const val CHANNEL_ID = "oyeai_social"
    private const val NOTIFICATION_ID = 1003
    private const val SCROLL_TIMEOUT_MS = 5_000L

    private val engine = SessionEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null

    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state.asStateFlow()

    fun isConnected(): Boolean = OyeAccessibilityService.current() != null

    fun accessibilityStatus(context: Context): AccessibilityStatus {
        if (isConnected()) return AccessibilityStatus.On
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(context, OyeAccessibilityService::class.java).flattenToString()
        val turnedOn = enabled.split(':').any { it.equals(component, ignoreCase = true) }
        return if (turnedOn) AccessibilityStatus.Starting else AccessibilityStatus.Off
    }

    fun intervalMs(): Long = engine.intervalMs

    fun start(context: Context): StartResult {
        if (!isConnected()) return StartResult.NeedAccessibility
        if (engine.phase == Phase.Idle) {
            val settings = AppSettings(context)
            engine.setInterval(settings.scrollIntervalMs)
            engine.setMaxSession(settings.maxSessionMinutes * 60_000L)
        }
        val outcome = engine.start(now())
        ensureLoop(context.applicationContext)
        publish()
        if (outcome != StartOutcome.AlreadyRunning) showNotification(context)
        return when (outcome) {
            StartOutcome.Started -> StartResult.Started
            StartOutcome.Resumed -> StartResult.Resumed
            StartOutcome.AlreadyRunning -> StartResult.AlreadyRunning
        }
    }

    fun pause(context: Context): Boolean {
        val changed = engine.pause()
        publish()
        if (changed) showNotification(context)
        return changed
    }

    fun stop(context: Context, reason: StopReason = StopReason.UserStopped): Boolean {
        val wasActive = engine.stop(reason)
        loop?.cancel()
        loop = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        publish()
        return wasActive
    }

    /** Ends the running session [minutes] from now. */
    fun limitMinutes(minutes: Int): Boolean {
        val changed = engine.limitTo(now(), minutes)
        publish()
        return changed
    }

    /** Sets the scroll interval, saves it, and applies it at once if a session is running. */
    fun setSpeed(context: Context, intervalMs: Long) {
        engine.setInterval(intervalMs)
        AppSettings(context).scrollIntervalMs = engine.intervalMs
        publish()
    }

    /** Performs one scroll now, outside any session. Reports only what really happened. */
    suspend fun scrollOnce(direction: Direction): ScrollResult {
        val service = OyeAccessibilityService.current() ?: return ScrollResult.NotConnected
        return withTimeoutOrNull(SCROLL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                service.scroll(direction) { result -> if (cont.isActive) cont.resume(result) }
            }
        } ?: ScrollResult.Failed
    }

    private fun ensureLoop(context: Context) {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                delay(TICK_MS)
                onTick(context)
            }
        }
    }

    private fun onTick(context: Context) {
        if (engine.phase == Phase.Idle) return
        val service = OyeAccessibilityService.current()
        if (service == null) {
            stop(context, StopReason.AccessibilityOff)
            return
        }
        when (val tick = engine.tick(now())) {
            is Tick.Scroll -> service.scroll(Direction.Forward) { result ->
                val reason = engine.onResult(tick.session, result)
                if (reason != null) stop(context, reason) else publish()
            }
            is Tick.Finished -> stop(context, tick.reason)
            Tick.None -> Unit
        }
        publish()
    }

    private fun publish() {
        _state.value = SocialState(
            phase = engine.phase,
            endsAt = engine.endsAt,
            intervalMs = engine.intervalMs,
            scrolls = engine.scrolls,
            lastResult = engine.lastResult,
            lastStop = engine.lastStop
        )
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    /** The visible indicator while scrolling: an ongoing notification with a Stop button. */
    private fun showNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.social_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val stop = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SocialReceiver::class.java).setAction(SocialReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val running = engine.phase == Phase.Running
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(context.getString(R.string.social_notif_title))
            .setContentText(
                context.getString(if (running) R.string.social_notif_running else R.string.social_notif_paused)
            )
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.social_notif_stop), stop)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications are not allowed. Scrolling still runs, and the card shows its status.
        }
    }
}
