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
            map.getString(SyncKeys.CUSTOM_SUBJECTS)?.let { applyCustomSubjects(editor, it) }
            editor.apply()
        }
    }

    private fun applyCustomSubjects(editor: SharedPreferences.Editor, json: String) {
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()            // "MONDAY_1"
                val underscore = k.lastIndexOf('_')
                if (underscore <= 0) continue
                val day = DayOfWeek.valueOf(k.substring(0, underscore))
                val period = k.substring(underscore + 1).toIntOrNull() ?: continue
                editor.putString(Schedule.customKey(day, period), obj.getString(k))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
