package com.gsminulx.gsmwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import com.gsminulx.gsmwatch.core.UiState
import kotlinx.coroutines.delay
import java.time.LocalDate
import androidx.wear.compose.material3.*

@Composable
fun TimerScreen(
    timer: SchoolTimer,
    status: UiState<Unit>,
    onRetry: () -> Unit,
    onTimetableClick: () -> Unit,
    onMealsClick: () -> Unit,
    onTasksClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var timeMessage by remember(timer) { mutableStateOf(timer.getRemainingTimeMessage()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 👉 화면이 켜져 있을 때만 1초마다 갱신 (배터리 절약). 데이터 조회는 ViewModel 이 담당.
    LaunchedEffect(timer, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                timeMessage = timer.getRemainingTimeMessage()
                delay(1000L)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                CompactButton(
                    onClick = onSettingsClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                ) {
                    Text("⚙️ ${timer.grade}학년 ${timer.classNum}반", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // [Phase 3] 네트워크 실패 시 캐시 표시 안내 + 재시도
            if (status is UiState.Error) {
                item {
                    CompactButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("⚠️ 오프라인 · 다시 시도", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item {
                Text(
                    text = timeMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 19.sp, // 더 크게
                    fontWeight = FontWeight.Black, // 가장 두껍게
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(bottom = 20.dp, start = 10.dp, end = 10.dp)
                )
            }

            if (!timer.isWeekend()) {
                val today = LocalDate.now().dayOfWeek
                val displaySchedule = timer.getVisibleScheduleForDay(today).filter { period ->
                    period.subject.isNotEmpty()
                }
                item { ScheduleCard(displaySchedule) }
                item {
                    CompactButton(onClick = onTimetableClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                        Text("시간표 상세정보", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            val upcomingMeals = timer.getUpcomingMeals()
            if (upcomingMeals.isEmpty()) {
                item {
                    val loadingText = if (status is UiState.Loading) "식단 로딩중..." else "식단 정보 없음"
                    Text(loadingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                itemsIndexed(upcomingMeals) { index, (title, mealInfo) ->
                    val isHighlight = index == 0
                    MealCard(title = title, menu = mealInfo.menu, isHighlight = isHighlight)
                }
                item {
                    CompactButton(onClick = onMealsClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                        Text("식단표 상세정보", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            item {
                Button(
                    onClick = onTasksClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(0.9f).padding(top = 8.dp)
                ) {
                    Text("💡 학급 일정 & D-Day", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
