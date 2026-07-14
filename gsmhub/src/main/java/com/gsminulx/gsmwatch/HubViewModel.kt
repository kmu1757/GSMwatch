package com.gsminulx.gsmwatch

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsminulx.gsmwatch.core.MealInfo
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.SchoolRepository
import com.gsminulx.gsmwatch.core.TaskInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HubUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val grade: Int = 1,
    val classNum: Int = 1,
    val timetable: Map<DayOfWeek, List<Period>> = emptyMap(),
    val meals: Map<DayOfWeek, List<MealInfo>> = emptyMap(),
    val tasks: List<TaskInfo> = emptyList()
)

class HubViewModel(app: Application) : AndroidViewModel(app) {

    // 워치 앱과 동일한 SharedPreferences 이름/키를 사용한다.
    private val prefs = app.getSharedPreferences("GsmWatchPrefs", Context.MODE_PRIVATE)
    private val repository = SchoolRepository(prefs)

    // 커스텀 과목 편집 시 재조립을 위해 원시 NEIS 시간표를 보관한다.
    private var rawTimetable: Map<DayOfWeek, Map<Int, String>> = emptyMap()

    private val _state = MutableStateFlow(
        HubUiState(grade = prefs.getInt("grade", 1), classNum = prefs.getInt("classNum", 1))
    )
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val s = _state.value
                val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val data = repository.fetchWeeklyData(s.grade, s.classNum, monday)
                rawTimetable = data.timetable
                _state.value = _state.value.copy(
                    loading = false,
                    error = if (data.networkOk) null else "오프라인 · 캐시 표시",
                    timetable = Schedule.buildWeeklyTimetable(rawTimetable, prefs),
                    meals = data.meals,
                    tasks = data.tasks
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "네트워크 오류")
            }
        }
    }

    /** 학년/반 저장 → 워치 동기화 → 재조회. */
    fun updateSettings(grade: Int, classNum: Int) {
        prefs.edit().putInt("grade", grade).putInt("classNum", classNum).apply()
        _state.value = _state.value.copy(grade = grade, classNum = classNum)
        WearSync.pushSettings(getApplication(), prefs)
        refresh()
    }

    /** 커스텀 과목 저장/삭제 → 워치 동기화 → 로컬 시간표 즉시 재조립(네트워크 없이). */
    fun editCustomSubject(day: DayOfWeek, period: Int, subject: String) {
        if (subject.isBlank()) {
            Schedule.clearCustomSubject(prefs, day, period)
        } else {
            Schedule.saveCustomSubject(prefs, day, period, subject)
        }
        WearSync.pushSettings(getApplication(), prefs)
        _state.value = _state.value.copy(
            timetable = Schedule.buildWeeklyTimetable(rawTimetable, prefs)
        )
    }
}
