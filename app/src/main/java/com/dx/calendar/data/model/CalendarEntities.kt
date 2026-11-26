package com.dx.calendar.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_categories")
data class CalendarCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val accountName: String = "本機時間表",
    val isVisible: Boolean = true,
    val isTaskCategory: Boolean = false,
    val isPrivacyCategory: Boolean = false,
    val isBuiltIn: Boolean = false
)

@Entity(
    tableName = "calendar_events",
    indices = [Index("startEpochMillis"), Index("endEpochMillis"), Index("isRoutine"), Index("isPrivacy")]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long = 1L,
    val title: String,
    val location: String = "",
    val url: String = "",
    val notes: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean = false,

    // 多個日期支援（截圖2：多個日期，JSON 儲存額外指定的 ISO 日期字串清單，或批次關聯）
    val extraDatesJson: String = "[]",

    // 重複規則
    val recurrenceType: String = RecurrenceType.NONE.name,
    val recurrenceEndEpochMillis: Long? = null,

    // 細緻顯示控制（解決簡單日曆與 Google 日曆例行事項塞滿月曆的痛點！）
    val isRoutine: Boolean = false,               // 歸類為例行事務（如每日吃藥、換防蚊液）
    val showInMonthGrid: Boolean = true,          // 是否在月曆格顯示（例行事務可關閉以保持月曆清爽）
    val showInAgendaAndWidget: Boolean = true,    // 是否在時間表清單與桌面小工具顯示
    val isPaused: Boolean = false,                // 例行事務可一鍵暫停而不必刪除

    // 雙指紋鎖隱私日曆保護
    val isPrivacy: Boolean = false,

    // 多重提醒 & 連段追擊呼叫 & 鬧鐘連動
    val reminderMinutesJson: String = "[0, 5]",   // 可多選提前提醒分鐘數，如 [0, 5, 15, 60]
    val enableMultiCall: Boolean = false,         // 是否開啟多重連段呼叫
    val multiCallCount: Int = 3,                  // 連段呼叫次數（如連響 3 次）
    val multiCallIntervalMinutes: Int = 3,        // 每次連段間隔分鐘數（如每 3 分鐘追擊一次）
    val useAlarmStream: Boolean = false,          // 是否使用全螢幕鬧鐘音軌強提醒
    val syncSystemAlarm: Boolean = false,         // 是否連動寫入 Android 系統時鐘鬧鐘

    val customColorArgb: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_items",
    indices = [Index("dueEpochDay"), Index("isCompleted"), Index("isRoutine"), Index("isPrivacy")]
)
data class TaskItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long = 6L,
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long?,                       // LocalDate.toEpochDay()，可為無日期或指定日
    val dueMinuteOfDay: Int? = null,              // 例如 11:00 = 660，對應「換防蚊液 上午11時」
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val recurrenceType: String = RecurrenceType.NONE.name,

    // 顯示控制與例行歸納
    val isRoutine: Boolean = false,
    val showInMonthGrid: Boolean = true,
    val showInAgendaAndWidget: Boolean = true,
    val isPaused: Boolean = false,
    val isPrivacy: Boolean = false,

    // 多重提醒 & 鬧鐘連動
    val reminderMinutesJson: String = "[0]",
    val enableMultiCall: Boolean = false,
    val multiCallCount: Int = 3,
    val multiCallIntervalMinutes: Int = 3,
    val useAlarmStream: Boolean = false,
    val syncSystemAlarm: Boolean = false,

    // Google Tasks 雙向同步識別
    val googleTaskId: String? = null,
    val googleTaskListId: String? = null,
    val googleTaskListTitle: String = "Google Tasks",
    val colorArgb: Int = 0xFF1A73E8.toInt(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 例行事務與重複待辦的「單日完成打卡紀錄」：不產生數百筆重複月曆垃圾，又能追蹤今日是否已吃藥/已完成！
 */
@Entity(
    tableName = "routine_checkins",
    primaryKeys = ["targetType", "targetId", "epochDay"]
)
data class RoutineCheckInEntity(
    val targetType: String, // "EVENT" or "TASK"
    val targetId: Long,
    val epochDay: Long,
    val completedAt: Long = System.currentTimeMillis()
)

/**
 * 針對系統日曆（如 Google 帳號同步進來的活動）支援「單一活動/重複系列獨立隱藏或轉為例行事務」
 * 徹底突破傳統日曆只能開關整個帳號的限制！
 */
@Entity(tableName = "external_event_overrides")
data class ExternalEventOverrideEntity(
    @PrimaryKey val externalKey: String, // "EVENT_ID:{id}" or "TITLE_SIG:{calendarId}:{title}"
    val showInMonthGrid: Boolean = false,
    val showInAgendaAndWidget: Boolean = true,
    val isRoutine: Boolean = true,
    val isPrivacy: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 常用標題歷史範本（對應截圖2標題欄右側的時鐘快速選取按鈕）
 */
@Entity(tableName = "title_history")
data class EventTitleHistoryEntity(
    @PrimaryKey val title: String,
    val usageCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)
