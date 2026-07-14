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

// 데이터 획득/파싱은 :core SchoolRepository, 시간표 조립/커스텀 과목은 :core Schedule 에 위임.
// 이 클래스는 워치 화면 표시용 상태(이번주+다음주)와 '남은 시간' 계산만 담당한다.
class SchoolTimer(val grade: Int = 1, val classNum: Int = 1, val sharedPref: SharedPreferences) {

    private val repository = SchoolRepository(sharedPref)

    val baseSchedule = Schedule.baseSchedule
    val weekdays = Schedule.weekdays

    var weeklyTimetable by mutableStateOf<Map<DayOfWeek, List<Period>>>(emptyMap())     // 이번주
        private set
    var nextWeekTimetable by mutableStateOf<Map<DayOfWeek, List<Period>>>(emptyMap())   // 다음주
        private set
    var weeklyMeals by mutableStateOf<Map<DayOfWeek, List<MealInfo>>>(emptyMap())
        private set
    var nextWeekMeals by mutableStateOf<Map<DayOfWeek, List<MealInfo>>>(emptyMap())
        private set
    var ddayTasks by mutableStateOf<List<TaskInfo>>(emptyList())
        private set

    fun saveCustomSubject(day: DayOfWeek, period: Int, subject: String) =
        Schedule.saveCustomSubject(sharedPref, day, period, subject)

    fun clearCustomSubject(day: DayOfWeek, period: Int) =
        Schedule.clearCustomSubject(sharedPref, day, period)

    private fun currentMonday(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * 통신이 불안정한 워치를 위해 캐시를 먼저 즉시 반영한 뒤 네트워크로 갱신한다(cache-first).
     * @return NEIS 네트워크 유효 응답이 있었으면 true.
     */
    suspend fun fetchRealData(): Boolean {
        val monday = currentMonday()
        applyData(repository.cachedWeeklyData(monday)) // 캐시 우선 (즉시 표시)
        return try {
            val data = repository.fetchWeeklyData(grade, classNum, monday)
            applyData(data)
            data.networkOk
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 커스텀 과목만 바뀐 경우 등, 네트워크 없이 캐시로 화면만 재구성한다. */
    fun reloadFromCache() {
        applyData(repository.cachedWeeklyData(currentMonday()))
    }

    private fun applyData(data: SchoolRepository.WeeklyData) {
        weeklyTimetable = Schedule.buildWeeklyTimetable(data.thisWeek.timetableRaw, sharedPref)
        nextWeekTimetable = Schedule.buildWeeklyTimetable(data.nextWeek.timetableRaw, sharedPref)
        weeklyMeals = data.thisWeek.meals
        nextWeekMeals = data.nextWeek.meals
        ddayTasks = data.tasks
    }

    fun isWeekend(): Boolean {
        val today = LocalDate.now().dayOfWeek
        return today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY
    }

    fun getTodaySchedule(): List<Period> = weeklyTimetable[LocalDate.now().dayOfWeek] ?: baseSchedule

    fun getVisibleScheduleForDay(day: DayOfWeek, includeExtra: Boolean = false): List<Period> =
        Schedule.getVisibleScheduleForDay(weeklyTimetable, day, includeExtra)

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
            val m = String.format("%02d", duration.toMinutes())
            val s = String.format("%02d", duration.seconds % 60)
            "$displayName 종료까지\n${m}분 ${s}초"
        } else "현재는 정규 일과\n시간이 아닙니다."
    }

    /** 지금 이후의 다음 급식 3개 (이번주+다음주 2주 범위, 실제 날짜 기준). */
    fun getUpcomingMeals(): List<Pair<String, MealInfo>> {
        val now = LocalDateTime.now()
        val monday = currentMonday()
        val dated = mutableListOf<Pair<LocalDate, MealInfo>>()
        for (wk in 0..1) {
            val meals = if (wk == 0) weeklyMeals else nextWeekMeals
            for (i in 0..4) {
                val date = monday.plusDays((wk * 7 + i).toLong())
                (meals[date.dayOfWeek] ?: emptyList()).forEach { dated.add(date to it) }
            }
        }
        if (dated.isEmpty()) return emptyList()

        var startIndex = dated.indexOfFirst { (date, meal) -> date.atTime(meal.endTime).isAfter(now) }
        if (startIndex == -1) startIndex = 0

        return (0 until minOf(3, dated.size)).map { k ->
            val (date, meal) = dated[(startIndex + k) % dated.size]
            val title = when (date) {
                now.toLocalDate() -> meal.type
                now.toLocalDate().plusDays(1) -> "내일 ${meal.type}"
                else -> "${getDayName(date.dayOfWeek)} ${meal.type}"
            }
            Pair(title, meal)
        }
    }
}
