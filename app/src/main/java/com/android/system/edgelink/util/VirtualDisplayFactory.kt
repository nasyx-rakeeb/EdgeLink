package com.android.system.edgelink.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface

class VirtualDisplayFactory(private val context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int, // NEW: We pass this explicitly now
        surface: Surface
    ): VirtualDisplay {
        
        // 1024 = DESTROY_CONTENT_ON_REMOVAL
        // 256  = PUBLIC
        // 8    = OWN_CONTENT_ONLY
        // 4    = PRESENTATION
        val flags = 1024 or 256 or 8 or 4

        return displayManager.createVirtualDisplay(
            name,
            width,
            height,
            densityDpi,
            surface,
            flags
        )
    }
}
