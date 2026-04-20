package com.focuslock.app

/**
 * Singleton shared in-process between FocusService and FocusAccessibilityService.
 * Both run in the same process, so this is safe and avoids IPC complexity.
 */
object FocusState {
    /** True while a focus (work) block is active — overlay should be shown for disallowed apps */
    @Volatile var isFocusActive: Boolean = false

    /** Packages allowed during focus. Always includes com.focuslock.app itself. */
    @Volatile var allowedPackages: Set<String> = emptySet()

    /** Human-readable countdown shown on the overlay, e.g. "24:13" */
    @Volatile var timerDisplay: String = "25:00"
}
