package com.pmfby.evidence.app.telephony

import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File

/**
 * Android does not let a third-party app record the far end of a cellular call, but the phone's
 * own recorder (MIUI/HyperOS "Call recording", Samsung, Pixel...) already does, and writes the
 * file to a known folder — on MIUI as `Name(00918587870501)_20261021110200.mp3`. This finds that
 * file after the call so the app can upload it as evidence with no user action.
 *
 * Needs "All files access" because these folders are not indexed in MediaStore.
 */
object SystemCallRecordingFinder {

    private const val TAG = "CallRecFinder"
    private val AUDIO_EXT = setOf("mp3", "m4a", "amr", "wav", "aac", "3gp", "opus", "ogg", "awb")

    private val RECORDER_DIRS = listOf(
        "MIUI/sound_recorder/call_rec",
        "Recordings/Call",
        "Recordings/Call recordings",
        "Recordings/Call Recordings",
        "Music/Recordings/Call Recordings",
        "Call",
        "CallRecordings",
        "Sounds/CallRecord",
        "Recorder/call",
        "PhoneRecord",
    )

    fun hasAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun digitsTail(phone: String): String = phone.filter { it.isDigit() }.takeLast(10)

    private fun candidates(sinceMillis: Long): List<File> {
        val root = Environment.getExternalStorageDirectory()
        return RECORDER_DIRS.asSequence()
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles()?.asSequence() ?: emptySequence() }
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXT && it.length() > 0 && it.lastModified() >= sinceMillis - 5_000 }
            .toList()
    }

    /** Best match: file name carries the dialled number, else the newest recording made since the call began. */
    private fun pick(phone: String, sinceMillis: Long): File? {
        val tail = digitsTail(phone)
        val all = candidates(sinceMillis).sortedByDescending { it.lastModified() }
        return all.firstOrNull { tail.isNotEmpty() && it.name.filter { c -> c.isDigit() }.contains(tail) } ?: all.firstOrNull()
    }

    /**
     * Polls until a matching recording exists and has stopped growing (the recorder is still
     * writing while the call is live). Returns null if nothing appears within [timeoutMs].
     */
    suspend fun waitForFinishedRecording(phone: String, callStartMillis: Long, timeoutMs: Long): File? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastPath: String? = null
        var lastSize = -1L
        var lastMod = -1L
        while (System.currentTimeMillis() < deadline) {
            val f = pick(phone, callStartMillis)
            if (f != null) {
                val size = f.length()
                val mod = f.lastModified()
                if (f.absolutePath == lastPath && size == lastSize && mod == lastMod && size > 0) {
                    Log.i(TAG, "Found finished recording ${f.name} ($size bytes)")
                    return f
                }
                lastPath = f.absolutePath
                lastSize = size
                lastMod = mod
            }
            delay(1_500)
        }
        Log.w(TAG, "No recording found for ...${digitsTail(phone)} since $callStartMillis")
        return null
    }

    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "amr" -> "audio/amr"
        "wav" -> "audio/wav"
        "3gp" -> "audio/3gpp"
        "opus", "ogg" -> "audio/ogg"
        else -> "audio/mpeg"
    }
}
