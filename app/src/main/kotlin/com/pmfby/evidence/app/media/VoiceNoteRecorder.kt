package com.pmfby.evidence.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * Manually-initiated post-call voice note. Requires the on-screen consent notice to have
 * been acknowledged BEFORE this is ever called (enforced in the UI layer — see
 * ui/screens/VoiceNoteScreen.kt — and again server-side: backend/src/routes/callRecordings.js
 * rejects consentAcknowledged != true for kind != voice_message_playback).
 */
class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(caseId: String): File {
        val dir = File(context.cacheDir, "evidence").apply { mkdirs() }
        val file = File(dir, "voice_note_${caseId}_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
            } catch (e: IOException) {
                release()
                throw e
            }
        }
        recorder = mr
        return file
    }

    /** Returns the recorded file and its duration in seconds, or null if nothing was recording. */
    fun stop(): Pair<File, Int>? {
        val mr = recorder ?: return null
        val file = outputFile
        return try {
            mr.stop()
            mr.release()
            recorder = null
            if (file != null) {
                val durationSeconds = readDurationSeconds(file)
                file to durationSeconds
            } else {
                null
            }
        } catch (e: RuntimeException) {
            // stop() can throw if called too soon after start() with no data recorded.
            mr.release()
            recorder = null
            file?.delete()
            null
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            // no-op — we're discarding the file regardless
        }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun readDurationSeconds(file: File): Int {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000).toInt()
        } catch (e: Exception) {
            0
        } finally {
            retriever.release()
        }
    }
}
