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
 * 透過 Android `<activity-alias>` 機制，在每日跨日（00:00）、開機、時區變更或退回背景時，
 * 依據當天幾號自動切換對應的 `MainActivityAliasDay01`..`MainActivityAliasDay31` 桌面圖示；
 * 若使用者關閉動態圖示，則切回預設靜態圖示 `MainActivityAliasDefault`。
 *
 * 重要防閃退保護：
 * 1. `@Synchronized` 防止多執行緒同時變更組件狀態。
 * 2. `Day01`..`Day31` 於 Manifest 預設即為 `enabled="false"`（`COMPONENT_ENABLED_STATE_DEFAULT`），
 *    僅針對實際處於啟用狀態的舊別名執行停用，避免一次發送 31 次 IPC 廣播造成摺疊機桌面與啟動任務中斷閃退。
 */
object DynamicAppIconManager {

    const val ACTION_UPDATE_DYNAMIC_ICON = "com.dx.calendar.ACTION_UPDATE_DYNAMIC_ICON"
    private const val MIDNIGHT_REQUEST_CODE = 773100

    const val DEFAULT_ALIAS = "com.dx.calendar.MainActivityAliasDefault"

    fun dayAliasName(dayOfMonth: Int): String {
        val clamped = dayOfMonth.coerceIn(1, 31)
        return "com.dx.calendar.MainActivityAliasDay%02d".format(clamped)
    }

    /**
     * 星期幾＋日期動態圖示別名（dayOfWeek: 1=星期一 .. 7=星期日；dayOfMonth: 1..31）
     */
    fun weekdayDayAliasName(dayOfWeek: Int, dayOfMonth: Int): String {
        val w = dayOfWeek.coerceIn(1, 7)
        val d = dayOfMonth.coerceIn(1, 31)
        return "com.dx.calendar.MainActivityAliasWd%dDay%02d".format(w, d)
    }

    internal fun isAliasEffectivelyEnabled(alias: String, componentState: Int): Boolean {
        return if (alias == DEFAULT_ALIAS) {
            componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                componentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    /**
     * 同步目前桌面圖示至目標日期（支援純日期 1–31 與「星期幾＋日期」雙模式；關閉時切回預設圖示）。
     * 若目標 alias 已啟用且其他 alias 均未啟用，直接略過（0 次寫入 IPC）。
     */
    @Synchronized
    fun syncIcon(
        context: Context,
        enabled: Boolean,
        showWeekday: Boolean = false,
        date: LocalDate = LocalDate.now()
    ): Boolean {
        return runCatching {
            val appCtx = context.applicationContext ?: context
            val pm = appCtx.packageManager
            val pkg = appCtx.packageName
            val targetAlias = when {
                !enabled -> DEFAULT_ALIAS
                showWeekday -> weekdayDayAliasName(date.dayOfWeek.value, date.dayOfMonth)
                else -> dayAliasName(date.dayOfMonth)
            }

            val allAliases = buildList(249) {
                add(DEFAULT_ALIAS)
                for (d in 1..31) add(dayAliasName(d))
                for (w in 1..7) {
                    for (d in 1..31) add(weekdayDayAliasName(w, d))
                }
            }

            val states = HashMap<String, Int>(256)
            for (alias in allAliases) {
                states[alias] = pm.getComponentEnabledSetting(ComponentName(pkg, alias))
            }

            val alreadySynced = allAliases.all { alias ->
                val active = isAliasEffectivelyEnabled(alias, states[alias] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                if (alias == targetAlias) active else !active
            }
            if (alreadySynced) return@runCatching false

            // 關鍵順序：先啟用目標 alias，再僅停用先前實際處於啟用狀態的舊 alias（最多僅 2 次 IPC）
            val targetState = states[targetAlias] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (!isAliasEffectivelyEnabled(targetAlias, targetState)) {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, targetAlias),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            for (alias in allAliases) {
                if (alias != targetAlias) {
                    val cur = states[alias] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    if (isAliasEffectivelyEnabled(alias, cur)) {
                        pm.setComponentEnabledSetting(
                            ComponentName(pkg, alias),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 排程下一次午夜（明日 00:00:05）自動刷新動態日期圖示與小工具
     */
    fun scheduleMidnightRefresh(context: Context) {
        runCatching {
            val appCtx = context.applicationContext ?: context
            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val tomorrowStartMillis = LocalDate.now()
                .plusDays(1)
                .atTime(0, 0, 5)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val intent = Intent(appCtx, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_UPDATE_DYNAMIC_ICON
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
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
}
