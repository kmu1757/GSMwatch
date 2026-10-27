package com.gsminulx.gsmwatch.core

/**
 * NEIS 응답 분류. "통신이 됐는지"와 "데이터가 있는지"를 구분한다.
 * (예전에는 데이터가 없으면 무조건 오프라인으로 봤기 때문에, 방학·주말처럼 NEIS 가 정상적으로
 * '해당 데이터 없음(INFO-200)' 을 돌려줘도 워치에 '오프라인' 이 떴다.)
 */
enum class NeisResult {
    /** 데이터 있음 */
    DATA,

    /** 통신 정상 + 해당 기간 데이터 없음 (INFO-200: 방학·주말·미등록 등) */
    EMPTY,

    /** 통신은 됐지만 API 오류 (인증키 오류·요청 제한 등) */
    API_ERROR,

    /** 요청 자체가 실패 (네트워크 끊김·타임아웃 등) */
    FAILED;

    /** NEIS 서버와 정상적으로 통신했는지. */
    val reachable: Boolean get() = this == DATA || this == EMPTY

    companion object {
        /** NEIS 응답 본문 분류. [dataKey] 는 데이터 배열 키("mealServiceDietInfo", "hisTimetable"). */
        fun classify(body: String?, dataKey: String): NeisResult = when {
            body.isNullOrBlank() -> FAILED
            body.contains("\"$dataKey\"") -> DATA
            body.contains("INFO-200") -> EMPTY
            else -> API_ERROR
        }

        /** 급식·시간표 두 요청 결과 → UI 에 알릴 문제(없으면 null). 하나라도 통신이 됐으면 정상으로 본다. */
        fun problemOf(vararg results: NeisResult): FetchProblem? = when {
            results.any { it.reachable } -> null
            results.any { it == API_ERROR } -> FetchProblem.API_ERROR
            else -> FetchProblem.OFFLINE
        }
    }
}

/** 조회 실패 종류 (캐시를 대신 표시 중일 때 사용자에게 보여줄 안내). */
enum class FetchProblem(val message: String) {
    OFFLINE("오프라인"),
    API_ERROR("NEIS 응답 오류"),
}
