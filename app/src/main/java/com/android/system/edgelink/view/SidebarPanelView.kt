package com.android.system.edgelink.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.android.system.edgelink.R
import com.android.system.edgelink.model.AppInfo
import com.android.system.edgelink.util.AppLauncher
import com.android.system.edgelink.util.AppPrefs

class SidebarPanelView(
    context: Context,
    private val isLeft: Boolean,
    private val onCloseRequest: () -> Unit,
    private val onAppSelected: (AppInfo) -> Unit
) : FrameLayout(context) {

    private val appLauncher = AppLauncher(context)
    private val apps = mutableListOf<AppInfo>()
    private val prefs = AppPrefs(context)
    private val gridView: GridView
    private val loadingView: ProgressBar
    
    private val panelContainer: LinearLayout
    private val panelWidth = 500

    init {
        setBackgroundColor(Color.parseColor("#00000000")) 
        setOnClickListener { closePanel() }

        panelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable()
            bg.setColor(context.getColor(R.color.edgelink_bg))
            bg.cornerRadius = 40f
            bg.alpha = prefs.getPanelOpacity()
            background = bg
            elevation = 40f
            setPadding(30, 20, 30, 20)
            isClickable = true
            setOnClickListener { /* Consume clicks */ }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val calculatedHeight = (metrics.heightPixels * (prefs.getPanelHeightPercent() / 100f)).toInt()

        val panelParams = LayoutParams(panelWidth, calculatedHeight).apply {
            gravity = (if (isLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            setMargins(20, 0, 20, 0)
        }
        addView(panelContainer, panelParams)

        loadingView = ProgressBar(context)
        panelContainer.addView(loadingView, LinearLayout.LayoutParams(100, 100).apply { gravity = Gravity.CENTER; topMargin = 50 })

        gridView = GridView(context).apply {
            numColumns = 2
            verticalSpacing = 50
            horizontalSpacing = 20
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        panelContainer.addView(gridView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))

        Thread {
            val allApps = appLauncher.getAllLaunchableApps()
            val pinnedPackages = prefs.getPinnedApps()

            val pinnedList = allApps.filter { pinnedPackages.contains(it.packageName) }.sortedBy { it.label }
            val unpinnedList = allApps.filter { !pinnedPackages.contains(it.packageName) }.sortedBy { it.label }
            val sortedApps = pinnedList + unpinnedList

            post {
                loadingView.visibility = View.GONE
                gridView.visibility = View.VISIBLE
                apps.addAll(sortedApps)
                gridView.adapter = AppGridAdapter(context, apps, pinnedPackages)
            }
        }.start()

        startEnterAnimation()
    }

    private fun startEnterAnimation() {
        val colorAnim = ObjectAnimator.ofArgb(this, "backgroundColor", Color.TRANSPARENT, Color.parseColor("#40000000"))
        colorAnim.duration = 250

        val startX = if (isLeft) -panelWidth.toFloat() else panelWidth.toFloat()
        panelContainer.translationX = startX
        
        val slideAnim = ObjectAnimator.ofFloat(panelContainer, "translationX", startX, 0f)
        slideAnim.duration = 250
        slideAnim.interpolator = DecelerateInterpolator()

        colorAnim.start()
        slideAnim.start()
    }

    fun closePanel() {
        val colorAnim = ObjectAnimator.ofArgb(this, "backgroundColor", Color.parseColor("#40000000"), Color.TRANSPARENT)
        colorAnim.duration = 200

        val targetX = if (isLeft) -panelWidth.toFloat() else panelWidth.toFloat()
        val slideAnim = ObjectAnimator.ofFloat(panelContainer, "translationX", 0f, targetX)
        slideAnim.duration = 200
        slideAnim.interpolator = DecelerateInterpolator()

        slideAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onCloseRequest()
            }
        })

        colorAnim.start()
        slideAnim.start()
    }

    private inner class AppGridAdapter(
        val ctx: Context, 
        val list: List<AppInfo>,
        val pinnedSet: Set<String>
    ) : BaseAdapter() {
        
        override fun getCount(): Int = list.size
        override fun getItem(position: Int): AppInfo = list[position]
        override fun getItemId(position: Int): Long = position.toLong()
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = getItem(position)
            val isPinned = pinnedSet.contains(app.packageName)

            // 1. Inflate or Reuse View
            val view = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER // This centers children in the container
                setPadding(10, 15, 10, 15)
                
                // Icon Container
                val iconContainer = FrameLayout(ctx)
                iconContainer.id = 100
                iconContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                
                // App Icon
                iconContainer.addView(ImageView(ctx).apply { 
                    layoutParams = FrameLayout.LayoutParams(130, 130)
                    id = 101 
                })
                
                // Pin Badge (Initially hidden)
                val badge = View(ctx).apply {
                    id = 103 // Specific ID for the badge
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(getSystemAccentColor(ctx)); setStroke(2, Color.WHITE) }
                    layoutParams = FrameLayout.LayoutParams(24, 24).apply { gravity = Gravity.TOP or Gravity.END; setMargins(0, 0, 10, 0) }
                    visibility = View.GONE // Default to GONE
                }
                iconContainer.addView(badge)
                
                addView(iconContainer)

                // Label
                addView(TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    textSize = 12f; setTextColor(ctx.getColor(R.color.edgelink_text))
                    gravity = Gravity.CENTER // Force text to center itself
                    textAlignment = View.TEXT_ALIGNMENT_CENTER // Double force for certain Android versions
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 12, 0, 0)
                    id = 102
                })
            }

            // 2. Bind Data
            val iconContainer = view.findViewById<FrameLayout>(100)
            val img = iconContainer.findViewById<ImageView>(101)
            val badge = iconContainer.findViewById<View>(103)
            val txt = view.findViewById<TextView>(102)
            
            img.setImageDrawable(app.icon)
            txt.text = app.label
            
            // 3. FIX: Explicitly Show/Hide badge based on CURRENT item state
            if (isPinned) {
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }

            view.setOnClickListener { 
                closePanel() 
                postDelayed({ onAppSelected(app) }, 50)
            }
            return view
        }
        
        private fun getSystemAccentColor(context: Context): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
            return typedValue.data
        }
    }
}
