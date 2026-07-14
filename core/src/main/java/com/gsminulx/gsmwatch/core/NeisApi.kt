package com.gsminulx.gsmwatch.core

import retrofit2.http.GET
import retrofit2.http.Query

// NEIS(나이스) 급식/시간표 오픈 API. 응답 원문(JSON 문자열)을 그대로 받아 org.json 으로 파싱한다
// (ScalarsConverter → String). 기존 검증된 파싱 로직을 그대로 재사용하기 위함.
interface NeisApi {
    @GET("hub/mealServiceDietInfo")
    suspend fun getMeals(
        @Query("KEY") key: String = Constants.API_KEY,
        @Query("Type") type: String = "json",
        @Query("pSize") pSize: Int = 1000,
        @Query("ATPT_OFCDC_SC_CODE") officeCode: String = Constants.ATPT_OFCDC_SC_CODE,
        @Query("SD_SCHUL_CODE") schoolCode: String = Constants.SD_SCHUL_CODE,
        @Query("MLSV_FROM_YMD") fromDate: String,
        @Query("MLSV_TO_YMD") toDate: String
    ): String

    @GET("hub/hisTimetable")
    suspend fun getTimetable(
        @Query("KEY") key: String = Constants.API_KEY,
        @Query("Type") type: String = "json",
        @Query("pSize") pSize: Int = 1000,
        @Query("ATPT_OFCDC_SC_CODE") officeCode: String = Constants.ATPT_OFCDC_SC_CODE,
        @Query("SD_SCHUL_CODE") schoolCode: String = Constants.SD_SCHUL_CODE,
        @Query("GRADE") grade: Int,
        @Query("CLASS_NM") classNum: Int,
        @Query("TI_FROM_YMD") fromDate: String,
        @Query("TI_TO_YMD") toDate: String
    ): String
}

// 학급 D-Day 일정 제공 디스코드 봇 서버 API (외부 서버, BOT_API_URL 로 주소 지정).
interface BotApi {
    @GET("api/tasks")
    suspend fun getTasks(
        @Query("grade") grade: Int,
        @Query("class_nm") classNum: Int
    ): String
}
