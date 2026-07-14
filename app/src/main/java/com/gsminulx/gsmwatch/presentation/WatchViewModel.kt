package com.gsminulx.gsmwatch.presentation

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsminulx.gsmwatch.core.UiState
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

    // Loading → 첫 조회 중, Success → 최신 데이터, Error → 네트워크 실패(캐시 표시 중)
    var status by mutableStateOf<UiState<Unit>>(UiState.Loading)
        private set

    init {
        refresh()
    }

    fun refresh() {
        status = UiState.Loading
        viewModelScope.launch {
            val networkOk = timer.fetchRealData()
            val hasData = timer.weeklyMeals.isNotEmpty() || timer.weeklyTimetable.isNotEmpty()
            status = when {
                networkOk -> UiState.Success(Unit)
                hasData -> UiState.Success(Unit)          // 캐시라도 표시할 데이터가 있으면 성공 취급
                else -> UiState.Error("네트워크 오류")
            }
        }
    }

    fun updateSettings(newGrade: Int, newClass: Int) {
        prefs.edit().putInt("grade", newGrade).putInt("classNum", newClass).apply()
        grade = newGrade
        classNum = newClass
        timer = SchoolTimer(newGrade, newClass, prefs)  // 새 학년/반으로 재생성 → 재조회
        refresh()
    }
}
