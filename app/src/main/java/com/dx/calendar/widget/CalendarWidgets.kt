package com.dx.calendar.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dx.calendar.MainActivity
import com.dx.calendar.R
import com.dx.calendar.data.local.AppDatabase
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.repository.CalendarRepository
import com.dx.calendar.data.repository.UnifiedCalendarItem
import com.dx.calendar.util.LunarCalendarEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 具備 SupervisorJob 與全域防崩潰保護的小工具背景執行域，
 * 確保任何極端螢幕尺寸、Binder 限制或系統廣播異常皆不會導致主程式閃退。
 */
private val widgetScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }
)

private val activeWidgetAnimJobs = ConcurrentHashMap<Int, Job>()

object WidgetRefreshHelper {
    const val ACTION_PREV_MONTH = "com.dx.calendar.widget.ACTION_PREV_MONTH"
    const val ACTION_NEXT_MONTH = "com.dx.calendar.widget.ACTION_NEXT_MONTH"
    const val ACTION_RESET_TODAY = "com.dx.calendar.widget.ACTION_RESET_TODAY"
    const val ACTION_TOGGLE_WEEK_MODE = "com.dx.calendar.widget.ACTION_TOGGLE_WEEK_MODE"
    const val ACTION_TOGGLE_WEEK_ROW = "com.dx.calendar.widget.ACTION_TOGGLE_WEEK_ROW"
    const val ACTION_TOGGLE_TASK = "com.dx.calendar.widget.ACTION_TOGGLE_TASK"
    const val ACTION_CLICK_ITEM = "com.dx.calendar.widget.ACTION_CLICK_ITEM"
    const val EXTRA_WEEK_ROW = "extra_widget_week_row"
    const val EXTRA_TASK_ID = "extra_widget_task_id"
    const val EXTRA_QUICK_ADD = "extra_open_quick_add"
    const val EXTRA_OPEN_EPOCH_DAY = "extra_widget_open_epoch_day"
    const val EXTRA_OPEN_ITEM_RAW_ID = "extra_widget_open_item_raw_id"
    const val EXTRA_OPEN_DAY_VIEW = "extra_widget_open_day_view"
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

    /**
     * 類 vivo 月曆小工具「點擊單週手風琴展開」狀態：
     * -1 表示全月等高檢視；0..5 表示展開該週（放大顯示該週 7 天詳細行程卡片，其餘週折疊精簡）。
     */
    fun getExpandedWeekRow(context: Context, appWidgetId: Int): Int {
        return context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .getInt("expanded_week_row_$appWidgetId", -1)
    }

    fun setExpandedWeekRow(context: Context, appWidgetId: Int, row: Int) {
        context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .edit().putInt("expanded_week_row_$appWidgetId", row).apply()
    }

    /**
     * 儲存單顆桌面小工具專屬的深淺色與透明度設定 (0.0f 全透明 ~ 1.0f 實心)
     */
    fun setPerWidgetAppearance(context: Context, appWidgetId: Int, isDark: Boolean, opacity: Float) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_custom_style_$appWidgetId", true)
            .putBoolean("custom_dark_$appWidgetId", isDark)
            .putFloat("custom_opacity_$appWidgetId", opacity.coerceIn(0f, 1f))
            .apply()
    }

    /**
     * 當使用者選擇「套用至全部小工具」或於 App 設定頁調整全域小工具外觀時，清除個別覆寫使全部同步
     */
    fun clearAllPerWidgetAppearanceOverrides(context: Context) {
        val sp = context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
        val keysToRemove = sp.all.keys.filter {
            it.startsWith("has_custom_style_") || it.startsWith("custom_dark_") || it.startsWith("custom_opacity_")
        }
        if (keysToRemove.isNotEmpty()) {
            val editor = sp.edit()
            keysToRemove.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun getEffectiveWidgetAppearance(
        context: Context,
        appWidgetId: Int,
        globalDark: Boolean,
        globalOpacity: Float
    ): Pair<Boolean, Float> {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return globalDark to globalOpacity.coerceIn(0f, 1f)
        }
        val sp = context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
        return if (sp.getBoolean("has_custom_style_$appWidgetId", false)) {
            val d = sp.getBoolean("custom_dark_$appWidgetId", globalDark)
            val o = sp.getFloat("custom_opacity_$appWidgetId", globalOpacity).coerceIn(0f, 1f)
            d to o
        } else {
            globalDark to globalOpacity.coerceIn(0f, 1f)
        }
    }

    fun refreshAllWidgets(context: Context) {
        runCatching {
            val appContext = context.applicationContext ?: context
            val mgr = AppWidgetManager.getInstance(appContext)

            // 1. 刷新類 vivo 月曆小工具
            val vivoMonthIds = mgr.getAppWidgetIds(ComponentName(appContext, VivoMonthWidgetProvider::class.java))
            if (vivoMonthIds.isNotEmpty()) {
                VivoMonthWidgetProvider.updateWidgets(appContext, mgr, vivoMonthIds)
            }

            // 2. 刷新類 Google 月檢視小工具
            val googleMonthIds = mgr.getAppWidgetIds(ComponentName(appContext, GoogleMonthWidgetProvider::class.java))
            if (googleMonthIds.isNotEmpty()) {
                GoogleMonthWidgetProvider.updateWidgets(appContext, mgr, googleMonthIds)
            }

            // 3. 刷新類 Google 日曆時間表可捲動小工具
            val googleSchedIds = mgr.getAppWidgetIds(ComponentName(appContext, GoogleScheduleWidgetProvider::class.java))
            if (googleSchedIds.isNotEmpty()) {
                GoogleScheduleWidgetProvider.updateWidgets(appContext, mgr, googleSchedIds)
                runCatching { mgr.notifyAppWidgetViewDataChanged(googleSchedIds, R.id.widget_list_view) }
            }

            // 4. 刷新類 vivo 待辦與例行事項可捲動小工具
            val vivoTaskIds = mgr.getAppWidgetIds(ComponentName(appContext, VivoTasksWidgetProvider::class.java))
            if (vivoTaskIds.isNotEmpty()) {
                VivoTasksWidgetProvider.updateWidgets(appContext, mgr, vivoTaskIds)
                runCatching { mgr.notifyAppWidgetViewDataChanged(vivoTaskIds, R.id.widget_list_view) }
            }
        }
    }
}

/**
 * 1. 類 vivo 風格基底「月曆小工具」
 * 支援桌面自由縮放、0%~100% 自由透明度、點擊 `⚙` 設定透明度、點擊 `<` `>` 切換前後月份、
 * 點擊單週平滑展開動畫特效、再點收合切回全月、`now` 快捷鍵！
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
        val action = intent.action
        if (action == WidgetRefreshHelper.ACTION_PREV_MONTH ||
            action == WidgetRefreshHelper.ACTION_NEXT_MONTH ||
            action == WidgetRefreshHelper.ACTION_RESET_TODAY ||
            action == WidgetRefreshHelper.ACTION_TOGGLE_WEEK_ROW ||
            action == WidgetRefreshHelper.ACTION_TOGGLE_WEEK_MODE
        ) {
            val appCtx = context.applicationContext ?: context
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val currentExpanded = WidgetRefreshHelper.getExpandedWeekRow(appCtx, widgetId)
                val mgr = AppWidgetManager.getInstance(appCtx)
                when (action) {
                    WidgetRefreshHelper.ACTION_PREV_MONTH -> {
                        val nextOffset = WidgetRefreshHelper.getMonthOffset(appCtx, widgetId) - 1
                        WidgetRefreshHelper.setMonthOffset(appCtx, widgetId, nextOffset)
                        if (currentExpanded >= 0) {
                            val nextYm = YearMonth.now().plusMonths(nextOffset.toLong())
                            WidgetRefreshHelper.setExpandedWeekRow(
                                appCtx,
                                widgetId,
                                if (nextYm == YearMonth.now()) -2 else 0
                            )
                        }
                        updateWidgets(appCtx, mgr, intArrayOf(widgetId), pendingResult = safeGoAsync())
                    }
                    WidgetRefreshHelper.ACTION_NEXT_MONTH -> {
                        val nextOffset = WidgetRefreshHelper.getMonthOffset(appCtx, widgetId) + 1
                        WidgetRefreshHelper.setMonthOffset(appCtx, widgetId, nextOffset)
                        if (currentExpanded >= 0) {
                            val nextYm = YearMonth.now().plusMonths(nextOffset.toLong())
                            WidgetRefreshHelper.setExpandedWeekRow(
                                appCtx,
                                widgetId,
                                if (nextYm == YearMonth.now()) -2 else 0
                            )
                        }
                        updateWidgets(appCtx, mgr, intArrayOf(widgetId), pendingResult = safeGoAsync())
                    }
                    WidgetRefreshHelper.ACTION_RESET_TODAY -> {
                        WidgetRefreshHelper.setMonthOffset(appCtx, widgetId, 0)
                        WidgetRefreshHelper.setWeekOffset(appCtx, widgetId, 0)
                        if (currentExpanded >= 0) {
                            WidgetRefreshHelper.setExpandedWeekRow(appCtx, widgetId, -2)
                        }
                        updateWidgets(appCtx, mgr, intArrayOf(widgetId), pendingResult = safeGoAsync())
                    }
                    WidgetRefreshHelper.ACTION_TOGGLE_WEEK_ROW -> {
                        val tappedRow = intent.getIntExtra(WidgetRefreshHelper.EXTRA_WEEK_ROW, -1)
                        if (tappedRow in 0..5) {
                            val nextRow = if (currentExpanded == tappedRow) -1 else tappedRow
                            WidgetRefreshHelper.setExpandedWeekRow(appCtx, widgetId, nextRow)
                            animateWeekTransition(
                                context = appCtx,
                                mgr = mgr,
                                widgetId = widgetId,
                                fromExpandedRow = currentExpanded,
                                toExpandedRow = nextRow,
                                pendingResult = safeGoAsync()
                            )
                        }
                    }
                    WidgetRefreshHelper.ACTION_TOGGLE_WEEK_MODE -> {
                        val offset = WidgetRefreshHelper.getMonthOffset(appCtx, widgetId)
                        val ym = YearMonth.now().plusMonths(offset.toLong())
                        val nextRow = if (currentExpanded >= 0) {
                            -1
                        } else {
                            if (ym == YearMonth.now()) -2 else 0
                        }
                        WidgetRefreshHelper.setExpandedWeekRow(appCtx, widgetId, nextRow)
                        animateWeekTransition(
                            context = appCtx,
                            mgr = mgr,
                            widgetId = widgetId,
                            fromExpandedRow = currentExpanded,
                            toExpandedRow = nextRow,
                            pendingResult = safeGoAsync()
                        )
                    }
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    private fun safeGoAsync(): BroadcastReceiver.PendingResult? = runCatching { goAsync() }.getOrNull()

    companion object {
        // 輕巧流暢的手風琴展開/收合動畫中間插值幀（總時長約 105ms，輕盈不拖泥帶水）
        private val EXPAND_ANIM_EASED_STEPS = floatArrayOf(0.34f, 0.70f, 0.92f)
        private const val EXPAND_ANIM_FRAME_DELAY_MS = 28L

        private fun computeNumWeeks(ym: YearMonth, startMonday: Boolean): Int {
            val firstOfMonth = ym.atDay(1)
            val shift = if (startMonday) firstOfMonth.dayOfWeek.value - 1 else firstOfMonth.dayOfWeek.value % 7
            return ((shift + ym.lengthOfMonth() + 6) / 7).coerceIn(5, 6)
        }

        private fun computeTodayWeekRow(ym: YearMonth, startMonday: Boolean): Int {
            val firstOfMonth = ym.atDay(1)
            val shift = if (startMonday) firstOfMonth.dayOfWeek.value - 1 else firstOfMonth.dayOfWeek.value % 7
            val today = LocalDate.now()
            return if (YearMonth.from(today) == ym) {
                ((shift + today.dayOfMonth - 1) / 7).coerceIn(0, 5)
            } else {
                0
            }
        }

        private fun resolveExpandedRow(
            context: Context,
            widgetId: Int,
            ym: YearMonth,
            startMonday: Boolean,
            numWeeks: Int,
            rawRow: Int
        ): Int {
            return when {
                rawRow == -2 -> {
                    val autoRow = computeTodayWeekRow(ym, startMonday).coerceIn(0, numWeeks - 1)
                    WidgetRefreshHelper.setExpandedWeekRow(context, widgetId, autoRow)
                    autoRow
                }
                rawRow in 0 until numWeeks -> rawRow
                else -> -1
            }
        }

        /**
         * 桌面小工具展開／收合／切換週次的平滑微動畫引擎：
         * 透過 `partiallyUpdateAppWidget` 快速推送手風琴高度插值與卡片滑入漸顯幀，
         * 最後以完整高解析幀定格並同步更新點擊層權重。
         */
        fun animateWeekTransition(
            context: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            fromExpandedRow: Int,
            toExpandedRow: Int,
            pendingResult: BroadcastReceiver.PendingResult? = null
        ) {
            val appCtx = context.applicationContext ?: context
            activeWidgetAnimJobs[widgetId]?.cancel()
            val job = widgetScope.launch {
                try {
                    withTimeoutOrNull(3500L) {
                        val repo = CalendarRepository(appCtx)
                        val settings = repo.prefs.getSettingsOnce()
                        val cats = repo.dao.getCategoriesOnce()
                        val events = repo.dao.getAllEventsOnce()
                        val tasks = repo.dao.getAllTasksOnce()
                        val checkIns = repo.dao.getRoutineCheckInsOnce()

                        val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                            appCtx, widgetId, settings.widgetDarkStyle, settings.widgetOpacity
                        )
                        val offset = WidgetRefreshHelper.getMonthOffset(appCtx, widgetId)
                        val ym = YearMonth.now().plusMonths(offset.toLong())
                        val numWeeks = computeNumWeeks(ym, settings.startWeekOnMonday)
                        val resolvedTo = resolveExpandedRow(appCtx, widgetId, ym, settings.startWeekOnMonday, numWeeks, toExpandedRow)
                        val resolvedFrom = if (fromExpandedRow in 0 until numWeeks) fromExpandedRow else -1

                        val gridStart = ym.atDay(1).minusDays(7)
                        val gridEnd = ym.atEndOfMonth().plusDays(14)
                        val items = repo.buildItemsForRange(
                            gridStart, gridEnd, cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
                        ).filter { it.showInMonthGrid && it.showInAgendaAndWidget }

                        val (minW, minH) = readSafeWidgetDp(mgr, widgetId, defaultW = 250, defaultH = 250)
                        val canvasHeightDp = (minH - 42).coerceAtLeast(100)
                        val accentArgb = settings.brandStyle.primaryHex.toInt()

                        if (resolvedFrom != resolvedTo) {
                            for (easedStep in EXPAND_ANIM_EASED_STEPS) {
                                val frameBmp = MonthCanvasRenderer.renderMonthBitmap(
                                    context = appCtx,
                                    yearMonth = ym,
                                    widthDp = minW,
                                    heightDp = canvasHeightDp,
                                    items = items,
                                    startMonday = settings.startWeekOnMonday,
                                    showLunar = settings.showLunar,
                                    darkStyle = darkStyle,
                                    accentArgb = accentArgb,
                                    googleGridStyle = false,
                                    numWeeks = numWeeks,
                                    expandedWeekRow = resolvedTo,
                                    prevExpandedRow = resolvedFrom,
                                    transitionProgress = easedStep,
                                    maxPixels = MonthCanvasRenderer.ANIM_FRAME_MAX_PIXELS
                                )
                                val partialViews = RemoteViews(appCtx.packageName, R.layout.widget_month_canvas).apply {
                                    setImageViewBitmap(R.id.widget_canvas_img, frameBmp)
                                }
                                safePartiallyUpdateWidget(mgr, widgetId, partialViews)
                                delay(EXPAND_ANIM_FRAME_DELAY_MS)
                            }
                        }

                        // 最終定格高解析幀與完整互動點擊層更新
                        val finalViews = buildVivoMonthFullRemoteViews(
                            context = appCtx,
                            id = widgetId,
                            ym = ym,
                            minW = minW,
                            canvasHeightDp = canvasHeightDp,
                            items = items,
                            startMonday = settings.startWeekOnMonday,
                            showLunar = settings.showLunar,
                            darkStyle = darkStyle,
                            opacity = opacity,
                            accentArgb = accentArgb,
                            numWeeks = numWeeks,
                            expandedRow = resolvedTo
                        )
                        safeUpdateWidget(mgr, widgetId, finalViews)
                    }
                } finally {
                    runCatching { pendingResult?.finish() }
                }
            }
            activeWidgetAnimJobs[widgetId] = job
        }

        fun updateWidgets(
            context: Context,
            mgr: AppWidgetManager,
            ids: IntArray,
            pendingResult: BroadcastReceiver.PendingResult? = null
        ) {
            val appCtx = context.applicationContext ?: context
            widgetScope.launch {
                try {
                    withTimeoutOrNull(4500L) {
                        val repo = CalendarRepository(appCtx)
                        val settings = repo.prefs.getSettingsOnce()
                        val cats = repo.dao.getCategoriesOnce()
                        val events = repo.dao.getAllEventsOnce()
                        val tasks = repo.dao.getAllTasksOnce()
                        val checkIns = repo.dao.getRoutineCheckInsOnce()

                        for (id in ids) {
                            activeWidgetAnimJobs[id]?.cancel()
                            runCatching {
                                val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                                    appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity
                                )
                                val offset = WidgetRefreshHelper.getMonthOffset(appCtx, id)
                                val ym = YearMonth.now().plusMonths(offset.toLong())
                                val numWeeks = computeNumWeeks(ym, settings.startWeekOnMonday)
                                val rawExpanded = WidgetRefreshHelper.getExpandedWeekRow(appCtx, id)
                                val expandedRow = resolveExpandedRow(appCtx, id, ym, settings.startWeekOnMonday, numWeeks, rawExpanded)

                                val gridStart = ym.atDay(1).minusDays(7)
                                val gridEnd = ym.atEndOfMonth().plusDays(14)

                                // 隱私日曆在桌面小工具永遠為 false 鎖定保護！
                                val items = repo.buildItemsForRange(
                                    gridStart, gridEnd, cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
                                ).filter { it.showInMonthGrid && it.showInAgendaAndWidget }

                                val (minW, minH) = readSafeWidgetDp(mgr, id, defaultW = 250, defaultH = 250)
                                val views = buildVivoMonthFullRemoteViews(
                                    context = appCtx,
                                    id = id,
                                    ym = ym,
                                    minW = minW,
                                    canvasHeightDp = (minH - 42).coerceAtLeast(100),
                                    items = items,
                                    startMonday = settings.startWeekOnMonday,
                                    showLunar = settings.showLunar,
                                    darkStyle = darkStyle,
                                    opacity = opacity,
                                    accentArgb = settings.brandStyle.primaryHex.toInt(),
                                    numWeeks = numWeeks,
                                    expandedRow = expandedRow
                                )
                                safeUpdateWidget(mgr, id, views)
                            }
                        }
                    }
                } finally {
                    runCatching { pendingResult?.finish() }
                }
            }
        }

        private fun buildVivoMonthFullRemoteViews(
            context: Context,
            id: Int,
            ym: YearMonth,
            minW: Int,
            canvasHeightDp: Int,
            items: List<UnifiedCalendarItem>,
            startMonday: Boolean,
            showLunar: Boolean,
            darkStyle: Boolean,
            opacity: Float,
            accentArgb: Int,
            numWeeks: Int,
            expandedRow: Int
        ): RemoteViews {
            val bmp = MonthCanvasRenderer.renderMonthBitmap(
                context = context,
                yearMonth = ym,
                widthDp = minW,
                heightDp = canvasHeightDp,
                items = items,
                startMonday = startMonday,
                showLunar = showLunar,
                darkStyle = darkStyle,
                accentArgb = accentArgb,
                googleGridStyle = false,
                numWeeks = numWeeks,
                expandedWeekRow = expandedRow,
                prevExpandedRow = expandedRow,
                transitionProgress = 1f
            )

            val views = RemoteViews(context.packageName, R.layout.widget_month_canvas)
            applyMonthContainerThemeAndOpacity(views, darkStyle, opacity)

            views.setTextViewText(
                R.id.widget_month_title,
                if (expandedRow >= 0) "%d/%02d · 第%d週".format(ym.year, ym.monthValue, expandedRow + 1)
                else "%d/%02d".format(ym.year, ym.monthValue)
            )
            views.setViewVisibility(R.id.btn_widget_mode, android.view.View.VISIBLE)
            views.setTextViewText(R.id.btn_widget_mode, if (expandedRow >= 0) "全月" else "單週")
            views.setTextViewText(R.id.btn_widget_today, "now")
            views.setImageViewBitmap(R.id.widget_canvas_img, bmp)

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

            views.setViewVisibility(R.id.widget_month_tap_grid, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_week_tap_row, android.view.View.GONE)
            bindVivoAccordionWeekTapRows(context, views, id, numWeeks, expandedRow)

            views.setOnClickPendingIntent(R.id.btn_widget_config, buildWidgetConfigPi(context, id, 8))
            views.setOnClickPendingIntent(R.id.btn_widget_add, buildMainAppPi(context, id * 10 + 4, quickAdd = true))
            views.setOnClickPendingIntent(R.id.widget_month_title, buildMainAppPi(context, id * 10 + 6, quickAdd = false))
            return views
        }
    }
}

/**
 * 2. 類 Google 日曆風格「月檢視」小工具（滿版網格 + Tasks 圓圈 + 自由縮放 + 自由透明度）
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
        val action = intent.action
        if (action == WidgetRefreshHelper.ACTION_PREV_MONTH ||
            action == WidgetRefreshHelper.ACTION_NEXT_MONTH ||
            action == WidgetRefreshHelper.ACTION_RESET_TODAY
        ) {
            val appCtx = context.applicationContext ?: context
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                when (action) {
                    WidgetRefreshHelper.ACTION_PREV_MONTH -> {
                        WidgetRefreshHelper.setMonthOffset(appCtx, widgetId, WidgetRefreshHelper.getMonthOffset(appCtx, widgetId) - 1)
                    }
                    WidgetRefreshHelper.ACTION_NEXT_MONTH -> {
                        WidgetRefreshHelper.setMonthOffset(appCtx, widgetId, WidgetRefreshHelper.getMonthOffset(appCtx, widgetId) + 1)
                    }
                    WidgetRefreshHelper.ACTION_RESET_TODAY -> {
                        WidgetRefreshHelper.setMonthOffset(appCtx, widgetId, 0)
                    }
                }
                val pending = runCatching { goAsync() }.getOrNull()
                updateWidgets(appCtx, AppWidgetManager.getInstance(appCtx), intArrayOf(widgetId), pending)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        fun updateWidgets(
            context: Context,
            mgr: AppWidgetManager,
            ids: IntArray,
            pendingResult: BroadcastReceiver.PendingResult? = null
        ) {
            val appCtx = context.applicationContext ?: context
            widgetScope.launch {
                try {
                    withTimeoutOrNull(4500L) {
                        val repo = CalendarRepository(appCtx)
                        val settings = repo.prefs.getSettingsOnce()
                        val cats = repo.dao.getCategoriesOnce()
                        val events = repo.dao.getAllEventsOnce()
                        val tasks = repo.dao.getAllTasksOnce()
                        val checkIns = repo.dao.getRoutineCheckInsOnce()

                        for (id in ids) {
                            runCatching {
                                val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                                    appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity
                                )
                                val offset = WidgetRefreshHelper.getMonthOffset(appCtx, id)
                                val ym = YearMonth.now().plusMonths(offset.toLong())
                                val items = repo.buildItemsForRange(
                                    ym.atDay(1).minusDays(7), ym.atEndOfMonth().plusDays(14),
                                    cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
                                ).filter { it.showInMonthGrid && it.showInAgendaAndWidget }

                                val (minW, minH) = readSafeWidgetDp(mgr, id, defaultW = 260, defaultH = 260)

                                val bmp = MonthCanvasRenderer.renderMonthBitmap(
                                    context = appCtx,
                                    yearMonth = ym,
                                    widthDp = minW,
                                    heightDp = (minH - 42).coerceAtLeast(100),
                                    items = items,
                                    startMonday = settings.startWeekOnMonday,
                                    showLunar = settings.showLunar,
                                    darkStyle = darkStyle,
                                    accentArgb = if (darkStyle) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt(),
                                    googleGridStyle = true
                                )

                                val views = RemoteViews(appCtx.packageName, R.layout.widget_month_canvas)
                                applyMonthContainerThemeAndOpacity(views, darkStyle, opacity)

                                views.setTextViewText(R.id.widget_month_title, "%d年%d月".format(ym.year, ym.monthValue))
                                views.setViewVisibility(R.id.btn_widget_mode, android.view.View.GONE)
                                views.setTextViewText(R.id.btn_widget_today, "now")
                                views.setImageViewBitmap(R.id.widget_canvas_img, bmp)

                                views.setOnClickPendingIntent(
                                    R.id.btn_widget_prev,
                                    buildBroadcastPi(appCtx, GoogleMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_PREV_MONTH, id, 11)
                                )
                                views.setOnClickPendingIntent(
                                    R.id.btn_widget_next,
                                    buildBroadcastPi(appCtx, GoogleMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_NEXT_MONTH, id, 12)
                                )
                                views.setOnClickPendingIntent(
                                    R.id.btn_widget_today,
                                    buildBroadcastPi(appCtx, GoogleMonthWidgetProvider::class.java, WidgetRefreshHelper.ACTION_RESET_TODAY, id, 13)
                                )
                                views.setOnClickPendingIntent(R.id.btn_widget_config, buildWidgetConfigPi(appCtx, id, 18))
                                views.setOnClickPendingIntent(R.id.btn_widget_add, buildMainAppPi(appCtx, id * 10 + 14, quickAdd = true))
                                views.setOnClickPendingIntent(R.id.widget_month_title, buildMainAppPi(appCtx, id * 10 + 16, quickAdd = false))
                                views.setViewVisibility(R.id.widget_month_tap_grid, android.view.View.VISIBLE)
                                views.setViewVisibility(R.id.widget_week_tap_row, android.view.View.GONE)
                                bindMonthTapCells(appCtx, views, id, ym, settings.startWeekOnMonday)

                                safeUpdateWidget(mgr, id, views)
                            }
                        }
                    }
                } finally {
                    runCatching { pendingResult?.finish() }
                }
            }
        }
    }
}

/**
 * 3. 類 Google 日曆風格「日曆時間表」可上下捲動小工具（支援 0%~100% 自由透明度與 `⚙` 設定鈕）
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
        if (intent.action == WidgetRefreshHelper.ACTION_TOGGLE_TASK ||
            intent.action == WidgetRefreshHelper.ACTION_CLICK_ITEM
        ) {
            val appCtx = context.applicationContext ?: context
            val mode = intent.getStringExtra(WidgetRefreshHelper.EXTRA_WIDGET_MODE) ?: "SCHEDULE"
            val isTask = intent.getBooleanExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, false)
            val taskId = intent.getLongExtra(WidgetRefreshHelper.EXTRA_TASK_ID, -1L)
            val rawId = intent.getLongExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, -1L)
            val epochDay = intent.getLongExtra(
                WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY,
                LocalDate.now().toEpochDay()
            )

            if (mode == "TASKS") {
                val pending = runCatching { goAsync() }.getOrNull()
                widgetScope.launch {
                    try {
                        val dao = AppDatabase.getInstance(appCtx).calendarDao()
                        if (isTask && taskId > 0L) {
                            val task = dao.getTaskById(taskId)
                            if (task != null) {
                                if (task.recurrenceType != "NONE") {
                                    val keySet = dao.getRoutineCheckInsOnce().any {
                                        it.targetType == "TASK" && it.targetId == task.id && it.epochDay == epochDay
                                    }
                                    if (keySet) dao.removeCheckIn("TASK", task.id, epochDay)
                                    else dao.insertCheckIn(RoutineCheckInEntity("TASK", task.id, epochDay))
                                } else {
                                    dao.upsertTask(task.copy(isCompleted = !task.isCompleted))
                                }
                                WidgetRefreshHelper.refreshAllWidgets(appCtx)
                            }
                        } else if (rawId > 0L) {
                            // 例行活動於桌面小工具直接打卡切換完成狀態，避免 Android 14–16 背景 Receiver startActivity 限制
                            val done = dao.getRoutineCheckInsOnce().any {
                                it.targetType == "EVENT" && it.targetId == rawId && it.epochDay == epochDay
                            }
                            if (done) dao.removeCheckIn("EVENT", rawId, epochDay)
                            else dao.insertCheckIn(RoutineCheckInEntity("EVENT", rawId, epochDay))
                            WidgetRefreshHelper.refreshAllWidgets(appCtx)
                        }
                    } finally {
                        runCatching { pending?.finish() }
                    }
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val appCtx = context.applicationContext ?: context
            widgetScope.launch {
                val repo = CalendarRepository(appCtx)
                val settings = repo.prefs.getSettingsOnce()
                val today = LocalDate.now()
                val dowNames = arrayOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
                val header = "${today.dayOfMonth} ${dowNames[today.dayOfWeek.value - 1]}"

                for (id in ids) {
                    runCatching {
                        val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                            appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_scrollable_list)
                        applyListContainerThemeAndOpacity(views, darkStyle, opacity)

                        views.setTextViewText(R.id.widget_list_header_title, header)
                        views.setTextViewText(R.id.widget_list_badge, "")
                        views.setViewVisibility(R.id.widget_list_badge, android.view.View.INVISIBLE)

                        val svcIntent = Intent(appCtx, ScheduleRemoteViewsService::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            putExtra("mode", "SCHEDULE")
                            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                        }
                        views.setRemoteAdapter(R.id.widget_list_view, svcIntent)
                        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

                        views.setOnClickPendingIntent(R.id.btn_widget_list_config, buildWidgetConfigPi(appCtx, id, 28))
                        views.setOnClickPendingIntent(R.id.btn_widget_list_add, buildMainAppPi(appCtx, id * 10 + 21, quickAdd = true))
                        views.setOnClickPendingIntent(R.id.widget_list_header_title, buildMainAppPi(appCtx, id * 10 + 22, quickAdd = false))

                        val clickTemplate = Intent(appCtx, MainActivity::class.java).apply {
                            action = WidgetRefreshHelper.ACTION_CLICK_ITEM
                            setPackage(appCtx.packageName)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                        val templatePi = buildSafeActivityPi(
                            context = appCtx,
                            reqCode = id * 10 + 23,
                            intent = clickTemplate,
                            mutable = true
                        )
                        views.setPendingIntentTemplate(R.id.widget_list_view, templatePi)

                        safeUpdateWidget(mgr, id, views)
                    }
                }
            }
        }
    }
}

/**
 * 4. 類 vivo 風格「待辦事項與例行中心」可捲動小工具（支援 0%~100% 自由透明度與 `⚙` 設定鈕）
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
            val appCtx = context.applicationContext ?: context
            widgetScope.launch {
                val repo = CalendarRepository(appCtx)
                val settings = repo.prefs.getSettingsOnce()
                val pendingCount = repo.dao.getAllTasksOnce().count { !it.isCompleted && !it.isPrivacy }

                for (id in ids) {
                    runCatching {
                        val (darkStyle, opacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                            appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_scrollable_list)
                        applyListContainerThemeAndOpacity(views, darkStyle, opacity)

                        views.setTextViewText(R.id.widget_list_header_title, "待辦與例行")
                        views.setViewVisibility(R.id.widget_list_badge, android.view.View.VISIBLE)
                        views.setTextViewText(R.id.widget_list_badge, "$pendingCount 項待辦")

                        val svcIntent = Intent(appCtx, ScheduleRemoteViewsService::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            putExtra("mode", "TASKS")
                            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                        }
                        views.setRemoteAdapter(R.id.widget_list_view, svcIntent)
                        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

                        views.setOnClickPendingIntent(R.id.btn_widget_list_config, buildWidgetConfigPi(appCtx, id, 38))
                        views.setOnClickPendingIntent(R.id.btn_widget_list_add, buildMainAppPi(appCtx, id * 10 + 31, quickAdd = true))
                        views.setOnClickPendingIntent(R.id.widget_list_header_title, buildMainAppPi(appCtx, id * 10 + 32, quickAdd = false))

                        val clickTemplate = Intent(appCtx, GoogleScheduleWidgetProvider::class.java).apply {
                            action = WidgetRefreshHelper.ACTION_TOGGLE_TASK
                            setPackage(appCtx.packageName)
                        }
                        val templatePi = PendingIntent.getBroadcast(
                            appCtx,
                            id * 10 + 33,
                            clickTemplate,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                        views.setPendingIntentTemplate(R.id.widget_list_view, templatePi)

                        safeUpdateWidget(mgr, id, views)
                    }
                }
            }
        }
    }
}

class ScheduleRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val mode = intent.getStringExtra("mode") ?: "SCHEDULE"
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return ScheduleRemoteViewsFactory(applicationContext, mode, widgetId)
    }
}

private class ScheduleRemoteViewsFactory(
    private val context: Context,
    private val mode: String,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    @Volatile
    private var rows: List<UnifiedCalendarItem> = emptyList()
    @Volatile
    private var darkStyle: Boolean = true
    @Volatile
    private var opacity: Float = 0.85f
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                val repo = CalendarRepository(context)
                val settings = repo.prefs.getSettingsOnce()
                val eff = WidgetRefreshHelper.getEffectiveWidgetAppearance(
                    context, appWidgetId, settings.widgetDarkStyle, settings.widgetOpacity
                )
                darkStyle = eff.first
                opacity = eff.second

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
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        val snapshot = rows
        if (position !in snapshot.indices) return null
        return runCatching {
            val item = snapshot[position]
            val zone = ZoneId.systemDefault()
            val dowNames = arrayOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
            val showDateHeader = position == 0 || snapshot[position - 1].date != item.date

            val rv = RemoteViews(context.packageName, R.layout.widget_item_row)
            val headerColor = if (darkStyle) 0xFFB0B0BC.toInt() else 0xFF4A4A54.toInt()
            val titleColor = if (darkStyle) 0xFFF5F5F7.toInt() else 0xFF16161A.toInt()
            val subColor = if (darkStyle) 0xFFB5B5BE.toInt() else 0xFF5A5A66.toInt()

            val cardAlpha = (opacity * (if (darkStyle) 42f else 22f)).roundToInt().coerceIn(0, 80)
            rv.setImageViewResource(
                R.id.row_card_bg,
                if (darkStyle) R.drawable.bg_widget_row_dark_solid else R.drawable.bg_widget_row_light_solid
            )
            rv.setInt(R.id.row_card_bg, "setImageAlpha", cardAlpha)

            if (showDateHeader) {
                val lunar = LunarCalendarEngine.getLunarInfo(item.date)
                rv.setViewVisibility(R.id.row_date_header, android.view.View.VISIBLE)
                rv.setTextColor(R.id.row_date_header, headerColor)
                rv.setTextViewText(
                    R.id.row_date_header,
                    "${item.date.monthValue}月${item.date.dayOfMonth}日 ${dowNames[item.date.dayOfWeek.value - 1]} · ${lunar.shortDisplay}"
                )
            } else {
                rv.setViewVisibility(R.id.row_date_header, android.view.View.GONE)
            }

            val checkableInMode = item.isTask || (mode == "TASKS" && item.isRoutine)
            val prefix = when {
                checkableInMode && item.isCompleted -> "✓ "
                checkableInMode -> "○ "
                else -> ""
            }
            rv.setTextColor(R.id.row_item_title, titleColor)
            rv.setTextViewText(R.id.row_item_title, prefix + item.title)

            val timeLabel = if (item.isAllDay) {
                "全天"
            } else {
                val s = Instant.ofEpochMilli(item.startMillis).atZone(zone).toLocalTime().format(timeFmt)
                val e = Instant.ofEpochMilli(item.endMillis).atZone(zone).toLocalTime().format(timeFmt)
                if (item.isTask) s else "$s - $e"
            }
            rv.setTextColor(R.id.row_item_time, subColor)
            rv.setTextViewText(R.id.row_item_time, timeLabel)
            rv.setInt(R.id.row_color_bar, "setBackgroundColor", item.colorArgb)

            val fillIn = Intent().apply {
                putExtra(WidgetRefreshHelper.EXTRA_WIDGET_MODE, mode)
                putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, item.date.toEpochDay())
                putExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, item.rawId)
                putExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, item.isTask)
                if (item.isTask) {
                    putExtra(WidgetRefreshHelper.EXTRA_TASK_ID, item.rawId)
                }
            }
            rv.setOnClickFillInIntent(R.id.row_card_container, fillIn)
            rv.setOnClickFillInIntent(R.id.row_date_header, fillIn)
            rv
        }.getOrNull()
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

private fun readSafeWidgetDp(
    mgr: AppWidgetManager,
    widgetId: Int,
    defaultW: Int,
    defaultH: Int
): Pair<Int, Int> {
    val opts = runCatching { mgr.getAppWidgetOptions(widgetId) }.getOrNull()
    val rawW = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultW) ?: defaultW
    val rawH = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultH) ?: defaultH
    // 摺疊機內外螢幕切換時可能回報極大或極小過渡 dp，強制夾緊在合理範圍
    return rawW.coerceIn(140, 620) to rawH.coerceIn(140, 660)
}

private fun safeUpdateWidget(mgr: AppWidgetManager, widgetId: Int, views: RemoteViews) {
    runCatching { mgr.updateAppWidget(widgetId, views) }
}

private fun safePartiallyUpdateWidget(mgr: AppWidgetManager, widgetId: Int, views: RemoteViews) {
    runCatching { mgr.partiallyUpdateAppWidget(widgetId, views) }
}

private fun applyMonthContainerThemeAndOpacity(views: RemoteViews, darkStyle: Boolean, opacity: Float) {
    val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    views.setImageViewResource(
        R.id.widget_bg_layer,
        if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
    )
    views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

    val titleColor = if (darkStyle) 0xFFF5F5F7.toInt() else 0xFF161618.toInt()
    val iconMutedColor = if (darkStyle) 0xFFA8A8B4.toInt() else 0xFF5A5A66.toInt()
    val modeChipTextColor = if (darkStyle) 0xFFD8D8E0.toInt() else 0xFF282830.toInt()

    views.setTextColor(R.id.widget_month_title, titleColor)
    views.setTextColor(R.id.btn_widget_prev, iconMutedColor)
    views.setTextColor(R.id.btn_widget_next, iconMutedColor)
    views.setTextColor(R.id.btn_widget_config, iconMutedColor)
    views.setTextColor(R.id.btn_widget_mode, modeChipTextColor)
}

private fun applyListContainerThemeAndOpacity(views: RemoteViews, darkStyle: Boolean, opacity: Float) {
    val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    views.setImageViewResource(
        R.id.widget_bg_layer,
        if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
    )
    views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

    val titleColor = if (darkStyle) 0xFFF5F5F7.toInt() else 0xFF161618.toInt()
    val iconMutedColor = if (darkStyle) 0xFFA8A8B4.toInt() else 0xFF5A5A66.toInt()
    val emptyColor = if (darkStyle) 0xFF8E8E96.toInt() else 0xFF64646E.toInt()

    views.setTextColor(R.id.widget_list_header_title, titleColor)
    views.setTextColor(R.id.btn_widget_list_config, iconMutedColor)
    views.setTextColor(R.id.widget_empty_text, emptyColor)
}

/**
 * 相容 Android 8 ~ Android 16 (targetSdk = 36) 的安全 Activity PendingIntent 建構器：
 * 於 API 34+ 明確授權桌面點擊觸發背景啟動 Activity (Creator BAL Opt-In)，解決點擊無反應與權限閃退。
 */
private fun buildSafeActivityPi(
    context: Context,
    reqCode: Int,
    intent: Intent,
    mutable: Boolean = false
): PendingIntent {
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)

    if (Build.VERSION.SDK_INT >= 34) {
        val optionsBundle = runCatching {
            val opts = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 36) {
                opts.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                )
            } else {
                @Suppress("DEPRECATION")
                opts.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            opts.toBundle()
        }.getOrNull()
        if (optionsBundle != null) {
            return PendingIntent.getActivity(context, reqCode, intent, flags, optionsBundle)
        }
    }
    return PendingIntent.getActivity(context, reqCode, intent, flags)
}

private fun buildWidgetConfigPi(context: Context, widgetId: Int, reqOffset: Int): PendingIntent {
    val intent = Intent(context, WidgetConfigActivity::class.java).apply {
        setPackage(context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return buildSafeActivityPi(context, widgetId * 100 + reqOffset, intent, mutable = false)
}

/**
 * 響應式高解析度月曆小工具畫布渲染器：
 * 1. 內建摺疊機防爆記憶體像素預算縮放 (`computeSafeCanvasDimensions`)，徹底根絕 `TransactionTooLargeException` 與 `exceeds maximum bitmap memory usage`！
 * 2. 內建手風琴展開／收合平滑過渡動畫渲染 (`prevExpandedRow` → `expandedWeekRow` + `transitionProgress`)，支援列高平滑伸縮、高亮光暈與卡片滑入漸顯！
 */
internal object MonthCanvasRenderer {

    const val DEFAULT_MAX_PIXELS = 340_000   // 約 1.36 MB 上限，兼顧清晰度與摺疊機外螢幕記憶體安全
    const val ANIM_FRAME_MAX_PIXELS = 175_000 // 動畫中間幀約 700 KB，極速渲染零卡頓
    const val MAX_SIDE_PX = 820

    internal fun computeSafeCanvasDimensions(
        widthDp: Int,
        heightDp: Int,
        density: Float,
        maxPixels: Int = DEFAULT_MAX_PIXELS,
        maxSidePx: Int = MAX_SIDE_PX
    ): Pair<Int, Int> {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f }?.coerceIn(1f, 2.4f) ?: 2f
        val safeWdp = widthDp.coerceIn(120, 680)
        val safeHdp = heightDp.coerceIn(90, 680)

        var w = (safeWdp * safeDensity).coerceAtLeast(180f)
        var h = (safeHdp * safeDensity).coerceAtLeast(140f)

        val longest = maxOf(w, h)
        if (longest > maxSidePx) {
            val sideScale = maxSidePx / longest
            w *= sideScale
            h *= sideScale
        }

        val area = w * h
        val safeBudget = maxPixels.coerceAtLeast(60_000).toFloat()
        if (area > safeBudget) {
            val areaScale = sqrt(safeBudget / area)
            w *= areaScale
            h *= areaScale
        }

        return w.roundToInt().coerceAtLeast(160) to h.roundToInt().coerceAtLeast(120)
    }

    internal fun computeAccordionRowWeights(
        numWeeks: Int,
        prevExpandedRow: Int,
        targetExpandedRow: Int,
        progress: Float
    ): FloatArray {
        val safeWeeks = numWeeks.coerceIn(5, 6)
        val t = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        return FloatArray(safeWeeks) { r ->
            val startW = if (prevExpandedRow in 0 until safeWeeks && r == prevExpandedRow) 4f else 1f
            val endW = if (targetExpandedRow in 0 until safeWeeks && r == targetExpandedRow) 4f else 1f
            startW + (endW - startW) * t
        }
    }

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
        googleGridStyle: Boolean,
        numWeeks: Int = 6,
        expandedWeekRow: Int = -1,
        prevExpandedRow: Int = expandedWeekRow,
        transitionProgress: Float = 1f,
        maxPixels: Int = DEFAULT_MAX_PIXELS
    ): Bitmap {
        val rawDensity = context.resources.displayMetrics.density
        val (wPx, hPx) = computeSafeCanvasDimensions(
            widthDp = widthDp,
            heightDp = heightDp,
            density = rawDensity,
            maxPixels = maxPixels
        )
        // 依據實際畫布像素與邏輯寬度反推繪圖等效密度，確保任何縮放比例下文字與圓角完全成比例且銳利！
        val density = (wPx.toFloat() / widthDp.coerceIn(120, 680).toFloat()).coerceIn(0.85f, 2.4f)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val primaryText = if (darkStyle) 0xFFF2F2F7.toInt() else 0xFF18181B.toInt()
        val secondaryText = if (darkStyle) 0xFF9898A0.toInt() else 0xFF5C5C66.toInt()
        val gridLineColor = if (darkStyle) 0x26FFFFFF else 0x22000000

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryText
            textSize = 10.5f * density
            textAlign = Paint.Align.CENTER
        }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (if (hPx < 300) 11.5f else 13.5f) * density
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
        val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF141418.toInt()
            textSize = 8.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        val gridH = (hPx - gridTop).coerceAtLeast(60f)
        val safeWeeks = numWeeks.coerceIn(5, 6)
        val tProgress = transitionProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val rowWeights = computeAccordionRowWeights(
            numWeeks = safeWeeks,
            prevExpandedRow = prevExpandedRow,
            targetExpandedRow = expandedWeekRow,
            progress = tProgress
        )
        val totalWeight = rowWeights.sum().coerceAtLeast(1f)
        val unitH = gridH / totalWeight
        val anyWeekAccordionMode = rowWeights.any { it > 1.05f }
        val isAnimating = tProgress < 0.99f && prevExpandedRow != expandedWeekRow

        val firstOfMonth = yearMonth.atDay(1)
        val shift = if (startMonday) firstOfMonth.dayOfWeek.value - 1 else firstOfMonth.dayOfWeek.value % 7
        val startDate = firstOfMonth.minusDays(shift.toLong())
        val today = LocalDate.now()
        val byDate = items.groupBy { it.date }

        var curTop = gridTop
        for (r in 0 until safeWeeks) {
            val rWeight = rowWeights[r]
            val rowExpandFrac = ((rWeight - 1f) / 3f).coerceIn(0f, 1f) // 0f = 完全折疊, 1f = 完全展開
            val rowH = unitH * rWeight
            val cellTop = curTop
            curTop += rowH

            if (googleGridStyle) {
                canvas.drawLine(0f, cellTop, wPx.toFloat(), cellTop, linePaint)
            } else if (rowExpandFrac > 0.04f) {
                // 類 vivo 展開單週的柔和底色強調帶 + 展開瞬間微縮放與光暈邊框動畫特效！
                val insetX = (1f - rowExpandFrac) * 6f * density + 1f * density
                val baseBandAlpha = if (darkStyle) 0x18 else 0x0E
                val animBoostAlpha = if (isAnimating && r == expandedWeekRow) {
                    (sin(tProgress * PI) * 18.0).roundToInt()
                } else 0
                val bandAlpha = (baseBandAlpha * rowExpandFrac + animBoostAlpha).roundToInt().coerceIn(0, 56)
                chipPaint.color = (if (darkStyle) 0x00FFFFFF else 0x00000000) or (bandAlpha shl 24)
                val bandRect = RectF(insetX, cellTop + 1f * density, wPx - insetX, cellTop + rowH - 1f * density)
                canvas.drawRoundRect(bandRect, 10f * density, 10f * density, chipPaint)

                // 展開動畫期間帶有一抹細緻主題色微光邊框，定格後轉為柔和玻璃細線
                if (isAnimating && r == expandedWeekRow) {
                    val glowAlpha = (sin(tProgress * PI) * 125.0).roundToInt().coerceIn(30, 140)
                    linePaint.color = (accentArgb and 0x00FFFFFF) or (glowAlpha shl 24)
                    linePaint.strokeWidth = 1.4f * density
                    linePaint.style = Paint.Style.STROKE
                    canvas.drawRoundRect(bandRect, 10f * density, 10f * density, linePaint)
                    linePaint.style = Paint.Style.FILL
                    linePaint.strokeWidth = 1f
                } else {
                    val borderAlpha = ((if (darkStyle) 0x32 else 0x22) * rowExpandFrac).roundToInt().coerceIn(0, 64)
                    linePaint.color = (if (darkStyle) 0x00FFFFFF else 0x00000000) or (borderAlpha shl 24)
                    canvas.drawLine(4f * density, cellTop + 1f * density, wPx - 4f * density, cellTop + 1f * density, linePaint)
                    canvas.drawLine(4f * density, cellTop + rowH - 1f * density, wPx - 4f * density, cellTop + rowH - 1f * density, linePaint)
                }
                linePaint.color = gridLineColor
            }

            val maxChips = when {
                anyWeekAccordionMode -> 0
                rowH >= 74f * density -> 3
                rowH >= 54f * density -> 2
                rowH >= 38f * density -> 1
                else -> 0
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
                val dayBaseline = cellTop + (if (anyWeekAccordionMode && rowExpandFrac < 0.3f) 13.5f else 14.5f) * density
                val lunar = if (showLunar) LunarCalendarEngine.getLunarInfo(d) else null

                if (isToday) {
                    if (!googleGridStyle) {
                        val pillW = (colW - 6f * density).coerceAtMost(32f * density)
                        val pillH = if (showLunar) 26f * density else 18f * density
                        chipPaint.color = accentArgb
                        canvas.drawRoundRect(
                            RectF(cx - pillW / 2f, cellTop + 2f * density, cx + pillW / 2f, cellTop + 2f * density + pillH),
                            7f * density,
                            7f * density,
                            chipPaint
                        )
                    } else {
                        canvas.drawRoundRect(
                            RectF(cellLeft + 3f * density, cellTop + 2f * density, cellLeft + colW - 3f * density, cellTop + rowH - 2f * density),
                            8f * density,
                            8f * density,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accentArgb and 0x00FFFFFF) or 0x2E000000 }
                        )
                    }
                }

                dayPaint.color = when {
                    isToday && !googleGridStyle -> 0xFFFFFFFF.toInt()
                    isToday -> accentArgb
                    else -> primaryText
                }
                dayPaint.alpha = if (isToday) 255 else alphaFactor
                canvas.drawText(d.dayOfMonth.toString(), cx, dayBaseline, dayPaint)

                var contentStartY = dayBaseline + 3f * density
                if (showLunar && lunar != null && rowH >= 24f * density) {
                    lunarPaint.color = when {
                        isToday && !googleGridStyle -> 0xFFEFEFEF.toInt()
                        lunar.isTaiwanHoliday || lunar.solarTerm != null -> accentArgb
                        else -> secondaryText
                    }
                    lunarPaint.alpha = if (isToday) 255 else alphaFactor
                    val lunarBaseline = dayBaseline + 9.2f * density
                    canvas.drawText(lunar.shortDisplay.take(4), cx, lunarBaseline, lunarPaint)
                    contentStartY = lunarBaseline + 3.5f * density
                }

                val dayEvents = byDate[d] ?: emptyList()

                if (rowExpandFrac >= 0.22f) {
                    // 類 vivo 單週展開模式（含展開滑入與收合淡出微動畫裁切區！）
                    val cardAreaTop = maxOf(contentStartY + 1f * density, cellTop + unitH * 0.92f)
                    val cardAreaBottom = cellTop + rowH - 3.5f * density
                    if (cardAreaBottom > cardAreaTop + 6f * density) {
                        val cardOpacity = ((rowExpandFrac - 0.18f) / 0.82f).coerceIn(0f, 1f)
                        val slideOffsetY = -(1f - rowExpandFrac) * 7f * density

                        canvas.save()
                        canvas.clipRect(cellLeft, cardAreaTop, cellLeft + colW, cardAreaBottom)

                        var yCursor = cardAreaTop + slideOffsetY
                        val lineH = 9.8f * density
                        val cardGap = 2.5f * density
                        val padX = 2.2f * density
                        val innerMaxW = (colW - padX * 2f - 5f * density).coerceAtLeast(12f * density)
                        var drawnCount = 0

                        for (ev in dayEvents) {
                            val remainH = cardAreaBottom - yCursor
                            if (remainH < lineH + 4f * density) break
                            val label = (if (ev.isTask) "○" else "") + ev.title
                            val maxLinesForCard = when {
                                dayEvents.size <= 2 && remainH >= lineH * 3 + 8f * density -> 3
                                remainH >= lineH * 2 + 6f * density -> 2
                                else -> 1
                            }
                            val lines = wrapTextToLines(label, cardTitlePaint, innerMaxW, maxLinesForCard)
                            val cardH = lines.size * lineH + 5f * density
                            if (yCursor + cardH > cardAreaBottom + 4f * density && drawnCount > 0) break

                            val bgBaseAlpha = if (darkStyle) 0x8A else 0x3D
                            val bgScaledAlpha = (bgBaseAlpha * cardOpacity).roundToInt().coerceIn(0, 255)
                            chipPaint.color = (ev.colorArgb and 0x00FFFFFF) or (bgScaledAlpha shl 24)
                            val cardRect = RectF(cellLeft + padX, yCursor, cellLeft + colW - padX, yCursor + cardH)
                            canvas.drawRoundRect(cardRect, 4.5f * density, 4.5f * density, chipPaint)

                            chipPaint.color = ev.colorArgb
                            chipPaint.alpha = (255 * cardOpacity).roundToInt().coerceIn(0, 255)
                            canvas.drawRoundRect(
                                RectF(cellLeft + padX, yCursor + 1.5f * density, cellLeft + padX + 2.2f * density, yCursor + cardH - 1.5f * density),
                                1.5f * density,
                                1.5f * density,
                                chipPaint
                            )
                            chipPaint.alpha = 255

                            val textBaseAlpha = if (inMonth) 255 else 175
                            cardTitlePaint.alpha = (textBaseAlpha * cardOpacity).roundToInt().coerceIn(0, 255)
                            lines.forEachIndexed { lIdx, lineText ->
                                canvas.drawText(
                                    lineText,
                                    cellLeft + padX + 4f * density,
                                    yCursor + 2.2f * density + (lIdx + 1) * lineH - 1.5f * density,
                                    cardTitlePaint
                                )
                            }
                            yCursor += cardH + cardGap
                            drawnCount++
                        }

                        val hiddenMore = dayEvents.size - drawnCount
                        if (hiddenMore > 0 && cardAreaBottom - yCursor >= 8f * density) {
                            lunarPaint.color = secondaryText
                            lunarPaint.alpha = (220 * cardOpacity).roundToInt().coerceIn(0, 255)
                            canvas.drawText("+$hiddenMore", cx, cardAreaBottom - 1f * density, lunarPaint)
                        }
                        canvas.restore()
                    }
                } else if (anyWeekAccordionMode) {
                    // 其他收合週列：以精巧彩色圓點提示當天有行程，點擊該週即可展開！
                    if (dayEvents.isNotEmpty() && contentStartY + 3f * density <= cellTop + rowH) {
                        val dots = dayEvents.take(3)
                        val dotR = 1.7f * density
                        val dotGap = 4.6f * density
                        val totalDotW = (dots.size - 1) * dotGap
                        dots.forEachIndexed { idx, ev ->
                            chipPaint.color = ev.colorArgb
                            chipPaint.alpha = alphaFactor
                            canvas.drawCircle(cx - totalDotW / 2f + idx * dotGap, contentStartY + 1.2f * density, dotR, chipPaint)
                        }
                        chipPaint.alpha = 255
                    }
                } else {
                    // 一般全月等高檢視：顯示單行行程標籤
                    val chipH = 10.5f * density
                    dayEvents.take(maxChips).forEachIndexed { idx, ev ->
                        val top = contentStartY + idx * (chipH + 1.5f * density)
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
        }
        return bmp
    }

    private fun wrapTextToLines(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isEmpty() || maxLines <= 0) return emptyList()
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length && lines.size < maxLines) {
            val isLastLine = lines.size == maxLines - 1
            var end = start + 1
            while (end <= text.length && paint.measureText(text, start, end) <= maxWidth) {
                end++
            }
            val fitEnd = (end - 1).coerceAtLeast(start + 1)
            if (isLastLine && fitEnd < text.length) {
                var ellEnd = fitEnd
                while (ellEnd > start + 1 && paint.measureText(text.substring(start, ellEnd) + "…") > maxWidth) {
                    ellEnd--
                }
                lines.add(text.substring(start, ellEnd) + "…")
                break
            } else {
                lines.add(text.substring(start, fitEnd))
                start = fitEnd
            }
        }
        return lines
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
        setPackage(context.packageName)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return PendingIntent.getBroadcast(
        context,
        widgetId * 100 + reqOffset,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private val MONTH_CELL_VIEW_IDS = intArrayOf(
    R.id.widget_cell_00, R.id.widget_cell_01, R.id.widget_cell_02, R.id.widget_cell_03, R.id.widget_cell_04, R.id.widget_cell_05, R.id.widget_cell_06,
    R.id.widget_cell_07, R.id.widget_cell_08, R.id.widget_cell_09, R.id.widget_cell_10, R.id.widget_cell_11, R.id.widget_cell_12, R.id.widget_cell_13,
    R.id.widget_cell_14, R.id.widget_cell_15, R.id.widget_cell_16, R.id.widget_cell_17, R.id.widget_cell_18, R.id.widget_cell_19, R.id.widget_cell_20,
    R.id.widget_cell_21, R.id.widget_cell_22, R.id.widget_cell_23, R.id.widget_cell_24, R.id.widget_cell_25, R.id.widget_cell_26, R.id.widget_cell_27,
    R.id.widget_cell_28, R.id.widget_cell_29, R.id.widget_cell_30, R.id.widget_cell_31, R.id.widget_cell_32, R.id.widget_cell_33, R.id.widget_cell_34,
    R.id.widget_cell_35, R.id.widget_cell_36, R.id.widget_cell_37, R.id.widget_cell_38, R.id.widget_cell_39, R.id.widget_cell_40, R.id.widget_cell_41
)

private val MONTH_ROW_VIEW_IDS = intArrayOf(
    R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2,
    R.id.widget_row_3, R.id.widget_row_4, R.id.widget_row_5
)

private val MONTH_ROW_EXPAND_VIEW_IDS = intArrayOf(
    R.id.widget_row_expand_0, R.id.widget_row_expand_1, R.id.widget_row_expand_2,
    R.id.widget_row_expand_3, R.id.widget_row_expand_4, R.id.widget_row_expand_5
)

private fun bindVivoAccordionWeekTapRows(
    context: Context,
    views: RemoteViews,
    widgetId: Int,
    numWeeks: Int,
    expandedWeekRow: Int
) {
    for (r in 0 until 6) {
        val rowVisible = r < numWeeks
        views.setViewVisibility(MONTH_ROW_VIEW_IDS[r], if (rowVisible) android.view.View.VISIBLE else android.view.View.GONE)
        val expandVisible = rowVisible && r == expandedWeekRow
        views.setViewVisibility(MONTH_ROW_EXPAND_VIEW_IDS[r], if (expandVisible) android.view.View.VISIBLE else android.view.View.GONE)

        if (rowVisible) {
            val rowTogglePi = buildWeekRowTogglePi(context, widgetId, r)
            for (c in 0 until 7) {
                views.setOnClickPendingIntent(MONTH_CELL_VIEW_IDS[r * 7 + c], rowTogglePi)
            }
            if (expandVisible) {
                views.setOnClickPendingIntent(MONTH_ROW_EXPAND_VIEW_IDS[r], rowTogglePi)
            }
        }
    }
}

private fun buildWeekRowTogglePi(context: Context, widgetId: Int, weekRow: Int): PendingIntent {
    val intent = Intent(context, VivoMonthWidgetProvider::class.java).apply {
        action = WidgetRefreshHelper.ACTION_TOGGLE_WEEK_ROW
        setPackage(context.packageName)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        putExtra(WidgetRefreshHelper.EXTRA_WEEK_ROW, weekRow)
    }
    return PendingIntent.getBroadcast(
        context,
        widgetId * 100 + 40 + weekRow,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun bindMonthTapCells(
    context: Context,
    views: RemoteViews,
    widgetId: Int,
    yearMonth: YearMonth,
    startMonday: Boolean
) {
    for (r in 0 until 6) {
        views.setViewVisibility(MONTH_ROW_VIEW_IDS[r], android.view.View.VISIBLE)
        views.setViewVisibility(MONTH_ROW_EXPAND_VIEW_IDS[r], android.view.View.GONE)
    }
    val firstOfMonth = yearMonth.atDay(1)
    val shift = if (startMonday) firstOfMonth.dayOfWeek.value - 1 else firstOfMonth.dayOfWeek.value % 7
    val startDate = firstOfMonth.minusDays(shift.toLong())
    for (idx in 0 until 42) {
        val cellDate = startDate.plusDays(idx.toLong())
        views.setOnClickPendingIntent(
            MONTH_CELL_VIEW_IDS[idx],
            buildOpenDatePi(context, widgetId * 1000 + idx + 100, cellDate.toEpochDay())
        )
    }
}

private fun buildOpenDatePi(context: Context, reqCode: Int, epochDay: Long): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = "${WidgetRefreshHelper.ACTION_CLICK_ITEM}.$epochDay"
        setPackage(context.packageName)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, epochDay)
        putExtra(WidgetRefreshHelper.EXTRA_OPEN_DAY_VIEW, true)
    }
    return buildSafeActivityPi(context, reqCode, intent, mutable = false)
}

private fun buildMainAppPi(context: Context, reqCode: Int, quickAdd: Boolean): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = if (quickAdd) "com.dx.calendar.widget.OPEN_QUICK_ADD" else "com.dx.calendar.widget.OPEN_MAIN"
        setPackage(context.packageName)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        putExtra(WidgetRefreshHelper.EXTRA_QUICK_ADD, quickAdd)
    }
    return buildSafeActivityPi(context, reqCode, intent, mutable = false)
}
