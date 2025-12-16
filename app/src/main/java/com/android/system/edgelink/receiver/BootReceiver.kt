package com.android.system.edgelink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.system.edgelink.service.EdgeLinkService
import com.android.system.edgelink.util.AppPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            
            val prefs = AppPrefs(context)
            if (prefs.isEnabled()) {
                context.startService(Intent(context, EdgeLinkService::class.java))
            }
        }
    }
}
