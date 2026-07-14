// SchoolTimer.kt

package com.gsminulx.gsmwatch.presentation

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gsminulx.gsmwatch.core.MealInfo
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.SchoolRepository
import com.gsminulx.gsmwatch.core.TaskInfo
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// 데이터 획득/파싱은 :core 의 SchoolRepository, 시간표 조립/커스텀 과목은 :core 의 Schedule 에 위임한다.
// 이 클래스는 워치 화면 표시용 상태 보관과 '남은 시간' 계산만 담당한다.
class SchoolTimer(val grade: Int = 1, val classNum: Int = 1, val sharedPref: SharedPreferences) {

    private val repository = SchoolRepository(sharedPref)

    val baseSchedule = Schedule.baseSchedule
    val weekdays = Schedule.weekdays

    var weeklyTimetable by mutableStateOf<Map<DayOfWeek, List<Period>>>(emptyMap())
    var weeklyMeals by mutableStateOf<Map<DayOfWeek, List<MealInfo>>>(emptyMap())
    var ddayTasks by mutableStateOf<List<TaskInfo>>(emptyList())

    fun saveCustomSubject(day: DayOfWeek, period: Int, subject: String) =
        Schedule.saveCustomSubject(sharedPref, day, period, subject)

    fun clearCustomSubject(day: DayOfWeek, period: Int) =
        Schedule.clearCustomSubject(sharedPref, day, period)

    suspend fun fetchRealData() {
        try {
            var targetDate = LocalDate.now()
            if (isNextWeekTarget()) targetDate = targetDate.plusDays(3)
            val monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

            val data = repository.fetchWeeklyData(grade, classNum, monday)

            // 상태 반영 (호출자 코루틴 컨텍스트 = 메인)
            weeklyMeals = data.meals
            weeklyTimetable = Schedule.buildWeeklyTimetable(data.timetable, sharedPref)
            ddayTasks = data.tasks
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isWeekend(): Boolean {
        val today = LocalDate.now().dayOfWeek
        return today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY
    }

    private fun isNextWeekTarget(): Boolean {
        val now = LocalDateTime.now()
        return isWeekend() || (now.dayOfWeek == DayOfWeek.FRIDAY && now.toLocalTime().isAfter(LocalTime.of(19, 30)))
    }

    fun getTodaySchedule(): List<Period> = weeklyTimetable[LocalDate.now().dayOfWeek] ?: baseSchedule

    fun getVisibleScheduleForDay(day: DayOfWeek): List<Period> =
        Schedule.getVisibleScheduleForDay(weeklyTimetable, day)

    fun getDayName(day: DayOfWeek) = Schedule.getDayName(day)

    fun getRemainingTimeMessage(): String {
        val nowDateTime = LocalDateTime.now()
        val nowTime = nowDateTime.toLocalTime()
        val today = nowDateTime.dayOfWeek

        if (today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY) {
            var targetDate = nowDateTime.toLocalDate()
            while (targetDate.dayOfWeek != DayOfWeek.SUNDAY) { targetDate = targetDate.plusDays(1) }
            val targetTime = when (grade) { 1 -> LocalTime.of(20, 20); 2 -> LocalTime.of(20, 40); else -> LocalTime.of(21, 0) }
            val targetDateTime = LocalDateTime.of(targetDate, targetTime)
            if (nowDateTime.isAfter(targetDateTime)) return "기숙사 입소 완료!\n새로운 주를 준비하세요 🌙"
            val duration = Duration.between(nowDateTime, targetDateTime)
            val h = duration.toHours()
            val m = String.format("%02d", duration.toMinutes() % 60)
            val s = String.format("%02d", duration.seconds % 60)
            return "기숙사 입소까지\n${h}시간 ${m}분 ${s}초"
        }

        val schedule = getTodaySchedule()
        val currentPeriod = schedule.find { (nowTime.isAfter(it.start) || nowTime == it.start) && nowTime.isBefore(it.end) }
        return if (currentPeriod != null) {
            val duration = Duration.between(nowTime, currentPeriod.end)
            val displayName = if (currentPeriod.subject.isNotEmpty()) "${currentPeriod.name} ${currentPeriod.subject}" else currentPeriod.name

            // %02d를 쓰면 '9초'도 '09초'가 되어서 글자 너비가 변하지 않아!
            val m = String.format("%02d", duration.toMinutes())
            val s = String.format("%02d", duration.seconds % 60)
            "$displayName 종료까지\n${m}분 ${s}초" // \n으로 무조건 2줄 유지
        } else "현재는 정규 일과\n시간이 아닙니다."
    }

    fun getUpcomingMeals(): List<Pair<String, MealInfo>> {
        if (weeklyMeals.isEmpty()) return emptyList()
        val nowTime = LocalTime.now()
        val today = LocalDate.now().dayOfWeek

        val allMeals = weekdays.flatMap { day -> (weeklyMeals[day] ?: emptyList()).map { Pair(day, it) } }

        var startIndex = allMeals.indexOfFirst { (mealDay, meal) -> mealDay > today || (mealDay == today && meal.endTime.isAfter(nowTime)) }
        if (startIndex == -1) startIndex = 0

        return (0 until minOf(3, allMeals.size)).map { i ->
            val data = allMeals[(startIndex + i) % allMeals.size]
            val title = when {
                data.first == today -> data.second.type
                data.first == today.plus(1) -> "내일 ${data.second.type}"
                else -> "${getDayName(data.first)} ${data.second.type}"
            }
            Pair(title, data.second)
        }
    }
}
