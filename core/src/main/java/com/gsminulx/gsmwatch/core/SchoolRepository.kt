package com.gsminulx.gsmwatch.core

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * NEIS 급식/시간표 + 학급 봇 D-Day 데이터의 단일 획득/파싱 지점 (워치/폰 공용).
 * 네트워크 실패 시 SharedPreferences 캐시로 폴백한다.
 */
class SchoolRepository(private val prefs: SharedPreferences) {

    private val neisApi: NeisApi = Retrofit.Builder()
        .baseUrl(Constants.NEIS_BASE_URL)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(NeisApi::class.java)

    private val botApi: BotApi = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(Constants.BOT_API_URL))
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(BotApi::class.java)

    private fun normalizeBaseUrl(url: String): String {
        val u = url.ifBlank { "http://127.0.0.1:8080" }
        return if (u.endsWith("/")) u else "$u/"
    }

    /**
     * 시간표는 원시 형태(요일 → 교시 → 과목명)로 반환한다. baseSchedule/커스텀 오버레이는 소비 측 책임.
     * networkOk: NEIS(급식/시간표) 호출 중 하나라도 네트워크에서 성공했는지 (실패 시 캐시 폴백 → false).
     */
    data class WeeklyData(
        val meals: Map<DayOfWeek, List<MealInfo>>,
        val timetable: Map<DayOfWeek, Map<Int, String>>,
        val tasks: List<TaskInfo>,
        val networkOk: Boolean
    )

    suspend fun fetchWeeklyData(grade: Int, classNum: Int, monday: LocalDate): WeeklyData =
        withContext(Dispatchers.IO) {
            val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
            val fromDate = monday.format(fmt)
            val toDate = monday.plusDays(4).format(fmt)

            var mealOk = false
            val mealJson = try {
                neisApi.getMeals(fromDate = fromDate, toDate = toDate).also { mealOk = true }
            } catch (e: Exception) { prefs.getString("cached_meal", "") ?: "" }

            var timeOk = false
            val timeJson = try {
                neisApi.getTimetable(grade = grade, classNum = classNum, fromDate = fromDate, toDate = toDate).also { timeOk = true }
            } catch (e: Exception) { prefs.getString("cached_time", "") ?: "" }

            val tasksJson = try {
                botApi.getTasks(grade, classNum)
            } catch (e: Exception) { prefs.getString("cached_tasks", "[]") ?: "[]" }

            if (mealJson.isNotEmpty()) prefs.edit().putString("cached_meal", mealJson).apply()
            if (timeJson.isNotEmpty()) prefs.edit().putString("cached_time", timeJson).apply()
            if (tasksJson.isNotBlank() && tasksJson != "[]") prefs.edit().putString("cached_tasks", tasksJson).apply()

            WeeklyData(
                meals = parseMeals(mealJson),
                timetable = parseTimetable(timeJson),
                tasks = parseTasks(tasksJson),
                networkOk = mealOk || timeOk
            )
        }

    // --- 파서 (기존 SchoolTimer 로직 이관, 동작 동일) ---

    /** 요일 → 교시 → 과목명 (NEIS 원시). */
    fun parseTimetable(json: String): Map<DayOfWeek, Map<Int, String>> {
        val parsedData = mutableMapOf<DayOfWeek, MutableMap<Int, String>>()
        if (json.contains("\"hisTimetable\"")) {
            val rows = JSONObject(json).getJSONArray("hisTimetable").getJSONObject(1).getJSONArray("row")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val date = LocalDate.parse(row.optString("ALL_TI_YMD"), DateTimeFormatter.ofPattern("yyyyMMdd"))
                val p = row.optString("PERIO").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                val content = row.optString("ITRT_CNTNT", "")
                if (p > 0 && content.isNotEmpty()) {
                    parsedData.getOrPut(date.dayOfWeek) { mutableMapOf() }[p] = content
                }
            }
        }
        return parsedData
    }

    fun parseMeals(json: String): Map<DayOfWeek, List<MealInfo>> {
        val map = mutableMapOf<DayOfWeek, MutableList<MealInfo>>()
        if (!json.contains("\"mealServiceDietInfo\"")) return map
        val rows = JSONObject(json).getJSONArray("mealServiceDietInfo").getJSONObject(1).getJSONArray("row")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val date = LocalDate.parse(row.optString("MLSV_YMD"), DateTimeFormatter.ofPattern("yyyyMMdd"))
            val type = row.optString("MMEAL_SC_NM").replace(" ", "")
            val menu = row.optString("DDISH_NM")
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("\\([0-9.]+\\)"), "")
                .replace(Regex("[0-9]+\\."), "")
                .trim()
            val endTime = when (type) {
                "조식" -> LocalTime.of(8, 40)
                "중식" -> LocalTime.of(13, 30)
                "석식" -> LocalTime.of(19, 30)
                else -> LocalTime.of(23, 59)
            }
            map.getOrPut(date.dayOfWeek) { mutableListOf() }.add(MealInfo(type, menu, endTime))
        }
        return map
    }

    fun parseTasks(json: String): List<TaskInfo> {
        val list = mutableListOf<TaskInfo>()
        if (json.isBlank() || json == "[]" || json.contains("\"error\"")) return list
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TaskInfo(
                        id = obj.optInt("id", 0),
                        type = obj.optString("task_type", ""),
                        deadline = obj.optString("deadline", "미정"),
                        content = obj.optString("content", "")
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }
}
