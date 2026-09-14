package com.yftech.comsigan

import com.yftech.comsigan.data.ComciDocument
import com.yftech.comsigan.data.DayPhase
import com.yftech.comsigan.data.Timetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * 실제 컴시간 응답으로 "지금 무슨 시간인지"와 "다음 수업이 뭔지"를 확인한다.
 * 자료의 일과시간은 09:00·10:00·11:00·12:00·13:50·14:50·15:50·16:50이라
 * 4교시 뒤 60분이 점심, 나머지 사이가 10분 쉬는시간이다.
 */
class DaySegmentTest {

    private val document: ComciDocument by lazy {
        val json = javaClass.classLoader!!.getResourceAsStream("comci-29175.json")!!
            .bufferedReader().readText()
        ComciDocument(json)
    }

    /** 자료가 담고 있는 주는 2026-08-10(월)부터다. */
    private val thisWeek: Timetable by lazy { document.timetable(document.grades.first(), 1) }

    private val nextWeek: Timetable by lazy { thisWeek.copy(weekStart = "2026-08-17") }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.time

    @Test
    fun lessonLengthComesFromPeriodGaps() {
        assertEquals(50, thisWeek.lessonMinutes)
    }

    @Test
    fun phasesAcrossAMonday() {
        // 08:30 등교 전 → 1교시를 가리킨다.
        thisWeek.segment(at(2026, 8, 10, 8, 30)).let {
            assertEquals(DayPhase.BEFORE_CLASSES, it.phase)
            assertEquals(1, it.period)
            assertEquals("다음 1교시", it.headline)
        }

        // 09:30 1교시 수업 중.
        thisWeek.segment(at(2026, 8, 10, 9, 30)).let {
            assertEquals(DayPhase.LESSON, it.phase)
            assertEquals(1, it.period)
            assertEquals("지금 1교시", it.headline)
            assertNull(it.nextLabel)
        }

        // 09:55 — 1교시(09:50)가 끝나고 2교시(10:00) 전, 10분 쉬는시간.
        thisWeek.segment(at(2026, 8, 10, 9, 55)).let {
            assertEquals(DayPhase.SHORT_BREAK, it.phase)
            assertEquals(1, it.previousPeriod)
            assertEquals(2, it.period)
            assertEquals("지금 쉬는시간", it.headline)
            assertEquals("다음 2교시", it.nextLabel)
        }

        // 13:00 — 4교시(12:50)와 5교시(13:50) 사이 60분이라 점심.
        thisWeek.segment(at(2026, 8, 10, 13, 0)).let {
            assertEquals(DayPhase.LUNCH, it.phase)
            assertEquals(5, it.period)
            assertEquals("점심시간", it.headline)
            assertEquals("다음 5교시", it.nextLabel)
        }

        // 쉬는시간에는 '지금 교시'가 없다 — 그래야 다음 수업을 가리킨다.
        assertNull(thisWeek.currentPeriod(at(2026, 8, 10, 9, 55)))
        assertEquals(2, thisWeek.focusPeriod(at(2026, 8, 10, 9, 55)))

        // 22:00 일과 끝.
        assertEquals(DayPhase.DONE, thisWeek.segment(at(2026, 8, 10, 22, 0)).phase)
    }

    @Test
    fun fridayAfterSchoolLeadsToNextMondayNotThisMonday() {
        val friday = at(2026, 8, 14, 22, 0)

        // 이 표만 보면 금요일 뒤에는 아무것도 없다(예전에는 이번 주 월요일로 되감겼다).
        assertTrue(thisWeek.upcoming(friday, afterPeriod = Timetable.SKIP_TODAY).isEmpty())

        val ahead = Timetable.upcoming(thisWeek, nextWeek, friday, Timetable.SKIP_TODAY, limit = 2)
        assertEquals(2, ahead.size)
        assertTrue("월요일부터여야 한다", ahead.all { it.day == 0 })
        assertEquals(1, ahead.first().period)
        assertEquals(1, ahead.first().weekOffset)
        assertTrue(ahead.first().label.startsWith("다음 주 월 1교시"))
    }

    @Test
    fun weekendAlsoLeadsToNextMonday() {
        val saturday = at(2026, 8, 15, 10, 0)
        val ahead = Timetable.upcoming(thisWeek, nextWeek, saturday, Timetable.SKIP_TODAY, limit = 1)
        assertEquals(1, ahead.size)
        assertEquals(0, ahead.first().day)
        assertEquals(1, ahead.first().weekOffset)
    }

    @Test
    fun weekAlreadyRolledOverIsUsedAsIs() {
        // 주말에 서버가 벌써 다음 주 표를 '이번 주'로 주는 경우.
        val sunday = at(2026, 8, 16, 10, 0)
        val ahead = Timetable.upcoming(nextWeek, null, sunday, Timetable.SKIP_TODAY, limit = 1)
        assertEquals(1, ahead.size)
        assertEquals(0, ahead.first().day)
    }

    @Test
    fun documentExposesWeeks() {
        assertEquals(1, document.todayWeek)
        assertEquals("2026-08-10", document.weekStartDate)
        assertEquals(listOf(1, 2), document.weeks.map { it.r })
        assertEquals("26-08-10 ~ 26-08-15", thisWeek.weekLabel)
    }
}
