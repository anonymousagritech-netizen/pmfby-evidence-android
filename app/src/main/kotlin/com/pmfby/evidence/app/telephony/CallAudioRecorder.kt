package com.pmfby.evidence.app.telephony

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Best-effort ATTEMPT at automatic call-audio capture, started the moment the surveyor
 * places a call. This is genuinely an attempt, not a guarantee: since Android 10 (API 29),
 * [MediaRecorder.AudioSource.VOICE_CALL] is reserved for apps with system/privileged
 * signature permissions (CAPTURE_AUDIO_OUTPUT) on stock AOSP and most OEMs reject it with
 * a SecurityException or silently produce silence for ordinary third-party apps — this is
 * an OS-level restriction, not a bug in this code. Some OEMs (MIUI among them, historically)
 * have been more permissive for sideloaded apps in some versions, which is exactly why this
 * is wired up to actually try on the real device rather than assumed to be impossible.
 *
 * Tries VOICE_CALL first, then VOICE_COMMUNICATION, then plain MIC as a last resort (MIC
 * will capture the surveyor's own voice + whatever the earpiece/speaker leaks, not a clean
 * two-way recording, but is strictly better than nothing and never throws). The manual
 * voice-note upload flow (CallViewModel.uploadCallRecording) remains available regardless
 * of what happens here — this is additive, not a replacement.
 */
object CallAudioRecorder {

    sealed class StartResult {
        data class Started(val source: String, val outputFile: File) : StartResult()
        data class Failed(val reason: String) : StartResult()
    }

    private var activeRecorder: MediaRecorder? = null

    fun start(context: Context, outputFile: File): StartResult {
        val attempts = listOf(
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "MIC" to MediaRecorder.AudioSource.MIC,
        )
        for ((label, source) in attempts) {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            try {
                recorder.setAudioSource(source)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setOutputFile(outputFile.absolutePath)
                recorder.prepare()
                recorder.start()
                activeRecorder = recorder
                Log.i("CallAudioRecorder", "Started recording with audio source $label")
                return StartResult.Started(label, outputFile)
            } catch (e: Exception) {
                Log.w("CallAudioRecorder", "Audio source $label rejected: ${e.javaClass.simpleName}: ${e.message}")
                try { recorder.release() } catch (_: Exception) {}
            }
        }
        return StartResult.Failed("All audio sources (VOICE_CALL, VOICE_COMMUNICATION, MIC) failed to start")
    }

    /** Returns true if something was actually recorded (non-empty file), false otherwise. */
    fun stop(): Boolean {
        val recorder = activeRecorder ?: return false
        activeRecorder = null
        return try {
            recorder.stop()
            true
        } catch (e: Exception) {
            Log.w("CallAudioRecorder", "stop() failed: ${e.message}")
            false
        } finally {
            try { recorder.release() } catch (_: Exception) {}
        }
    }
}
