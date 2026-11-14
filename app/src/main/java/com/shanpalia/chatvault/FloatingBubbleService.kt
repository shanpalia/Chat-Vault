package com.shanpalia.chatvault

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var menu: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val badge = ImageView(this).apply {
            setImageResource(R.drawable.chat_vault_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
            background = null
            elevation = dp(8).toFloat()
            contentDescription = "Chat Vault floating menu"
        }

        bubbleParams = WindowManager.LayoutParams(
            dp(62), dp(62), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(260) }

        badge.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var downX = 0f; private var downY = 0f; private var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = bubbleParams.x; startY = bubbleParams.y
                        downX = event.rawX; downY = event.rawY; moved = false; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(7) || kotlin.math.abs(dy) > dp(7)) moved = true
                        bubbleParams.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - bubbleParams.width).coerceAtLeast(0))
                        bubbleParams.y = (startY + dy).coerceAtLeast(dp(80))
                        runCatching { windowManager?.updateViewLayout(badge, bubbleParams) }; return true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) toggleMenu(); return true }
                }
                return false
            }
        })
        bubble = badge
        runCatching { windowManager?.addView(badge, bubbleParams) }.onFailure { stopSelf() }
    }

    private fun toggleMenu() {
        if (menu != null) { removeMenu(); return }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.rgb(22, 168, 135))
            }
            elevation = dp(16).toFloat()
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(10))
        }
        heading.addView(ImageView(this).apply { setImageResource(R.drawable.chat_vault_logo) },
            LinearLayout.LayoutParams(dp(32), dp(32)))
        heading.addView(TextView(this).apply {
            text = "CHAT VAULT"; textSize = 14f; letterSpacing = .06f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(22, 168, 135))
            setPadding(dp(9), 0, 0, 0)
        })
        panel.addView(heading)

        fun action(icon: String, label: String, callback: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(13), dp(12), dp(13))
                isClickable = true
                background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
                setOnClickListener { removeMenu(); callback() }
            }
            row.addView(TextView(this).apply {
                text = icon; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(22, 168, 135)); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(34), -2))
            row.addView(TextView(this).apply {
                text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(23, 53, 47))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(row, LinearLayout.LayoutParams(dp(218), -2))
        }
        action("⌂", "Open App") { openVault(false) }
        action("⬇", "Backup") { openVault(true) }
        action("×", "Close") { /* Close only the menu; keep the floating icon active. */ }
        action("⏻", "Exit Bubble") { stopSelf() }

        val width = dp(240)
        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val right = screenWidth - (bubbleParams.x + bubbleParams.width)
            x = if (right >= width) bubbleParams.x + bubbleParams.width + dp(6)
                else (bubbleParams.x - width - dp(6)).coerceAtLeast(0)
            y = bubbleParams.y
        }
        menu = panel
        runCatching { windowManager?.addView(panel, params) }.onFailure { menu = null }
    }

    private fun openVault(backup: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (backup) putExtra("open_backup_picker", true)
        }
        startActivity(intent)
    }

    private fun removeMenu() {
        menu?.let { runCatching { windowManager?.removeView(it) } }
        menu = null
    }

    override fun onDestroy() {
        removeMenu()
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}