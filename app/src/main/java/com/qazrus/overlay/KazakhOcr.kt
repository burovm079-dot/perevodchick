package com.qazrus.overlay

import android.content.Context
import android.graphics.Bitmap
import cz.adaptech.tesseract4android.TessBaseAPI
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline OCR for Kazakh and Russian Cyrillic.
 * Language data is downloaded from the official tessdata_fast repository on first use.
 */
class KazakhOcr(context: Context) {
    private val root = File(context.filesDir, "qazrus-tesseract")
    private val dataDir = File(root, "tessdata")

    fun recognize(bitmap: Bitmap): String {
        ensureLanguageData("kaz")
        ensureLanguageData("rus")

        val api = TessBaseAPI()
        try {
            check(api.init(root.absolutePath, "kaz+rus")) {
                "Не удалось запустить Tesseract OCR"
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(bitmap)
            return api.utF8Text?.trim().orEmpty()
        } finally {
            api.end()
        }
    }

    private fun ensureLanguageData(language: String) {
        if (!dataDir.exists()) dataDir.mkdirs()
        val target = File(dataDir, "$language.traineddata")
        if (target.exists() && target.length() > MIN_DATA_BYTES) return

        val url = URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$language.traineddata")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 45000
            requestMethod = "GET"
        }
        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Не удалось загрузить языковой файл $language"
            }
            val temporary = File(dataDir, "$language.traineddata.part")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.length() > MIN_DATA_BYTES) { "Файл языка $language повреждён" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Не удалось сохранить файл языка $language" }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MIN_DATA_BYTES = 100_000L
    }
}
