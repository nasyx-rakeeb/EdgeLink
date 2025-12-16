package com.android.system.edgelink.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.system.edgelink.model.AppInfo

class AppLauncher(private val context: Context) {
    fun getAllLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // Match ALL apps, no filters
        val flags = PackageManager.MATCH_ALL
        return pm.queryIntentActivities(mainIntent, flags)
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(pm)
                )
            }.sortedBy { it.label }
    }
}
