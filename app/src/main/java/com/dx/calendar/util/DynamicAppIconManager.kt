package com.dx.calendar.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.dx.calendar.alarm.ReminderBroadcastReceiver
import java.time.LocalDate
import java.time.ZoneId

/**
 * 動態日期 App 圖示管理器（1–31 日）
 *
 * 透過 Android `<activity-alias>` 機制，在每日跨日（00:00）、開機、時區變更或開啟 App 時，
 * 依據當天幾號自動切換對應的 `MainActivityAliasDay01`..`MainActivityAliasDay31` 桌面圖示；
 * 若使用者關閉動態圖示，則切回預設靜態圖示 `MainActivityAliasDefault`。
 */
object DynamicAppIconManager {

    const val ACTION_UPDATE_DYNAMIC_ICON = "com.dx.calendar.ACTION_UPDATE_DYNAMIC_ICON"
    private const val MIDNIGHT_REQUEST_CODE = 773100

    private const val DEFAULT_ALIAS = "com.dx.calendar.MainActivityAliasDefault"

    fun dayAliasName(dayOfMonth: Int): String {
        val clamped = dayOfMonth.coerceIn(1, 31)
        return "com.dx.calendar.MainActivityAliasDay%02d".format(clamped)
    }

    /**
     * 同步目前桌面圖示至目標日期（或關閉動態模式時切回預設圖示）。
     * 若目標 alias 已啟用且其他 alias 均停用，直接略過以避免桌面閃爍。
     */
    fun syncIcon(
        context: Context,
        enabled: Boolean,
        date: LocalDate = LocalDate.now()
    ): Boolean {
        val pm = context.packageManager
        val pkg = context.packageName
        val targetAlias = if (enabled) dayAliasName(date.dayOfMonth) else DEFAULT_ALIAS

        val allAliases = buildList(32) {
            add(DEFAULT_ALIAS)
            for (d in 1..31) add(dayAliasName(d))
        }

        // 先檢查是否已經完全吻合，避免重複觸發 Launcher 重繪
        val alreadySynced = allAliases.all { alias ->
            val cn = ComponentName(pkg, alias)
            val state = pm.getComponentEnabledSetting(cn)
            if (alias == targetAlias) {
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (alias == DEFAULT_ALIAS && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            } else {
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    (alias != DEFAULT_ALIAS && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            }
        }
        if (alreadySynced) return false

        // 關鍵順序：先啟用目標 alias，再停用其他 alias，確保系統永遠保有有效 Launcher 入口
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, targetAlias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            allAliases.forEach { alias ->
                if (alias != targetAlias) {
                    val cn = ComponentName(pkg, alias)
                    val cur = pm.getComponentEnabledSetting(cn)
                    if (cur != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        pm.setComponentEnabledSetting(
                            cn,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
        }
        return true
    }

    /**
     * 排程下一次午夜（明日 00:00:05）自動刷新動態日期圖示與小工具
     */
    fun scheduleMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tomorrowStartMillis = LocalDate.now()
            .plusDays(1)
            .atTime(0, 0, 5)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_UPDATE_DYNAMIC_ICON
        }
        val pi = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrowStartMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrowStartMillis, pi)
            }
        }.onFailure {
            alarmManager.set(AlarmManager.RTC_WAKEUP, tomorrowStartMillis, pi)
        }
    }
}
