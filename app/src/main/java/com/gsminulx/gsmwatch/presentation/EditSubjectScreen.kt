package com.gsminulx.gsmwatch.presentation

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.gsminulx.gsmwatch.analytics.AppAnalytics
import com.gsminulx.gsmwatch.core.Schedule
import java.time.DayOfWeek
import com.gsminulx.gsmwatch.core.R as CoreR

private const val SUBJECT_INPUT_KEY = "subject"

/**
 * 과목 편집. 워치 화면 위 텍스트필드 대신 시스템 RemoteInput(키보드·음성·필기)을 띄운다.
 * 저장/초기화 후에는 성공 확인 애니메이션을 보여주고 [onDone] 으로 돌아간다.
 */
@Composable
fun EditSubjectScreen(
    timer: SchoolTimer,
    day: DayOfWeek,
    period: Int,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val analytics = remember(context) { AppAnalytics(context) }
    val title = "${Schedule.getDayName(day)} ${period}교시"
    val custom = timer.customSubject(day, period)
    val shown = timer.weeklyTimetable[day]?.firstOrNull { it.name == "${period}교시" }?.subject.orEmpty()
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(SUBJECT_INPUT_KEY)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) {
            timer.saveCustomSubject(day, period, text)
            analytics.trackTimetableSubjectEdited(day.name.lowercase(), period, true)
            timer.reloadFromCache()
            confirmation = "저장됨"
        }
    }

    ScreenList { spec ->
        item { ListHeader(modifier = itemModifier(spec), transformation = itemTransformation(spec)) { Text(title) } }
        item {
            Card(
                modifier = itemModifier(spec),
                transformation = itemTransformation(spec),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    shown.ifEmpty { "비어 있음" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (shown.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (custom != null) "직접 입력한 과목" else "NEIS 시간표",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Button(
                onClick = {
                    val remoteInput = RemoteInput.Builder(SUBJECT_INPUT_KEY)
                        .setLabel("과목명")
                        .wearableExtender {
                            setEmojisAllowed(false)
                            setInputActionType(EditorInfo.IME_ACTION_DONE)
                        }
                        .build()
                    val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                    RemoteInputIntentHelper.putTitleExtra(intent, title)
                    launcher.launch(intent)
                },
                icon = { WatchIcon(CoreR.drawable.ic_keyboard, null) },
                secondaryLabel = { Text("키보드 · 음성") },
                modifier = itemModifier(spec),
                transformation = itemTransformation(spec)
            ) { Text("직접 입력") }
        }
        if (custom != null) {
            item {
                FilledTonalButton(
                    onClick = {
                        timer.clearCustomSubject(day, period)
                        analytics.trackTimetableSubjectEdited(day.name.lowercase(), period, false)
                        timer.reloadFromCache()
                        confirmation = "되돌림"
                    },
                    icon = { WatchIcon(CoreR.drawable.ic_restart, null) },
                    secondaryLabel = { Text("NEIS 과목으로") },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = itemModifier(spec),
                    transformation = itemTransformation(spec)
                ) { Text("기본값으로") }
            }
        }
    }

    SuccessConfirmationDialog(
        visible = confirmation != null,
        onDismissRequest = {
            confirmation = null
            onDone()
        },
        curvedText = { curvedText(confirmation.orEmpty()) }
    )
}
