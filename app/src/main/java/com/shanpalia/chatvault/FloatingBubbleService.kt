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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val badge = ImageView(this).apply {
            setImageResource(R.drawable.chat_vault_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(5, 5, 5, 5)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(2, Color.rgb(22, 168, 135))
            }
            elevation = 12f
            contentDescription = "Chat Vault floating shortcut"
        }
        bubbleParams = WindowManager.LayoutParams(
            56, 56, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 12; y = 260 }

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
                        if (kotlin.math.abs(dx) > 7 || kotlin.math.abs(dy) > 7) moved = true
                        bubbleParams.x = (startX + dx).coerceAtLeast(0)
                        bubbleParams.y = (startY + dy).coerceAtLeast(80)
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
            setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
                setStroke(2, Color.rgb(22, 168, 135))
            }
            elevation = 16f
        }
        val brand = TextView(this).apply {
            text = "CHAT VAULT"
            textSize = 12f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 168, 135))
            gravity = Gravity.CENTER
            setPadding(12, 9, 12, 9)
        }
        panel.addView(brand, LinearLayout.LayoutParams(184, -2))

        fun action(label: String, callback: () -> Unit) {
            val item = TextView(this).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(23, 53, 47))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 12, 14, 12)
                isClickable = true
                setOnClickListener { removeMenu(); callback() }
            }
            panel.addView(item, LinearLayout.LayoutParams(184, -2))
        }
        action("▣  Open Chat Vault") { openVault() }
        action("×  Turn off bubble") { stopSelf() }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenWidth = resources.displayMetrics.widthPixels
            x = (bubbleParams.x + 64).coerceAtMost((screenWidth - 205).coerceAtLeast(0))
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