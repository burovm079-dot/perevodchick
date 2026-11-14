package com.qazrus.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var captureManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val title = TextView(this).apply {
            text = "QazRus Overlay"
            textSize = 26f
            gravity = Gravity.CENTER
        }

        val description = TextView(this).apply {
            text = "Казахско-русский переводчик для текста на экране.\n\nСейчас добавлен безопасный тест системного разрешения на захват экрана. Распознавание казахского текста и перевод — следующие этапы."
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

        val button = Button(this).apply {
            text = "Проверить захват экрана"
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
            addView(title, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(description, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        setContentView(layout)
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            startForegroundService(serviceIntent)
            status.text = "Разрешение получено. Проверяю получение кадра…"
        } else {
            status.text = "Захват экрана не разрешён."
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 4101
    }
}
