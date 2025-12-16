package com.android.system.edgelink.util

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("edgelink_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(v: Boolean) = prefs.edit().putBoolean("enabled", v).apply()

    fun getEdgePosition(): Int = prefs.getInt("position", 1) // 1=Left, 0=Right
    fun setEdgePosition(p: Int) = prefs.edit().putInt("position", p).apply()

    fun getVerticalOffset(): Int = prefs.getInt("vertical_offset", 50)
    fun setVerticalOffset(v: Int) = prefs.edit().putInt("vertical_offset", v).apply()

    fun getHandleOpacity(): Int = prefs.getInt("handle_opacity", 255)
    fun setHandleOpacity(v: Int) = prefs.edit().putInt("handle_opacity", v).apply()

    fun getHandleWidth(): Int = prefs.getInt("handle_width", 20)
    fun setHandleWidth(v: Int) = prefs.edit().putInt("handle_width", v).apply()

    fun getHandleHeight(): Int = prefs.getInt("handle_height", 300)
    fun setHandleHeight(v: Int) = prefs.edit().putInt("handle_height", v).apply()

    fun getPanelOpacity(): Int = prefs.getInt("panel_opacity", 240)
    fun setPanelOpacity(v: Int) = prefs.edit().putInt("panel_opacity", v).apply()

    fun getPanelHeightPercent(): Int = prefs.getInt("panel_height", 70)
    fun setPanelHeightPercent(v: Int) = prefs.edit().putInt("panel_height", v).apply()

    // --- NEW: PINNED APPS ---
    fun getPinnedApps(): Set<String> {
        return prefs.getStringSet("pinned_apps", emptySet()) ?: emptySet()
    }

    fun setPinnedApps(packages: Set<String>) {
        prefs.edit().putStringSet("pinned_apps", packages).apply()
    }

    fun isAppPinned(packageName: String): Boolean {
        return getPinnedApps().contains(packageName)
    }

    fun toggleAppPin(packageName: String) {
        val current = getPinnedApps().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setPinnedApps(current)
    }
}
