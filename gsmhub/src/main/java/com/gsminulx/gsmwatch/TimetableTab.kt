@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.gsminulx.gsmwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gsminulx.gsmwatch.core.DayStatus
import com.gsminulx.gsmwatch.core.DayStatusCalculator
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.RemainingFormat
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.caption
import com.gsminulx.gsmwatch.core.label
import com.gsminulx.gsmwatch.core.number
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.gsminulx.gsmwatch.core.R as CoreR

private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** RESUMED 상태에서만 1초마다 갱신되는 현재 시각. */
@Composable
fun rememberTickingNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
        }
    }
    return now
}

@Composable
fun TimetableTab(
    state: HubUiState,
    onSelectWeek: (Int) -> Unit,
    onEditSubject: (DayOfWeek, Int, String) -> Unit,
    now: LocalDateTime
) {
    val today = now.toLocalDate()
    var day by rememberSaveable { mutableStateOf(defaultDay(today)) }
    var showExtra by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Period?>(null) }

    val status = DayStatusCalculator.statusAt(now, state.thisWeekTimetable[today.dayOfWeek].orEmpty(), state.grade)
    val periods = Schedule.getVisibleScheduleForDay(state.timetable, day, includeExtra = showExtra)
    val viewingToday = state.selectedWeek == 0 && day == today.dayOfWeek
    val currentName = (status as? DayStatus.InPeriod)?.period?.name

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { NowCard(status) }
        item { WeekSelector(state.selectedWeek, onSelectWeek) }
        item { DaySelector(week = state.selectedWeek, selected = day, onSelect = { day = it }, today = today) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                periods.forEachIndexed { index, period ->
                    PeriodItem(
                        period = period,
                        index = index,
                        count = periods.size,
                        isNow = viewingToday && period.name == currentName,
                        onClick = { editing = period }
                    )
                }
            }
        }
        item {
            SegmentedListItem(
                checked = showExtra,
                onCheckedChange = { showExtra = it },
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                supportingContent = { Text("방과후·야자 교시 과목도 편집") },
                trailingContent = { Switch(checked = showExtra, onCheckedChange = null) }
            ) { Text("10·11교시 표시") }
        }
    }

    editing?.let { period ->
        val pNum = period.number ?: 0
        EditSubjectSheet(
            title = "${Schedule.getDayName(day)} ${pNum}교시",
            timeRange = "${period.start.format(hm)} – ${period.end.format(hm)}",
            initial = period.subject,
            onSave = { text -> onEditSubject(day, pNum, text.trim()); editing = null },
            onReset = { onEditSubject(day, pNum, ""); editing = null },
            onDismiss = { editing = null }
        )
    }
}

/** '지금' Hero 카드: 상태 이름 + 큰 카운트다운 + 물결 진행률(M3 Expressive). */
@Composable
fun NowCard(status: DayStatus, modifier: Modifier = Modifier) {
    val icon = when (status) {
        is DayStatus.InPeriod -> if (status.period.number != null) CoreR.drawable.ic_school else CoreR.drawable.ic_timer
        is DayStatus.UntilNext -> if (status.next.name.startsWith("기상")) CoreR.drawable.ic_alarm else CoreR.drawable.ic_schedule
        is DayStatus.WeekendDorm -> CoreR.drawable.ic_bedtime
    }
    val remaining = if (status.remaining.toHours() >= 24) RemainingFormat.korean(status.remaining)
    else RemainingFormat.clock(status.remaining)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("지금", style = MaterialTheme.typography.labelMedium)
                    Text(status.label, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    remaining,
                    style = MaterialTheme.typography.displayMediumEmphasized.copy(fontFeatureSettings = "tnum")
                )
                Spacer(Modifier.size(8.dp))
                Text(status.caption, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            status.progress?.let { p ->
                Spacer(Modifier.height(12.dp))
                LinearWavyProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                )
            }
        }
    }
}

@Composable
private fun PeriodItem(period: Period, index: Int, count: Int, isNow: Boolean, onClick: () -> Unit) {
    val filled = period.subject.isNotBlank()
    val colors = if (isNow) {
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            supportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    } else ListItemDefaults.segmentedColors()

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = colors,
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (isNow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${period.number ?: "-"}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        },
        supportingContent = {
            Text(if (isNow) "지금 · ${period.start.format(hm)} – ${period.end.format(hm)}" else "${period.start.format(hm)} – ${period.end.format(hm)}")
        },
        trailingContent = {
            Icon(
                painterResource(if (filled) CoreR.drawable.ic_edit else CoreR.drawable.ic_add),
                contentDescription = if (filled) "편집" else "과목 추가",
                modifier = Modifier.size(20.dp)
            )
        }
    ) {
        Text(
            when {
                filled -> period.subject
                period.name in listOf("8교시", "9교시") -> "방과후·동아리 추가"
                else -> "과목 추가"
            },
            color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (filled) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
        )
    }
}

/** 과목 편집 바텀시트 (비우고 저장하거나 '기본값으로' 를 누르면 NEIS 과목으로 되돌린다). */
@Composable
private fun EditSubjectSheet(
    title: String,
    timeRange: String,
    initial: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(timeRange, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("과목명") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onReset) {
                    Icon(painterResource(CoreR.drawable.ic_restart), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("기본값으로")
                }
                Button(onClick = { onSave(text) }) {
                    Icon(painterResource(CoreR.drawable.ic_check), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("저장")
                }
            }
        }
    }
}
