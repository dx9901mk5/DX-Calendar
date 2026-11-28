package com.dx.calendar.data.sync

import android.content.Context
import com.dx.calendar.util.PrivacyCryptoManager
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CustomOAuthUiState(
    val clientId: String = "",
    val hasClientSecret: Boolean = false,
    val authorizedEmail: String = "",
    val isAuthorized: Boolean = false,
    val isAuthorizing: Boolean = false,
    val lastError: String = ""
)

/**
 * Google Tasks 雙軌存取與 BYO（自訂 Google Cloud OAuth 憑證）授權引擎：
 *
 * 1. 開發者本人帳號（以 SHA-256 雜湊比對，公開程式碼絕不外洩明文 Email）：
 *    自動辨識並走原生 GoogleAuthUtil 直連同步，完全免手動填寫。
 * 2. 一般使用者（BYO 自訂憑證模式）：
 *    不共用開發者 GCP 額度或未審查提示；使用者依彈出教學於自己的 Google Cloud 免費建立
 *    「電腦版應用程式 (Desktop app)」OAuth 用戶端並貼入 Client ID（與 Secret），
 *    透過標準 OAuth 2.0 + PKCE (S256) + 本機 127.0.0.1 Loopback 回呼完成授權，
 *    並以 Android Keystore AES-256-GCM 加密保存於本機。
 */
object GoogleTasksCustomOAuthManager {

    private const val PREF_NAME = "dx_google_tasks_byo_oauth_v1"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_SECRET = "client_secret"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_AUTH_EMAIL = "authorized_email"

    const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val GOOGLE_USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo"
    const val GOOGLE_CLOUD_TASKS_API_URL = "https://console.cloud.google.com/apis/library/tasks.googleapis.com"
    const val TASKS_OAUTH_SCOPES = "https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/userinfo.email"

    // 開發者專屬帳號之 SHA-256 雜湊清單（不儲存任何明文信箱）
    private val OWNER_EMAIL_SHA256_SET = setOf(
        "f6ba4f97f9775fe56f69ce164fec52685c8cf29980ee6532881a67f4c7e77232"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isOwnerEmail(email: String?): Boolean {
        val normalized = email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.isBlank()) return false
        return OWNER_EMAIL_SHA256_SET.contains(sha256Hex(normalized))
    }

    fun findOwnerEmail(candidates: Iterable<String>): String? {
        return candidates.firstOrNull { isOwnerEmail(it) }?.trim()
    }

    fun isValidGoogleClientId(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.length >= 20 &&
            trimmed.endsWith(".apps.googleusercontent.com", ignoreCase = true) &&
            !trimmed.contains(" ") &&
            !trimmed.contains("\n")
    }

    /**
     * 支援使用者直接貼入從 Google Cloud Console 下載的 `client_secret_*.json` 全文，
     * 或只貼入純文字 `Client ID`；若為 JSON 則自動解析並回傳 `(clientId, clientSecret)`。
     */
    fun parseGoogleClientJson(rawInput: String): Pair<String, String>? {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val root = JsonParser.parseString(trimmed).asJsonObject
            val container = root.getAsJsonObject("installed")
                ?: root.getAsJsonObject("web")
                ?: root
            val cid = container.get("client_id")?.asString?.trim().orEmpty()
            val csec = container.get("client_secret")?.asString?.trim().orEmpty()
            if (cid.isNotBlank()) cid to csec else null
        }.getOrNull()
    }

    /**
     * 產生標準 RFC 7636 PKCE (S256) 的 (codeVerifier, codeChallenge)
     */
    fun generatePkcePair(random: SecureRandom = SecureRandom()): Pair<String, String> {
        val verifierBytes = ByteArray(48)
        random.nextBytes(verifierBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return verifier to challenge
    }

    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
        loginHint: String = ""
    ): String {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        return buildString {
            append(GOOGLE_AUTH_ENDPOINT)
            append("?client_id=").append(enc(clientId.trim()))
            append("&redirect_uri=").append(enc(redirectUri))
            append("&response_type=code")
            append("&scope=").append(enc(TASKS_OAUTH_SCOPES))
            append("&code_challenge=").append(enc(codeChallenge))
            append("&code_challenge_method=S256")
            append("&access_type=offline")
            append("&prompt=consent")
            append("&state=").append(enc(state))
            if (loginHint.isNotBlank()) {
                append("&login_hint=").append(enc(loginHint.trim()))
            }
        }
    }

    fun parseQueryParams(requestTarget: String): Map<String, String> {
        val query = requestTarget.substringAfter("?", "")
        if (query.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        query.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = runCatching { URLDecoder.decode(pair.substring(0, idx), "UTF-8") }.getOrDefault(pair.substring(0, idx))
                val v = runCatching { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }.getOrDefault(pair.substring(idx + 1))
                result[k] = v
            }
        }
        return result
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun readDecrypted(context: Context, key: String): String {
        val raw = prefs(context).getString(key, "").orEmpty()
        if (raw.isBlank()) return ""
        val dec = PrivacyCryptoManager.decryptIfNeeded(raw)
        return if (dec == "🔒 [隱私加密內容]") "" else dec
    }

    private fun writeEncrypted(context: Context, editor: android.content.SharedPreferences.Editor, key: String, value: String) {
        if (value.isBlank()) {
            editor.remove(key)
        } else {
            editor.putString(key, PrivacyCryptoManager.encryptIfNeeded(value, isPrivacy = true))
        }
    }

    fun getUiState(context: Context, isAuthorizing: Boolean = false, lastError: String = ""): CustomOAuthUiState {
        val clientId = readDecrypted(context, KEY_CLIENT_ID)
        val clientSecret = readDecrypted(context, KEY_CLIENT_SECRET)
        val refreshToken = readDecrypted(context, KEY_REFRESH_TOKEN)
        val accessToken = readDecrypted(context, KEY_ACCESS_TOKEN)
        val email = readDecrypted(context, KEY_AUTH_EMAIL)
        val hasTokens = refreshToken.isNotBlank() || accessToken.isNotBlank()
        return CustomOAuthUiState(
            clientId = clientId,
            hasClientSecret = clientSecret.isNotBlank(),
            authorizedEmail = email,
            isAuthorized = clientId.isNotBlank() && hasTokens,
            isAuthorizing = isAuthorizing,
            lastError = lastError
        )
    }

    fun hasClientConfig(context: Context): Boolean = readDecrypted(context, KEY_CLIENT_ID).isNotBlank()

    fun isAuthorized(context: Context): Boolean = getUiState(context).isAuthorized

    fun getSavedClientSecret(context: Context): String = readDecrypted(context, KEY_CLIENT_SECRET)

    fun saveClientConfig(context: Context, clientId: String, clientSecret: String) {
        val prevId = readDecrypted(context, KEY_CLIENT_ID)
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()
        prefs(context).edit().apply {
            writeEncrypted(context, this, KEY_CLIENT_ID, cleanId)
            writeEncrypted(context, this, KEY_CLIENT_SECRET, cleanSecret)
            // 若更換了不同 Client ID，清除舊的 token 強制重新授權
            if (prevId.isNotBlank() && prevId != cleanId) {
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_EXPIRES_AT)
                remove(KEY_AUTH_EMAIL)
            }
        }.apply()
    }

    fun clearAll(context: Context) {
        cancelPendingAuthorization()
        prefs(context).edit().clear().apply()
    }

    fun cancelPendingAuthorization() {
        runCatching { activeServerSocket?.close() }
        activeServerSocket = null
    }

    /**
     * 啟動本機 127.0.0.1 Loopback + PKCE (S256) OAuth 2.0 授權流程：
     * 1. 於本機隨機可用埠啟動短暫 HTTP 監聽器（最長等候 180 秒）
     * 2. 透過回呼開啟系統瀏覽器讓使用者登入自己的 Google Cloud Desktop OAuth 用戶端
     * 3. 接收授權碼 code 後立即換取 access_token 與 refresh_token 並加密存入本機
     */
    suspend fun authorizeWithLoopback(
        context: Context,
        rawClientId: String,
        rawClientSecret: String,
        loginHint: String = "",
        onOpenAuthUrl: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val parsedJson = parseGoogleClientJson(rawClientId)
            val clientId = (parsedJson?.first ?: rawClientId).trim()
            val clientSecret = (parsedJson?.second?.takeIf { it.isNotBlank() } ?: rawClientSecret).trim()

            if (!isValidGoogleClientId(clientId)) {
                throw IllegalArgumentException(
                    "Client ID 格式不正確，請確認結尾為 .apps.googleusercontent.com（或直接貼上 Google 下載的 client_secret_*.json 內容）"
                )
            }

            saveClientConfig(context, clientId, clientSecret)
            cancelPendingAuthorization()

            val (codeVerifier, codeChallenge) = generatePkcePair()
            val stateBytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
            val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)

            val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply {
                soTimeout = 180_000
            }
            activeServerSocket = server

            val port = server.localPort
            val redirectUri = "http://127.0.0.1:$port/callback"
            val authUrl = buildAuthorizationUrl(
                clientId = clientId,
                redirectUri = redirectUri,
                codeChallenge = codeChallenge,
                state = state,
                loginHint = loginHint
            )

            withContext(Dispatchers.Main) {
                onOpenAuthUrl(authUrl)
            }

            var authCode: String? = null
            var authError: String? = null
            try {
                val deadline = System.currentTimeMillis() + 180_000L
                while (System.currentTimeMillis() < deadline && authCode == null && authError == null) {
                    val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1_000L).toInt()
                    server.soTimeout = remaining
                    val socket = server.accept()
                    socket.use { client ->
                        client.soTimeout = 5_000
                        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        // 讀完剩餘標頭，避免部分瀏覽器提早重置連線
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val target = requestLine.split(" ").getOrNull(1).orEmpty()
                        if (!target.startsWith("/callback")) {
                            val notFound = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                            client.getOutputStream().write(notFound.toByteArray(Charsets.UTF_8))
                            client.getOutputStream().flush()
                        } else {
                            val params = parseQueryParams(target)
                            val returnedState = params["state"].orEmpty()
                            val err = params["error"]
                            val code = params["code"]

                            val html = when {
                                !err.isNullOrBlank() -> {
                                    authError = "Google 授權取消或遭拒 ($err)"
                                    buildCallbackHtml(false, "授權未完成（$err），請返回 DX日曆重試。")
                                }
                                returnedState != state -> {
                                    authError = "授權驗證碼 (state) 不符，請重新嘗試"
                                    buildCallbackHtml(false, "安全性驗證失敗，請返回 DX日曆重試。")
                                }
                                code.isNullOrBlank() -> {
                                    authError = "未收到 Google 授權碼 (code)"
                                    buildCallbackHtml(false, "未取得授權碼，請返回 DX日曆重試。")
                                }
                                else -> {
                                    authCode = code
                                    buildCallbackHtml(true, "Google Tasks 授權成功！您現在可以關閉此分頁返回 DX日曆。")
                                }
                            }
                            val bodyBytes = html.toByteArray(Charsets.UTF_8)
                            val respHeader = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=UTF-8\r\n" +
                                "Content-Length: ${bodyBytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            client.getOutputStream().write(respHeader.toByteArray(Charsets.UTF_8))
                            client.getOutputStream().write(bodyBytes)
                            client.getOutputStream().flush()
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {
                throw IllegalStateException("等候瀏覽器授權逾時（超過 3 分鐘），請再試一次")
            } finally {
                runCatching { server.close() }
                if (activeServerSocket === server) activeServerSocket = null
            }

            if (!authError.isNullOrBlank()) {
                throw IllegalStateException(authError)
            }
            val code = authCode ?: throw IllegalStateException("授權已取消或未完成")

            // 以授權碼 + PKCE code_verifier 換取 access_token 與 refresh_token
            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", clientId)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
            if (clientSecret.isNotBlank()) {
                formBuilder.add("client_secret", clientSecret)
            }

            val tokenReq = Request.Builder()
                .url(GOOGLE_TOKEN_ENDPOINT)
                .post(formBuilder.build())
                .build()

            val tokenJson = httpClient.newCall(tokenReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errDesc = runCatching {
                        val obj = JsonParser.parseString(body).asJsonObject
                        obj.get("error_description")?.asString ?: obj.get("error")?.asString
                    }.getOrNull() ?: "HTTP ${resp.code}"
                    throw IllegalStateException("換取 Google 權杖失敗：$errDesc（請確認您建立的是「電腦版應用程式 / Desktop app」用戶端並已填入 Client Secret）")
                }
                JsonParser.parseString(body).asJsonObject
            }

            val accessToken = tokenJson.get("access_token")?.asString?.trim().orEmpty()
            val refreshToken = tokenJson.get("refresh_token")?.asString?.trim().orEmpty()
            val expiresInSec = tokenJson.get("expires_in")?.asLong ?: 3600L
            if (accessToken.isBlank()) {
                throw IllegalStateException("Google 未回傳有效的 access_token")
            }

            val userEmail = fetchUserEmail(accessToken).ifBlank { loginHint.trim().ifBlank { "自訂 OAuth 帳號" } }
            val expiresAt = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(120L) * 1000L)

            prefs(context).edit().apply {
                writeEncrypted(context, this, KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken.isNotBlank()) {
                    writeEncrypted(context, this, KEY_REFRESH_TOKEN, refreshToken)
                }
                putLong(KEY_EXPIRES_AT, expiresAt)
                writeEncrypted(context, this, KEY_AUTH_EMAIL, userEmail)
            }.apply()

            userEmail
        }
    }

    /**
     * 取得目前有效的自訂 OAuth Access Token；若已接近過期則自動以 Refresh Token 靜默刷新！
     */
    suspend fun getValidAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val clientId = readDecrypted(context, KEY_CLIENT_ID)
        if (clientId.isBlank()) {
            throw GoogleTasksCustomSetupRequiredException("尚未設定自訂 Google Tasks OAuth 憑證")
        }
        val cachedToken = readDecrypted(context, KEY_ACCESS_TOKEN)
        val expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (cachedToken.isNotBlank() && System.currentTimeMillis() < expiresAt - 60_000L) {
            return@withContext cachedToken
        }

        val refreshToken = readDecrypted(context, KEY_REFRESH_TOKEN)
        if (refreshToken.isBlank()) {
            throw GoogleTasksCustomAuthRequiredException("請先完成自訂 OAuth 瀏覽器授權")
        }

        val clientSecret = readDecrypted(context, KEY_CLIENT_SECRET)
        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
        if (clientSecret.isNotBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val req = Request.Builder()
            .url(GOOGLE_TOKEN_ENDPOINT)
            .post(formBuilder.build())
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 400 || resp.code == 401) {
                    // Token 已過期或遭撤銷（如測試模式 7 天到期），清除失效 token 引導重新一鍵授權
                    prefs(context).edit()
                        .remove(KEY_ACCESS_TOKEN)
                        .remove(KEY_REFRESH_TOKEN)
                        .remove(KEY_EXPIRES_AT)
                        .apply()
                    throw GoogleTasksCustomAuthRequiredException(
                        "自訂 OAuth 授權已過期（若您的 GCP 處於測試模式每 7 天會失效），請點「前往授權」重新驗證"
                    )
                }
                throw IllegalStateException("刷新 Google Tasks 授權失敗 (${resp.code})")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val newToken = json.get("access_token")?.asString?.trim().orEmpty()
            val expiresIn = json.get("expires_in")?.asLong ?: 3600L
            if (newToken.isBlank()) {
                throw GoogleTasksCustomAuthRequiredException("未取得新權杖，請重新授權")
            }
            prefs(context).edit().apply {
                writeEncrypted(context, this, KEY_ACCESS_TOKEN, newToken)
                putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn.coerceAtLeast(120L) * 1000L)
            }.apply()
            newToken
        }
    }

    private fun fetchUserEmail(accessToken: String): String {
        return runCatching {
            val req = Request.Builder()
                .url(GOOGLE_USERINFO_ENDPOINT)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                val obj = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                obj.get("email")?.asString?.trim().orEmpty()
            }
        }.getOrDefault("")
    }

    private fun buildCallbackHtml(success: Boolean, message: String): String {
        val icon = if (success) "✅" else "⚠️"
        val color = if (success) "#1A73E8" else "#D93025"
        return """
            <!DOCTYPE html>
            <html lang="zh-Hant">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>DX日曆 Google Tasks 授權</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background:#16171B; color:#F5F5F7; display:flex; align-items:center; justify-content:center; min-height:100vh; margin:0; padding:24px; }
                .card { background:#202228; border:1px solid #C8962E55; border-radius:18px; padding:28px 24px; max-width:420px; text-align:center; box-shadow:0 12px 32px rgba(0,0,0,0.45); }
                .icon { font-size:44px; margin-bottom:12px; }
                h1 { font-size:20px; color:$color; margin:0 0 10px; }
                p { font-size:14px; line-height:1.6; color:#D0D2D9; margin:0; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="icon">$icon</div>
                <h1>DX日曆 · Google Tasks</h1>
                <p>$message</p>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
