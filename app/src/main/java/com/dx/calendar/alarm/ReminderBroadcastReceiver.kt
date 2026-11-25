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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ALARM_BURST = "dx_alarm_burst_channel"
        const val CHANNEL_STANDARD_REMINDER = "dx_standard_reminder_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(context)
                }
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
                AlarmRingingActivity.stopActiveRinging()
            }

            MultiCallAlarmScheduler.ACTION_SNOOZE_REMINDER -> {
                val sourceType = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_SOURCE_TYPE) ?: "EVENT"
                val itemId = intent.getLongExtra(MultiCallAlarmScheduler.EXTRA_ITEM_ID, 0L)
                val title = intent.getStringExtra(MultiCallAlarmScheduler.EXTRA_TITLE) ?: "提醒"
                val isPrivacy = intent.getBooleanExtra(MultiCallAlarmScheduler.EXTRA_IS_PRIVACY, false)
                val notifId = intent.getIntExtra("notif_id", 0)

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
                AlarmRingingActivity.stopActiveRinging()

                // 延後 5 分鐘再次呼叫
                MultiCallAlarmScheduler.scheduleNextBurstCall(
                    context = context,
                    sourceType = sourceType,
                    itemId = itemId,
                    title = title,
                    subtitle = "貪睡延後提醒",
                    isPrivacy = isPrivacy,
                    useAlarmStream = true,
                    nextBurstIndex = 1,
                    burstTotal = 1,
                    burstIntervalMin = 5,
                    offsetMinutes = 0
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

        // 隱私日曆脫敏保護：未解鎖前通知列絕不外洩隱私內容！
        val displayTitle = if (isPrivacy) "🔒 隱私日曆提醒" else rawTitle
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
                offsetMinutes = offsetMin
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

        val openMainPi = PendingIntent.getActivity(
            context,
            notifId + 4,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (useAlarmStream || enableMultiCall) CHANNEL_ALARM_BURST else CHANNEL_STANDARD_REMINDER
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (useAlarmStream) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openMainPi)
            .addAction(0, "確認收到 (停止連段)", dismissPi)
            .addAction(0, "延後 5 分鐘", snoozePi)

        if (useAlarmStream || enableMultiCall) {
            builder.setFullScreenIntent(fullScreenPi, true)
            // 若當前處於前景/桌面可直接啟動全螢幕鬧鐘響鈴頁
            runCatching { context.startActivity(fullScreenIntent) }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())
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
