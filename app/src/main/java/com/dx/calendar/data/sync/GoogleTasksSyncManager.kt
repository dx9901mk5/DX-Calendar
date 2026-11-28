package com.dx.calendar.data.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.dx.calendar.data.local.CalendarDao
import com.dx.calendar.data.model.TaskItemEntity
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class GoogleTasksConsentRequiredException(val consentIntent: Intent) :
    Exception("需要授權存取 Google Tasks")

class GoogleTasksCustomSetupRequiredException(message: String = "請先依教學填入您自申請的 Google Cloud OAuth 憑證") :
    Exception(message)

class GoogleTasksCustomAuthRequiredException(message: String = "請開啟設定教學完成自訂 Google OAuth 授權") :
    Exception(message)

/**
 * Google Tasks 雲端雙向自動同步引擎：
 * 首次綁定授權一次後，每次開啟／切回 App 即自動於背景判讀同步 Google Tasks 至月曆、時間表與桌面小工具！
 */
class GoogleTasksSyncManager(
    private val context: Context,
    private val dao: CalendarDao
) {
    companion object {
        const val TASKS_SCOPE = "oauth2:https://www.googleapis.com/auth/tasks"
        private const val BASE_URL = "https://tasks.googleapis.com/tasks/v1"

        /**
         * Google Tasks 官方 REST API v1 的 `due` 欄位會強制將時間歸零為 `T00:00:00.000Z`（僅保留日期）。
         * 此函式：
         * 1. 若未來 API 回傳非午夜時刻，立即依本機時區換算為分鐘數；
         * 2. 否則智慧解析任務標題或備註中的明確時刻（例如 `[13:30]`、`14:00`、`下午3點半`），讓使用者有寫時間的任務直接正確定位到時刻！
         */
        fun extractDueMinuteOfDay(
            dueIso: String?,
            title: String,
            notes: String,
            zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
        ): Int? {
            if (!dueIso.isNullOrBlank()) {
                val parsedInstant = runCatching { Instant.parse(dueIso) }.getOrNull()
                if (parsedInstant != null) {
                    val utcTime = parsedInstant.atZone(ZoneOffset.UTC).toLocalTime()
                    if (utcTime.hour != 0 || utcTime.minute != 0 || utcTime.second != 0) {
                        val localTime = parsedInstant.atZone(zone).toLocalTime()
                        return localTime.hour * 60 + localTime.minute
                    }
                }
            }
            // 從標題優先、其次備註自動辨識明確時間（不影響純文字任務）
            val candidates = listOf(title, notes).filter { it.isNotBlank() }
            val colonTimeRegex = Regex("(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\\s*(\\d{1,2})[:：](\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            val cnTimeRegex = Regex("(上午|早上|清晨|中午|下午|晚上|傍晚|凌晨)\\s*(\\d{1,2})\\s*[點点時时]\\s*(半|(\\d{1,2})分?)?")
            for (src in candidates) {
                colonTimeRegex.find(src)?.let { m ->
                    val rawH = m.groupValues[2].toIntOrNull()
                    val rawM = m.groupValues[3].toIntOrNull()
                    if (rawH != null && rawM != null && rawH in 0..23 && rawM in 0..59) {
                        val p = m.groupValues[1].ifBlank { m.groupValues[4] }.lowercase()
                        val h = when {
                            p in listOf("下午", "晚上", "傍晚", "pm") && rawH in 1..11 -> rawH + 12
                            p == "中午" && rawH in 1..5 -> rawH + 12
                            p in listOf("凌晨", "am") && rawH == 12 -> 0
                            else -> rawH
                        }
                        return h.coerceIn(0, 23) * 60 + rawM
                    }
                }
                cnTimeRegex.find(src)?.let { m ->
                    val rawH = m.groupValues[2].toIntOrNull()
                    if (rawH != null && rawH in 0..23) {
                        val p = m.groupValues[1]
                        val h = when {
                            p in listOf("下午", "晚上", "傍晚") && rawH in 1..11 -> rawH + 12
                            p == "中午" && rawH in 1..5 -> rawH + 12
                            p == "凌晨" && rawH == 12 -> 0
                            else -> rawH
                        }
                        val min = when {
                            m.groupValues[3] == "半" -> 30
                            m.groupValues[4].isNotBlank() -> (m.groupValues[4].toIntOrNull() ?: 0).coerceIn(0, 59)
                            else -> 0
                        }
                        return h.coerceIn(0, 23) * 60 + min
                    }
                }
            }
            return null
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun syncWithGoogleAccount(accountEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        val isOwner = GoogleTasksCustomOAuthManager.isOwnerEmail(accountEmail)
        if (!isOwner && !GoogleTasksCustomOAuthManager.isAuthorized(context)) {
            return@withContext Result.failure(
                if (GoogleTasksCustomOAuthManager.hasClientConfig(context)) {
                    GoogleTasksCustomAuthRequiredException("已填入自訂 Client ID，請點「前往授權並同步」完成登入")
                } else {
                    GoogleTasksCustomSetupRequiredException("使用 Google Tasks 同步前，請先依教學填入您自己申請的 Google Cloud OAuth 憑證")
                }
            )
        }
        if (isOwner && accountEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("尚未選擇連結帳號"))
        }
        runCatching {
            val token = if (isOwner) {
                val account = Account(accountEmail, "com.google")
                try {
                    GoogleAuthUtil.getToken(context, account, TASKS_SCOPE)
                } catch (recoverable: UserRecoverableAuthException) {
                    val intent = recoverable.intent
                    if (intent != null) {
                        throw GoogleTasksConsentRequiredException(intent)
                    }
                    throw recoverable
                } catch (e: Exception) {
                    if (e.message?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true) {
                        throw IllegalStateException(
                            "尚未於 Google Cloud Console 登記 Android OAuth 憑證或尚未啟用 Google Tasks API"
                        )
                    }
                    throw e
                }
            } else {
                GoogleTasksCustomOAuthManager.getValidAccessToken(context)
            }

            val defaultTaskReminders = com.dx.calendar.data.local.AppPreferences(context)
                .getSettingsOnce()
                .defaultTaskReminders
            val defaultRemJson = com.google.gson.Gson().toJson(defaultTaskReminders)

            var syncedCount = 0

            // 1. 取得所有 Google Tasks 清單
            val listsReq = Request.Builder()
                .url("$BASE_URL/users/@me/lists?maxResults=20")
                .addHeader("Authorization", "Bearer $token")
                .build()

            val listsJson = client.newCall(listsReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google Tasks API 回應錯誤 (${resp.code})")
                }
                JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
            }

            val itemsArray = listsJson.getAsJsonArray("items") ?: return@runCatching 0
            for (listElem in itemsArray) {
                val listObj = listElem.asJsonObject
                val listId = listObj.get("id")?.asString ?: continue
                val listTitle = listObj.get("title")?.asString ?: "Google Tasks"

                // 2. 拉取每個清單下的 Tasks（包含已完成與未完成）
                val tasksReq = Request.Builder()
                    .url("$BASE_URL/lists/$listId/tasks?showCompleted=true&showHidden=true&maxResults=100")
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val tasksJson = client.newCall(tasksReq).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                } ?: continue

                val taskArray = tasksJson.getAsJsonArray("items") ?: continue
                for (tElem in taskArray) {
                    val tObj = tElem.asJsonObject
                    val gTaskId = tObj.get("id")?.asString ?: continue
                    val title = tObj.get("title")?.asString?.trim() ?: continue
                    if (title.isEmpty()) continue
                    val notes = tObj.get("notes")?.asString ?: ""
                    val status = tObj.get("status")?.asString ?: "needsAction"
                    val isCompleted = status == "completed"
                    val dueIso = tObj.get("due")?.asString

                    val dueEpochDay = dueIso?.let { iso ->
                        runCatching {
                            Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }.getOrElse {
                            runCatching { LocalDate.parse(iso.take(10)).toEpochDay() }.getOrNull()
                        }
                    }
                    val parsedDueMinute = extractDueMinuteOfDay(dueIso, title, notes)

                    val existing = dao.getTaskByGoogleId(gTaskId)
                    if (existing != null) {
                        dao.upsertTask(
                            existing.copy(
                                title = title,
                                notes = notes,
                                dueEpochDay = dueEpochDay ?: existing.dueEpochDay,
                                dueMinuteOfDay = parsedDueMinute ?: existing.dueMinuteOfDay,
                                isCompleted = isCompleted,
                                googleTaskListId = listId,
                                googleTaskListTitle = listTitle,
                                colorArgb = 0xFF1A73E8.toInt(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        dao.upsertTask(
                            TaskItemEntity(
                                title = title,
                                notes = notes,
                                dueEpochDay = dueEpochDay,
                                dueMinuteOfDay = parsedDueMinute,
                                isCompleted = isCompleted,
                                reminderMinutesJson = defaultRemJson,
                                googleTaskId = gTaskId,
                                googleTaskListId = listId,
                                googleTaskListTitle = listTitle,
                                colorArgb = 0xFF1A73E8.toInt()
                            )
                        )
                    }
                    syncedCount++
                }
            }
            syncedCount
        }
    }

    suspend fun pushTaskCompletionToCloud(accountEmail: String, task: TaskItemEntity) {
        if (task.googleTaskId.isNullOrBlank() || task.googleTaskListId.isNullOrBlank()) return
        val isOwner = GoogleTasksCustomOAuthManager.isOwnerEmail(accountEmail)
        if (isOwner && accountEmail.isBlank()) return
        if (!isOwner && !GoogleTasksCustomOAuthManager.isAuthorized(context)) return
        withContext(Dispatchers.IO) {
            runCatching {
                val token = if (isOwner) {
                    val account = Account(accountEmail, "com.google")
                    GoogleAuthUtil.getToken(context, account, TASKS_SCOPE)
                } else {
                    GoogleTasksCustomOAuthManager.getValidAccessToken(context)
                }
                val bodyJson = JsonObject().apply {
                    addProperty("id", task.googleTaskId)
                    addProperty("title", task.title)
                    addProperty("status", if (task.isCompleted) "completed" else "needsAction")
                }
                val req = Request.Builder()
                    .url("$BASE_URL/lists/${task.googleTaskListId}/tasks/${task.googleTaskId}")
                    .patch(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }
}
