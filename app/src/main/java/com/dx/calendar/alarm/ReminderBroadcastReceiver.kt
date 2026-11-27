package com.dx.calendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.dx.calendar.MainActivity
import com.dx.calendar.data.local.AppPreferences
import com.dx.calendar.util.DynamicAppIconManager
import com.dx.calendar.widget.WidgetRefreshHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ALARM_BURST = "dx_alarm_burst_channel"
        const val CHANNEL_STANDARD_REMINDER = "dx_standard_reminder_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = runCatching { goAsync() }.getOrNull()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = AppPreferences(context).getSettingsOnce()
                        DynamicAppIconManager.syncIcon(context, settings.dynamicDayIconEnabled, settings.dynamicDayIconShowWeekday)
                        DynamicAppIconManager.scheduleMidnightRefresh(context)
                        MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(context)
                        WidgetRefreshHelper.refreshAllWidgets(context)
                    } finally {
                        runCatching { pending?.finish() }
                    }
                }
            }

            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            DynamicAppIconManager.ACTION_UPDATE_DYNAMIC_ICON -> {
                val pending = runCatching { goAsync() }.getOrNull()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = AppPreferences(context).getSettingsOnce()
                        DynamicAppIconManager.syncIcon(context, settings.dynamicDayIconEnabled, settings.dynamicDayIconShowWeekday)
                        DynamicAppIconManager.scheduleMidnightRefresh(context)
                        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
                            MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(context)
                        }
                        WidgetRefreshHelper.refreshAllWidgets(context)
                    } finally {
                        runCatching { pending?.finish() }
                    }
                }
            }

            LiveUpdateIslandHelper.ACTION_DISMISS_ISLAND -> {
                val notifId = intent.getIntExtra("notif_id", LiveUpdateIslandHelper.DEMO_ISLAND_NOTIF_ID)
                LiveUpdateIslandHelper.cancelIslandNotification(context, notifId)
            }

            MultiCallAlarmScheduler.ACTION_DISMISS_BURST -> {
                val sourceType = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE) ?: "EVENT"
                val itemId = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, 0L)
                val offsetMin = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_OFFSET_MINUTES, 0)
                val total = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_BURST_TOTAL, 5)
                val notifId = intent.getIntExtra("notif_id", 0)

                MultiCallAlarmScheduler.cancelRemainingBurstCalls(context, sourceType, itemId, offsetMin, total)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
                nm.cancel(notifId + 50000)
                AlarmRingingActivity.stopActiveRinging()
            }

            MultiCallAlarmScheduler.ACTION_SNOOZE_REMINDER -> {
                val sourceType = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE) ?: "EVENT"
                val itemId = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, 0L)
                val title = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_TITLE) ?: "提醒"
                val isPrivacy = intent.getBooleanExtra(MultiCallAlarmScheduler.EXTRA_IS_PRIVACY, false)
                val eventEpochDay = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_EVENT_EPOCH_DAY, Long.MIN_VALUE)
                val shortCritical = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SHORT_CRITICAL).orEmpty()
                val timeSummary = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_TIME_SUMMARY).orEmpty()
                val notifId = intent.getIntExtra("notif_id", 0)

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
                nm.cancel(notifId + 50000)
                AlarmRingingActivity.stopActiveRinging()

                val snoozeSub = if (timeSummary.isNotBlank()) {
                    "$timeSummary · 貪睡延後 5 分鐘提醒"
                } else {
                    "貪睡延後 5 分鐘提醒"
                }

                // 延後 5 分鐘再次呼叫（保留原事件名稱、時間與直達目標日期）
                MultiCallAlarmScheduler.scheduleNextBurstCall(
                    context = context,
                    sourceType = sourceType,
                    itemId = itemId,
                    title = title,
                    subtitle = snoozeSub,
                    isPrivacy = isPrivacy,
                    useAlarmStream = true,
                    nextBurstIndex = 1,
                    burstTotal = 1,
                    burstIntervalMin = 5,
                    offsetMinutes = 0,
                    eventEpochDay = eventEpochDay,
                    shortCriticalText = shortCritical,
                    timeSummary = timeSummary
                )
            }

            MultiCallAlarmScheduler.ACTION_FIRE_REMINDER -> {
                handleFireReminder(context, intent)
            }
        }
    }

    private fun handleFireReminder(context: Context, intent: Intent) {
        ensureNotificationChannels(context)
        val sourceType = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE) ?: "EVENT"
        val itemId = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, 0L)
        val rawTitle = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_TITLE) ?: "DX日曆提醒"
        val subtitle = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SUBTITLE) ?: "時間到了"
        val isPrivacy = intent.getBooleanExtra(MultiCallAlarmScheduler.EXTRA_IS_PRIVACY, false)
        val useAlarmStream = intent.getBooleanExtra(MultiCallAlarmScheduler.EXTRA_USE_ALARM_STREAM, false)
        val enableMultiCall = intent.getBooleanExtra(MultiCallAlarmScheduler.EXTRA_ENABLE_MULTI_CALL, false)
        val burstIndex = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_BURST_INDEX, 1)
        val burstTotal = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_BURST_TOTAL, 1)
        val burstIntervalMin = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_BURST_INTERVAL_MIN, 3)
        val offsetMin = intent.getIntExtra(MultiCallAlarmScheduler.EXTRA_OFFSET_MINUTES, 0)
        val eventEpochDay = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_EVENT_EPOCH_DAY, Long.MIN_VALUE)
        val eventStartMillis = intent.getLongExtra(
            MultiCallAlarmScheduler.EXTRA_EVENT_START_MILLIS,
            System.currentTimeMillis() + offsetMin.coerceAtLeast(0) * 60_000L
        )
        val rawShortCritical = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SHORT_CRITICAL).orEmpty()
        val timeSummary = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_TIME_SUMMARY).orEmpty()
        val isTaskSource = sourceType == "TASK"

        // 隱私日曆脫敏保護：未解鎖前通知列絕不外洩隱私內容！
        val displayTitle = if (isPrivacy) {
            if (isTaskSource) "🔒 隱私待辦提醒" else "🔒 隱私日曆提醒"
        } else {
            rawTitle
        }
        val displayBody = buildString {
            if (isPrivacy) append("請開啟 DX日曆 驗證雙指紋鎖檢視內容")
            else append(subtitle)
            if (enableMultiCall && burstTotal > 1) {
                append(" · 連段追擊呼叫 ($burstIndex/$burstTotal)")
            }
        }

        val notifId = ("$sourceType:$itemId:$offsetMin".hashCode() and 0x7FFFFFFF)

        // 若開啟連段追擊呼叫，且尚未達到最後一段，立即排程下一波追擊鬧鐘！
        if (enableMultiCall && burstIndex < burstTotal) {
            MultiCallAlarmScheduler.scheduleNextBurstCall(
                context = context,
                sourceType = sourceType,
                itemId = itemId,
                title = rawTitle,
                subtitle = subtitle,
                isPrivacy = isPrivacy,
                useAlarmStream = useAlarmStream,
                nextBurstIndex = burstIndex + 1,
                burstTotal = burstTotal,
                burstIntervalMin = burstIntervalMin,
                offsetMinutes = offsetMin,
                eventEpochDay = eventEpochDay,
                eventStartMillis = eventStartMillis,
                shortCriticalText = rawShortCritical,
                timeSummary = timeSummary
            )
        }

        // 若為重複活動的第一段提醒，同步排程下一個週期
        if (burstIndex == 1 && sourceType != "DEMO") {
            CoroutineScope(Dispatchers.IO).launch {
                MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(context)
            }
        }

        val dismissIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = MultiCallAlarmScheduler.ACTION_DISMISS_BURST
            putExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE, sourceType)
            putExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, itemId)
            putExtra(MultiCallAlarmScheduler.EXTRA_OFFSET_MINUTES, offsetMin)
            putExtra(MultiCallAlarmScheduler.EXTRA_BURST_TOTAL, burstTotal)
            putExtra("notif_id", notifId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = MultiCallAlarmScheduler.ACTION_SNOOZE_REMINDER
            putExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE, sourceType)
            putExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, itemId)
            putExtra(MultiCallAlarmScheduler.EXTRA_TITLE, rawTitle)
            putExtra(MultiCallAlarmScheduler.EXTRA_IS_PRIVACY, isPrivacy)
            putExtra(MultiCallAlarmScheduler.EXTRA_EVENT_EPOCH_DAY, eventEpochDay)
            putExtra(MultiCallAlarmScheduler.EXTRA_SHORT_CRITICAL, rawShortCritical)
            putExtra(MultiCallAlarmScheduler.EXTRA_TIME_SUMMARY, timeSummary)
            putExtra("notif_id", notifId)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE, sourceType)
            putExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, itemId)
            putExtra(MultiCallAlarmScheduler.EXTRA_TITLE, displayTitle)
            putExtra(MultiCallAlarmScheduler.EXTRA_SUBTITLE, displayBody)
            putExtra(MultiCallAlarmScheduler.EXTRA_BURST_INDEX, burstIndex)
            putExtra(MultiCallAlarmScheduler.EXTRA_BURST_TOTAL, burstTotal)
            putExtra(MultiCallAlarmScheduler.EXTRA_OFFSET_MINUTES, offsetMin)
            putExtra("notif_id", notifId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            notifId + 3,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openMainIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (eventEpochDay != Long.MIN_VALUE) {
                putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, eventEpochDay)
                if (itemId > 0L && sourceType != "DEMO") {
                    putExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, itemId)
                    putExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, isTaskSource)
                }
            }
        }
        val openMainPi = PendingIntent.getActivity(
            context,
            notifId + 4,
            openMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val liveIslandEnabled = runCatching {
            runBlocking(Dispatchers.IO) { AppPreferences(context).getSettingsOnce().liveUpdateIslandEnabled }
        }.getOrDefault(true)

        val shortText = when {
            isPrivacy -> if (isTaskSource) "隱私待辦" else "隱私提醒"
            enableMultiCall && burstTotal > 1 -> "連段$burstIndex/$burstTotal"
            rawShortCritical.isNotBlank() -> LiveUpdateIslandHelper.formatShortCriticalText(rawShortCritical)
            else -> MultiCallAlarmScheduler.buildIslandShortText(rawTitle, isPrivacy = false, isTask = isTaskSource)
        }
        val progressPct = if (enableMultiCall && burstTotal > 1) {
            ((burstIndex.toFloat() / burstTotal.toFloat()) * 100f).toInt().coerceIn(10, 100)
        } else if (offsetMin > 0) {
            (100 - (offsetMin * 2)).coerceIn(20, 95)
        } else {
            100
        }

        if (liveIslandEnabled && !useAlarmStream && !enableMultiCall) {
            // 一般日曆／待辦提醒直接以「原生原子島 Live Updates 通知」單一整合發送：完整帶出事件名稱與時間，點擊直達該筆行程！
            LiveUpdateIslandHelper.postLiveUpdateNotification(
                context = context,
                notifId = notifId,
                title = displayTitle,
                body = displayBody,
                shortCriticalText = shortText,
                progressPercent = progressPct,
                targetWhenMillis = eventStartMillis,
                alertWithSound = true,
                dismissPendingIntent = dismissPi,
                snoozePendingIntent = snoozePi,
                contentPendingIntent = openMainPi
            )
            return
        }

        val channelId = if (useAlarmStream || enableMultiCall) CHANNEL_ALARM_BURST else CHANNEL_STANDARD_REMINDER
        val dismissBtnText = if (enableMultiCall && burstTotal > 1) "確認收到 (停止連段)" else "確認收到"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.dx.calendar.R.drawable.ic_stat_calendar_island)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setWhen(eventStartMillis)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (useAlarmStream) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(openMainPi)
            .addAction(0, dismissBtnText, dismissPi)
            .addAction(0, "延後 5 分鐘", snoozePi)

        if (useAlarmStream || enableMultiCall) {
            builder.setFullScreenIntent(fullScreenPi, true)
            runCatching { context.startActivity(fullScreenIntent) }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())

        // 連段強鬧鐘模式下，亦同步推送安靜上島膠囊顯示事件名稱與連段進度
        if (liveIslandEnabled) {
            LiveUpdateIslandHelper.postLiveUpdateNotification(
                context = context,
                notifId = notifId + 50000,
                title = displayTitle,
                body = displayBody,
                shortCriticalText = shortText,
                progressPercent = progressPct,
                targetWhenMillis = eventStartMillis,
                alertWithSound = false,
                dismissPendingIntent = dismissPi,
                snoozePendingIntent = snoozePi,
                contentPendingIntent = openMainPi
            )
        }
    }

    private fun ensureNotificationChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val chAlarm = NotificationChannel(
            CHANNEL_ALARM_BURST,
            "多重連段強提醒與鬧鐘",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用於連段追擊呼叫與全螢幕鬧鐘強提醒"
            enableVibration(true)
            setSound(alarmSound, alarmAttrs)
        }

        val chStandard = NotificationChannel(
            CHANNEL_STANDARD_REMINDER,
            "一般日程與待辦提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "一般日曆活動與待辦事項通知"
            enableVibration(true)
        }

        nm.createNotificationChannel(chAlarm)
        nm.createNotificationChannel(chStandard)
    }
}
