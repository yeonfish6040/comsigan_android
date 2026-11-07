package com.yftech.comsigan.data

import android.content.Context
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** GitHub Raw에서 읽는 데이터 전용 규칙. 알 수 없는 스키마/연산은 적용하지 않는다. */
class ComciProtocolConfig private constructor(private val root: JSONObject) {
    val encoded: String = root.toString()
    val cacheId: String = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
    val revision: String get() = root.getString("revision")
    val baseUrl: String get() = root.getString("baseURL")
    val refreshSeconds: Int get() = root.getInt("refreshSeconds")
    private val discovery get() = root.getJSONObject("discovery")
    val discoveryEnabled get() = discovery.getBoolean("enabled")
    val discoveryUrl get() = baseUrl + discovery.getString("path")
    val discoveryCharset get() = if (discovery.getString("encoding") == "eucKR") "EUC-KR" else "UTF-8"
    val maxDepth get() = discovery.getInt("maxDepth")
    val wrapperPattern get() = discovery.getString("wrapperPattern")
    val splitKeyPattern get() = discovery.getString("splitKeyPattern")
    val keyIndexPattern get() = discovery.getString("keyIndexPattern")
    val encodedSearchPattern get() = discovery.getString("encodedSearchPattern")
    val fieldPatterns: Map<String, String> get() = discovery.getJSONObject("fieldPatterns").strings()
    fun patterns(name: String): List<String> = discovery.getJSONObject("patterns").getJSONArray(name).strings()
    val allowPlaintext get() = root.getJSONObject("response").getBoolean("allowPlaintext")
    fun hexFormats(format: String) = formats("hexFormats", format)
    fun base64Formats(format: String) = formats("base64Formats", format)
    private fun formats(name: String, format: String) = root.getJSONObject("response").getJSONArray(name).strings().map { it.replace("{format}", format) }
    private val school get() = root.getJSONObject("school")
    val schoolRoot get() = school.getString("root")
    fun schoolField(name: String) = school.getInt(name)

    fun fixedEndpoint(): ComciEndpoint {
        val e = root.getJSONObject("endpoint")
        return ComciEndpoint(e.getString("route"), e.getString("searchPrefix"), e.getString("dataPrefix"),
            e.getString("encryptionKey"), e.getJSONObject("fieldNames").strings(), e.getString("responseFormat"),
            e.getBoolean("encryptedSearch"), this)
    }

    fun requestToken(name: String, variables: Map<String, String>): String {
        val request = root.getJSONObject("requests").getJSONObject(name)
        val values = variables.toMutableMap()
        val transforms = request.getJSONObject("variableTransforms")
        for (key in transforms.keys()) {
            values[key] = apply(transforms.getJSONArray(key).strings(), values.getValue(key))
        }
        val value = Regex("\\{([A-Za-z]+)\\}").replace(request.getString("template")) { values.getValue(it.groupValues[1]) }
        return apply(request.getJSONArray("transforms").strings(), value)
    }
    private fun apply(operations: List<String>, value: String): String = operations.fold(value) { text, operation ->
        when (operation) {
            "uri" -> ComciEndpoint.encodeURIComponent(text)
            "base64" -> ComciEndpoint.base64(text.toByteArray(Charsets.UTF_8))
            "eucKRURI" -> URLEncoder.encode(text, "EUC-KR").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
            else -> error("Unsupported protocol operation")
        }
    }

    private fun validate() {
        require(root.getInt("schemaVersion") == 1 && revision.isNotBlank() && revision.length <= 100)
        val url = URI(baseUrl)
        require(url.scheme in listOf("http", "https") && !url.host.isNullOrEmpty() && url.rawUserInfo == null && url.rawQuery == null && url.rawFragment == null && url.rawPath.orEmpty().isEmpty())
        require(url.host !in listOf("localhost", "127.0.0.1", "::1", "[::1]") && !url.host.endsWith(".local"))
        require(refreshSeconds in 60..86400 && maxDepth in 1..8)
        val path = discovery.getString("path")
        require(path.startsWith('/') && !path.startsWith("//") && path.length <= 200 && '?' !in path && '#' !in path)
        require(discovery.getString("encoding") in listOf("eucKR", "utf8"))
        fun regex(pattern: String, captures: Int) {
            require(pattern.isNotEmpty() && pattern.length <= 2048)
            require(Regex(pattern, RegexOption.DOT_MATCHES_ALL).toPattern().matcher("").groupCount() >= captures)
        }
        regex(wrapperPattern, 2); regex(splitKeyPattern, 2); regex(keyIndexPattern, 1); regex(encodedSearchPattern, 0)
        val allPatterns = discovery.getJSONObject("patterns")
        require(allPatterns.length() <= 10)
        for (key in listOf("route", "searchPrefix", "dataPrefix", "key", "format")) {
            val values = patterns(key)
            require(values.isNotEmpty() && values.size <= 8)
            values.forEach { regex(it, 1) }
        }
        require(fieldPatterns.size <= 40)
        fieldPatterns.values.forEach { regex(it, 1) }
        val e = root.getJSONObject("endpoint")
        for (key in listOf("route", "searchPrefix", "dataPrefix", "responseFormat")) require(Regex("[A-Za-z0-9_-]{1,100}").matches(e.getString(key)))
        require(Regex("[0-9a-f]{64}").matches(e.getString("encryptionKey")))
        e.getBoolean("encryptedSearch")
        val fields = e.getJSONObject("fieldNames").strings()
        val required = listOf("자료147", "자료481", "자료446", "자료492")
        require(fields.size <= 40 && required.all { it in fields && it in fieldPatterns })
        for (name in listOf("search", "legacySearch", "timetable")) {
            val request = root.getJSONObject("requests").getJSONObject(name)
            require(request.getString("template").length in 1..512)
            val remainder = Regex("\\{(?:query|school|week|format|searchPrefix|dataPrefix)\\}").replace(request.getString("template"), "")
            require('{' !in remainder && '}' !in remainder)
            val variables = request.getJSONObject("variableTransforms")
            val groups = mutableListOf(request.getJSONArray("transforms").strings())
            for (key in variables.keys()) {
                require(key in listOf("query", "school", "week", "format", "searchPrefix", "dataPrefix"))
                groups.add(variables.getJSONArray(key).strings())
            }
            require(groups.all { it.size <= 8 && it.all { step -> step in listOf("uri", "base64", "eucKRURI") } })
            requestToken(name, mapOf("query" to "test", "school" to "1", "week" to "1", "format" to "enc5", "searchPrefix" to "1l", "dataPrefix" to "1_"))
        }
        val response = root.getJSONObject("response")
        require(response.getString("cipher") == "aes256-cbc-pkcs7" && response.getString("authentication") == "hmac-sha256-key-sha256")
        response.getBoolean("allowPlaintext")
        val hex = response.getJSONArray("hexFormats").strings()
        val base64 = response.getJSONArray("base64Formats").strings()
        require(hex.isNotEmpty() && base64.isNotEmpty() && hex.size + base64.size <= 16)
        require((hex + base64).all { it == "{format}" || Regex("[A-Za-z0-9_-]{1,32}").matches(it) })
        require(schoolRoot.length in 1..100 && schoolField("codeDivisor") in 1..1_000_000)
        for (key in listOf("nameIndex", "regionIndex", "markerIndex", "codeOffset")) require(schoolField(key) in 0..100)
    }
    companion object {
        const val RAW_URL = "https://raw.githubusercontent.com/yeonfish6040/comsigan_ios/main/Config/comci-protocol.json"
        val bundled: ComciProtocolConfig by lazy { decode(ComciProtocolDefaults.json) }
        fun decode(text: String): ComciProtocolConfig {
            require(text.toByteArray().size <= 256 * 1024) { "Protocol configuration too large" }
            return ComciProtocolConfig(JSONObject(text)).also { it.validate() }
        }
    }
}
private fun JSONObject.strings(): Map<String, String> = keys().asSequence().associateWith { getString(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

/** 후보 설정은 API 요청까지 성공해야 active에 저장된다. 테스트에서는 메모리 저장소를 주입한다. */
class ComciProtocolStore(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
    private val download: () -> String,
) {
    private fun cached(key: String) = read(key)?.let { runCatching { ComciProtocolConfig.decode(it) }.getOrNull() }
    @Synchronized fun candidates(forceRefresh: Boolean = false, now: Long = System.currentTimeMillis()): List<ComciProtocolConfig> {
        val active = cached("active")
        val age = now - (read("lastAttempt")?.toLongOrNull() ?: 0L)
        if (age >= if (forceRefresh) 30_000 else (active?.refreshSeconds ?: ComciProtocolConfig.bundled.refreshSeconds) * 1000L) {
            write("lastAttempt", now.toString())
            runCatching { ComciProtocolConfig.decode(download()) }.onSuccess { write("pending", it.encoded) }
        }
        return listOfNotNull(cached("pending"), cached("active"), ComciProtocolConfig.bundled).distinctBy { it.cacheId }
    }
    @Synchronized fun markWorking(config: ComciProtocolConfig) {
        write("active", config.encoded)
        if (cached("pending")?.cacheId == config.cacheId) write("pending", null)
    }
    @Synchronized fun rejectCandidate(config: ComciProtocolConfig) {
        if (cached("pending")?.cacheId == config.cacheId) write("pending", null)
    }
    companion object {
        @Volatile private var instance: ComciProtocolStore? = null
        @Synchronized fun shared(context: Context): ComciProtocolStore {
            instance?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences("remoteComciProtocolV1", Context.MODE_PRIVATE)
            return ComciProtocolStore(
                read = { prefs.getString(it, null) },
                write = { key, value -> prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() },
                download = { Http.getString(ComciProtocolConfig.RAW_URL) },
            ).also { instance = it }
        }
    }
}
