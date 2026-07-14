package com.gsminulx.gsmwatch.core

object Constants {
    const val NEIS_BASE_URL = "https://open.neis.go.kr/"
    // 전남광주통합특별시교육청(광주). 광주+전남 교육청 통합 후에도 시도교육청 코드 F10 은 유지됨.
    const val ATPT_OFCDC_SC_CODE = "F10"
    // 광주소프트웨어마이스터고. 교육청 통합으로 표준학교코드가 변경됨 (구 7380292 → 7140392, 2026).
    const val SD_SCHUL_CODE = "7140392"
    val API_KEY: String = BuildConfig.DATA_GSM_API_KEY
    val BOT_API_URL: String = BuildConfig.BOT_API_URL
}
