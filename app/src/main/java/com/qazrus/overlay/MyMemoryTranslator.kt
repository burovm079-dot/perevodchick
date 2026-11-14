package com.qazrus.overlay

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small online translation adapter. The caller must obtain explicit user consent
 * before invoking it because the recognized screen text is sent to MyMemory.
 */
object MyMemoryTranslator {
    fun translate(text: String): String {
        require(text.isNotBlank()) { "Пустой текст для перевода" }
        val uri = Uri.parse("https://api.mymemory.translated.net/get").buildUpon()
            .appendQueryParameter("q", text.take(450))
            .appendQueryParameter("langpair", "kk|ru")
            .build()
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(code in 200..299) { "Сервис перевода ответил HTTP $code" }
            val json = JSONObject(body)
            check(json.optInt("responseStatus", 200) == 200) {
                json.optString("responseDetails", "Сервис отклонил запрос")
            }
            val translated = json.optJSONObject("responseData")
                ?.optString("translatedText").orEmpty().trim()
            check(translated.isNotBlank()) { "Сервис вернул пустой перевод" }
            return translated
        } finally {
            connection.disconnect()
        }
    }
}
