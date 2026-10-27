package com.gsminulx.gsmwatch

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gsminulx.gsmwatch.core.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.time.temporal.TemporalAdjusters
import com.gsminulx.gsmwatch.core.R as CoreR

/** "10월 27일 (화)" */
fun formatToday(date: LocalDate): String =
    "${date.monthValue}월 ${date.dayOfMonth}일 (${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

/** 이번주 월요일 기준 [week](0/1) 주의 [day] 날짜. */
fun dateOf(today: LocalDate, week: Int, day: DayOfWeek): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(week * 7L + (day.value - 1))

/** 평일이면 오늘, 주말이면 월요일. */
fun defaultDay(today: LocalDate = LocalDate.now()): DayOfWeek =
    if (today.dayOfWeek in Schedule.weekdays) today.dayOfWeek else DayOfWeek.MONDAY

/** 연결형(connected) ButtonGroup 의 위치별 모양. */
@Composable
private fun connectedShapes(index: Int, count: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

/**
 * Material 3 Expressive 연결형 버튼 그룹(단일 선택). 선택된 버튼은 모양이 둥글게 변형된다.
 */
@Composable
fun ConnectedToggleGroup(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    label: @Composable (index: Int, selected: Boolean) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        repeat(count) { i ->
            val selected = i == selectedIndex
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(i) },
                shapes = connectedShapes(i, count),
                colors = colors,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton }
            ) { label(i, selected) }
        }
    }
}

@Composable
fun WeekSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    ConnectedToggleGroup(count = 2, selectedIndex = selected, onSelect = onSelect, modifier = modifier) { i, _ ->
        Text(if (i == 0) "이번주" else "다음주")
    }
}

/** 월~금 + 날짜. */
@Composable
fun DaySelector(
    week: Int,
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val days = Schedule.weekdays
    ConnectedToggleGroup(
        count = days.size,
        selectedIndex = days.indexOf(selected),
        onSelect = { onSelect(days[it]) },
        modifier = modifier
    ) { i, _ ->
        val date = dateOf(today, week, days[i])
        Text(
            "${Schedule.getDayName(days[i]).first()} ${date.dayOfMonth}",
            fontWeight = if (date == today) FontWeight.Bold else null,
            maxLines = 1
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    )
}

/** 아이콘 + 문구 빈 상태. */
@Composable
fun EmptyHint(@DrawableRes icon: Int, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@DrawableRes
fun mealIcon(type: String): Int = when {
    type.contains("조") -> CoreR.drawable.ic_breakfast
    type.contains("석") -> CoreR.drawable.ic_dinner
    else -> CoreR.drawable.ic_lunch
}
