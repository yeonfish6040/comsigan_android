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
    private fun fixture(mode: String): ByteArray = javaClass.classLoader!!
        .getResourceAsStream("timetable-$mode.txt")!!.use { it.readBytes() }

    @Test
    fun encryptedFormatsDecodeAndMapRotatingFields() {
        // 이 고정 자료는 openssl AES와 Python HMAC으로 생성했다.
        for (mode in listOf("enc1", "enc2")) {
            val document = ComciDocument(ComciResponse.timetableJson(fixture(mode), ComciEndpoint.FALLBACK))
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
        ComciResponse.timetableJson(parts.joinToString(":").toByteArray(), ComciEndpoint.FALLBACK)
    }

    @Test
    fun missingOrMalformedRemoteArraysAreRejectedEvenIfCanonicalAliasesExist() {
        val endpoint = ComciEndpoint.FALLBACK
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

    @Test(expected = ComciException::class)
    fun unsupportedPageDoesNotCachePartialFallbackConstants() {
        ComciEndpoint.fromPage("<html>server unavailable</html>")
    }
}
