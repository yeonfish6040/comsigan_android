package com.yftech.comsigan.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

/** 하루를 잘랐을 때 지금 서 있는 자리. */
enum class DayPhase {
    /** 1교시 시작 전. */
    BEFORE_CLASSES,

    /** 수업 중. */
    LESSON,

    /** 수업과 수업 사이 쉬는시간. */
    SHORT_BREAK,

    /** 점심시간(쉬는시간보다 뚜렷하게 긴 사이). */
    LUNCH,

    /** 오늘 일과가 끝났거나 오늘은 수업이 없다. */
    DONE,
}

/** 지금이 어느 구간인지와, 그 구간에서 가리킬 교시. */
data class DaySegment(
    val phase: DayPhase,
    /** 수업 중이면 그 교시, 쉬는시간·점심·등교 전이면 이어질 교시. */
    val period: Int? = null,
    /** 방금 끝난 교시(쉬는시간·점심에만). */
    val previousPeriod: Int? = null,
    /** 이 구간이 끝나는 시각. */
    val end: Date? = null,
) {
    val isBreak: Boolean get() = phase == DayPhase.SHORT_BREAK || phase == DayPhase.LUNCH

    /** 머리글에 쓰는 지금 상태. 예: "지금 3교시", "지금 쉬는시간", "점심시간", "다음 1교시" */
    val headline: String?
        get() = when (phase) {
            DayPhase.LESSON -> period?.let { "지금 ${it}교시" }
            DayPhase.SHORT_BREAK -> "지금 쉬는시간"
            DayPhase.LUNCH -> "점심시간"
            DayPhase.BEFORE_CLASSES -> period?.let { "다음 ${it}교시" }
            DayPhase.DONE -> null
        }

    /** 쉬는시간·점심에만 덧붙는 다음 교시 표기. 예: "다음 5교시" */
    val nextLabel: String? get() = if (isBreak && period != null) "다음 ${period}교시" else null
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
    /** 이 표가 담고 있는 주의 월요일. 예: "2026-08-24" */
    val weekStart: String = "",
    /** 서버가 준 기간 표기. 예: "26-08-17 ~ 26-08-22" */
    val weekLabel: String = "",
) {
    val maxPeriod: Int get() = maxOf(periodCounts.maxOrNull() ?: 0, 1)

    fun cell(day: Int, period: Int): TimetableCell =
        days.getOrNull(day)?.getOrNull(period - 1) ?: TimetableCell.EMPTY

    fun periodCount(day: Int): Int = periodCounts.getOrNull(day) ?: 0

    fun periodTime(period: Int): PeriodTime? = periodTimes.firstOrNull { it.period == period }

    /**
     * 수업 한 교시의 길이(분). 서버는 시작 시각만 주므로
     * 연속한 교시의 시작 간격에서 쉬는시간을 뺀 값으로 본다.
     */
    val lessonMinutes: Int
        get() {
            val sorted = periodTimes.sortedBy { it.period }
            val shortest = sorted.zipWithNext()
                .filter { (previous, next) -> next.period == previous.period + 1 }
                .map { (previous, next) -> (next.hour * 60 + next.minute) - (previous.hour * 60 + previous.minute) }
                .filter { it > 0 }
                .minOrNull() ?: return DEFAULT_LESSON_MINUTES
            return (shortest - BREAK_MINUTES).coerceIn(30, 70)
        }

    /** 그날 각 교시가 차지하는 시간대. 시각을 모르는 교시는 빠진다. */
    fun periodSpans(now: Date = Date()): List<PeriodSpan> {
        val day = dayIndex(now) ?: return emptyList()
        val count = periodCount(day)
        if (count <= 0) return emptyList()

        val length = lessonMinutes * 60 * 1000L
        return (1..count).mapNotNull { period ->
            val start = periodTime(period)?.startOn(now) ?: return@mapNotNull null
            PeriodSpan(period, start, Date(start.time + length))
        }
    }

    /** 지금이 수업인지 쉬는시간인지 점심인지. */
    fun segment(now: Date = Date()): DaySegment {
        val spans = periodSpans(now)
        val first = spans.firstOrNull() ?: return DaySegment(DayPhase.DONE)
        val last = spans.last()

        if (now < first.start) {
            return DaySegment(DayPhase.BEFORE_CLASSES, period = first.period, end = first.start)
        }
        if (now >= last.end) return DaySegment(DayPhase.DONE)

        spans.forEachIndexed { index, span ->
            if (now >= span.start && now < span.end) {
                return DaySegment(DayPhase.LESSON, period = span.period, end = span.end)
            }
            val next = spans.getOrNull(index + 1) ?: return@forEachIndexed
            if (now < span.end || now >= next.start) return@forEachIndexed
            val gapMinutes = (next.start.time - span.end.time) / 60_000L
            return DaySegment(
                phase = if (gapMinutes >= LUNCH_MINUTES) DayPhase.LUNCH else DayPhase.SHORT_BREAK,
                period = next.period,
                previousPeriod = span.period,
                end = next.start,
            )
        }
        return DaySegment(DayPhase.DONE)
    }

    /** 지금 진행 중인 교시 번호(1-base). 쉬는시간·점심이면 null. */
    fun currentPeriod(now: Date = Date()): Int? =
        segment(now).takeIf { it.phase == DayPhase.LESSON }?.period

    /** 지금(없으면 다음) 교시. */
    fun focusPeriod(now: Date = Date()): Int? = currentPeriod(now) ?: nextPeriod(now)

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

    /**
     * 이 표 안에서 앞으로 있을 수업. 금요일을 지나면 더 찾지 않는다 —
     * 그 다음은 이 표가 아니라 다음 주 표에 들어 있다.
     */
    fun upcoming(now: Date = Date(), afterPeriod: Int = 0, limit: Int = 3): List<UpcomingClass> {
        val today = dayIndex(now) ?: return emptyList()
        return classes(fromDay = today, fromPeriod = afterPeriod + 1, today = today, weekOffset = 0, limit = limit)
    }

    /** 월요일 1교시부터 채운다. 아직 시작하지 않은 주의 표에 쓴다. */
    fun upcomingFromMonday(limit: Int = 3, weekOffset: Int = 1): List<UpcomingClass> =
        classes(fromDay = 0, fromPeriod = 1, today = null, weekOffset = weekOffset, limit = limit)

    private fun classes(
        fromDay: Int,
        fromPeriod: Int,
        today: Int?,
        weekOffset: Int,
        limit: Int,
    ): List<UpcomingClass> {
        if (limit <= 0) return emptyList()
        val result = mutableListOf<UpcomingClass>()

        for (day in fromDay until DAY_NAMES.size) {
            val from = maxOf(if (day == fromDay) fromPeriod else 1, 1)
            for (period in from..periodCount(day)) {
                val cell = cell(day, period)
                if (cell.isEmpty) continue
                result.add(UpcomingClass(day, period, cell, isToday = day == today, weekOffset = weekOffset))
                if (result.size >= limit) return result
            }
        }
        return result
    }

    /** 이 표가 담은 주가 아직 시작하지 않았는지. 시작일을 모르면 null. */
    fun startsAfter(now: Date = Date()): Boolean? {
        val monday = runCatching { WEEK_START_FORMAT.parse(weekStart) }.getOrNull() ?: return null
        return startOfDay(monday) > startOfDay(now)
    }

    companion object {
        val DAY_NAMES = listOf("월", "화", "수", "목", "금")

        /** 수업 사이 쉬는시간(분). */
        const val BREAK_MINUTES = 10

        /** 이보다 긴 사이는 점심시간으로 본다. */
        const val LUNCH_MINUTES = 20

        /** 시작 시각만 알 때 쓰는 기본 수업 길이(분). */
        const val DEFAULT_LESSON_MINUTES = 50

        /** 교시 번호보다 크면 오늘은 더 볼 게 없다는 뜻. */
        const val SKIP_TODAY = 99

        private val WEEK_START_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /** 요일 인덱스(월=0). 주말이면 null. */
        fun dayIndex(date: Date = Date()): Int? {
            val calendar = Calendar.getInstance()
            calendar.time = date
            val index = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
            return if (index in 0..4) index else null
        }

        /**
         * 이번 주 표와 다음 주 표를 이어 붙여 앞으로 있을 수업을 찾는다.
         * 금요일 방과 후처럼 이번 주에 남은 수업이 없으면 다음 주 월요일부터 이어진다.
         */
        fun upcoming(
            current: Timetable?,
            next: Timetable?,
            now: Date = Date(),
            afterPeriod: Int = SKIP_TODAY,
            limit: Int = 3,
        ): List<UpcomingClass> {
            if (current == null) return emptyList()
            // 주말에는 서버가 벌써 다음 주 표를 '이번 주'로 주기도 한다. 그때는 그 표의 월요일이 곧 다음 수업이다.
            if (current.startsAfter(now) == true) return current.upcomingFromMonday(limit)

            val result = current.upcoming(now, afterPeriod, limit).toMutableList()
            if (result.size < limit && next != null && next.startsAfter(now) != false) {
                result += next.upcomingFromMonday(limit - result.size)
            }
            return result
        }

        private fun startOfDay(date: Date): Date {
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
    }
}

/** 한 교시가 차지하는 시간대. */
data class PeriodSpan(val period: Int, val start: Date, val end: Date)

/** 위젯이 "다음 수업"을 보여줄 때 쓰는 항목. */
data class UpcomingClass(
    val day: Int,
    val period: Int,
    val cell: TimetableCell,
    val isToday: Boolean,
    /** 0이면 이번 주, 1이면 다음 주. */
    val weekOffset: Int = 0,
) {
    /** "7교시", "금 1교시", "다음 주 월 1교시". */
    val label: String
        get() {
            if (isToday) return "${period}교시"
            val dayLabel = "${Timetable.DAY_NAMES[day]} ${period}교시"
            return if (weekOffset > 0) "다음 주 $dayLabel" else dayLabel
        }
}

data class School(val code: Int, val name: String, val region: String = "")

class ComciException(message: String) : Exception(message)
