package com.gsminulx.gsmwatch.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PickerGroup
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.rememberPickerState
import com.gsminulx.gsmwatch.core.R as CoreR

const val MAX_GRADE = 3
const val MAX_CLASS = 15

/**
 * 내 교실 설정: 학년/반을 로터리·스와이프 Picker 로 고르고 EdgeButton 으로 저장한다.
 * (폰 GSMhub 에서 DataGSM 로그인하면 자동으로 설정·동기화된다.)
 */
@Composable
fun SettingsScreen(initialGrade: Int, initialClass: Int, onSave: (Int, Int) -> Unit) {
    val gradeState = rememberPickerState(
        initialNumberOfOptions = MAX_GRADE,
        initiallySelectedIndex = (initialGrade - 1).coerceIn(0, MAX_GRADE - 1),
        shouldRepeatOptions = false
    )
    val classState = rememberPickerState(
        initialNumberOfOptions = MAX_CLASS,
        initiallySelectedIndex = (initialClass - 1).coerceIn(0, MAX_CLASS - 1),
        shouldRepeatOptions = false
    )
    var selectedColumn by rememberSaveable { mutableIntStateOf(0) }
    var saved by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 28.dp, bottom = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("내 교실", style = MaterialTheme.typography.titleMedium)
                Text(
                    "폰에서 로그인하면 자동 설정",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    ColumnLabel("학년", selected = selectedColumn == 0)
                    Spacer(Modifier.width(12.dp))
                    ColumnLabel("반", selected = selectedColumn == 1)
                }
                PickerGroup(
                    selectedPickerState = if (selectedColumn == 0) gradeState else classState,
                    autoCenter = false,
                    modifier = Modifier.weight(1f)
                ) {
                    PickerGroupItem(
                        pickerState = gradeState,
                        selected = selectedColumn == 0,
                        onSelected = { selectedColumn = 0 },
                        contentDescription = { "${gradeState.selectedOptionIndex + 1}학년" },
                        modifier = Modifier.size(width = 56.dp, height = 96.dp)
                    ) { optionIndex, _ ->
                        Text("${optionIndex + 1}", style = MaterialTheme.typography.numeralMedium)
                    }
                    PickerGroupItem(
                        pickerState = classState,
                        selected = selectedColumn == 1,
                        onSelected = { selectedColumn = 1 },
                        contentDescription = { "${classState.selectedOptionIndex + 1}반" },
                        modifier = Modifier.size(width = 56.dp, height = 96.dp)
                    ) { optionIndex, _ ->
                        Text("${optionIndex + 1}", style = MaterialTheme.typography.numeralMedium)
                    }
                }
            }
            EdgeButton(
                onClick = { saved = true },
                buttonSize = EdgeButtonSize.ExtraSmall,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                WatchIcon(CoreR.drawable.ic_check, "저장")
            }
        }
    }

    SuccessConfirmationDialog(
        visible = saved,
        onDismissRequest = {
            saved = false
            onSave(gradeState.selectedOptionIndex + 1, classState.selectedOptionIndex + 1)
        },
        curvedText = { curvedText("저장됨") }
    )
}

@Composable
private fun ColumnLabel(text: String, selected: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(56.dp)
    )
}
