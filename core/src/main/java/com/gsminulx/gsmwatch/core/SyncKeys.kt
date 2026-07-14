package com.gsminulx.gsmwatch.core

/**
 * 폰↔워치 Wear Data Layer 동기화에 쓰는 경로/키. 양쪽이 동일 값을 써야 하므로 :core 에서 단일 관리.
 * 커스텀 과목은 JSON 객체 문자열로 직렬화한다: {"MONDAY_1":"수학","TUESDAY_3":"영어", ...}
 */
object SyncKeys {
    // 설정(학년/반/커스텀 과목) 동기화 경로
    const val PATH = "/gsm_settings"
    const val GRADE = "grade"
    const val CLASS_NUM = "classNum"
    const val CUSTOM_SUBJECTS = "custom_subjects"
    const val TIMESTAMP = "timestamp"

    // 폰 → 워치 데이터(시간표/급식/D-Day) 전송 경로
    const val DATA_PATH = "/gsm_data"
    const val PAYLOAD = "payload"           // SyncPayload JSON 문자열
}
