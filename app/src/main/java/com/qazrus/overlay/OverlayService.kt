package com.qazrus.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Displays the latest OCR result in a user-authorized, dismissible system overlay. */
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        if (text.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        removePanel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 24, 29, 38))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(180, 150, 170, 195))
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(this).apply {
            this.text = "QazRus · распознанный текст"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val close = Button(this).apply {
            this.text = "×"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { stopSelf() }
        }
        header.addView(label, LinearLayout.LayoutParams(0, dp(40), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(40)))
        val body = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 6
            setPadding(0, dp(4), 0, dp(4))
        }
        container.addView(header)
        container.addView(body)
        panel = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
        }
        try {
            windowManager?.addView(container, params)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removePanel() {
        val current = panel ?: return
        try {
            windowManager?.removeView(current)
        } catch (_: Exception) {
        }
        panel = null
    }

    override fun onDestroy() {
        removePanel()
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TEXT = "qazrus.overlay.TEXT"
    }
}
