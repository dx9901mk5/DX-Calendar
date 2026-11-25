package com.dx.calendar.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.dx.calendar.MainActivity

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
 * 完整遵守 Android 16 Live Updates 與 OriginOS 6 原子島規範：
 * 1. Manifest 宣告 `android.permission.POST_PROMOTED_NOTIFICATIONS`
 * 2. 必須設定 `setOngoing(true)` + `setRequestPromotedOngoing(true)` + `setShortCriticalText(...)` (<= 7 字元)
 * 3. 支援 `Notification.ProgressStyle` 進度條樣式（亦支援標準 / BigText 樣式）
 * 4. 嚴格避開上島禁忌：不使用 `setCustomContentView` / RemoteViews、不設 `setColorized(true)`、
 *    Channel 重要性為 `IMPORTANCE_DEFAULT`（絕非 `IMPORTANCE_MIN`）、不設群組摘要（`setGroupSummary`）
 * 5. 提供 `canPostPromotedNotifications()` 權限檢查、`hasPromotableCharacteristics()` 資格除錯與系統授權導引
 */
object LiveUpdateIslandHelper {

    const val CHANNEL_LIVE_UPDATE_ISLAND = "dx_live_update_island_channel"
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
     * 導引使用者前往系統「即時更新 / 上島通知」授權頁 (`Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`)
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

    /**
     * 格式化膠囊短文字（建議 7 字元內，避免膠囊截斷）
     */
    fun formatShortCriticalText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "提醒中"
        return if (trimmed.length <= MAX_SHORT_CRITICAL_CHARS) {
            trimmed
        } else {
            trimmed.take(MAX_SHORT_CRITICAL_CHARS)
        }
    }

    fun ensureLiveUpdateChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 禁忌防範：絕不可設為 IMPORTANCE_MIN；使用 IMPORTANCE_DEFAULT 確保可上島且不干擾額外重複音效
        val channel = NotificationChannel(
            CHANNEL_LIVE_UPDATE_ISLAND,
            "原子島即時更新通知 (Live Updates)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "於 Android 16 / OriginOS 6 原子島膠囊即時顯示即將到來或進行中的日程提醒"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * 發布可上島的 Live Updates 即時通知
     */
    fun postLiveUpdateNotification(
        context: Context,
        notifId: Int,
        title: String,
        body: String,
        shortCriticalText: String,
        progressPercent: Int? = null,
        dismissPendingIntent: PendingIntent? = null,
        snoozePendingIntent: PendingIntent? = null
    ): LiveUpdatePostResult {
        ensureLiveUpdateChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val safeShort = formatShortCriticalText(shortCriticalText)

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
            val builder = Notification.Builder(context, CHANNEL_LIVE_UPDATE_ISLAND)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openMainPi)
                .setOngoing(true)
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                .setShortCriticalText(safeShort)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)

            // 選用 ProgressStyle（進度條上島）或標準 BigTextStyle，嚴禁 CustomContentView / setColorized(true) / GroupSummary
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
            // Android 8–15 相容降級：標準持續進度通知（同樣遵守無 RemoteViews / 無 Colorized）
            val compatBuilder = NotificationCompat.Builder(context, CHANNEL_LIVE_UPDATE_ISLAND)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText("$safeShort · $body")
                .setContentIntent(openMainPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
            title = "DX日曆 · 專案週會即將開始",
            body = "距離開始還有 3 分鐘 · 點擊進入日曆或按確認收到關閉原子島",
            shortCriticalText = "3 min",
            progressPercent = 65
        )
    }

    fun cancelIslandNotification(context: Context, notifId: Int = DEMO_ISLAND_NOTIF_ID) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifId)
    }
}
