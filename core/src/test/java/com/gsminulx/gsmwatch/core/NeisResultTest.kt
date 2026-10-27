package com.gsminulx.gsmwatch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeisResultTest {

    private val meals = "mealServiceDietInfo"

    @Test
    fun classify() {
        assertEquals(NeisResult.DATA, NeisResult.classify("""{"mealServiceDietInfo":[{"head":[]},{"row":[]}]}""", meals))
        // 통신은 정상이지만 해당 기간 데이터 없음 (방학·주말 등) → 오프라인이 아니다
        assertEquals(NeisResult.EMPTY, NeisResult.classify("""{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}""", meals))
        assertEquals(NeisResult.API_ERROR, NeisResult.classify("""{"RESULT":{"CODE":"ERROR-290","MESSAGE":"인증키가 유효하지 않습니다."}}""", meals))
        assertEquals(NeisResult.FAILED, NeisResult.classify(null, meals))
        assertEquals(NeisResult.FAILED, NeisResult.classify("", meals))
    }

    @Test
    fun problemOf() {
        assertNull(NeisResult.problemOf(NeisResult.DATA, NeisResult.FAILED))
        assertNull(NeisResult.problemOf(NeisResult.EMPTY, NeisResult.EMPTY))
        assertEquals(FetchProblem.API_ERROR, NeisResult.problemOf(NeisResult.API_ERROR, NeisResult.FAILED))
        assertEquals(FetchProblem.OFFLINE, NeisResult.problemOf(NeisResult.FAILED, NeisResult.FAILED))
    }
}
