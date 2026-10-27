package com.gsminulx.gsmwatch.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 학급 봇 D-Day 일정의 마감일(자유 형식 문자열) 해석과 정렬·긴급도 (워치/폰 공용).
 * 봇이 주는 deadline 은 "2026-10-30", "2026.10.30", "10/30", "10월 30일", "미정" 등 형식이 섞여 있을 수 있다.
 */
object Dday {

    enum class Urgency { URGENT, SOON, NORMAL, OVERDUE, UNKNOWN }

    data class Item(val task: TaskInfo, val date: LocalDate?, val days: Int?) {
        val urgency: Urgency get() = urgencyOf(days)
        /** "D-3" / "D-Day" / "D+2", 날짜를 모르면 원문(또는 "기한 미정"). */
        val badge: String get() = days?.let { label(it) } ?: task.deadline.ifBlank { "미정" }.let { if (it == "미정") "기한 미정" else it }
    }

    private val fullDate = Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})\s*[-./월]\s*(\d{1,2})""")
    private val compactDate = Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)""")
    private val monthDay = Regex("""(?<!\d)(\d{1,2})\s*[-./월]\s*(\d{1,2})(?!\d)""")

    /** 마감 문자열 → 날짜. 연도가 없으면 [today] 기준 가까운 날짜(반년 이상 지난 날짜면 내년)로 본다. */
    fun parse(deadline: String, today: LocalDate): LocalDate? {
        val s = deadline.trim()
        if (s.isEmpty() || s == "미정") return null
        (fullDate.find(s) ?: compactDate.find(s))?.let { m ->
            val (y, mo, d) = m.destructured
            return dateOrNull(y.toInt(), mo.toInt(), d.toInt())
        }
        monthDay.find(s)?.let { m ->
            val (mo, d) = m.destructured
            val candidate = dateOrNull(today.year, mo.toInt(), d.toInt()) ?: return null
            return if (candidate.isBefore(today.minusDays(180))) candidate.plusYears(1) else candidate
        }
        return null
    }

    fun daysUntil(deadline: String, today: LocalDate): Int? =
        parse(deadline, today)?.let { ChronoUnit.DAYS.between(today, it).toInt() }

    fun label(days: Int): String = when {
        days == 0 -> "D-Day"
        days > 0 -> "D-$days"
        else -> "D+${-days}"
    }

    fun urgencyOf(days: Int?): Urgency = when {
        days == null -> Urgency.UNKNOWN
        days < 0 -> Urgency.OVERDUE
        days <= 1 -> Urgency.URGENT
        days <= 3 -> Urgency.SOON
        else -> Urgency.NORMAL
    }

    /** 다가오는 일정(가까운 순) → 기한 미정 → 지난 일정(최근 순). 같은 순위는 원래 순서를 유지한다. */
    fun sort(tasks: List<TaskInfo>, today: LocalDate): List<Item> {
        val items = tasks.map { t ->
            val date = parse(t.deadline, today)
            Item(t, date, date?.let { ChronoUnit.DAYS.between(today, it).toInt() })
        }
        val upcoming = items.filter { it.days != null && it.days >= 0 }.sortedBy { it.days }
        val unknown = items.filter { it.days == null }
        val overdue = items.filter { it.days != null && it.days < 0 }.sortedByDescending { it.days }
        return upcoming + unknown + overdue
    }

    private fun dateOrNull(y: Int, m: Int, d: Int): LocalDate? =
        try { LocalDate.of(y, m, d) } catch (e: Exception) { null }
}
