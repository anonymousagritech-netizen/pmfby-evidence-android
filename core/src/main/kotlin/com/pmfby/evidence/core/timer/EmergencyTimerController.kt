package com.pmfby.evidence.core.timer

/**
 * Controller for the emergency press-and-hold recording session.
 *
 * Deliberately has NO Android imports (no MediaRecorder/CameraX/Service calls) so it can
 * be unit tested on the plain JVM without an emulator — the actual recording calls
 * (MediaRecorder/CameraX, foreground service start) live in the `app` module's
 * EmergencyRecordingService and call into this controller purely for state/timing
 * decisions. A fake [ClockSource] drives the hard-cap test deterministically; no real
 * `Thread.sleep` is needed to prove the 120s cutoff.
 *
 * Mirrors the design of the earlier React Native prototype's `emergencyTimer.js`
 * controller (see ../../../backend-and-web/_deprecated note and
 * ../../_deprecated_react-native-mobile/src/lib/emergencyTimer.js) — same hard cap,
 * same state machine, reimplemented here with zero Android/Expo dependencies.
 */
interface ClockSource {
    /** Milliseconds since an arbitrary fixed epoch; monotonic within one session. */
    fun nowMillis(): Long
}

/** Real wall-clock implementation used by the app. */
class SystemClockSource : ClockSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

enum class EmergencyRecordingState {
    IDLE,
    RECORDING,
    STOPPED_COMPLETE,   // reached naturally (user stop) or hit the hard cap
    STOPPED_HARD_CAP,   // reached the 120s hard cap specifically
    CANCELLED,          // user cancelled; partial files must be cleaned up
}

data class EmergencyTimerSnapshot(
    val state: EmergencyRecordingState,
    val elapsedSeconds: Int,
    val remainingSeconds: Int,
)

/** Raised if start()/stop()/cancel() is called from a state that doesn't allow it. */
class IllegalEmergencyTransition(message: String) : IllegalStateException(message)

class EmergencyTimerController(
    private val clock: ClockSource = SystemClockSource(),
    private val hardCapSeconds: Int = HARD_CAP_SECONDS,
) {
    companion object {
        const val HARD_CAP_SECONDS = 120
    }

    private var state: EmergencyRecordingState = EmergencyRecordingState.IDLE
    private var startedAtMillis: Long = 0L
    private var stoppedAtMillis: Long? = null

    /** Files created during this session (voice/video temp paths) tracked for cleanup. */
    private val partialFiles = mutableListOf<String>()

    /** Callback the app module wires up to actually delete partial files on cancel. */
    var onCleanupPartialFiles: ((List<String>) -> Unit)? = null

    fun currentState(): EmergencyRecordingState = state

    fun registerPartialFile(path: String) {
        partialFiles.add(path)
    }

    /** Begin a recording session. Must be called from IDLE. */
    fun start() {
        if (state != EmergencyRecordingState.IDLE) {
            throw IllegalEmergencyTransition("start() called from state $state, expected IDLE")
        }
        startedAtMillis = clock.nowMillis()
        stoppedAtMillis = null
        partialFiles.clear()
        state = EmergencyRecordingState.RECORDING
    }

    /**
     * Poll elapsed time. If the hard cap has been reached, this transitions the state to
     * STOPPED_HARD_CAP as a side effect (the caller — a periodic UI/service tick — is
     * expected to call this regularly while RECORDING, exactly like the app's 1s ticker).
     */
    fun tick(): EmergencyTimerSnapshot {
        if (state == EmergencyRecordingState.RECORDING) {
            val elapsed = elapsedSecondsSinceStart()
            if (elapsed >= hardCapSeconds) {
                stoppedAtMillis = startedAtMillis + hardCapSeconds * 1000L
                state = EmergencyRecordingState.STOPPED_HARD_CAP
            }
        }
        return snapshot()
    }

    /** User-initiated stop (releasing the press-and-hold, or tapping Stop). */
    fun stop() {
        if (state != EmergencyRecordingState.RECORDING) {
            throw IllegalEmergencyTransition("stop() called from state $state, expected RECORDING")
        }
        stoppedAtMillis = clock.nowMillis()
        state = EmergencyRecordingState.STOPPED_COMPLETE
    }

    /** User-initiated cancel. Triggers cleanup of any partial files via the callback. */
    fun cancel() {
        if (state != EmergencyRecordingState.RECORDING && state != EmergencyRecordingState.IDLE) {
            throw IllegalEmergencyTransition("cancel() called from state $state, expected RECORDING or IDLE")
        }
        state = EmergencyRecordingState.CANCELLED
        stoppedAtMillis = clock.nowMillis()
        if (partialFiles.isNotEmpty()) {
            onCleanupPartialFiles?.invoke(partialFiles.toList())
        }
        partialFiles.clear()
    }

    /** Reset back to IDLE so a new session can start (e.g. after upload completes). */
    fun reset() {
        state = EmergencyRecordingState.IDLE
        startedAtMillis = 0L
        stoppedAtMillis = null
        partialFiles.clear()
    }

    fun elapsedSecondsSinceStart(): Int {
        if (state == EmergencyRecordingState.IDLE) return 0
        val endMillis = stoppedAtMillis ?: clock.nowMillis()
        val elapsedMs = (endMillis - startedAtMillis).coerceAtLeast(0)
        return (elapsedMs / 1000L).toInt().coerceAtMost(hardCapSeconds)
    }

    fun snapshot(): EmergencyTimerSnapshot {
        val elapsed = elapsedSecondsSinceStart()
        return EmergencyTimerSnapshot(
            state = state,
            elapsedSeconds = elapsed,
            remainingSeconds = (hardCapSeconds - elapsed).coerceAtLeast(0),
        )
    }
}
