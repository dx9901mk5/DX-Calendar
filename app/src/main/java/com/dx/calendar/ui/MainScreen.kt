package com.dx.calendar.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.alarm.MultiCallAlarmScheduler
import com.dx.calendar.data.model.CalendarViewMode
import com.dx.calendar.data.model.MainSection
import com.dx.calendar.ui.adaptive.WindowFormFactor
import com.dx.calendar.ui.adaptive.computeAdaptiveLayoutSpec
import com.dx.calendar.ui.components.AgendaScheduleListView
import com.dx.calendar.ui.components.MonthGridView
import com.dx.calendar.ui.components.RightDetailPanel
import com.dx.calendar.ui.components.WeekOrDayTimelineView
import com.dx.calendar.ui.components.YearGridView
import com.dx.calendar.ui.screens.EventEditorScreen
import com.dx.calendar.ui.screens.PrivacyVaultView
import com.dx.calendar.ui.screens.RoutinesAndHiddenHubView
import com.dx.calendar.ui.screens.SettingsAndManageScreen
import com.dx.calendar.ui.screens.TasksCenterView
import com.dx.calendar.ui.theme.DXCalendarTheme
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.util.LunarCalendarEngine
import java.time.LocalDate

@Composable
fun DXCalendarRootApp(
    viewModel: CalendarViewModel,
    hasCalendarPermission: Boolean,
    onRequestCalendarPermission: () -> Unit,
    onLaunchGoogleAccountPicker: () -> Unit,
    onTriggerDualBiometric: () -> Unit,
    onPickWallpaperImage: () -> Unit,
    onPickIcsFile: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val checkIns by viewModel.checkIns.collectAsState()
    val externalOverrides by viewModel.externalOverrides.collectAsState()
    val titleHistory by viewModel.titleHistory.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentYm by viewModel.currentYearMonth.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val mainSection by viewModel.mainSection.collectAsState()
    val isPrivacyUnlocked by viewModel.isPrivacyUnlocked.collectAsState()
    val focusedItem by viewModel.focusedItem.collectAsState()
    val editorState by viewModel.editorState.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val systemCalendars by viewModel.systemCalendars.collectAsState()
    val isSyncingTasks by viewModel.isSyncingTasks.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val rangeItems by viewModel.visibleRangeItems.collectAsState()

    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    var showSearchDialog by remember { mutableStateOf(false) }
    var showAlmanacDiscoveryDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showCompactBottomDayPanel by remember { mutableStateOf(true) }

    DXCalendarTheme(
        brandStyle = settings.brandStyle,
        darkThemeMode = settings.darkThemeMode
    ) {
        val tokens = LocalBrandTokens.current

        // 全畫面覆蓋：活動/待辦/例行編輯器
        if (editorState.isOpen) {
            EventEditorScreen(
                initialDate = editorState.initialDate,
                editingEvent = editorState.editingEvent,
                editingTask = editorState.editingTask,
                presetRoutine = editorState.presetRoutine,
                presetPrivacy = editorState.presetPrivacy,
                presetTaskMode = editorState.presetTaskMode,
                routineDefaultHideFromMonth = settings.routineDefaultHideFromMonth,
                categories = categories,
                writableSystemCalendars = systemCalendars.filter { it.isWritable },
                titleHistory = titleHistory,
                parseIntList = { viewModel.repo.parseIntList(it) },
                parseDateList = { viewModel.repo.parseDateStrings(it) },
                onCancel = { viewModel.closeEditor() },
                onSave = { payload -> viewModel.saveFromEditor(payload) }
            )
            return@DXCalendarTheme
        }

        // 全畫面覆蓋：主題與日程設定頁
        if (isSettingsOpen) {
            SettingsAndManageScreen(
                settings = settings,
                updateState = updateState,
                categories = categories,
                systemCalendars = systemCalendars,
                hasCalendarPermission = hasCalendarPermission,
                onClose = { viewModel.openSettings(false) },
                onOpenUrl = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                onToggleAutoCheckUpdate = { viewModel.setAutoCheckUpdate(it) },
                onManualCheckUpdate = { viewModel.checkGitHubUpdate(autoDownloadIfFound = true, showDialogIfOnMain = false) },
                onStartUpdateDownload = { viewModel.startUpdateDownload() },
                onCancelUpdateDownload = { viewModel.cancelUpdateDownload() },
                onInstallDownloadedApk = { viewModel.installDownloadedUpdate() },
                onSelectBrand = { viewModel.setBrandStyle(it) },
                onSelectDarkMode = { viewModel.setDarkThemeMode(it) },
                onToggleStartMonday = { viewModel.setStartMonday(it) },
                onToggleShowLunar = { viewModel.setShowLunar(it) },
                onToggleShowWeekNum = { viewModel.setShowWeekNum(it) },
                onToggleGridLines = { viewModel.setShowGridLines(it) },
                onPickWallpaper = onPickWallpaperImage,
                onClearWallpaper = { viewModel.setWallpaper("", settings.wallpaperAlpha) },
                onChangeWallpaperAlpha = { viewModel.setWallpaper(settings.customWallpaperUri, it) },
                onChangeWidgetAppearance = { d, o -> viewModel.setWidgetAppearance(d, o) },
                onToggleCategoryVisible = { viewModel.toggleCategoryVisible(it) },
                onCreateCategory = { n, c -> viewModel.createCategory(n, c) },
                onRequestCalendarPermission = onRequestCalendarPermission,
                onToggleSystemCalendarVisible = { id, v -> viewModel.toggleSystemCalVisible(id, v) },
                onImportIcsFile = onPickIcsFile,
                onImportIcsUrl = { viewModel.importIcsFromUrl(it) },
                onExportIcsShare = {
                    viewModel.buildShareIcsIntent()?.let {
                        context.startActivity(Intent.createChooser(it, "匯出分享 DX日曆 (.ics)"))
                    }
                },
                onTestMultiCallAlarmDemo = { MultiCallAlarmScheduler.triggerImmediateDemoAlarm(context) }
            )
            return@DXCalendarTheme
        }

        val itemsByDate = remember(rangeItems) { rangeItems.groupBy { it.date } }
        val selectedDayItems = itemsByDate[selectedDate] ?: emptyList()

        // 響應式主容器（適配手機、8吋平板、10吋平板、Pixelbook 桌面與小視窗）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.backgroundColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    // Pixelbook / 外接鍵盤快捷鍵支援
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                viewModel.selectDate(selectedDate.minusDays(1))
                                true
                            }
                            Key.DirectionRight -> {
                                viewModel.selectDate(selectedDate.plusDays(1))
                                true
                            }
                            Key.PageUp -> {
                                viewModel.shiftMonth(-1)
                                true
                            }
                            Key.PageDown -> {
                                viewModel.shiftMonth(1)
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            val spec = computeAdaptiveLayoutSpec(maxWidth, maxHeight)

            Column(modifier = Modifier.fillMaxSize()) {
                // 頂部自適應工具列（仿 vivo 年份雙色標題 + 年/月/週/日/時間表 分段切換 + 透視例行按鈕 + 搜尋與設定）
                AdaptiveTopHeaderBar(
                    year = currentYm.year,
                    month = currentYm.monthValue,
                    viewMode = viewMode,
                    mainSection = mainSection,
                    compact = spec.compactTopBar,
                    peekHiddenInMonth = settings.peekHiddenInMonth,
                    isPrivacyUnlocked = isPrivacyUnlocked,
                    onPrevPeriod = {
                        if (viewMode == CalendarViewMode.YEAR) viewModel.shiftYear(-1)
                        else viewModel.shiftMonth(-1)
                    },
                    onNextPeriod = {
                        if (viewMode == CalendarViewMode.YEAR) viewModel.shiftYear(1)
                        else viewModel.shiftMonth(1)
                    },
                    onSelectViewMode = { viewModel.setViewMode(it) },
                    onTogglePeekHidden = { viewModel.setPeekHiddenInMonth(!settings.peekHiddenInMonth) },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenSettings = { viewModel.openSettings(true) },
                    onJumpToDate = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> viewModel.selectDate(LocalDate.of(y, m + 1, d)) },
                            selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
                        ).show()
                    },
                    onOpenAlmanacDiscovery = { showAlmanacDiscoveryDialog = true },
                    onQuickPrivacyAccess = {
                        viewModel.setMainSection(MainSection.PRIVACY)
                        if (!isPrivacyUnlocked) onTriggerDualBiometric()
                    }
                )

                // 主體區域（依 MainSection 與視窗尺寸動態呈現）
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (mainSection) {
                        MainSection.TASKS -> {
                            TasksCenterView(
                                tasks = allTasks,
                                isPrivacyUnlocked = isPrivacyUnlocked,
                                showCompleted = settings.showCompletedTasks,
                                googleEmail = settings.googleAccountEmail,
                                isSyncingTasks = isSyncingTasks,
                                onQuickAddTask = { t, todayFlag, showM -> viewModel.quickAddTask(t, todayFlag, showM) },
                                onToggleTask = { viewModel.toggleTaskEntity(it) },
                                onToggleTaskMonthVisible = { viewModel.toggleTaskMonthVisible(it) },
                                onDeleteTask = { viewModel.deleteTaskEntity(it) },
                                onClearCompleted = { viewModel.clearCompletedTasks() },
                                onToggleShowCompleted = { viewModel.setShowCompletedTasks(it) },
                                onSyncGoogleTasks = onLaunchGoogleAccountPicker,
                                onOpenFullEditorForTask = { viewModel.openNewEditor(presetTaskMode = true) }
                            )
                        }

                        MainSection.ROUTINES -> {
                            RoutinesAndHiddenHubView(
                                allEvents = allEvents,
                                allTasks = allTasks,
                                checkIns = checkIns,
                                externalOverrides = externalOverrides,
                                isPrivacyUnlocked = isPrivacyUnlocked,
                                routineDefaultHideFromMonth = settings.routineDefaultHideFromMonth,
                                onToggleDefaultRoutineHide = { viewModel.setRoutineDefaultHide(it) },
                                onAddRoutineQuick = { viewModel.openNewEditor(presetRoutine = true) },
                                onToggleTodayCheckIn = { type, id, done ->
                                    viewModel.toggleRoutineCheckIn(type, id, LocalDate.now().toEpochDay(), done)
                                },
                                onToggleEventMonthShow = { viewModel.toggleEventMonthVisible(it) },
                                onToggleEventPause = { viewModel.toggleEventPause(it) },
                                onEditEvent = { viewModel.openEditEventEntity(it) },
                                onDeleteEvent = { viewModel.deleteEventEntity(it) },
                                onToggleTaskMonthShow = { viewModel.toggleTaskMonthVisible(it) },
                                onRestoreExternalOverride = { viewModel.restoreExternalOverride(it) },
                                onCleanupExpiredItems = { viewModel.cleanupExpiredAccumulatedItems() }
                            )
                        }

                        MainSection.PRIVACY -> {
                            PrivacyVaultView(
                                isUnlocked = isPrivacyUnlocked,
                                requireDualFingerprint = settings.requireDualFingerprint,
                                hasPinConfigured = settings.privacyPinHash.isNotBlank(),
                                privacyItems = rangeItems.filter { it.isPrivacy },
                                onTriggerDualFingerprintUnlock = onTriggerDualBiometric,
                                onLockImmediately = { viewModel.setPrivacyUnlocked(false) },
                                onAddPrivacyEvent = { viewModel.openNewEditor(presetPrivacy = true) },
                                onSetupOrVerifyPin = { showPinDialog = true },
                                onToggleRequireDualFingerprint = { viewModel.setRequireDualFingerprint(it) },
                                onSelectItem = {
                                    viewModel.setFocusedItem(it)
                                    viewModel.openEditForUnifiedItem(it)
                                }
                            )
                        }

                        MainSection.CALENDAR -> {
                            if (spec.showDualPane) {
                                // 8吋平板 / 10吋平板 / Pixelbook / 摺疊機展開：左主視圖 + 右農曆干支詳情雙欄（超寬螢幕還附加左側日曆分類導覽列！）
                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (spec.showPermanentCategoryRail) {
                                        PermanentCategorySideRail(
                                            categories = categories.filter { !it.isPrivacyCategory },
                                            onToggleCategory = { viewModel.toggleCategoryVisible(it) },
                                            onOpenManage = { viewModel.openSettings(true) }
                                        )
                                        VerticalDivider(color = tokens.gridLineColor)
                                    }

                                    Box(modifier = Modifier.weight(spec.leftPaneWeight).fillMaxHeight()) {
                                        CalendarPrimaryViewport(
                                            viewMode = viewMode,
                                            currentYm = currentYm,
                                            selectedDate = selectedDate,
                                            today = today,
                                            itemsByDate = itemsByDate,
                                            allRangeItems = rangeItems,
                                            focusedItem = focusedItem,
                                            settings = settings,
                                            maxEventsInCell = spec.maxEventsInMonthCell,
                                            yearColumns = if (spec.formFactor == WindowFormFactor.TABLET_10_PIXELBOOK) 4 else 3,
                                            onSelectMonthFromYear = { ym ->
                                                viewModel.selectDate(ym.atDay(1))
                                                viewModel.setViewMode(CalendarViewMode.MONTH)
                                            },
                                            onSelectDate = { viewModel.selectDate(it) },
                                            onSelectItem = { viewModel.setFocusedItem(it) },
                                            onSwipePrevMonth = { viewModel.shiftMonth(-1) },
                                            onSwipeNextMonth = { viewModel.shiftMonth(1) },
                                            onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) }
                                        )
                                    }

                                    VerticalDivider(color = tokens.gridLineColor)

                                    Box(modifier = Modifier.weight(spec.rightPaneWeight).fillMaxHeight()) {
                                        RightDetailPanel(
                                            selectedDate = selectedDate,
                                            today = today,
                                            dayItems = selectedDayItems,
                                            focusedItem = focusedItem,
                                            onClearFocusedItem = { viewModel.setFocusedItem(null) },
                                            onSelectItem = { viewModel.setFocusedItem(it) },
                                            onAddEventForDate = { viewModel.openNewEditor(date = it) },
                                            onEditItem = { viewModel.openEditForUnifiedItem(it) },
                                            onDeleteItem = { viewModel.deleteUnifiedItem(it) },
                                            onShareItem = { item ->
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "【${item.title}】\n日期：${item.date}\n地點：${item.location}\n${item.notes}")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "分享行程"))
                                            },
                                            onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) },
                                            onHideExternalToRoutine = { viewModel.toggleExternalSeriesOverride(it) }
                                        )
                                    }
                                }
                            } else {
                                // 手機直向 / 小視窗緊湊單欄模式（完美還原截圖 1 標註 1 + 標註 2 可開關下方當日行程面板！）
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val showBottomSheetInMonth = viewMode == CalendarViewMode.MONTH &&
                                        showCompactBottomDayPanel &&
                                        spec.heightDp >= 480.dp

                                    Box(
                                        modifier = Modifier
                                            .weight(if (showBottomSheetInMonth) 0.58f else 1f)
                                            .fillMaxWidth()
                                    ) {
                                        CalendarPrimaryViewport(
                                            viewMode = viewMode,
                                            currentYm = currentYm,
                                            selectedDate = selectedDate,
                                            today = today,
                                            itemsByDate = itemsByDate,
                                            allRangeItems = rangeItems,
                                            focusedItem = focusedItem,
                                            settings = settings,
                                            maxEventsInCell = spec.maxEventsInMonthCell,
                                            yearColumns = 3,
                                            onSelectMonthFromYear = { ym ->
                                                viewModel.selectDate(ym.atDay(1))
                                                viewModel.setViewMode(CalendarViewMode.MONTH)
                                            },
                                            onSelectDate = {
                                                viewModel.selectDate(it)
                                                showCompactBottomDayPanel = true
                                            },
                                            onSelectItem = {
                                                viewModel.setFocusedItem(it)
                                                showCompactBottomDayPanel = true
                                            },
                                            onSwipePrevMonth = { viewModel.shiftMonth(-1) },
                                            onSwipeNextMonth = { viewModel.shiftMonth(1) },
                                            onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) }
                                        )
                                    }

                                    AnimatedVisibility(visible = showBottomSheetInMonth || focusedItem != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(if (spec.heightDp < 560.dp) 185.dp else 265.dp)
                                                .background(tokens.surfaceCardColor.copy(alpha = 0.96f))
                                        ) {
                                            HorizontalDivider(color = tokens.accentColor.copy(alpha = 0.5f), thickness = 1.5.dp)
                                            RightDetailPanel(
                                                selectedDate = selectedDate,
                                                today = today,
                                                dayItems = selectedDayItems,
                                                focusedItem = focusedItem,
                                                onClearFocusedItem = { viewModel.setFocusedItem(null) },
                                                onSelectItem = { viewModel.setFocusedItem(it) },
                                                onAddEventForDate = { viewModel.openNewEditor(date = it) },
                                                onEditItem = { viewModel.openEditForUnifiedItem(it) },
                                                onDeleteItem = { viewModel.deleteUnifiedItem(it) },
                                                onShareItem = { item ->
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, "【${item.title}】 ${item.date}")
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "分享行程"))
                                                },
                                                onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) },
                                                onHideExternalToRoutine = { viewModel.toggleExternalSeriesOverride(it) },
                                                showCloseDayHeaderButton = true,
                                                onCloseDayPanel = {
                                                    viewModel.setFocusedItem(null)
                                                    showCompactBottomDayPanel = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // vivo / OPPO 懸浮毛玻璃雙膠囊導覽列（完美還原示範影片底部設計！）
                    if (tokens.useFloatingOriginCapsule && spec.heightDp >= 420.dp) {
                        VivoFloatingCapsuleBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                            currentSection = mainSection,
                            isNotToday = selectedDate != today,
                            isPrivacyUnlocked = isPrivacyUnlocked,
                            onSelectSection = { sec ->
                                viewModel.setMainSection(sec)
                                if (sec == MainSection.PRIVACY && !isPrivacyUnlocked) {
                                    onTriggerDualBiometric()
                                }
                            },
                            onJumpToday = { viewModel.jumpToToday() },
                            onQuickAdd = {
                                viewModel.openNewEditor(
                                    date = selectedDate,
                                    presetRoutine = mainSection == MainSection.ROUTINES,
                                    presetPrivacy = mainSection == MainSection.PRIVACY && isPrivacyUnlocked,
                                    presetTaskMode = mainSection == MainSection.TASKS
                                )
                            }
                        )
                    }
                }

                // Google / 小米 / 三星 或超扁小視窗：經典底部導覽列 + 右側快速 + 按鈕（對應截圖 1 標註 3）
                if (!tokens.useFloatingOriginCapsule || spec.heightDp < 420.dp) {
                    ClassicBottomBar(
                        currentSection = mainSection,
                        onSelectSection = { sec ->
                            viewModel.setMainSection(sec)
                            if (sec == MainSection.PRIVACY && !isPrivacyUnlocked) {
                                onTriggerDualBiometric()
                            }
                        },
                        onJumpToday = { viewModel.jumpToToday() },
                        onQuickAdd = {
                            viewModel.openNewEditor(
                                date = selectedDate,
                                presetRoutine = mainSection == MainSection.ROUTINES,
                                presetPrivacy = mainSection == MainSection.PRIVACY && isPrivacyUnlocked,
                                presetTaskMode = mainSection == MainSection.TASKS
                            )
                        }
                    )
                }
            }
        }

        // === 搜尋對話框 ===
        if (showSearchDialog) {
            var q by remember { mutableStateOf("") }
            val matched = remember(q, rangeItems) {
                if (q.isBlank()) emptyList()
                else rangeItems.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.notes.contains(q, ignoreCase = true) ||
                        it.location.contains(q, ignoreCase = true)
                }
            }
            AlertDialog(
                onDismissRequest = { showSearchDialog = false },
                title = { Text("搜尋行程、待辦與例行事務") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = q,
                            onValueChange = { q = it },
                            placeholder = { Text("輸入關鍵字...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(
                            modifier = Modifier.height(240.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            matched.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectDate(item.date)
                                            viewModel.setFocusedItem(item)
                                            viewModel.setMainSection(MainSection.CALENDAR)
                                            showSearchDialog = false
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text("${item.date}  ${item.title}", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSearchDialog = false }) { Text("關閉") }
                }
            )
        }

        // === 黃曆宜忌發現彈窗（仿 vivo 01:09 發現頁） ===
        if (showAlmanacDiscoveryDialog) {
            val lunar = LunarCalendarEngine.getLunarInfo(selectedDate)
            AlertDialog(
                onDismissRequest = { showAlmanacDiscoveryDialog = false },
                title = { Text("${selectedDate} · 農曆${lunar.fullLunarText}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(lunar.fullGanZhiHeader, color = tokens.accentColor, fontWeight = FontWeight.Bold)
                        Text("宜：${lunar.yiList.joinToString("、")}")
                        Text("忌：${lunar.jiList.joinToString("、")}")
                        if (lunar.solarTermPoem != null) {
                            Text("節氣語錄：${lunar.solarTermPoem}", color = tokens.secondaryTextColor)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAlmanacDiscoveryDialog = false }) { Text("完成") }
                }
            )
        }

        // === 雙階段隱私備援 PIN 碼設定與驗證對話框 ===
        if (showPinDialog) {
            val isSettingNew = settings.privacyPinHash.isBlank() || isPrivacyUnlocked
            var pin1 by remember { mutableStateOf("") }
            var pin2 by remember { mutableStateOf("") }
            var errText by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text(if (isSettingNew) "設定隱私日曆 PIN 碼" else "雙重隱私 PIN 碼驗證") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pin1,
                            onValueChange = { pin1 = it; errText = "" },
                            label = { Text(if (isSettingNew) "輸入新 PIN 碼 (至少 4 碼)" else "第一道確認：輸入隱私 PIN 碼") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pin2,
                            onValueChange = { pin2 = it; errText = "" },
                            label = { Text(if (isSettingNew) "再次輸入確認 PIN 碼" else "第二道雙重確認：再次輸入 PIN 碼") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        if (errText.isNotBlank()) {
                            Text(errText, color = Color(0xFFEF5350), fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isSettingNew) {
                                if (pin1.length < 4 || pin1 != pin2) {
                                    errText = "兩次輸入需一致且至少 4 碼"
                                } else {
                                    viewModel.setPrivacyPin(pin1)
                                    viewModel.setPrivacyUnlocked(true)
                                    showPinDialog = false
                                }
                            } else {
                                if (pin1 == pin2 && viewModel.verifyPrivacyPin(pin1)) {
                                    viewModel.setPrivacyUnlocked(true)
                                    showPinDialog = false
                                } else {
                                    errText = "隱私 PIN 碼驗證錯誤"
                                }
                            }
                        }
                    ) { Text("確認") }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) { Text("取消") }
                }
            )
        }

        // === 啟動時自動偵測到 GitHub 新版本下載進度條彈窗 ===
        if (updateState.showAutoPromptDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (updateState.phase != UpdatePhase.DOWNLOADING) {
                        viewModel.dismissUpdateDialog()
                    }
                },
                title = {
                    Text("發現新版本 ${updateState.releaseInfo?.tagName.orEmpty()}")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "目前版本 v${updateState.currentVersion} → 最新版本 v${updateState.latestVersion}",
                            color = tokens.accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        when (updateState.phase) {
                            UpdatePhase.DOWNLOADING -> {
                                val pct = (updateState.progressFraction * 100).toInt()
                                val dlMb = updateState.downloadedBytes / (1024f * 1024f)
                                val totMb = updateState.totalBytes / (1024f * 1024f)
                                Text(
                                    text = if (updateState.totalBytes > 0) {
                                        "正在自動下載更新包：$pct% (%.1f / %.1f MB)".format(dlMb, totMb)
                                    } else "正在自動下載更新包：$pct%",
                                    fontSize = 13.sp
                                )
                                LinearProgressIndicator(
                                    progress = { updateState.progressFraction },
                                    color = tokens.accentColor,
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }

                            UpdatePhase.READY_TO_INSTALL -> {
                                Text("✓ 下載完成 (100%)！請點擊下方按鈕安裝更新。", color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(
                                    progress = { 1f },
                                    color = Color(0xFF00C853),
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }

                            else -> {
                                val notes = updateState.releaseInfo?.releaseNotes.orEmpty()
                                if (notes.isNotBlank()) {
                                    Text(notes.lines().take(6).joinToString("\n"), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    when (updateState.phase) {
                        UpdatePhase.READY_TO_INSTALL -> {
                            Button(
                                onClick = { viewModel.installDownloadedUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                            ) {
                                Text("立即安裝")
                            }
                        }
                        UpdatePhase.DOWNLOADING -> {
                            TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                                Text("背景繼續下載")
                            }
                        }
                        else -> {
                            Button(
                                onClick = { viewModel.startUpdateDownload() },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                            ) {
                                Text("立即下載更新")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (updateState.phase == UpdatePhase.DOWNLOADING) {
                                viewModel.cancelUpdateDownload()
                            }
                            viewModel.dismissUpdateDialog()
                        }
                    ) {
                        Text(if (updateState.phase == UpdatePhase.DOWNLOADING) "取消下載" else "稍後")
                    }
                }
            )
        }
    }
}

@Composable
private fun AdaptiveTopHeaderBar(
    year: Int,
    month: Int,
    viewMode: CalendarViewMode,
    mainSection: MainSection,
    compact: Boolean,
    peekHiddenInMonth: Boolean,
    isPrivacyUnlocked: Boolean,
    onPrevPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onTogglePeekHidden: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onJumpToDate: () -> Unit,
    onOpenAlmanacDiscovery: () -> Unit,
    onQuickPrivacyAccess: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    var showOverflow by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 年月標題（vivo 特色雙色年份：20白 + 26紅橘 / 11）
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevPeriod, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一期", tint = tokens.secondaryTextColor)
                }
                val titleAnnotated = buildAnnotatedString {
                    val yStr = year.toString()
                    if (tokens.useDualToneYearHeader && yStr.length == 4) {
                        withStyle(SpanStyle(color = tokens.primaryTextColor)) { append(yStr.substring(0, 2)) }
                        withStyle(SpanStyle(color = tokens.accentColor)) { append(yStr.substring(2, 4)) }
                        if (viewMode != CalendarViewMode.YEAR) {
                            withStyle(SpanStyle(color = tokens.primaryTextColor)) { append("/") }
                            withStyle(SpanStyle(color = tokens.accentColor)) { append("%02d".format(month)) }
                        }
                    } else {
                        withStyle(SpanStyle(color = tokens.primaryTextColor)) {
                            if (viewMode == CalendarViewMode.YEAR) append("${year}年")
                            else append("${year}年${month}月")
                        }
                    }
                }
                Text(
                    text = titleAnnotated,
                    fontSize = if (compact) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onJumpToDate() }
                )
                IconButton(onClick = onNextPeriod, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一期", tint = tokens.secondaryTextColor)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 大螢幕直接於同行顯示「年 / 月 / 週 / 日 / 時間表」分段膠囊（仿 vivo 平板頂欄）
            if (!compact && mainSection == MainSection.CALENDAR) {
                ViewModeSegmentedPill(current = viewMode, onSelect = onSelectViewMode)
                Spacer(modifier = Modifier.weight(1f))
            }

            // 一鍵「暫時透視月曆隱藏例行事務」眼睛按鈕
            IconButton(onClick = onTogglePeekHidden) {
                Icon(
                    imageVector = if (peekHiddenInMonth) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = "透視例行事務",
                    tint = if (peekHiddenInMonth) tokens.accentColor else tokens.secondaryTextColor
                )
            }

            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜尋", tint = tokens.primaryTextColor)
            }

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "主題與設定", tint = tokens.primaryTextColor)
            }

            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多功能", tint = tokens.primaryTextColor)
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text("日期重新導向 (跳至指定日)") },
                        leadingIcon = { Icon(Icons.Filled.Today, contentDescription = null) },
                        onClick = { showOverflow = false; onJumpToDate() }
                    )
                    DropdownMenuItem(
                        text = { Text("發現 (農曆黃曆宜忌)") },
                        leadingIcon = { Icon(Icons.Filled.Explore, contentDescription = null) },
                        onClick = { showOverflow = false; onOpenAlmanacDiscovery() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPrivacyUnlocked) "進入隱私日曆 (已解鎖)" else "雙指紋鎖開啟隱私日曆") },
                        leadingIcon = {
                            Icon(if (isPrivacyUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = null)
                        },
                        onClick = { showOverflow = false; onQuickPrivacyAccess() }
                    )
                }
            }
        }

        // 手機與小視窗：第二行顯示緊湊「年 / 月 / 週 / 日 / 時間表」分段膠囊
        if (compact && mainSection == MainSection.CALENDAR) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ViewModeSegmentedPill(current = viewMode, onSelect = onSelectViewMode)
            }
        }
    }
}

@Composable
private fun ViewModeSegmentedPill(
    current: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit
) {
    val tokens = LocalBrandTokens.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tokens.surfaceCardColor)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CalendarViewMode.entries.forEach { mode ->
            val selected = current == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) tokens.elevatedSurfaceColor else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = mode.label,
                    color = if (selected) tokens.primaryTextColor else tokens.secondaryTextColor,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun CalendarPrimaryViewport(
    viewMode: CalendarViewMode,
    currentYm: java.time.YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    itemsByDate: Map<LocalDate, List<com.dx.calendar.data.repository.UnifiedCalendarItem>>,
    allRangeItems: List<com.dx.calendar.data.repository.UnifiedCalendarItem>,
    focusedItem: com.dx.calendar.data.repository.UnifiedCalendarItem?,
    settings: com.dx.calendar.data.local.UserSettings,
    maxEventsInCell: Int,
    yearColumns: Int,
    onSelectMonthFromYear: (java.time.YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSelectItem: (com.dx.calendar.data.repository.UnifiedCalendarItem) -> Unit,
    onSwipePrevMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit,
    onToggleComplete: (com.dx.calendar.data.repository.UnifiedCalendarItem) -> Unit
) {
    when (viewMode) {
        CalendarViewMode.YEAR -> YearGridView(
            year = currentYm.year,
            today = today,
            startWeekOnMonday = settings.startWeekOnMonday,
            columns = yearColumns,
            onSelectMonth = onSelectMonthFromYear
        )

        CalendarViewMode.MONTH -> MonthGridView(
            yearMonth = currentYm,
            selectedDate = selectedDate,
            today = today,
            itemsByDate = itemsByDate,
            settings = settings,
            maxEventsPerCell = maxEventsInCell,
            onSelectDate = onSelectDate,
            onSwipePrevMonth = onSwipePrevMonth,
            onSwipeNextMonth = onSwipeNextMonth,
            onToggleTaskQuick = onToggleComplete
        )

        CalendarViewMode.WEEK -> WeekOrDayTimelineView(
            isSingleDay = false,
            selectedDate = selectedDate,
            today = today,
            startWeekOnMonday = settings.startWeekOnMonday,
            itemsByDate = itemsByDate,
            onSelectDate = onSelectDate,
            onSelectItem = onSelectItem
        )

        CalendarViewMode.DAY -> WeekOrDayTimelineView(
            isSingleDay = true,
            selectedDate = selectedDate,
            today = today,
            startWeekOnMonday = settings.startWeekOnMonday,
            itemsByDate = itemsByDate,
            onSelectDate = onSelectDate,
            onSelectItem = onSelectItem
        )

        CalendarViewMode.AGENDA -> AgendaScheduleListView(
            items = allRangeItems,
            selectedItem = focusedItem,
            onSelectItem = onSelectItem,
            onToggleTaskComplete = onToggleComplete
        )
    }
}

@Composable
private fun VivoFloatingCapsuleBar(
    modifier: Modifier,
    currentSection: MainSection,
    isNotToday: Boolean,
    isPrivacyUnlocked: Boolean,
    onSelectSection: (MainSection) -> Unit,
    onJumpToday: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        // 左側毛玻璃懸浮主膠囊：日曆 / 待辦 / 例行中心 / 隱私日曆
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(tokens.floatingPillColor)
                .border(0.8.dp, tokens.gridLineColor, RoundedCornerShape(30.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            val tabs = listOf(
                Triple(MainSection.CALENDAR, "日曆", Icons.Filled.CalendarMonth),
                Triple(MainSection.TASKS, "待辦事項", Icons.Filled.CheckCircleOutline),
                Triple(MainSection.ROUTINES, "例行中心", Icons.Filled.Repeat),
                Triple(MainSection.PRIVACY, "隱私", if (isPrivacyUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock)
            )
            tabs.forEach { (sec, label, icon) ->
                val selected = currentSection == sec
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) tokens.elevatedSurfaceColor else Color.Transparent)
                        .clickable { onSelectSection(sec) }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) tokens.accentColor else tokens.secondaryTextColor,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = label,
                        color = if (selected) tokens.accentColor else tokens.secondaryTextColor,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 右側圓形「+」快速新增按鈕（仿 vivo 紅橘色圓鈕 + 截圖 1 右下角 + 圈選！）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(tokens.accentColor)
                .clickable { onQuickAdd() }
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新增行程", tint = Color.White, modifier = Modifier.size(26.dp))
        }

        // 當不在今天時，浮現「今」圓形快捷返回鈕（完全還原 vivo 00:08 底部設計！）
        if (isNotToday) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(tokens.floatingPillColor)
                    .border(1.dp, tokens.accentColor, CircleShape)
                    .clickable { onJumpToday() }
            ) {
                Text("今", color = tokens.accentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClassicBottomBar(
    currentSection: MainSection,
    onSelectSection: (MainSection) -> Unit,
    onJumpToday: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    NavigationBar(containerColor = tokens.surfaceCardColor, tonalElevation = 4.dp) {
        NavigationBarItem(
            selected = false,
            onClick = onJumpToday,
            icon = { Icon(Icons.Filled.Today, contentDescription = "今天") },
            label = { Text("今天", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentSection == MainSection.CALENDAR,
            onClick = { onSelectSection(MainSection.CALENDAR) },
            icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "日曆") },
            label = { Text("日曆", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentSection == MainSection.TASKS,
            onClick = { onSelectSection(MainSection.TASKS) },
            icon = { Icon(Icons.Filled.CheckCircleOutline, contentDescription = "待辦") },
            label = { Text("Tasks", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentSection == MainSection.ROUTINES,
            onClick = { onSelectSection(MainSection.ROUTINES) },
            icon = { Icon(Icons.Filled.Repeat, contentDescription = "例行") },
            label = { Text("例行", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = false,
            onClick = onQuickAdd,
            icon = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(34.dp).background(tokens.accentColor, CircleShape)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新增", tint = Color.White)
                }
            },
            label = { Text("新增", fontSize = 11.sp) }
        )
    }
}

@Composable
private fun PermanentCategorySideRail(
    categories: List<com.dx.calendar.data.model.CalendarCategoryEntity>,
    onToggleCategory: (com.dx.calendar.data.model.CalendarCategoryEntity) -> Unit,
    onOpenManage: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    Column(
        modifier = Modifier
            .width(205.dp)
            .fillMaxHeight()
            .background(tokens.surfaceCardColor.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("時間表與 Tasks 篩選", color = tokens.secondaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        categories.forEach { cat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleCategory(cat) }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(checked = cat.isVisible, onCheckedChange = { onToggleCategory(cat) })
                Box(modifier = Modifier.size(10.dp).background(Color(cat.colorArgb), CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(cat.name, color = tokens.primaryTextColor, fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenManage, modifier = Modifier.fillMaxWidth()) {
            Text("管理帳號與匯入 →", fontSize = 12.sp)
        }
    }
}
