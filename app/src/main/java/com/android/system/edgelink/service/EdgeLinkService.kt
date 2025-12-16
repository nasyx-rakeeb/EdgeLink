package com.android.system.edgelink.service

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import com.android.system.edgelink.model.AppInfo
import com.android.system.edgelink.util.AppLauncher
import com.android.system.edgelink.util.AppPrefs
import com.android.system.edgelink.util.TouchInjector
import com.android.system.edgelink.util.VirtualDisplayFactory
import com.android.system.edgelink.view.EdgeHandleView
import com.android.system.edgelink.view.FloatingWindowView
import com.android.system.edgelink.view.SidebarPanelView

class EdgeLinkService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: AppPrefs
    private lateinit var displayFactory: VirtualDisplayFactory
    private lateinit var appLauncher: AppLauncher
    private lateinit var touchInjector: TouchInjector
    private lateinit var activityManager: ActivityManager
    
    private var edgeHandleView: EdgeHandleView? = null
    private var sidebarPanelView: SidebarPanelView? = null
    private var isPanelOpen = false
    
    private val activeWindows = mutableMapOf<FloatingWindowView, VirtualDisplay>()
    private val taskToWindowMap = mutableMapOf<Int, FloatingWindowView>()
    private val displayMetrics = DisplayMetrics()

    private val taskStackListener = object : TaskStackListener() {
        override fun onTaskRemoved(taskId: Int) {
            Handler(Looper.getMainLooper()).post {
                val window = taskToWindowMap[taskId]
                if (window != null) {
                    closeFloatingWindow(window)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        prefs = AppPrefs(this)
        displayFactory = VirtualDisplayFactory(this)
        appLauncher = AppLauncher(this)
        touchInjector = TouchInjector()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        try { ActivityTaskManager.getService().registerTaskStackListener(taskStackListener) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshHandle()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        refreshHandle()
        restackBubbles() 
    }

    private fun refreshHandle() {
        if (edgeHandleView != null) { windowManager.removeView(edgeHandleView); edgeHandleView = null }
        setupEdgeHandle()
    }

    private fun setupEdgeHandle() {
        edgeHandleView = EdgeHandleView(this) { toggleSidebar() }
        val isLeft = (prefs.getEdgePosition() == 1)
        val offsetPercent = prefs.getVerticalOffset()
        val yOffset = ((offsetPercent - 50) / 50f * (displayMetrics.heightPixels / 2)).toInt()

        val params = WindowManager.LayoutParams(
            prefs.getHandleWidth(), prefs.getHandleHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            y = yOffset
        }
        windowManager.addView(edgeHandleView, params)
    }

    private fun toggleSidebar() { if (isPanelOpen) closeSidebar() else openSidebar() }

    private fun openSidebar() {
        if (isPanelOpen) return
        
        // Pass a callback that actually removes the view, but let the View decide WHEN to call it
        sidebarPanelView = SidebarPanelView(this, 
            isLeft = (prefs.getEdgePosition() == 1),
            onCloseRequest = { 
                // This is the actual removal callback
                if (sidebarPanelView != null) {
                    windowManager.removeView(sidebarPanelView)
                    sidebarPanelView = null
                    isPanelOpen = false
                }
            },
            onAppSelected = { app -> launchFloatingWindow(app) }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        )
        windowManager.addView(sidebarPanelView, params)
        isPanelOpen = true
    }

    private fun closeSidebar() {
        if (!isPanelOpen || sidebarPanelView == null) return
        // Trigger animation. The view will call onCloseRequest() when done.
        sidebarPanelView?.closePanel()
    }

    private fun minimizeOthers(except: FloatingWindowView?) {
        activeWindows.keys.forEach { window ->
            if (window != except && !window.isMinimized) {
                window.applyMinimize(0, 0)
            }
        }
        restackBubbles()
    }

    private fun launchFloatingWindow(app: AppInfo) {
        val existingWindow = activeWindows.keys.find { it.appInfo.packageName == app.packageName }
        
        if (existingWindow != null) { 
            minimizeOthers(existingWindow)
            existingWindow.maximize() 
            return 
        }

        minimizeOthers(null)

        lateinit var windowView: FloatingWindowView
        
        windowView = FloatingWindowView(this, app,
            onClose = { closeFloatingWindow(windowView) },
            onSurfaceReady = { surface, w, h ->
                if (activeWindows.containsKey(windowView)) {
                    val existingVd = activeWindows[windowView]
                    existingVd?.surface = surface
                    existingVd?.resize(w, h, 320)
                } else {
                    val vd = displayFactory.createVirtualDisplay("EdgeLink-VD", w, h, 320, surface)
                    activeWindows[windowView] = vd
                    
                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        val options = ActivityOptions.makeBasic()
                        options.launchDisplayId = vd.display.displayId
                        startActivity(launchIntent, options.toBundle())
                        Handler(Looper.getMainLooper()).postDelayed({ windowView.hideLoading(); mapWindowToTask(windowView, app.packageName) }, 500)
                    }
                }
            },
            onSurfaceDestroyed = { },
            onResize = { w, h -> activeWindows[windowView]?.resize(w, h, 320) },
            onExpandStart = {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = 0 
                    startActivity(launchIntent, options.toBundle())
                    closeFloatingWindow(windowView)
                }
            },
            onBackPress = {
                val vd = activeWindows[windowView]
                if (vd != null) touchInjector.injectKey(KeyEvent.KEYCODE_BACK, vd.display.displayId)
            },
            onMinimizeRequest = { targetWindow ->
                targetWindow.applyMinimize(0, 0)
                restackBubbles()
            },
            onMaximize = {
                minimizeOthers(windowView)
            },
            onFocus = {
                // Focus logic
            }
        )

        windowView.setTouchCallback { event ->
            val vd = activeWindows[windowView]
            if (vd != null) touchInjector.inject(event, vd.display.displayId)
        }

        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        var w = 800
        var h = 1200
        
        if (screenWidth > screenHeight) {
            h = (screenHeight * 0.85).toInt()
            w = (h * 0.6).toInt()
        }

        val params = WindowManager.LayoutParams(
            w, h, 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        windowView.setWindowParams(params)
        windowManager.addView(windowView, params)
    }

    private fun restackBubbles() {
        val bubbles = activeWindows.keys.filter { it.isMinimized }
        val halfWidth = displayMetrics.widthPixels / 2
        val halfHeight = displayMetrics.heightPixels / 2
        val targetX = halfWidth - 100
        val startY = -halfHeight + 200

        bubbles.forEachIndexed { index, window ->
            val targetY = startY + (index * 160)
            window.updateBubblePosition(targetX, targetY)
        }
    }

    private fun mapWindowToTask(window: FloatingWindowView, packageName: String) {
        try {
            val tasks = activityManager.getRunningTasks(50)
            val match = tasks.find { it.baseActivity?.packageName == packageName }
            if (match != null) taskToWindowMap[match.id] = window
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun closeFloatingWindow(window: FloatingWindowView) {
        if (activeWindows.containsKey(window)) {
            activeWindows[window]?.release()
            activeWindows.remove(window)
            try { windowManager.removeView(window) } catch (e: Exception) { }
            val taskId = taskToWindowMap.entries.find { it.value == window }?.key
            if (taskId != null) taskToWindowMap.remove(taskId)
            restackBubbles()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { ActivityTaskManager.getService().unregisterTaskStackListener(taskStackListener) } catch (e: Exception) {}
        if (edgeHandleView != null) windowManager.removeView(edgeHandleView)
        activeWindows.forEach { (view, vd) -> try { windowManager.removeView(view) } catch(e:Exception){}; vd.release() }
    }
}
