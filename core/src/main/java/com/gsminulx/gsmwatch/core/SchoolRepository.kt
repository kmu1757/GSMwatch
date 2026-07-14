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
 * 이번주 + 다음주 2주치를 한 번에 받아 요일 기준으로 분할한다.
 *
 * 캐시는 원문 NEIS 대신 **컴팩트 페이로드**(SyncPayload, 원산지 등 불필요 필드 제외)로 저장한다.
 * 폰이 워치로 push 하는 데이터도 같은 페이로드라, 캐시/전송이 하나의 포맷으로 통일된다.
 */
class SchoolRepository(private val prefs: SharedPreferences) {

    companion object {
        const val CACHE_PAYLOAD = "cached_payload"    // SyncPayload JSON (워치 fetch/폰 push 공유)
        const val CACHE_PAYLOAD_TS = "cached_payload_ts"
        // 폰 push 수신 시에만 갱신되는 신호 키(워치 자체 fetch 와 구분해 실행 중 반영을 트리거).
        const val PUSH_SIGNAL = "data_push_signal"
    }

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

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

    /** 날짜 기준 원시 데이터(캐시/전송 단위). */
    data class CachedData(
        val timeByDate: Map<LocalDate, Map<Int, String>>,
        val mealsByDate: Map<LocalDate, List<MealInfo>>,
        val tasks: List<TaskInfo>,
        val networkOk: Boolean
    )

    /** 한 주(월~금)의 원시 시간표(요일→교시→과목)와 급식(요일→끼니목록). */
    data class WeekData(
        val timetableRaw: Map<DayOfWeek, Map<Int, String>>,
        val meals: Map<DayOfWeek, List<MealInfo>>
    )

    /** networkOk: NEIS 유효 응답이 하나라도 있었는지(실패/에러 → false, 캐시 표시 중). */
    data class WeeklyData(
        val thisWeek: WeekData,
        val nextWeek: WeekData,
        val tasks: List<TaskInfo>,
        val networkOk: Boolean
    )

    /** 네트워크 없이 캐시만으로 즉시 데이터를 구성한다(불안정 통신 환경 우선 표시). */
    fun cachedWeeklyData(monday: LocalDate): WeeklyData {
        val c = loadCache()
        return buildWeeklyData(c, monday)
    }

    /** 2주치(이번주+다음주)를 네트워크에서 받아 갱신한다. 실패/무데이터 응답은 캐시를 오염시키지 않는다. */
    suspend fun fetchWeeklyData(grade: Int, classNum: Int, monday: LocalDate): WeeklyData =
        withContext(Dispatchers.IO) {
            val fromDate = monday.format(fmt)
            val toDate = monday.plusDays(11).format(fmt) // 이번주 월 ~ 다음주 금 (2주)

            var mealOk = false
            val mealFresh = try {
                neisApi.getMeals(fromDate = fromDate, toDate = toDate).also {
                    if (it.contains("\"mealServiceDietInfo\"")) mealOk = true
                }
            } catch (e: Exception) { "" }

            var timeOk = false
            val timeFresh = try {
                neisApi.getTimetable(grade = grade, classNum = classNum, fromDate = fromDate, toDate = toDate).also {
                    if (it.contains("\"hisTimetable\"")) timeOk = true
                }
            } catch (e: Exception) { "" }

            var tasksOk = false
            val tasksFresh = try {
                botApi.getTasks(grade, classNum).also {
                    if (it.trimStart().startsWith("[") && it != "[]" && !it.contains("\"error\"")) tasksOk = true
                }
            } catch (e: Exception) { "" }

            // 부분 실패 시 해당 항목만 이전 캐시 유지 (성공 항목만 갱신)
            val prev = loadCache()
            val merged = CachedData(
                timeByDate = if (timeOk) parseTimetableByDate(timeFresh) else prev.timeByDate,
                mealsByDate = if (mealOk) parseMealsByDate(mealFresh) else prev.mealsByDate,
                tasks = if (tasksOk) parseTasks(tasksFresh) else prev.tasks,
                networkOk = mealOk || timeOk
            )
            saveCache(merged)
            buildWeeklyData(merged, monday)
        }

    // --- 캐시 (컴팩트 페이로드) ---

    private fun loadCache(): CachedData =
        SyncPayload.decode(prefs.getString(CACHE_PAYLOAD, "") ?: "")
            ?: CachedData(emptyMap(), emptyMap(), emptyList(), false)

    private fun saveCache(data: CachedData) {
        prefs.edit()
            .putString(CACHE_PAYLOAD, SyncPayload.encode(data))
            .putLong(CACHE_PAYLOAD_TS, System.currentTimeMillis())
            .apply()
    }

    private fun buildWeeklyData(c: CachedData, monday: LocalDate): WeeklyData {
        val nextMonday = monday.plusDays(7)
        return WeeklyData(
            thisWeek = WeekData(sliceTime(c.timeByDate, monday), sliceMeal(c.mealsByDate, monday)),
            nextWeek = WeekData(sliceTime(c.timeByDate, nextMonday), sliceMeal(c.mealsByDate, nextMonday)),
            tasks = c.tasks,
            networkOk = c.networkOk
        )
    }

    private fun sliceTime(byDate: Map<LocalDate, Map<Int, String>>, weekMonday: LocalDate): Map<DayOfWeek, Map<Int, String>> {
        val out = mutableMapOf<DayOfWeek, Map<Int, String>>()
        for (i in 0..4) {
            val date = weekMonday.plusDays(i.toLong())
            byDate[date]?.let { out[date.dayOfWeek] = it }
        }
        return out
    }

    private fun sliceMeal(byDate: Map<LocalDate, List<MealInfo>>, weekMonday: LocalDate): Map<DayOfWeek, List<MealInfo>> {
        val out = mutableMapOf<DayOfWeek, List<MealInfo>>()
        for (i in 0..4) {
            val date = weekMonday.plusDays(i.toLong())
            byDate[date]?.let { out[date.dayOfWeek] = it }
        }
        return out
    }

    // --- 파서 (날짜 기준) ---

    /** 날짜 → 교시 → 과목명 (NEIS 원시). */
    fun parseTimetableByDate(json: String): Map<LocalDate, Map<Int, String>> {
        val parsed = mutableMapOf<LocalDate, MutableMap<Int, String>>()
        try {
            if (json.contains("\"hisTimetable\"")) {
                val rows = JSONObject(json).getJSONArray("hisTimetable").getJSONObject(1).getJSONArray("row")
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val date = LocalDate.parse(row.optString("ALL_TI_YMD"), fmt)
                    val p = row.optString("PERIO").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                    val content = row.optString("ITRT_CNTNT", "")
                    if (p > 0 && content.isNotEmpty()) {
                        parsed.getOrPut(date) { mutableMapOf() }[p] = content
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return parsed
    }

    /** 날짜 → 끼니목록. */
    fun parseMealsByDate(json: String): Map<LocalDate, List<MealInfo>> {
        val map = mutableMapOf<LocalDate, MutableList<MealInfo>>()
        if (!json.contains("\"mealServiceDietInfo\"")) return map
        try {
            val rows = JSONObject(json).getJSONArray("mealServiceDietInfo").getJSONObject(1).getJSONArray("row")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val date = LocalDate.parse(row.optString("MLSV_YMD"), fmt)
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
                map.getOrPut(date) { mutableListOf() }.add(MealInfo(type, menu, endTime))
            }
        } catch (e: Exception) { e.printStackTrace() }
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
