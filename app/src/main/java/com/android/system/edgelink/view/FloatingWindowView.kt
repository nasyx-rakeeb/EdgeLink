package com.android.system.edgelink.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.android.system.edgelink.R
import com.android.system.edgelink.model.AppInfo

class FloatingWindowView(
    context: Context,
    val appInfo: AppInfo,
    private val onClose: () -> Unit,
    private val onSurfaceReady: (Surface, Int, Int) -> Unit,
    private val onSurfaceDestroyed: () -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onExpandStart: () -> Unit,
    private val onBackPress: () -> Unit,
    private val onMinimizeRequest: (FloatingWindowView) -> Unit,
    private val onMaximize: () -> Unit, // NEW: Signal when we maximize
    private val onFocus: () -> Unit     // NEW: Signal when we are touched
) : FrameLayout(context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var params: WindowManager.LayoutParams? = null
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var resizeRunnable: Runnable? = null

    var isMinimized = false
        private set

    private var savedWidth = 800
    private var savedHeight = 1200
    private var savedX = 0
    private var savedY = 0 
    
    private val accentColor = getSystemAccentColor(context)
    private val minSize = 450
    private val displayMetrics = DisplayMetrics()

    private val header: LinearLayout
    private val surfaceView: SurfaceView
    private val loadingView: FrameLayout
    private val resizingOverlay: FrameLayout
    private val footer: FrameLayout
    private val resizeHandle: ImageView
    private val bubbleIcon: ImageView

    init {
        updateBackground(isMinimized = false, isResizing = false)
        elevation = 20f
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        // HEADER
        header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.TRANSPARENT)
            // Header touch = Focus
            setOnTouchListener { _, event -> 
                if (event.action == MotionEvent.ACTION_DOWN) onFocus()
                false 
            }
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, 80))

        header.addView(ImageView(context).apply { setImageDrawable(appInfo.icon) }, LinearLayout.LayoutParams(50, 50))
        header.addView(TextView(context).apply {
            text = appInfo.label; textSize = 12f; setTextColor(Color.WHITE); setPadding(20, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val iconSize = 70
        header.addView(createIcon(R.drawable.ic_minimize) { 
             onMinimizeRequest(this)
        }, LinearLayout.LayoutParams(iconSize, iconSize))
        header.addView(createIcon(R.drawable.ic_fullscreen) { onExpandStart() }, LinearLayout.LayoutParams(iconSize, iconSize))
        header.addView(createIcon(R.drawable.ic_close) { onClose() }, LinearLayout.LayoutParams(iconSize, iconSize))

        // CONTENT
        val screenContainer = FrameLayout(context)
        val screenParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { topMargin = 80; bottomMargin = 80 }
        addView(screenContainer, screenParams)

        surfaceView = SurfaceView(context)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { onSurfaceReady(holder.surface, width, height - 160) }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }
            override fun surfaceDestroyed(holder: SurfaceHolder) { onSurfaceDestroyed() }
        })
        surfaceView.isFocusable = false; surfaceView.isFocusableInTouchMode = false
        screenContainer.addView(surfaceView)

        loadingView = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#E61E1E1E")); alpha = 1f }
        val spinner = ProgressBar(context).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(accentColor) }
        loadingView.addView(spinner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        screenContainer.addView(loadingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        resizingOverlay = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#E61E1E1E")); visibility = View.GONE; alpha = 0.95f }
        val overlayContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER) }
        overlayContent.addView(ImageView(context).apply { setImageResource(R.drawable.ic_resize_handle); setColorFilter(accentColor); layoutParams = LinearLayout.LayoutParams(80, 80) })
        overlayContent.addView(TextView(context).apply { text = "Resizing..."; textSize = 16f; setTextColor(accentColor); gravity = Gravity.CENTER; setPadding(20, 20, 20, 20); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT) })
        resizingOverlay.addView(overlayContent)
        screenContainer.addView(resizingOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // FOOTER
        footer = FrameLayout(context).apply { 
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event -> 
                if (event.action == MotionEvent.ACTION_DOWN) onFocus()
                true 
            }
        }
        val footerParams = LayoutParams(LayoutParams.MATCH_PARENT, 80).apply { gravity = Gravity.BOTTOM }
        addView(footer, footerParams)

        val backBtn = TextView(context).apply {
            text = "Back"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_outline); isClickable = true
            setOnClickListener { 
                onFocus()
                onBackPress() 
            }
        }
        footer.addView(backBtn, FrameLayout.LayoutParams(160, 60, Gravity.CENTER))

        resizeHandle = ImageView(context).apply { setImageResource(R.drawable.ic_resize_handle); setColorFilter(Color.GRAY); alpha = 0.5f }
        footer.addView(resizeHandle, FrameLayout.LayoutParams(50, 50, Gravity.BOTTOM or Gravity.END).apply { setMargins(0,0,10,10) })

        bubbleIcon = ImageView(context).apply { setImageDrawable(appInfo.icon); visibility = View.GONE; setOnClickListener { maximize() } }
        addView(bubbleIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setupDragListener(header)
        setupDragListener(bubbleIcon)
        setupResizeListener(resizeHandle)
    }

    fun setTouchCallback(callback: (MotionEvent) -> Unit) {
        surfaceView.setOnTouchListener { _, event -> 
            if (event.action == MotionEvent.ACTION_DOWN) onFocus()
            callback(event)
            true 
        }
    }

    private fun getSystemAccentColor(context: Context): Int {
        val typedValue = TypedValue(); context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true); return typedValue.data
    }
    fun hideLoading() { loadingView.animate().alpha(0f).setDuration(300).withEndAction { loadingView.visibility = View.GONE }.start() }
    private fun createIcon(res: Int, action: () -> Unit): ImageView { return ImageView(context).apply { setImageResource(res); setColorFilter(Color.WHITE); setPadding(12, 12, 12, 12); setOnClickListener { action() } } }
    fun setWindowParams(p: WindowManager.LayoutParams) { this.params = p }

    fun updateBubblePosition(x: Int, y: Int) {
        val p = params ?: return
        if (isMinimized) {
            p.x = x; p.y = y
            windowManager.updateViewLayout(this, p)
        }
    }

    fun applyMinimize(targetX: Int, targetY: Int) {
        val p = params ?: return
        isMinimized = true
        savedWidth = p.width; savedHeight = p.height; savedX = p.x; savedY = p.y
        header.visibility = View.INVISIBLE; footer.visibility = View.GONE; resizeHandle.visibility = View.GONE
        bubbleIcon.visibility = View.VISIBLE
        updateBackground(true, false)
        p.width = 140; p.height = 140; p.x = targetX; p.y = targetY
        windowManager.updateViewLayout(this, p)
    }

    fun maximize() {
        val p = params ?: return
        if (!isMinimized) return
        isMinimized = false
        bubbleIcon.visibility = View.GONE
        header.visibility = View.VISIBLE; footer.visibility = View.VISIBLE; resizeHandle.visibility = View.VISIBLE
        updateBackground(false, false)
        p.width = savedWidth; p.height = savedHeight; p.x = savedX; p.y = savedY
        windowManager.updateViewLayout(this, p)
        onResize(p.width, p.height - 160)
        
        // Signal Service to re-stack bubbles
        onMaximize()
        onFocus()
    }

    private fun updateBackground(isMinimized: Boolean, isResizing: Boolean) {
        val bg = GradientDrawable()
        if (isMinimized) { bg.shape = GradientDrawable.OVAL; bg.setColor(Color.parseColor("#E61E1E1E")); bg.setStroke(2, Color.WHITE) } 
        else { bg.shape = GradientDrawable.RECTANGLE; bg.setColor(Color.parseColor("#E61E1E1E")); bg.cornerRadius = 30f; if (isResizing) bg.setStroke(6, accentColor) else bg.setStroke(2, Color.parseColor("#44FFFFFF")) }
        background = bg
    }
    private fun setupDragListener(view: View) {
        view.setOnTouchListener(object : OnTouchListener {
            var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f; var isClick = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { 
                        onFocus()
                        initialX = p.x; initialY = p.y; initialTouchX = event.rawX; initialTouchY = event.rawY; isClick = true; return true 
                    }
                    MotionEvent.ACTION_MOVE -> { if (Math.abs(event.rawX - initialTouchX) > 10) isClick = false; p.x = initialX + (event.rawX - initialTouchX).toInt(); p.y = initialY + (event.rawY - initialTouchY).toInt(); windowManager.updateViewLayout(this@FloatingWindowView, p); return true }
                    MotionEvent.ACTION_UP -> { if (isClick && v == bubbleIcon) v.performClick(); return true }
                }
                return false
            }
        })
    }
    private fun setupResizeListener(view: View) {
        view.setOnTouchListener(object : OnTouchListener {
            var initialW = 0; var initialH = 0; var initialTX = 0f; var initialTY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { 
                        onFocus()
                        initialW = p.width; initialH = p.height; initialTX = event.rawX; initialTY = event.rawY; resizingOverlay.visibility = View.VISIBLE; updateBackground(isMinimized = false, isResizing = true); return true 
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.width = (initialW + (event.rawX - initialTX).toInt()).coerceIn(minSize, displayMetrics.widthPixels - 50)
                        p.height = (initialH + (event.rawY - initialTY).toInt()).coerceIn(minSize, displayMetrics.heightPixels - 50)
                        windowManager.updateViewLayout(this@FloatingWindowView, p)
                        resizeRunnable?.let { resizeHandler.removeCallbacks(it) }
                        resizeRunnable = Runnable { onResize(p.width, p.height - 160) }
                        resizeHandler.postDelayed(resizeRunnable!!, 150)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { resizingOverlay.visibility = View.GONE; updateBackground(isMinimized = false, isResizing = false); resizeRunnable?.let { resizeHandler.removeCallbacks(it) }; onResize(p.width, p.height - 160); return true }
                }
                return false
            }
        })
    }
}
