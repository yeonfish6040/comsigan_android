package com.yftech.comsigan.data

import java.net.URLEncoder

/**
 * 컴시간 열람 페이지(/st)에 박혀 있는 호출 상수. 컴시간이 갱신되면 값이 바뀌므로
 * 페이지에서 직접 뽑아 쓰고, 실패하면 마지막으로 알려진 값으로 동작한다.
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
    /** 자료 요청 경로. 예: "36179" */
    val route: String,
    /** 학교 검색 접두어. 예: "17384l" */
    val searchPrefix: String,
    /** 시간표 요청 접두어. 예: "73629_" */
    val dataPrefix: String,
) {
    /** 학교명 검색 URL. 질의는 EUC-KR로 퍼센트 인코딩해야 결과가 나온다. */
    fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "EUC-KR")
        return "$HOST/$route?$searchPrefix$encoded"
    }

    /** 시간표 URL. `<dataPrefix><학교코드>_0_<주차>`를 base64로 넘긴다. */
    fun timetableUrl(schoolCode: Int, week: Int = ComciWeek.FIRST.r): String {
        val payload = "$dataPrefix${schoolCode}_0_$week"
        return "$HOST/$route?${base64(payload.toByteArray(Charsets.UTF_8))}"
    }

    companion object {
        const val HOST = "http://comci.net:4082"
        val FALLBACK = ComciEndpoint(route = "36179", searchPrefix = "17384l", dataPrefix = "73629_")

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
        private val SEARCH_PREFIX = Regex("""\?(\d+l)'""")
        private val DATA_PREFIX = Regex("""sc_data\('(\d+_)'""")

        /** 열람 페이지를 긁어 상수를 갱신한다. */
        fun discover(): ComciEndpoint {
            val html = Http.getString("$HOST/st", charset = "EUC-KR")
            return ComciEndpoint(
                route = ROUTE.find(html)?.groupValues?.get(1) ?: FALLBACK.route,
                searchPrefix = SEARCH_PREFIX.find(html)?.groupValues?.get(1) ?: FALLBACK.searchPrefix,
                dataPrefix = DATA_PREFIX.find(html)?.groupValues?.get(1) ?: FALLBACK.dataPrefix,
            )
        }
    }
}
