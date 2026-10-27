package com.gsminulx.gsmwatch

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.gsminulx.gsmwatch.core.MealInfo
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.TaskInfo
import com.gsminulx.gsmwatch.ui.theme.GSMwatchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 폰(GSMhub) 탭별 스크린샷 (라이트/다크, 브랜드 팔레트). 에뮬레이터 없이 JVM 에서 렌더링한다.
 * 기록: ./gradlew :gsmhub:recordRoborazziDebug → gsmhub/build/outputs/roborazzi/
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class HubScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))

        // 2026-10-27(화) 10:50 → 3교시 진행 중
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 27, 10, 50, 12)

        private val SUBJECTS = listOf("국어", "수학", "영어", "프로그래밍", "물리", "체육", "자료구조", "방과후 SW")
        val RAW: Map<DayOfWeek, Map<Int, String>> = Schedule.weekdays.associateWith { day ->
            (1..7).associateWith { p -> SUBJECTS[(p + day.value) % SUBJECTS.size] }
        }
        val MEALS: Map<DayOfWeek, List<MealInfo>> = Schedule.weekdays.associateWith {
            listOf(
                MealInfo("조식", "토스트\n스크램블에그\n우유", LocalTime.of(8, 40)),
                MealInfo("중식", "잡곡밥\n김치찌개\n제육볶음\n배추김치\n요구르트", LocalTime.of(13, 30)),
                MealInfo("석식", "카레라이스\n돈가스\n깍두기\n과일", LocalTime.of(19, 30)),
            )
        }

        fun tasks(today: LocalDate) = listOf(
            TaskInfo(1, "수행평가", today.plusDays(1).toString(), "수학 2단원 서술형 평가"),
            TaskInfo(2, "과제", today.plusDays(3).toString(), "프로그래밍 과제: 정렬 알고리즘 구현"),
            TaskInfo(3, "준비물", "미정", "체육복 지참"),
            TaskInfo(4, "과제", today.plusDays(9).toString(), "영어 에세이 제출"),
        )
    }

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(loggedIn: Boolean = false): HubUiState {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("screenshot", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val timetable = Schedule.buildWeeklyTimetable(RAW, prefs)
        return HubUiState(
            grade = 2,
            classNum = 3,
            loggedInName = if (loggedIn) "김지엣" else null,
            thisWeekTimetable = timetable,
            nextWeekTimetable = timetable,
            thisWeekMeals = MEALS,
            nextWeekMeals = MEALS,
            tasks = tasks(NOW.toLocalDate())
        )
    }

    private fun shot(tab: HubTab, state: HubUiState = sampleState()) {
        composeRule.setContent {
            GSMwatchTheme(darkTheme = dark, dynamicColor = false) {
                HubContent(
                    state = state,
                    onRefresh = {}, onSelectWeek = {}, onEditSubject = { _, _, _ -> },
                    onSaveSettings = { _, _ -> }, onLogin = {}, onLogout = {}, onConsumeNotice = {},
                    initialTab = tab,
                    fixedNow = NOW
                )
            }
        }
        val mode = if (dark) "dark" else "light"
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/hub_${tab.name.lowercase()}_$mode.png")
    }

    @Test fun timetable() = shot(HubTab.TIMETABLE)
    @Test fun meals() = shot(HubTab.MEALS)
    @Test fun dday() = shot(HubTab.DDAY)
    @Test fun settings() = shot(HubTab.SETTINGS, sampleState(loggedIn = true))
}
