package com.yftech.comsigan

import com.yftech.comsigan.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteProtocolConfigTest {
    private fun modified(change: (JSONObject) -> Unit): ComciProtocolConfig {
        val json = JSONObject(ComciProtocolConfig.bundled.encoded)
        change(json)
        return ComciProtocolConfig.decode(json.toString())
    }
    @Test fun shippedAssetMatchesEmbeddedDefaults() {
        val asset = File("src/main/assets/comci-protocol.json").readText()
        assertEquals(ComciProtocolConfig.bundled.cacheId, ComciProtocolConfig.decode(asset).cacheId)
    }
    @Test fun remoteHostRouteAndEncodingOrderAreApplied() {
        val config = modified {
            it.put("baseURL", "https://api.example.com")
            it.getJSONObject("discovery").put("enabled", false)
            it.getJSONObject("endpoint").put("route", "replacement")
            it.getJSONObject("requests").getJSONObject("search").put("template", "{query}_{searchPrefix}")
        }
        val token = ComciEndpoint.base64("%ED%95%9C%EA%B5%AD%EB%94%94_21326l".toByteArray()).replace("=", "%3D")
        assertEquals("https://api.example.com/replacement?$token", config.fixedEndpoint().searchUrl("한국디"))
        assertNotEquals(ComciProtocolConfig.bundled.cacheId, config.cacheId) // revision을 깜빡해도 설정 변경을 감지한다.
    }
    @Test fun remoteExtractionRulesAreApplied() {
        val config = modified {
            it.getJSONObject("discovery").getJSONObject("patterns").put("route", listOf("routeOverride=([A-Za-z0-9]+)"))
        }
        val page = javaClass.classLoader!!.getResourceAsStream("comci-enc5-page-fragment.html")!!.bufferedReader().readText()
        assertEquals("replacement", ComciEndpoint.fromPage(page + " routeOverride=replacement", config).route)
    }
    @Test fun remoteFieldAliasesApplyToTimetableAndMetadata() {
        val source = javaClass.classLoader!!.getResourceAsStream("timetable-enc5.txt")!!.readBytes()
        val root = JSONObject(ComciResponse.timetableJson(source, ComciEndpoint.FALLBACK))
        val config = modified {
            it.getJSONObject("discovery").put("enabled", false)
            val fields = it.getJSONObject("endpoint").getJSONObject("fieldNames")
            for ((canonical, alias) in mapOf("자료147" to "daily", "자료481" to "original", "자료446" to "teachers", "자료492" to "subjects", "학급수" to "grades")) {
                val old = fields.optString(canonical, canonical)
                root.put(alias, root.get(old))
                root.remove(old)
                root.remove(canonical)
                fields.put(canonical, alias)
            }
        }
        val document = ComciDocument(ComciResponse.timetableJson(root.toString().toByteArray(), config.fixedEndpoint()))
        assertEquals("공국", document.timetable(1, 1).cell(0, 1).subject)
    }
    @Test fun remoteSchoolRootAndColumnLayoutAreApplied() {
        val config = modified {
            it.getJSONObject("school").put("root", "schools").put("nameIndex", 1).put("regionIndex", 2)
                .put("codeDivisor", 1_000_000).put("codeOffset", 0)
        }
        val result = ComciService.parseSchools("""{"schools":[[29175,"학교","경기"]]}""", config)
        assertEquals(29175, result.single().code)
        assertEquals("학교", result.single().name)
    }
    @Test fun malformedOrUnsupportedRulesAreRejected() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("schemaVersion", 2) },
            { it.put("baseURL", "file:///tmp/data") },
            { it.getJSONObject("response").put("cipher", "unknown") },
            { it.getJSONObject("discovery").put("wrapperPattern", "no-capture") },
            { it.getJSONObject("requests").getJSONObject("search").put("transforms", listOf("eval")) },
            { it.getJSONObject("requests").getJSONObject("search").put("template", "{unknown}") },
        )
        for (change in changes) assertThrows(Exception::class.java) { modified(change) }
        assertThrows(Exception::class.java) { ComciProtocolConfig.decode("<html>404</html>") }
    }
    @Test fun downloadPromotionRollbackAndOfflineRecovery() {
        val memory = mutableMapOf<String, String>()
        val working = modified { it.put("revision", "working") }
        var remote = working.encoded
        var offline = false
        var downloads = 0
        val store = ComciProtocolStore(
            read = { memory[it] }, write = { key, value -> if (value == null) memory.remove(key) else memory[key] = value },
            download = { downloads++; if (offline) error("offline") else remote },
        )
        assertEquals(working.cacheId, store.candidates(now = 1_000_000).first().cacheId)
        assertNull(memory["active"])
        store.markWorking(working)
        remote = "<html>404</html>"
        assertEquals(working.cacheId, store.candidates(forceRefresh = true, now = 1_060_000).first().cacheId)
        val broken = modified { it.put("revision", "bad-api") }
        remote = broken.encoded
        assertEquals(broken.cacheId, store.candidates(forceRefresh = true, now = 1_120_000).first().cacheId)
        store.rejectCandidate(broken)
        assertEquals(working.cacheId, store.candidates(now = 1_120_000).first().cacheId)
        offline = true
        assertEquals(working.cacheId, store.candidates(forceRefresh = true, now = 1_180_000).first().cacheId)
        assertEquals(4, downloads)
    }
}
