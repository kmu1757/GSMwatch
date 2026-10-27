package com.gsminulx.gsmwatch.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.gsminulx.gsmwatch.core.MealInfo
import com.gsminulx.gsmwatch.core.TaskInfo
import com.gsminulx.gsmwatch.core.UiState
import com.gsminulx.gsmwatch.presentation.theme.GsmWatchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 워치 화면 스크린샷 (Roborazzi). 에뮬레이터 없이 원형 워치 화면을 렌더링해 디자인을 확인한다.
 * 기록: ./gradlew :app:recordRoborazziDebug → app/build/outputs/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound)
class WatchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // 2026-10-27(화) 10:50 → 3교시 진행 중
    private val now = LocalDateTime.of(2026, 10, 27, 10, 50, 12)

    private fun sampleTimer(): SchoolTimer {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("screenshot", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return SchoolTimer(grade = 2, classNum = 3, sharedPref = prefs).apply {
            setPreviewData(SAMPLE_TIMETABLE, SAMPLE_MEALS, sampleTasks(now.toLocalDate()))
        }
    }

    private fun shot(name: String, scrolls: Int = 0, content: @Composable () -> Unit) {
        composeRule.setContent { GsmWatchTheme { AppScaffold { content() } } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/watch_${name}.png")
        repeat(scrolls) { i ->
            composeRule.onRoot().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/watch_${name}_${i + 1}.png")
        }
    }

    @Test
    fun home() {
        val timer = sampleTimer()
        shot("home", scrolls = 3) {
            HomeScreen(
                timer = timer,
                status = UiState.Success(Unit),
                onRefresh = {}, onTimetableClick = {}, onMealsClick = {}, onTasksClick = {}, onSettingsClick = {},
                now = { now }
            )
        }
    }

    @Test
    fun homeOffline() {
        val timer = sampleTimer()
        shot("home_offline") {
            HomeScreen(
                timer = timer,
                status = UiState.Error("네트워크 오류"),
                onRefresh = {}, onTimetableClick = {}, onMealsClick = {}, onTasksClick = {}, onSettingsClick = {},
                now = { now.withHour(22).withMinute(40) }
            )
        }
    }

    @Test
    fun timetable() {
        val timer = sampleTimer()
        shot("timetable", scrolls = 2) { WeeklyTimetableScreen(timer = timer, onEditPeriod = { _, _ -> }, now = now) }
    }

    @Test
    fun meals() {
        val timer = sampleTimer()
        shot("meals", scrolls = 1) { WeeklyMealsScreen(timer = timer, today = now.toLocalDate()) }
    }

    @Test
    fun tasks() {
        val timer = sampleTimer()
        shot("tasks", scrolls = 1) { TasksScreen(timer = timer, today = now.toLocalDate()) }
    }

    @Test
    fun edit() {
        val timer = sampleTimer()
        shot("edit") { EditSubjectScreen(timer = timer, day = DayOfWeek.TUESDAY, period = 3, onDone = {}) }
    }

    @Test
    fun settings() {
        shot("settings") { SettingsScreen(initialGrade = 2, initialClass = 3, onSave = { _, _ -> }) }
    }

    @Test
    fun statusWeekend() {
        shot("status_weekend") {
            StatusCard(
                com.gsminulx.gsmwatch.core.DayStatusCalculator.statusAt(
                    LocalDateTime.of(2026, 10, 31, 15, 0), emptyList(), 2
                )
            )
        }
    }

    companion object {
        private val SUBJECTS = listOf("국어", "수학", "영어", "프로그래밍", "물리", "체육", "자료구조", "방과후 SW")
        val SAMPLE_TIMETABLE: Map<DayOfWeek, Map<Int, String>> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        ).associateWith { day ->
            (1..7).associateWith { p -> SUBJECTS[(p + day.value) % SUBJECTS.size] }
        }
        val SAMPLE_MEALS: Map<DayOfWeek, List<MealInfo>> = SAMPLE_TIMETABLE.keys.associateWith {
            listOf(
                MealInfo("조식", "토스트\n스크램블에그\n우유", LocalTime.of(8, 40)),
                MealInfo("중식", "잡곡밥\n김치찌개\n제육볶음\n배추김치\n요구르트", LocalTime.of(13, 30)),
                MealInfo("석식", "카레라이스\n돈가스\n깍두기\n과일", LocalTime.of(19, 30)),
            )
        }

        fun sampleTasks(today: LocalDate) = listOf(
            TaskInfo(1, "수행평가", today.plusDays(1).toString(), "수학 2단원 서술형 평가"),
            TaskInfo(2, "과제", today.plusDays(3).toString(), "프로그래밍 과제: 정렬 알고리즘 구현"),
            TaskInfo(3, "준비물", "미정", "체육복 지참"),
            TaskInfo(4, "과제", today.plusDays(9).toString(), "영어 에세이 제출"),
        )
    }
}
