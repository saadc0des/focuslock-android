package com.focuslock.app

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    private val checkBoxMap = mutableMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedAllowed = AppSettings.getAllowedApps(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val header = TextView(this).apply {
            text = "Allowed Apps During Focus"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 32)
        }
        root.addView(header)

        val sub = TextView(this).apply {
            text = "These apps can be opened during a focus block."
            textSize = 12f
            setTextColor(Color.argb(160, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(32, 0, 32, 24)
        }
        root.addView(sub)

        val scrollView = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 32)
        }

        val apps = getInstalledLaunchableApps()
        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 16, 16, 16)
            }

            val icon = android.widget.ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            val iconSize = (40 * resources.displayMetrics.density).toInt()
            row.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize).apply { rightMargin = 20 })

            val name = TextView(this).apply {
                text = app.loadLabel(packageManager).toString()
                textSize = 15f
                setTextColor(Color.WHITE)
            }
            row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val cb = CheckBox(this).apply {
                isChecked = savedAllowed.contains(app.activityInfo.packageName)
                setOnCheckedChangeListener { _, _ -> saveCurrentSelection() }
            }
            checkBoxMap[app.activityInfo.packageName] = cb
            row.addView(cb)

            listLayout.addView(row)
        }

        scrollView.addView(listLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun saveCurrentSelection() {
        val selected = checkBoxMap.filter { it.value.isChecked }.keys.toSet()
        AppSettings.setAllowedApps(this, selected)
        // Immediately update in-process state if service is running
        FocusState.allowedPackages = AppSettings.getEffectiveAllowedApps(this)
    }

    private fun getInstalledLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        // Exclude FocusLock itself
        return apps
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }
}
