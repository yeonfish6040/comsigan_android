package com.yftech.comsigan.data

import java.util.Calendar
import java.util.Date

/** 한 교시에 해당하는 수업. */
data class TimetableCell(
    val subject: String = "",
    val teacher: String = "",
    val room: String = "",
    /** 동시수업(그룹) 접두어. 예: "A_" */
    val group: String = "",
    /** 변경/보강된 칸. */
    val isChanged: Boolean = false,
) {
    val isEmpty: Boolean get() = subject.isEmpty()

    /** "A_체육" 형태의 표시용 과목명. */
    val displaySubject: String get() = group + subject

    companion object {
        val EMPTY = TimetableCell()
    }
}

data class PeriodTime(val period: Int, val hour: Int, val minute: Int) {
    val label: String get() = "%02d:%02d".format(hour, minute)

    /** 오늘 날짜 기준 이 교시의 시작 시각. */
    fun startOn(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}

data class Timetable(
    val grade: Int,
    val klass: Int,
    /** [요일][교시] — 요일 0 = 월요일, 교시 0 = 1교시. */
    val days: List<List<TimetableCell>>,
    /** 요일별 수업 교시 수. */
    val periodCounts: List<Int>,
    val periodTimes: List<PeriodTime>,
    /** 학교 시간표가 마지막으로 갱신된 시각(자료244). */
    val sourceUpdatedAt: String,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val maxPeriod: Int get() = maxOf(periodCounts.maxOrNull() ?: 0, 1)

    fun cell(day: Int, period: Int): TimetableCell =
        days.getOrNull(day)?.getOrNull(period - 1) ?: TimetableCell.EMPTY

    fun periodCount(day: Int): Int = periodCounts.getOrNull(day) ?: 0

    fun periodTime(period: Int): PeriodTime? = periodTimes.firstOrNull { it.period == period }

    /** 지금 진행 중인 교시 번호(1-base). 수업 시간이 아니면 null. */
    fun currentPeriod(now: Date = Date()): Int? {
        val day = dayIndex(now) ?: return null
        val count = periodCount(day)
        if (count <= 0) return null

        for (period in 1..count) {
            val start = periodTime(period)?.startOn(now) ?: continue
            val next = if (period < count) periodTime(period + 1)?.startOn(now) else null
            val end = next ?: Date(start.time + 50 * 60 * 1000L)
            if (now >= start && now < end) return period
        }
        return null
    }

    /** 오늘 남은 수업 중 다음 교시 번호. */
    fun nextPeriod(now: Date = Date()): Int? {
        val day = dayIndex(now) ?: return null
        val count = periodCount(day)
        if (count <= 0) return null

        for (period in 1..count) {
            val start = periodTime(period)?.startOn(now) ?: continue
            if (start > now) return period
        }
        return null
    }

    /** 앞으로 있을 수업. 오늘 남은 교시를 먼저 채우고, 없으면 다음 등교일로 넘어간다. */
    fun upcoming(now: Date = Date(), afterPeriod: Int = 0, limit: Int = 3): List<UpcomingClass> {
        val today = dayIndex(now)
        val startDay = today ?: 0
        val result = mutableListOf<UpcomingClass>()

        for (offset in 0 until DAY_NAMES.size) {
            val day = (startDay + offset) % DAY_NAMES.size
            val from = if (offset == 0 && today != null) afterPeriod + 1 else 1
            for (period in from..periodCount(day)) {
                val cell = cell(day, period)
                if (cell.isEmpty) continue
                result.add(UpcomingClass(day, period, cell, isToday = offset == 0 && today != null))
                if (result.size >= limit) return result
            }
        }
        return result
    }

    companion object {
        val DAY_NAMES = listOf("월", "화", "수", "목", "금")

        /** 요일 인덱스(월=0). 주말이면 null. */
        fun dayIndex(date: Date = Date()): Int? {
            val calendar = Calendar.getInstance()
            calendar.time = date
            val index = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
            return if (index in 0..4) index else null
        }
    }
}

/** 위젯이 "다음 수업"을 보여줄 때 쓰는 항목. */
data class UpcomingClass(
    val day: Int,
    val period: Int,
    val cell: TimetableCell,
    val isToday: Boolean,
) {
    /** "7교시" 또는 "금 1교시". */
    val label: String
        get() = if (isToday) "${period}교시" else "${Timetable.DAY_NAMES[day]} ${period}교시"
}

data class School(val code: Int, val name: String, val region: String = "")

class ComciException(message: String) : Exception(message)
