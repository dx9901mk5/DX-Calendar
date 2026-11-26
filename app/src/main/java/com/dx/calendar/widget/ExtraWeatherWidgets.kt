package com.dx.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.dx.calendar.MainActivity
import com.dx.calendar.R
import com.dx.calendar.data.local.EventRichMetaStore
import com.dx.calendar.data.repository.CalendarRepository
import com.dx.calendar.data.weather.WeatherForecastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val WIDGET_HM_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 小工具 3（v2.0 新增）：DX 下一行程與天氣橫條（4×1，支援透明度與點擊直達事件）
 */
class NextEventWeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val appCtx = context.applicationContext ?: context
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val repo = CalendarRepository(appCtx)
                    val settings = repo.prefs.getSettingsOnce()
                    val zone = settings.effectiveZoneId
                    val today = LocalDate.now(zone)
                    val nowMs = System.currentTimeMillis()
                    val items = repo.buildItemsForRange(
                        rangeStart = today,
                        rangeEnd = today.plusDays(14),
                        categories = repo.dao.getCategoriesOnce(),
                        localEvents = repo.dao.getAllEventsOnce(),
                        tasks = repo.dao.getAllTasksOnce(),
                        checkIns = repo.dao.getRoutineCheckInsOnce(),
                        overrides = emptyList(),
                        settings = settings,
                        isPrivacyUnlocked = false
                    ).filter { !it.isPrivacy && it.showInAgendaAndWidget && !it.isCompleted }
                    val weather = WeatherForecastManager.loadCachedForecast(appCtx, zone)[today]

                    val nextItem = items.firstOrNull { !it.isAllDay && it.endMillis > nowMs }
                        ?: items.firstOrNull { !it.date.isBefore(today) }

                    ids.forEach { widgetId ->
                        val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                            context = appCtx,
                            appWidgetId = widgetId,
                            globalDark = settings.widgetDarkStyle,
                            globalOpacity = settings.widgetOpacity,
                            followSystemDark = settings.widgetFollowSystemDark
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_next_event_weather)
                        val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
                        views.setImageViewResource(
                            R.id.widget_bg_layer,
                            if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
                        )
                        views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

                        val primaryColor = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF101014.toInt()
                        val subColor = if (darkStyle) 0xFFE6E6F0.toInt() else 0xFF282834.toInt()
                        val accent = settings.brandStyle.primaryHex.toInt() or 0xFF000000.toInt()
                        views.setTextColor(R.id.widget_next_title, primaryColor)
                        views.setTextColor(R.id.widget_next_time, subColor)
                        views.setTextColor(R.id.widget_next_weather_temp, primaryColor)
                        views.setTextColor(R.id.widget_next_label, accent)

                        views.setTextViewText(R.id.widget_next_weather_icon, weather?.icon ?: "🌤️")
                        views.setTextViewText(
                            R.id.widget_next_weather_temp,
                            weather?.let { "${it.tempMinC}°~${it.tempMaxC}°" } ?: "今日天氣"
                        )

                        if (nextItem != null) {
                            val diffDays = ChronoUnit.DAYS.between(today, nextItem.date).toInt()
                            val dayTag = when (diffDays) {
                                0 -> "今天"
                                1 -> "明天"
                                else -> "${nextItem.date.monthValue}/${nextItem.date.dayOfMonth}"
                            }
                            val timeDesc = if (nextItem.isAllDay) {
                                "$dayTag · 全天"
                            } else {
                                val startStr = Instant.ofEpochMilli(nextItem.startMillis).atZone(zone).toLocalTime().format(WIDGET_HM_FMT)
                                val minsUntil = ((nextItem.startMillis - nowMs) / 60_000L).toInt()
                                val countdown = when {
                                    minsUntil in 1..180 -> " · 還有 ${minsUntil} 分鐘"
                                    minsUntil <= 0 && diffDays == 0 -> " · 進行中"
                                    else -> ""
                                }
                                "$dayTag $startStr$countdown"
                            }
                            views.setTextViewText(R.id.widget_next_label, "接下來行程")
                            views.setTextViewText(R.id.widget_next_title, nextItem.title)
                            views.setTextViewText(R.id.widget_next_time, timeDesc)

                            val intent = Intent(appCtx, MainActivity::class.java).apply {
                                action = "${WidgetRefreshHelper.ACTION_CLICK_ITEM}.next.${nextItem.rawId}"
                                setPackage(appCtx.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, nextItem.date.toEpochDay())
                                putExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, nextItem.rawId)
                                putExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, nextItem.isTask)
                            }
                            val pi = android.app.PendingIntent.getActivity(
                                appCtx,
                                widgetId * 100 + 71,
                                intent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_next_container, pi)
                        } else {
                            views.setTextViewText(R.id.widget_next_label, "接下來行程")
                            views.setTextViewText(R.id.widget_next_title, "近期無待辦行程")
                            views.setTextViewText(R.id.widget_next_time, weather?.let { "${it.label} · 點擊開啟 DX日曆" } ?: "點擊開啟 DX日曆")
                            val intent = Intent(appCtx, MainActivity::class.java).apply {
                                action = "com.dx.calendar.widget.OPEN_MAIN_NEXT"
                                setPackage(appCtx.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            val pi = android.app.PendingIntent.getActivity(
                                appCtx,
                                widgetId * 100 + 72,
                                intent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_next_container, pi)
                        }
                        runCatching { mgr.updateAppWidget(widgetId, views) }
                    }
                }
            }
        }
    }
}

/**
 * 小工具 4（v2.0 新增）：DX 倒數日與天氣小工具（2×2，顯示最近倒數日／紀念日／生日＋今日天氣）
 */
class CountdownWeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val appCtx = context.applicationContext ?: context
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val repo = CalendarRepository(appCtx)
                    val settings = repo.prefs.getSettingsOnce()
                    val zone = settings.effectiveZoneId
                    val today = LocalDate.now(zone)
                    val metaMap = EventRichMetaStore.ensureLoaded(appCtx)
                    val weather = WeatherForecastManager.loadCachedForecast(appCtx, zone)[today]

                    val upcoming = repo.buildItemsForRange(
                        rangeStart = today,
                        rangeEnd = today.plusDays(180),
                        categories = repo.dao.getCategoriesOnce(),
                        localEvents = repo.dao.getAllEventsOnce(),
                        tasks = repo.dao.getAllTasksOnce(),
                        checkIns = repo.dao.getRoutineCheckInsOnce(),
                        overrides = emptyList(),
                        settings = settings,
                        isPrivacyUnlocked = false
                    ).filter { !it.isPrivacy && !it.isCompleted && !it.date.isBefore(today) }

                    // 優先挑選已標記倒數日／生日的活動，否則退回最近一筆未來活動
                    val pinned = upcoming.firstOrNull { item ->
                        val meta = metaMap[EventRichMetaStore.makeKey(item.sourceType.name, item.rawId)]
                        meta?.isCountdown == true || meta?.isBirthday == true
                    } ?: upcoming.firstOrNull()

                    ids.forEach { widgetId ->
                        val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                            context = appCtx,
                            appWidgetId = widgetId,
                            globalDark = settings.widgetDarkStyle,
                            globalOpacity = settings.widgetOpacity,
                            followSystemDark = settings.widgetFollowSystemDark
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_countdown_weather)
                        val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
                        views.setImageViewResource(
                            R.id.widget_bg_layer,
                            if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
                        )
                        views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

                        val primaryColor = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF101014.toInt()
                        val subColor = if (darkStyle) 0xFFE6E6F0.toInt() else 0xFF282834.toInt()
                        val accent = settings.brandStyle.primaryHex.toInt() or 0xFF000000.toInt()
                        views.setTextColor(R.id.widget_cd_title, primaryColor)
                        views.setTextColor(R.id.widget_cd_weather, subColor)
                        views.setTextColor(R.id.widget_cd_date, subColor)
                        views.setTextColor(R.id.widget_cd_days, accent)

                        views.setTextViewText(
                            R.id.widget_cd_weather,
                            weather?.let { "${it.icon} ${it.tempMinC}°~${it.tempMaxC}°" } ?: "🌤️ 今日"
                        )

                        if (pinned != null) {
                            val diff = ChronoUnit.DAYS.between(today, pinned.date).toInt().coerceAtLeast(0)
                            val meta = metaMap[EventRichMetaStore.makeKey(pinned.sourceType.name, pinned.rawId)]
                            val ageSuffix = meta?.anniversaryStartYear?.let { startY ->
                                val y = pinned.date.year - startY
                                if (y > 0) (if (meta.isBirthday) " (${y}歲)" else " (第${y}年)") else ""
                            }.orEmpty()
                            views.setTextViewText(R.id.widget_cd_title, pinned.title + ageSuffix)
                            views.setTextViewText(R.id.widget_cd_days, if (diff == 0) "今天" else "${diff} 天")
                            views.setTextViewText(R.id.widget_cd_date, "${pinned.date.year}/${pinned.date.monthValue}/${pinned.date.dayOfMonth}")

                            val intent = Intent(appCtx, MainActivity::class.java).apply {
                                action = "${WidgetRefreshHelper.ACTION_CLICK_ITEM}.cd.${pinned.rawId}"
                                setPackage(appCtx.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, pinned.date.toEpochDay())
                                putExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, pinned.rawId)
                                putExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, pinned.isTask)
                            }
                            val pi = android.app.PendingIntent.getActivity(
                                appCtx,
                                widgetId * 100 + 81,
                                intent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_cd_container, pi)
                        } else {
                            views.setTextViewText(R.id.widget_cd_title, "尚未設定倒數日")
                            views.setTextViewText(R.id.widget_cd_days, "--")
                            views.setTextViewText(R.id.widget_cd_date, "點擊新增重要紀念日")
                            val intent = Intent(appCtx, MainActivity::class.java).apply {
                                action = "com.dx.calendar.widget.OPEN_MAIN_CD"
                                setPackage(appCtx.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            val pi = android.app.PendingIntent.getActivity(
                                appCtx,
                                widgetId * 100 + 82,
                                intent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_cd_container, pi)
                        }
                        runCatching { mgr.updateAppWidget(widgetId, views) }
                    }
                }
            }
        }
    }
}

/**
 * 小工具 5（v2.0.1 補齊）：DX 單週 7 天天氣與行程小工具（4×2，含未來 7 天天氣、農曆、行程、常駐 now 刷新與 ⚙ 透明度設定）
 */
class WeekWeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetRefreshHelper.ACTION_RESET_TODAY) {
            val appCtx = context.applicationContext ?: context
            val mgr = AppWidgetManager.getInstance(appCtx)
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateWidgets(appCtx, mgr, intArrayOf(id))
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private val COL_IDS = intArrayOf(
            R.id.week_col_0, R.id.week_col_1, R.id.week_col_2,
            R.id.week_col_3, R.id.week_col_4, R.id.week_col_5, R.id.week_col_6
        )
        private val DOW_IDS = intArrayOf(
            R.id.week_dow_0, R.id.week_dow_1, R.id.week_dow_2,
            R.id.week_dow_3, R.id.week_dow_4, R.id.week_dow_5, R.id.week_dow_6
        )
        private val NUM_IDS = intArrayOf(
            R.id.week_num_0, R.id.week_num_1, R.id.week_num_2,
            R.id.week_num_3, R.id.week_num_4, R.id.week_num_5, R.id.week_num_6
        )
        private val WEATHER_IDS = intArrayOf(
            R.id.week_w_0, R.id.week_w_1, R.id.week_w_2,
            R.id.week_w_3, R.id.week_w_4, R.id.week_w_5, R.id.week_w_6
        )
        private val EV_IDS = intArrayOf(
            R.id.week_ev_0, R.id.week_ev_1, R.id.week_ev_2,
            R.id.week_ev_3, R.id.week_ev_4, R.id.week_ev_5, R.id.week_ev_6
        )

        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val appCtx = context.applicationContext ?: context
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val repo = CalendarRepository(appCtx)
                    val settings = repo.prefs.getSettingsOnce()
                    val zone = settings.effectiveZoneId
                    val today = LocalDate.now(zone)
                    val items = repo.buildItemsForRange(
                        rangeStart = today,
                        rangeEnd = today.plusDays(6),
                        categories = repo.dao.getCategoriesOnce(),
                        localEvents = repo.dao.getAllEventsOnce(),
                        tasks = repo.dao.getAllTasksOnce(),
                        checkIns = repo.dao.getRoutineCheckInsOnce(),
                        overrides = emptyList(),
                        settings = settings,
                        isPrivacyUnlocked = false
                    ).filter { !it.isPrivacy && it.showInAgendaAndWidget && !it.isCompleted }
                    val byDate = items.groupBy { it.date }
                    val weatherMap = WeatherForecastManager.loadCachedForecast(appCtx, zone)
                    val dowLabels = arrayOf("一", "二", "三", "四", "五", "六", "日")

                    ids.forEach { widgetId ->
                        val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                            context = appCtx,
                            appWidgetId = widgetId,
                            globalDark = settings.widgetDarkStyle,
                            globalOpacity = settings.widgetOpacity,
                            followSystemDark = settings.widgetFollowSystemDark
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_week_weather)
                        val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
                        views.setImageViewResource(
                            R.id.widget_bg_layer,
                            if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
                        )
                        views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

                        val primaryColor = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF101014.toInt()
                        val subColor = if (darkStyle) 0xFFE6E6F0.toInt() else 0xFF282834.toInt()
                        val accent = settings.brandStyle.primaryHex.toInt() or 0xFF000000.toInt()
                        views.setTextColor(R.id.widget_week_title, primaryColor)
                        views.setTextViewText(R.id.widget_week_title, "${today.year}/${today.monthValue} · 7 天天氣行程")
                        views.setTextColor(R.id.btn_widget_week_now, accent)
                        views.setInt(
                            R.id.btn_widget_week_now,
                            "setBackgroundResource",
                            if (darkStyle) R.drawable.bg_widget_now_pill_dark else R.drawable.bg_widget_now_pill_light
                        )
                        views.setTextColor(R.id.btn_widget_week_config, subColor)

                        val nowIntent = Intent(appCtx, WeekWeatherWidgetProvider::class.java).apply {
                            action = WidgetRefreshHelper.ACTION_RESET_TODAY
                            setPackage(appCtx.packageName)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        }
                        views.setOnClickPendingIntent(
                            R.id.btn_widget_week_now,
                            android.app.PendingIntent.getBroadcast(
                                appCtx,
                                widgetId * 100 + 91,
                                nowIntent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                        val cfgIntent = Intent(appCtx, WidgetConfigActivity::class.java).apply {
                            action = "com.dx.calendar.widget.CONFIG_$widgetId"
                            setPackage(appCtx.packageName)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        views.setOnClickPendingIntent(
                            R.id.btn_widget_week_config,
                            android.app.PendingIntent.getActivity(
                                appCtx,
                                widgetId * 100 + 92,
                                cfgIntent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                        )

                        for (i in 0..6) {
                            val d = today.plusDays(i.toLong())
                            val dayEvs = byDate[d].orEmpty()
                            val w = weatherMap[d]
                            val dowStr = if (i == 0) "今" else dowLabels[d.dayOfWeek.value - 1]
                            views.setTextViewText(DOW_IDS[i], dowStr)
                            views.setTextColor(DOW_IDS[i], if (i == 0) accent else subColor)
                            views.setTextViewText(NUM_IDS[i], d.dayOfMonth.toString())
                            views.setTextColor(NUM_IDS[i], if (i == 0) accent else primaryColor)
                            views.setTextViewText(WEATHER_IDS[i], w?.icon ?: "·")

                            val summary = if (dayEvs.isEmpty()) {
                                w?.let { "${it.tempMinC}°/${it.tempMaxC}°" } ?: "—"
                            } else {
                                dayEvs.take(3).joinToString("\n") { ev ->
                                    (if (ev.isTask) "✓" else "•") + ev.title
                                }
                            }
                            views.setTextViewText(EV_IDS[i], summary)
                            views.setTextColor(EV_IDS[i], subColor)

                            val openDayIntent = Intent(appCtx, MainActivity::class.java).apply {
                                action = "${WidgetRefreshHelper.ACTION_CLICK_ITEM}.week.${d.toEpochDay()}"
                                setPackage(appCtx.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, d.toEpochDay())
                                putExtra(WidgetRefreshHelper.EXTRA_OPEN_DAY_VIEW, true)
                            }
                            views.setOnClickPendingIntent(
                                COL_IDS[i],
                                android.app.PendingIntent.getActivity(
                                    appCtx,
                                    widgetId * 100 + 93 + i,
                                    openDayIntent,
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                        runCatching { mgr.updateAppWidget(widgetId, views) }
                    }
                }
            }
        }
    }
}
