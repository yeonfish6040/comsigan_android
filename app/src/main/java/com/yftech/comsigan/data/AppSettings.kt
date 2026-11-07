package com.yftech.comsigan.data

import android.content.Context

/** 앱과 위젯이 함께 읽는 설정. 같은 프로세스라 SharedPreferences로 충분하다. */
object AppSettings {
    private const val PREFS = "comsigan"
    private const val KEY_GRADE = "grade"
    private const val KEY_CLASS = "klass"
    private const val KEY_SCHOOL_CODE = "schoolCode"
    private const val KEY_SCHOOL_NAME = "schoolName"
    private const val KEY_THEME = "widgetTheme"
    private const val KEY_TUTORIAL_DONE = "tutorialDone"
    private const val KEY_LAYOUT_PREFIX = "widgetLayout_"
    private const val KEY_PERIOD_TIMES_PREFIX = "periodTimes_"
    const val KEY_ENDPOINT = "endpointV3"
    const val KEY_ENDPOINT_CHECKED_AT = "endpointCheckedAt"

    /** 처음 실행 시 기본으로 보는 학교(검색 결과의 이름·코드 그대로). */
    val DEFAULT_SCHOOL = School(code = 29175, name = "한국디지털미디어고등학", region = "경기")

    /**
     * 컴시간 자료가 실제 운영과 어긋난다고 확인된 학교의 기본 보정값.
     * 사용자가 설정에서 고치면 그 값이 이것보다 우선한다.
     */
    val BUILT_IN_PERIOD_TIMES: Map<Int, List<PeriodTime>> = mapOf(
        // 한국디지털미디어고 — 컴시간에는 점심 이후가 :50 시작으로 올라와 있지만
        // 실제로는 :40에 시작한다(50분 수업 + 10분 쉬는시간).
        29175 to listOf(
            PeriodTime(1, 9, 0),
            PeriodTime(2, 10, 0),
            PeriodTime(3, 11, 0),
            PeriodTime(4, 12, 0),
            PeriodTime(5, 13, 40),
            PeriodTime(6, 14, 40),
            PeriodTime(7, 15, 40),
            PeriodTime(8, 16, 40),
        ),
    )

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun grade(context: Context): Int = prefs(context).getInt(KEY_GRADE, 1).coerceAtLeast(1)

    fun klass(context: Context): Int = prefs(context).getInt(KEY_CLASS, 1).coerceAtLeast(1)

    fun school(context: Context): School {
        val prefs = prefs(context)
        val code = prefs.getInt(KEY_SCHOOL_CODE, 0)
        if (code <= 0) return DEFAULT_SCHOOL
        return School(code = code, name = prefs.getString(KEY_SCHOOL_NAME, "").orEmpty())
    }

    fun setClass(context: Context, grade: Int, klass: Int) {
        prefs(context).edit().putInt(KEY_GRADE, grade).putInt(KEY_CLASS, klass).apply()
    }

    /** 위젯 테마 id. 투명 배경도 테마 중 하나다. */
    fun widgetTheme(context: Context): String? = prefs(context).getString(KEY_THEME, null)

    fun setWidgetTheme(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME, id).commit()
    }

    /** 위젯 자리별로 고른 레이아웃 id. */
    fun widgetLayout(context: Context, slotKey: String): String? =
        prefs(context).getString(KEY_LAYOUT_PREFIX + slotKey, null)

    fun setWidgetLayout(context: Context, slotKey: String, layoutId: String) {
        prefs(context).edit().putString(KEY_LAYOUT_PREFIX + slotKey, layoutId).commit()
    }

    // MARK: 일과시간

    /**
     * 학교가 컴시간에 올려 둔 일과시간이 실제 운영과 다를 때 쓰는 보정값. 학교마다 따로 둔다.
     * "교시,시,분" 세 쌍을 ';'로 이어 한 줄로 넣는다.
     */
    fun periodTimes(context: Context, school: Int): List<PeriodTime>? {
        val raw = prefs(context).getString(KEY_PERIOD_TIMES_PREFIX + school, null) ?: return null
        val times = raw.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 3) return@mapNotNull null
            val period = parts[0].toIntOrNull() ?: return@mapNotNull null
            val hour = parts[1].toIntOrNull() ?: return@mapNotNull null
            val minute = parts[2].toIntOrNull() ?: return@mapNotNull null
            PeriodTime(period, hour, minute)
        }
        return times.ifEmpty { null }?.sortedBy { it.period }
    }

    /** null이나 빈 목록을 넣으면 사용자 보정을 지우고 기본 보정값 또는 서버 자료를 따른다. */
    fun setPeriodTimes(context: Context, school: Int, times: List<PeriodTime>?) {
        val key = KEY_PERIOD_TIMES_PREFIX + school
        val editor = prefs(context).edit()
        if (times.isNullOrEmpty()) {
            editor.remove(key)
        } else {
            editor.putString(key, times.joinToString(";") { "${it.period},${it.hour},${it.minute}" })
        }
        // 위젯이 곧바로 다시 그리며 읽으므로 commit으로 확실히 넘긴다.
        editor.commit()
    }

    /** 서버 자료 위에 보정값을 얹은 최종 일과시간. */
    fun resolvedPeriodTimes(context: Context, school: Int, server: List<PeriodTime>): List<PeriodTime> =
        periodTimes(context, school) ?: BUILT_IN_PERIOD_TIMES[school] ?: server

    /** 첫 실행 안내를 이미 봤는지. */
    fun isTutorialDone(context: Context): Boolean = prefs(context).getBoolean(KEY_TUTORIAL_DONE, false)

    fun setTutorialDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_TUTORIAL_DONE, true).apply()
    }

    fun setSchool(context: Context, school: School) {
        prefs(context).edit()
            .putInt(KEY_SCHOOL_CODE, school.code)
            .putString(KEY_SCHOOL_NAME, school.name)
            .apply()
    }
}
