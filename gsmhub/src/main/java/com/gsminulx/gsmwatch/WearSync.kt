package com.gsminulx.gsmwatch

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.SyncKeys
import org.json.JSONObject

/**
 * 폰 → 워치 단방향 설정 동기화. 학년/반 + 커스텀 과목 전체를 Wear Data Layer 로 밀어 넣는다.
 * (폰이 입력하기 편하므로 폰을 편집 주체로 삼는다.)
 */
object WearSync {

    fun pushSettings(context: Context, prefs: SharedPreferences) {
        val grade = prefs.getInt("grade", 1)
        val classNum = prefs.getInt("classNum", 1)

        // 저장된 커스텀 과목 전체를 JSON 으로 직렬화 ("MONDAY_1" 형태 키)
        val customJson = JSONObject()
        for (day in Schedule.weekdays) {
            for (period in 0..11) {
                val value = Schedule.getCustomSubject(prefs, day, period)
                if (value != null) customJson.put("${day.name}_$period", value)
            }
        }

        val request = PutDataMapRequest.create(SyncKeys.PATH).apply {
            dataMap.putInt(SyncKeys.GRADE, grade)
            dataMap.putInt(SyncKeys.CLASS_NUM, classNum)
            dataMap.putString(SyncKeys.CUSTOM_SUBJECTS, customJson.toString())
            // 동일 데이터는 DataClient 가 무시하므로 타임스탬프로 항상 갱신 이벤트 유발
            dataMap.putLong(SyncKeys.TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }
}
