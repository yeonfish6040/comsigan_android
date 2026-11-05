package com.yftech.comsigan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 컴시간 원본 JSON 파서. 열람 페이지의 자료944()/동시그룹코드() 로직을 그대로 옮겼다.
 */
class ComciDocument(json: String) {
    private val root: JSONObject = JSONObject(json)

    val schoolName: String get() = root.optString("학교명", "")
    val sourceUpdatedAt: String get() = root.optString("자료244", "")

    /** 학년별 학급 수. 인덱스 0은 전체 합계라 쓰지 않는다. */
    val classCounts: List<Int>
        get() {
            val array = root.optJSONArray("학급수") ?: return emptyList()
            return (0 until array.length()).map { numeric(array.opt(it)) }
        }

    val grades: List<Int>
        get() = classCounts.let { counts ->
            if (counts.size <= 1) emptyList() else (1 until counts.size).filter { counts[it] > 0 }
        }

    fun classCount(grade: Int): Int = classCounts.getOrNull(grade) ?: 0

    // MARK: 주차

    /** 열람할 수 있는 주차 목록. 한 줄이 [r, "26-08-17 ~ 26-08-22"] 형태다. */
    val weeks: List<ComciWeek>
        get() {
            val array = root.optJSONArray("일자자료") ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val row = array.opt(index) as? JSONArray ?: return@mapNotNull null
                if (row.length() < 2) return@mapNotNull null
                val r = numeric(row.opt(0))
                if (r > 0) ComciWeek(r, row.optString(1)) else null
            }
        }

    /** 오늘이 속한 주차의 r. */
    val todayWeek: Int get() = if (root.has("오늘r")) numeric(root.opt("오늘r")) else ComciWeek.FIRST.r

    /** 이 자료가 담고 있는 주의 월요일. 예: "2026-08-10" */
    val weekStartDate: String get() = root.optString("시작일", "")

    /**
     * 이 자료가 어느 주차인지. 응답에 r이 없어서 시작일로 목록과 맞춰 본다.
     * (일자자료 라벨은 "26-08-10 ~ …"처럼 두 자리 연도로 시작한다.)
     */
    val week: ComciWeek?
        get() {
            val start = weekStartDate.drop(2)
            if (start.isEmpty()) return null
            return weeks.firstOrNull { it.label.startsWith(start) }
        }

    private val division: Int get() = if (root.has("분리")) numeric(root.opt("분리")) else 100
    private val usesClassroom: Boolean get() = numeric(root.opt("강의실")) == 1
    private val usesChangeNotice: Boolean get() = numeric(root.opt("변경알림")) == 1

    /** 학교가 컴시간에 올려 둔 원본 일과시간. 설정 화면에서 "서버 값으로 되돌리기"에 쓴다. */
    val serverPeriodTimes: List<PeriodTime>
        get() {
            val array = root.optJSONArray("일과시간") ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val text = array.optString(index)
                val open = text.indexOf('(')
                val close = text.indexOf(')')
                if (open < 0 || close < open) return@mapNotNull null
                val parts = text.substring(open + 1, close).split(":")
                if (parts.size != 2) return@mapNotNull null
                val hour = parts[0].toIntOrNull() ?: return@mapNotNull null
                val minute = parts[1].toIntOrNull() ?: return@mapNotNull null
                PeriodTime(index + 1, hour, minute)
            }
        }

    // MARK: 시간표 생성

    /**
     * [context]를 주면 설정에 저장된 일과시간 보정을 얹는다.
     * 순수 파서로만 쓸 때(테스트 등)는 빼도 되고, 그때는 서버 자료를 그대로 쓴다.
     */
    fun timetable(
        grade: Int,
        klass: Int,
        context: Context? = null,
        schoolCode: Int? = null,
    ): Timetable {
        if (klass < 1 || klass > classCount(grade)) {
            throw ComciException("${grade}학년 ${klass}반 시간표가 없습니다.")
        }

        val div = division
        val days = mutableListOf<List<TimetableCell>>()
        val periodCounts = mutableListOf<Int>()

        for (day in 1..MAX_DAY) {
            val row = MutableList(MAX_PERIOD) { TimetableCell.EMPTY }
            periodCounts.add(numeric(value("자료147", grade, klass, day, 0)))

            for (period in 1..MAX_PERIOD) {
                val rawDaily = value("자료147", grade, klass, day, period)
                val originalData = numeric(value("자료481", grade, klass, day, period))
                val dailyData = numeric(rawDaily)
                // 변경알림을 쓰는 학교는 ">" 접두어가 곧 변경 표시다(열람 페이지 baSplit과 동일).
                val isChanged = if (usesChangeNotice) {
                    (rawDaily as? String)?.startsWith(">") == true
                } else {
                    originalData != dailyData
                }

                if (dailyData <= 100) {
                    row[period - 1] = TimetableCell(isChanged = isChanged)
                    continue
                }

                val teacherIndex = teacherIndex(dailyData, div)
                var subjectIndex = subjectIndex(dailyData, div)
                val timePrefix = timePrefix(subjectIndex, div)
                subjectIndex %= div

                val teachers = root.optJSONArray("자료446")
                val subjects = root.optJSONArray("자료492")
                val teacherName = teacherName(teachers?.optString(teacherIndex).orEmpty())
                val subjectName = subjects?.optString(subjectIndex).orEmpty()
                val group = if (timePrefix.isEmpty()) {
                    groupCode(grade, klass, subjectIndex, day, period)
                } else {
                    timePrefix
                }

                row[period - 1] = TimetableCell(
                    subject = subjectName,
                    teacher = teacherName,
                    room = classroom(grade, klass, day, period),
                    group = group,
                    isChanged = isChanged,
                )
            }
            days.add(row)
        }

        return Timetable(
            grade = grade,
            klass = klass,
            days = days,
            periodCounts = periodCounts,
            periodTimes = context?.let {
                val code = schoolCode ?: AppSettings.school(it).code
                AppSettings.resolvedPeriodTimes(it, code, serverPeriodTimes)
            } ?: serverPeriodTimes,
            sourceUpdatedAt = sourceUpdatedAt,
            weekStart = weekStartDate,
            weekLabel = week?.label.orEmpty(),
        )
    }

    // MARK: 원본 인코딩 해석

    private fun teacherIndex(merged: Int, div: Int) = if (div == 100) merged / div else merged % div

    private fun subjectIndex(merged: Int, div: Int) = if (div == 100) merged % div else merged / div

    /** 과목 번호에 실려 있는 동시수업 그룹(A_, B_ …) 접두어. */
    private fun timePrefix(subject: Int, div: Int): String {
        if (div == 100) return ""
        val group = subject / div
        return if (group in 1..26) groupLetter(group) else ""
    }

    private fun classroom(grade: Int, klass: Int, day: Int, period: Int): String {
        if (!usesClassroom) return ""
        val info = value("자료245", grade, klass, day, period) as? String ?: return ""
        val separator = info.indexOf('_')
        if (separator <= 0) return ""
        val number = info.substring(0, separator).toIntOrNull() ?: 0
        if (number <= 0) return ""
        return info.substring(separator + 1)
    }

    /** 동시수업 그룹표(동시그룹)에서 이 칸이 속한 그룹을 찾는다. */
    private fun groupCode(grade: Int, klass: Int, subject: Int, day: Int, period: Int): String {
        val div = division
        val groups = root.optJSONArray("동시그룹") ?: return ""
        val groupCount = numeric((groups.opt(0) as? JSONArray)?.opt(0))
        if (groupCount <= 0) return ""

        for (i in 1..groupCount) {
            val inner = groups.opt(i) as? JSONArray ?: continue
            val innerCount = numeric(inner.opt(0))
            if (innerCount <= 0) continue

            for (pass in 1..2) {
                var matched = false
                var currentGroup = 0

                for (j in 1..innerCount) {
                    if (j >= inner.length()) continue
                    val groupValue = numeric(inner.opt(j))

                    val subject4 = groupValue / 1000
                    val group2 = subject4 / 1000
                    val subject2 = subject4 - group2 * 1000
                    val teacher = group2 / 100
                    val group = group2 - teacher * 100
                    val classroom = groupValue - subject4 * 1000
                    val grade2 = classroom / 100
                    val class2 = classroom - grade2 * 100

                    val rawValue = numeric(value("자료147", grade2, class2, day, period))
                    val subject3 = rawValue / div
                    val teacher2 = rawValue - subject3 * div

                    if (pass == 1) {
                        if (!(subject2 == subject3 && teacher == teacher2)) {
                            matched = false
                            break
                        }
                        if (grade == grade2 && klass == class2 && subject == subject2 &&
                            teacher == teacher2 && group > 0
                        ) {
                            matched = true
                            currentGroup = group
                        }
                    } else {
                        if (subject2 != subject3) {
                            matched = false
                            break
                        }
                        if (grade == grade2 && klass == class2 && subject == subject2 && group > 0) {
                            matched = true
                            currentGroup = group
                        }
                    }
                }

                if (matched) return groupLetter(currentGroup)
            }
        }
        return ""
    }

    private fun groupLetter(group: Int): String = "${(group + 64).toChar()}_"

    /**
     * 교사명의 별표를 떼어낸다(열람 페이지 Q성명과 동일).
     * 이름 가운데에 별표가 있거나 "김*" 형태면 그대로 두고, 그 외에는 첫 별표만 지운다.
     */
    private fun teacherName(raw: String): String {
        if (raw.isEmpty()) return ""
        val keepAsIs = (raw.contains('*') && !raw.startsWith("*") && !raw.endsWith("*")) ||
            (raw.length == 2 && raw[1] == '*')
        return if (keepAsIs) raw else raw.replaceFirst("*", "")
    }

    // MARK: JSON 탐색

    private fun value(key: String, vararg path: Int): Any? {
        var current: Any? = root.opt(key)
        for (index in path) {
            val array = current as? JSONArray ?: return null
            if (index < 0 || index >= array.length()) return null
            current = array.opt(index)
        }
        return if (current == JSONObject.NULL) null else current
    }

    companion object {
        private const val MAX_PERIOD = 8
        private const val MAX_DAY = 5

        /** 숫자 또는 ">31033" 형태의 변경 표기 문자열을 정수로 바꾼다. */
        fun numeric(any: Any?): Int = when (any) {
            is Number -> any.toInt()
            is String -> {
                val trimmed = if (any.startsWith(">")) any.substring(1) else any
                trimmed.toIntOrNull() ?: trimmed.toDoubleOrNull()?.toInt() ?: 0
            }
            else -> 0
        }
    }
}
