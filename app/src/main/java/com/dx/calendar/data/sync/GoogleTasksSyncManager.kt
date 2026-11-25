package com.dx.calendar.data.sync

import android.accounts.Account
import android.content.Context
import com.dx.calendar.data.local.CalendarDao
import com.dx.calendar.data.model.TaskItemEntity
import com.google.android.gms.auth.GoogleAuthUtil
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

/**
 * Google Tasks 雲端雙向同步引擎
 * 支援透過裝置上已授權的 Google 帳號同步 Google Tasks 清單與任務到 DX日曆 月曆格、時間表與小工具！
 */
class GoogleTasksSyncManager(
    private val context: Context,
    private val dao: CalendarDao
) {
    companion object {
        const val TASKS_SCOPE = "oauth2:https://www.googleapis.com/auth/tasks"
        private const val BASE_URL = "https://tasks.googleapis.com/tasks/v1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun syncWithGoogleAccount(accountEmail: String): Result<Int> = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("尚未選擇 Google 帳號"))
        }
        runCatching {
            val account = Account(accountEmail, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, TASKS_SCOPE)
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

                    val existing = dao.getTaskByGoogleId(gTaskId)
                    if (existing != null) {
                        dao.upsertTask(
                            existing.copy(
                                title = title,
                                notes = notes,
                                dueEpochDay = dueEpochDay ?: existing.dueEpochDay,
                                isCompleted = isCompleted,
                                googleTaskListId = listId,
                                googleTaskListTitle = listTitle,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        dao.upsertTask(
                            TaskItemEntity(
                                title = title,
                                notes = notes,
                                dueEpochDay = dueEpochDay,
                                isCompleted = isCompleted,
                                googleTaskId = gTaskId,
                                googleTaskListId = listId,
                                googleTaskListTitle = listTitle
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
        if (accountEmail.isBlank() || task.googleTaskId.isNullOrBlank() || task.googleTaskListId.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val account = Account(accountEmail, "com.google")
                val token = GoogleAuthUtil.getToken(context, account, TASKS_SCOPE)
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
