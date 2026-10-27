package com.gsminulx.gsmwatch.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.gsminulx.gsmwatch.analytics.AppAnalytics
import com.gsminulx.gsmwatch.core.DayStatus
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.number
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.gsminulx.gsmwatch.core.R as CoreR

/** 이번주(0~4) + 다음주(5~9) 평일 10페이지. */
private const val PAGE_COUNT = 10

private fun pageWeek(page: Int) = page / 5
private fun pageDay(page: Int): DayOfWeek = Schedule.weekdays[page % 5]

/** 시작 페이지: 오늘(평일), 주말이면 다음주 월요일. */
private fun todayPage(today: LocalDate = LocalDate.now()): Int {
    val idx = Schedule.weekdays.indexOf(today.dayOfWeek)
    return if (idx >= 0) idx else 5
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 2주 요일 페이저 공통 골격. HorizontalPagerScaffold 가 페이지 인디케이터와 페이지 전환 애니메이션을,
 * 각 페이지의 ScreenScaffold 가 TimeText·스크롤 인디케이터를 담당한다.
 */
@Composable
private fun WeekPager(
    pagerState: PagerState,
    page: @Composable (week: Int, day: DayOfWeek, date: LocalDate) -> Unit,
    dateOf: (Int, DayOfWeek) -> LocalDate
) {
    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { index ->
            AnimatedPage(pageIndex = index, pagerState = pagerState) {
                val week = pageWeek(index)
                val day = pageDay(index)
                page(week, day, dateOf(week, day))
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, week: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatDateHeader(date), style = MaterialTheme.typography.titleMedium)
        Text(
            if (week == 0) "이번주" else "다음주",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WeeklyTimetableScreen(
    timer: SchoolTimer,
    onEditPeriod: (DayOfWeek, Int) -> Unit,
    now: LocalDateTime = LocalDateTime.now()
) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    var showExtra by rememberSaveable { mutableStateOf(false) } // 10·11교시 펼치기
    val pagerState = rememberPagerState(initialPage = todayPage(now.toLocalDate())) { PAGE_COUNT }

    LaunchedEffect(timer) {
        val visiblePeriodCount = timer.getVisibleScheduleForDay(LocalDate.now().dayOfWeek).count { it.subject.isNotEmpty() }
        analytics.trackTimetableLoaded(timer.grade, timer.classNum, visiblePeriodCount)
    }

    val currentPeriod = (timer.status(now) as? DayStatus.InPeriod)?.period?.name

    WeekPager(pagerState = pagerState, dateOf = timer::dateOf, page = { week, day, date ->
        val periods = Schedule.getVisibleScheduleForDay(timer.timetableFor(week), day, includeExtra = showExtra)
        val isToday = date == now.toLocalDate()
        ScreenList { spec ->
            item { ListHeader(modifier = itemModifier(spec), transformation = itemTransformation(spec)) { DayHeader(date, week) } }
            periods.forEach { period ->
                item {
                    PeriodButton(
                        period = period,
                        isNow = isToday && period.name == currentPeriod,
                        onClick = { period.number?.let { onEditPeriod(day, it) } },
                        modifier = itemModifier(spec),
                        transformation = itemTransformation(spec)
                    )
                }
            }
            item {
                SwitchButton(
                    checked = showExtra,
                    onCheckedChange = { showExtra = it },
                    secondaryLabel = { Text("방과후·야자") },
                    modifier = itemModifier(spec),
                    transformation = itemTransformation(spec)
                ) { Text("10·11교시") }
            }
            item {
                Text(
                    "과목을 탭해서 바꿀 수 있어요",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = itemModifier(spec)
                )
            }
        }
    })
}

/** 교시 버튼: 지금 교시 = Filled, 과목 있음 = FilledTonal, 비어 있음 = Outlined(추가 힌트). */
@Composable
private fun PeriodButton(
    period: Period,
    isNow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: androidx.wear.compose.material3.SurfaceTransformation
) {
    val secondary: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text("${period.name} · ${period.start.format(timeFormat)}")
    }
    when {
        period.subject.isEmpty() -> {
            val hint = if (period.name in listOf("8교시", "9교시")) "방과후·동아리" else "과목 추가"
            OutlinedButton(
                onClick = onClick,
                icon = { WatchIcon(CoreR.drawable.ic_add, null) },
                secondaryLabel = secondary,
                modifier = modifier,
                transformation = transformation
            ) { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        isNow -> Button(
            onClick = onClick,
            secondaryLabel = secondary,
            icon = { WatchIcon(CoreR.drawable.ic_schedule, "지금") },
            modifier = modifier,
            transformation = transformation
        ) { Text(period.subject) }
        else -> FilledTonalButton(
            onClick = onClick,
            secondaryLabel = secondary,
            modifier = modifier,
            transformation = transformation
        ) { Text(period.subject) }
    }
}

@Composable
fun WeeklyMealsScreen(timer: SchoolTimer, today: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    val pagerState = rememberPagerState(initialPage = todayPage(today)) { PAGE_COUNT }

    LaunchedEffect(timer) {
        analytics.trackMealsLoaded(timer.weeklyMeals.values.sumOf { it.size })
    }

    WeekPager(pagerState = pagerState, dateOf = timer::dateOf, page = { week, day, date ->
        val meals = timer.mealsFor(week)[day].orEmpty()
        ScreenList { spec ->
            item { ListHeader(modifier = itemModifier(spec), transformation = itemTransformation(spec)) { DayHeader(date, week) } }
            if (meals.isEmpty()) {
                item { EmptyState(CoreR.drawable.ic_restaurant, "급식 정보가 없어요", modifier = itemModifier(spec)) }
            } else {
                meals.forEach { meal ->
                    item {
                        TitleCard(
                            title = {
                                Icon(
                                    painter = painterResource(mealIcon(meal.type)),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(meal.type)
                            },
                            colors = CardDefaults.cardColors(),
                            modifier = itemModifier(spec),
                            transformation = itemTransformation(spec)
                        ) {
                            Text(meal.menu, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    })
}
