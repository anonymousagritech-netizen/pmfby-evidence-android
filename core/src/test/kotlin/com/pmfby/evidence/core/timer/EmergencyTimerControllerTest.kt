package com.pmfby.evidence.core.timer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A fake clock the test fully controls — no real `Thread.sleep`, no flakiness. */
private class FakeClock(private var millis: Long = 0L) : ClockSource {
    override fun nowMillis(): Long = millis
    fun advanceSeconds(seconds: Int) {
        millis += seconds * 1000L
    }
}

class EmergencyTimerControllerTest {

    @Test
    fun `starts in IDLE and transitions to RECORDING on start`() {
        val controller = EmergencyTimerController(FakeClock())
        assertEquals(EmergencyRecordingState.IDLE, controller.currentState())
        controller.start()
        assertEquals(EmergencyRecordingState.RECORDING, controller.currentState())
    }

    @Test
    fun `hard-stops at exactly 120 seconds`() {
        val clock = FakeClock()
        val controller = EmergencyTimerController(clock)
        controller.start()

        clock.advanceSeconds(119)
        var snapshot = controller.tick()
        assertEquals(EmergencyRecordingState.RECORDING, snapshot.state)
        assertEquals(119, snapshot.elapsedSeconds)

        clock.advanceSeconds(1) // now at 120s exactly
        snapshot = controller.tick()
        assertEquals(EmergencyRecordingState.STOPPED_HARD_CAP, snapshot.state)
        assertEquals(120, snapshot.elapsedSeconds)
        assertEquals(0, snapshot.remainingSeconds)
    }

    @Test
    fun `never reports elapsed beyond the hard cap even if ticked late`() {
        val clock = FakeClock()
        val controller = EmergencyTimerController(clock)
        controller.start()
        clock.advanceSeconds(500) // way past 120s before the first tick ever happens
        val snapshot = controller.tick()
        assertEquals(EmergencyRecordingState.STOPPED_HARD_CAP, snapshot.state)
        assertEquals(120, snapshot.elapsedSeconds)
    }

    @Test
    fun `user stop before hard cap transitions to STOPPED_COMPLETE`() {
        val clock = FakeClock()
        val controller = EmergencyTimerController(clock)
        controller.start()
        clock.advanceSeconds(30)
        controller.stop()
        assertEquals(EmergencyRecordingState.STOPPED_COMPLETE, controller.currentState())
        assertEquals(30, controller.elapsedSecondsSinceStart())
    }

    @Test
    fun `stop() throws if not currently recording`() {
        val controller = EmergencyTimerController(FakeClock())
        assertThrows(IllegalEmergencyTransition::class.java) { controller.stop() }
    }

    @Test
    fun `cancel from RECORDING cleans up partial files and clears them`() {
        val clock = FakeClock()
        val controller = EmergencyTimerController(clock)
        var cleanedUp: List<String>? = null
        controller.onCleanupPartialFiles = { files -> cleanedUp = files }

        controller.start()
        controller.registerPartialFile("/tmp/emergency_video_123.mp4")
        controller.registerPartialFile("/tmp/emergency_audio_123.m4a")
        clock.advanceSeconds(10)
        controller.cancel()

        assertEquals(EmergencyRecordingState.CANCELLED, controller.currentState())
        assertEquals(listOf("/tmp/emergency_video_123.mp4", "/tmp/emergency_audio_123.m4a"), cleanedUp)
    }

    @Test
    fun `cancel from IDLE is allowed and is a no-op cleanup`() {
        val controller = EmergencyTimerController(FakeClock())
        var cleanupCalled = false
        controller.onCleanupPartialFiles = { cleanupCalled = true }
        controller.cancel()
        assertEquals(EmergencyRecordingState.CANCELLED, controller.currentState())
        assertTrue(!cleanupCalled)
    }

    @Test
    fun `cancel from a terminal state throws`() {
        val clock = FakeClock()
        val controller = EmergencyTimerController(clock)
        controller.start()
        controller.stop()
        assertThrows(IllegalEmergencyTransition::class.java) { controller.cancel() }
    }

    @Test
    fun `reset returns controller to IDLE so a new session can start`() {
        val clock = FakeClock()
        val controller = EmergencyTimerController(clock)
        controller.start()
        clock.advanceSeconds(5)
        controller.stop()
        controller.reset()
        assertEquals(EmergencyRecordingState.IDLE, controller.currentState())
        assertEquals(0, controller.elapsedSecondsSinceStart())
        controller.start() // should not throw now that we're back in IDLE
        assertEquals(EmergencyRecordingState.RECORDING, controller.currentState())
    }

    @Test
    fun `start() throws if already recording`() {
        val controller = EmergencyTimerController(FakeClock())
        controller.start()
        assertThrows(IllegalEmergencyTransition::class.java) { controller.start() }
    }
}
