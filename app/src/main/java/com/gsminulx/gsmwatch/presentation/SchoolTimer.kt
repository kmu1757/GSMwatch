// SchoolTimer.kt

package com.gsminulx.gsmwatch.presentation

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gsminulx.gsmwatch.core.DayStatus
import com.gsminulx.gsmwatch.core.DayStatusCalculator
import com.gsminulx.gsmwatch.core.Dday
import com.gsminulx.gsmwatch.core.MealInfo
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.SchoolRepository
import com.gsminulx.gsmwatch.core.TaskInfo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

// 데이터 획득/파싱은 :core SchoolRepository, 시간표 조립/커스텀 과목은 :core Schedule,
// '지금' 상태 계산은 :core DayStatusCalculator 에 위임한다.
// 이 클래스는 워치 화면 표시용 상태(이번주+다음주)만 담당한다.
class SchoolTimer(val grade: Int = 1, val classNum: Int = 1, val sharedPref: SharedPreferences) {

    private val repository = SchoolRepository(sharedPref)

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

    fun customSubject(day: DayOfWeek, period: Int): String? =
        Schedule.getCustomSubject(sharedPref, day, period)

    private fun currentMonday(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** week: 0=이번주, 1=다음주 */
    fun dateOf(week: Int, day: DayOfWeek): LocalDate =
        currentMonday().plusDays(week * 7L + (day.value - 1))

    fun timetableFor(week: Int): Map<DayOfWeek, List<Period>> =
        if (week == 0) weeklyTimetable else nextWeekTimetable

    fun mealsFor(week: Int): Map<DayOfWeek, List<MealInfo>> =
        if (week == 0) weeklyMeals else nextWeekMeals

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

    /** 프리뷰·스크린샷 테스트용: 네트워크 없이 표시 데이터를 직접 넣는다. */
    @VisibleForTesting
    fun setPreviewData(
        timetableRaw: Map<DayOfWeek, Map<Int, String>>,
        meals: Map<DayOfWeek, List<MealInfo>>,
        tasks: List<TaskInfo>
    ) {
        weeklyTimetable = Schedule.buildWeeklyTimetable(timetableRaw, sharedPref)
        nextWeekTimetable = weeklyTimetable
        weeklyMeals = meals
        nextWeekMeals = meals
        ddayTasks = tasks
    }

    private fun applyData(data: SchoolRepository.WeeklyData) {
        weeklyTimetable = Schedule.buildWeeklyTimetable(data.thisWeek.timetableRaw, sharedPref)
        nextWeekTimetable = Schedule.buildWeeklyTimetable(data.nextWeek.timetableRaw, sharedPref)
        weeklyMeals = data.thisWeek.meals
        nextWeekMeals = data.nextWeek.meals
        ddayTasks = data.tasks
    }

    fun getVisibleScheduleForDay(day: DayOfWeek, includeExtra: Boolean = false): List<Period> =
        Schedule.getVisibleScheduleForDay(weeklyTimetable, day, includeExtra)

    fun getDayName(day: DayOfWeek) = Schedule.getDayName(day)

    /** 지금 일과 상태 (홈 카운트다운/타일 공용). */
    fun status(now: LocalDateTime = LocalDateTime.now()): DayStatus =
        DayStatusCalculator.statusAt(now, weeklyTimetable[now.dayOfWeek].orEmpty(), grade)

    /** 마감순으로 정렬된 D-Day 일정. */
    fun sortedTasks(today: LocalDate = LocalDate.now()): List<Dday.Item> = Dday.sort(ddayTasks, today)

    /** 지금 이후의 다음 급식 3개 (이번주+다음주 2주 범위, 실제 날짜 기준). */
    fun getUpcomingMeals(now: LocalDateTime = LocalDateTime.now()): List<Pair<String, MealInfo>> {
        val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
