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
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.gsminulx.gsmwatch.analytics.AppAnalytics
import com.gsminulx.gsmwatch.presentation.theme.GsmWatchTheme
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            GsmWatchTheme {
                MainApp()
            }
        }
    }
}

private object Routes {
    const val MAIN = "main"
    const val TIMETABLE = "timetable"
    const val MEALS = "meals"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{day}/{period}"
    fun edit(day: DayOfWeek, period: Int) = "edit/${day.name}/$period"
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    // 화면들이 단일 SchoolTimer/상태를 공유 → 화면 전환 시 중복 네트워크 호출 없음
    val vm: WatchViewModel = viewModel()
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(Unit) { analytics.trackScreen("main") }

    fun open(route: String, screen: String) {
        analytics.trackScreen(screen)
        navController.navigate(route)
    }

    // AppScaffold: 화면 전환 사이에도 곡선 TimeText 를 유지하는 Wear M3 앱 골격.
    // 뒤로 가기는 스와이프(SwipeDismissableNavHost)로 하므로 '돌아가기' 버튼은 두지 않는다.
    AppScaffold {
        SwipeDismissableNavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                HomeScreen(
                    timer = vm.timer,
                    status = vm.status,
                    onRefresh = { vm.refresh() },
                    onTimetableClick = { open(Routes.TIMETABLE, "weekly_timetable") },
                    onMealsClick = { open(Routes.MEALS, "weekly_meals") },
                    onTasksClick = { open(Routes.TASKS, "dday_tasks") },
                    onSettingsClick = { open(Routes.SETTINGS, "settings") }
                )
            }
            composable(Routes.TIMETABLE) {
                WeeklyTimetableScreen(
                    timer = vm.timer,
                    onEditPeriod = { day, period -> open(Routes.edit(day, period), "edit_subject") }
                )
            }
            composable(Routes.MEALS) {
                WeeklyMealsScreen(timer = vm.timer)
            }
            composable(Routes.TASKS) {
                TasksScreen(timer = vm.timer)
            }
            composable(Routes.EDIT) { entry ->
                val day = entry.arguments?.getString("day")?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                val period = entry.arguments?.getString("period")?.toIntOrNull()
                if (day != null && period != null) {
                    EditSubjectScreen(
                        timer = vm.timer,
                        day = day,
                        period = period,
                        onDone = { navController.popBackStack() }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    initialGrade = vm.grade,
                    initialClass = vm.classNum,
                    onSave = { newGrade, newClass ->
                        vm.updateSettings(newGrade, newClass)
                        analytics.trackSettingsSaved(newGrade, newClass)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
