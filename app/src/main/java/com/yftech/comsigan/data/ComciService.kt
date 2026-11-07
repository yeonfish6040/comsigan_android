package com.yftech.comsigan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/** 컴시간 학교 검색 / 시간표 요청 + 디스크 캐시. */
object ComciService {
    /** 캐시 유효 시간. */
    private const val CACHE_LIFETIME_MS = 30 * 60 * 1000L
    /** 호출 상수 재확인 주기. */
    private const val ENDPOINT_LIFETIME_MS = 24 * 60 * 60 * 1000L

    // MARK: 학교 검색

    suspend fun searchSchools(context: Context, name: String): List<School> = withContext(Dispatchers.IO) {
        val query = name.trim()
        if (query.length < 2) return@withContext emptyList()

        withEndpoint(context) { endpoint ->
            parseSchools(ComciResponse.json(Http.getBytes(endpoint.searchUrl(query)), endpoint), endpoint.config)
        }
    }

    /** 학교코드의 위치도 서버가 한 줄마다 지정한다(열람 페이지 sc_disp와 동일). */
    fun parseSchools(json: String, config: ComciProtocolConfig = ComciProtocolConfig.bundled): List<School> {
        val rows = JSONObject(json).optJSONArray(config.schoolRoot)
            ?: throw ComciException("검색 결과를 해석하지 못했습니다.")
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.opt(index) as? JSONArray ?: return@mapNotNull null
            val marker = ComciDocument.numeric(row.opt(config.schoolField("markerIndex")))
            if (marker < 0) return@mapNotNull null
            val index = marker.toLong() / config.schoolField("codeDivisor") + config.schoolField("codeOffset")
            if (index < 0 || index >= row.length()) return@mapNotNull null
            val codeIndex = index.toInt()
            School(
                code = ComciDocument.numeric(row.opt(codeIndex)),
                name = row.optString(config.schoolField("nameIndex")),
                region = row.optString(config.schoolField("regionIndex")),
            ).takeIf { it.code > 0 }
        }
    }

    // MARK: 시간표

    suspend fun timetable(
        context: Context,
        school: Int,
        grade: Int,
        klass: Int,
        week: ComciWeek = ComciWeek.FIRST,
        forceRefresh: Boolean = false,
    ): Timetable = document(context, school, week, forceRefresh)
        .timetable(grade, klass, context, schoolCode = school)

    suspend fun document(
        context: Context,
        school: Int,
        week: ComciWeek = ComciWeek.FIRST,
        forceRefresh: Boolean = false,
    ): ComciDocument = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cached(context, school, week, ignoreAge = false)?.let { return@withContext ComciDocument(it) }
        }

        try {
            withEndpoint(context) { endpoint ->
                val bytes = Http.getBytes(endpoint.timetableUrl(school, week.r))
                val json = ComciResponse.timetableJson(bytes, endpoint)
                val document = ComciDocument(json) // 복호화와 파싱에 성공한 것만 캐시한다
                cacheFile(context, school, week).writeText(json)
                document
            }
        } catch (error: Exception) {
            // 네트워크가 끊겼으면 기한이 지난 캐시라도 쓴다.
            val stale = cached(context, school, week, ignoreAge = true) ?: throw error
            ComciDocument(stale)
        }
    }

    /** 오늘이 낀 주의 자료. r=1이 이번 주라는 보장이 없어 응답의 '오늘r'을 보고 맞춘다. */
    suspend fun currentDocument(context: Context, school: Int, forceRefresh: Boolean = false): ComciDocument {
        val probe = document(context, school, ComciWeek.FIRST, forceRefresh)
        val todayWeek = probe.todayWeek
        if (todayWeek == ComciWeek.FIRST.r) return probe
        return document(context, school, ComciWeek(todayWeek), forceRefresh)
    }

    /**
     * 위젯이 그릴 때 필요한 두 주의 자료를 캐시에서 꺼낸다. 네트워크는 절대 타지 않는다.
     * 다음 주 자료는 [ensureWeeks]가 받아 둔 경우에만 들어 있다.
     */
    fun cachedWeeks(context: Context, school: Int): CachedWeeks? {
        val probe = cachedDocument(context, school, ComciWeek.FIRST) ?: return null
        val todayWeek = probe.todayWeek
        val current = if (todayWeek == ComciWeek.FIRST.r) {
            probe
        } else {
            cachedDocument(context, school, ComciWeek(todayWeek)) ?: probe
        }
        return CachedWeeks(current, cachedDocument(context, school, ComciWeek(todayWeek + 1)))
    }

    /**
     * 위젯이 그리는 데 필요한 주(오늘이 낀 주 + 이번 주가 끝나 가면 다음 주)를 캐시에 채운다.
     * 새로 받아온 게 있으면 true — 그때만 위젯을 다시 그리면 된다.
     */
    suspend fun ensureWeeks(
        context: Context,
        school: Int,
        grade: Int,
        klass: Int,
        now: Date = Date(),
        lookahead: Int = 3,
    ): Boolean = withContext(Dispatchers.IO) {
        var fetched = false
        fun fresh(week: ComciWeek) =
            cachedDocument(context, school, week)?.takeIf { !isCacheStale(context, school, week) }

        // r=1은 '오늘r'을 알기 위해서라도 있어야 한다.
        val probe = fresh(ComciWeek.FIRST) ?: run {
            fetched = true
            document(context, school, ComciWeek.FIRST, forceRefresh = true)
        }
        val todayWeek = ComciWeek(probe.todayWeek)
        val current = if (todayWeek.r == ComciWeek.FIRST.r) probe else fresh(todayWeek) ?: run {
            fetched = true
            document(context, school, todayWeek, forceRefresh = true)
        }

        val timetable = runCatching {
            current.timetable(grade, klass, context, schoolCode = school)
        }.getOrNull() ?: return@withContext fetched
        val after = timetable.focusPeriod(now) ?: Timetable.SKIP_TODAY
        if (timetable.upcoming(now, after, lookahead).size >= lookahead) return@withContext fetched

        // 학교가 다음 주를 아직 올리지 않았으면 목록에 없다.
        val nextWeek = ComciWeek(todayWeek.r + 1)
        if (current.weeks.none { it.r == nextWeek.r }) return@withContext fetched
        if (fresh(nextWeek) != null) return@withContext fetched
        runCatching { document(context, school, nextWeek, forceRefresh = true) }.onSuccess { fetched = true }
        fetched
    }

    /** 파일이 그대로면 다시 파싱하지 않도록 들고 있는 것. */
    private val memo = mutableMapOf<Pair<Int, Int>, Pair<Long, ComciDocument>>()

    /** 네트워크를 절대 타지 않는 즉시 읽기. 위젯이 매 갱신마다 부르므로 파싱 결과를 재사용한다. */
    @Synchronized
    fun cachedDocument(context: Context, school: Int, week: ComciWeek = ComciWeek.FIRST): ComciDocument? {
        val file = cacheFile(context, school, week)
        if (!file.exists()) return null
        val key = school to week.r
        val stamp = file.lastModified()
        memo[key]?.let { (cachedStamp, document) -> if (cachedStamp == stamp) return document }
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val document = runCatching { ComciDocument(text) }.getOrNull() ?: return null
        memo[key] = stamp to document
        return document
    }

    /** 캐시가 유효 기간을 넘겼는지. */
    fun isCacheStale(context: Context, school: Int, week: ComciWeek = ComciWeek.FIRST): Boolean {
        val file = cacheFile(context, school, week)
        if (!file.exists()) return true
        return System.currentTimeMillis() - file.lastModified() > CACHE_LIFETIME_MS
    }

    /** 위젯이 한 번에 받아 가는 두 주의 자료. */
    data class CachedWeeks(val current: ComciDocument, val next: ComciDocument?)

    // MARK: 호출 상수

    private fun <T> withEndpoint(context: Context, request: (ComciEndpoint) -> T): T {
        val store = ComciProtocolStore.shared(context)
        var lastError: Exception = ComciException("시간표 연결 설정을 불러오지 못했습니다.")
        for (refresh in listOf(false, true)) {
            for (config in store.candidates(forceRefresh = refresh)) {
                try {
                    val result = request(endpoint(context, config, forceRefresh = refresh))
                    store.markWorking(config)
                    return result
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) {
                    lastError = error
                    store.rejectCandidate(config)
                }
            }
        }
        throw lastError
    }

    private fun endpoint(context: Context, config: ComciProtocolConfig, forceRefresh: Boolean = false): ComciEndpoint {
        if (!config.discoveryEnabled) return config.fixedEndpoint()
        val prefs = AppSettings.prefs(context)
        val key = "configuredComciEndpointV1"
        val stored = prefs.getString(key, null)
        val checkedAt = prefs.getLong(key + "CheckedAt", 0)
        if (!forceRefresh && stored != null && System.currentTimeMillis() - checkedAt < ENDPOINT_LIFETIME_MS) {
            runCatching {
                val json = JSONObject(stored)
                require(json.getString("configId") == config.cacheId)
                return ComciEndpoint(
                    route = json.getString("route"), searchPrefix = json.getString("searchPrefix"),
                    dataPrefix = json.getString("dataPrefix"), decryptionKey = json.getString("decryptionKey"),
                    responseFormat = json.getString("responseFormat"), encodedSearch = json.getBoolean("encodedSearch"),
                    dataKeys = json.getJSONObject("dataKeys").let { keys -> keys.keys().asSequence().associateWith { keys.getString(it) } },
                    protocolConfig = config,
                )
            }
        }
        val discovered = ComciEndpoint.discover(config)
        val encoded = JSONObject()
            .put("configId", config.cacheId)
            .put("route", discovered.route)
            .put("searchPrefix", discovered.searchPrefix)
            .put("dataPrefix", discovered.dataPrefix)
            .put("decryptionKey", discovered.decryptionKey)
            .put("responseFormat", discovered.responseFormat)
            .put("encodedSearch", discovered.encodedSearch)
            .put("dataKeys", JSONObject(discovered.dataKeys))
            .toString()
        prefs.edit()
            .putString(key, encoded)
            .putLong(key + "CheckedAt", System.currentTimeMillis())
            .apply()
        return discovered
    }

    // MARK: 디스크 캐시

    /** r=1 캐시는 기존 파일명을 유지해 이미 받아 둔 자료를 그대로 이어 쓴다. */
    private fun cacheFile(context: Context, school: Int, week: ComciWeek) = File(
        context.cacheDir,
        if (week.r == ComciWeek.FIRST.r) "comci-$school.json" else "comci-$school-r${week.r}.json",
    )

    private fun cached(context: Context, school: Int, week: ComciWeek, ignoreAge: Boolean): String? {
        val file = cacheFile(context, school, week)
        if (!file.exists()) return null
        if (!ignoreAge && System.currentTimeMillis() - file.lastModified() > CACHE_LIFETIME_MS) return null
        return runCatching { file.readText() }.getOrNull()
    }
}
