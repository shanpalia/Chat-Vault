package com.shanpalia.chatvault

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

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

        // Floating launcher: the exact existing app logo only, with no text or added frame.
        val badge = ImageView(this).apply {
            setImageResource(R.drawable.chat_vault_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Chat Vault"
            background = null
        }
        bubbleParams = WindowManager.LayoutParams(
            dp(62), dp(62), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                        startX = bubbleParams.x
                        startY = bubbleParams.y
                        downX = event.rawX
                        downY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(7) || kotlin.math.abs(dy) > dp(7)) moved = true
                        bubbleParams.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - bubbleParams.width).coerceAtLeast(0))
                        bubbleParams.y = (startY + dy).coerceAtLeast(dp(80))
                        runCatching { windowManager?.updateViewLayout(badge, bubbleParams) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleMenu()
                        return true
                    }
                }
                return false
            }
        })
        bubble = badge
        runCatching { windowManager?.addView(badge, bubbleParams) }.onFailure { stopSelf() }
    }

    private fun toggleMenu() {
        if (menu != null) {
            removeMenu()
            return
        }

        // Compact vertical icon-only action frame, positioned to the right of the bubble.
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(2), Color.rgb(22, 168, 135))
            }
            elevation = dp(18).toFloat()
        }

        fun addAction(iconRes: Int, description: String, callback: () -> Unit) {
            val tile = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(232, 248, 244))
                    cornerRadius = dp(15).toFloat()
                }
                isClickable = true
                isFocusable = true
                contentDescription = description
                setOnClickListener {
                    removeMenu()
                    callback()
                }
            }
            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(Color.rgb(22, 168, 135))
            }
            tile.addView(icon, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            frame.addView(tile, LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }

        addAction(R.drawable.ic_float_open, "Open app") { openVault(false) }
        addAction(R.drawable.ic_float_backup, "Backup") { openVault(true) }
        addAction(R.drawable.ic_float_close, "Close menu") { /* Bubble remains visible and active. */ }
        addAction(R.drawable.ic_float_exit, "Exit bubble") { stopSelf() }

        val frameWidth = dp(78)
        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            frameWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val rightSpace = screenWidth - (bubbleParams.x + bubbleParams.width)
            x = if (rightSpace >= frameWidth) bubbleParams.x + bubbleParams.width + dp(8)
                else (bubbleParams.x - frameWidth - dp(8)).coerceAtLeast(0)
            y = bubbleParams.y.coerceAtLeast(dp(80))
        }
        menu = frame
        runCatching { windowManager?.addView(frame, params) }.onFailure { menu = null }
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