package com.dx.calendar.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.EventTitleHistoryEntity
import com.dx.calendar.data.model.ExternalEventOverrideEntity
import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.model.TaskItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Database(
    entities = [
        CalendarCategoryEntity::class,
        CalendarEventEntity::class,
        TaskItemEntity::class,
        RoutineCheckInEntity::class,
        ExternalEventOverrideEntity::class,
        EventTitleHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dx_calendar.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { seedDefaultData(it.calendarDao()) }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private suspend fun seedDefaultData(dao: CalendarDao) {
            // 仿 vivo 日曆預設本機時間表類型 + 待辦事項 + 例行事務 + 隱私空間
            val defaultCategories = listOf(
                CalendarCategoryEntity(id = 1, name = "個人", colorArgb = 0xFF448AFF.toInt(), accountName = "本機時間表", isBuiltIn = true),
                CalendarCategoryEntity(id = 2, name = "生日", colorArgb = 0xFFFFB300.toInt(), accountName = "本機時間表", isBuiltIn = true),
                CalendarCategoryEntity(id = 3, name = "工作", colorArgb = 0xFFFF5252.toInt(), accountName = "本機時間表", isBuiltIn = true),
                CalendarCategoryEntity(id = 4, name = "紀念日", colorArgb = 0xFFAB47BC.toInt(), accountName = "本機時間表", isBuiltIn = true),
                CalendarCategoryEntity(id = 5, name = "倒數天數", colorArgb = 0xFF00C853.toInt(), accountName = "本機時間表", isBuiltIn = true),
                CalendarCategoryEntity(id = 6, name = "To-Do / Google Tasks", colorArgb = 0xFFFFAB00.toInt(), accountName = "待辦事項", isTaskCategory = true, isBuiltIn = true),
                CalendarCategoryEntity(id = 7, name = "例行事務", colorArgb = 0xFF26A69A.toInt(), accountName = "例行中心", isBuiltIn = true),
                CalendarCategoryEntity(id = 8, name = "隱私密件", colorArgb = 0xFFE91E63.toInt(), accountName = "隱私日曆", isPrivacyCategory = true, isBuiltIn = true)
            )
            defaultCategories.forEach { dao.upsertCategory(it) }

            // 不再自動新增任何預設吃藥或例行提醒項目，僅保留分類與常用標題歷史，讓使用者依需求自行開啟與新增
            listOf("部門會議", "看診預約", "信用卡繳費", "出國旅遊").forEach {
                dao.upsertTitleHistory(EventTitleHistoryEntity(title = it))
            }
        }
    }
}
