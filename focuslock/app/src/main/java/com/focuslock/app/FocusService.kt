package com.focuslock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat

class FocusService : Service() {

    companion object {
        const val ACTION_STOP = "com.focuslock.app.ACTION_STOP"
        private const val CHANNEL_ID = "focuslock_channel"
        private const val NOTIF_ID = 1

        // Pomodoro durations in seconds
        private const val FOCUS_DURATION_SEC = 25 * 60
        private const val REST_DURATION_SEC = 5 * 60
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var eventEndTimeMs = 0L

    // Pomodoro state
    private var remainingSeconds = FOCUS_DURATION_SEC
    private var isFocusPhase = true

    private val tick = object : Runnable {
        override fun run() {
            val nowMs = System.currentTimeMillis()

            // Check if the calendar event has ended
            if (nowMs >= eventEndTimeMs) {
                stopSelf()
                return
            }

            remainingSeconds--
            if (remainingSeconds <= 0) {
                // Phase flip
                isFocusPhase = !isFocusPhase
                remainingSeconds = if (isFocusPhase) FOCUS_DURATION_SEC else REST_DURATION_SEC
                applyPhaseState()
            }

            // Update shared state timer display
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            FocusState.timerDisplay = "%02d:%02d".format(mins, secs)

            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Read event end time from intent
        val endTime = intent?.getLongExtra("event_end_ms", 0L) ?: 0L
        if (endTime <= System.currentTimeMillis()) {
            stopSelf()
            return START_NOT_STICKY
        }
        eventEndTimeMs = endTime

        if (!isRunning) {
            isRunning = true
            createNotificationChannel()
            startForegroundCompat()

            // Initialise allowed packages
            FocusState.allowedPackages = AppSettings.getEffectiveAllowedApps(this)

            // Start in focus phase
            isFocusPhase = true
            remainingSeconds = FOCUS_DURATION_SEC
            applyPhaseState()
            handler.post(tick)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tick)

        // End focus — hide overlay
        FocusState.isFocusActive = false
        FocusAccessibilityService.instance?.hideOverlay()
    }

    // -------------------------------------------------------------------------
    // Phase management
    // -------------------------------------------------------------------------

    private fun applyPhaseState() {
        if (isFocusPhase) {
            FocusState.isFocusActive = true
            // Refresh allowed packages every time we re-enter focus (user may have changed settings)
            FocusState.allowedPackages = AppSettings.getEffectiveAllowedApps(this)
        } else {
            // Rest — disable blocking, hide overlay
            FocusState.isFocusActive = false
            FocusAccessibilityService.instance?.hideOverlay()
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusLock Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active Pomodoro timer"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val phase = if (isFocusPhase) "Focus" else "Rest"
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, FocusService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock — $phase")
            .setContentText(FocusState.timerDisplay)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification())
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            startForeground(NOTIF_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}
