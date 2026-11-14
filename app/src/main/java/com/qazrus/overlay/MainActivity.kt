package com.qazrus.overlay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var overlayButton: Button
    private lateinit var captureManager: MediaProjectionManager
    private lateinit var onlineTranslation: CheckBox

    private val ocrReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            status.text = intent?.getStringExtra(ScreenCaptureService.EXTRA_TEXT)
                ?: "Распознавание завершено."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val title = TextView(this).apply {
            text = "QazRus Overlay"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        val description = TextView(this).apply {
            text = "Казахско-русский переводчик для текста на экране.\n\nСейчас доступно: захват одного кадра и локальное OCR казахского/русского текста. Плавающая панель показывает распознанный оригинал; перевод ещё разрабатывается."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply {
            text = "Статус: приложение запущено"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 16)
        }
        overlayButton = Button(this).apply {
            text = "Разрешить окно поверх приложений"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(permissionIntent)
                } else {
                    status.text = "Разрешение на плавающее окно уже выдано."
                }
            }
        }
        onlineTranslation = CheckBox(this).apply {\n            text = "Включить онлайн-перевод казахский → русский (отправка текста в MyMemory)"\n            textSize = 14f\n            setPadding(8, 12, 8, 12)\n        }\n        val captureButton = Button(this).apply {
            text = "Захватить экран и распознать текст"
            setOnClickListener {
                status.text = "Ожидаю разрешение Android…"
                @Suppress("DEPRECATION")
                startActivityForResult(captureManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 32, 24, 32)
            addView(title, matchWrap())
            addView(description, matchWrap())
            addView(overlayButton, matchWrap())
            addView(captureButton, matchWrap())
            addView(status, matchWrap())
        }
        setContentView(layout)
        updateOverlayButton()
        val filter = IntentFilter(ScreenCaptureService.ACTION_OCR_RESULT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(ocrReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(ocrReceiver, filter)
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun updateOverlayButton() {
        overlayButton.text = if (Settings.canDrawOverlays(this)) {
            "Плавающее окно разрешено"
        } else {
            "Разрешить окно поверх приложений"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::overlayButton.isInitialized) updateOverlayButton()
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)\n                putExtra(ScreenCaptureService.EXTRA_ALLOW_ONLINE_TRANSLATION, onlineTranslation.isChecked)
            }
            startForegroundService(serviceIntent)
            status.text = "Разрешение получено. Захватываю экран…"
        } else {
            status.text = "Захват экрана не разрешён."
        }
    }

    override fun onDestroy() {
        unregisterReceiver(ocrReceiver)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CAPTURE = 4101
    }
}
