package com.dx.calendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.dx.calendar.MainActivity
import com.dx.calendar.data.local.AppDatabase
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.data.model.TaskItemEntity
import com.dx.calendar.util.LunarCalendarEngine
import com.dx.calendar.util.PrivacyCryptoManager
import com.dx.calendar.util.ReminderTimeFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

data class ReminderNotificationContent(
    val title: String,
    val subtitle: String,
    val shortCriticalText: String,
    val timeSummary: String
)

/**
 * 多重呼叫（連段追擊提醒）排程器與 Android 系統原生鬧鐘連動引擎
 */
object MultiCallAlarmScheduler {

    const val ACTION_FIRE_REMINDER = "com.dx.calendar.ACTION_FIRE_REMINDER"
    const val ACTION_DISMISS_BURST = "com.dx.calendar.ACTION_DISMISS_BURST"
    const val ACTION_SNOOZE_REMINDER = "com.dx.calendar.ACTION_SNOOZE_REMINDER"

    const val EXTRA_SOURCE_TYPE = "extra_source_type" // "EVENT", "SYS_EVENT", "TASK", "DEMO"
    const val EXTRA_ITEM_ID = "extra_item_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_SUBTITLE = "extra_subtitle"
    const val EXTRA_IS_PRIVACY = "extra_is_privacy"
    const val EXTRA_USE_ALARM_STREAM = "extra_use_alarm_stream"
    const val EXTRA_ENABLE_MULTI_CALL = "extra_enable_multi_call"
    const val EXTRA_BURST_INDEX = "extra_burst_index"
    const val EXTRA_BURST_TOTAL = "extra_burst_total"
    const val EXTRA_BURST_INTERVAL_MIN = "extra_burst_interval_min"
    const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"
    const val EXTRA_EVENT_EPOCH_DAY = "extra_event_epoch_day"
    const val EXTRA_EVENT_START_MILLIS = "extra_event_start_millis"
    const val EXTRA_SHORT_CRITICAL = "extra_short_critical"
    const val EXTRA_TIME_SUMMARY = "extra_time_summary"

    private val COMMON_CANCEL_OFFSETS = listOf(0, 1, 3, 5, 10, 15, 20, 30, 45, 60, 120, 180, 1440, 2880, 10080)
    private val gson = Gson()

    /**
     * 決定 Google / 系統同步行事曆活動應排程的提醒提前分鐘數：
     * 1. 若該活動本身在 Google 日曆／DX日曆有明確設定提醒分鐘數，一律依其設定排程（含全天活動）；
     * 2. 若使用者在 DX日曆為該活動開啟多重連段／鬧鐘聲道，保底準時 (`0`) 提醒；
     * 3. 若該活動本身無個別提醒設定：
     *    - 非全天行程依使用者「預設日程提醒時間」(`defaultEventReminders`) 排程；
     *    - 當使用者將「預設日程提醒時間」設為空（預設不提示）時，嚴格回傳空集合，絕不強制彈出通知！
     *    - 全天系統活動（如國定假日）若無個別提醒，預設不打擾。
     */
    fun resolveSystemEventOffsets(
        eventReminders: List<Int>,
        defaultEventReminders: List<Int>,
        isAllDay: Boolean,
        hasExplicitAlarmMeta: Boolean
    ): List<Int> {
        val cleanExplicit = eventReminders.filter { it >= 0 }.distinct().sorted()
        if (cleanExplicit.isNotEmpty()) return cleanExplicit
        if (hasExplicitAlarmMeta) return listOf(0)
        if (!isAllDay && defaultEventReminders.isNotEmpty()) {
            return defaultEventReminders.filter { it >= 0 }.distinct().sorted()
        }
        return emptyList()
    }

    /**
     * 決定待辦事項 / Google Tasks 應排程的提醒提前分鐘數：
     * - 若使用者開啟了多重連段／鬧鐘聲道，確保至少有準時 (`0`) 提醒；
     * - 若為雲端同步的 Google Tasks 且使用者已將「預設待辦事項提醒時間」設為不提示（空集合），
     *   未自訂額外提醒的任務（僅保留舊預設 `[0]`）將自動靜默不提示；
     * - 其餘則依該任務自身所設定的 `reminderMinutes` 排程（空集合即不提示）。
     */
    fun resolveTaskOffsets(
        taskReminders: List<Int>,
        defaultTaskReminders: List<Int>,
        isCloudGoogleTask: Boolean,
        hasExplicitAlarmMeta: Boolean
    ): List<Int> {
        val cleanTask = taskReminders.filter { it >= 0 }.distinct().sorted()
        if (hasExplicitAlarmMeta) return cleanTask.ifEmpty { listOf(0) }
        if (isCloudGoogleTask && defaultTaskReminders.isEmpty() && cleanTask == listOf(0)) {
            return emptyList()
        }
        return cleanTask
    }

    /**
     * 產生 Android 16 / OriginOS 6 原子島膠囊精簡字樣（<= 7 字元）：
     * 讓收合於狀態列膠囊時依然能一眼辨識是「什麼事件」，不再只顯示泛稱的「進行中 / 10分後」。
     */
    fun buildIslandShortText(
        rawTitle: String,
        timeShort: String? = null,
        isPrivacy: Boolean = false,
        isTask: Boolean = false
    ): String {
        if (isPrivacy) return if (isTask) "隱私待辦" else "隱私提醒"
        val clean = rawTitle.trim()
            .removePrefix("📅")
            .removePrefix("☑️")
            .removePrefix("✅")
            .trim()
        if (clean.isEmpty()) {
            return LiveUpdateIslandHelper.formatShortCriticalText(
                timeShort ?: if (isTask) "待辦提醒" else "日程提醒"
            )
        }
        val compact = if (!timeShort.isNullOrBlank() && clean.length <= 2) {
            "$timeShort$clean"
        } else {
            clean
        }
        return LiveUpdateIslandHelper.formatShortCriticalText(compact)
    }

    /**
     * 產生一般日曆事件／Google 行事曆活動的通知標題與內容：
     * 確保通知跳出時同時帶出「事件名稱」與「日期／起訖時間／倒數／地點」。
     */
    fun buildEventNotificationContent(
        rawTitle: String,
        startDate: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        offsetMinutes: Int,
        location: String = "",
        isPrivacy: Boolean = false,
        noun: String = "活動"
    ): ReminderNotificationContent {
        if (isPrivacy) {
            return ReminderNotificationContent(
                title = "🔒 隱私日曆提醒",
                subtitle = "請開啟 DX日曆 驗證雙指紋鎖檢視內容",
                shortCriticalText = "隱私提醒",
                timeSummary = "隱私行程"
            )
        }
        val cleanTitle = rawTitle.trim().ifBlank { "未命名行程" }
        val dateLabel = "${startDate.monthValue}/${startDate.dayOfMonth}"
        val startClock = if (isAllDay || startTime == null) null else "%02d:%02d".format(startTime.hour, startTime.minute)
        val endClock = if (isAllDay || endTime == null) null else "%02d:%02d".format(endTime.hour, endTime.minute)

        val timeSummary = when {
            isAllDay || startClock == null -> "$dateLabel 全天"
            endClock != null && endClock != startClock -> "$dateLabel $startClock–$endClock"
            else -> "$dateLabel $startClock"
        }
        val title = when {
            isAllDay || startClock == null -> "📅 $cleanTitle · 全天"
            cleanTitle.contains(startClock) -> "📅 $cleanTitle"
            else -> "📅 $cleanTitle · $startClock"
        }
        val countdown = ReminderTimeFormatter.formatCountdownSubtitle(offsetMinutes, noun)
        val subtitle = buildString {
            append(timeSummary)
            append(" · ")
            append(countdown)
            if (location.isNotBlank()) {
                append(" · 📍")
                append(location.trim().take(24))
            }
        }
        return ReminderNotificationContent(
            title = title,
            subtitle = subtitle,
            shortCriticalText = buildIslandShortText(cleanTitle, startClock, isPrivacy = false, isTask = false),
            timeSummary = timeSummary
        )
    }

    /**
     * 產生 Google Tasks / 待辦事項的通知標題與內容：
     * 確保通知跳出時明確帶出「Tasks 事件名稱」與「日期與時間」。
     */
    fun buildTaskNotificationContent(
        rawTitle: String,
        dueDate: LocalDate,
        dueMinuteOfDay: Int?,
        offsetMinutes: Int,
        isPrivacy: Boolean = false
    ): ReminderNotificationContent {
        if (isPrivacy) {
            return ReminderNotificationContent(
                title = "🔒 隱私待辦提醒",
                subtitle = "請開啟 DX日曆 驗證雙指紋鎖檢視內容",
                shortCriticalText = "隱私待辦",
                timeSummary = "隱私待辦"
            )
        }
        val cleanTitle = rawTitle.trim().ifBlank { "待辦事項" }
        val dateLabel = "${dueDate.monthValue}/${dueDate.dayOfMonth}"
        val effectiveMin = dueMinuteOfDay ?: (9 * 60)
        val timeClock = "%02d:%02d".format((effectiveMin / 60).coerceIn(0, 23), (effectiveMin % 60).coerceIn(0, 59))
        val hasExplicitTime = dueMinuteOfDay != null
        val timeSummary = if (hasExplicitTime) "$dateLabel $timeClock" else "$dateLabel $timeClock（預設時刻）"
        val countdown = if (offsetMinutes <= 0) {
            "待辦時間到了"
        } else {
            "${ReminderTimeFormatter.formatDuration(offsetMinutes)}後到期"
        }
        val title = if (cleanTitle.contains(timeClock)) {
            "☑️ $cleanTitle"
        } else {
            "☑️ $cleanTitle · $timeClock"
        }
        val subtitle = "待辦提醒 · $timeSummary · $countdown"
        return ReminderNotificationContent(
            title = title,
            subtitle = subtitle,
            shortCriticalText = buildIslandShortText(cleanTitle, timeClock, isPrivacy = false, isTask = true),
            timeSummary = timeSummary
        )
    }

    suspend fun rescheduleAllUpcomingReminders(context: Context) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).calendarDao()
        val settings = com.dx.calendar.data.local.AppPreferences(context).getSettingsOnce()
        val routineEnabled = settings.routineMedicationFeatureEnabled
        val zone = settings.effectiveZoneId
        val now = System.currentTimeMillis()
        val events = dao.getAllEventsOnce()
        val tasks = dao.getAllTasksOnce()

        events.forEach { ev ->
            if (!ev.isPaused && (!ev.isRoutine || routineEnabled)) {
                scheduleForEvent(context, ev, now)
            } else {
                cancelEventAlarms(context, ev)
            }
        }
        tasks.forEach { tk ->
            if (!tk.isPaused && !tk.isCompleted && (!tk.isRoutine || routineEnabled)) {
                scheduleForTask(context, tk, now, settings.defaultTaskReminders)
            } else {
                cancelTaskAlarms(context, tk)
            }
        }

        // 同步為近 7 天內的「Google / 系統同步行事曆」排程真實提醒與連段鬧鐘／原子島通知！
        // 嚴格尊重「預設日程提醒時間」：若活動本身無個別提醒且使用者設為「不提示」，絕不彈出通知。
        val bridge = com.dx.calendar.data.sync.SystemCalendarBridge(context)
        if (bridge.hasReadPermission()) {
            val sysEvents = bridge.querySystemInstances(now - 60_000L, now + 7L * 86_400_000L)
                .filter { !settings.hiddenSystemCalIds.contains(it.calendarId.toString()) }
                .take(64)
            for (inst in sysEvents) {
                val meta = com.dx.calendar.data.sync.SystemEventAlarmPrefs.get(context, inst.eventId)
                val hasExplicitMeta = meta?.enableMultiCall == true || meta?.useAlarmStream == true
                val offsets = resolveSystemEventOffsets(
                    eventReminders = inst.reminderMinutes,
                    defaultEventReminders = settings.defaultEventReminders,
                    isAllDay = inst.isAllDay,
                    hasExplicitAlarmMeta = hasExplicitMeta
                )
                if (offsets.isEmpty()) {
                    cancelSystemEventAlarms(context, inst.eventId)
                    continue
                }

                val enableBurst = meta?.enableMultiCall ?: false
                val useAlarm = (meta?.useAlarmStream ?: false) || enableBurst
                val burstTotal = if (enableBurst) (meta?.multiCallCount ?: 3).coerceAtLeast(2) else 1
                val burstInterval = (meta?.multiCallIntervalMinutes ?: 3).coerceAtLeast(1)

                val eventDate: LocalDate
                val startTime: LocalTime?
                val endTime: LocalTime?
                val anchorStartMillis: Long
                if (inst.isAllDay) {
                    eventDate = Instant.ofEpochMilli(inst.beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    startTime = null
                    endTime = null
                    anchorStartMillis = eventDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                } else {
                    val startZdt = Instant.ofEpochMilli(inst.beginMillis).atZone(zone)
                    val endZdt = Instant.ofEpochMilli(inst.endMillis).atZone(zone)
                    eventDate = startZdt.toLocalDate()
                    startTime = startZdt.toLocalTime()
                    endTime = if (inst.endMillis > inst.beginMillis) endZdt.toLocalTime() else null
                    anchorStartMillis = inst.beginMillis
                }

                offsets.forEach { offsetMin ->
                    val triggerAt = anchorStartMillis - offsetMin * 60_000L
                    if (triggerAt > now + 2_000L) {
                        val content = buildEventNotificationContent(
                            rawTitle = inst.title,
                            startDate = eventDate,
                            startTime = startTime,
                            endTime = endTime,
                            isAllDay = inst.isAllDay,
                            offsetMinutes = offsetMin,
                            location = inst.location,
                            isPrivacy = false,
                            noun = "行程"
                        )
                        scheduleExactTrigger(
                            context = context,
                            requestCode = computeRequestCode("SYS_EVENT", inst.eventId, offsetMin, 1),
                            triggerAtMillis = triggerAt,
                            sourceType = "SYS_EVENT",
                            itemId = inst.eventId,
                            title = content.title,
                            subtitle = content.subtitle,
                            isPrivacy = false,
                            useAlarmStream = useAlarm,
                            enableMultiCall = enableBurst,
                            burstIndex = 1,
                            burstTotal = burstTotal,
                            burstIntervalMin = burstInterval,
                            offsetMinutes = offsetMin,
                            eventEpochDay = eventDate.toEpochDay(),
                            eventStartMillis = anchorStartMillis,
                            shortCriticalText = content.shortCriticalText,
                            timeSummary = content.timeSummary
                        )
                    }
                }
            }
        }
    }

    fun cancelEventAlarms(context: Context, event: CalendarEventEntity) {
        val offsets = (parseOffsets(event.reminderMinutesJson) + COMMON_CANCEL_OFFSETS).distinct()
        val burstTotal = if (event.enableMultiCall) event.multiCallCount.coerceAtLeast(3) else 3
        offsets.forEach { offsetMin ->
            cancelRemainingBurstCalls(context, "EVENT", event.id, offsetMin, burstTotal)
        }
    }

    fun cancelTaskAlarms(context: Context, task: TaskItemEntity) {
        val offsets = (parseOffsets(task.reminderMinutesJson) + COMMON_CANCEL_OFFSETS).distinct()
        val burstTotal = if (task.enableMultiCall) task.multiCallCount.coerceAtLeast(3) else 3
        offsets.forEach { offsetMin ->
            cancelRemainingBurstCalls(context, "TASK", task.id, offsetMin, burstTotal)
        }
    }

    fun cancelSystemEventAlarms(context: Context, eventId: Long, extraOffsets: List<Int> = emptyList()) {
        val offsets = (extraOffsets + COMMON_CANCEL_OFFSETS).distinct()
        offsets.forEach { offsetMin ->
            cancelRemainingBurstCalls(context, "SYS_EVENT", eventId, offsetMin, 5)
        }
    }

    fun scheduleForEvent(context: Context, event: CalendarEventEntity, nowMillis: Long = System.currentTimeMillis()) {
        if (event.isPaused) {
            cancelEventAlarms(context, event)
            return
        }
        val offsets = parseOffsets(event.reminderMinutesJson)
        if (offsets.isEmpty()) {
            cancelEventAlarms(context, event)
            return
        }

        val nextOccurrenceStart = findNextEventStartMillis(event, nowMillis) ?: return
        val zone = ZoneId.systemDefault()
        val startZdt = Instant.ofEpochMilli(nextOccurrenceStart).atZone(zone)
        val durationMs = (event.endEpochMillis - event.startEpochMillis).coerceAtLeast(0L)
        val endTime = if (!event.isAllDay && durationMs > 0L) {
            Instant.ofEpochMilli(nextOccurrenceStart + durationMs).atZone(zone).toLocalTime()
        } else null
        val cleanTitle = PrivacyCryptoManager.decryptIfNeeded(event.title)
        val cleanLocation = PrivacyCryptoManager.decryptIfNeeded(event.location)

        for (offsetMin in offsets) {
            val triggerAt = nextOccurrenceStart - offsetMin * 60_000L
            if (triggerAt > nowMillis - 5_000L) {
                val content = buildEventNotificationContent(
                    rawTitle = cleanTitle,
                    startDate = startZdt.toLocalDate(),
                    startTime = if (event.isAllDay) null else startZdt.toLocalTime(),
                    endTime = endTime,
                    isAllDay = event.isAllDay,
                    offsetMinutes = offsetMin,
                    location = cleanLocation,
                    isPrivacy = event.isPrivacy,
                    noun = "活動"
                )
                scheduleExactTrigger(
                    context = context,
                    requestCode = computeRequestCode("EVENT", event.id, offsetMin, 1),
                    triggerAtMillis = triggerAt.coerceAtLeast(nowMillis + 1_500L),
                    sourceType = "EVENT",
                    itemId = event.id,
                    title = content.title,
                    subtitle = content.subtitle,
                    isPrivacy = event.isPrivacy,
                    useAlarmStream = event.useAlarmStream || event.enableMultiCall,
                    enableMultiCall = event.enableMultiCall,
                    burstIndex = 1,
                    burstTotal = if (event.enableMultiCall) event.multiCallCount.coerceAtLeast(2) else 1,
                    burstIntervalMin = event.multiCallIntervalMinutes.coerceAtLeast(1),
                    offsetMinutes = offsetMin,
                    eventEpochDay = startZdt.toLocalDate().toEpochDay(),
                    eventStartMillis = nextOccurrenceStart,
                    shortCriticalText = content.shortCriticalText,
                    timeSummary = content.timeSummary
                )
            }
        }
    }

    fun scheduleForTask(
        context: Context,
        task: TaskItemEntity,
        nowMillis: Long = System.currentTimeMillis(),
        defaultTaskReminders: List<Int> = listOf(0)
    ) {
        if (task.isPaused || task.isCompleted) {
            cancelTaskAlarms(context, task)
            return
        }
        val dueDay = task.dueEpochDay ?: return
        val offsets = resolveTaskOffsets(
            taskReminders = parseOffsets(task.reminderMinutesJson),
            defaultTaskReminders = defaultTaskReminders,
            isCloudGoogleTask = !task.googleTaskId.isNullOrBlank(),
            hasExplicitAlarmMeta = task.enableMultiCall || task.useAlarmStream
        )
        if (offsets.isEmpty()) {
            cancelTaskAlarms(context, task)
            return
        }

        val zone = ZoneId.systemDefault()
        val minuteOfDay = task.dueMinuteOfDay ?: (9 * 60) // 無指定時刻預設早上 09:00 提醒
        val time = LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))
        val baseDate = LocalDate.ofEpochDay(dueDay)
        val recType = runCatching { RecurrenceType.valueOf(task.recurrenceType) }.getOrDefault(RecurrenceType.NONE)
        val nextDate = findNextDateByRecurrence(baseDate, recType, nowMillis, time) ?: return
        val dueMillis = nextDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
        val cleanTitle = PrivacyCryptoManager.decryptIfNeeded(task.title)

        for (offsetMin in offsets) {
            val triggerAt = dueMillis - offsetMin * 60_000L
            if (triggerAt > nowMillis - 5_000L) {
                val content = buildTaskNotificationContent(
                    rawTitle = cleanTitle,
                    dueDate = nextDate,
                    dueMinuteOfDay = task.dueMinuteOfDay,
                    offsetMinutes = offsetMin,
                    isPrivacy = task.isPrivacy
                )
                scheduleExactTrigger(
                    context = context,
                    requestCode = computeRequestCode("TASK", task.id, offsetMin, 1),
                    triggerAtMillis = triggerAt.coerceAtLeast(nowMillis + 1_500L),
                    sourceType = "TASK",
                    itemId = task.id,
                    title = content.title,
                    subtitle = content.subtitle,
                    isPrivacy = task.isPrivacy,
                    useAlarmStream = task.useAlarmStream || task.enableMultiCall,
                    enableMultiCall = task.enableMultiCall,
                    burstIndex = 1,
                    burstTotal = if (task.enableMultiCall) task.multiCallCount.coerceAtLeast(2) else 1,
                    burstIntervalMin = task.multiCallIntervalMinutes.coerceAtLeast(1),
                    offsetMinutes = offsetMin,
                    eventEpochDay = nextDate.toEpochDay(),
                    eventStartMillis = dueMillis,
                    shortCriticalText = content.shortCriticalText,
                    timeSummary = content.timeSummary
                )
            }
        }
    }

    fun scheduleNextBurstCall(
        context: Context,
        sourceType: String,
        itemId: Long,
        title: String,
        subtitle: String,
        isPrivacy: Boolean,
        useAlarmStream: Boolean,
        nextBurstIndex: Int,
        burstTotal: Int,
        burstIntervalMin: Int,
        offsetMinutes: Int,
        eventEpochDay: Long = Long.MIN_VALUE,
        eventStartMillis: Long = System.currentTimeMillis() + burstIntervalMin * 60_000L,
        shortCriticalText: String = "",
        timeSummary: String = ""
    ) {
        val triggerAt = System.currentTimeMillis() + burstIntervalMin * 60_000L
        scheduleExactTrigger(
            context = context,
            requestCode = computeRequestCode(sourceType, itemId, offsetMinutes, nextBurstIndex),
            triggerAtMillis = triggerAt,
            sourceType = sourceType,
            itemId = itemId,
            title = title,
            subtitle = subtitle,
            isPrivacy = isPrivacy,
            useAlarmStream = useAlarmStream,
            enableMultiCall = true,
            burstIndex = nextBurstIndex,
            burstTotal = burstTotal,
            burstIntervalMin = burstIntervalMin,
            offsetMinutes = offsetMinutes,
            eventEpochDay = eventEpochDay,
            eventStartMillis = eventStartMillis,
            shortCriticalText = shortCriticalText,
            timeSummary = timeSummary
        )
    }

    fun cancelRemainingBurstCalls(
        context: Context,
        sourceType: String,
        itemId: Long,
        offsetMinutes: Int,
        burstTotal: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (idx in 1..(burstTotal + 2)) {
            val rc = computeRequestCode(sourceType, itemId, offsetMinutes, idx)
            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_FIRE_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context,
                rc,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    /**
     * 連動手機原生「時鐘」App 建立真實系統鬧鐘 (`AlarmClock.ACTION_SET_ALARM`)
     */
    fun syncToSystemClockAlarm(context: Context, hour: Int, minute: Int, label: String) {
        runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, "DX日曆：$label")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * 立即發送一組多重呼叫展示提醒（供使用者在設定頁一鍵體驗全螢幕鬧鐘連段呼叫）
     */
    fun triggerImmediateDemoAlarm(context: Context) {
        val now = LocalTime.now()
        val clock = "%02d:%02d".format(now.hour, now.minute)
        scheduleExactTrigger(
            context = context,
            requestCode = 998877,
            triggerAtMillis = System.currentTimeMillis() + 1_200L,
            sourceType = "DEMO",
            itemId = 9999L,
            title = "📅 多重連段鬧鐘呼叫示範 · $clock",
            subtitle = "今日 $clock · 全螢幕強提醒 + 連段追擊呼叫測試",
            isPrivacy = false,
            useAlarmStream = true,
            enableMultiCall = true,
            burstIndex = 1,
            burstTotal = 3,
            burstIntervalMin = 1,
            offsetMinutes = 0,
            eventEpochDay = LocalDate.now().toEpochDay(),
            eventStartMillis = System.currentTimeMillis() + 1_200L,
            shortCriticalText = "連段鬧鐘",
            timeSummary = "今日 $clock"
        )
    }

    private fun scheduleExactTrigger(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        sourceType: String,
        itemId: Long,
        title: String,
        subtitle: String,
        isPrivacy: Boolean,
        useAlarmStream: Boolean,
        enableMultiCall: Boolean,
        burstIndex: Int,
        burstTotal: Int,
        burstIntervalMin: Int,
        offsetMinutes: Int,
        eventEpochDay: Long = Long.MIN_VALUE,
        eventStartMillis: Long = triggerAtMillis,
        shortCriticalText: String = "",
        timeSummary: String = ""
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_SOURCE_TYPE, sourceType)
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTITLE, subtitle)
            putExtra(EXTRA_IS_PRIVACY, isPrivacy)
            putExtra(EXTRA_USE_ALARM_STREAM, useAlarmStream)
            putExtra(EXTRA_ENABLE_MULTI_CALL, enableMultiCall)
            putExtra(EXTRA_BURST_INDEX, burstIndex)
            putExtra(EXTRA_BURST_TOTAL, burstTotal)
            putExtra(EXTRA_BURST_INTERVAL_MIN, burstIntervalMin)
            putExtra(EXTRA_OFFSET_MINUTES, offsetMinutes)
            putExtra(EXTRA_EVENT_EPOCH_DAY, eventEpochDay)
            putExtra(EXTRA_EVENT_START_MILLIS, eventStartMillis)
            putExtra(EXTRA_SHORT_CRITICAL, shortCriticalText)
            putExtra(EXTRA_TIME_SUMMARY, timeSummary)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        runCatching {
            if (canExact) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pi
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }.onFailure {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun findNextEventStartMillis(event: CalendarEventEntity, nowMillis: Long): Long? {
        val zone = ZoneId.systemDefault()
        val baseZdt = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone)
        val baseDate = baseZdt.toLocalDate()
        val baseTime = if (event.isAllDay) LocalTime.of(9, 0) else baseZdt.toLocalTime()
        val (recType, recIntervalWeeks) = com.dx.calendar.data.model.RecurrenceCodec.decode(event.recurrenceType)

        val nextDate = findNextDateByRecurrence(baseDate, recType, nowMillis, baseTime, recIntervalWeeks) ?: return null
        return nextDate.atTime(baseTime).atZone(zone).toInstant().toEpochMilli()
    }

    private fun findNextDateByRecurrence(
        baseDate: LocalDate,
        recType: RecurrenceType,
        nowMillis: Long,
        timeOfDay: LocalTime,
        intervalWeeks: Int = 1
    ): LocalDate? {
        val zone = ZoneId.systemDefault()
        if (recType == RecurrenceType.NONE) {
            val ms = baseDate.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
            return if (ms >= nowMillis - 60_000L) baseDate else null
        }

        val today = LocalDate.now()
        val candidate = if (today.isAfter(baseDate)) today else baseDate
        for (step in 0..370) {
            val d = candidate.plusDays(step.toLong())
            val ms = d.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
            if (ms < nowMillis) continue
            val matches = when (recType) {
                RecurrenceType.NONE -> d == baseDate
                RecurrenceType.DAILY -> true
                RecurrenceType.WEEKDAYS -> d.dayOfWeek.value in 1..5
                RecurrenceType.WEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(baseDate, d) % 7L == 0L
                RecurrenceType.BIWEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(baseDate, d) % 14L == 0L
                RecurrenceType.CUSTOM_WEEKS -> {
                    val periodDays = intervalWeeks.coerceIn(2, 52) * 7L
                    java.time.temporal.ChronoUnit.DAYS.between(baseDate, d) % periodDays == 0L
                }
                RecurrenceType.MONTHLY -> d.dayOfMonth == baseDate.dayOfMonth
                RecurrenceType.LUNAR_MONTHLY -> {
                    val bL = LunarCalendarEngine.getLunarInfo(baseDate)
                    val cL = LunarCalendarEngine.getLunarInfo(d)
                    cL.lunarDay == bL.lunarDay && !cL.isLeapMonth
                }
                RecurrenceType.LUNAR_FIRST_FIFTEENTH -> {
                    val cL = LunarCalendarEngine.getLunarInfo(d)
                    (cL.lunarDay == 1 || cL.lunarDay == 15) && !cL.isLeapMonth
                }
                RecurrenceType.YEARLY -> d.monthValue == baseDate.monthValue && d.dayOfMonth == baseDate.dayOfMonth
                RecurrenceType.LUNAR_YEARLY -> {
                    val bL = LunarCalendarEngine.getLunarInfo(baseDate)
                    val cL = LunarCalendarEngine.getLunarInfo(d)
                    cL.lunarMonth == bL.lunarMonth && cL.lunarDay == bL.lunarDay && !cL.isLeapMonth
                }
            }
            if (matches) return d
        }
        return null
    }

    private fun parseOffsets(json: String): List<Int> {
        return runCatching {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun computeRequestCode(sourceType: String, id: Long, offsetMin: Int, burstIdx: Int): Int {
        return abs("$sourceType:$id:$offsetMin:$burstIdx".hashCode())
    }
}
