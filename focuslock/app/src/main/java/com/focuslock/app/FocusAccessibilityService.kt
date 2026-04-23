package com.focuslock.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Accessibility Service — detects foreground app changes in real time.
 *
 * When focus is active (FocusState.isFocusActive == true) and the detected
 * foreground package is NOT in FocusState.allowedPackages, we show a full-screen
 * WindowManager overlay built from plain Views (no Compose, no lifecycle issues).
 *
 * The overlay shows:
 *  - A countdown timer (mirrored from FocusService via FocusState)
 *  - A grid of allowed app shortcuts the user can tap to switch to
 *
 * Tapping an allowed app icon hides the overlay and launches that app.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var timerLabel: TextView? = null
    private var isOverlayShowing = false

    // Ticker to update the timer label every second while overlay is visible
    private val timerTicker = object : Runnable {
        override fun run() {
            if (isOverlayShowing) {
                timerLabel?.text = FocusState.timerDisplay
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        FocusAccessibilityService.instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        FocusAccessibilityService.instance = null
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() { /* required override */ }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.isEmpty()) return

        // Ignore system UI / overlays that aren't real apps
        if (pkg == "android" || pkg == "com.android.systemui") return

        if (FocusState.isFocusActive) {
            if (pkg !in FocusState.allowedPackages) {
                showOverlay()
            } else {
                hideOverlay()
            }
        } else {
            hideOverlay()
        }
    }

    // -------------------------------------------------------------------------
    // Overlay management
    // -------------------------------------------------------------------------

    fun showOverlay() {
        mainHandler.post {
            if (isOverlayShowing) {
                // Just refresh app grid in case allowed list changed
                refreshAppGrid()
                return@post
            }
            if (!canDrawOverlays()) return@post

            val wm = windowManager ?: return@post

            val root = buildOverlayView()
            overlayRoot = root

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                wm.addView(root, params)
                isOverlayShowing = true
                mainHandler.post(timerTicker)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideOverlay() {
        mainHandler.post {
            if (!isOverlayShowing) return@post
            val wm = windowManager ?: return@post
            val root = overlayRoot ?: return@post
            try {
                wm.removeView(root)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayRoot = null
            timerLabel = null
            isOverlayShowing = false
            mainHandler.removeCallbacks(timerTicker)
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else true
    }

    // -------------------------------------------------------------------------
    // View building
    // -------------------------------------------------------------------------

    private fun buildOverlayView(): FrameLayout {
        val ctx = this

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(245, 18, 18, 18))
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 80, 48, 48)
        }

        // --- Lock icon area ---
        val lockLabel = TextView(ctx).apply {
            text = "🔒 Focus Mode Active"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(lockLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 })

        // --- Timer display ---
        val timerView = TextView(ctx).apply {
            text = FocusState.timerDisplay
            textSize = 54f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        timerLabel = timerView
        container.addView(timerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 })

        // --- Phase label ---
        val phaseLabel = TextView(ctx).apply {
            text = "Stay focused — rest coming soon"
            textSize = 14f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
        }
        container.addView(phaseLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 48 })

        // --- Allowed apps label ---
        val appsLabel = TextView(ctx).apply {
            text = "Allowed Apps"
            textSize = 13f
            setTextColor(Color.argb(160, 255, 255, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        container.addView(appsLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })

        // --- App grid ---
        val grid = buildAppGrid()
        container.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(container, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        return root
    }

    private fun buildAppGrid(): GridLayout {
        val ctx = this
        val pm = packageManager
        val grid = GridLayout(ctx).apply {
            columnCount = 4
        }

        val allowed = FocusState.allowedPackages
            .filter { it != packageName } // don't show FocusLock itself
            .take(12) // cap at 12 icons (3 rows × 4 cols)

        for (pkg in allowed) {
            val appIcon: Drawable? = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
            val appName: String = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }

            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(20, 20, 20, 20)
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)),
                    null, null
                )
                setOnClickListener {
                    hideOverlay()
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        ctx.startActivity(launchIntent)
                    }
                }
            }

            val iconView = ImageView(ctx).apply {
                setImageDrawable(appIcon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val iconSize = (56 * resources.displayMetrics.density).toInt()
            cell.addView(iconView, LinearLayout.LayoutParams(iconSize, iconSize).apply { bottomMargin = 8 })

            val nameView = TextView(ctx).apply {
                text = if (appName.length > 10) appName.take(9) + "…" else appName
                textSize = 11f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            cell.addView(nameView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            })
        }

        return grid
    }

    private fun refreshAppGrid() {
        val root = overlayRoot ?: return
        // Rebuild and swap the grid (find the container LinearLayout, rebuild grid at index 4)
        mainHandler.post {
            try {
                val container = root.getChildAt(0) as? LinearLayout ?: return@post
                // Remove old grid (last child) and add fresh one
                val gridIndex = container.childCount - 1
                container.removeViewAt(gridIndex)
                container.addView(buildAppGrid(), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        /** Nullable reference — only valid while the service is connected */
        @Volatile var instance: FocusAccessibilityService? = null

        fun isEnabled(ctx: android.content.Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val componentName = "${ctx.packageName}/.FocusAccessibilityService"
            return enabledServices.split(":").any { it.equals(componentName, ignoreCase = true) }
        }
    }
}
