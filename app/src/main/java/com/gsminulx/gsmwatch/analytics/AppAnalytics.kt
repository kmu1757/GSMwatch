package com.gsminulx.gsmwatch.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AppAnalytics(context: Context) {
    // 분석은 부가 기능이므로 초기화 실패(테스트·미설정 환경 등)가 앱 동작을 막지 않게 한다.
    private val firebaseAnalytics: FirebaseAnalytics? =
        runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull()

    fun trackScreen(screenName: String) {
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        })
    }

    fun trackSettingsSaved(grade: Int, classNum: Int) {
        firebaseAnalytics?.logEvent("settings_saved", Bundle().apply {
            putLong("grade", grade.toLong())
            putLong("class_num", classNum.toLong())
        })
        firebaseAnalytics?.setUserProperty("grade", grade.toString())
        firebaseAnalytics?.setUserProperty("class_num", classNum.toString())
    }

    fun trackTimetableLoaded(grade: Int, classNum: Int, visiblePeriodCount: Int) {
        firebaseAnalytics?.logEvent("timetable_loaded", Bundle().apply {
            putLong("grade", grade.toLong())
            putLong("class_num", classNum.toLong())
            putLong("visible_period_count", visiblePeriodCount.toLong())
            putLong("has_data", if (visiblePeriodCount > 0) 1L else 0L)
        })
    }

    fun trackMealsLoaded(mealCount: Int) {
        firebaseAnalytics?.logEvent("meals_loaded", Bundle().apply {
            putLong("meal_count", mealCount.toLong())
            putLong("has_data", if (mealCount > 0) 1L else 0L)
        })
    }

    fun trackDdayLoaded(taskCount: Int) {
        firebaseAnalytics?.logEvent("dday_loaded", Bundle().apply {
            putLong("task_count", taskCount.toLong())
            putLong("has_data", if (taskCount > 0) 1L else 0L)
        })
    }

    fun trackTimetableSubjectEdited(dayOfWeek: String, periodNum: Int, hasValue: Boolean) {
        firebaseAnalytics?.logEvent("timetable_subject_edited", Bundle().apply {
            putString("day_of_week", dayOfWeek)
            putLong("period_num", periodNum.toLong())
            putLong("has_value", if (hasValue) 1L else 0L)
        })
    }
}
