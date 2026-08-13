package com.yftech.comsigan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

        val bytes = Http.getBytes(endpoint(context).searchUrl(query))
        val root = runCatching { JSONObject(Http.sanitizedJson(bytes)) }.getOrNull()
            ?: throw ComciException("검색 결과를 해석하지 못했습니다.")
        val rows = root.optJSONArray("학교검색") ?: return@withContext emptyList()

        // 한 줄이 [내부코드, 지역, 학교명, 학교코드] 형태다.
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.opt(index) as? JSONArray ?: return@mapNotNull null
            if (row.length() < 4) return@mapNotNull null
            School(
                code = ComciDocument.numeric(row.opt(3)),
                name = row.optString(2),
                region = row.optString(1),
            )
        }.filter { it.code > 0 }
    }

    // MARK: 시간표

    suspend fun timetable(context: Context, school: Int, grade: Int, klass: Int, forceRefresh: Boolean = false): Timetable =
        document(context, school, forceRefresh).timetable(grade, klass)

    suspend fun document(context: Context, school: Int, forceRefresh: Boolean = false): ComciDocument =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                cached(context, school, ignoreAge = false)?.let { return@withContext ComciDocument(it) }
            }

            try {
                val json = Http.sanitizedJson(Http.getBytes(endpoint(context).timetableUrl(school)))
                val document = ComciDocument(json) // 파싱에 성공한 것만 캐시한다
                cacheFile(context, school).writeText(json)
                document
            } catch (error: Exception) {
                // 네트워크가 끊겼으면 기한이 지난 캐시라도 쓴다.
                val stale = cached(context, school, ignoreAge = true) ?: throw error
                ComciDocument(stale)
            }
        }

    /** 파일이 그대로면 다시 파싱하지 않도록 들고 있는 것. */
    private var memo: Triple<Int, Long, ComciDocument>? = null

    /** 네트워크를 절대 타지 않는 즉시 읽기. 위젯이 매 갱신마다 부르므로 파싱 결과를 재사용한다. */
    @Synchronized
    fun cachedDocument(context: Context, school: Int): ComciDocument? {
        val file = cacheFile(context, school)
        if (!file.exists()) return null
        val stamp = file.lastModified()
        memo?.let { (cachedSchool, cachedStamp, document) ->
            if (cachedSchool == school && cachedStamp == stamp) return document
        }
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val document = runCatching { ComciDocument(text) }.getOrNull() ?: return null
        memo = Triple(school, stamp, document)
        return document
    }

    /** 캐시가 유효 기간을 넘겼는지. */
    fun isCacheStale(context: Context, school: Int): Boolean {
        val file = cacheFile(context, school)
        if (!file.exists()) return true
        return System.currentTimeMillis() - file.lastModified() > CACHE_LIFETIME_MS
    }

    // MARK: 호출 상수

    private fun endpoint(context: Context): ComciEndpoint {
        val prefs = AppSettings.prefs(context)
        val stored = prefs.getString(AppSettings.KEY_ENDPOINT, null)
        val checkedAt = prefs.getLong(AppSettings.KEY_ENDPOINT_CHECKED_AT, 0)
        if (stored != null && System.currentTimeMillis() - checkedAt < ENDPOINT_LIFETIME_MS) {
            runCatching {
                val json = JSONObject(stored)
                return ComciEndpoint(
                    route = json.getString("route"),
                    searchPrefix = json.getString("searchPrefix"),
                    dataPrefix = json.getString("dataPrefix"),
                )
            }
        }

        val discovered = runCatching { ComciEndpoint.discover() }.getOrElse { return ComciEndpoint.FALLBACK }
        val encoded = JSONObject()
            .put("route", discovered.route)
            .put("searchPrefix", discovered.searchPrefix)
            .put("dataPrefix", discovered.dataPrefix)
            .toString()
        prefs.edit()
            .putString(AppSettings.KEY_ENDPOINT, encoded)
            .putLong(AppSettings.KEY_ENDPOINT_CHECKED_AT, System.currentTimeMillis())
            .apply()
        return discovered
    }

    // MARK: 디스크 캐시

    private fun cacheFile(context: Context, school: Int) = File(context.cacheDir, "comci-$school.json")

    private fun cached(context: Context, school: Int, ignoreAge: Boolean): String? {
        val file = cacheFile(context, school)
        if (!file.exists()) return null
        if (!ignoreAge && System.currentTimeMillis() - file.lastModified() > CACHE_LIFETIME_MS) return null
        return runCatching { file.readText() }.getOrNull()
    }
}
