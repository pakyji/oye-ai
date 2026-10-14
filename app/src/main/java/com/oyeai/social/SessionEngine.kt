package com.oyeai.social

/** Scroll direction. Forward is the next item, backward is the previous one. */
enum class Direction { Forward, Backward }

/** What really happened on one scroll attempt. Only [Done] means the scroll was performed. */
sealed class ScrollResult {
    /** The scroll was performed. [method] is "accessibility" or "gesture". */
    data class Done(val method: String) : ScrollResult()
    /** The accessibility service is not connected, so nothing was attempted. */
    object NotConnected : ScrollResult()
    /** Android gave no active window to scroll. */
    object NoActiveWindow : ScrollResult()
    /** Another app is in front. OYE only scrolls Instagram and Facebook. */
    object UnsupportedApp : ScrollResult()
    /** The scroll was attempted but could not be performed. */
    object Failed : ScrollResult()
}

enum class Phase { Idle, Running, Paused }

enum class StopReason { UserStopped, Expired, RepeatedFailures, AccessibilityOff }

enum class StartOutcome { Started, Resumed, AlreadyRunning }

/** What the controller should do on a clock tick. */
sealed class Tick {
    object None : Tick()
    /** Request one forward scroll, tagged with the session it belongs to. */
    data class Scroll(val session: Int) : Tick()
    data class Finished(val reason: StopReason) : Tick()
}

/**
 * The auto-scroll state machine. It has no Android dependencies, so the rules can be tested directly:
 * scrolling starts only after a start request, stops at the session limit, pauses, and gives up after
 * repeated failures. Time is passed in by the caller, in milliseconds.
 *
 * Not thread-safe; use it from one thread (the main thread in the app).
 */
class SessionEngine(
    initialIntervalMs: Long = DEFAULT_INTERVAL_MS,
    initialMaxSessionMs: Long = DEFAULT_MAX_SESSION_MS
) {
    var phase: Phase = Phase.Idle
        private set
    var intervalMs: Long = clampInterval(initialIntervalMs)
        private set
    var maxSessionMs: Long = clampSession(initialMaxSessionMs)
        private set
    /** When the current session ends. Zero when idle. The limit keeps counting while paused. */
    var endsAt: Long = 0L
        private set
    var scrolls: Int = 0
        private set
    /** Consecutive failed attempts. Reset by any successful scroll. */
    var failures: Int = 0
        private set
    var lastResult: ScrollResult? = null
        private set
    var lastStop: StopReason? = null
        private set

    private var session = 0
    private var nextScrollAt = 0L
    private var inFlight = false

    fun setInterval(ms: Long) {
        intervalMs = clampInterval(ms)
    }

    /** Applies to the next session started. */
    fun setMaxSession(ms: Long) {
        maxSessionMs = clampSession(ms)
    }

    fun start(now: Long): StartOutcome = when (phase) {
        Phase.Running -> StartOutcome.AlreadyRunning
        Phase.Paused -> {
            phase = Phase.Running
            nextScrollAt = now + intervalMs
            StartOutcome.Resumed
        }
        Phase.Idle -> {
            session++
            phase = Phase.Running
            endsAt = now + maxSessionMs
            nextScrollAt = now + intervalMs
            scrolls = 0
            failures = 0
            lastResult = null
            lastStop = null
            inFlight = false
            StartOutcome.Started
        }
    }

    fun pause(): Boolean {
        if (phase != Phase.Running) return false
        phase = Phase.Paused
        return true
    }

    fun stop(reason: StopReason): Boolean {
        if (phase == Phase.Idle) return false
        phase = Phase.Idle
        endsAt = 0L
        inFlight = false
        lastStop = reason
        return true
    }

    /** Ends the current session [minutes] from [now]. Returns false when nothing is running. */
    fun limitTo(now: Long, minutes: Int): Boolean {
        if (phase == Phase.Idle) return false
        endsAt = now + minutes * 60_000L
        return true
    }

    fun tick(now: Long): Tick {
        if (phase == Phase.Idle) return Tick.None
        if (now >= endsAt) {
            stop(StopReason.Expired)
            return Tick.Finished(StopReason.Expired)
        }
        if (phase == Phase.Running && !inFlight && now >= nextScrollAt) {
            inFlight = true
            nextScrollAt = now + intervalMs
            return Tick.Scroll(session)
        }
        return Tick.None
    }

    /**
     * Records the outcome of a scroll. Returns the reason when the session was stopped because of it.
     * Results from an earlier session are ignored.
     */
    fun onResult(sessionId: Int, result: ScrollResult): StopReason? {
        if (sessionId != session) return null
        inFlight = false
        lastResult = result
        if (phase == Phase.Idle) return null
        if (result is ScrollResult.Done) {
            scrolls++
            failures = 0
            return null
        }
        failures++
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            stop(StopReason.RepeatedFailures)
            return StopReason.RepeatedFailures
        }
        return null
    }

    companion object {
        const val SPEED_SLOW_MS = 10_000L
        const val SPEED_NORMAL_MS = 6_000L
        const val SPEED_FAST_MS = 3_000L
        const val SPEED_STEP_MS = 2_000L
        const val MIN_INTERVAL_MS = 3_000L
        const val MAX_INTERVAL_MS = 20_000L
        const val DEFAULT_INTERVAL_MS = SPEED_NORMAL_MS
        const val MIN_SESSION_MS = 60_000L
        const val MAX_SESSION_MS = 60 * 60_000L
        const val DEFAULT_MAX_SESSION_MS = 15 * 60_000L
        const val MAX_CONSECUTIVE_FAILURES = 5

        fun clampInterval(ms: Long): Long = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        fun clampSession(ms: Long): Long = ms.coerceIn(MIN_SESSION_MS, MAX_SESSION_MS)
    }
}
