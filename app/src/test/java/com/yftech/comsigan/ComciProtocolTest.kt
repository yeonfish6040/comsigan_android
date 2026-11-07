package com.yftech.comsigan

import com.yftech.comsigan.data.ComciDocument
import com.yftech.comsigan.data.ComciEndpoint
import com.yftech.comsigan.data.ComciException
import com.yftech.comsigan.data.ComciResponse
import com.yftech.comsigan.data.ComciService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ComciProtocolTest {
    private val legacyEndpoint = ComciEndpoint("937001", "44704l", "74173_")
    private fun fixture(mode: String): ByteArray = javaClass.classLoader!!
        .getResourceAsStream("timetable-$mode.txt")!!.use { it.readBytes() }

    @Test
    fun encryptedFormatsDecodeAndMapRotatingFields() {
        // 이 고정 자료는 openssl AES와 Python HMAC으로 생성했다.
        for (mode in listOf("enc1", "enc2")) {
            val document = ComciDocument(ComciResponse.timetableJson(fixture(mode), legacyEndpoint))
            assertEquals("26-11-06 08:00", document.sourceUpdatedAt)
            val timetable = document.timetable(1, 1)
            assertEquals("국어", timetable.cell(0, 1).subject)
            assertEquals("교사", timetable.cell(0, 1).teacher)
            assertEquals(1, timetable.periodCounts.first())
        }
    }

    @Test(expected = ComciException::class)
    fun corruptedEncryptedResponseIsRejected() {
        val parts = fixture("enc2").toString(Charsets.UTF_8).split(':').toMutableList()
        val first = if (parts[2][0] == 'A') 'B' else 'A'
        parts[2] = first + parts[2].drop(1)
        ComciResponse.timetableJson(parts.joinToString(":").toByteArray(), legacyEndpoint)
    }

    @Test
    fun missingOrMalformedRemoteArraysAreRejectedEvenIfCanonicalAliasesExist() {
        val endpoint = legacyEndpoint
        val valid = ComciResponse.timetableJson(fixture("enc2"), endpoint)
        for (canonical in listOf("자료147", "자료481", "자료446", "자료492")) {
            val remote = endpoint.dataKeys.getValue(canonical)
            for (wrongType in listOf(false, true)) {
                val root = JSONObject(valid)
                if (wrongType) root.put(remote, "invalid") else root.remove(remote)
                assertThrows(ComciException::class.java) {
                    ComciResponse.timetableJson(root.toString().toByteArray(), endpoint)
                }
            }
        }
    }

    @Test
    fun schoolCodeFollowsThePositionSpecifiedByEachRow() {
        val schools = ComciService.parseSchools("""{"학교검색":[[6347,"경기","학교1",29175,99,88],[19041,"경기","학교2",55,66,29176]]}""")
        assertEquals(listOf(29175, 29176), schools.map { it.code })
    }

    @Test
    fun discoveryReadsPackedScriptWithoutExecutingIt() {
        val script = """var url='./123456?99999l';sc_data('22222_',sc,1,'0');원자료=Q자료(자료.자료901[a]);일일자료=Q자료(자료.자료902[a]);성명=Q성명(자료.자료903[th]);과목명=Q과목명(자료.자료904[sb]);var m3=자료.자료905[a];'수정일: '+H시간표.자료906;var key=CryptoJS.enc.Hex.parse('${ComciEndpoint.DEFAULT_DECRYPTION_KEY}');"""
        val key = 83
        val packed = script.mapIndexed { index, character ->
            "%04x".format(character.code xor ((key + index * 13) and 255))
        }.joinToString("")
        val endpoint = ComciEndpoint.fromPage("<script>})([[0,\"$packed\"]],$key);</script>")
        assertEquals("123456", endpoint.route)
        assertEquals("99999l", endpoint.searchPrefix)
        assertEquals("22222_", endpoint.dataPrefix)
        assertEquals("자료902", endpoint.dataKeys["자료147"])
        assertEquals("자료904", endpoint.dataKeys["자료492"])
        val token = ComciEndpoint.base64("22222_29175_0_3_enc2".toByteArray())
        assertTrue(endpoint.timetableUrl(29175, 3).endsWith("?$token"))
    }

    @Test
    fun nestedCurrentPageDiscoversEnc5RequestsAndSplitKey() {
        // 2026-11-07 공식 페이지의 요청/복호화 함수와 필드 참조를 두 겹 포장한 자료.
        val page = javaClass.classLoader!!.getResourceAsStream("comci-enc5-page-fragment.html")!!
            .bufferedReader().use { it.readText() }
        val endpoint = ComciEndpoint.fromPage(page)
        assertEquals("952563", endpoint.route)
        assertEquals("21326l", endpoint.searchPrefix)
        assertEquals("10893_", endpoint.dataPrefix)
        assertEquals("enc5", endpoint.responseFormat)
        assertTrue(endpoint.encodedSearch)
        assertEquals("08061397ca3616e62f72c905b1ca6a0dda0584b7e357507831f0175668e165bd", endpoint.decryptionKey)
        assertEquals("자료112", endpoint.dataKeys["자료147"])
        // UTF-8 인코딩된 검색어까지 포함해 base64로 감싼 정확한 요청 토큰.
        val token = ComciEndpoint.base64("21326l%ED%95%9C%EA%B5%AD%EB%94%94_enc5".toByteArray())
        assertEquals("http://comci.net:4082/952563?${token.replace("=", "%3D")}", endpoint.searchUrl("한국디"))
        val timetableToken = ComciEndpoint.base64("10893_29175_0_1_enc5".toByteArray())
        assertEquals("http://comci.net:4082/952563?$timetableToken", endpoint.timetableUrl(29175))
    }

    @Test
    fun capturedEnc5SchoolAndTimetableResponsesDecode() {
        val endpoint = ComciEndpoint.FALLBACK
        val schoolsBytes = javaClass.classLoader!!.getResourceAsStream("school-enc5.txt")!!.use { it.readBytes() }
        val schools = ComciService.parseSchools(ComciResponse.json(schoolsBytes, endpoint))
        assertEquals(29175, schools.single().code)
        val timetable = ComciDocument(ComciResponse.timetableJson(fixture("enc5"), endpoint)).timetable(1, 1)
        assertEquals(listOf("공국", "공수", "인일", "무역", "통사", "애니", "HR"),
            timetable.days[0].filter { !it.isEmpty }.map { it.displaySubject })
    }

    @Test(expected = ComciException::class)
    fun capturedEnc5ResponseRejectsCorruptAuthenticationTag() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("school-enc5.txt")!!.use { it.readBytes() }
        val parts = bytes.toString(Charsets.UTF_8).split(':').toMutableList()
        parts[3] = (if (parts[3][0] == 'A') 'B' else 'A') + parts[3].drop(1)
        ComciResponse.json(parts.joinToString(":").toByteArray(), ComciEndpoint.FALLBACK)
    }

    @Test
    fun responseSuffixRotationFollowsThePageAndRejectsUnadvertisedFormats() {
        val page = javaClass.classLoader!!.getResourceAsStream("comci-enc5-page-fragment.html")!!
            .bufferedReader().use { it.readText() }
        val script = ComciEndpoint.decodedPage(page).replace("_enc5", "_enc6")
        val endpoint = ComciEndpoint.fromPage(script)
        assertEquals("enc6", endpoint.responseFormat)
        val bytes = javaClass.classLoader!!.getResourceAsStream("school-enc6.txt")!!.use { it.readBytes() }
        assertEquals(29175, ComciService.parseSchools(ComciResponse.json(bytes, endpoint)).single().code)
        assertThrows(ComciException::class.java) {
            ComciResponse.json(bytes, ComciEndpoint.FALLBACK)
        }
    }

    @Test(expected = ComciException::class)
    fun unsupportedPageDoesNotCachePartialFallbackConstants() {
        ComciEndpoint.fromPage("<html>server unavailable</html>")
    }
}
