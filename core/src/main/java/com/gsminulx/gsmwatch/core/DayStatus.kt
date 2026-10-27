package com.gsminulx.gsmwatch.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * '지금' 학생 일과 상태 (워치 홈 / 타일 / 폰 '지금' 카드 공용).
 * 화면마다 문자열을 조립하던 방식 대신 구조화된 상태를 주고, 표시는 각 UI 가 담당한다.
 */
sealed interface DayStatus {
    /** 카운트다운 목표 시각. */
    val target: LocalDateTime

    /** 목표 시각까지 남은 시간 (음수 없음). */
    val remaining: Duration

    /** 현재 구간의 시작 시각 (알 수 없으면 null). 진행률 = (now - start) / (target - start). */
    val start: LocalDateTime?

    /** 현재 구간의 경과 비율 0..1 (구간 시작을 알 수 없으면 null). */
    val progress: Float?

    /** 교시/일과 진행 중 → [period] 종료까지. */
    data class InPeriod(
        val period: Period,
        override val start: LocalDateTime,
        override val target: LocalDateTime,
        override val remaining: Duration,
        override val progress: Float
    ) : DayStatus

    /** 일과 사이 빈 시간(새벽·쉬는 틈·점호 이후) → [next] 시작까지. */
    data class UntilNext(
        val next: Period,
        override val start: LocalDateTime?,
        override val target: LocalDateTime,
        override val remaining: Duration,
        override val progress: Float?
    ) : DayStatus

    /** 주말(금요일 종례 이후 포함) → 일요일 기숙사 입소까지. */
    data class WeekendDorm(
        override val start: LocalDateTime,
        override val target: LocalDateTime,
        override val remaining: Duration,
        override val progress: Float?
    ) : DayStatus
}

/** "3교시 · 수학", "점심 시간", "기숙사 입소" 처럼 상태의 대상 이름. */
val DayStatus.label: String
    get() = when (this) {
        is DayStatus.InPeriod -> period.displayName
        is DayStatus.UntilNext -> next.displayName
        is DayStatus.WeekendDorm -> "기숙사 입소"
    }

/** 남은 시간 앞에 붙는 설명 ("종료까지" / "시작까지" / "입소까지"). */
val DayStatus.caption: String
    get() = when (this) {
        is DayStatus.InPeriod -> "종료까지"
        is DayStatus.UntilNext -> "시작까지"
        is DayStatus.WeekendDorm -> "입소까지"
    }

/** 교시 번호 ("3교시" → 3). 교시가 아니면 null. */
val Period.number: Int?
    get() = if (name.endsWith("교시")) name.removeSuffix("교시").toIntOrNull() else null

/** 과목이 있으면 "3교시 · 수학", 없으면 이름 그대로. */
val Period.displayName: String
    get() = if (subject.isNotBlank()) "$name · $subject" else name

object DayStatusCalculator {

    /** 학년별 일요일 기숙사 입소 시각. */
    fun dormEntryTime(grade: Int): LocalTime = when (grade) {
        1 -> LocalTime.of(20, 20)
        2 -> LocalTime.of(20, 40)
        else -> LocalTime.of(21, 0)
    }

    /** 금요일은 8교시 이후 수업이 없으므로(시간표에서도 숨김) 8교시 시작 시각부터 주말로 본다. */
    val fridayDismissal: LocalTime =
        Schedule.baseSchedule.first { it.name == "8교시" }.start

    /**
     * [now] 시점의 상태를 계산한다.
     * @param todaySchedule 오늘 표시용 시간표(과목 포함). 비어 있으면 기본 일과표를 쓴다.
     */
    fun statusAt(now: LocalDateTime, todaySchedule: List<Period>, grade: Int): DayStatus {
        val schedule = todaySchedule.ifEmpty { Schedule.baseSchedule }.sortedBy { it.start }
        val date = now.toLocalDate()
        val day = date.dayOfWeek
        val time = now.toLocalTime()

        // 주말: 금요일 종례 이후 ~ 일요일 입소 전
        val isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ||
            (day == DayOfWeek.FRIDAY && !time.isBefore(fridayDismissal))
        if (isWeekend) {
            val sunday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val dormTarget = sunday.atTime(dormEntryTime(grade))
            if (now.isBefore(dormTarget)) {
                val fridayStart = sunday.minusDays(2).atTime(fridayDismissal)
                return DayStatus.WeekendDorm(fridayStart, dormTarget, between(now, dormTarget), progress(fridayStart, now, dormTarget))
            }
            // 일요일 입소 이후 → 월요일 기상까지
            return untilFirstOf(date.plusDays(1), from = dormTarget, now = now)
        }

        // 진행 중인 일과
        schedule.firstOrNull { !time.isBefore(it.start) && time.isBefore(it.end) }?.let { p ->
            val start = date.atTime(p.start)
            val end = date.atTime(p.end)
            return DayStatus.InPeriod(p, start, end, between(now, end), progress(start, now, end) ?: 0f)
        }

        // 오늘 남은 다음 일과
        schedule.firstOrNull { it.start.isAfter(time) }?.let { next ->
            val prevEnd = schedule.lastOrNull { !it.end.isAfter(time) }?.let { date.atTime(it.end) }
                ?: previousDayEnd(date, grade)
            val target = date.atTime(next.start)
            return DayStatus.UntilNext(next, prevEnd, target, between(now, target), progress(prevEnd, now, target))
        }

        // 오늘 일과 종료(점호 이후) → 내일 기상까지
        val lastEnd = schedule.lastOrNull()?.let { date.atTime(it.end) }
        return untilFirstOf(date.plusDays(1), from = lastEnd, now = now)
    }

    /** [date] 의 첫 일과(기상) 시작까지. */
    private fun untilFirstOf(date: LocalDate, from: LocalDateTime?, now: LocalDateTime): DayStatus.UntilNext {
        val first = Schedule.baseSchedule.minBy { it.start }
        val target = date.atTime(first.start)
        return DayStatus.UntilNext(first, from, target, between(now, target), from?.let { progress(it, now, target) })
    }

    /** 새벽(기상 전) 구간의 시작: 전날 마지막 일과 종료, 월요일이면 일요일 기숙사 입소. */
    private fun previousDayEnd(date: LocalDate, grade: Int): LocalDateTime {
        val prev = date.minusDays(1)
        return if (prev.dayOfWeek == DayOfWeek.SUNDAY) {
            prev.atTime(dormEntryTime(grade))
        } else {
            prev.atTime(Schedule.baseSchedule.maxOf { it.end })
        }
    }

    private fun between(from: LocalDateTime, to: LocalDateTime): Duration =
        Duration.between(from, to).let { if (it.isNegative) Duration.ZERO else it }

    private fun progress(start: LocalDateTime, now: LocalDateTime, end: LocalDateTime): Float? {
        val total = Duration.between(start, end).seconds
        if (total <= 0) return null
        val elapsed = Duration.between(start, now).seconds
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }
}

/** 남은 시간 표기 헬퍼. */
object RemainingFormat {
    /** 숫자 시계형: 1시간 이상이면 "1:05:09", 아니면 "05:09". */
    fun clock(d: Duration): String {
        val total = d.seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * 한국어 표기: "1일 3시간", "1시간 05분", "12분" (초는 [withSeconds] 일 때만, 1시간 미만에서).
     * 초를 생략할 때는 분을 올림한다(30초 남음 → "1분") — "0분" 이 보이지 않도록.
     */
    fun korean(d: Duration, withSeconds: Boolean = false): String {
        val raw = d.seconds.coerceAtLeast(0)
        val total = if (withSeconds || raw % 60 == 0L) raw else raw - raw % 60 + 60
        val days = total / 86_400
        val h = (total % 86_400) / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            days > 0 -> if (h > 0) "${days}일 ${h}시간" else "${days}일"
            h > 0 -> "${h}시간 %02d분".format(m)
            withSeconds -> "${m}분 %02d초".format(s)
            else -> "${m}분"
        }
    }
}
