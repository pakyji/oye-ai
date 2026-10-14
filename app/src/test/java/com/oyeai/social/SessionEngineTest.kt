package com.oyeai.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private val minute = 60_000L

    @Test
    fun doesNothingUntilStarted() {
        val engine = SessionEngine()
        assertEquals(Tick.None, engine.tick(0))
        assertEquals(Tick.None, engine.tick(10 * minute))
        assertEquals(Phase.Idle, engine.phase)
    }

    @Test
    fun scrollsAtTheConfiguredIntervalOnlyAfterStart() {
        val engine = SessionEngine(initialIntervalMs = 5_000)
        assertEquals(StartOutcome.Started, engine.start(now = 0))
        assertEquals(Tick.None, engine.tick(4_999))
        val first = engine.tick(5_000)
        assertTrue(first is Tick.Scroll)
    }

    @Test
    fun doesNotStartTwoScrollsAtOnce() {
        val engine = SessionEngine(initialIntervalMs = 5_000)
        engine.start(0)
        val tick = engine.tick(5_000) as Tick.Scroll
        // The previous scroll has not reported back yet.
        assertEquals(Tick.None, engine.tick(20_000))
        engine.onResult(tick.session, ScrollResult.Done("accessibility"))
        assertTrue(engine.tick(25_000) is Tick.Scroll)
    }

    @Test
    fun pauseStopsScrollingAndResumeContinues() {
        val engine = SessionEngine(initialIntervalMs = 5_000)
        engine.start(0)
        assertTrue(engine.pause())
        assertEquals(Tick.None, engine.tick(60_000))
        assertEquals(StartOutcome.Resumed, engine.start(60_000))
        assertTrue(engine.tick(65_000) is Tick.Scroll)
    }

    @Test
    fun startWhileRunningDoesNotRestartTheSession() {
        val engine = SessionEngine()
        engine.start(0)
        assertEquals(StartOutcome.AlreadyRunning, engine.start(1_000))
        assertEquals(15 * minute, engine.endsAt)
    }

    @Test
    fun stopEndsTheSessionImmediately() {
        val engine = SessionEngine(initialIntervalMs = 5_000)
        engine.start(0)
        assertTrue(engine.stop(StopReason.UserStopped))
        assertEquals(Phase.Idle, engine.phase)
        assertEquals(Tick.None, engine.tick(60_000))
        assertEquals(StopReason.UserStopped, engine.lastStop)
        assertFalse(engine.stop(StopReason.UserStopped))
    }

    @Test
    fun sessionLimitStopsAutomation() {
        val engine = SessionEngine(initialIntervalMs = 5_000, initialMaxSessionMs = 2 * minute)
        engine.start(0)
        assertTrue(engine.tick(119_999) !is Tick.Finished)
        assertEquals(Tick.Finished(StopReason.Expired), engine.tick(2 * minute))
        assertEquals(Phase.Idle, engine.phase)
    }

    @Test
    fun voiceTimeLimitReplacesTheSessionEnd() {
        val engine = SessionEngine()
        engine.start(0)
        assertTrue(engine.limitTo(now = 5 * minute, minutes = 10))
        assertEquals(15 * minute, engine.endsAt)
        // A later "stop in 1 minute" shortens the session.
        assertTrue(engine.limitTo(now = 5 * minute, minutes = 1))
        assertEquals(6 * minute, engine.endsAt)
    }

    @Test
    fun timeLimitNeedsARunningSession() {
        assertFalse(SessionEngine().limitTo(0, 10))
    }

    @Test
    fun repeatedFailuresStopTheSessionWithAReason() {
        val engine = SessionEngine(initialIntervalMs = 3_000)
        engine.start(0)
        var now = 0L
        var reason: StopReason? = null
        repeat(SessionEngine.MAX_CONSECUTIVE_FAILURES) {
            now += 3_000
            val tick = engine.tick(now) as Tick.Scroll
            reason = engine.onResult(tick.session, ScrollResult.UnsupportedApp)
        }
        assertEquals(StopReason.RepeatedFailures, reason)
        assertEquals(Phase.Idle, engine.phase)
    }

    @Test
    fun aSuccessfulScrollResetsTheFailureCount() {
        val engine = SessionEngine(initialIntervalMs = 3_000)
        engine.start(0)
        var now = 0L
        repeat(3) {
            now += 3_000
            val tick = engine.tick(now) as Tick.Scroll
            engine.onResult(tick.session, ScrollResult.Failed)
        }
        now += 3_000
        val ok = engine.tick(now) as Tick.Scroll
        assertNull(engine.onResult(ok.session, ScrollResult.Done("gesture")))
        assertEquals(0, engine.failures)
        assertEquals(1, engine.scrolls)
        assertEquals(Phase.Running, engine.phase)
    }

    @Test
    fun resultsFromAnEarlierSessionAreIgnored() {
        val engine = SessionEngine(initialIntervalMs = 3_000)
        engine.start(0)
        val old = engine.tick(3_000) as Tick.Scroll
        engine.stop(StopReason.UserStopped)
        engine.start(10_000)
        assertNull(engine.onResult(old.session, ScrollResult.Done("accessibility")))
        assertEquals(0, engine.scrolls)
    }

    @Test
    fun intervalsAndSessionLengthAreClamped() {
        val engine = SessionEngine()
        engine.setInterval(1_000)
        assertEquals(SessionEngine.MIN_INTERVAL_MS, engine.intervalMs)
        engine.setInterval(99_000)
        assertEquals(SessionEngine.MAX_INTERVAL_MS, engine.intervalMs)
        engine.setMaxSession(1)
        assertEquals(SessionEngine.MIN_SESSION_MS, engine.maxSessionMs)
    }
}
