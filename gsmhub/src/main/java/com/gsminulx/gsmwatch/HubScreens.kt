@file:OptIn(ExperimentalMaterial3Api::class)

package com.gsminulx.gsmwatch

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GSMhub", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(
                            "${state.grade}학년 ${state.classNum}반",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = { TextButton(onClick = { vm.refresh() }) { Text("새로고침") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.emoji, fontSize = 18.sp) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> TimetableScreen(state, onSelectWeek = vm::selectWeek, onEditSubject = vm::editCustomSubject)
                1 -> MealScreen(state, onSelectWeek = vm::selectWeek)
                2 -> DdayScreen(state)
                else -> SettingsScreen(state, onSave = vm::updateSettings)
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp), strokeWidth = 2.dp)
            }
            state.error?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                ) {
                    Text(
                        "⚠️ $msg",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("이번주", "다음주").forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun DaySelector(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Schedule.weekdays.forEach { day ->
            FilterChip(
                selected = selected == day,
                onClick = { onSelect(day) },
                label = { Text(Schedule.getDayName(day).first().toString(), fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

private fun defaultDay(): DayOfWeek {
    val today = LocalDate.now().dayOfWeek
    return if (today in Schedule.weekdays) today else DayOfWeek.MONDAY
}

@Composable
private fun TimetableScreen(
    state: HubUiState,
    onSelectWeek: (Int) -> Unit,
    onEditSubject: (DayOfWeek, Int, String) -> Unit
) {
    var day by rememberSaveable { mutableStateOf(defaultDay()) }
    var showExtra by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Period?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekSelector(state.selectedWeek, onSelectWeek)
        DaySelector(day) { day = it }

        val periods = Schedule.getVisibleScheduleForDay(state.timetable, day, includeExtra = showExtra)
        LazyColumn(
            modifier = Modifier.fillMaxSize().animateContentSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(periods) { period -> PeriodRow(period) { editing = period } }
            item {
                OutlinedButton(
                    onClick = { showExtra = !showExtra },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) { Text(if (showExtra) "－ 10·11교시 접기" else "＋ 10·11교시 추가") }
            }
        }
    }

    editing?.let { period ->
        val pNum = period.name.replace("교시", "").toIntOrNull() ?: 0
        EditSubjectDialog(
            title = "${Schedule.getDayName(day)} ${pNum}교시",
            initial = period.subject,
            onConfirm = { text -> onEditSubject(day, pNum, text); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun PeriodRow(period: Period, onClick: () -> Unit) {
    val filled = period.subject.isNotBlank()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (filled) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(period.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = period.subject.ifBlank { "＋ 과목 추가" },
                color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun MealScreen(state: HubUiState, onSelectWeek: (Int) -> Unit) {
    var day by rememberSaveable { mutableStateOf(defaultDay()) }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekSelector(state.selectedWeek, onSelectWeek)
        DaySelector(day) { day = it }

        val meals = state.meals[day] ?: emptyList()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (meals.isEmpty()) item { EmptyHint("급식 정보가 없습니다.") }
            else items(meals) { meal -> MealCard(meal) }
        }
    }
}

@Composable
private fun mealAccent(type: String): Color = when {
    type.contains("조") -> MaterialTheme.colorScheme.tertiary
    type.contains("중") -> MaterialTheme.colorScheme.primary
    type.contains("석") -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun MealCard(meal: MealInfo) {
    val accent = mealAccent(meal.type)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                Text(
                    meal.type,
                    color = accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(meal.menu, fontSize = 15.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun DdayScreen(state: HubUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.tasks.isEmpty()) {
            item { EmptyHint("등록된 D-Day 일정이 없습니다.\n(학급 봇 서버 연결 필요)") }
        } else {
            itemsIndexed(state.tasks) { index, task ->
                TaskCard(task, critical = index < 2 && task.deadline != "미정")
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskInfo, critical: Boolean) {
    val container = if (critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    val accent = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val onContainer = if (critical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                    Text(task.type, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
                Text(
                    if (task.deadline == "미정") "기한 미정" else "🔥 ${task.deadline}",
                    fontWeight = FontWeight.Bold, color = accent, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(task.content, fontSize = 15.sp, lineHeight = 22.sp, color = onContainer)
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
        Spacer(Modifier.height(8.dp))
        Text("내 교실 설정", fontWeight = FontWeight.Black, fontSize = 22.sp)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper(label = "${grade}학년", onMinus = { if (grade > 1) grade-- }, onPlus = { if (grade < 3) grade++ })
                Stepper(label = "${classNum}반", onMinus = { if (classNum > 1) classNum-- }, onPlus = { if (classNum < 15) classNum++ })
            }
        }

        FilledTonalButton(onClick = { onSave(grade, classNum) }, modifier = Modifier.fillMaxWidth()) {
            Text("저장하고 워치로 동기화", fontWeight = FontWeight.Bold)
        }

        Text(
            "저장하면 학년/반과 편집한 과목이 연결된 워치로 전송됩니다.\n다크모드는 기기 설정을 따릅니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
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
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(52.dp), contentPadding = PaddingValues(0.dp)) { Text("－", fontSize = 22.sp) }
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(52.dp), contentPadding = PaddingValues(0.dp)) { Text("＋", fontSize = 22.sp) }
    }
}

@Composable
private fun EditSubjectDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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
    Box(modifier = Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
