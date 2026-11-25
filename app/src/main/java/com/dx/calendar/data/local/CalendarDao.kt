package com.dx.calendar.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.EventTitleHistoryEntity
import com.dx.calendar.data.model.ExternalEventOverrideEntity
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.model.TaskItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    // === 分類與時間表類型 ===
    @Query("SELECT * FROM calendar_categories ORDER BY isTaskCategory ASC, id ASC")
    fun observeCategories(): Flow<List<CalendarCategoryEntity>>

    @Query("SELECT * FROM calendar_categories ORDER BY isTaskCategory ASC, id ASC")
    suspend fun getCategoriesOnce(): List<CalendarCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CalendarCategoryEntity): Long

    @Delete
    suspend fun deleteCategory(category: CalendarCategoryEntity)

    // === 日曆活動 ===
    @Query("SELECT * FROM calendar_events ORDER BY startEpochMillis ASC")
    fun observeAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY startEpochMillis ASC")
    suspend fun getAllEventsOnce(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity): Long

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM calendar_events WHERE recurrenceType = 'NONE' AND endEpochMillis < :beforeMillis AND isRoutine = 0")
    suspend fun cleanupExpiredSingleEvents(beforeMillis: Long): Int

    // === 待辦事項 / Google Tasks ===
    @Query("SELECT * FROM task_items ORDER BY isCompleted ASC, COALESCE(dueEpochDay, 999999999) ASC, COALESCE(dueMinuteOfDay, 1440) ASC")
    fun observeAllTasks(): Flow<List<TaskItemEntity>>

    @Query("SELECT * FROM task_items ORDER BY isCompleted ASC, COALESCE(dueEpochDay, 999999999) ASC")
    suspend fun getAllTasksOnce(): List<TaskItemEntity>

    @Query("SELECT * FROM task_items WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskItemEntity?

    @Query("SELECT * FROM task_items WHERE googleTaskId = :googleTaskId LIMIT 1")
    suspend fun getTaskByGoogleId(googleTaskId: String): TaskItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskItemEntity): Long

    @Update
    suspend fun updateTask(task: TaskItemEntity)

    @Query("DELETE FROM task_items WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM task_items WHERE isCompleted = 1 AND isPrivacy = 0")
    suspend fun clearCompletedPublicTasks(): Int

    // === 例行事務每日打卡 ===
    @Query("SELECT * FROM routine_checkins")
    fun observeRoutineCheckIns(): Flow<List<RoutineCheckInEntity>>

    @Query("SELECT * FROM routine_checkins")
    suspend fun getRoutineCheckInsOnce(): List<RoutineCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: RoutineCheckInEntity)

    @Query("DELETE FROM routine_checkins WHERE targetType = :targetType AND targetId = :targetId AND epochDay = :epochDay")
    suspend fun removeCheckIn(targetType: String, targetId: Long, epochDay: Long)

    // === 外部 Google / 系統日曆個別活動顯示覆寫 ===
    @Query("SELECT * FROM external_event_overrides")
    fun observeExternalOverrides(): Flow<List<ExternalEventOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExternalOverride(overrideEntity: ExternalEventOverrideEntity)

    @Query("DELETE FROM external_event_overrides WHERE externalKey = :key")
    suspend fun deleteExternalOverride(key: String)

    // === 常用標題歷史 ===
    @Query("SELECT * FROM title_history ORDER BY usageCount DESC, lastUsedAt DESC LIMIT 25")
    fun observeTitleHistory(): Flow<List<EventTitleHistoryEntity>>

    @Query("SELECT * FROM title_history WHERE title = :title LIMIT 1")
    suspend fun getTitleHistoryItem(title: String): EventTitleHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleHistory(item: EventTitleHistoryEntity)
}
