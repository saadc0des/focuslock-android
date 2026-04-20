package com.focuslock.app

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREFS_NAME = "focuslock_prefs"
    private const val KEY_ALLOWED_APPS = "allowed_apps"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllowedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

    fun setAllowedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALLOWED_APPS, packages).apply()
    }

    /** Always allow FocusLock itself so the user can reach Settings during focus */
    fun getEffectiveAllowedApps(context: Context): Set<String> {
        return getAllowedApps(context) + context.packageName
    }
}
