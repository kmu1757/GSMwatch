@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.gsminulx.gsmwatch

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import com.gsminulx.gsmwatch.core.R as CoreR

enum class HubTab(
    val label: String,
    val title: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int
) {
    TIMETABLE("시간표", "시간표", CoreR.drawable.ic_calendar, CoreR.drawable.ic_calendar_filled),
    MEALS("급식", "급식", CoreR.drawable.ic_restaurant, CoreR.drawable.ic_restaurant_filled),
    DDAY("D-Day", "학급 일정", CoreR.drawable.ic_assignment, CoreR.drawable.ic_assignment_filled),
    SETTINGS("설정", "설정", CoreR.drawable.ic_settings, CoreR.drawable.ic_settings_filled),
}

/**
 * GSMhub 골격 (Material 3 Expressive):
 * 접히는 LargeFlexibleTopAppBar + ShortNavigationBar + 당겨서 새로고침(Expressive LoadingIndicator) + 스낵바.
 */
@Composable
fun HubApp(
    vm: HubViewModel = viewModel(),
    oauthRedirect: Uri? = null,
    onRedirectHandled: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HubContent(
        state = state,
        onRefresh = vm::refresh,
        onSelectWeek = vm::selectWeek,
        onEditSubject = vm::editCustomSubject,
        onSaveSettings = { g, c -> vm.updateSettings(g, c) },
        onLogin = { vm.prepareLoginUrl()?.let { url -> launchCustomTab(context, url) } },
        onLogout = vm::logout,
        onConsumeNotice = vm::consumeNotice
    )

    // OAuth 리다이렉트 수신 시 로그인 완료 처리
    LaunchedEffect(oauthRedirect) {
        oauthRedirect?.let { vm.completeLogin(it); onRedirectHandled() }
    }
}

/** 상태만 받는 UI (프리뷰·스크린샷 테스트에서 ViewModel 없이 그릴 수 있게 분리). */
@Composable
fun HubContent(
    state: HubUiState,
    onRefresh: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onEditSubject: (java.time.DayOfWeek, Int, String) -> Unit,
    onSaveSettings: (Int, Int) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onConsumeNotice: () -> Unit,
    initialTab: HubTab = HubTab.TIMETABLE,
    fixedNow: LocalDateTime? = null // 프리뷰·스크린샷용 고정 시각 (null = 실제 시각, 매초 갱신)
) {
    val now = fixedNow ?: rememberTickingNow() // fixedNow 는 호출부에서 고정값이라 조건부 호출이 안전
    val today = now.toLocalDate()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(msg, actionLabel = "다시 시도", withDismissAction = true)
        if (result == SnackbarResult.ActionPerformed) onRefresh()
    }
    LaunchedEffect(state.notice) {
        val msg = state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        onConsumeNotice()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(tab.title) },
                subtitle = { Text("${state.grade}학년 ${state.classNum}반 · ${formatToday(today)}") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(painterResource(CoreR.drawable.ic_refresh), contentDescription = "새로고침")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            ShortNavigationBar {
                HubTab.entries.forEach { t ->
                    val selected = tab == t
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(if (selected) t.selectedIcon else t.icon), contentDescription = null) },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    HubTab.TIMETABLE -> TimetableTab(state, onSelectWeek = onSelectWeek, onEditSubject = onEditSubject, now = now)
                    HubTab.MEALS -> MealsTab(state, onSelectWeek = onSelectWeek, now = now)
                    HubTab.DDAY -> DdayTab(state, today = today)
                    HubTab.SETTINGS -> SettingsTab(state, onSave = onSaveSettings, onLogin = onLogin, onLogout = onLogout)
                }
            }
        }
    }
}

private fun launchCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}
