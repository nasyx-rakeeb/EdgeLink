package com.android.system.edgelink.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.android.system.edgelink.R
import com.android.system.edgelink.util.AppPrefs

class EdgeHandleView(
    context: Context,
    private val onClick: () -> Unit
) : View(context) {

    init {
        val prefs = AppPrefs(context)
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.setColor(context.getColor(R.color.edgelink_bg)) 
        shape.cornerRadius = 20f
        
        // Apply Opacity Preference
        shape.alpha = prefs.getHandleOpacity()
        
        background = shape
        
        setOnClickListener {
            onClick()
        }
    }
}
