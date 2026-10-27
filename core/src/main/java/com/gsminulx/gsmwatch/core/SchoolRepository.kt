package com.gsminulx.gsmwatch.core

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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

        private const val TAG = "GsmRepo"

        // 워치는 블루투스(폰 경유)·Wi-Fi 전환으로 첫 연결이 느릴 수 있어 기본(10초)보다 여유 있게 잡는다.
        // 인스턴스마다 만들지 않고 연결 풀을 공유한다.
        private val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val neisApi: NeisApi = Retrofit.Builder()
        .baseUrl(Constants.NEIS_BASE_URL)
        .client(http)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(NeisApi::class.java)

    private val botApi: BotApi = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(Constants.BOT_API_URL))
        .client(http)
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

    /**
     * networkOk: 이번 조회에서 NEIS 와 정상 통신했는지('데이터 없음' 응답도 정상).
     * problem: 통신하지 못했을 때의 원인(캐시 표시 중 안내용). 정상이면 null.
     */
    data class WeeklyData(
        val thisWeek: WeekData,
        val nextWeek: WeekData,
        val tasks: List<TaskInfo>,
        val networkOk: Boolean,
        val problem: FetchProblem? = null
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

            val mealFresh = request("급식") { neisApi.getMeals(fromDate = fromDate, toDate = toDate) }
            val mealResult = NeisResult.classify(mealFresh, "mealServiceDietInfo")
            val mealOk = mealResult == NeisResult.DATA

            val timeFresh = request("시간표") {
                neisApi.getTimetable(grade = grade, classNum = classNum, fromDate = fromDate, toDate = toDate)
            }
            val timeResult = NeisResult.classify(timeFresh, "hisTimetable")
            val timeOk = timeResult == NeisResult.DATA

            val tasksFresh = request("D-Day") { botApi.getTasks(grade, classNum) }.orEmpty()
            val tasksOk = tasksFresh.trimStart().startsWith("[") && tasksFresh != "[]" && !tasksFresh.contains("\"error\"")

            if (mealResult == NeisResult.API_ERROR) Log.w(TAG, "NEIS 급식 응답 오류: ${mealFresh?.take(200)}")
            if (timeResult == NeisResult.API_ERROR) Log.w(TAG, "NEIS 시간표 응답 오류: ${timeFresh?.take(200)}")
            val problem = NeisResult.problemOf(mealResult, timeResult)
            Log.i(TAG, "fetch done meals=$mealResult timetable=$timeResult tasks=${if (tasksOk) "ok" else "-"} problem=$problem")

            // 부분 실패 시 해당 항목만 이전 캐시 유지 (성공 항목만 갱신)
            val prev = loadCache()
            val merged = CachedData(
                timeByDate = if (timeOk) parseTimetableByDate(timeFresh.orEmpty()) else prev.timeByDate,
                mealsByDate = if (mealOk) parseMealsByDate(mealFresh.orEmpty()) else prev.mealsByDate,
                tasks = if (tasksOk) parseTasks(tasksFresh) else prev.tasks,
                networkOk = problem == null
            )
            saveCache(merged)
            buildWeeklyData(merged, monday).copy(problem = problem)
        }

    /** 요청 하나를 실행하고, 실패하면 원인을 로그로 남긴 뒤 null (취소는 그대로 전파). */
    private suspend fun request(what: String, block: suspend () -> String): String? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what 요청 실패: ${e.javaClass.name}: ${e.message}", e)
            null
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
