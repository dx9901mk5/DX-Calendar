package com.dx.calendar.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.dx.calendar.MainActivity
import com.dx.calendar.R

data class LiveUpdateIslandStatus(
    val isAndroid16OrHigher: Boolean,
    val canPostPromoted: Boolean
)

data class LiveUpdatePostResult(
    val notifId: Int,
    val isAndroid16OrHigher: Boolean,
    val canPostPromoted: Boolean,
    val hasPromotableCharacteristics: Boolean
)

/**
 * Android 16 原生 Live Updates（即時更新通知 / vivo OriginOS 6 原子島上島引擎）
 *
 * 關鍵修正與保證：
 * 1. 明確呼叫 Android 16 SDK `Notification.Builder.setRequestPromotedOngoing(true)` 設定 `FLAG_REQUEST_PROMOTED_ONGOING`，
 *    確保 `notification.hasPromotableCharacteristics() == true`，真正登上 vivo OriginOS 原子島與 Android 16 狀態列膠囊！
 * 2. 採用獨立單色向量日曆圖示 (`R.drawable.ic_stat_calendar_island`)、`CATEGORY_EVENT`、`setOngoing(true)`、
 *    `setShortCriticalText` (<= 7 字元) 與 `Notification.ProgressStyle`。
 * 3. 日曆提醒觸發時直接以此單一 Promoted 通知彈出提醒並無縫收攏進入原子島，避免雙重通知干擾。
 */
object LiveUpdateIslandHelper {

    const val CHANNEL_LIVE_UPDATE_ISLAND = "dx_atomic_island_live_v2"
    const val CHANNEL_LIVE_UPDATE_SILENT = "dx_atomic_island_silent_v2"
    const val ACTION_DISMISS_ISLAND = "com.dx.calendar.ACTION_DISMISS_ISLAND"
    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    const val DEMO_ISLAND_NOTIF_ID = 881601
    const val MAX_SHORT_CRITICAL_CHARS = 7

    fun isAndroid16Supported(): Boolean = Build.VERSION.SDK_INT >= 36

    fun getStatus(context: Context): LiveUpdateIslandStatus {
        val api36 = isAndroid16Supported()
        val canPost = if (api36) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
        } else {
            false
        }
        return LiveUpdateIslandStatus(
            isAndroid16OrHigher = api36,
            canPostPromoted = canPost
        )
    }

    /**
     * 導引使用者前往系統「即時更新 / 原子島通知」授權頁 (`Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`)
     */
    fun openPromotionSettings(context: Context) {
        val intent = if (isAndroid16Supported()) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    fun formatShortCriticalText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "提醒中"
        return if (trimmed.length <= MAX_SHORT_CRITICAL_CHARS) {
            trimmed
        } else {
            trimmed.take(MAX_SHORT_CRITICAL_CHARS)
        }
    }

    fun ensureLiveUpdateChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // 主提醒上島頻道：IMPORTANCE_HIGH 支援彈出提醒後直接收合駐留於原子島膠囊
        val alertChannel = NotificationChannel(
            CHANNEL_LIVE_UPDATE_ISLAND,
            "日曆提醒與原子島即時通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "日程開始前與進行中提醒，支援直接登上 vivo 原子島 / Android 16 Live Updates"
            enableVibration(true)
            if (soundUri != null) setSound(soundUri, audioAttrs)
            setShowBadge(true)
        }

        // 靜默更新上島頻道（供背景進度刷新或鬧鐘音軌另外播放時使用，避免重複響鈴）
        val silentChannel = NotificationChannel(
            CHANNEL_LIVE_UPDATE_SILENT,
            "原子島狀態即時更新",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "於原子島膠囊安靜更新日程倒數與進度"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        nm.createNotificationChannel(alertChannel)
        nm.createNotificationChannel(silentChannel)
    }

    /**
     * 發布可登上原子島的日曆通知（Android 16 原生 Promoted Ongoing + OriginOS 相容）
     */
    fun postLiveUpdateNotification(
        context: Context,
        notifId: Int,
        title: String,
        body: String,
        shortCriticalText: String,
        progressPercent: Int? = null,
        targetWhenMillis: Long = System.currentTimeMillis(),
        alertWithSound: Boolean = true,
        dismissPendingIntent: PendingIntent? = null,
        snoozePendingIntent: PendingIntent? = null
    ): LiveUpdatePostResult {
        ensureLiveUpdateChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val safeShort = formatShortCriticalText(shortCriticalText)
        val channelId = if (alertWithSound) CHANNEL_LIVE_UPDATE_ISLAND else CHANNEL_LIVE_UPDATE_SILENT

        val openMainPi = PendingIntent.getActivity(
            context,
            notifId + 10,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finalDismissPi = dismissPendingIntent ?: PendingIntent.getBroadcast(
            context,
            notifId + 11,
            Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_DISMISS_ISLAND
                putExtra("notif_id", notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= 36) {
            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_calendar_island)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openMainPi)
                .setCategory(Notification.CATEGORY_EVENT)
                .setWhen(targetWhenMillis)
                .setShowWhen(true)
                .setOngoing(true)
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                .setShortCriticalText(safeShort)
                .setOnlyAlertOnce(!alertWithSound)
                .setVisibility(Notification.VISIBILITY_PUBLIC)

            // 動態呼叫系統執行期 setRequestPromotedOngoing(true) 確保同時寫入 FLAG 與 Extra
            runCatching {
                builder.javaClass
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(builder, true)
            }

            if (progressPercent != null) {
                val style = Notification.ProgressStyle()
                    .setProgress(progressPercent.coerceIn(0, 100))
                builder.setStyle(style)
            } else {
                builder.setStyle(Notification.BigTextStyle().bigText(body))
            }

            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    "確認收到",
                    finalDismissPi
                ).build()
            )
            if (snoozePendingIntent != null) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_lock_idle_alarm),
                        "延後 5 分鐘",
                        snoozePendingIntent
                    ).build()
                )
            }

            val notification = builder.build()
            val canPost = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
            val promotable = runCatching { notification.hasPromotableCharacteristics() }.getOrDefault(false)
            nm.notify(notifId, notification)

            return LiveUpdatePostResult(
                notifId = notifId,
                isAndroid16OrHigher = true,
                canPostPromoted = canPost,
                hasPromotableCharacteristics = promotable
            )
        } else {
            val compatBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_calendar_island)
                .setContentTitle(title)
                .setContentText("$safeShort · $body")
                .setContentIntent(openMainPi)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setWhen(targetWhenMillis)
                .setShowWhen(true)
                .setOngoing(true)
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                .setOnlyAlertOnce(!alertWithSound)
                .setPriority(if (alertWithSound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, "確認收到", finalDismissPi)

            if (progressPercent != null) {
                compatBuilder.setProgress(100, progressPercent.coerceIn(0, 100), false)
            }
            if (snoozePendingIntent != null) {
                compatBuilder.addAction(0, "延後 5 分鐘", snoozePendingIntent)
            }

            nm.notify(notifId, compatBuilder.build())
            return LiveUpdatePostResult(
                notifId = notifId,
                isAndroid16OrHigher = false,
                canPostPromoted = false,
                hasPromotableCharacteristics = false
            )
        }
    }

    /**
     * 一鍵發送原子島 Live Updates 測試通知（附 ProgressStyle 進度條與資格回報）
     */
    fun postDemoIslandNotification(context: Context): LiveUpdatePostResult {
        return postLiveUpdateNotification(
            context = context,
            notifId = DEMO_ISLAND_NOTIF_ID,
            title = "DX日曆 · 重要會議即將開始",
            body = "距離開始還有 3 分鐘 · 點擊開啟日曆或按確認收到關閉原子島",
            shortCriticalText = "3分後",
            progressPercent = 70,
            targetWhenMillis = System.currentTimeMillis() + 180_000L,
            alertWithSound = true
        )
    }

    fun cancelIslandNotification(context: Context, notifId: Int = DEMO_ISLAND_NOTIF_ID) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifId)
    }
}
