package com.dx.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dx.calendar.MainActivity
import com.dx.calendar.R
import com.dx.calendar.data.local.AppDatabase
import com.dx.calendar.data.local.AppPreferences
import com.dx.calendar.data.repository.CalendarRepository
import com.dx.calendar.data.repository.UnifiedCalendarItem
import com.dx.calendar.util.LunarCalendarEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WidgetRefreshHelper {
    const val ACTION_PREV_MONTH = "com.dx.calendar.widget.ACTION_PREV_MONTH"
    const val ACTION_NEXT_MONTH = "com.dx.calendar.widget.ACTION_NEXT_MONTH"
    const val ACTION_RESET_TODAY = "com.dx.calendar.widget.ACTION_RESET_TODAY"
    const val ACTION_TOGGLE_WEEK_MODE = "com.dx.calendar.widget.ACTION_TOGGLE_WEEK_MODE"
    const val ACTION_TOGGLE_TASK = "com.dx.calendar.widget.ACTION_TOGGLE_TASK"
    const val ACTION_CLICK_ITEM = "com.dx.calendar.widget.ACTION_CLICK_ITEM"
    const val EXTRA_TASK_ID = "extra_widget_task_id"
    const val EXTRA_QUICK_ADD = "extra_open_quick_add"
    const val EXTRA_OPEN_EPOCH_DAY = "extra_widget_open_epoch_day"
    const val EXTRA_CLICK_IS_TASK = "extra_widget_click_is_task"
    const val EXTRA_WIDGET_MODE = "extra_widget_list_mode"

    private const val PREF_WIDGET_STATE = "dx_widget_month_offsets"

    fun getMonthOffset(context: Context, appWidgetId: Int): Int {
        return context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .getInt("offset_$appWidgetId", 0)
    }

    fun setMonthOffset(context: Context, appWidgetId: Int, offset: Int) {
        context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .edit().putInt("offset_$appWidgetId", offset).apply()
    }

    fun isWeekMode(context: Context, appWidgetId: Int): Boolean {
        return context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .getBoolean("week_mode_$appWidgetId", false)
    }

    fun setWeekMode(context: Context, appWidgetId: Int, weekMode: Boolean) {
        context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .edit().putBoolean("week_mode_$appWidgetId", weekMode).apply()
    }

    fun getWeekOffset(context: Context, appWidgetId: Int): Int {
        return context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .getInt("week_offset_$appWidgetId", 0)
    }

    fun setWeekOffset(context: Context, appWidgetId: Int, offset: Int) {
        context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .edit().putInt("week_offset_$appWidgetId", offset).apply()
    }

    fun refreshAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appContext)

        // 1. 刷新 vivo 月曆小工具
        val vivoMonthIds = mgr.getAppWidgetIds(ComponentName(appContext, VivoMonthWidgetProvider::class.java))
        if (vivoMonthIds.isNotEmpty()) {
            VivoMonthWidgetProvider.updateWidgets(appContext, mgr, vivoMonthIds)
        }

        // 2. 刷新 Google 月檢視小工具
        val googleMonthIds = mgr.getAppWidgetIds(ComponentName(appContext, GoogleMonthWidgetProvider::class.java))
        if (googleMonthIds.isNotEmpty()) {
            GoogleMonthWidgetProvider.updateWidgets(appContext, mgr, googleMonthIds)
        }

        // 3. 刷新 Google 日曆時間表可捲動小工具
        val googleSchedIds = mgr.getAppWidgetIds(ComponentName(appContext, GoogleScheduleWidgetProvider::class.java))
        if (googleSchedIds.isNotEmpty()) {
            GoogleScheduleWidgetProvider.updateWidgets(appContext, mgr, googleSchedIds)
            mgr.notifyAppWidgetViewDataChanged(googleSchedIds, R.id.widget_list_view)
        }

        // 4. 刷新 vivo 待辦與例行事項可捲動小工具
        val vivoTaskIds = mgr.getAppWidgetIds(ComponentName(appContext, VivoTasksWidgetProvider::class.java))
        if (vivoTaskIds.isNotEmpty()) {
            VivoTasksWidgetProvider.updateWidgets(appContext, mgr, vivoTaskIds)
            mgr.notifyAppWidgetViewDataChanged(vivoTaskIds, R.id.widget_list_view)
        }
    }
}

/**
 * 1. vivo 風格基底「月曆小工具」
 * 支援桌面自由縮放、直接在桌面上點擊 `<` `>` 切換前後月份、點「今」回當月、點 `+` 快速新增、顯示農曆與彩色日程膠囊！
 */
class VivoMonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val inWeek = WidgetRefreshHelper.isWeekMode(context, widgetId)
            when (intent.action) {
                WidgetRefreshHelper.ACTION_PREV_MONTH -> {
                    if (inWeek) {
                        WidgetRefreshHelper.setWeekOffset(context, widgetId, WidgetRefreshHelper.getWeekOffset(context, widgetId) - 1)
                    } else {
                        WidgetRefreshHelper.setMonthOffset(context, widgetId, WidgetRefreshHelper.getMonthOffset(context, widgetId) - 1)
                    }
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
                WidgetRefreshHelper.ACTION_NEXT_MONTH -> {
                    if (inWeek) {
                        WidgetRefreshHelper.setWeekOffset(context, widgetId, WidgetRefreshHelper.getWeekOffset(context, widgetId) + 1)
                    } else {
                        WidgetRefreshHelper.setMonthOffset(context, widgetId, WidgetRefreshHelper.getMonthOffset(context, widgetId) + 1)
                    }
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
                WidgetRefreshHelper.ACTION_RESET_TODAY -> {
                    WidgetRefreshHelper.setMonthOffset(context, widgetId, 0)
                    WidgetRefreshHelper.setWeekOffset(context, widgetId, 0)
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
                WidgetRefreshHelper.ACTION_TOGGLE_WEEK_MODE -> {
                    WidgetRefreshHelper.setWeekMode(context, widgetId, !inWeek)
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
            }
        }
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = CalendarRepository(context)
                val settings = repo.prefs.getSettingsOnce()
                val cats = repo.dao.getCategoriesOnce()
                val events = repo.dao.getAllEventsOnce()
                val tasks = repo.dao.getAllTasksOnce()
                val checkIns = repo.dao.getRoutineCheckInsOnce()

                for (id in ids) {
                    val weekMode = WidgetRefreshHelper.isWeekMode(context, id)
                    val offset = WidgetRefreshHelper.getMonthOffset(context, id)
                    val weekOffset = WidgetRefreshHelper.getWeekOffset(context, id)
                    val today = LocalDate.now()
                    val anchorDate = today.plusWeeks(weekOffset.toLong())
                    val ym = if (weekMode) YearMonth.from(anchorDate) else YearMonth.now().plusMonths(offset.toLong())
                    val gridStart = if (weekMode) anchorDate.minusDays(7) else ym.atDay(1).minusDays(7)
                    val gridEnd = if (weekMode) anchorDate.plusDays(14) else ym.atEndOfMonth().plusDays(14)

                    // 隱私日曆在桌面小工具永遠為 false 鎖定保護！
                    val items = repo.buildItemsForRange(
                        gridStart, gridEnd, cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
                    ).filter { it.showInMonthGrid && it.showInAgendaAndWidget }

                    val opts = mgr.getAppWidgetOptions(id)
                    val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(140)
                    val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250).coerceAtLeast(140)

                    val bmp = if (weekMode) {
                        MonthCanvasRenderer.renderWeekExpandedBitmap(
                            context = context,
                            anchorDate = anchorDate,
                            widthDp = minW,
                            heightDp = (minH - 42).coerceAtLeast(100),
                            items = items,
                            startMonday = settings.startWeekOnMonday,
                            showLunar = settings.showLunar,
                            darkStyle = settings.widgetDarkStyle,
                            accentArgb = settings.brandStyle.primaryHex.toInt()
                        )
                    } else {
                        MonthCanvasRenderer.renderMonthBitmap(
                            context = context,
                            yearMonth = ym,
                            widthDp = minW,
                            heightDp = (minH - 42).coerceAtLeast(100),
                            items = items,
                            startMonday = settings.startWeekOnMonday,
                            showLunar = settings.showLunar,
                            darkStyle = settings.widgetDarkStyle,
                            accentArgb = settings.brandStyle.primaryHex.toInt(),
                            googleGridStyle = false
                        )
                    }

                    val views = RemoteViews(context.packageName, R.layout.widget_month_canvas)
                    views.setInt(
                        R.id.widget_root,
                        "setBackgroundResource",
                        if (settings.widgetDarkStyle) R.drawable.bg_widget_dark else R.drawable.bg_widget_light
                    )
                    views.setTextViewText(
                        R.id.widget_month_title,
                        if (weekMode) "%d/%02d · 週".format(ym.year, ym.monthValue)
                        else "%d/%02d".format(ym.year, ym.monthValue)
                    )
                    views.setTextColor(
                        R.id.widget_month_title,
                        if (settings.widgetDarkStyle) 0xFFF5F5F7.toInt() else 0xFF161618.toInt()
                    )
                    views.setViewVisibility(R.id.btn_widget_mode, android.view.View.VISIBLE)
                    views.setTextViewText(R.id.btn_widget_mode, if (weekMode) "月曆" else "單週")
                    views.setTextViewText(R.id.btn_widget_today, "now")
                    views.setImageViewBitmap(R.id.widget_canvas_img, bmp)

                    // 綁定桌面直接翻月/翻週、展開單週、now 回到當週當日與快速新增按鈕
                    views.setOnClickPendingIntent(
                        R.id.btn_widget_prev,
                        buildBroadcastPi(context, VivoMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_PREV_MONTH, id, 1)
                    )
                    views.setOnClickPendingIntent(
                        R.id.btn_widget_next,
                        buildBroadcastPi(context, VivoMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_NEXT_MONTH, id, 2)
                    )
                    views.setOnClickPendingIntent(
                        R.id.btn_widget_today,
                        buildBroadcastPi(context, VivoMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_RESET_TODAY, id, 3)
                    )
                    val toggleWeekPi = buildBroadcastPi(
                        context,
                        VivoMonthWidgetProvider::class.java,
                        WidgetRefreshHelper.ACTION_TOGGLE_WEEK_MODE,
                        id,
                        7
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_mode, toggleWeekPi)
                    // 桌面點擊月曆區域可直接展開/收合單週，點擊標題列開啟主程式
                    views.setOnClickPendingIntent(R.id.widget_canvas_img, toggleWeekPi)
                    views.setOnClickPendingIntent(R.id.btn_widget_add, buildMainAppPi(context, id * 10 + 4, quickAdd = true))
                    views.setOnClickPendingIntent(R.id.widget_month_title, buildMainAppPi(context, id * 10 + 6, quickAdd = false))

                    mgr.updateAppWidget(id, views)
                }
            }
        }
    }
}

/**
 * 2. Google 日曆風格「月檢視」小工具（滿版網格 + Tasks 圓圈 + 自由縮放）
 */
class GoogleMonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            when (intent.action) {
                WidgetRefreshHelper.ACTION_PREV_MONTH -> {
                    WidgetRefreshHelper.setMonthOffset(context, widgetId, WidgetRefreshHelper.getMonthOffset(context, widgetId) - 1)
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
                WidgetRefreshHelper.ACTION_NEXT_MONTH -> {
                    WidgetRefreshHelper.setMonthOffset(context, widgetId, WidgetRefreshHelper.getMonthOffset(context, widgetId) + 1)
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
                WidgetRefreshHelper.ACTION_RESET_TODAY -> {
                    WidgetRefreshHelper.setMonthOffset(context, widgetId, 0)
                    updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
                }
            }
        }
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = CalendarRepository(context)
                val settings = repo.prefs.getSettingsOnce()
                val cats = repo.dao.getCategoriesOnce()
                val events = repo.dao.getAllEventsOnce()
                val tasks = repo.dao.getAllTasksOnce()
                val checkIns = repo.dao.getRoutineCheckInsOnce()

                for (id in ids) {
                    val offset = WidgetRefreshHelper.getMonthOffset(context, id)
                    val ym = YearMonth.now().plusMonths(offset.toLong())
                    val items = repo.buildItemsForRange(
                        ym.atDay(1).minusDays(7), ym.atEndOfMonth().plusDays(14),
                        cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
                    ).filter { it.showInMonthGrid && it.showInAgendaAndWidget }

                    val opts = mgr.getAppWidgetOptions(id)
                    val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260).coerceAtLeast(140)
                    val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260).coerceAtLeast(140)

                    val bmp = MonthCanvasRenderer.renderMonthBitmap(
                        context = context,
                        yearMonth = ym,
                        widthDp = minW,
                        heightDp = (minH - 42).coerceAtLeast(100),
                        items = items,
                        startMonday = settings.startWeekOnMonday,
                        showLunar = settings.showLunar,
                        darkStyle = settings.widgetDarkStyle,
                        accentArgb = 0xFF8AB4F8.toInt(),
                        googleGridStyle = true
                    )

                    val views = RemoteViews(context.packageName, R.layout.widget_month_canvas)
                    views.setInt(
                        R.id.widget_root,
                        "setBackgroundResource",
                        if (settings.widgetDarkStyle) R.drawable.bg_widget_dark else R.drawable.bg_widget_light
                    )
                    views.setTextViewText(R.id.widget_month_title, "%d年%d月".format(ym.year, ym.monthValue))
                    views.setTextColor(
                        R.id.widget_month_title,
                        if (settings.widgetDarkStyle) 0xFFE3E3E3.toInt() else 0xFF1F1F1F.toInt()
                    )
                    views.setViewVisibility(R.id.btn_widget_mode, android.view.View.GONE)
                    views.setTextViewText(R.id.btn_widget_today, "now")
                    views.setImageViewBitmap(R.id.widget_canvas_img, bmp)

                    views.setOnClickPendingIntent(
                        R.id.btn_widget_prev,
                        buildBroadcastPi(context, GoogleMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_PREV_MONTH, id, 11)
                    )
                    views.setOnClickPendingIntent(
                        R.id.btn_widget_next,
                        buildBroadcastPi(context, GoogleMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_NEXT_MONTH, id, 12)
                    )
                    views.setOnClickPendingIntent(
                        R.id.btn_widget_today,
                        buildBroadcastPi(context, GoogleMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_RESET_TODAY, id, 13)
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_add, buildMainAppPi(context, id * 10 + 14, quickAdd = true))
                    views.setOnClickPendingIntent(R.id.widget_canvas_img, buildMainAppPi(context, id * 10 + 15, quickAdd = false))

                    mgr.updateAppWidget(id, views)
                }
            }
        }
    }
}

/**
 * 3. Google 日曆風格「日曆時間表」可上下捲動小工具（完美還原示範影片 01:38 右上角可捲動彩色膠囊 + Google Tasks 小工具）
 */
class GoogleScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefreshHelper.ACTION_TOGGLE_TASK ||
            intent.action == WidgetRefreshHelper.ACTION_CLICK_ITEM
        ) {
            val mode = intent.getStringExtra(WidgetRefreshHelper.EXTRA_WIDGET_MODE) ?: "SCHEDULE"
            val isTask = intent.getBooleanExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, false)
            val taskId = intent.getLongExtra(WidgetRefreshHelper.EXTRA_TASK_ID, -1L)
            val epochDay = intent.getLongExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, Long.MIN_VALUE)

            if (mode == "TASKS" && isTask && taskId > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = AppDatabase.getInstance(context).calendarDao()
                    val task = dao.getTaskById(taskId)
                    if (task != null) {
                        dao.upsertTask(task.copy(isCompleted = !task.isCompleted))
                        WidgetRefreshHelper.refreshAllWidgets(context)
                    }
                }
            } else {
                // 時間表點擊任意活動或待辦，立即開啟 DX日曆 並定位至該日期詳情！
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    if (epochDay != Long.MIN_VALUE) {
                        putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, epochDay)
                    }
                }
                runCatching { context.startActivity(openIntent) }
            }
        }
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val today = LocalDate.now()
            val dowNames = arrayOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
            val header = "${today.dayOfMonth} ${dowNames[today.dayOfWeek.value - 1]}"

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_scrollable_list)
                views.setTextViewText(R.id.widget_list_header_title, header)
                views.setTextViewText(R.id.widget_list_badge, "時間表")

                val svcIntent = Intent(context, ScheduleRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra("mode", "SCHEDULE")
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_list_view, svcIntent)
                views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

                views.setOnClickPendingIntent(R.id.btn_widget_list_add, buildMainAppPi(context, id * 10 + 21, quickAdd = true))
                views.setOnClickPendingIntent(R.id.widget_list_header_title, buildMainAppPi(context, id * 10 + 22, quickAdd = false))

                val clickTemplate = Intent(context, GoogleScheduleWidgetProvider::class.java).apply {
                    action = WidgetRefreshHelper.ACTION_TOGGLE_TASK
                }
                val templatePi = PendingIntent.getBroadcast(
                    context,
                    id * 10 + 23,
                    clickTemplate,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_list_view, templatePi)

                mgr.updateAppWidget(id, views)
            }
        }
    }
}

/**
 * 4. vivo 風格「待辦事項與例行中心」可捲動小工具（還原影片 02:26 待辦數徽章 + 桌面直接打勾完成）
 */
class VivoTasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getInstance(context).calendarDao()
                val pendingCount = dao.getAllTasksOnce().count { !it.isCompleted && !it.isPrivacy }

                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_scrollable_list)
                    views.setTextViewText(R.id.widget_list_header_title, "待辦與例行")
                    views.setTextViewText(R.id.widget_list_badge, "$pendingCount 項待辦")

                    val svcIntent = Intent(context, ScheduleRemoteViewsService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra("mode", "TASKS")
                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.widget_list_view, svcIntent)
                    views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

                    views.setOnClickPendingIntent(R.id.btn_widget_list_add, buildMainAppPi(context, id * 10 + 31, quickAdd = true))
                    views.setOnClickPendingIntent(R.id.widget_list_header_title, buildMainAppPi(context, id * 10 + 32, quickAdd = false))

                    val clickTemplate = Intent(context, GoogleScheduleWidgetProvider::class.java).apply {
                        action = WidgetRefreshHelper.ACTION_TOGGLE_TASK
                    }
                    val templatePi = PendingIntent.getBroadcast(
                        context,
                        id * 10 + 33,
                        clickTemplate,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    views.setPendingIntentTemplate(R.id.widget_list_view, templatePi)

                    mgr.updateAppWidget(id, views)
                }
            }
        }
    }
}

class ScheduleRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val mode = intent.getStringExtra("mode") ?: "SCHEDULE"
        return ScheduleRemoteViewsFactory(applicationContext, mode)
    }
}

private class ScheduleRemoteViewsFactory(
    private val context: Context,
    private val mode: String
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<UnifiedCalendarItem> = emptyList()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking(Dispatchers.IO) {
            val repo = CalendarRepository(context)
            val settings = repo.prefs.getSettingsOnce()
            val cats = repo.dao.getCategoriesOnce()
            val events = repo.dao.getAllEventsOnce()
            val tasks = repo.dao.getAllTasksOnce()
            val checkIns = repo.dao.getRoutineCheckInsOnce()

            val start = LocalDate.now()
            val end = start.plusDays(45)
            val all = repo.buildItemsForRange(
                start, end, cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
            ).filter { it.showInAgendaAndWidget }

            rows = if (mode == "TASKS") {
                all.filter { it.isTask || it.isRoutine }.take(60)
            } else {
                all.take(80)
            }
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in rows.indices) return null
        val item = rows[position]
        val zone = ZoneId.systemDefault()
        val dowNames = arrayOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
        val showDateHeader = position == 0 || rows[position - 1].date != item.date

        val rv = RemoteViews(context.packageName, R.layout.widget_item_row)
        if (showDateHeader) {
            val lunar = LunarCalendarEngine.getLunarInfo(item.date)
            rv.setViewVisibility(R.id.row_date_header, android.view.View.VISIBLE)
            rv.setTextViewText(
                R.id.row_date_header,
                "${item.date.monthValue}月${item.date.dayOfMonth}日 ${dowNames[item.date.dayOfWeek.value - 1]} · ${lunar.shortDisplay}"
            )
        } else {
            rv.setViewVisibility(R.id.row_date_header, android.view.View.GONE)
        }

        val prefix = when {
            item.isTask && item.isCompleted -> "✓ "
            item.isTask -> "○ "
            else -> ""
        }
        rv.setTextViewText(R.id.row_item_title, prefix + item.title)

        val timeLabel = if (item.isAllDay) {
            "全天"
        } else {
            val s = Instant.ofEpochMilli(item.startMillis).atZone(zone).toLocalTime().format(timeFmt)
            val e = Instant.ofEpochMilli(item.endMillis).atZone(zone).toLocalTime().format(timeFmt)
            if (item.isTask) s else "$s - $e"
        }
        rv.setTextViewText(R.id.row_item_time, timeLabel)
        rv.setInt(R.id.row_color_bar, "setBackgroundColor", item.colorArgb)

        val fillIn = Intent().apply {
            putExtra(WidgetRefreshHelper.EXTRA_WIDGET_MODE, mode)
            putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, item.date.toEpochDay())
            putExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, item.isTask)
            if (item.isTask) {
                putExtra(WidgetRefreshHelper.EXTRA_TASK_ID, item.rawId)
            }
        }
        rv.setOnClickFillInIntent(R.id.row_card_container, fillIn)
        rv.setOnClickFillInIntent(R.id.row_date_header, fillIn)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

/**
 * 響應式高解析度月曆小工具畫布渲染器（隨桌面任意拉大縮小動態計算欄寬、列高與最多顯示行程數！）
 */
private object MonthCanvasRenderer {

    fun renderMonthBitmap(
        context: Context,
        yearMonth: YearMonth,
        widthDp: Int,
        heightDp: Int,
        items: List<UnifiedCalendarItem>,
        startMonday: Boolean,
        showLunar: Boolean,
        darkStyle: Boolean,
        accentArgb: Int,
        googleGridStyle: Boolean
    ): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtMost(2.5f)
        val wPx = (widthDp * density).toInt().coerceIn(240, 1800)
        val hPx = (heightDp * density).toInt().coerceIn(180, 1800)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val primaryText = if (darkStyle) 0xFFF2F2F7.toInt() else 0xFF18181B.toInt()
        val secondaryText = if (darkStyle) 0xFF9898A0.toInt() else 0xFF686870.toInt()
        val gridLineColor = if (darkStyle) 0x26FFFFFF else 0x1F000000

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryText
            textSize = 10.5f * density
            textAlign = Paint.Align.CENTER
        }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (if (hPx < 360) 11.5f else 13.5f) * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lunarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.2f * density
            textAlign = Paint.Align.CENTER
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryText
            textSize = 8.2f * density
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridLineColor
            strokeWidth = 1f
        }

        val weekLabels = if (startMonday) {
            arrayOf("一", "二", "三", "四", "五", "六", "日")
        } else {
            arrayOf("日", "一", "二", "三", "四", "五", "六")
        }

        val colW = wPx / 7f
        val headerH = 20f * density
        for (c in 0..6) {
            canvas.drawText(weekLabels[c], colW * c + colW / 2f, headerH - 5f * density, headerPaint)
        }

        val gridTop = headerH + 2f * density
        val rowH = (hPx - gridTop) / 6f
        val firstOfMonth = yearMonth.atDay(1)
        val shift = if (startMonday) firstOfMonth.dayOfWeek.value - 1 else firstOfMonth.dayOfWeek.value % 7
        val startDate = firstOfMonth.minusDays(shift.toLong())
        val today = LocalDate.now()
        val byDate = items.groupBy { it.date }

        val maxChips = when {
            rowH >= 74f * density -> 3
            rowH >= 54f * density -> 2
            rowH >= 38f * density -> 1
            else -> 0
        }

        for (r in 0..5) {
            val cellTop = gridTop + r * rowH
            if (googleGridStyle) {
                canvas.drawLine(0f, cellTop, wPx.toFloat(), cellTop, linePaint)
            }
            for (c in 0..6) {
                val cellLeft = c * colW
                if (googleGridStyle && r == 0 && c > 0) {
                    canvas.drawLine(cellLeft, gridTop, cellLeft, hPx.toFloat(), linePaint)
                }
                val d = startDate.plusDays((r * 7 + c).toLong())
                val inMonth = d.month == yearMonth.month
                val isToday = d == today
                val alphaFactor = if (inMonth) 255 else 95

                val cx = cellLeft + colW / 2f
                val dayBaseline = cellTop + 14.5f * density

                if (isToday) {
                    chipPaint.color = accentArgb
                    canvas.drawRoundRect(
                        RectF(cellLeft + 3f * density, cellTop + 2f * density, cellLeft + colW - 3f * density, cellTop + rowH - 2f * density),
                        8f * density,
                        8f * density,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accentArgb and 0x00FFFFFF) or 0x2E000000 }
                    )
                }

                dayPaint.color = if (isToday) accentArgb else primaryText
                dayPaint.alpha = alphaFactor
                canvas.drawText(d.dayOfMonth.toString(), cx, dayBaseline, dayPaint)

                var chipStartY = dayBaseline + 3f * density
                if (showLunar && rowH >= 30f * density) {
                    val lunar = LunarCalendarEngine.getLunarInfo(d)
                    lunarPaint.color = if (lunar.isTaiwanHoliday || lunar.solarTerm != null) accentArgb else secondaryText
                    lunarPaint.alpha = alphaFactor
                    val lunarBaseline = dayBaseline + 9.5f * density
                    canvas.drawText(lunar.shortDisplay.take(4), cx, lunarBaseline, lunarPaint)
                    chipStartY = lunarBaseline + 3f * density
                }

                val dayEvents = byDate[d] ?: emptyList()
                val chipH = 10.5f * density
                dayEvents.take(maxChips).forEachIndexed { idx, ev ->
                    val top = chipStartY + idx * (chipH + 1.5f * density)
                    if (top + chipH <= cellTop + rowH) {
                        chipPaint.color = (ev.colorArgb and 0x00FFFFFF) or 0x55000000
                        val rect = RectF(cellLeft + 2f * density, top, cellLeft + colW - 2f * density, top + chipH)
                        canvas.drawRoundRect(rect, 3f * density, 3f * density, chipPaint)
                        val label = (if (ev.isTask) "○" else "") + ev.title
                        val clipped = clipTextToWidth(label, chipTextPaint, colW - 6f * density)
                        chipTextPaint.alpha = alphaFactor
                        canvas.drawText(clipped, cellLeft + 3.5f * density, top + chipH - 2.2f * density, chipTextPaint)
                    }
                }
            }
        }
        return bmp
    }

    /**
     * 類 vivo 桌面小工具「展開單週模式」畫布渲染器：
     * 上半部呈現當週 7 天日期＋農曆列（標記今日），下半部展開顯示本週詳細行程與待辦清單！
     */
    fun renderWeekExpandedBitmap(
        context: Context,
        anchorDate: LocalDate,
        widthDp: Int,
        heightDp: Int,
        items: List<UnifiedCalendarItem>,
        startMonday: Boolean,
        showLunar: Boolean,
        darkStyle: Boolean,
        accentArgb: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtMost(2.5f)
        val wPx = (widthDp * density).toInt().coerceIn(240, 1800)
        val hPx = (heightDp * density).toInt().coerceIn(180, 1800)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val primaryText = if (darkStyle) 0xFFF2F2F7.toInt() else 0xFF18181B.toInt()
        val secondaryText = if (darkStyle) 0xFF9898A0.toInt() else 0xFF686870.toInt()
        val cardBg = if (darkStyle) 0x22FFFFFF else 0x12000000
        val dividerColor = if (darkStyle) 0x26FFFFFF else 0x1F000000

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryText
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
        }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lunarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f * density
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryText
            textSize = 11.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryText
            textSize = 9.5f * density
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val weekLabels = if (startMonday) {
            arrayOf("一", "二", "三", "四", "五", "六", "日")
        } else {
            arrayOf("日", "一", "二", "三", "四", "五", "六")
        }

        val shift = if (startMonday) anchorDate.dayOfWeek.value - 1 else anchorDate.dayOfWeek.value % 7
        val weekStart = anchorDate.minusDays(shift.toLong())
        val weekEnd = weekStart.plusDays(6)
        val today = LocalDate.now()
        val byDate = items.groupBy { it.date }

        val colW = wPx / 7f
        val headerH = 16f * density
        val stripH = 42f * density

        for (c in 0..6) {
            val d = weekStart.plusDays(c.toLong())
            val cx = c * colW + colW / 2f
            canvas.drawText(weekLabels[c], cx, headerH - 3f * density, headerPaint)

            val isToday = d == today
            if (isToday) {
                fillPaint.color = (accentArgb and 0x00FFFFFF) or 0x33000000
                canvas.drawRoundRect(
                    RectF(c * colW + 3f * density, headerH, (c + 1) * colW - 3f * density, headerH + stripH),
                    8f * density,
                    8f * density,
                    fillPaint
                )
            }
            dayPaint.color = if (isToday) accentArgb else primaryText
            canvas.drawText(d.dayOfMonth.toString(), cx, headerH + 17f * density, dayPaint)

            if (showLunar) {
                val lunar = LunarCalendarEngine.getLunarInfo(d)
                lunarPaint.color = if (lunar.isTaiwanHoliday || lunar.solarTerm != null) accentArgb else secondaryText
                canvas.drawText(lunar.shortDisplay.take(3), cx, headerH + 30f * density, lunarPaint)
            }

            if (!byDate[d].isNullOrEmpty()) {
                fillPaint.color = accentArgb
                canvas.drawCircle(cx, headerH + stripH - 4f * density, 2.2f * density, fillPaint)
            }
        }

        // 分隔線
        val listTop = headerH + stripH + 6f * density
        fillPaint.color = dividerColor
        canvas.drawRect(0f, listTop - 3f * density, wPx.toFloat(), listTop - 2f * density, fillPaint)

        // 本週詳細行程展開清單
        val weekItems = items
            .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
            .sortedWith(compareBy<UnifiedCalendarItem> { it.date }.thenBy { it.startMillis })

        if (weekItems.isEmpty()) {
            subPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("本週無公開行程（點擊切換月檢視）", wPx / 2f, listTop + 28f * density, subPaint)
        } else {
            val zone = ZoneId.systemDefault()
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val rowH = 26f * density
            val gap = 4f * density
            val maxRows = ((hPx - listTop) / (rowH + gap)).toInt().coerceAtLeast(1)

            weekItems.take(maxRows).forEachIndexed { idx, ev ->
                val top = listTop + idx * (rowH + gap)
                fillPaint.color = cardBg
                canvas.drawRoundRect(
                    RectF(2f * density, top, wPx - 2f * density, top + rowH),
                    6f * density,
                    6f * density,
                    fillPaint
                )
                fillPaint.color = ev.colorArgb
                canvas.drawRoundRect(
                    RectF(6f * density, top + 4f * density, 9.5f * density, top + rowH - 4f * density),
                    2f * density,
                    2f * density,
                    fillPaint
                )
                val dateTag = "${ev.date.monthValue}/${ev.date.dayOfMonth}"
                val timeTag = if (ev.isAllDay) "全天" else Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalTime().format(timeFmt)
                val label = (if (ev.isTask) "○ " else "") + ev.title
                val clippedTitle = clipTextToWidth(label, titlePaint, wPx - 80f * density)
                canvas.drawText(clippedTitle, 15f * density, top + 16.5f * density, titlePaint)

                subPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("$dateTag $timeTag", wPx - 8f * density, top + 16.5f * density, subPaint)
            }
        }
        return bmp
    }

    private fun clipTextToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) > maxWidth) {
            end--
        }
        return text.substring(0, end)
    }
}

private fun buildBroadcastPi(
    context: Context,
    cls: Class<*>,
    action: String,
    widgetId: Int,
    reqOffset: Int
): PendingIntent {
    val intent = Intent(context, cls).apply {
        this.action = action
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return PendingIntent.getBroadcast(
        context,
        widgetId * 100 + reqOffset,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun buildMainAppPi(context: Context, reqCode: Int, quickAdd: Boolean): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(WidgetRefreshHelper.EXTRA_QUICK_ADD, quickAdd)
    }
    return PendingIntent.getActivity(
        context,
        reqCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
