package com.dx.calendar.data.repository

import android.content.Context
import com.dx.calendar.data.local.AppDatabase
import com.dx.calendar.data.local.AppPreferences
import com.dx.calendar.data.local.UserSettings
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.ExternalEventOverrideEntity
import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.model.TaskItemEntity
import com.dx.calendar.data.sync.SystemCalendarAccountInfo
import com.dx.calendar.data.sync.SystemCalendarBridge
import com.dx.calendar.util.LunarCalendarEngine
import com.dx.calendar.util.PrivacyCryptoManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class UnifiedSourceType {
    LOCAL_EVENT,
    SYSTEM_EVENT,
    TASK
}

/**
 * 統一渲染項目：支援本機活動、Google/系統日曆活動、Google Tasks / 待辦事項、例行事務與隱私日曆
 */
data class UnifiedCalendarItem(
    val uniqueKey: String,
    val sourceType: UnifiedSourceType,
    val rawId: Long,
    val title: String,
    val notes: String,
    val location: String,
    val url: String,
    val date: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val colorArgb: Int,
    val categoryOrAccountLabel: String,
    val isTask: Boolean,
    val isCompleted: Boolean,
    val isRoutine: Boolean,
    val showInMonthGrid: Boolean,
    val showInAgendaAndWidget: Boolean,
    val isPaused: Boolean,
    val isPrivacy: Boolean,
    val reminderMinutes: List<Int>,
    val enableMultiCall: Boolean,
    val multiCallCount: Int,
    val multiCallIntervalMinutes: Int,
    val useAlarmStream: Boolean,
    val syncSystemAlarm: Boolean,
    val recurrenceLabel: String = "",
    val externalOverrideKey: String? = null
)

class CalendarRepository(private val context: Context) {

    val db = AppDatabase.getInstance(context)
    val dao = db.calendarDao()
    val prefs = AppPreferences(context)
    val systemBridge = SystemCalendarBridge(context)
    private val gson = Gson()

    fun parseIntList(json: String): List<Int> {
        return runCatching {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun toIntListJson(list: List<Int>): String = gson.toJson(list.distinct().sorted())

    fun parseDateStrings(json: String): List<LocalDate> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            val raw = gson.fromJson<List<String>>(json, type) ?: emptyList()
            raw.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    fun toDateStringsJson(dates: List<LocalDate>): String =
        gson.toJson(dates.distinct().sorted().map { it.toString() })

    /**
     * 將本機活動、系統日曆、Tasks 與例行打卡展開到指定日期區間 [rangeStart, rangeEnd]
     */
    suspend fun buildItemsForRange(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        categories: List<CalendarCategoryEntity>,
        localEvents: List<CalendarEventEntity>,
        tasks: List<TaskItemEntity>,
        checkIns: List<RoutineCheckInEntity>,
        overrides: List<ExternalEventOverrideEntity>,
        settings: UserSettings,
        isPrivacyUnlocked: Boolean
    ): List<UnifiedCalendarItem> = withContext(Dispatchers.Default) {
        val zone = ZoneId.systemDefault()
        val categoryMap = categories.associateBy { it.id }
        val checkInSet = checkIns.map { "${it.targetType}:${it.targetId}:${it.epochDay}" }.toHashSet()
        val overrideMap = overrides.associateBy { it.externalKey }
        val items = mutableListOf<UnifiedCalendarItem>()

        // 1. 展開本機日曆活動（含多日期、重複規則、例行打卡、隱私解密）
        for (ev in localEvents) {
            if (ev.isPrivacy && !isPrivacyUnlocked) continue
            if (ev.isPaused) continue
            val cat = categoryMap[ev.categoryId]
            if (cat != null && !cat.isVisible && !ev.isPrivacy) continue

            val cleanTitle = PrivacyCryptoManager.decryptIfNeeded(ev.title)
            val cleanNotes = PrivacyCryptoManager.decryptIfNeeded(ev.notes)
            val cleanLoc = PrivacyCryptoManager.decryptIfNeeded(ev.location)
            val color = ev.customColorArgb ?: cat?.colorArgb ?: 0xFFFF5238.toInt()
            val catLabel = if (ev.isPrivacy) "隱私日曆" else (cat?.name ?: "個人")
            val reminders = parseIntList(ev.reminderMinutesJson)
            val recType = runCatching { RecurrenceType.valueOf(ev.recurrenceType) }.getOrDefault(RecurrenceType.NONE)

            val baseStartZdt = Instant.ofEpochMilli(ev.startEpochMillis).atZone(zone)
            val baseEndZdt = Instant.ofEpochMilli(ev.endEpochMillis).atZone(zone)
            val baseDate = baseStartZdt.toLocalDate()
            val spanDays = ChronoUnit.DAYS.between(baseDate, baseEndZdt.toLocalDate()).coerceAtLeast(0)
            val extraDates = parseDateStrings(ev.extraDatesJson)

            if (recType == RecurrenceType.NONE) {
                // 主活動（可跨多日）
                var d = baseDate
                val lastD = baseDate.plusDays(spanDays)
                while (!d.isAfter(lastD)) {
                    if (!d.isBefore(rangeStart) && !d.isAfter(rangeEnd)) {
                        val isDone = checkInSet.contains("EVENT:${ev.id}:${d.toEpochDay()}")
                        items.add(
                            createEventUnifiedItem(
                                ev, cleanTitle, cleanNotes, cleanLoc, d,
                                ev.startEpochMillis, ev.endEpochMillis, color, catLabel,
                                isDone, reminders, recType.label
                            )
                        )
                    }
                    d = d.plusDays(1)
                }
                // 截圖 2「多個日期」額外指定的不連續日期
                for (extraDate in extraDates) {
                    if (extraDate != baseDate && !extraDate.isBefore(rangeStart) && !extraDate.isAfter(rangeEnd)) {
                        val exStart = extraDate.atTime(baseStartZdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        val exEnd = extraDate.plusDays(spanDays).atTime(baseEndZdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        val isDone = checkInSet.contains("EVENT:${ev.id}:${extraDate.toEpochDay()}")
                        items.add(
                            createEventUnifiedItem(
                                ev, cleanTitle, cleanNotes, cleanLoc, extraDate,
                                exStart, exEnd, color, catLabel,
                                isDone, reminders, "多個日期"
                            )
                        )
                    }
                }
            } else {
                // 重複規則展開
                val recEndDate = ev.recurrenceEndEpochMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                }
                var cursorDate = if (rangeStart.isAfter(baseDate)) rangeStart else baseDate
                while (!cursorDate.isAfter(rangeEnd)) {
                    if (recEndDate != null && cursorDate.isAfter(recEndDate)) break
                    if (matchesRecurrence(baseDate, cursorDate, recType)) {
                        val occStart = cursorDate.atTime(baseStartZdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        val occEnd = cursorDate.plusDays(spanDays).atTime(baseEndZdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        val isDone = checkInSet.contains("EVENT:${ev.id}:${cursorDate.toEpochDay()}")
                        items.add(
                            createEventUnifiedItem(
                                ev, cleanTitle, cleanNotes, cleanLoc, cursorDate,
                                occStart, occEnd, color, catLabel,
                                isDone, reminders, recType.label
                            )
                        )
                    }
                    cursorDate = cursorDate.plusDays(1)
                }
            }
        }

        // 2. 展開待辦事項 / Google Tasks（顯示在日曆與小工具！）
        val taskCategoryVisible = categories.find { it.isTaskCategory }?.isVisible ?: true
        if (taskCategoryVisible) {
            for (task in tasks) {
                if (task.isPrivacy && !isPrivacyUnlocked) continue
                if (task.isPaused) continue
                val recType = runCatching { RecurrenceType.valueOf(task.recurrenceType) }.getOrDefault(RecurrenceType.NONE)
                val cleanTitle = PrivacyCryptoManager.decryptIfNeeded(task.title)
                val cleanNotes = PrivacyCryptoManager.decryptIfNeeded(task.notes)
                val reminders = parseIntList(task.reminderMinutesJson)
                val timeOfDay = task.dueMinuteOfDay?.let { LocalTime.of((it / 60).coerceIn(0, 23), (it % 60).coerceIn(0, 59)) }

                val baseDueDate = task.dueEpochDay?.let { LocalDate.ofEpochDay(it) } ?: continue
                if (recType == RecurrenceType.NONE) {
                    if (!settings.showCompletedTasks && task.isCompleted) continue
                    if (!baseDueDate.isBefore(rangeStart) && !baseDueDate.isAfter(rangeEnd)) {
                        val startMs = (timeOfDay?.let { baseDueDate.atTime(it) } ?: baseDueDate.atStartOfDay())
                            .atZone(zone).toInstant().toEpochMilli()
                        items.add(
                            UnifiedCalendarItem(
                                uniqueKey = "TASK_${task.id}_${baseDueDate}",
                                sourceType = UnifiedSourceType.TASK,
                                rawId = task.id,
                                title = cleanTitle,
                                notes = cleanNotes,
                                location = "",
                                url = "",
                                date = baseDueDate,
                                startMillis = startMs,
                                endMillis = startMs + 1_800_000L,
                                isAllDay = timeOfDay == null,
                                colorArgb = task.colorArgb,
                                categoryOrAccountLabel = SystemCalendarBridge.formatConciseCalendarName(task.googleTaskListTitle),
                                isTask = true,
                                isCompleted = task.isCompleted,
                                isRoutine = task.isRoutine,
                                showInMonthGrid = task.showInMonthGrid,
                                showInAgendaAndWidget = task.showInAgendaAndWidget,
                                isPaused = task.isPaused,
                                isPrivacy = task.isPrivacy,
                                reminderMinutes = reminders,
                                enableMultiCall = task.enableMultiCall,
                                multiCallCount = task.multiCallCount,
                                multiCallIntervalMinutes = task.multiCallIntervalMinutes,
                                useAlarmStream = task.useAlarmStream,
                                syncSystemAlarm = task.syncSystemAlarm
                            )
                        )
                    }
                } else {
                    // 重複型待辦 / 例行待辦（每日打卡制）
                    var d = if (rangeStart.isAfter(baseDueDate)) rangeStart else baseDueDate
                    while (!d.isAfter(rangeEnd)) {
                        if (matchesRecurrence(baseDueDate, d, recType)) {
                            val doneToday = checkInSet.contains("TASK:${task.id}:${d.toEpochDay()}")
                            if (settings.showCompletedTasks || !doneToday) {
                                val startMs = (timeOfDay?.let { d.atTime(it) } ?: d.atStartOfDay())
                                    .atZone(zone).toInstant().toEpochMilli()
                                items.add(
                                    UnifiedCalendarItem(
                                        uniqueKey = "TASK_${task.id}_${d}",
                                        sourceType = UnifiedSourceType.TASK,
                                        rawId = task.id,
                                        title = cleanTitle,
                                        notes = cleanNotes,
                                        location = "",
                                        url = "",
                                        date = d,
                                        startMillis = startMs,
                                        endMillis = startMs + 1_800_000L,
                                        isAllDay = timeOfDay == null,
                                        colorArgb = task.colorArgb,
                                        categoryOrAccountLabel = SystemCalendarBridge.formatConciseCalendarName(task.googleTaskListTitle),
                                        isTask = true,
                                        isCompleted = doneToday,
                                        isRoutine = task.isRoutine,
                                        showInMonthGrid = task.showInMonthGrid,
                                        showInAgendaAndWidget = task.showInAgendaAndWidget,
                                        isPaused = task.isPaused,
                                        isPrivacy = task.isPrivacy,
                                        reminderMinutes = reminders,
                                        enableMultiCall = task.enableMultiCall,
                                        multiCallCount = task.multiCallCount,
                                        multiCallIntervalMinutes = task.multiCallIntervalMinutes,
                                        useAlarmStream = task.useAlarmStream,
                                        syncSystemAlarm = task.syncSystemAlarm,
                                        recurrenceLabel = recType.label
                                    )
                                )
                            }
                        }
                        d = d.plusDays(1)
                    }
                }
            }
        }

        // 3. 讀取系統日曆 (Google 日曆等)，並套用個別活動隱藏/例行覆寫規則！
        if (systemBridge.hasReadPermission()) {
            val startMs = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val sysInstances = withContext(Dispatchers.IO) {
                systemBridge.querySystemInstances(startMs, endMs)
            }
            for (inst in sysInstances) {
                if (settings.hiddenSystemCalIds.contains(inst.calendarId.toString())) continue
                val sigKey = "TITLE_SIG:${inst.calendarId}:${inst.title}"
                val idKey = "EVENT_ID:${inst.eventId}"
                val ov = overrideMap[idKey] ?: overrideMap[sigKey]
                if (ov?.isPrivacy == true && !isPrivacyUnlocked) continue

                val instStartDate = if (inst.isAllDay) {
                    Instant.ofEpochMilli(inst.beginMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                } else {
                    Instant.ofEpochMilli(inst.beginMillis).atZone(zone).toLocalDate()
                }
                val instEndDate = if (inst.isAllDay) {
                    Instant.ofEpochMilli((inst.endMillis - 1000L).coerceAtLeast(inst.beginMillis))
                        .atZone(ZoneId.of("UTC")).toLocalDate()
                } else {
                    Instant.ofEpochMilli((inst.endMillis - 1L).coerceAtLeast(inst.beginMillis))
                        .atZone(zone).toLocalDate()
                }

                var d = if (instStartDate.isBefore(rangeStart)) rangeStart else instStartDate
                val endClamp = if (instEndDate.isAfter(rangeEnd)) rangeEnd else instEndDate
                while (!d.isAfter(endClamp)) {
                    items.add(
                        UnifiedCalendarItem(
                            uniqueKey = "SYS_${inst.eventId}_${inst.beginMillis}_$d",
                            sourceType = UnifiedSourceType.SYSTEM_EVENT,
                            rawId = inst.eventId,
                            title = inst.title,
                            notes = inst.description,
                            location = inst.location,
                            url = "",
                            date = d,
                            startMillis = inst.beginMillis,
                            endMillis = inst.endMillis,
                            isAllDay = inst.isAllDay,
                            colorArgb = inst.colorArgb,
                            categoryOrAccountLabel = inst.calendarDisplayName,
                            isTask = false,
                            isCompleted = false,
                            isRoutine = ov?.isRoutine ?: false,
                            showInMonthGrid = ov?.showInMonthGrid ?: true,
                            showInAgendaAndWidget = ov?.showInAgendaAndWidget ?: true,
                            isPaused = false,
                            isPrivacy = ov?.isPrivacy ?: false,
                            reminderMinutes = emptyList(),
                            enableMultiCall = false,
                            multiCallCount = 1,
                            multiCallIntervalMinutes = 5,
                            useAlarmStream = false,
                            syncSystemAlarm = false,
                            recurrenceLabel = if (!inst.rrule.isNullOrBlank()) "系統重複活動" else "",
                            externalOverrideKey = sigKey
                        )
                    )
                    d = d.plusDays(1)
                }
            }
        }

        items.distinctBy { it.uniqueKey }.sortedWith(
            compareBy<UnifiedCalendarItem> { it.date }
                .thenByDescending { it.isAllDay && !it.isTask }
                .thenBy { it.startMillis }
                .thenBy { it.title }
        )
    }

    private fun createEventUnifiedItem(
        ev: CalendarEventEntity,
        title: String,
        notes: String,
        loc: String,
        date: LocalDate,
        startMs: Long,
        endMs: Long,
        color: Int,
        catLabel: String,
        isDone: Boolean,
        reminders: List<Int>,
        recLabel: String
    ): UnifiedCalendarItem {
        return UnifiedCalendarItem(
            uniqueKey = "LOC_${ev.id}_$date",
            sourceType = UnifiedSourceType.LOCAL_EVENT,
            rawId = ev.id,
            title = title,
            notes = notes,
            location = loc,
            url = ev.url,
            date = date,
            startMillis = startMs,
            endMillis = endMs,
            isAllDay = ev.isAllDay,
            colorArgb = color,
            categoryOrAccountLabel = catLabel,
            isTask = false,
            isCompleted = isDone,
            isRoutine = ev.isRoutine,
            showInMonthGrid = ev.showInMonthGrid,
            showInAgendaAndWidget = ev.showInAgendaAndWidget,
            isPaused = ev.isPaused,
            isPrivacy = ev.isPrivacy,
            reminderMinutes = reminders,
            enableMultiCall = ev.enableMultiCall,
            multiCallCount = ev.multiCallCount,
            multiCallIntervalMinutes = ev.multiCallIntervalMinutes,
            useAlarmStream = ev.useAlarmStream,
            syncSystemAlarm = ev.syncSystemAlarm,
            recurrenceLabel = recLabel
        )
    }

    fun matchesRecurrence(baseDate: LocalDate, candidate: LocalDate, type: RecurrenceType): Boolean {
        if (candidate.isBefore(baseDate)) return false
        if (candidate == baseDate) return true
        return when (type) {
            RecurrenceType.NONE -> false
            RecurrenceType.DAILY -> true
            RecurrenceType.WEEKDAYS -> candidate.dayOfWeek.value in 1..5
            RecurrenceType.WEEKLY -> ChronoUnit.DAYS.between(baseDate, candidate) % 7L == 0L
            RecurrenceType.BIWEEKLY -> ChronoUnit.DAYS.between(baseDate, candidate) % 14L == 0L
            RecurrenceType.MONTHLY -> candidate.dayOfMonth == baseDate.dayOfMonth
            RecurrenceType.YEARLY -> candidate.monthValue == baseDate.monthValue && candidate.dayOfMonth == baseDate.dayOfMonth
            RecurrenceType.LUNAR_YEARLY -> {
                val baseLunar = LunarCalendarEngine.getLunarInfo(baseDate)
                val candLunar = LunarCalendarEngine.getLunarInfo(candidate)
                candLunar.lunarMonth == baseLunar.lunarMonth &&
                    candLunar.lunarDay == baseLunar.lunarDay &&
                    !candLunar.isLeapMonth
            }
        }
    }

    suspend fun getSystemCalendars(): List<SystemCalendarAccountInfo> = withContext(Dispatchers.IO) {
        systemBridge.querySystemCalendars()
    }
}
