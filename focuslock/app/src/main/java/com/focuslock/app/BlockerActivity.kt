package com.focuslock.app

import androidx.activity.ComponentActivity
import android.os.Bundle

/**
 * Kept as a stub. The real blocking is now handled by FocusAccessibilityService
 * + WindowManager overlay. This activity is no longer launched during focus sessions.
 */
class BlockerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
