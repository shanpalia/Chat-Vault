package com.shanpalia.chatvault

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var menu: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val badge = TextView(this).apply {
            text = "▣"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(22, 168, 135))
                setStroke(3, Color.WHITE)
            }
            elevation = 14f
            contentDescription = "Chat Vault shortcuts"
        }
        bubbleParams = WindowManager.LayoutParams(
            58, 58, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            setPadding(14, 12, 14, 12)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 28f
                setStroke(2, Color.rgb(22, 168, 135))
            }
            elevation = 18f
        }
        fun action(label: String, callback: () -> Unit) {
            val item = TextView(this).apply {
                text = label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(23, 53, 47))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(18, 15, 18, 15)
                isClickable = true
                setOnClickListener { removeMenu(); callback() }
            }
            panel.addView(item, LinearLayout.LayoutParams(210, -2))
        }
        action("⬆  Backup chats") { openVault(true) }
        action("▣  Open Chat Vault") { openVault(false) }
        action("×  Close") { removeMenu() }

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleParams.x + 64).coerceAtMost(resources.displayMetrics.widthPixels - 230)
            y = bubbleParams.y
        }
        menu = panel
        runCatching { windowManager?.addView(panel, menuParams) }.onFailure { menu = null }
    }

    private fun openVault(importNow: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (importNow) putExtra("OPEN_IMPORT", true)
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
