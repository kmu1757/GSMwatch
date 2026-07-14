package com.gsminulx.gsmwatch.presentation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsminulx.gsmwatch.core.SchoolRepository
import com.gsminulx.gsmwatch.core.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 워치 앱 공용 상태. 화면마다 SchoolTimer 를 새로 만들어 각자 fetch 하던 중복을 없애고,
 * 단일 SchoolTimer 인스턴스와 로딩/에러 상태를 모든 화면이 공유하게 한다.
 */
class WatchViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("GsmWatchPrefs", Context.MODE_PRIVATE)

    var grade by mutableIntStateOf(prefs.getInt("grade", 1))
        private set
    var classNum by mutableIntStateOf(prefs.getInt("classNum", 1))
        private set

    var timer by mutableStateOf(SchoolTimer(grade, classNum, prefs))
        private set

    // Loading → 조회 중, Success → 최신 데이터, Error → 네트워크 실패(캐시 표시 중)
    var status by mutableStateOf<UiState<Unit>>(UiState.Loading)
        private set

    private var refreshJob: Job? = null

    // 폰(gsmhub)이 Data Layer 로 밀어넣어 SettingsSyncService 가 prefs(학년/반)를 갱신하면
    // 실행 중인 앱도 즉시 반영한다. (SharedPreferences 는 리스너를 약참조로 보관하므로
    // 반드시 강참조 프로퍼티로 유지해야 GC 되지 않는다.)
    // 참고: 학년/반이 그대로이고 커스텀 과목만 바뀐 동기화는 다음 실행 시 반영된다.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            "grade", "classNum" -> {
                val g = sp.getInt("grade", grade)
                val c = sp.getInt("classNum", classNum)
                if (g != grade || c != classNum) {
                    grade = g
                    classNum = c
                    timer = SchoolTimer(g, c, prefs) // 새 학년/반 → 재조회(커스텀 과목도 함께 반영)
                    refresh()
                }
            }
            SchoolRepository.PUSH_SIGNAL -> {
                // 폰이 데이터를 push → 캐시를 다시 읽어 실행 중인 화면도 즉시 갱신
                timer.reloadFromCache()
                status = UiState.Success(Unit)
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        status = UiState.Loading
        refreshJob = viewModelScope.launch {
            val networkOk = timer.fetchRealData()
            // 취소된(뒤처진) 조회가 최신 상태를 덮어쓰지 않도록 활성 상태에서만 반영
            if (isActive) {
                status = if (networkOk) UiState.Success(Unit) else UiState.Error("네트워크 오류")
            }
        }
    }

    fun updateSettings(newGrade: Int, newClass: Int) {
        prefs.edit().putInt("grade", newGrade).putInt("classNum", newClass).apply()
        grade = newGrade
        classNum = newClass
        timer = SchoolTimer(newGrade, newClass, prefs) // 새 학년/반으로 재생성 → 재조회
        refresh()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }
}
