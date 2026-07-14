package com.gsminulx.gsmwatch

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * DataGSM OAuth 2.0 (Authorization Code + PKCE) — 폰 로그인.
 * 로그인 후 userinfo 의 student.grade / student.classNum 을 받아 학년/반 자동 설정에 쓴다.
 * (토큰 교환이 표준 form-urlencoded 가 아니라 JSON body 라 수동으로 처리.)
 */
object DataGsmAuth {
    private const val AUTHORIZE_URL = "https://oauth.authorization.datagsm.kr/v1/oauth/authorize"
    private const val TOKEN_URL = "https://oauth.authorization.datagsm.kr/v1/oauth/token"
    private const val USERINFO_URL = "https://oauth.resource.datagsm.kr/userinfo"
    // scope 파라미터는 서드파티 scope({appId}:{scope})용. 기본 self:read 는 scope 를 생략해야 하며
    // 명시적으로 scope=self:read 를 보내면 authorize 가 400 을 반환한다(실측 확인).

    fun isConfigured(): Boolean = BuildConfig.DATAGSM_CLIENT_ID.isNotBlank()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun buildAuthorizeUrl(state: String, codeChallenge: String): String =
        Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.DATAGSM_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.DATAGSM_REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build().toString()

    data class Student(val grade: Int, val classNum: Int, val name: String)

    /** code + PKCE verifier → 토큰 교환 → userinfo → 학생 정보. 실패 시 null. */
    suspend fun login(code: String, verifier: String): Student? = withContext(Dispatchers.IO) {
        try {
            val accessToken = exchangeToken(code, verifier) ?: return@withContext null
            fetchStudent(accessToken)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun exchangeToken(code: String, verifier: String): String? {
        val json = JSONObject()
            .put("grant_type", "authorization_code")
            .put("code", code)
            .put("client_id", BuildConfig.DATAGSM_CLIENT_ID)
            .put("redirect_uri", BuildConfig.DATAGSM_REDIRECT_URI)
            .put("code_verifier", verifier)
        if (BuildConfig.DATAGSM_CLIENT_SECRET.isNotBlank()) {
            json.put("client_secret", BuildConfig.DATAGSM_CLIENT_SECRET)
        }
        val body = json.toString()
        val conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) return null
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(resp).optString("access_token").ifBlank { null }
    }

    private fun fetchStudent(accessToken: String): Student? {
        val conn = (URL(USERINFO_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        if (conn.responseCode !in 200..299) return null
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(resp)
        if (!root.optBoolean("isStudent", false)) return null
        val s = root.optJSONObject("student") ?: return null
        return Student(
            grade = s.optInt("grade", 1),
            classNum = s.optInt("classNum", 1),
            name = s.optString("name", "")
        )
    }
}
