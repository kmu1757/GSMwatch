package com.gsminulx.gsmwatch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DdayTest {

    private val today = LocalDate.of(2026, 10, 27)

    private fun task(deadline: String, id: Int = 0) = TaskInfo(id, "수행평가", deadline, "내용$id")

    @Test
    fun parsesCommonFormats() {
        val expected = LocalDate.of(2026, 10, 30)
        listOf("2026-10-30", "2026.10.30", "2026/10/30", "2026년 10월 30일", "20261030", "2026-10-30T23:59:00", "10/30", "10.30", "10월 30일", "10월30일까지")
            .forEach { assertEquals(it, expected, Dday.parse(it, today)) }
    }

    @Test
    fun unknownOrInvalid_returnsNull() {
        assertNull(Dday.parse("미정", today))
        assertNull(Dday.parse("", today))
        assertNull(Dday.parse("다음주", today))
        assertNull(Dday.parse("2026-13-40", today))
    }

    @Test
    fun monthDayFarInThePast_rollsToNextYear() {
        assertEquals(LocalDate.of(2027, 1, 5), Dday.parse("1/5", today))
        // 최근에 지난 날짜는 올해로 본다(지난 일정)
        assertEquals(LocalDate.of(2026, 10, 20), Dday.parse("10/20", today))
    }

    @Test
    fun labelsAndUrgency() {
        assertEquals("D-Day", Dday.label(0))
        assertEquals("D-3", Dday.label(3))
        assertEquals("D+2", Dday.label(-2))
        assertEquals(Dday.Urgency.URGENT, Dday.urgencyOf(1))
        assertEquals(Dday.Urgency.SOON, Dday.urgencyOf(3))
        assertEquals(Dday.Urgency.NORMAL, Dday.urgencyOf(4))
        assertEquals(Dday.Urgency.OVERDUE, Dday.urgencyOf(-1))
        assertEquals(Dday.Urgency.UNKNOWN, Dday.urgencyOf(null))
    }

    @Test
    fun sort_upcomingThenUnknownThenOverdue() {
        val tasks = listOf(task("미정", 1), task("2026-11-05", 2), task("2026-10-20", 3), task("10/28", 4), task("2026-10-25", 5))
        assertEquals(listOf(4, 2, 1, 5, 3), Dday.sort(tasks, today).map { it.task.id })
    }

    @Test
    fun badge() {
        val items = Dday.sort(listOf(task("10/28", 1), task("미정", 2), task("곧", 3)), today)
        assertEquals(listOf("D-1", "기한 미정", "곧"), items.map { it.badge })
    }
}
