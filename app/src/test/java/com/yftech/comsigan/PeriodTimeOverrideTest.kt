package com.yftech.comsigan

import com.yftech.comsigan.data.AppSettings
import com.yftech.comsigan.data.ComciDocument
import com.yftech.comsigan.data.DayPhase
import com.yftech.comsigan.data.Timetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * 일과시간 보정이 걸렸을 때 하루가 어떻게 잘리는지 확인한다.
 * 한국디지털미디어고는 컴시간에 점심 이후가 :50 시작으로 올라와 있지만
 * 실제로는 :40에 시작한다(50분 수업 + 10분 쉬는시간).
 */
class PeriodTimeOverrideTest {

    private val document: ComciDocument by lazy {
        val json = javaClass.classLoader!!.getResourceAsStream("comci-29175.json")!!
            .bufferedReader().readText()
        ComciDocument(json)
    }

    /** 보정값을 얹은 표. 앱에서는 AppSettings.resolvedPeriodTimes가 이 자리를 채운다. */
    private val corrected: Timetable by lazy {
        val override = AppSettings.BUILT_IN_PERIOD_TIMES.getValue(29175)
        document.timetable(document.grades.first(), 1).copy(periodTimes = override)
    }

    private fun at(hour: Int, minute: Int): Date =
        Calendar.getInstance().apply {
            clear()
            // 자료가 담고 있는 주의 월요일.
            set(2026, 8 - 1, 10, hour, minute)
        }.time

    @Test
    fun serverTimesStillStartTheAfternoonTenMinutesLate() {
        // 보정이 필요한 이유 자체를 못 박아 둔다. 학교가 자료를 고치면 여기가 먼저 깨진다.
        val server = document.serverPeriodTimes.first { it.period == 5 }
        assertEquals(13, server.hour)
        assertEquals(50, server.minute)
    }

    @Test
    fun lessonLengthIsStillFiftyMinutes() {
        // 교시 간격이 60분으로 그대로라 쉬는시간 10분을 뺀 50분이 나와야 한다.
        assertEquals(50, corrected.lessonMinutes)
    }

    @Test
    fun lunchEndsAtOneForty() {
        // 4교시는 12:00~12:50, 그 뒤 13:40까지가 점심이다.
        corrected.segment(at(12, 49)).let {
            assertEquals(DayPhase.LESSON, it.phase)
            assertEquals(4, it.period)
        }
        corrected.segment(at(13, 0)).let {
            assertEquals(DayPhase.LUNCH, it.phase)
            assertEquals(5, it.period)
            assertEquals(4, it.previousPeriod)
            assertNotNull(it.end)
        }
        // 서버 값이었다면 아직 점심이었을 13:45에 이미 5교시다.
        corrected.segment(at(13, 45)).let {
            assertEquals(DayPhase.LESSON, it.phase)
            assertEquals(5, it.period)
        }
    }

    @Test
    fun afternoonRepeatsFiftyTenFromFortyPast() {
        // 5교시 13:40~14:30 → 쉬는시간 → 6교시 14:40~15:30 → 쉬는시간 → 7교시 15:40~16:30
        corrected.segment(at(14, 35)).let {
            assertEquals(DayPhase.SHORT_BREAK, it.phase)
            assertEquals(6, it.period)
            assertEquals(5, it.previousPeriod)
        }
        corrected.segment(at(14, 40)).let {
            assertEquals(DayPhase.LESSON, it.phase)
            assertEquals(6, it.period)
        }
        corrected.segment(at(15, 45)).let {
            assertEquals(DayPhase.LESSON, it.phase)
            assertEquals(7, it.period)
        }
        // 월요일은 7교시까지라 16:30이면 일과가 끝난다.
        assertEquals(DayPhase.DONE, corrected.segment(at(16, 30)).phase)
    }
}
