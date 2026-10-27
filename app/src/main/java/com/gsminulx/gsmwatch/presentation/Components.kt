package com.gsminulx.gsmwatch.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.gsminulx.gsmwatch.core.R as CoreR
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Wear M3 Expressive 기본 화면 골격: ScreenScaffold + TransformingLazyColumn.
 * 스크롤 인디케이터·로터리 스크롤·가장자리 모핑 효과가 자동으로 붙는다.
 * 각 아이템은 [itemModifier]/[itemTransformation] 을 적용해 스크롤 시 형태 변환을 받는다.
 */
@Composable
fun ScreenList(
    modifier: Modifier = Modifier,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val list: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            content(spec)
        }
    }
    if (edgeButton != null) {
        ScreenScaffold(scrollState = listState, edgeButton = edgeButton, modifier = modifier, content = list)
    } else {
        ScreenScaffold(scrollState = listState, modifier = modifier, content = list)
    }
}

/** TLC 아이템 공통 Modifier (가로 채움 + 스크롤 변환 높이). */
fun TransformingLazyColumnItemScope.itemModifier(spec: TransformationSpec): Modifier =
    Modifier.fillMaxWidth().transformedHeight(this, spec)

/** TLC 아이템 공통 표면 변환. */
fun TransformingLazyColumnItemScope.itemTransformation(spec: TransformationSpec): SurfaceTransformation =
    SurfaceTransformation(spec)

@Composable
fun WatchIcon(@DrawableRes id: Int, contentDescription: String? = null, modifier: Modifier = Modifier) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, modifier = modifier)
}

/** 아이콘 + 안내 문구로 된 빈 상태. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 끼니 이름 → 아이콘. */
@DrawableRes
fun mealIcon(type: String): Int = when {
    type.contains("조") -> CoreR.drawable.ic_sunny
    type.contains("석") -> CoreR.drawable.ic_dinner
    else -> CoreR.drawable.ic_lunch
}

/** "10월 27일 (화)" */
fun formatDateHeader(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    return "${date.monthValue}월 ${date.dayOfMonth}일 ($dow)"
}
