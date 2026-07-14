package com.gsminulx.gsmwatch.core

import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * GSM 일과(벨) 스케줄과 시간표 조립 로직 (워치/폰 공용).
 * NEIS 원시 시간표(요일→교시→과목)와 저장된 커스텀 과목을 baseSchedule 위에 올려 표시용 시간표를 만든다.
 * 커스텀 과목 저장 키 형식("custom_{DAY}_{교시}")은 워치/폰이 동일해야 하므로 여기서 단일 관리한다.
 */
object Schedule {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    val weekdays = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )

    // 기본 시간표 구조 (8~11교시 포함)
    val baseSchedule: List<Period> = listOf(
        Period("기상 시간", LocalTime.parse("06:30", formatter), LocalTime.parse("07:20", formatter)),
        Period("등교 시간", LocalTime.parse("07:20", formatter), LocalTime.parse("08:15", formatter)),
        Period("0교시", LocalTime.parse("08:15", formatter), LocalTime.parse("08:30", formatter)),
        Period("조회 및 준비", LocalTime.parse("08:30", formatter), LocalTime.parse("08:40", formatter)),
        Period("1교시", LocalTime.parse("08:40", formatter), LocalTime.parse("09:30", formatter)),
        Period("쉬는 시간", LocalTime.parse("09:30", formatter), LocalTime.parse("09:40", formatter)),
        Period("2교시", LocalTime.parse("09:40", formatter), LocalTime.parse("10:30", formatter)),
        Period("쉬는 시간", LocalTime.parse("10:30", formatter), LocalTime.parse("10:40", formatter)),
        Period("3교시", LocalTime.parse("10:40", formatter), LocalTime.parse("11:30", formatter)),
        Period("쉬는 시간", LocalTime.parse("11:30", formatter), LocalTime.parse("11:40", formatter)),
        Period("4교시", LocalTime.parse("11:40", formatter), LocalTime.parse("12:30", formatter)),
        Period("점심 시간", LocalTime.parse("12:30", formatter), LocalTime.parse("13:30", formatter)),
        Period("5교시", LocalTime.parse("13:30", formatter), LocalTime.parse("14:20", formatter)),
        Period("쉬는 시간", LocalTime.parse("14:20", formatter), LocalTime.parse("14:30", formatter)),
        Period("6교시", LocalTime.parse("14:30", formatter), LocalTime.parse("15:20", formatter)),
        Period("쉬는 시간", LocalTime.parse("15:20", formatter), LocalTime.parse("15:30", formatter)),
        Period("7교시", LocalTime.parse("15:30", formatter), LocalTime.parse("16:20", formatter)),
        Period("청소 및 종례", LocalTime.parse("16:20", formatter), LocalTime.parse("16:40", formatter)),
        Period("8교시", LocalTime.parse("16:40", formatter), LocalTime.parse("17:30", formatter)),
        Period("쉬는 시간", LocalTime.parse("17:30", formatter), LocalTime.parse("17:40", formatter)),
        Period("9교시", LocalTime.parse("17:40", formatter), LocalTime.parse("18:30", formatter)),
        Period("석식 시간", LocalTime.parse("18:30", formatter), LocalTime.parse("19:30", formatter)),
        Period("10교시", LocalTime.parse("19:30", formatter), LocalTime.parse("20:20", formatter)),
        Period("쉬는 시간", LocalTime.parse("20:20", formatter), LocalTime.parse("20:30", formatter)),
        Period("11교시", LocalTime.parse("20:30", formatter), LocalTime.parse("21:20", formatter)),
        Period("기숙사 입소", LocalTime.parse("21:20", formatter), LocalTime.parse("21:30", formatter)),
        Period("점호", LocalTime.parse("22:00", formatter), LocalTime.parse("22:10", formatter))
    )

    fun getDayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "월요일"
        DayOfWeek.TUESDAY -> "화요일"
        DayOfWeek.WEDNESDAY -> "수요일"
        DayOfWeek.THURSDAY -> "목요일"
        DayOfWeek.FRIDAY -> "금요일"
        else -> "주말"
    }

    // --- 커스텀 과목 (SharedPreferences) ---

    fun customKey(day: DayOfWeek, period: Int) = "custom_${day.name}_$period"

    fun getCustomSubject(prefs: SharedPreferences, day: DayOfWeek, period: Int): String? =
        prefs.getString(customKey(day, period), null)

    fun saveCustomSubject(prefs: SharedPreferences, day: DayOfWeek, period: Int, subject: String) {
        prefs.edit().putString(customKey(day, period), subject).apply()
    }

    fun clearCustomSubject(prefs: SharedPreferences, day: DayOfWeek, period: Int) {
        prefs.edit().remove(customKey(day, period)).apply()
    }

    /** 원시 NEIS 시간표 + 저장된 커스텀 과목을 baseSchedule 위에 합쳐 표시용 주간 시간표를 만든다. */
    fun buildWeeklyTimetable(
        raw: Map<DayOfWeek, Map<Int, String>>,
        prefs: SharedPreferences
    ): Map<DayOfWeek, List<Period>> {
        val finalMap = mutableMapOf<DayOfWeek, List<Period>>()
        for (day in weekdays) {
            val dailySubjects = raw[day] ?: emptyMap()
            finalMap[day] = baseSchedule.map { period ->
                if (period.name.contains("교시")) {
                    val pNum = period.name.replace("교시", "").toIntOrNull() ?: 0
                    // 저장된 커스텀 과목이 있으면 우선 적용
                    val customSubj = getCustomSubject(prefs, day, pNum)
                    val realSubj = customSubj ?: dailySubjects[pNum]
                    if (realSubj != null) period.copy(subject = realSubj) else period
                } else period
            }
        }
        return finalMap
    }

    /**
     * 특정 요일의 '교시'만 필터링. 기본은 0/10/11교시 및 금요일 8~9교시를 숨긴다.
     * includeExtra=true 면 확장 편집용으로 8~11교시(0교시 제외)까지 모두 노출한다.
     */
    fun getVisibleScheduleForDay(
        timetable: Map<DayOfWeek, List<Period>>,
        day: DayOfWeek,
        includeExtra: Boolean = false
    ): List<Period> {
        val schedule = timetable[day] ?: baseSchedule
        return schedule.filter { period ->
            if (!period.name.contains("교시")) return@filter false
            if (period.name == "0교시") return@filter false // 0교시는 항상 숨김
            if (includeExtra) return@filter true // 확장 시 8~11교시 모두 노출
            val isHidden = period.name in listOf("10교시", "11교시")
            val isFridayHidden = day == DayOfWeek.FRIDAY && period.name in listOf("8교시", "9교시")
            !isHidden && !isFridayHidden
        }
    }
}
