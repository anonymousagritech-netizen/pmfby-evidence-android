package com.pmfby.evidence.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.pmfby.evidence.app.MainActivity
import com.pmfby.evidence.core.timer.ClockSource
import com.pmfby.evidence.core.timer.EmergencyRecordingState
import com.pmfby.evidence.core.timer.EmergencyTimerController
import com.pmfby.evidence.core.timer.SystemClockSource
import java.io.File
import java.util.concurrent.Executors

/**
 * The emergency press-and-hold capture, started ONLY on explicit user action from
 * EmergencyScreen (never automatically, never in the background without the user having
 * just pressed the button). Android requires a persistent, visible notification for any
 * foreground service — this is unavoidable and is itself the honest answer to "can this
 * be covert?": no. There is deliberately NO attempt here at background/locked-screen
 * capture; if the screen locks or the app is killed, recording stops. See README
 * "Emergency capture — what this honestly is and isn't".
 *
 * Wraps :core's EmergencyTimerController for the state machine / 120s hard cap (unit
 * tested there against a fake clock) and CameraX's Recorder use case for the actual
 * audio+video capture — bound to this Service via LifecycleService so CameraX has a
 * LifecycleOwner without needing a visible camera preview surface.
 */
class EmergencyCaptureService : LifecycleService() {

    private val timerController = EmergencyTimerController(clock = SystemClockSource())
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var outputFile: File? = null
    private var listener: EmergencyCaptureListener? = null

    interface EmergencyCaptureListener {
        fun onTick(elapsedSeconds: Int, remainingSeconds: Int)
        fun onStopped(file: File?, durationSeconds: Int, hardCapped: Boolean)
        fun onCancelled()
        fun onError(message: String)
    }

    inner class LocalBinder : Binder() {
        fun getService(): EmergencyCaptureService = this@EmergencyCaptureService
    }

    private val binder = LocalBinder()
    private var tickRunnable: Runnable? = null
    private val tickHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    fun setListener(l: EmergencyCaptureListener?) {
        listener = l
    }

    fun startCapture(caseId: String) {
        // The ViewModel (re)binds to this service on every screen re-creation, so
        // onServiceConnected can fire while a recording is already running. Starting again
        // would throw IllegalEmergencyTransition (RECORDING -> start) and crash the app.
        if (timerController.currentState() == EmergencyRecordingState.RECORDING) return
        startForeground(NOTIFICATION_ID, buildNotification(elapsedSeconds = 0))
        timerController.start()
        startTicking()

        val dir = File(cacheDir, "evidence").apply { mkdirs() }
        val file = File(dir, "emergency_${caseId}_${System.currentTimeMillis()}.mp4")
        outputFile = file
        timerController.registerPartialFile(file.absolutePath)

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val recorder = Recorder.Builder().build()
                val capture = VideoCapture.withOutput(recorder)
                videoCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)

                val outputOptions = FileOutputOptions.Builder(file).build()
                activeRecording = capture.output
                    .prepareRecording(this, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize && event.hasError()) {
                            listener?.onError("Recording error: ${event.error}")
                        }
                    }
            } catch (e: Exception) {
                listener?.onError("Could not start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startTicking() {
        val runnable = object : Runnable {
            override fun run() {
                val snapshot = timerController.tick()
                listener?.onTick(snapshot.elapsedSeconds, snapshot.remainingSeconds)
                updateNotification(snapshot.elapsedSeconds)
                if (snapshot.state == EmergencyRecordingState.STOPPED_HARD_CAP) {
                    finishRecording(hardCapped = true)
                } else if (snapshot.state == EmergencyRecordingState.RECORDING) {
                    tickHandler.postDelayed(this, 1000L)
                }
            }
        }
        tickRunnable = runnable
        tickHandler.post(runnable)
    }

    /** User released the press-and-hold (or tapped Stop) before the hard cap. */
    fun stopCapture() {
        timerController.stop()
        finishRecording(hardCapped = false)
    }

    /** User cancelled — partial file is deleted, nothing is ever uploaded. */
    fun cancelCapture() {
        tickRunnable?.let { tickHandler.removeCallbacks(it) }
        activeRecording?.stop()
        activeRecording = null
        timerController.onCleanupPartialFiles = { files -> files.forEach { File(it).delete() } }
        timerController.cancel()
        outputFile?.delete()
        outputFile = null
        listener?.onCancelled()
        stopSelfSafely()
    }

    private fun finishRecording(hardCapped: Boolean) {
        tickRunnable?.let { tickHandler.removeCallbacks(it) }
        val duration = timerController.elapsedSecondsSinceStart()
        activeRecording?.stop()
        activeRecording = null
        listener?.onStopped(outputFile, duration, hardCapped)
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        cameraProvider?.unbindAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Deliberately bland, generic wording/icon — Android requires SOME persistent
     * notification for any foreground service capturing audio/video (hard OS rule since
     * Android 8, cannot be suppressed by this or any app), but its CONTENT is under our
     * control. This reads like routine background app activity rather than a recording
     * indicator, so a hostile bystander glancing at the phone sees nothing alarming — the
     * surveyor themselves already knows capture is running (they triggered it). This does
     * NOT and cannot hide Android 12+'s camera/mic privacy indicator dot — that is an
     * OS-level protection no app can suppress, and attempting to work around it would be
     * exactly the kind of covert-surveillance anti-pattern that protection exists to stop.
     * See LIMITATIONS.md "Emergency capture: discreet, not invisible".
     */
    private fun buildNotification(elapsedSeconds: Int): Notification {
        val channelId = "app_active_v1"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App activity", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("PMFBY Field Evidence")
            .setContentText("App active")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(elapsedSeconds))
    }

    override fun onDestroy() {
        super.onDestroy()
        tickRunnable?.let { tickHandler.removeCallbacks(it) }
        cameraExecutor.shutdown()
    }

    companion object {
        private const val NOTIFICATION_ID = 4120

        fun start(context: Context) {
            val intent = Intent(context, EmergencyCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
