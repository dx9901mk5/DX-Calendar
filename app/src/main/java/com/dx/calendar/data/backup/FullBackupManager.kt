package com.dx.calendar.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.dx.calendar.data.local.CalendarDao
import com.dx.calendar.data.local.EventRichMeta
import com.dx.calendar.data.local.EventRichMetaStore
import com.dx.calendar.data.local.EventTemplateItem
import com.dx.calendar.data.local.EventTemplateStore
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.ExternalEventOverrideEntity
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.model.TaskItemEntity
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 跨系統通用 DX日曆整庫備份 Schema（v2）：
 * 完整支援 Android 換機還原，並預留未來 Windows Store 電腦端伴侶版共用同一份 JSON 同步格式。
 */
data class DxFullBackupPayload(
    val schemaVersion: Int = 2,
    val appId: String = "com.dx.calendar",
    val deviceName: String,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val categories: List<CalendarCategoryEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val tasks: List<TaskItemEntity> = emptyList(),
    val routineCheckIns: List<RoutineCheckInEntity> = emptyList(),
    val externalOverrides: List<ExternalEventOverrideEntity> = emptyList(),
    val templates: List<EventTemplateItem> = emptyList(),
    val richMeta: Map<String, EventRichMeta> = emptyMap()
)

data class BackupRestoreSummary(
    val deviceName: String,
    val eventsCount: Int,
    val tasksCount: Int,
    val templatesCount: Int
)

object FullBackupManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun sanitizeDeviceName(raw: String): String {
        val cleaned = raw.trim()
            .ifBlank { Build.MODEL ?: "Android" }
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        return cleaned.take(32).ifBlank { "DX_Device" }
    }

    fun buildBackupFileName(deviceName: String): String {
        val safe = sanitizeDeviceName(deviceName)
        val ts = LocalDateTime.now().format(FILE_TIME_FMT)
        return "DX-Calendar_${safe}_${ts}.dxbackup.json"
    }

    suspend fun buildBackupJson(
        context: Context,
        dao: CalendarDao,
        deviceName: String
    ): String = withContext(Dispatchers.IO) {
        val payload = DxFullBackupPayload(
            deviceName = deviceName.trim().ifBlank { Build.MODEL ?: "Android" },
            categories = dao.getCategoriesOnce(),
            events = dao.getAllEventsOnce(),
            tasks = dao.getAllTasksOnce(),
            routineCheckIns = dao.getRoutineCheckInsOnce(),
            externalOverrides = emptyList(),
            templates = EventTemplateStore.ensureLoaded(context),
            richMeta = EventRichMetaStore.getAll(context)
        )
        gson.toJson(payload)
    }

    /**
     * 產生暫存備份檔並直接建立連動「Google 雲端硬碟 App」上傳之 Intent（若未安裝則自動退回系統分享選單）
     */
    suspend fun buildGoogleDriveUploadIntent(
        context: Context,
        dao: CalendarDao,
        deviceName: String
    ): Intent = withContext(Dispatchers.IO) {
        val json = buildBackupJson(context, dao, deviceName)
        val dir = File(context.cacheDir, "dx_backups").apply { mkdirs() }
        val outFile = File(dir, buildBackupFileName(deviceName))
        outFile.writeText(json, Charsets.UTF_8)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val driveIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, outFile.name)
            putExtra(Intent.EXTRA_TEXT, "DX日曆整庫備份（裝置：${deviceName.ifBlank { Build.MODEL }}）")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 若裝置已安裝 Google 雲端硬碟 App，優先直接指定由 Google Drive 承接上傳
        val drivePkg = "com.google.android.apps.docs"
        val hasDriveApp = runCatching {
            context.packageManager.getPackageInfo(drivePkg, 0)
            true
        }.getOrDefault(false)

        if (hasDriveApp) {
            driveIntent.setPackage(drivePkg)
            driveIntent
        } else {
            Intent.createChooser(driveIntent, "上傳 DX日曆備份至 Google 雲端硬碟或分享")
        }
    }

    suspend fun writeBackupToUri(
        context: Context,
        dao: CalendarDao,
        deviceName: String,
        targetUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildBackupJson(context, dao, deviceName)
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: throw IllegalStateException("無法開啟目標檔案寫入串流")
            buildBackupFileName(deviceName)
        }
    }

    suspend fun restoreFromUri(
        context: Context,
        dao: CalendarDao,
        sourceUri: Uri
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw IllegalStateException("無法讀取備份檔案")

            val payload = gson.fromJson(raw, DxFullBackupPayload::class.java)
                ?: throw IllegalArgumentException("備份檔格式不符")

            payload.categories.forEach { dao.upsertCategory(it) }
            payload.events.forEach { dao.upsertEvent(it) }
            payload.tasks.forEach { dao.upsertTask(it) }
            payload.routineCheckIns.forEach { dao.insertCheckIn(it) }
            payload.externalOverrides.forEach { dao.upsertExternalOverride(it) }
            if (payload.templates.isNotEmpty()) {
                EventTemplateStore.replaceAll(context, payload.templates)
            }
            if (payload.richMeta.isNotEmpty()) {
                EventRichMetaStore.replaceAll(context, payload.richMeta)
            }

            BackupRestoreSummary(
                deviceName = payload.deviceName,
                eventsCount = payload.events.size,
                tasksCount = payload.tasks.size,
                templatesCount = payload.templates.size
            )
        }
    }
}
