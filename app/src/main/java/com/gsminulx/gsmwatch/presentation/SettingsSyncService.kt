package com.gsminulx.gsmwatch.presentation

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.gsminulx.gsmwatch.core.Schedule
import com.gsminulx.gsmwatch.core.SyncKeys
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * 폰(gsmhub)에서 Wear Data Layer 로 밀어넣은 설정(학년/반 + 커스텀 과목)을 워치에서 수신해
 * 워치 로컬 SharedPreferences("GsmWatchPrefs")에 반영한다.
 */
class SettingsSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val prefs = getSharedPreferences("GsmWatchPrefs", Context.MODE_PRIVATE)
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != SyncKeys.PATH) continue

            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val editor = prefs.edit()
            editor.putInt(SyncKeys.GRADE, map.getInt(SyncKeys.GRADE, prefs.getInt(SyncKeys.GRADE, 1)))
            editor.putInt(SyncKeys.CLASS_NUM, map.getInt(SyncKeys.CLASS_NUM, prefs.getInt(SyncKeys.CLASS_NUM, 1)))
            map.getString(SyncKeys.CUSTOM_SUBJECTS)?.let { applyCustomSubjects(prefs, editor, it) }
            editor.apply()
        }
    }

    private fun applyCustomSubjects(prefs: SharedPreferences, editor: SharedPreferences.Editor, json: String) {
        try {
            // 폰이 보낸 것은 커스텀 과목 '전체 스냅샷'이다(폰이 편집 주체).
            // 폰에서 삭제한 과목이 워치에 남지 않도록 기존 custom_ 키를 모두 제거한 뒤 스냅샷을 다시 적용한다.
            for (existingKey in prefs.all.keys) {
                if (existingKey.startsWith("custom_")) editor.remove(existingKey)
            }
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next() // "MONDAY_1"
                try {
                    val underscore = k.lastIndexOf('_')
                    if (underscore <= 0) continue
                    val day = DayOfWeek.valueOf(k.substring(0, underscore))
                    val period = k.substring(underscore + 1).toIntOrNull() ?: continue
                    editor.putString(Schedule.customKey(day, period), obj.getString(k))
                } catch (e: Exception) {
                    // 잘못된 키 하나는 건너뛰고 나머지는 계속 적용
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
