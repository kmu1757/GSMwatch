package com.gsminulx.gsmwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gsminulx.gsmwatch.core.Dday
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.gsminulx.gsmwatch.core.R as CoreR

private val deadlineFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

private data class UrgencyColors(val container: Color, val content: Color, val badge: Color, val onBadge: Color)

@Composable
private fun urgencyColors(urgency: Dday.Urgency): UrgencyColors {
    val c = MaterialTheme.colorScheme
    return when (urgency) {
        Dday.Urgency.URGENT -> UrgencyColors(c.errorContainer, c.onErrorContainer, c.error, c.onError)
        Dday.Urgency.SOON -> UrgencyColors(c.tertiaryContainer, c.onTertiaryContainer, c.tertiary, c.onTertiary)
        Dday.Urgency.OVERDUE -> UrgencyColors(c.surfaceContainerLow, c.onSurfaceVariant, c.surfaceContainerHighest, c.onSurfaceVariant)
        else -> UrgencyColors(c.surfaceContainer, c.onSurface, c.primaryContainer, c.onPrimaryContainer)
    }
}

/** 학급 일정(D-Day): 마감 가까운 순, 남은 일수 기반 긴급도 색(워치와 같은 규칙). */
@Composable
fun DdayTab(state: HubUiState, today: LocalDate = LocalDate.now()) {
    val sorted = Dday.sort(state.tasks, today)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sorted.isEmpty()) {
            item {
                EmptyHint(
                    CoreR.drawable.ic_event_available,
                    "등록된 일정이 없어요",
                    "학급 디스코드 봇에 등록된 과제·수행평가가 여기에 표시돼요"
                )
            }
        } else {
            items(sorted) { item -> TaskCard(item) }
        }
    }
}

@Composable
private fun TaskCard(item: Dday.Item) {
    val colors = urgencyColors(item.urgency)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colors.badge,
                contentColor = colors.onBadge,
                modifier = Modifier.widthIn(min = 72.dp)
            ) {
                Text(
                    item.badge,
                    style = if (item.days != null) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.task.type.ifBlank { "일정" }, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text(item.task.content, style = MaterialTheme.typography.bodyLarge)
                item.date?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it.format(deadlineFormat), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
