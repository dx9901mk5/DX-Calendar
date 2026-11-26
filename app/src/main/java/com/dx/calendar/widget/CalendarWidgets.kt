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
        globalOpacity: Float,
        followSystemDark: Boolean = false
    ): Pair<Boolean, Float> {
        val sysNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val baseDark = if (followSystemDark) sysNight else globalDark
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return baseDark to globalOpacity.coerceIn(0f, 1f)
        }
        val sp = context.getSharedPreferences(PREF_WIDGET_STATE, Context.MODE_PRIVATE)
        return if (sp.getBoolean("has_custom_style_$appWidgetId", false)) {
            val d = sp.getBoolean("custom_dark_$appWidgetId", baseDark)
            val o = sp.getFloat("custom_opacity_$appWidgetId", globalOpacity).coerceIn(0f, 1f)
            d to o
        } else {
            baseDark to globalOpacity.coerceIn(0f, 1f)
        }
    }

    fun refreshAllWidgets(context: Context) {
        runCatching {
            val appContext = context.applicationContext ?: context
            val mgr = AppWidgetManager.getInstance(appContext)

            // 1. 刷新 DX 月曆小工具
            val vivoMonthIds = mgr.getAppWidgetIds(ComponentName(appContext, VivoMonthWidgetProvider::class.java))
            if (vivoMonthIds.isNotEmpty()) {
                VivoMonthWidgetProvider.updateWidgets(appContext, mgr, vivoMonthIds)
            }

            // 2. 刷新 DX 時間表可捲動小工具
            val googleSchedIds = mgr.getAppWidgetIds(ComponentName(appContext, GoogleScheduleWidgetProvider::class.java))
            if (googleSchedIds.isNotEmpty()) {
                GoogleScheduleWidgetProvider.updateWidgets(appContext, mgr, googleSchedIds)
                runCatching { mgr.notifyAppWidgetViewDataChanged(googleSchedIds, R.id.widget_list_view) }
            }

            // 3. 刷新 DX 下一行程與天氣橫條小工具
            val nextEventIds = mgr.getAppWidgetIds(ComponentName(appContext, NextEventWeatherWidgetProvider::class.java))
            if (nextEventIds.isNotEmpty()) {
                NextEventWeatherWidgetProvider.updateWidgets(appContext, mgr, nextEventIds)
            }

            // 4. 刷新 DX 倒數日與天氣小工具
            val countdownIds = mgr.getAppWidgetIds(ComponentName(appContext, CountdownWeatherWidgetProvider::class.java))
            if (countdownIds.isNotEmpty()) {
                CountdownWeatherWidgetProvider.updateWidgets(appContext, mgr, countdownIds)
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
        // 預渲染 60fps 手風琴展開/收合平滑緩動中間幀（10 個 cubic-out 過渡幀 + 終幀，總時長約 160ms，絲滑高幀率不卡頓）
        private val EXPAND_ANIM_EASED_STEPS = floatArrayOf(
            0.14f, 0.28f, 0.42f, 0.55f, 0.67f, 0.77f, 0.85f, 0.91f, 0.96f, 0.99f
        )
        private const val EXPAND_ANIM_FRAME_INTERVAL_MS = 16L

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
                            appCtx, widgetId, settings.widgetDarkStyle, settings.widgetOpacity, settings.widgetFollowSystemDark
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

                        val (minW, minH) = readSafeWidgetDp(appCtx, mgr, widgetId, defaultW = 280, defaultH = 280)
                        val canvasHeightDp = (minH - 42).coerceAtLeast(100)
                        val accentArgb = settings.brandStyle.primaryHex.toInt()

                        if (resolvedFrom != resolvedTo) {
                            runCatching {
                                // 先於背景一次預渲染所有輕量動畫幀，再以精準 16ms (~60fps) 節拍連續推送，消除幀間渲染與 IPC 延遲！
                                val preRenderedFrames = EXPAND_ANIM_EASED_STEPS.map { easedStep ->
                                    MonthCanvasRenderer.renderMonthBitmap(
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
                                }
                                var nextFrameDeadline = android.os.SystemClock.elapsedRealtime()
                                for (frameBmp in preRenderedFrames) {
                                    val partialViews = RemoteViews(appCtx.packageName, R.layout.widget_month_canvas).apply {
                                        setImageViewBitmap(R.id.widget_canvas_img, frameBmp)
                                    }
                                    safePartiallyUpdateWidget(mgr, widgetId, partialViews)
                                    nextFrameDeadline += EXPAND_ANIM_FRAME_INTERVAL_MS
                                    val waitMs = nextFrameDeadline - android.os.SystemClock.elapsedRealtime()
                                    if (waitMs > 0L) delay(waitMs)
                                }
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
                                    appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity, settings.widgetFollowSystemDark
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

                                val (minW, minH) = readSafeWidgetDp(appCtx, mgr, id, defaultW = 280, defaultH = 280)
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
            applyMonthContainerThemeAndOpacity(views, darkStyle, opacity, accentArgb)

            val titleText = "%d/%02d".format(ym.year, ym.monthValue)
            val titleSpan = android.text.SpannableString(titleText).apply {
                val slashIdx = titleText.indexOf('/')
                if (slashIdx > 0) {
                    val slashColor = if (darkStyle) 0x88FFFFFF.toInt() else 0x88000000.toInt()
                    setSpan(
                        android.text.style.ForegroundColorSpan(slashColor),
                        slashIdx,
                        slashIdx + 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        android.text.style.ForegroundColorSpan(accentArgb or 0xFF000000.toInt()),
                        slashIdx + 1,
                        titleText.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            views.setTextViewText(R.id.widget_month_title, titleSpan)
            // 完全對齊 vivo 簡潔大字頂欄：隱藏多餘的「全月」與「＋」鈕，非當月才顯示「今」快捷返回鈕
            views.setViewVisibility(R.id.btn_widget_mode, android.view.View.GONE)
            views.setViewVisibility(R.id.btn_widget_add, android.view.View.GONE)
            val isNotCurrentMonth = ym != YearMonth.now()
            views.setViewVisibility(
                R.id.btn_widget_today,
                if (isNotCurrentMonth) android.view.View.VISIBLE else android.view.View.GONE
            )
            views.setTextViewText(R.id.btn_widget_today, "今")
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
            bindVivoAccordionWeekTapRows(
                context = context,
                views = views,
                widgetId = id,
                yearMonth = ym,
                startMonday = startMonday,
                items = items,
                numWeeks = numWeeks,
                expandedWeekRow = expandedRow
            )

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
                                    appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity, settings.widgetFollowSystemDark
                                )
                                val offset = WidgetRefreshHelper.getMonthOffset(appCtx, id)
                                val ym = YearMonth.now().plusMonths(offset.toLong())
                                val items = repo.buildItemsForRange(
                                    ym.atDay(1).minusDays(7), ym.atEndOfMonth().plusDays(14),
                                    cats, events, tasks, checkIns, emptyList(), settings, isPrivacyUnlocked = false
                                ).filter { it.showInMonthGrid && it.showInAgendaAndWidget }

                                val (minW, minH) = readSafeWidgetDp(appCtx, mgr, id, defaultW = 280, defaultH = 280)

                                val googleAccent = if (darkStyle) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
                                val bmp = MonthCanvasRenderer.renderMonthBitmap(
                                    context = appCtx,
                                    yearMonth = ym,
                                    widthDp = minW,
                                    heightDp = (minH - 42).coerceAtLeast(100),
                                    items = items,
                                    startMonday = settings.startWeekOnMonday,
                                    showLunar = settings.showLunar,
                                    darkStyle = darkStyle,
                                    accentArgb = googleAccent,
                                    googleGridStyle = true
                                )

                                val views = RemoteViews(appCtx.packageName, R.layout.widget_month_canvas)
                                applyMonthContainerThemeAndOpacity(views, darkStyle, opacity, googleAccent)

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
                            appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity, settings.widgetFollowSystemDark
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_scrollable_list)
                        applyListContainerThemeAndOpacity(views, darkStyle, opacity, settings.brandStyle.primaryHex.toInt())

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
                            appCtx, id, settings.widgetDarkStyle, settings.widgetOpacity, settings.widgetFollowSystemDark
                        )
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_scrollable_list)
                        applyListContainerThemeAndOpacity(views, darkStyle, opacity, settings.brandStyle.primaryHex.toInt())

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
                    context, appWidgetId, settings.widgetDarkStyle, settings.widgetOpacity, settings.widgetFollowSystemDark
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
    context: Context,
    mgr: AppWidgetManager,
    widgetId: Int,
    defaultW: Int,
    defaultH: Int
): Pair<Int, Int> {
    val opts = runCatching { mgr.getAppWidgetOptions(widgetId) }.getOrNull()
    val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Android 12+ 優先讀取桌面精準回報的實際小工具長寬 (OPTION_APPWIDGET_SIZES)，消除 fitXY 非等比拉伸造成的字體模糊！
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && opts != null) {
        val sizes = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                opts.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
            } else {
                @Suppress("DEPRECATION")
                opts.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            }
        }.getOrNull()
        if (!sizes.isNullOrEmpty()) {
            val picked = if (isLandscape) {
                sizes.maxByOrNull { it.width / it.height.coerceAtLeast(1f) }
            } else {
                sizes.maxByOrNull { it.height / it.width.coerceAtLeast(1f) }
            } ?: sizes.first()
            val w = picked.width.roundToInt().coerceIn(150, 760)
            val h = picked.height.roundToInt().coerceIn(150, 820)
            return w to h
        }
    }

    // 標準回退：直向桌面實際寬高為 MIN_WIDTH × MAX_HEIGHT；橫向桌面實際寬高為 MAX_WIDTH × MIN_HEIGHT
    val rawW = if (isLandscape) {
        opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, defaultW) ?: defaultW
    } else {
        opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultW) ?: defaultW
    }
    val rawH = if (isLandscape) {
        opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultH) ?: defaultH
    } else {
        opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defaultH)?.takeIf { it > 0 }
            ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultH)
            ?: defaultH
    }
    return rawW.coerceIn(150, 760) to rawH.coerceIn(150, 820)
}

private fun safeUpdateWidget(mgr: AppWidgetManager, widgetId: Int, views: RemoteViews) {
    runCatching { mgr.updateAppWidget(widgetId, views) }
}

private fun safePartiallyUpdateWidget(mgr: AppWidgetManager, widgetId: Int, views: RemoteViews) {
    runCatching { mgr.partiallyUpdateAppWidget(widgetId, views) }
}

private fun applyMonthContainerThemeAndOpacity(
    views: RemoteViews,
    darkStyle: Boolean,
    opacity: Float,
    accentArgb: Int
) {
    val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    views.setImageViewResource(
        R.id.widget_bg_layer,
        if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
    )
    views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

    val titleColor = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF101014.toInt()
    val iconMutedColor = if (darkStyle) 0xFFE6E6F0.toInt() else 0xFF282832.toInt()
    val modeChipTextColor = if (darkStyle) 0xFFF2F2FA.toInt() else 0xFF1A1A22.toInt()
    val modeChipBgColor = if (darkStyle) 0x26FFFFFF else 0x16000000
    val safeAccent = accentArgb or 0xFF000000.toInt()
    val todayChipBg = (safeAccent and 0x00FFFFFF) or 0x28000000

    views.setTextColor(R.id.widget_month_title, titleColor)
    views.setTextColor(R.id.btn_widget_prev, iconMutedColor)
    views.setTextColor(R.id.btn_widget_next, iconMutedColor)
    views.setTextColor(R.id.btn_widget_config, iconMutedColor)
    views.setTextColor(R.id.btn_widget_mode, modeChipTextColor)
    views.setInt(R.id.btn_widget_mode, "setBackgroundColor", modeChipBgColor)
    views.setTextColor(R.id.btn_widget_today, safeAccent)
    views.setInt(R.id.btn_widget_today, "setBackgroundColor", todayChipBg)
    views.setTextColor(R.id.btn_widget_add, safeAccent)
}

private fun applyListContainerThemeAndOpacity(
    views: RemoteViews,
    darkStyle: Boolean,
    opacity: Float,
    accentArgb: Int
) {
    val alpha255 = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    views.setImageViewResource(
        R.id.widget_bg_layer,
        if (darkStyle) R.drawable.bg_widget_dark_solid else R.drawable.bg_widget_light_solid
    )
    views.setInt(R.id.widget_bg_layer, "setImageAlpha", alpha255)

    val titleColor = if (darkStyle) 0xFFF5F5F7.toInt() else 0xFF161618.toInt()
    val iconMutedColor = if (darkStyle) 0xFFA8A8B4.toInt() else 0xFF5A5A66.toInt()
    val emptyColor = if (darkStyle) 0xFF8E8E96.toInt() else 0xFF64646E.toInt()
    val safeAccent = accentArgb or 0xFF000000.toInt()

    views.setTextColor(R.id.widget_list_header_title, titleColor)
    views.setTextColor(R.id.btn_widget_list_config, iconMutedColor)
    views.setTextColor(R.id.widget_empty_text, emptyColor)
    views.setTextColor(R.id.widget_list_badge, safeAccent)
    views.setTextColor(R.id.btn_widget_list_add, safeAccent)
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

    // 升級為旗艦 2K/QHD+ 與摺疊機 1:1 原生高解析像素預算（2,400,000 px ≈ 9.6MB，並透過 computeScreenSafePixelBudget
    // 針對小螢幕自動限制在螢幕總像素 85% 內，確保字體 1:1 銳利不縮放模糊且絕不超限！）
    const val DEFAULT_MAX_PIXELS = 2_400_000
    const val ANIM_FRAME_MAX_PIXELS = 160_000
    const val MAX_SIDE_PX = 2200

    internal fun computeScreenSafePixelBudget(screenWidthPx: Int, screenHeightPx: Int, requestedMaxPixels: Int): Int {
        val safeReq = requestedMaxPixels.coerceAtLeast(60_000).toLong()
        val screenCap = (screenWidthPx.toLong().coerceAtLeast(320L) * screenHeightPx.toLong().coerceAtLeast(320L)) * 85L / 100L
        return minOf(safeReq, screenCap.coerceAtLeast(60_000L)).toInt()
    }

    internal fun computeSafeCanvasDimensions(
        widthDp: Int,
        heightDp: Int,
        density: Float,
        maxPixels: Int = DEFAULT_MAX_PIXELS,
        maxSidePx: Int = MAX_SIDE_PX
    ): Pair<Int, Int> {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f }?.coerceIn(1.5f, 4.0f) ?: 3.0f
        val safeWdp = widthDp.coerceIn(120, 760)
        val safeHdp = heightDp.coerceIn(90, 820)

        var w = (safeWdp * safeDensity).coerceAtLeast(240f)
        var h = (safeHdp * safeDensity).coerceAtLeast(180f)

        val longest = maxOf(w, h)
        if (longest > maxSidePx) {
            val sideScale = maxSidePx / longest
            w *= sideScale
            h *= sideScale
        }

        val area = w * h
        val safeBudget = maxPixels.coerceAtLeast(120_000).toFloat()
        if (area > safeBudget) {
            val areaScale = sqrt(safeBudget / area)
            w *= areaScale
            h *= areaScale
        }

        return w.roundToInt().coerceAtLeast(200) to h.roundToInt().coerceAtLeast(150)
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
        val dm = context.resources.displayMetrics
        val rawDensity = dm.density
        // 動態結合當前螢幕解析度計算絕對安全上限（修正動畫輕量幀 maxPixels < 650_000 時舊 coerceIn 空區間崩潰！）
        val effectiveMaxPixels = computeScreenSafePixelBudget(dm.widthPixels, dm.heightPixels, maxPixels)
        val (wPx, hPx) = computeSafeCanvasDimensions(
            widthDp = widthDp,
            heightDp = heightDp,
            density = rawDensity,
            maxPixels = effectiveMaxPixels
        )
        val density = (wPx.toFloat() / widthDp.coerceIn(120, 760).toFloat()).coerceIn(1.0f, 4.0f)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 背景可高度透明，但所有文字維持 100% 實心高對比＋微距陰影，避免與桌布融在一起顯得過透或模糊
        val primaryText = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF101014.toInt()
        val secondaryText = if (darkStyle) 0xFFE6E6F0.toInt() else 0xFF2A2A34.toInt()
        val textShadowColor = if (darkStyle) 0xB3000000.toInt() else 0x66FFFFFF.toInt()
        val gridLineColor = if (darkStyle) 0x26FFFFFF else 0x22000000

        val textFlags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.DITHER_FLAG
        val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val headerPaint = Paint(textFlags).apply {
            color = secondaryText
            textSize = 11.8f * density
            textAlign = Paint.Align.CENTER
            typeface = mediumTypeface
            setShadowLayer(1.6f * density, 0f, 0.8f * density, textShadowColor)
        }
        val dayPaint = Paint(textFlags).apply {
            textSize = (if (hPx < 420) 13.6f else 15.8f) * density
            textAlign = Paint.Align.CENTER
            typeface = mediumTypeface
            setShadowLayer(1.8f * density, 0f, 0.9f * density, textShadowColor)
        }
        val lunarPaint = Paint(textFlags).apply {
            textSize = 9.9f * density
            textAlign = Paint.Align.CENTER
            typeface = mediumTypeface
            setShadowLayer(1.4f * density, 0f, 0.7f * density, textShadowColor)
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val chipTextPaint = Paint(textFlags).apply {
            color = primaryText
            textSize = 9.8f * density
            typeface = mediumTypeface
            setShadowLayer(1.2f * density, 0f, 0.6f * density, textShadowColor)
        }
        val cardTitlePaint = Paint(textFlags).apply {
            color = if (darkStyle) 0xFFFFFFFF.toInt() else 0xFF101014.toInt()
            textSize = 10.8f * density
            typeface = mediumTypeface
            setShadowLayer(1.2f * density, 0f, 0.6f * density, textShadowColor)
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
        val weatherMap = com.dx.calendar.data.weather.WeatherForecastManager.loadCachedForecast(context)
        val weatherPaint = Paint(textFlags).apply {
            textSize = 9.0f * density
            textAlign = Paint.Align.RIGHT
        }

        var curTop = gridTop
        for (r in 0 until safeWeeks) {
            val rWeight = rowWeights[r]
            val rowExpandFrac = ((rWeight - 1f) / 3f).coerceIn(0f, 1f) // 0f = 完全折疊, 1f = 完全展開
            val rowH = unitH * rWeight
            val cellTop = curTop
            curTop += rowH

            if (googleGridStyle) {
                canvas.drawLine(0f, cellTop, wPx.toFloat(), cellTop, linePaint)
            } else if (rowExpandFrac > 0.04f && isAnimating && r == expandedWeekRow) {
                // 僅在展開瞬間帶有一抹細緻主題色微光邊框，定格後完全通透自然融入背景（對齊 vivo 簡潔無灰框排版！）
                val insetX = (1f - rowExpandFrac) * 6f * density + 1f * density
                val bandRect = RectF(insetX, cellTop + 1f * density, wPx - insetX, cellTop + rowH - 1f * density)
                val glowAlpha = (sin(tProgress * PI) * 110.0).roundToInt().coerceIn(20, 120)
                linePaint.color = (accentArgb and 0x00FFFFFF) or (glowAlpha shl 24)
                linePaint.strokeWidth = 1.3f * density
                linePaint.style = Paint.Style.STROKE
                canvas.drawRoundRect(bandRect, 10f * density, 10f * density, linePaint)
                linePaint.style = Paint.Style.FILL
                linePaint.strokeWidth = 1f
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
                val alphaFactor = if (inMonth) 255 else 155

                val cx = cellLeft + colW / 2f
                val dayBaseline = cellTop + (if (anyWeekAccordionMode && rowExpandFrac < 0.3f) 14.8f else 15.8f) * density
                val lunar = if (showLunar) LunarCalendarEngine.getLunarInfo(d) else null

                // 月曆小工具同步繪製今日起 7 天天氣圖示（右上角）
                weatherMap[d]?.let { wInfo ->
                    canvas.drawText(
                        wInfo.icon,
                        cellLeft + colW - 2.0f * density,
                        dayBaseline - 1.5f * density,
                        weatherPaint
                    )
                }

                if (isToday) {
                    if (!googleGridStyle) {
                        val pillW = (colW - 4f * density).coerceAtMost(38f * density)
                        val pillH = if (showLunar) 30f * density else 20f * density
                        chipPaint.color = accentArgb
                        canvas.drawRoundRect(
                            RectF(cx - pillW / 2f, cellTop + 1.5f * density, cx + pillW / 2f, cellTop + 1.5f * density + pillH),
                            8f * density,
                            8f * density,
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

                dayPaint.typeface = Typeface.create(Typeface.DEFAULT, if (isToday) Typeface.BOLD else Typeface.NORMAL)
                dayPaint.color = when {
                    isToday && !googleGridStyle -> 0xFFFFFFFF.toInt()
                    isToday -> accentArgb
                    else -> primaryText
                }
                dayPaint.alpha = if (isToday) 255 else alphaFactor
                canvas.drawText(d.dayOfMonth.toString(), cx, dayBaseline, dayPaint)

                var contentStartY = dayBaseline + 3.5f * density
                if (showLunar && lunar != null && rowH >= 24f * density) {
                    lunarPaint.color = when {
                        isToday && !googleGridStyle -> 0xFFF5F5F7.toInt()
                        lunar.isTaiwanHoliday || lunar.solarTerm != null -> accentArgb
                        else -> secondaryText
                    }
                    lunarPaint.alpha = if (isToday) 255 else alphaFactor
                    val lunarBaseline = dayBaseline + 10.6f * density
                    canvas.drawText(lunar.shortDisplay.take(4), cx, lunarBaseline, lunarPaint)
                    contentStartY = lunarBaseline + 4.2f * density
                }

                val dayEvents = byDate[d] ?: emptyList()

                if (rowExpandFrac >= 0.22f) {
                    // 完全對齊 vivo 展開週卡片排版：大字清晰靠左對齊、無左側冗餘色條、依行數動態自適應高度！
                    val cardAreaTop = maxOf(contentStartY + 2f * density, cellTop + unitH * 0.86f)
                    val cardAreaBottom = cellTop + rowH - 3f * density
                    if (cardAreaBottom > cardAreaTop + 6f * density) {
                        val cardOpacity = ((rowExpandFrac - 0.18f) / 0.82f).coerceIn(0f, 1f)
                        val slideOffsetY = -(1f - rowExpandFrac) * 7f * density

                        canvas.save()
                        canvas.clipRect(cellLeft, cardAreaTop, cellLeft + colW, cardAreaBottom)

                        var yCursor = cardAreaTop + slideOffsetY
                        val lineH = 12.2f * density
                        val cardGap = 2.6f * density
                        val padX = 1.6f * density
                        val textPadX = 3.2f * density
                        val innerMaxW = (colW - padX * 2f - textPadX * 2f).coerceAtLeast(14f * density)
                        var drawnCount = 0

                        for (ev in dayEvents) {
                            val remainH = cardAreaBottom - yCursor
                            if (remainH < lineH + 5f * density) break
                            val label = (if (ev.isTask) "○" else "") + ev.title
                            val maxLinesForCard = when {
                                remainH >= lineH * 3 + 8f * density -> 3
                                remainH >= lineH * 2 + 6f * density -> 2
                                else -> 1
                            }
                            val lines = wrapTextToLines(label, cardTitlePaint, innerMaxW, maxLinesForCard)
                            val cardH = lines.size * lineH + 6.4f * density
                            if (yCursor + cardH > cardAreaBottom + 4f * density && drawnCount > 0) break

                            val bgBaseAlpha = if (darkStyle) 0xD8 else 0x8C
                            val bgScaledAlpha = (bgBaseAlpha * cardOpacity).roundToInt().coerceIn(0, 255)
                            chipPaint.color = (ev.colorArgb and 0x00FFFFFF) or (bgScaledAlpha shl 24)
                            val cardRect = RectF(cellLeft + padX, yCursor, cellLeft + colW - padX, yCursor + cardH)
                            canvas.drawRoundRect(cardRect, 6f * density, 6f * density, chipPaint)

                            val textBaseAlpha = if (inMonth) 255 else 215
                            cardTitlePaint.alpha = (textBaseAlpha * cardOpacity).roundToInt().coerceIn(0, 255)
                            lines.forEachIndexed { lIdx, lineText ->
                                canvas.drawText(
                                    lineText,
                                    cellLeft + padX + textPadX,
                                    yCursor + 3.0f * density + (lIdx + 1) * lineH - 2.0f * density,
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
                    // 其他收合週列：完全對齊 vivo 樣式，在農曆下方繪製精簡彩色短橫條！
                    if (dayEvents.isNotEmpty() && contentStartY + 3f * density <= cellTop + rowH) {
                        val bars = dayEvents.take(3)
                        val barH = 2.4f * density
                        val barGap = 2.0f * density
                        val totalBarsW = (colW * 0.46f).coerceAtMost(20f * density)
                        val segW = ((totalBarsW - (bars.size - 1) * barGap) / bars.size).coerceAtLeast(3f * density)
                        val startX = cx - totalBarsW / 2f
                        bars.forEachIndexed { idx, ev ->
                            chipPaint.color = ev.colorArgb
                            chipPaint.alpha = alphaFactor
                            val left = startX + idx * (segW + barGap)
                            canvas.drawRoundRect(
                                RectF(left, contentStartY, left + segW, contentStartY + barH),
                                barH / 2f,
                                barH / 2f,
                                chipPaint
                            )
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
    yearMonth: YearMonth,
    startMonday: Boolean,
    items: List<UnifiedCalendarItem>,
    numWeeks: Int,
    expandedWeekRow: Int
) {
    val firstOfMonth = yearMonth.atDay(1)
    val shift = if (startMonday) firstOfMonth.dayOfWeek.value - 1 else firstOfMonth.dayOfWeek.value % 7
    val startDate = firstOfMonth.minusDays(shift.toLong())
    val itemsByDate = items.groupBy { it.date }

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
                // 展開週次的 7 天 × 3 個事件槽：點擊事件直接開啟 App 並跳至該當下事件詳情！
                for (c in 0 until 7) {
                    val cellDate = startDate.plusDays((r * 7 + c).toLong())
                    val dayEvents = itemsByDate[cellDate] ?: emptyList()
                    for (s in 0 until 3) {
                        val slotId = context.resources.getIdentifier(
                            "widget_exp_r${r}_c${c}_s${s}",
                            "id",
                            context.packageName
                        )
                        if (slotId == 0) continue
                        val reqCode = widgetId * 1000 + 300 + r * 21 + c * 3 + s
                        val ev = dayEvents.getOrNull(s)
                        val pi = if (ev != null) {
                            buildOpenEventPi(
                                context = context,
                                reqCode = reqCode,
                                epochDay = cellDate.toEpochDay(),
                                rawId = ev.rawId,
                                isTask = ev.isTask
                            )
                        } else if (dayEvents.isNotEmpty() && s == 2) {
                            buildOpenDatePi(context, reqCode, cellDate.toEpochDay())
                        } else if (dayEvents.isEmpty()) {
                            rowTogglePi
                        } else {
                            buildOpenDatePi(context, reqCode, cellDate.toEpochDay())
                        }
                        views.setOnClickPendingIntent(slotId, pi)
                    }
                }
            }
        }
    }
}

private fun buildOpenEventPi(
    context: Context,
    reqCode: Int,
    epochDay: Long,
    rawId: Long,
    isTask: Boolean
): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = "${WidgetRefreshHelper.ACTION_CLICK_ITEM}.${epochDay}.${rawId}.${if (isTask) 1 else 0}"
        setPackage(context.packageName)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        putExtra(WidgetRefreshHelper.EXTRA_OPEN_EPOCH_DAY, epochDay)
        putExtra(WidgetRefreshHelper.EXTRA_OPEN_ITEM_RAW_ID, rawId)
        putExtra(WidgetRefreshHelper.EXTRA_CLICK_IS_TASK, isTask)
    }
    return buildSafeActivityPi(context, reqCode, intent, mutable = false)
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
