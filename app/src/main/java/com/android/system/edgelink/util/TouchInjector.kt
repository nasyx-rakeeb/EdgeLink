package com.android.system.edgelink.util

import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

class TouchInjector {

    private val inputManager = InputManager.getInstance()
    
    // Inject Input Event Method (Hidden API)
    private val injectMethod = InputManager::class.java.getMethod(
        "injectInputEvent",
        InputEvent::class.java,
        Int::class.javaPrimitiveType
    )

    fun inject(event: MotionEvent, displayId: Int) {
        val newEvent = MotionEvent.obtain(event)
        newEvent.displayId = displayId
        try {
            injectMethod.invoke(inputManager, newEvent, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            newEvent.recycle()
        }
    }

    // NEW: Inject Key Event (For Back Button)
    fun injectKey(keyCode: Int, displayId: Int) {
        val now = SystemClock.uptimeMillis()
        try {
            // Key Down
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0).apply { this.displayId = displayId }
            injectMethod.invoke(inputManager, down, 0)

            // Key Up
            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0).apply { this.displayId = displayId }
            injectMethod.invoke(inputManager, up, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
