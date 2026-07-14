package com.gsminulx.gsmwatch.core

import java.time.LocalTime

// 워치/폰 공용 데이터 모델. core 를 Compose 비의존으로 유지하기 위해 @Immutable 은 붙이지 않는다
// (Compose 재구성 최적화가 필요하면 소비 측에서 안정성 추론에 맡기거나 래핑한다).

data class Period(
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String = ""
)

data class MealInfo(
    val type: String,
    val menu: String,
    val endTime: LocalTime
)

data class TaskInfo(
    val id: Int,
    val type: String,
    val deadline: String,
    val content: String
)

// 네트워크 상태 래퍼 (로딩/성공/에러)
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
