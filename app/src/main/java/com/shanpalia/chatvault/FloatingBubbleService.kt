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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Branded pill is intentionally large enough to be recognized at a glance.
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(16), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(16, 47, 42))
                cornerRadius = dp(30).toFloat()
                setStroke(dp(2), Color.rgb(25, 181, 143))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Chat Vault floating shortcut"
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.chat_vault_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        badge.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)))
        val name = TextView(this).apply {
            text = "Chat Vault"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(9), 0, 0, 0)
        }
        badge.addView(name, LinearLayout.LayoutParams(-2, -2))

        bubbleParams = WindowManager.LayoutParams(
            dp(164), dp(56), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(260) }

        badge.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f
            private var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = bubbleParams.x; startY = bubbleParams.y
                        downX = event.rawX; downY = event.rawY; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(7) || kotlin.math.abs(dy) > dp(7)) moved = true
                        val screenWidth = resources.displayMetrics.widthPixels
                        bubbleParams.x = (startX + dx).coerceIn(0, (screenWidth - bubbleParams.width).coerceAtLeast(0))
                        bubbleParams.y = (startY + dy).coerceAtLeast(dp(80))
                        runCatching { windowManager?.updateViewLayout(badge, bubbleParams) }
                        return true
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
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(2), Color.rgb(22, 168, 135))
            }
            elevation = dp(16).toFloat()
        }
        val brand = TextView(this).apply {
            text = "CHAT VAULT"
            textSize = 13f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 168, 135))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        panel.addView(brand, LinearLayout.LayoutParams(dp(210), -2))

        fun action(label: String, callback: () -> Unit) {
            val item = TextView(this).apply {
                text = label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(23, 53, 47))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                isClickable = true
                setOnClickListener { removeMenu(); callback() }
            }
            panel.addView(item, LinearLayout.LayoutParams(dp(210), -2))
        }
        action("▣   Open Chat Vault") { openVault() }
        action("×   Turn off bubble") { stopSelf() }

        val screenWidth = resources.displayMetrics.widthPixels
        val panelWidth = dp(234)
        val menuParams = WindowManager.LayoutParams(
            panelWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val spaceRight = screenWidth - (bubbleParams.x + bubbleParams.width)
            x = if (spaceRight >= panelWidth) bubbleParams.x + bubbleParams.width + dp(6)
                else (bubbleParams.x - panelWidth - dp(6)).coerceAtLeast(0)
            y = bubbleParams.y
        }
        menu = panel
        runCatching { windowManager?.addView(panel, menuParams) }.onFailure { menu = null }
    }

    private fun openVault() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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