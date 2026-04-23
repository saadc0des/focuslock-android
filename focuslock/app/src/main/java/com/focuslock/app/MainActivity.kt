package com.focuslock.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var activeEventEndMs = 0L

    // Permission launchers
    private val calendarPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { checkAndRequestPermissions() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { checkAndRequestPermissions() }

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------
    private lateinit var statusText: TextView
    private lateinit var eventText: TextView
    private lateinit var accessibilityBtn: Button
    private lateinit var overlayBtn: Button
    private lateinit var usageBtn: Button
    private lateinit var settingsBtn: Button

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build a simple programmatic layout (no XML needed)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        val title = TextView(this).apply {
            text = "FocusLock"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(title, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 32 })

        statusText = TextView(this).apply {
            text = "Checking permissions…"
            textSize = 14f
            setTextColor(android.graphics.Color.argb(200, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(statusText, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 })

        eventText = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#BB86FC"))
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(eventText, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 32 })

        accessibilityBtn = makeButton("Enable Accessibility Service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        layout.addView(accessibilityBtn, buttonParams())

        overlayBtn = makeButton("Allow Overlay Permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        layout.addView(overlayBtn, buttonParams())

        usageBtn = makeButton("Allow Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        layout.addView(usageBtn, buttonParams())

        settingsBtn = makeButton("Allowed Apps Settings") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        layout.addView(settingsBtn, buttonParams())

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
        handler.post(calendarPoller)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(calendarPoller)
    }

    // -------------------------------------------------------------------------
    // Permission checks
    // -------------------------------------------------------------------------

    private fun checkAndRequestPermissions() {
        val calOk = ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.READ_CALENDAR) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!calOk) {
            calendarPermLauncher.launch(android.Manifest.permission.READ_CALENDAR)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifOk = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!notifOk) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val accessibilityOk = FocusAccessibilityService.isEnabled(this)
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        accessibilityBtn.visibility = if (accessibilityOk)
            android.view.View.GONE else android.view.View.VISIBLE
        overlayBtn.visibility = if (overlayOk)
            android.view.View.GONE else android.view.View.VISIBLE

        val allOk = accessibilityOk && overlayOk
        statusText.text = if (allOk) "✓ All permissions granted — monitoring calendar"
        else "⚠ Grant all permissions above to enable blocking"
    }

    // -------------------------------------------------------------------------
    // Calendar polling (every 30 s while app is in foreground)
    // -------------------------------------------------------------------------

    private val calendarPoller = object : Runnable {
        override fun run() {
            val event = getActiveCalendarEvent()
            handleEvent(event)
            handler.postDelayed(this, 30_000L)
        }
    }

    private data class CalEvent(val title: String, val endMs: Long)

    private fun getActiveCalendarEvent(): CalEvent? {
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )
        val selection = "${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DTEND} >= ?"
        val args = arrayOf(now.toString(), now.toString())

        try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, args, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0) ?: continue
                    val allDay = cursor.getInt(3) != 0
                    val endMs = cursor.getLong(2)

                    // Skip all-day events (birthdays, holidays, etc.)
                    if (allDay) continue
                    // Skip blank titles and rest-time markers
                    if (title.isBlank()) continue
                    if (title.trim().lowercase() == "rest time") continue

                    return CalEvent(title, endMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun handleEvent(event: CalEvent?) {
        if (event != null && event.endMs > System.currentTimeMillis()) {
            eventText.text = "Active event: ${event.title}"
            if (activeEventEndMs != event.endMs) {
                activeEventEndMs = event.endMs
                startFocusService(event.endMs)
            }
        } else {
            if (activeEventEndMs != 0L) {
                activeEventEndMs = 0L
                stopFocusService()
            }
            eventText.text = "No active calendar event"
        }
    }

    private fun startFocusService(endMs: Long) {
        val intent = Intent(this, FocusService::class.java).apply {
            putExtra("event_end_ms", endMs)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopFocusService() {
        startService(Intent(this, FocusService::class.java).apply {
            action = FocusService.ACTION_STOP
        })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setBackgroundColor(android.graphics.Color.parseColor("#3700B3"))
        setTextColor(android.graphics.Color.WHITE)
        textSize = 14f
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun buttonParams() = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 16 }
}
