package com.gsminulx.gsmwatch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DayStatusTest {

    // 2026-10-26 은 월요일
    private val monday = LocalDate.of(2026, 10, 26)
    private val friday = monday.plusDays(4)
    private val saturday = monday.plusDays(5)
    private val sunday = monday.plusDays(6)

    private val schedule = Schedule.baseSchedule.map { if (it.name == "3교시") it.copy(subject = "수학") else it }

    private fun status(date: LocalDate, h: Int, m: Int, s: Int = 0, grade: Int = 1) =
        DayStatusCalculator.statusAt(LocalDateTime.of(date, LocalTime.of(h, m, s)), schedule, grade)

    @Test
    fun inPeriod_countsDownToEnd_withSubjectLabel() {
        val st = status(monday, 10, 50) as DayStatus.InPeriod
        assertEquals("3교시", st.period.name)
        assertEquals("3교시 · 수학", st.label)
        assertEquals("종료까지", st.caption)
        assertEquals(Duration.ofMinutes(40), st.remaining)
        assertEquals(10f / 50f, st.progress, 0.001f)
    }

    @Test
    fun periodStartIsInclusive_endIsExclusive() {
        assertEquals("1교시", (status(monday, 8, 40) as DayStatus.InPeriod).period.name)
        assertEquals("쉬는 시간", (status(monday, 9, 30) as DayStatus.InPeriod).period.name)
    }

    @Test
    fun beforeWakeUp_countsDownToWakeUp() {
        val st = status(LocalDate.of(2026, 10, 27), 5, 30) as DayStatus.UntilNext
        assertEquals("기상 시간", st.next.name)
        assertEquals(Duration.ofHours(1), st.remaining)
        // 전날 점호 종료(22:10) ~ 기상(06:30) 구간 진행률
        val total = Duration.ofHours(8).plusMinutes(20).seconds.toFloat()
        val elapsed = Duration.ofHours(7).plusMinutes(20).seconds.toFloat()
        assertEquals(elapsed / total, st.progress!!, 0.001f)
    }

    @Test
    fun mondayMorning_progressStartsFromSundayDormEntry() {
        val st = status(monday, 6, 0, grade = 3) as DayStatus.UntilNext
        assertEquals("기상 시간", st.next.name)
        assertNotNull(st.progress)
    }

    @Test
    fun gapBeforeRollCall_countsDownToRollCall() {
        val st = status(monday, 21, 40) as DayStatus.UntilNext
        assertEquals("점호", st.next.name)
        assertEquals(Duration.ofMinutes(20), st.remaining)
        assertEquals(10f / 30f, st.progress!!, 0.001f)
    }

    @Test
    fun afterRollCall_countsDownToTomorrowWakeUp() {
        val st = status(monday, 23, 0) as DayStatus.UntilNext
        assertEquals("기상 시간", st.next.name)
        assertEquals(LocalDateTime.of(monday.plusDays(1), LocalTime.of(6, 30)), st.target)
        assertEquals(Duration.ofHours(7).plusMinutes(30), st.remaining)
    }

    @Test
    fun fridayBeforeDismissal_isRegularDay() {
        val st = status(friday, 16, 30) as DayStatus.InPeriod
        assertEquals("청소 및 종례", st.period.name)
    }

    @Test
    fun fridayAfterDismissal_switchesToDormCountdown() {
        val st = status(friday, 16, 40, grade = 2) as DayStatus.WeekendDorm
        assertEquals(LocalDateTime.of(sunday, LocalTime.of(20, 40)), st.target)
        assertEquals("기숙사 입소", st.label)
        assertEquals(0f, st.progress!!, 0.001f)
    }

    @Test
    fun weekend_targetsSundayDormEntryByGrade() {
        assertEquals(LocalTime.of(20, 20), (status(saturday, 12, 0, grade = 1) as DayStatus.WeekendDorm).target.toLocalTime())
        assertEquals(LocalTime.of(20, 40), (status(saturday, 12, 0, grade = 2) as DayStatus.WeekendDorm).target.toLocalTime())
        assertEquals(LocalTime.of(21, 0), (status(sunday, 12, 0, grade = 3) as DayStatus.WeekendDorm).target.toLocalTime())
    }

    @Test
    fun sundayAfterDormEntry_countsDownToMondayWakeUp() {
        val st = status(sunday, 21, 0, grade = 1) as DayStatus.UntilNext
        assertEquals("기상 시간", st.next.name)
        assertEquals(LocalDateTime.of(sunday.plusDays(1), LocalTime.of(6, 30)), st.target)
    }

    @Test
    fun emptySchedule_fallsBackToBaseSchedule() {
        val st = DayStatusCalculator.statusAt(LocalDateTime.of(monday, LocalTime.of(13, 0)), emptyList(), 1)
        assertEquals("점심 시간", (st as DayStatus.InPeriod).period.name)
    }

    @Test
    fun periodNumber_onlyForNumberedPeriods() {
        assertEquals(3, Period("3교시", LocalTime.NOON, LocalTime.NOON).number)
        assertEquals(10, Period("10교시", LocalTime.NOON, LocalTime.NOON).number)
        assertNull(Period("점심 시간", LocalTime.NOON, LocalTime.NOON).number)
    }

    @Test
    fun remainingFormat() {
        assertEquals("05:09", RemainingFormat.clock(Duration.ofSeconds(309)))
        assertEquals("1:05:09", RemainingFormat.clock(Duration.ofSeconds(3909)))
        assertEquals("1시간 05분", RemainingFormat.korean(Duration.ofMinutes(65)))
        assertEquals("12분", RemainingFormat.korean(Duration.ofMinutes(12)))
        assertEquals("1분", RemainingFormat.korean(Duration.ofSeconds(30)))
        assertEquals("12분 30초", RemainingFormat.korean(Duration.ofSeconds(750), withSeconds = true))
        assertEquals("1일 3시간", RemainingFormat.korean(Duration.ofHours(27)))
        assertTrue(RemainingFormat.clock(Duration.ofSeconds(-5)) == "00:00")
    }
}
