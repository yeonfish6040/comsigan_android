package com.yftech.comsigan

import com.yftech.comsigan.data.ComciDocument
import com.yftech.comsigan.data.ComciEndpoint
import com.yftech.comsigan.data.ComciResponse
import com.yftech.comsigan.data.ComciService
import com.yftech.comsigan.data.Http
import com.yftech.comsigan.data.School
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제 컴시간 서버를 호출하는 확인용 테스트(네트워크 필요).
 * 상수 탐색 → EUC-KR 학교 검색 → 임의 학교 시간표 파싱까지 한 번에 본다.
 */
class ComciNetworkTest {

    @Test
    fun base64MatchesKnownToken() {
        // 열람 페이지가 만들어내던 토큰과 같은지 확인: 73629_29175_0_1
        assertEquals(
            "NzM2MjlfMjkxNzVfMF8x",
            ComciEndpoint.base64("73629_29175_0_1".toByteArray()),
        )
    }

    @Test
    fun searchAndFetch() {
        val endpoint = ComciEndpoint.discover()
        println("endpoint = $endpoint")
        assertTrue(endpoint.route.isNotEmpty())

        val schools = search(endpoint, "디지털미디어")
        println("검색 결과 = $schools")
        assertTrue("검색 결과가 없음", schools.isNotEmpty())

        val school = schools.first()
        val json = ComciResponse.timetableJson(Http.getBytes(endpoint.timetableUrl(school.code)), endpoint)
        val document = ComciDocument(json)
        val timetable = document.timetable(1, 1)
        val monday = timetable.days[0].filter { !it.isEmpty }.map { it.displaySubject }
        println("${school.name}(${school.code}) 1-1 월요일 = $monday")

        assertTrue("월요일 수업이 비어 있음", monday.isNotEmpty())
        assertTrue("학급수를 못 읽음", document.classCount(1) > 0)
    }

    private fun search(endpoint: ComciEndpoint, query: String): List<School> {
        return ComciService.parseSchools(Http.sanitizedJson(Http.getBytes(endpoint.searchUrl(query))))
    }
}
