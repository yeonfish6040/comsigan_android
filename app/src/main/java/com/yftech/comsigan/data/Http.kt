package com.yftech.comsigan.data

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 컴시간은 평문 HTTP + 응답에 NUL이 섞여 있어서 바이트로 받아 직접 정리한다. */
object Http {
    private const val TIMEOUT_MS = 15_000

    fun getBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw ComciException("서버 응답 오류 ($status)")
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { it.copyTo(buffer) }
            return buffer.toByteArray()
        } catch (error: IOException) {
            val endedBeforeResponse = generateSequence<Throwable>(error) { it.cause }.take(16).any { cause ->
                val message = cause.message.orEmpty().lowercase()
                cause is EOFException || "unexpected end of stream" in message || "unexpected end of file" in message
            }
            val message = if (endedBeforeResponse) {
                "시간표 서버가 응답 전에 연결을 종료했습니다. 잠시 후 다시 시도해 주세요."
            } else {
                "시간표 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
            }
            throw ComciException(message, error)
        } finally {
            connection.disconnect()
        }
    }

    fun getString(url: String, charset: String = "UTF-8"): String =
        String(getBytes(url), charset(charset))

    /** NUL을 걷어내고 마지막 '}' 뒤의 잡음을 잘라낸다(열람 페이지도 같은 처리를 한다). */
    fun sanitizedJson(bytes: ByteArray): String {
        val text = String(bytes.filter { it.toInt() != 0 }.toByteArray(), Charsets.UTF_8)
        val end = text.lastIndexOf('}')
        return if (end >= 0) text.substring(0, end + 1) else text
    }
}
