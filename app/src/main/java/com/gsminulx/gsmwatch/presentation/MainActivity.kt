package com.gsminulx.gsmwatch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsminulx.gsmwatch.presentation.theme.GsmWatchTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.gsminulx.gsmwatch.analytics.AppAnalytics

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // [수정] 스플래시 스크린 적용 (튕김 현상 방지)
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            // [수정] GsmWatchTheme으로 감싸서 정의한 다크 테마 색상이 정상 출력되도록 함
            GsmWatchTheme {
                MainApp()
            }
        }
    }
}

enum class AppScreen { MAIN, WEEKLY_TIMETABLE, WEEKLY_MEALS, DDAY_TASKS, SETTINGS } // [수정] DDAY_TASKS 추가

@Composable
fun MainApp() {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    // [Phase 3] 화면들이 단일 SchoolTimer/상태를 공유 → 화면 전환 시 중복 네트워크 호출 제거
    val vm: WatchViewModel = viewModel()
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(Unit) {
        analytics.trackScreen("main")
    }

    // 👉 [핵심 2] 스와이프 시 자연스럽게 이전 화면으로 돌아가는 NavHost
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = AppScreen.MAIN.name
    ) {

        // 1. 메인 타이머 화면
        composable(AppScreen.MAIN.name) {
            TimerScreen(
                timer = vm.timer,
                status = vm.status,
                onRetry = { vm.refresh() },
                onTimetableClick = {
                    analytics.trackScreen("weekly_timetable")
                    navController.navigate(AppScreen.WEEKLY_TIMETABLE.name)
                },
                onMealsClick = {
                    analytics.trackScreen("weekly_meals")
                    navController.navigate(AppScreen.WEEKLY_MEALS.name)
                },
                onTasksClick = { // [추가]
                    analytics.trackScreen("dday_tasks")
                    navController.navigate(AppScreen.DDAY_TASKS.name)
                },
                onSettingsClick = {
                    analytics.trackScreen("settings")
                    navController.navigate(AppScreen.SETTINGS.name)
                }
            )
        }

        // 2. 시간표 전체보기 화면
        composable(AppScreen.WEEKLY_TIMETABLE.name) {
            WeeklyTimetableScreen(
                timer = vm.timer,
                onBack = { navController.popBackStack() } // 👉 스와이프하거나 '돌아가기'를 누르면 스택에서 빠짐
            )
        }

        // 3. 식단표 전체보기 화면
        composable(AppScreen.WEEKLY_MEALS.name) {
            WeeklyMealsScreen(
                timer = vm.timer,
                onBack = { navController.popBackStack() }
            )
        }

        // [추가] 4. D-Day 일정 목록 화면
        composable(AppScreen.DDAY_TASKS.name) {
            DDayTasksScreen(
                timer = vm.timer,
                onBack = { navController.popBackStack() }
            )
        }

        // 4. 설정 화면
        composable(AppScreen.SETTINGS.name) {
            SettingsScreen(
                initialGrade = vm.grade,
                initialClass = vm.classNum,
                onSave = { newGrade, newClass ->
                    vm.updateSettings(newGrade, newClass)
                    analytics.trackSettingsSaved(newGrade, newClass)
                    navController.popBackStack() // 👉 저장 완료 시 이전 화면(메인)으로 돌아감
                }
            )
        }
    }
}
