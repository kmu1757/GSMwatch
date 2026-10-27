package com.gsminulx.gsmwatch

import android.app.Application
import android.content.Context
import android.net.Uri
import java.util.UUID
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
    val loggedInName: String? = null, // DataGSM 로그인한 학생 이름 (null = 미로그인)
    val notice: String? = null, // 한 번만 보여줄 안내(스낵바)
    val selectedWeek: Int = 0, // 0 = 이번주, 1 = 다음주
    val thisWeekTimetable: Map<DayOfWeek, List<Period>> = emptyMap(),
    val nextWeekTimetable: Map<DayOfWeek, List<Period>> = emptyMap(),
    val thisWeekMeals: Map<DayOfWeek, List<MealInfo>> = emptyMap(),
    val nextWeekMeals: Map<DayOfWeek, List<MealInfo>> = emptyMap(),
    val tasks: List<TaskInfo> = emptyList()
) {
    val timetable: Map<DayOfWeek, List<Period>> get() = if (selectedWeek == 0) thisWeekTimetable else nextWeekTimetable
    val meals: Map<DayOfWeek, List<MealInfo>> get() = if (selectedWeek == 0) thisWeekMeals else nextWeekMeals
}

class HubViewModel(app: Application) : AndroidViewModel(app) {

    // 워치 앱과 동일한 SharedPreferences 이름/키를 사용한다.
    private val prefs = app.getSharedPreferences("GsmWatchPrefs", Context.MODE_PRIVATE)
    private val repository = SchoolRepository(prefs)

    // 커스텀 과목 편집 시 네트워크 없이 재조립하기 위해 원시(2주) 시간표를 보관한다.
    private var thisWeekRaw: Map<DayOfWeek, Map<Int, String>> = emptyMap()
    private var nextWeekRaw: Map<DayOfWeek, Map<Int, String>> = emptyMap()

    private val _state = MutableStateFlow(
        HubUiState(
            grade = prefs.getInt("grade", 1),
            classNum = prefs.getInt("classNum", 1),
            // 로그인 표시는 앱을 다시 켜도 유지(학년/반은 이미 prefs 에 저장돼 있음)
            loggedInName = prefs.getString(KEY_LOGGED_IN_NAME, null)
        )
    )
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    init {
        applyData(repository.cachedWeeklyData(monday()), fromNetwork = false) // 캐시 우선(즉시)
        refresh()
    }

    private fun monday(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val s = _state.value
                val data = repository.fetchWeeklyData(s.grade, s.classNum, monday())
                applyData(data, fromNetwork = true)
                // 폰이 받은 데이터를 워치로 push (둘 다 → 워치는 최신 것 사용)
                WearSync.pushData(getApplication(), prefs)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "네트워크 오류")
            }
        }
    }

    private fun applyData(data: SchoolRepository.WeeklyData, fromNetwork: Boolean) {
        thisWeekRaw = data.thisWeek.timetableRaw
        nextWeekRaw = data.nextWeek.timetableRaw
        _state.value = _state.value.copy(
            loading = false,
            // 캐시 선반영 시에는 기존 에러 유지, 네트워크 조회 후에는 실패 원인('데이터 없음'은 정상) 표시
            error = if (!fromNetwork) _state.value.error else data.problem?.let { "${it.message} · 저장된 정보 표시" },
            thisWeekTimetable = Schedule.buildWeeklyTimetable(thisWeekRaw, prefs),
            nextWeekTimetable = Schedule.buildWeeklyTimetable(nextWeekRaw, prefs),
            thisWeekMeals = data.thisWeek.meals,
            nextWeekMeals = data.nextWeek.meals,
            tasks = data.tasks
        )
    }

    fun selectWeek(week: Int) {
        _state.value = _state.value.copy(selectedWeek = week)
    }

    /** 학년/반 저장 → 워치 동기화 → 재조회. */
    fun updateSettings(grade: Int, classNum: Int, notice: String? = "저장하고 워치로 보냈어요") {
        prefs.edit().putInt("grade", grade).putInt("classNum", classNum).apply()
        _state.value = _state.value.copy(grade = grade, classNum = classNum, notice = notice)
        WearSync.pushSettings(getApplication(), prefs)
        refresh()
    }

    fun consumeNotice() {
        _state.value = _state.value.copy(notice = null)
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
            thisWeekTimetable = Schedule.buildWeeklyTimetable(thisWeekRaw, prefs),
            nextWeekTimetable = Schedule.buildWeeklyTimetable(nextWeekRaw, prefs)
        )
    }

    // --- DataGSM OAuth 로그인 ---

    /** 로그인 authorize URL 을 만들고 PKCE verifier/state 를 저장한다. Client ID 미설정 시 null. */
    fun prepareLoginUrl(): String? {
        if (!DataGsmAuth.isConfigured()) {
            _state.value = _state.value.copy(error = "DataGSM Client ID 미설정 (local.properties)")
            return null
        }
        val verifier = DataGsmAuth.generateCodeVerifier()
        val loginState = UUID.randomUUID().toString()
        prefs.edit().putString("oauth_verifier", verifier).putString("oauth_state", loginState).apply()
        return DataGsmAuth.buildAuthorizeUrl(loginState, DataGsmAuth.codeChallenge(verifier))
    }

    /** 리다이렉트 URI 수신 → code/state 검증 → 토큰 교환 → userinfo → 학년/반 자동 설정. */
    fun completeLogin(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        val savedState = prefs.getString("oauth_state", null)
        val verifier = prefs.getString("oauth_verifier", null)
        if (code.isNullOrBlank() || returnedState == null || returnedState != savedState || verifier.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "로그인 실패 (인증 상태 불일치)")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val student = DataGsmAuth.login(code, verifier)
            if (student != null) {
                prefs.edit().putString(KEY_LOGGED_IN_NAME, student.name).apply()
                _state.value = _state.value.copy(loggedInName = student.name, loading = false)
                // 학년/반 자동 + 워치 push + 재조회
                updateSettings(student.grade, student.classNum, notice = "${student.name} 님, ${student.grade}학년 ${student.classNum}반으로 설정했어요")
            } else {
                _state.value = _state.value.copy(loading = false, error = "DataGSM 로그인 실패")
            }
        }
    }

    /** 로그인 표시만 지운다(학년/반 설정은 유지). */
    fun logout() {
        prefs.edit().remove(KEY_LOGGED_IN_NAME).apply()
        _state.value = _state.value.copy(loggedInName = null, notice = "로그아웃했어요")
    }

    private companion object {
        const val KEY_LOGGED_IN_NAME = "datagsm_name"
    }
}
