package com.yftech.comsigan.data

import java.security.MessageDigest
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 열람 페이지의 enc1(hex) 및 공개 요청 형식에 지정된 base64 응답. 인증에 성공한 자료만 복호화한다. */
object ComciResponse {
    fun timetableJson(bytes: ByteArray, endpoint: ComciEndpoint): String = normalize(json(bytes, endpoint), endpoint)

    /** 학교 검색도 enc5 봉투를 사용하므로 시간표 스키마 검사와 복호화를 나눈다. */
    fun json(bytes: ByteArray, endpoint: ComciEndpoint): String {
        val text = bytes.toString(Charsets.UTF_8).trimEnd('\u0000')
        if (!text.startsWith("enc")) {
            if (!endpoint.config.allowPlaintext) throw ComciException("시간표 자료를 복호화하지 못했습니다.")
            return Http.sanitizedJson(bytes)
        }
        try {
            val parts = text.split(':')
            val hexFormats = endpoint.config.hexFormats(endpoint.responseFormat)
            val base64Formats = endpoint.config.base64Formats(endpoint.responseFormat)
            require(parts.size == 4 && parts[0] in hexFormats + base64Formats)
            val base64 = parts[0] in base64Formats
            val binary = parts.drop(1).map { value ->
                if (base64) decodeBase64(value) else decodeHex(value)
            }
            val (iv, ciphertext, tag) = binary
            require(iv.size == 16 && tag.size == 32 && ciphertext.isNotEmpty() && ciphertext.size % 16 == 0)
            val key = decodeHex(endpoint.decryptionKey)
            require(key.size == 32)
            val macKey = MessageDigest.getInstance("SHA-256").digest(key)
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(macKey, "HmacSHA256")) }
            val expected = mac.doFinal(parts.take(3).joinToString(":").toByteArray(Charsets.UTF_8))
            require(MessageDigest.isEqual(expected, tag))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }
            return Http.sanitizedJson(cipher.doFinal(ciphertext))
        } catch (error: Exception) {
            throw ComciException("시간표 자료를 복호화하지 못했습니다.")
        }
    }

    private fun normalize(json: String, endpoint: ComciEndpoint): String {
        val root = JSONObject(json)
        // 필드 이름이 다시 바뀌면 빈 시간표를 정상 자료로 캐시하지 않고 요청 형식을 재탐색한다.
        // 예전 고정 이름이 우연히 응답에 있어도, 현재 페이지가 지정한 원본 배열을 확인해야 한다.
        for (canonical in listOf("자료147", "자료481", "자료446", "자료492")) {
            val remote = endpoint.dataKeys[canonical]
            if (remote == null || root.optJSONArray(remote) == null) {
                throw ComciException("시간표 자료를 해석하지 못했습니다.")
            }
        }
        val values = endpoint.dataKeys.mapNotNull { (canonical, remote) ->
            if (root.has(remote)) canonical to root.get(remote) else null
        }
        for ((canonical, value) in values) root.put(canonical, value)
        if (root.optJSONArray("학급수") == null) {
            throw ComciException("시간표 자료를 해석하지 못했습니다.")
        }
        return root.toString()
    }

    private fun decodeHex(value: String): ByteArray {
        require(value.isNotEmpty() && value.length % 2 == 0 && value.all { it in '0'..'9' || it in 'a'..'f' })
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    // java.util.Base64는 API 26부터 제공되므로 minSdk 24에서도 쓸 수 있게 직접 읽는다.
    private fun decodeBase64(value: String): ByteArray {
        require(value.isNotEmpty() && Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?").matches(value))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = ArrayList<Byte>()
        for (offset in value.indices step 4) {
            val a = alphabet.indexOf(value[offset])
            val b = alphabet.indexOf(value[offset + 1])
            val c = alphabet.indexOf(value[offset + 2]).coerceAtLeast(0)
            val d = alphabet.indexOf(value[offset + 3]).coerceAtLeast(0)
            output.add(((a shl 2) or (b shr 4)).toByte())
            if (value[offset + 2] != '=') output.add(((b shl 4) or (c shr 2)).toByte())
            if (value[offset + 3] != '=') output.add(((c shl 6) or d).toByte())
        }
        return output.toByteArray().also { require(ComciEndpoint.base64(it) == value) }
    }
}
