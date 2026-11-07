package com.yftech.comsigan.data

import java.net.URLEncoder
import org.json.JSONArray

/**
 * 컴시간 열람 페이지(/st)에 박혀 있는 호출 상수. 컴시간이 갱신되면 값이 바뀌므로
 * 페이지에서 직접 뽑아 요청 형식과 응답 스키마를 함께 갱신한다.
 */
/**
 * 열람할 주차. 요청 끝자리 `r`이 이 값이고, 고를 수 있는 주차 목록은
 * 응답의 `일자자료`가 알려준다(열람 페이지 일자설정하기와 동일).
 */
data class ComciWeek(
    val r: Int,
    /** 서버가 준 기간 표기. 예: "26-08-17 ~ 26-08-22" */
    val label: String = "",
) {
    companion object {
        /** 주차를 모르는 상태에서 처음 부를 때 쓰는 값(열람 페이지도 r=1로 시작한다). */
        val FIRST = ComciWeek(1)
    }
}

data class ComciEndpoint(
    /** 자료 요청 경로. 예: "952563" */
    val route: String,
    /** 학교 검색 접두어. 예: "21326l" */
    val searchPrefix: String,
    /** 시간표 요청 접두어. 예: "10893_" */
    val dataPrefix: String,
    /** 열람 페이지가 공개하는 시간표 응답 복호화 키. */
    val decryptionKey: String = DEFAULT_DECRYPTION_KEY,
    /** 변하는 서버 필드 이름 → 앱 파서가 쓰는 고정 이름. 캐시에는 고정 이름으로 저장한다. */
    val dataKeys: Map<String, String> = DEFAULT_DATA_KEYS,
    val responseFormat: String = "enc2",
    val encodedSearch: Boolean = false,
) {
    /** enc5 검색은 UTF-8 퍼센트 인코딩한 검색어를 요청 전체와 함께 base64로 감싼다. */
    fun searchUrl(query: String): String {
        val token = if (encodedSearch) {
            encodeURIComponent(base64((searchPrefix + encodeURIComponent(query) + "_" + responseFormat).toByteArray(Charsets.UTF_8)))
        } else {
            searchPrefix + URLEncoder.encode(query, "EUC-KR")
        }
        return "$HOST/$route?$token"
    }

    fun timetableUrl(schoolCode: Int, week: Int = ComciWeek.FIRST.r): String {
        val payload = "$dataPrefix${schoolCode}_0_${week}_$responseFormat"
        return "$HOST/$route?${base64(payload.toByteArray(Charsets.UTF_8))}"
    }

    companion object {
        const val HOST = "http://comci.net:4082"

        val DEFAULT_DATA_KEYS = mapOf(
            "자료147" to "자료139", "자료481" to "자료121", "자료446" to "자료148",
            "자료492" to "자료178", "자료245" to "자료151", "자료244" to "자료100",
        )
        const val DEFAULT_DECRYPTION_KEY = "6f428ea903c751d0b82f649d1e703ac5d4892fb601e8c347a5d09b2cf316784e"

        val FALLBACK = ComciEndpoint(
            route = "952563", searchPrefix = "21326l", dataPrefix = "10893_",
            decryptionKey = "08061397ca3616e62f72c905b1ca6a0dda0584b7e357507831f0175668e165bd",
            dataKeys = mapOf(
                "자료147" to "자료112", "자료481" to "자료199", "자료446" to "자료102",
                "자료492" to "자료142", "자료245" to "자료145", "자료244" to "자료162",
            ),
            responseFormat = "enc5", encodedSearch = true,
        )

        private fun encodeURIComponent(value: String): String = buildString {
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val code = byte.toInt() and 255
                if (code in 65..90 || code in 97..122 || code in 48..57 || code.toChar() in "-_.!~*'()") {
                    append(code.toChar())
                } else {
                    append("%%%02X".format(code))
                }
            }
        }

        private const val BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        /** android.util.Base64는 JVM 테스트에서 못 쓰므로 직접 인코딩한다. */
        fun base64(bytes: ByteArray): String {
            val out = StringBuilder()
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
                val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
                out.append(BASE64_ALPHABET[b0 shr 2])
                out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
                out.append(if (i + 1 < bytes.size) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
                out.append(if (i + 2 < bytes.size) BASE64_ALPHABET[b2 and 0x3F] else '=')
                i += 3
            }
            return out.toString()
        }

        private val ROUTE = Regex("""\./(\d+)\?""")
        private val SEARCH_PREFIX = Regex("""(?:\?|btoa\(['"])(\d+l)['"]""")
        private val DATA_PREFIX = Regex("""sc_data\('(\d+_)'""")

        private val KEY = Regex("""var\s+key\s*=\s*CryptoJS\.enc\.Hex\.parse\(['"]([0-9a-f]{64})['"]\)""")
        private val PACKED_SCRIPT = Regex("""\}\)\((\[\[.*?\]\]),\s*(\d+)\);""", RegexOption.DOT_MATCHES_ALL)

        /** 중첩된 hour-obfuscated 문자열을 복원한다. JavaScript는 실행하지 않는다. */
        fun decodedPage(html: String): String {
            var script = html
            repeat(8) {
                val matches = PACKED_SCRIPT.findAll(script).toList()
                if (matches.isEmpty()) return script
                for (match in matches.asReversed()) {
                    val blocks = JSONArray(match.groupValues[1])
                    val key = match.groupValues[2].toInt()
                    val decoded = buildString {
                        for (index in 0 until blocks.length()) {
                            val block = blocks.getJSONArray(index)
                            val text = block.getString(1)
                            if (block.getInt(0) != 0) {
                                append(text)
                            } else {
                                require(text.length % 4 == 0) { "Invalid timetable script" }
                                for (offset in text.indices step 4) {
                                    val code = text.substring(offset, offset + 4).toInt(16)
                                    append((code xor ((key + offset / 4 * 13) and 255)).toChar())
                                }
                            }
                        }
                    }
                    script = script.replaceRange(match.range, decoded)
                }
                require(script.length <= 4_000_000) { "Timetable script too large" }
            }
            throw ComciException("시간표 서버의 요청 형식을 확인하지 못했습니다.")
        }

        private fun decryptionKey(script: String): String {
            KEY.find(script)?.groupValues?.get(1)?.let { return it }
            // 공개 페이지의 조각 재배열만 해석한다. 임의 표현식은 평가하지 않는다.
            val pattern = Regex("""CryptoJS\.enc\.Hex\.parse\(\(function\(a\)\{return\s+((?:a\[\d+\]\+)*a\[\d+\]);\}\)\((\[[^]]+\])\)\)""")
            val match = pattern.find(script) ?: throw ComciException("시간표 서버의 요청 형식을 확인하지 못했습니다.")
            val pieces = JSONArray(match.groupValues[2])
            val order = Regex("""a\[(\d+)\]""").findAll(match.groupValues[1])
            val key = order.joinToString("") { pieces.getString(it.groupValues[1].toInt()) }
            require(Regex("[0-9a-f]{64}").matches(key)) { "Invalid timetable key" }
            return key
        }

        fun fromPage(html: String): ComciEndpoint {
            val script = decodedPage(html)
            fun required(pattern: Regex): String = pattern.find(script)?.groupValues?.get(1)
                ?: throw ComciException("시간표 서버의 요청 형식을 확인하지 못했습니다.")
            return ComciEndpoint(
                route = required(ROUTE),
                searchPrefix = required(SEARCH_PREFIX),
                dataPrefix = required(DATA_PREFIX),
                decryptionKey = decryptionKey(script),
                responseFormat = Regex("""_(enc\d+)['"]""").find(script)?.groupValues?.get(1) ?: "enc2",
                encodedSearch = Regex("""btoa\(['"]\d+l['"]\s*\+\s*encodeURIComponent\(""").containsMatchIn(script),
                dataKeys = mapOf(
                    "자료147" to required(Regex("""일일자료\s*=\s*Q자료\(자료\.(자료\d+)""")),
                    "자료481" to required(Regex("""원자료\s*=\s*Q자료\(자료\.(자료\d+)""")),
                    "자료446" to required(Regex("""성명\s*=\s*Q성명\(자료\.(자료\d+)""")),
                    "자료492" to required(Regex("""과목명\s*=\s*Q과목명\(자료\.(자료\d+)""")),
                    "자료245" to required(Regex("""var\s+m3\s*=\s*자료\.(자료\d+)""")),
                    "자료244" to required(Regex("""수정일: ['"]\s*\+\s*H시간표\.(자료\d+)""")),
                ),
            )
        }

        /** 열람 페이지를 긁어 상수를 갱신한다. */
        fun discover(): ComciEndpoint = fromPage(Http.getString("$HOST/st", charset = "EUC-KR"))
    }
}
