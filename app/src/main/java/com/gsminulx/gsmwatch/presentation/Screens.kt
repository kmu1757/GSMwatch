package com.gsminulx.gsmwatch.presentation

import android.content.Context // 추가
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // 추가
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import android.widget.Toast
import androidx.compose.foundation.clickable // [추가]
import androidx.compose.foundation.layout.Row // [추가]
import java.time.DayOfWeek
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import java.time.LocalDate
import com.gsminulx.gsmwatch.analytics.AppAnalytics
import com.gsminulx.gsmwatch.core.Period
import com.gsminulx.gsmwatch.core.TaskInfo

@Composable
fun SettingsScreen(initialGrade: Int, initialClass: Int, onSave: (Int, Int) -> Unit) {
    var g by remember { mutableIntStateOf(initialGrade) }
    var c by remember { mutableIntStateOf(initialClass) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 30.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("⚙️ 내 교실 설정", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { if (g > 1) g-- }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.size(48.dp)) { Text("-", fontSize = 24.sp) }
                    Text("${g}학년", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Button(onClick = { if (g < 3) g++ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.size(48.dp)) { Text("+", fontSize = 24.sp) }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { if (c > 1) c-- }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.size(48.dp)) { Text("-", fontSize = 24.sp) }
                    Text("${c}반", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Button(onClick = { if (c < 15) c++ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.size(48.dp)) { Text("+", fontSize = 24.sp) }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Button(onClick = { onSave(g, c) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text(text = "저장하기", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WeeklyTimetableScreen(timer: SchoolTimer, onBack: () -> Unit) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    val coroutineScope = rememberCoroutineScope()

    var editingPeriod by remember { mutableStateOf<Pair<DayOfWeek, Period>?>(null) }

    LaunchedEffect(timer) {
        val visiblePeriodCount = timer.getVisibleScheduleForDay(LocalDate.now().dayOfWeek).count { it.subject.isNotEmpty() }
        analytics.trackTimetableLoaded(timer.grade, timer.classNum, visiblePeriodCount)
    }
    val pagerState = rememberPagerState(pageCount = { timer.weekdays.size })

    if (editingPeriod != null) {
        EditTimetableScreen(
            timer = timer,
            day = editingPeriod!!.first,
            period = editingPeriod!!.second,
            onSaved = { coroutineScope.launch { timer.fetchRealData() }; editingPeriod = null },
            onCancel = { editingPeriod = null }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val day = timer.weekdays[page]
                ScalingLazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 40.dp, bottom = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    item { CompactButton(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) { Text("돌아가기") } }
                    item { Text(text = "◀  ${timer.getDayName(day)}  ▶", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 16.dp)) }
                    item {
                        val daySchedule = timer.weeklyTimetable[day] ?: timer.baseSchedule
                        val displaySchedule = daySchedule
                            .filter { period ->
                                val isPeriod = period.name.contains("교시")
                                val isHidden = period.name in listOf("0교시", "10교시", "11교시")
                                val isFridayHidden = day == DayOfWeek.FRIDAY && period.name in listOf("8교시", "9교시")

                                // 교시이면서, 숨길 교시가 아니고, 금요일 8~9교시가 아닌 것만 남겨!
                                isPeriod && !isHidden && !isFridayHidden
                            }
                            .map { period ->
                                // 과목이 비어있다면 힌트 제공
                                if (period.subject.isEmpty()) {
                                    val hint = if (period.name in listOf("8교시", "9교시")) "➕ 방과후/동아리 추가" else "➕ 과목 추가"
                                    period.copy(subject = hint)
                                } else period
                            }

                        ScheduleCard(displaySchedule, onPeriodClick = { editingPeriod = Pair(day, it) })
                    }
                    item { Text("과목을 터치해서 변경하세요", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

@Composable
fun WeeklyMealsScreen(timer: SchoolTimer, onBack: () -> Unit) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }

    LaunchedEffect(timer) {
        val mealCount = timer.weeklyMeals.values.sumOf { meals -> meals.size }
        analytics.trackMealsLoaded(mealCount)
    }
    val pagerState = rememberPagerState(pageCount = { timer.weekdays.size })

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val day = timer.weekdays[page]
            ScalingLazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 40.dp, bottom = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item { CompactButton(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) { Text("돌아가기") } }
                item { Text(text = "◀  ${timer.getDayName(day)}  ▶", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 16.dp)) }

                val dayMeals = timer.weeklyMeals[day] ?: emptyList()
                if (dayMeals.isEmpty()) {
                    item { Text("급식 정보가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
                } else {
                    dayMeals.forEach { meal ->
                        item { MealCard(title = meal.type, menu = meal.menu, isHighlight = false) }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleCard(schedule: List<Period>, onPeriodClick: (Period) -> Unit = {}) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(0.95f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.Start) {
            schedule.forEach { period ->
                // '➕' 기호로 시작하면 힌트 문구라고 판단해!
                val isHint = period.subject.startsWith("➕")

                Text(
                    text = buildAnnotatedString {
                        append(period.name)
                        if (period.subject.isNotEmpty()) {
                            append("   ")
                            // 힌트면 색상을 살짝 연하게(onSurfaceVariant), 아니면 기본 흰색(onSurface)으로!
                            withStyle(style = SpanStyle(
                                color = if (isHint) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isHint) FontWeight.Normal else FontWeight.Medium
                            )) {
                                append(period.subject)
                            }
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPeriodClick(period) } // 클릭 시 편집 화면 실행
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MealCard(title: String, menu: String, isHighlight: Boolean) {
    val containerColor = if (isHighlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val titleColor = if (isHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val menuColor = if (isHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
            Text(text = menu, color = menuColor, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ActionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
        modifier = modifier.height(40.dp)
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
@Composable
fun EditTimetableScreen(
    timer: SchoolTimer,
    day: DayOfWeek,
    period: Period,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    val pNum = period.name.replace("교시", "").toIntOrNull() ?: 0
    var customSubject by remember { mutableStateOf(timer.sharedPref.getString("custom_${day.name}_$pNum", "") ?: "") }

    // 👉 [추가] 워치에서 스와이프(뒤로 가기) 시 앱이 꺼지지 않고 '취소' 동작을 하도록 가로챔!
    BackHandler { onCancel() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 30.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(text = "${timer.getDayName(day)} ${pNum}교시 변경", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
            }

            item {
                BasicTextField(
                    value = customSubject,
                    onValueChange = { customSubject = it },
                    // 👉 [수정] 글자색을 테마 글자색으로 변경하여 대비 높임
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), // 커서 색상도 돋보이게!
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp)) // 배경을 조금 더 어둡게 해서 대비를 줌
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        if (customSubject.isEmpty()) Text("과목명 입력", color = Color.LightGray, textAlign = TextAlign.Center)
                        innerTextField()
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        if (customSubject.isNotBlank()) timer.saveCustomSubject(day, pNum, customSubject)
                        else timer.clearCustomSubject(day, pNum)
                        analytics.trackTimetableSubjectEdited(day.name.lowercase(), pNum, customSubject.isNotBlank())
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(0.8f).padding(top = 15.dp)
                ) { Text("저장하기") }
            }

            item {
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { timer.clearCustomSubject(day, pNum); onSaved() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("초기화", fontSize = 12.sp)
                    }
                    Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text("취소", fontSize = 12.sp) // [수정] 오타 수정
                    }
                }
            }
        }
    }
}

// [추가] D-Day 일정 화면 UI 컴포저블
@Composable
fun DDayTasksScreen(timer: SchoolTimer, onBack: () -> Unit) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }

    LaunchedEffect(timer) {
        analytics.trackDdayLoaded(timer.ddayTasks.size)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 40.dp, bottom = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { CompactButton(onClick = onBack, modifier = Modifier.padding(bottom = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { Text("◀ 돌아가기") } }
            item { 
                Text(text = "D-Day 일정", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 16.dp)) 
            }

            val tasks = timer.ddayTasks
            if (tasks.isEmpty()) {
                item { 
                    Text("등록된 일정이 없습니다!\n(네트워크 확인 필요)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center) 
                }
            } else {
                itemsIndexed(tasks) { index, task ->
                    TaskCard(task = task, isHighlight = (index < 2 && task.deadline != "미정"))
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: TaskInfo, isHighlight: Boolean) {
    val isCritical = isHighlight && task.deadline != "미정" // 임박한 경우 강렬하게 표시
    val containerColor = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainer
    val typeColor = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
    val contentColor = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "[${task.type}]", color = typeColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(text = if (task.deadline == "미정") "기한: 미정" else "🔥 까지", color = typeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = task.content, color = contentColor, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            
            if (task.deadline != "미정") {
                Spacer(modifier = Modifier.height(2.dp))
                // D-Day 강조
                Text(text = "마감일: ${task.deadline}", color = typeColor.copy(alpha=0.8f), fontSize = 11.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}
