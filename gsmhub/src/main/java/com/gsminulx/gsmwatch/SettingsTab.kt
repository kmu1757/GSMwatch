package com.gsminulx.gsmwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gsminulx.gsmwatch.core.R as CoreR

private const val MAX_GRADE = 3
private const val MAX_CLASS = 15

@Composable
fun SettingsTab(state: HubUiState, onSave: (Int, Int) -> Unit, onLogin: () -> Unit, onLogout: () -> Unit) {
    var grade by rememberSaveable(state.grade) { mutableIntStateOf(state.grade) }
    var classNum by rememberSaveable(state.classNum) { mutableIntStateOf(state.classNum) }
    val dirty = grade != state.grade || classNum != state.classNum

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeader("계정") }
        item { LoginCard(state.loggedInName, onLogin = onLogin, onLogout = onLogout) }

        item { SectionHeader("내 교실") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("학년", style = MaterialTheme.typography.titleSmall)
                    ConnectedToggleGroup(
                        count = MAX_GRADE,
                        selectedIndex = grade - 1,
                        onSelect = { grade = it + 1 },
                        colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) { i, _ ->
                        Text("${i + 1}학년")
                    }
                    Text("반", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledTonalIconButton(onClick = { if (classNum > 1) classNum-- }, enabled = classNum > 1) {
                            Icon(painterResource(CoreR.drawable.ic_remove), contentDescription = "반 줄이기")
                        }
                        Text(
                            "${classNum}반",
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(112.dp)
                        )
                        FilledTonalIconButton(onClick = { if (classNum < MAX_CLASS) classNum++ }, enabled = classNum < MAX_CLASS) {
                            Icon(painterResource(CoreR.drawable.ic_add), contentDescription = "반 늘리기")
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onSave(grade, classNum) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(painterResource(CoreR.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (dirty) "저장하고 워치로 동기화" else "워치로 다시 보내기")
            }
        }

        item { SectionHeader("정보") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        painterResource(CoreR.drawable.ic_watch),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("워치 동기화", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "학년/반과 편집한 과목, 받아온 시간표·급식이 연결된 워치로 전송돼요. " +
                                "색상과 다크모드는 기기 설정(배경화면 색)을 따릅니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "GSMhub ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(name: String?, onLogin: () -> Unit, onLogout: () -> Unit) {
    val loggedIn = name != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (loggedIn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (loggedIn) CoreR.drawable.ic_account else CoreR.drawable.ic_school),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        if (loggedIn) "$name 님" else "DataGSM 로그인",
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                    Text(
                        if (loggedIn) "학년/반이 자동으로 설정됐어요" else "GSM 계정으로 로그인하면 학년/반이 자동 설정돼요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (loggedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onLogin, modifier = Modifier.weight(1f)) { Text("다시 로그인") }
                    TextButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(CoreR.drawable.ic_logout), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("로그아웃")
                    }
                }
            } else {
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(CoreR.drawable.ic_login), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("GSM 계정으로 로그인")
                }
            }
        }
    }
}
