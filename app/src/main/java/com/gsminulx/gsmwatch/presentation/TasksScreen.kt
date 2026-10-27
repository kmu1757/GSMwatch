package com.gsminulx.gsmwatch.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.CardColors
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.gsminulx.gsmwatch.analytics.AppAnalytics
import com.gsminulx.gsmwatch.core.Dday
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.gsminulx.gsmwatch.core.R as CoreR

private val deadlineFormat = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)

/** 학급 일정(D-Day): 마감 가까운 순, 남은 일수에 따라 카드 색으로 긴급도를 표시한다. */
@Composable
fun TasksScreen(timer: SchoolTimer, today: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    LaunchedEffect(timer) { analytics.trackDdayLoaded(timer.ddayTasks.size) }

    val items = timer.sortedTasks(today)

    ScreenList { spec ->
        item { ListHeader(modifier = itemModifier(spec), transformation = itemTransformation(spec)) { Text("학급 일정") } }
        if (items.isEmpty()) {
            item {
                EmptyState(
                    CoreR.drawable.ic_event_available,
                    "등록된 일정이 없어요",
                    "학급 봇에 등록된 과제·수행평가가 여기에 표시돼요",
                    modifier = itemModifier(spec)
                )
            }
        } else {
            items.forEach { task ->
                val date = task.date
                item {
                    TitleCard(
                        title = { Text(task.task.type.ifBlank { "일정" }) },
                        time = { Text(task.badge) },
                        subtitle = if (date != null) ({ Text(date.format(deadlineFormat)) }) else null,
                        colors = urgencyColors(task.urgency),
                        modifier = itemModifier(spec),
                        transformation = itemTransformation(spec)
                    ) {
                        Text(task.task.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun urgencyColors(urgency: Dday.Urgency): CardColors {
    val c = MaterialTheme.colorScheme
    return when (urgency) {
        Dday.Urgency.URGENT -> CardDefaults.cardColors(
            containerColor = c.errorContainer,
            contentColor = c.onErrorContainer,
            titleColor = c.onErrorContainer,
            subtitleColor = c.onErrorContainer,
            timeColor = c.onErrorContainer
        )
        Dday.Urgency.SOON -> CardDefaults.cardColors(
            containerColor = c.tertiaryContainer,
            contentColor = c.onTertiaryContainer,
            titleColor = c.onTertiaryContainer,
            subtitleColor = c.onTertiaryContainer,
            timeColor = c.onTertiaryContainer
        )
        Dday.Urgency.OVERDUE -> CardDefaults.cardColors(
            containerColor = c.surfaceContainerLow,
            contentColor = c.onSurfaceVariant,
            titleColor = c.onSurfaceVariant,
            subtitleColor = c.onSurfaceVariant,
            timeColor = c.onSurfaceVariant
        )
        else -> CardDefaults.cardColors(timeColor = c.primary)
    }
}
