@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.gsminulx.gsmwatch

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gsminulx.gsmwatch.core.MealInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.gsminulx.gsmwatch.core.R as CoreR

private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** 끼니별 Expressive 도형 + 색 역할. */
private data class MealStyle(val shape: Shape, val container: Color, val onContainer: Color)

@Composable
private fun mealStyle(type: String): MealStyle {
    val c = MaterialTheme.colorScheme
    return when {
        type.contains("조") -> MealStyle(MaterialShapes.Sunny.toShape(), c.tertiaryContainer, c.onTertiaryContainer)
        type.contains("석") -> MealStyle(MaterialShapes.Cookie6Sided.toShape(), c.secondaryContainer, c.onSecondaryContainer)
        else -> MealStyle(MaterialShapes.Cookie9Sided.toShape(), c.primaryContainer, c.onPrimaryContainer)
    }
}

@Composable
fun MealsTab(state: HubUiState, onSelectWeek: (Int) -> Unit, now: LocalDateTime = LocalDateTime.now()) {
    val today = now.toLocalDate()
    var day by rememberSaveable { mutableStateOf(defaultDay(today)) }
    val meals = state.meals[day].orEmpty()
    val date = dateOf(today, state.selectedWeek, day)
    // 다음 식사: 오늘이면 아직 끝나지 않은 첫 끼니
    val nextMeal = if (date == today) meals.firstOrNull { date.atTime(it.endTime).isAfter(now) } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { WeekSelector(state.selectedWeek, onSelectWeek) }
        item { DaySelector(week = state.selectedWeek, selected = day, onSelect = { day = it }, today = today) }
        if (meals.isEmpty()) {
            item { EmptyHint(CoreR.drawable.ic_restaurant, "급식 정보가 없어요", "주말·공휴일이거나 아직 등록되지 않았어요") }
        } else {
            items(meals) { meal -> MealCard(meal, isNext = meal == nextMeal) }
        }
    }
}

@Composable
private fun MealCard(meal: MealInfo, isNext: Boolean) {
    val style = mealStyle(meal.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(style.shape).background(style.container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(mealIcon(meal.type)),
                        contentDescription = null,
                        tint = style.onContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.type, style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        "${meal.endTime.format(hm)}까지",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isNext) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
                        Text(
                            "다음 식사",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(meal.menu, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
