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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * 多重呼叫（連段追擊提醒）排程器與 Android 系統原生鬧鐘連動引擎
 */
object MultiCallAlarmScheduler {

    const val ACTION_FIRE_REMINDER = "com.dx.calendar.ACTION_FIRE_REMINDER"
    const val ACTION_DISMISS_BURST = "com.dx.calendar.ACTION_DISMISS_BURST"
    const val ACTION_SNOOZE_REMINDER = "com.dx.calendar.ACTION_SNOOZE_REMINDER"

    const val EXTRA_SOURCE_TYPE = "extra_source_type" // "EVENT" or "TASK"
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

    private val gson = Gson()

    suspend fun rescheduleAllUpcomingReminders(context: Context) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).calendarDao()
        val now = System.currentTimeMillis()
        val events = dao.getAllEventsOnce()
        val tasks = dao.getAllTasksOnce()

        events.forEach { ev ->
            if (!ev.isPaused) {
                scheduleForEvent(context, ev, now)
            }
        }
        tasks.forEach { tk ->
            if (!tk.isPaused && !tk.isCompleted) {
                scheduleForTask(context, tk, now)
            }
        }
    }

    fun scheduleForEvent(context: Context, event: CalendarEventEntity, nowMillis: Long = System.currentTimeMillis()) {
        if (event.isPaused) return
        val offsets = parseOffsets(event.reminderMinutesJson)
        if (offsets.isEmpty()) return

        val nextOccurrenceStart = findNextEventStartMillis(event, nowMillis) ?: return
        val cleanTitle = PrivacyCryptoManager.decryptIfNeeded(event.title)

        for (offsetMin in offsets) {
            val triggerAt = nextOccurrenceStart - offsetMin * 60_000L
            if (triggerAt > nowMillis - 5_000L) {
                val subtitle = if (offsetMin == 0) "活動現在開始" else "${offsetMin} 分鐘後開始"
                scheduleExactTrigger(
                    context = context,
                    requestCode = computeRequestCode("EVENT", event.id, offsetMin, 1),
                    triggerAtMillis = triggerAt.coerceAtLeast(nowMillis + 1_500L),
                    sourceType = "EVENT",
                    itemId = event.id,
                    title = cleanTitle,
                    subtitle = subtitle,
                    isPrivacy = event.isPrivacy,
                    useAlarmStream = event.useAlarmStream || event.enableMultiCall,
                    enableMultiCall = event.enableMultiCall,
                    burstIndex = 1,
                    burstTotal = if (event.enableMultiCall) event.multiCallCount.coerceAtLeast(2) else 1,
                    burstIntervalMin = event.multiCallIntervalMinutes.coerceAtLeast(1),
                    offsetMinutes = offsetMin
                )
            }
        }
    }

    fun scheduleForTask(context: Context, task: TaskItemEntity, nowMillis: Long = System.currentTimeMillis()) {
        if (task.isPaused || task.isCompleted) return
        val dueDay = task.dueEpochDay ?: return
        val offsets = parseOffsets(task.reminderMinutesJson)
        if (offsets.isEmpty()) return

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
                scheduleExactTrigger(
                    context = context,
                    requestCode = computeRequestCode("TASK", task.id, offsetMin, 1),
                    triggerAtMillis = triggerAt.coerceAtLeast(nowMillis + 1_500L),
                    sourceType = "TASK",
                    itemId = task.id,
                    title = cleanTitle,
                    subtitle = "待辦提醒",
                    isPrivacy = task.isPrivacy,
                    useAlarmStream = task.useAlarmStream || task.enableMultiCall,
                    enableMultiCall = task.enableMultiCall,
                    burstIndex = 1,
                    burstTotal = if (task.enableMultiCall) task.multiCallCount.coerceAtLeast(2) else 1,
                    burstIntervalMin = task.multiCallIntervalMinutes.coerceAtLeast(1),
                    offsetMinutes = offsetMin
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
        offsetMinutes: Int
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
            offsetMinutes = offsetMinutes
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
        scheduleExactTrigger(
            context = context,
            requestCode = 998877,
            triggerAtMillis = System.currentTimeMillis() + 1_200L,
            sourceType = "DEMO",
            itemId = 9999L,
            title = "多重連段鬧鐘呼叫示範",
            subtitle = "全螢幕強提醒 + 連段追擊呼叫測試",
            isPrivacy = false,
            useAlarmStream = true,
            enableMultiCall = true,
            burstIndex = 1,
            burstTotal = 3,
            burstIntervalMin = 1,
            offsetMinutes = 0
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
        offsetMinutes: Int
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
        val recType = runCatching { RecurrenceType.valueOf(event.recurrenceType) }.getOrDefault(RecurrenceType.NONE)

        val nextDate = findNextDateByRecurrence(baseDate, recType, nowMillis, baseTime) ?: return null
        return nextDate.atTime(baseTime).atZone(zone).toInstant().toEpochMilli()
    }

    private fun findNextDateByRecurrence(
        baseDate: LocalDate,
        recType: RecurrenceType,
        nowMillis: Long,
        timeOfDay: LocalTime
    ): LocalDate? {
        val zone = ZoneId.systemDefault()
        if (recType == RecurrenceType.NONE) {
            val ms = baseDate.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
            return if (ms >= nowMillis - 60_000L) baseDate else null
        }

        val today = LocalDate.now()
        var candidate = if (today.isAfter(baseDate)) today else baseDate
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
                RecurrenceType.MONTHLY -> d.dayOfMonth == baseDate.dayOfMonth
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
