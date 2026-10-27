package com.gsminulx.gsmwatch.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.transformedHeight
import com.gsminulx.gsmwatch.core.DayStatus
import com.gsminulx.gsmwatch.core.RemainingFormat
import com.gsminulx.gsmwatch.core.UiState
import com.gsminulx.gsmwatch.core.caption
import com.gsminulx.gsmwatch.core.label
import com.gsminulx.gsmwatch.core.number
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import com.gsminulx.gsmwatch.core.R as CoreR

/**
 * 워치 홈: 지금 일과 카운트다운(Hero) → 오늘 시간표 → 다음 급식 → 상세 화면 이동.
 * EdgeButton 은 새로고침(통신이 불안정한 워치에서 가장 자주 쓰는 동작).
 */
@Composable
fun HomeScreen(
    timer: SchoolTimer,
    status: UiState<Unit>,
    onRefresh: () -> Unit,
    onTimetableClick: () -> Unit,
    onMealsClick: () -> Unit,
    onTasksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    now: () -> LocalDateTime = LocalDateTime::now,
) {
    var current by remember(timer) { mutableStateOf(now()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 화면이 켜져 있을 때만 1초마다 갱신(배터리 절약). 데이터 조회는 ViewModel 이 담당.
    LaunchedEffect(timer, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                current = now()
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
        }
    }

    val dayStatus = timer.status(current)
    val loading = status is UiState.Loading
    val meals = timer.getUpcomingMeals()
    val tasks = timer.sortedTasks(current.toLocalDate())

    ScreenList(
        edgeButton = {
            EdgeButton(onClick = { if (!loading) onRefresh() }, buttonSize = EdgeButtonSize.Small) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        colors = ProgressIndicatorDefaults.colors(
                            indicatorColor = LocalContentColor.current,
                            trackColor = LocalContentColor.current.copy(alpha = 0.3f)
                        ),
                        strokeWidth = 3.dp
                    )
                } else {
                    WatchIcon(CoreR.drawable.ic_refresh, "새로고침")
                }
            }
        }
    ) { spec ->
        item {
            CompactButton(
                onClick = onSettingsClick,
                icon = { WatchIcon(CoreR.drawable.ic_school, null) },
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.transformedHeight(this, spec),
                transformation = itemTransformation(spec)
            ) { Text("${timer.grade}학년 ${timer.classNum}반") }
        }

        item {
            StatusCard(dayStatus, modifier = itemModifier(spec), transformation = itemTransformation(spec))
        }

        // 네트워크 실패 → 캐시 표시 중 안내 + 재시도
        if (status is UiState.Error) {
            item {
                FilledTonalButton(
                    onClick = onRefresh,
                    icon = { WatchIcon(CoreR.drawable.ic_cloud_off, null) },
                    secondaryLabel = { Text("저장된 정보 표시 중") },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        iconColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = itemModifier(spec),
                    transformation = itemTransformation(spec)
                ) { Text("오프라인 · 다시 시도") }
            }
        }

        if (!timer.isWeekend()) {
            item {
                TodayScheduleCard(
                    timer = timer,
                    currentPeriodName = (dayStatus as? DayStatus.InPeriod)?.period?.name,
                    onClick = onTimetableClick,
                    modifier = itemModifier(spec),
                    transformation = itemTransformation(spec)
                )
            }
        }

        item { ListHeader(transformation = itemTransformation(spec), modifier = itemModifier(spec)) { Text("다음 급식") } }
        if (meals.isEmpty()) {
            item {
                if (loading) {
                    Text(
                        "식단을 불러오는 중…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = itemModifier(spec),
                        textAlign = TextAlign.Center
                    )
                } else {
                    EmptyState(CoreR.drawable.ic_restaurant, "급식 정보가 없어요", modifier = itemModifier(spec))
                }
            }
        } else {
            meals.forEachIndexed { index, (title, meal) ->
                item {
                    val highlight = index == 0
                    TitleCard(
                        onClick = onMealsClick,
                        title = {
                            Icon(
                                painter = painterResource(mealIcon(meal.type)),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = if (highlight) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                titleColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                timeColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else CardDefaults.cardColors(),
                        time = if (highlight) ({ Text("다음") }) else null,
                        modifier = itemModifier(spec),
                        transformation = itemTransformation(spec)
                    ) {
                        Text(meal.menu, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { ListHeader(transformation = itemTransformation(spec), modifier = itemModifier(spec)) { Text("바로가기") } }
        item {
            FilledTonalButton(
                onClick = onTimetableClick,
                icon = { WatchIcon(CoreR.drawable.ic_calendar, null) },
                modifier = itemModifier(spec),
                transformation = itemTransformation(spec)
            ) { Text("주간 시간표") }
        }
        item {
            FilledTonalButton(
                onClick = onMealsClick,
                icon = { WatchIcon(CoreR.drawable.ic_restaurant, null) },
                modifier = itemModifier(spec),
                transformation = itemTransformation(spec)
            ) { Text("주간 급식") }
        }
        item {
            val nearest = tasks.firstOrNull()
            FilledTonalButton(
                onClick = onTasksClick,
                icon = { WatchIcon(CoreR.drawable.ic_assignment, null) },
                secondaryLabel = if (nearest != null) ({ Text("${tasks.size}개 · ${nearest.badge}") }) else null,
                modifier = itemModifier(spec),
                transformation = itemTransformation(spec)
            ) { Text("학급 일정") }
        }
    }
}

/** 지금 상태 카드: 대상 이름 + 큰 숫자 카운트다운 + 진행률. */
@Composable
fun StatusCard(st: DayStatus, modifier: Modifier = Modifier, transformation: SurfaceTransformation? = null) {
    val icon = when (st) {
        is DayStatus.InPeriod -> if (st.period.number != null) CoreR.drawable.ic_school else CoreR.drawable.ic_timer
        is DayStatus.UntilNext -> if (st.next.name.startsWith("기상")) CoreR.drawable.ic_alarm else CoreR.drawable.ic_schedule
        is DayStatus.WeekendDorm -> CoreR.drawable.ic_bedtime
    }
    val r = st.remaining
    val typography = MaterialTheme.typography
    val (remainingText, remainingStyle) = when {
        r.toHours() >= 24 -> RemainingFormat.korean(r) to typography.titleLarge
        r.toHours() >= 1 -> RemainingFormat.clock(r) to typography.numeralSmall
        else -> RemainingFormat.clock(r) to typography.numeralMedium
    }

    Card(
        modifier = modifier,
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    st.label,
                    style = typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                remainingText,
                style = remainingStyle.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(st.caption, style = typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            st.progress?.let { p ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** 오늘 교시 요약 카드 (현재 교시 강조). 탭하면 주간 시간표로 이동. */
@Composable
private fun TodayScheduleCard(
    timer: SchoolTimer,
    currentPeriodName: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val today = java.time.LocalDate.now().dayOfWeek
    val periods = timer.getVisibleScheduleForDay(today).filter { it.subject.isNotEmpty() }
    TitleCard(
        onClick = onClick,
        title = { Text("오늘 시간표") },
        time = { Text(timer.getDayName(today)) },
        modifier = modifier,
        transformation = transformation
    ) {
        if (periods.isEmpty()) {
            Text(
                "시간표 정보가 없어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                periods.forEach { p ->
                    val isNow = p.name == currentPeriodName
                    val color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${p.number ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            p.subject,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            fontWeight = if (isNow) FontWeight.Bold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
