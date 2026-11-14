package com.qazrus.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.nio.ByteBuffer

/**
 * Captures one user-authorized screen frame and runs Kazakh/Russian OCR locally.
 * It deliberately stops after one frame in this prototype.
 */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var captured = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Подготовка захвата экрана…"))

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (resultData == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        worker = HandlerThread("qazrus-screen-capture").also { it.start() }
        val handler = Handler(worker!!.looper)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (captured) {
                image.close()
                return@setOnImageAvailableListener
            }
            captured = true
            try {
                updateNotification("Кадр получен. Загружаю OCR-языки и распознаю текст…")
                val bitmap = image.toBitmap()
                image.close()
                val recognized = KazakhOcr(this).recognize(bitmap)
                bitmap.recycle()
                val summary = if (recognized.isBlank()) {
                    "Текст не найден на кадре."
                } else {
                    "OCR: " + recognized.replace("\\s+".toRegex(), " ").take(180)
                }
                updateNotification(summary)
            } catch (error: Exception) {
                updateNotification("Ошибка OCR: " + (error.message ?: "неизвестная ошибка").take(100))
            } finally {
                stopCapture()
                stopSelf()
            }
        }, handler)

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
                stopSelf()
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "QazRusOverlayCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        if (virtualDisplay == null) {
            updateNotification("Не удалось создать виртуальный экран.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val pixels = IntArray(paddedWidth * height)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * rowStride + x * pixelStride
                val red = bytes[offset].toInt() and 0xff
                val green = bytes[offset + 1].toInt() and 0xff
                val blue = bytes[offset + 2].toInt() and 0xff
                val alpha = bytes[offset + 3].toInt() and 0xff
                pixels[y * paddedWidth + x] = Color.argb(alpha, red, green, blue)
            }
        }
        return Bitmap.createBitmap(pixels, 0, paddedWidth, paddedWidth, height, Bitmap.Config.ARGB_8888)
            .let { if (it.width == width) it else Bitmap.createBitmap(it, 0, 0, width, height).also { cropped -> it.recycle() } }
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        worker?.quitSafely()
        worker = null
    }

    private fun buildNotification(message: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Захват экрана QazRus",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("QazRus Overlay")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("QazRus Overlay")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        }
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "qazrus.extra.RESULT_CODE"
        const val EXTRA_DATA = "qazrus.extra.DATA"
        private const val CHANNEL_ID = "qazrus_capture"
        private const val NOTIFICATION_ID = 4102
    }
}
