package com.gsminulx.gsmwatch.presentation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsminulx.gsmwatch.core.FetchProblem
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

    // Loading → 조회 중, Success → 최신 데이터, Error → 조회 실패(캐시 표시 중, message = 원인)
    var status by mutableStateOf<UiState<Unit>>(UiState.Loading)
        private set

    private var refreshJob: Job? = null

    // 워치는 화면을 켠 직후·블루투스↔Wi-Fi 전환 중에 네트워크가 늦게 붙는다. 그때 첫 조회가 '오프라인' 으로
    // 끝나면, 인터넷이 확인된 네트워크가 잡히는 순간 한 번 자동으로 다시 조회한다.
    // (NEIS 자체가 안 될 때 무한 재시도하지 않도록, 직접/최초 조회 한 번당 자동 재조회는 최대 1회)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private var waitingForNetwork = false
    private var autoRetried = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                // 콜백은 바인더 스레드에서 온다 → 상태 변경은 메인에서
                viewModelScope.launch {
                    if (waitingForNetwork) {
                        autoRetried = true
                        refresh(auto = true)
                    }
                }
            }
        }
    }

    // 폰(gsmhub)이 Data Layer 로 밀어넣어 SettingsSyncService 가 prefs(학년/반)를 갱신하면
    // 실행 중인 앱도 즉시 반영한다. (SharedPreferences 는 리스너를 약참조로 보관하므로
    // 반드시 강참조 프로퍼티로 유지해야 GC 되지 않는다.)
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
            else -> if (key?.startsWith("custom_") == true) {
                // 폰에서 과목만 바뀐 동기화도 실행 중인 화면에 바로 반영
                timer.reloadFromCache()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    fun refresh() = refresh(auto = false)

    private fun refresh(auto: Boolean) {
        stopWaitingForNetwork()
        if (!auto) autoRetried = false
        refreshJob?.cancel()
        status = UiState.Loading
        refreshJob = viewModelScope.launch {
            val problem = timer.fetchRealData()
            // 취소된(뒤처진) 조회가 최신 상태를 덮어쓰지 않도록 활성 상태에서만 반영
            if (isActive) {
                status = if (problem == null) UiState.Success(Unit) else UiState.Error(problem.message)
                when {
                    problem == null -> GsmTileService.requestUpdate(getApplication())
                    problem == FetchProblem.OFFLINE && !autoRetried -> waitForNetwork()
                }
            }
        }
    }

    private fun waitForNetwork() {
        if (waitingForNetwork || connectivity == null) return
        waitingForNetwork = true
        // 이미 인터넷이 되는 네트워크가 있으면 등록 직후 콜백이 와서 바로 한 번 재조회한다.
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
            .onFailure { waitingForNetwork = false }
    }

    private fun stopWaitingForNetwork() {
        if (!waitingForNetwork) return
        waitingForNetwork = false
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    fun updateSettings(newGrade: Int, newClass: Int) {
        prefs.edit().putInt("grade", newGrade).putInt("classNum", newClass).apply()
        grade = newGrade
        classNum = newClass
        timer = SchoolTimer(newGrade, newClass, prefs) // 새 학년/반으로 재생성 → 재조회
        refresh()
        GsmTileService.requestUpdate(getApplication())
    }

    override fun onCleared() {
        stopWaitingForNetwork()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }
}
