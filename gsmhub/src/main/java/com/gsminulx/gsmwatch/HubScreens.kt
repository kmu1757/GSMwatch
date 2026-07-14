@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsminulx.gsmwatch.core.MealInfo
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.TaskInfo
import java.time.DayOfWeek
import java.time.LocalDate

private data class TabItem(val label: String, val emoji: String)

private val TABS = listOf(
    TabItem("시간표", "📅"),
    TabItem("급식", "🍚"),
    TabItem("D-Day", "🔔"),
    TabItem("설정", "⚙️")
)

@Composable
fun HubApp(vm: HubViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GSMhub · ${state.grade}학년 ${state.classNum}반") },
                actions = {
                    TextButton(onClick = { vm.refresh() }) { Text("새로고침") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.emoji, fontSize = 18.sp) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> TimetableScreen(state, onEditSubject = vm::editCustomSubject)
                1 -> MealScreen(state)
                2 -> DdayScreen(state)
                else -> SettingsScreen(state, onSave = vm::updateSettings)
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }
            state.error?.let { msg ->
                Text(
                    text = "⚠️ $msg (캐시 표시)",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun DaySelector(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Schedule.weekdays.forEach { day ->
            FilterChip(
                selected = selected == day,
                onClick = { onSelect(day) },
                label = { Text(Schedule.getDayName(day).first().toString()) }
            )
        }
    }
}

private fun defaultDay(): DayOfWeek {
    val today = LocalDate.now().dayOfWeek
    return if (today in Schedule.weekdays) today else DayOfWeek.MONDAY
}

@Composable
private fun TimetableScreen(state: HubUiState, onEditSubject: (DayOfWeek, Int, String) -> Unit) {
    var day by rememberSaveable { mutableStateOf(defaultDay()) }
    var editing by remember { mutableStateOf<Period?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        DaySelector(selected = day, onSelect = { day = it })

        val periods = Schedule.getVisibleScheduleForDay(state.timetable, day)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "${Schedule.getDayName(day)} 시간표 · 항목을 눌러 과목 편집",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(periods) { period ->
                PeriodRow(period = period, onClick = { editing = period })
            }
        }
    }

    editing?.let { period ->
        val pNum = period.name.replace("교시", "").toIntOrNull() ?: 0
        EditSubjectDialog(
            title = "${Schedule.getDayName(day)} ${pNum}교시",
            initial = period.subject,
            onConfirm = { text ->
                onEditSubject(day, pNum, text)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun PeriodRow(period: Period, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(period.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = period.subject.ifBlank { "＋ 과목 추가" },
                color = if (period.subject.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                fontWeight = if (period.subject.isBlank()) FontWeight.Normal else FontWeight.Medium,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun MealScreen(state: HubUiState) {
    var day by rememberSaveable { mutableStateOf(defaultDay()) }

    Column(modifier = Modifier.fillMaxSize()) {
        DaySelector(selected = day, onSelect = { day = it })

        val meals = state.meals[day] ?: emptyList()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (meals.isEmpty()) {
                item { EmptyHint("급식 정보가 없습니다.") }
            } else {
                items(meals) { meal -> MealCard(meal) }
            }
        }
    }
}

@Composable
private fun MealCard(meal: MealInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(meal.type, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(meal.menu, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun DdayScreen(state: HubUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.tasks.isEmpty()) {
            item { EmptyHint("등록된 D-Day 일정이 없습니다.\n(학급 봇 서버 연결 필요)") }
        } else {
            itemsIndexed(state.tasks) { index, task ->
                TaskCard(task, isCritical = index < 2 && task.deadline != "미정")
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskInfo, isCritical: Boolean) {
    val container = if (isCritical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val accent = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("[${task.type}]", fontWeight = FontWeight.Black, color = accent, fontSize = 15.sp)
                Text(
                    if (task.deadline == "미정") "기한: 미정" else "🔥 ${task.deadline}",
                    fontWeight = FontWeight.Bold, color = accent, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(task.content, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun SettingsScreen(state: HubUiState, onSave: (Int, Int) -> Unit) {
    var grade by rememberSaveable(state.grade) { mutableIntStateOf(state.grade) }
    var classNum by rememberSaveable(state.classNum) { mutableIntStateOf(state.classNum) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("내 교실 설정", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        Stepper(label = "${grade}학년", onMinus = { if (grade > 1) grade-- }, onPlus = { if (grade < 3) grade++ })
        Stepper(label = "${classNum}반", onMinus = { if (classNum > 1) classNum-- }, onPlus = { if (classNum < 15) classNum++ })

        FilledTonalButton(
            onClick = { onSave(grade, classNum) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("저장하고 워치로 동기화") }

        Text(
            "저장하면 학년/반과 편집한 과목이 연결된 워치로 전송됩니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onMinus) { Text("－", fontSize = 20.sp) }
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onPlus) { Text("＋", fontSize = 20.sp) }
    }
}

@Composable
private fun EditSubjectDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // "＋ 과목 추가" 힌트가 들어온 경우 빈 값으로 시작
    var text by remember { mutableStateOf(if (initial.startsWith("＋")) "" else initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("과목명 (비우면 삭제)") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun EmptyHint(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}
