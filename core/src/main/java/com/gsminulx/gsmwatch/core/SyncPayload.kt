package com.gsminulx.gsmwatch.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 캐시 저장 및 폰→워치 전송에 쓰는 컴팩트 데이터 포맷.
 * NEIS 원문(원산지/영양정보 등 대용량 필드 포함)을 그대로 두지 않고, 화면에 필요한 값만 담아
 * Wear Data Layer 의 데이터 크기 제한(약 100KB) 안에서 안전하게 전송한다.
 */
object SyncPayload {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun encode(data: SchoolRepository.CachedData): String {
        val root = JSONObject()
        root.put("nok", data.networkOk)

        val tt = JSONObject()
        for ((date, periods) in data.timeByDate) {
            val po = JSONObject()
            for ((p, subj) in periods) po.put(p.toString(), subj)
            tt.put(date.format(dateFmt), po)
        }
        root.put("tt", tt)

        val ml = JSONObject()
        for ((date, meals) in data.mealsByDate) {
            val arr = JSONArray()
            for (m in meals) {
                arr.put(JSONObject().put("t", m.type).put("m", m.menu).put("e", m.endTime.toString()))
            }
            ml.put(date.format(dateFmt), arr)
        }
        root.put("ml", ml)

        val tk = JSONArray()
        for (task in data.tasks) {
            tk.put(
                JSONObject().put("id", task.id).put("tp", task.type)
                    .put("dl", task.deadline).put("ct", task.content)
            )
        }
        root.put("tk", tk)

        return root.toString()
    }

    fun decode(json: String): SchoolRepository.CachedData? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)

            val timeByDate = mutableMapOf<LocalDate, Map<Int, String>>()
            root.optJSONObject("tt")?.let { tt ->
                tt.keys().forEach { dateKey ->
                    val date = LocalDate.parse(dateKey, dateFmt)
                    val po = tt.getJSONObject(dateKey)
                    val periods = mutableMapOf<Int, String>()
                    po.keys().forEach { pk -> pk.toIntOrNull()?.let { periods[it] = po.getString(pk) } }
                    timeByDate[date] = periods
                }
            }

            val mealsByDate = mutableMapOf<LocalDate, List<MealInfo>>()
            root.optJSONObject("ml")?.let { ml ->
                ml.keys().forEach { dateKey ->
                    val date = LocalDate.parse(dateKey, dateFmt)
                    val arr = ml.getJSONArray(dateKey)
                    val list = mutableListOf<MealInfo>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(MealInfo(o.optString("t"), o.optString("m"), LocalTime.parse(o.optString("e", "23:59"))))
                    }
                    mealsByDate[date] = list
                }
            }

            val tasks = mutableListOf<TaskInfo>()
            root.optJSONArray("tk")?.let { tk ->
                for (i in 0 until tk.length()) {
                    val o = tk.getJSONObject(i)
                    tasks.add(TaskInfo(o.optInt("id", 0), o.optString("tp"), o.optString("dl", "미정"), o.optString("ct")))
                }
            }

            SchoolRepository.CachedData(timeByDate, mealsByDate, tasks, root.optBoolean("nok", false))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
