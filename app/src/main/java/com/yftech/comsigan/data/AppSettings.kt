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
    private const val KEY_LAYOUT_PREFIX = "widgetLayout_"
    const val KEY_ENDPOINT = "endpoint"
    const val KEY_ENDPOINT_CHECKED_AT = "endpointCheckedAt"

    /** 처음 실행 시 기본으로 보는 학교(검색 결과의 이름·코드 그대로). */
    val DEFAULT_SCHOOL = School(code = 29175, name = "한국디지털미디어고등학", region = "경기")

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

    fun setSchool(context: Context, school: School) {
        prefs(context).edit()
            .putInt(KEY_SCHOOL_CODE, school.code)
            .putString(KEY_SCHOOL_NAME, school.name)
            .apply()
    }
}
