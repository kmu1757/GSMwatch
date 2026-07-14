package com.gsminulx.gsmwatch

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.SchoolRepository
import com.gsminulx.gsmwatch.core.SyncKeys
import org.json.JSONObject

/**
 * 폰 → 워치 동기화. 설정(학년/반/커스텀 과목)과, 폰이 받은 시간표/급식/D-Day 데이터를
 * Wear Data Layer 로 밀어 넣는다. (통신이 안정적인 폰을 데이터 공급/편집 주체로 삼는다.)
 */
object WearSync {

    /** 폰이 받아 캐시한 데이터(컴팩트 페이로드)를 워치로 전송. 워치는 자체 fetch 와 이 push 중 최신 것 사용. */
    fun pushData(context: Context, prefs: SharedPreferences) {
        val payload = prefs.getString(SchoolRepository.CACHE_PAYLOAD, null) ?: return
        val ts = prefs.getLong(SchoolRepository.CACHE_PAYLOAD_TS, System.currentTimeMillis())
        val request = PutDataMapRequest.create(SyncKeys.DATA_PATH).apply {
            dataMap.putString(SyncKeys.PAYLOAD, payload)
            dataMap.putLong(SyncKeys.TIMESTAMP, ts)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

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
