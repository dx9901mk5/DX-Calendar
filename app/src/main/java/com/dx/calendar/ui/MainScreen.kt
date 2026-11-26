package com.dx.calendar.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import com.dx.calendar.data.weather.DailyWeatherInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.dx.calendar.ui.components.SamsungDayTimelinePopupCard
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
    onAddGoogleAccount: () -> Unit = onLaunchGoogleAccountPicker,
    onAddSystemGoogleAccount: () -> Unit = onLaunchGoogleAccountPicker,
    onTriggerDualBiometric: (onVerified: () -> Unit) -> Unit,
    onPickWallpaperImage: () -> Unit,
    onPickIcsFile: () -> Unit,
    onLaunchVoiceInput: ((String) -> Unit) -> Unit = {},
    onPickEventAttachment: ((String) -> Unit) -> Unit = {},
    onUploadBackupToGoogleDrive: () -> Unit = {},
    onExportFullBackupFile: () -> Unit = {},
    onRestoreFullBackupFile: () -> Unit = {},
    onImportContactBirthdays: () -> Unit = {}
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
    val onlyWomensHealthFilter by viewModel.onlyWomensHealthFilter.collectAsState()
    val focusedItem by viewModel.focusedItem.collectAsState()
    val editorState by viewModel.editorState.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val systemCalendars by viewModel.systemCalendars.collectAsState()
    val isSyncingTasks by viewModel.isSyncingTasks.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val rangeItems by viewModel.visibleRangeItems.collectAsState()
    val weatherByDate by viewModel.weatherByDate.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val richMetaMap by viewModel.richMetaMap.collectAsState()

    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    var showSearchDialog by remember { mutableStateOf(false) }
    var showAlmanacDiscoveryDialog by remember { mutableStateOf(false) }
    var showQuickNlpDialog by remember { mutableStateOf(false) }
    var autoLaunchVoiceInNlp by remember { mutableStateOf(false) }
    var showTemplateStampBar by remember { mutableStateOf(false) }
    var activeStampTemplate by remember { mutableStateOf<com.dx.calendar.data.local.EventTemplateItem?>(null) }
    var showNewTemplateDialog by remember { mutableStateOf(false) }
    var movingItem by remember { mutableStateOf<com.dx.calendar.data.repository.UnifiedCalendarItem?>(null) }
    var pomodoroTargetTitle by remember { mutableStateOf<String?>(null) }
    var showPomodoroDialog by remember { mutableStateOf(false) }
    var pomodoroCompleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showFeatureGuideDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    // 仿三星日曆手勢動畫狀態：手機月檢視預設滿版月曆 (false)，上滑平滑壓縮月曆並拉起下方時間表 (true)；再點已選日期彈出 24h 浮動時間軸卡片
    var showCompactBottomDayPanel by remember {
        mutableStateOf(settings.monthViewLayoutMode == com.dx.calendar.data.local.MonthViewLayoutMode.SCHEDULE_SPLIT)
    }
    var sheetDragFraction by remember {
        mutableFloatStateOf(if (settings.monthViewLayoutMode == com.dx.calendar.data.local.MonthViewLayoutMode.SCHEDULE_SPLIT) 1f else 0f)
    }
    LaunchedEffect(settings.monthViewLayoutMode) {
        val split = settings.monthViewLayoutMode == com.dx.calendar.data.local.MonthViewLayoutMode.SCHEDULE_SPLIT
        showCompactBottomDayPanel = split
        sheetDragFraction = if (split) 1f else 0f
    }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var floatingTimelinePopupDate by remember { mutableStateOf<LocalDate?>(null) }

    DXCalendarTheme(
        brandStyle = settings.brandStyle,
        darkThemeMode = settings.darkThemeMode
    ) {
        val tokens = LocalBrandTokens.current

        // 主畫面階層式返回鍵邏輯：編輯器 → 設定頁 → 浮動時間軸彈窗 → 詳情面板 → 收合手機時間表 → 離開女性健康臨時篩選 → 返回日曆分頁 → 返回月檢視
        BackHandler(
            enabled = !editorState.isOpen &&
                !isSettingsOpen &&
                (floatingTimelinePopupDate != null ||
                    focusedItem != null ||
                    showCompactBottomDayPanel ||
                    onlyWomensHealthFilter ||
                    mainSection != MainSection.CALENDAR ||
                    viewMode != CalendarViewMode.MONTH)
        ) {
            when {
                floatingTimelinePopupDate != null -> floatingTimelinePopupDate = null
                focusedItem != null -> viewModel.setFocusedItem(null)
                showCompactBottomDayPanel -> {
                    showCompactBottomDayPanel = false
                    sheetDragFraction = 0f
                }
                onlyWomensHealthFilter -> viewModel.setOnlyWomensHealthFilter(false)
                mainSection != MainSection.CALENDAR -> viewModel.setMainSection(MainSection.CALENDAR)
                viewMode != CalendarViewMode.MONTH -> viewModel.setViewMode(CalendarViewMode.MONTH)
            }
        }

        val displayRangeItems = remember(rangeItems, onlyWomensHealthFilter) {
            if (onlyWomensHealthFilter) {
                rangeItems.filter { com.dx.calendar.data.model.WomensHealthPresets.isWomensHealthItem(it) }
            } else {
                rangeItems
            }
        }
        val itemsByDate = remember(displayRangeItems) { displayRangeItems.groupBy { it.date } }
        val selectedDayItems = itemsByDate[selectedDate] ?: emptyList()

        Box(modifier = Modifier.fillMaxSize()) {
        // 響應式主容器（適配手機、8吋平板、10吋平板、Pixelbook 桌面與小視窗；不巢狀 BoxWithConstraints）
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

            // 摺疊機展開/收合、分割視窗跨斷點或離開月檢視時，自動重置拖曳旗標，防止手勢狀態卡死
            LaunchedEffect(spec.showDualPane, viewMode) {
                isDraggingSheet = false
                if (spec.showDualPane || viewMode != CalendarViewMode.MONTH) {
                    sheetDragFraction = 0f
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 頂部自適應工具列（精簡移除眼睛透視鈕；隱私入口僅於設定最底開啟後才顯示）
                AdaptiveTopHeaderBar(
                    year = currentYm.year,
                    month = currentYm.monthValue,
                    viewMode = viewMode,
                    mainSection = mainSection,
                    compact = spec.compactTopBar,
                    narrow = spec.isNarrowSplit,
                    privacyFeatureEnabled = settings.privacyFeatureEnabled,
                    isPrivacyUnlocked = isPrivacyUnlocked,
                    onlyWomensHealthFilter = onlyWomensHealthFilter,
                    showTemplateStampBar = showTemplateStampBar,
                    onPrevPeriod = {
                        if (viewMode == CalendarViewMode.YEAR) viewModel.shiftYear(-1)
                        else viewModel.shiftMonth(-1)
                    },
                    onNextPeriod = {
                        if (viewMode == CalendarViewMode.YEAR) viewModel.shiftYear(1)
                        else viewModel.shiftMonth(1)
                    },
                    onSelectViewMode = { viewModel.setViewMode(it) },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenSettings = { viewModel.openSettings(true) },
                    onJumpToToday = { viewModel.jumpToToday() },
                    onOpenQuickNlp = {
                        autoLaunchVoiceInNlp = false
                        showQuickNlpDialog = true
                    },
                    onToggleWomensHealthFilter = { viewModel.toggleOnlyWomensHealthFilter() },
                    onToggleTemplateBar = {
                        showTemplateStampBar = !showTemplateStampBar
                        if (!showTemplateStampBar) activeStampTemplate = null
                    },
                    onOpenPomodoro = {
                        pomodoroTargetTitle = null
                        pomodoroCompleteAction = null
                        showPomodoroDialog = true
                    },
                    onImportContactBirthdays = onImportContactBirthdays,
                    onOpenFeatureGuide = { showFeatureGuideDialog = true },
                    onJumpToDate = {
                        runCatching {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> viewModel.selectDate(LocalDate.of(y, m + 1, d)) },
                                selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
                            ).show()
                        }
                    },
                    onOpenAlmanacDiscovery = { showAlmanacDiscoveryDialog = true },
                    onQuickPrivacyAccess = {
                        if (isPrivacyUnlocked) {
                            viewModel.setPrivacyUnlocked(false)
                            android.widget.Toast.makeText(
                                context,
                                "已隱藏隱私內容",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "請使用指紋驗證",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onTriggerDualBiometric {
                                viewModel.setPrivacyUnlocked(true)
                                android.widget.Toast.makeText(
                                    context,
                                    "已顯示隱私內容",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )

                // 臨時專屬檢視橫幅：單獨拉出只檢視「女性健康」類別（一鍵新增健康追蹤或恢復全部行程）
                AnimatedVisibility(visible = onlyWomensHealthFilter && mainSection == MainSection.CALENDAR) {
                    val healthRose = Color(com.dx.calendar.data.model.WomensHealthPresets.CATEGORY_COLOR_ARGB)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(healthRose.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🌸 臨時只檢視：女性健康（本期 ${displayRangeItems.size} 筆）",
                            color = healthRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.openNewEditor(date = selectedDate, presetWomensHealth = true) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("＋ 紀錄", color = healthRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.setOnlyWomensHealthFilter(false) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("恢復全部", color = tokens.primaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // #2 行程長按拖曳改期提示橫幅
                AnimatedVisibility(visible = movingItem != null) {
                    movingItem?.let { itemToMove ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.accentColor.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "📌 改期中「${itemToMove.title}」— 請點擊目標日期完成移動",
                                color = tokens.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { movingItem = null }) {
                                Text("取消", color = tokens.primaryTextColor, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // #4 輪班與常用範本連點排班列（Stamp Mode）
                AnimatedVisibility(visible = showTemplateStampBar && mainSection == MainSection.CALENDAR) {
                    com.dx.calendar.ui.components.ShiftTemplateStampBar(
                        templates = templates,
                        activeTemplate = activeStampTemplate,
                        onSelectTemplate = { activeStampTemplate = it },
                        onAddTemplateClick = { showNewTemplateDialog = true },
                        onCloseBar = {
                            showTemplateStampBar = false
                            activeStampTemplate = null
                        }
                    )
                }

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
                                onOpenFullEditorForTask = { viewModel.openNewEditor(presetTaskMode = true) },
                                onScheduleTaskTimeBlock = { task, h, m ->
                                    viewModel.scheduleTaskToTimeBlock(task, selectedDate, h, m)
                                },
                                onStartPomodoroForTask = { task ->
                                    pomodoroTargetTitle = com.dx.calendar.util.PrivacyCryptoManager.decryptIfNeeded(task.title)
                                    pomodoroCompleteAction = { viewModel.toggleTaskEntity(task) }
                                    showPomodoroDialog = true
                                }
                            )
                        }

                        MainSection.ROUTINES -> {
                            RoutinesAndHiddenHubView(
                                allEvents = allEvents,
                                allTasks = allTasks,
                                checkIns = checkIns,
                                externalOverrides = externalOverrides,
                                isPrivacyUnlocked = isPrivacyUnlocked,
                                routineMedicationFeatureEnabled = settings.routineMedicationFeatureEnabled,
                                routineDefaultHideFromMonth = settings.routineDefaultHideFromMonth,
                                onToggleRoutineMedicationFeature = { viewModel.setRoutineMedicationFeatureEnabled(it) },
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
                                requireDualFingerprint = true,
                                hasPinConfigured = settings.privacyPinHash.isNotBlank(),
                                privacyItems = rangeItems.filter { it.isPrivacy },
                                onTriggerDualFingerprintUnlock = {
                                    onTriggerDualBiometric { viewModel.setPrivacyUnlocked(true) }
                                },
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
                                            onlyWomensHealthFilter = onlyWomensHealthFilter,
                                            onToggleWomensHealthFilter = { viewModel.toggleOnlyWomensHealthFilter() },
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
                                            weatherByDate = weatherByDate,
                                            allRangeItems = displayRangeItems,
                                            focusedItem = focusedItem,
                                            settings = settings,
                                            maxEventsInCell = spec.maxEventsInMonthCell,
                                            yearColumns = spec.yearGridColumns,
                                            hideLunarForCompactHeight = spec.hideLunarInMonthCell,
                                            onSelectMonthFromYear = { ym ->
                                                viewModel.selectDate(ym.atDay(1))
                                                viewModel.setViewMode(CalendarViewMode.MONTH)
                                            },
                                            onSelectDate = { tappedDate ->
                                                val moveTarget = movingItem
                                                val stampTpl = activeStampTemplate
                                                when {
                                                    moveTarget != null -> {
                                                        viewModel.moveUnifiedItemToDate(moveTarget, tappedDate)
                                                        movingItem = null
                                                    }
                                                    stampTpl != null -> {
                                                        viewModel.stampTemplateOnDate(stampTpl, tappedDate)
                                                        viewModel.selectDate(tappedDate)
                                                    }
                                                    else -> viewModel.selectDate(tappedDate)
                                                }
                                            },
                                            onTapAlreadySelectedDate = { tappedDate ->
                                                val moveTarget = movingItem
                                                val stampTpl = activeStampTemplate
                                                when {
                                                    moveTarget != null -> {
                                                        viewModel.moveUnifiedItemToDate(moveTarget, tappedDate)
                                                        movingItem = null
                                                    }
                                                    stampTpl != null -> viewModel.stampTemplateOnDate(stampTpl, tappedDate)
                                                    else -> floatingTimelinePopupDate = tappedDate
                                                }
                                            },
                                            onSelectItem = { viewModel.setFocusedItem(it) },
                                            onLongPressItem = { movingItem = it },
                                            onShiftEventTimeMinutes = { ev, deltaMin ->
                                                viewModel.shiftUnifiedItemTimeMinutes(ev, deltaMin)
                                            },
                                            onSelectSecondaryTimeZone = { tzId ->
                                                viewModel.setSecondaryTimeZoneId(tzId)
                                            },
                                            onSwipePrevMonth = { viewModel.shiftMonth(-1) },
                                            onSwipeNextMonth = { viewModel.shiftMonth(1) },
                                            onVerticalDragDelta = { deltaY ->
                                                if (deltaY < -18f && floatingTimelinePopupDate == null) {
                                                    floatingTimelinePopupDate = selectedDate
                                                }
                                            },
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
                                            onAddEventForDate = { viewModel.openNewEditor(date = it, presetWomensHealth = onlyWomensHealthFilter) },
                                            onEditItem = { viewModel.openEditForUnifiedItem(it) },
                                            onDeleteItem = { viewModel.deleteUnifiedItem(it) },
                                            onShareItem = { item ->
                                                runCatching {
                                                    val meta = richMetaMap[com.dx.calendar.data.local.EventRichMetaStore.makeKey(item.sourceType.name, item.rawId)]
                                                    val commentsBlock = meta?.sharedComments?.takeIf { it.isNotEmpty() }?.joinToString("\n") {
                                                        "・${it.author}：${it.content}"
                                                    }
                                                    val mapUrl = if (item.location.isNotBlank()) "\n導航：https://maps.google.com/?q=${Uri.encode(item.location)}" else ""
                                                    val textBody = buildString {
                                                        append("【${item.title}】\n日期：${item.date}")
                                                        if (item.location.isNotBlank()) append("\n地點：${item.location}$mapUrl")
                                                        if (item.notes.isNotBlank()) append("\n備註：${item.notes}")
                                                        if (!commentsBlock.isNullOrBlank()) append("\n\n💬 協作留言：\n$commentsBlock")
                                                    }
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, textBody)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "分享行程給家人／另一半"))
                                                }
                                            },
                                            onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) },
                                            onHideExternalToRoutine = { viewModel.toggleExternalSeriesOverride(it) },
                                            dayWeather = weatherByDate[selectedDate],
                                            richMetaMap = richMetaMap,
                                            onStartPomodoro = { item ->
                                                pomodoroTargetTitle = item.title
                                                pomodoroCompleteAction = if (item.isTask) { { viewModel.toggleUnifiedItemComplete(item) } } else null
                                                showPomodoroDialog = true
                                            },
                                            onBeginMoveItem = { item ->
                                                viewModel.setFocusedItem(null)
                                                movingItem = item
                                            },
                                            onAddSharedComment = { item, text ->
                                                viewModel.addSharedComment(item, text)
                                            }
                                        )
                                    }
                                }
                            } else {
                                // 手機直向 / 小視窗模式：完整實作仿三星日曆「上下滑動手勢連動動畫（滿版月曆 ⇄ 壓縮彩條月曆＋下方當日時間表）」！
                                val sheetActive = viewMode == CalendarViewMode.MONTH &&
                                    (showCompactBottomDayPanel || focusedItem != null) &&
                                    spec.heightDp >= 440.dp
                                val targetProgress = if (isDraggingSheet) {
                                    sheetDragFraction.coerceIn(0f, 1f)
                                } else {
                                    if (sheetActive) 1f else 0f
                                }
                                val animatedSheetProgress by animateFloatAsState(
                                    targetValue = targetProgress,
                                    animationSpec = spring(
                                        dampingRatio = 0.74f,
                                        stiffness = 400f
                                    ),
                                    label = "samsungMonthSheetProgress"
                                )

                                val density = LocalDensity.current
                                var compactViewportHeightPx by remember { mutableIntStateOf(0) }
                                val fallbackTotalH = (spec.heightDp - 140.dp).coerceAtLeast(240.dp)
                                val totalH = if (compactViewportHeightPx > 0) {
                                    with(density) { compactViewportHeightPx.toDp() }.coerceAtLeast(1.dp)
                                } else {
                                    fallbackTotalH
                                }
                                val canSplitVertically = viewMode == CalendarViewMode.MONTH && totalH >= 360.dp
                                val progress = if (canSplitVertically) {
                                    (if (isDraggingSheet) sheetDragFraction else animatedSheetProgress).coerceIn(0f, 1f)
                                } else 0f
                                // 月曆從 100% 滿版平滑壓縮至 44%（同時日期下方的行程標籤淡出轉為彩色細條），下方時間表由 0% 平滑滑升至 56%！
                                val calendarHeight = (totalH * (1f - 0.56f * progress)).coerceIn(1.dp, totalH)
                                val sheetHeight = (totalH - calendarHeight).coerceAtLeast(0.dp)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onSizeChanged { sz ->
                                            if (sz.height > 0 && sz.height != compactViewportHeightPx) {
                                                compactViewportHeightPx = sz.height
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(if (canSplitVertically) calendarHeight else totalH)
                                        ) {
                                            CalendarPrimaryViewport(
                                                viewMode = viewMode,
                                                currentYm = currentYm,
                                                selectedDate = selectedDate,
                                                today = today,
                                                itemsByDate = itemsByDate,
                                                weatherByDate = weatherByDate,
                                                allRangeItems = displayRangeItems,
                                                focusedItem = focusedItem,
                                                settings = settings,
                                                maxEventsInCell = spec.maxEventsInMonthCell,
                                                yearColumns = spec.yearGridColumns,
                                                compactSheetProgress = progress,
                                                hideLunarForCompactHeight = spec.hideLunarInMonthCell,
                                                onSelectMonthFromYear = { ym ->
                                                    viewModel.selectDate(ym.atDay(1))
                                                    viewModel.setViewMode(CalendarViewMode.MONTH)
                                                },
                                                onSelectDate = { tappedDate ->
                                                    val moveTarget = movingItem
                                                    val stampTpl = activeStampTemplate
                                                    when {
                                                        moveTarget != null -> {
                                                            viewModel.moveUnifiedItemToDate(moveTarget, tappedDate)
                                                            movingItem = null
                                                        }
                                                        stampTpl != null -> {
                                                            viewModel.stampTemplateOnDate(stampTpl, tappedDate)
                                                            viewModel.selectDate(tappedDate)
                                                        }
                                                        else -> viewModel.selectDate(tappedDate)
                                                    }
                                                },
                                                onTapAlreadySelectedDate = { tappedDate ->
                                                    val moveTarget = movingItem
                                                    val stampTpl = activeStampTemplate
                                                    when {
                                                        moveTarget != null -> {
                                                            viewModel.moveUnifiedItemToDate(moveTarget, tappedDate)
                                                            movingItem = null
                                                        }
                                                        stampTpl != null -> viewModel.stampTemplateOnDate(stampTpl, tappedDate)
                                                        else -> floatingTimelinePopupDate = tappedDate
                                                    }
                                                },
                                                onSelectItem = {
                                                    viewModel.setFocusedItem(it)
                                                    showCompactBottomDayPanel = true
                                                    sheetDragFraction = 1f
                                                },
                                                onLongPressItem = { movingItem = it },
                                                onShiftEventTimeMinutes = { ev, deltaMin ->
                                                    viewModel.shiftUnifiedItemTimeMinutes(ev, deltaMin)
                                                },
                                                onSelectSecondaryTimeZone = { tzId ->
                                                    viewModel.setSecondaryTimeZoneId(tzId)
                                                },
                                                onSwipePrevMonth = { viewModel.shiftMonth(-1) },
                                                onSwipeNextMonth = { viewModel.shiftMonth(1) },
                                                onVerticalDragDelta = if (canSplitVertically) { deltaY ->
                                                    if (!isDraggingSheet) {
                                                        isDraggingSheet = true
                                                        sheetDragFraction = if (showCompactBottomDayPanel || focusedItem != null) 1f else 0f
                                                    }
                                                    // 往上滑 (deltaY < 0) 展開時間表；往下滑 (deltaY > 0) 收合回滿版月曆
                                                    sheetDragFraction = (sheetDragFraction - deltaY / 520f).coerceIn(0f, 1f)
                                                } else null,
                                                onVerticalDragEnd = if (canSplitVertically) {
                                                    {
                                                        val shouldOpen = sheetDragFraction >= 0.36f
                                                        showCompactBottomDayPanel = shouldOpen
                                                        if (!shouldOpen) viewModel.setFocusedItem(null)
                                                        sheetDragFraction = if (shouldOpen) 1f else 0f
                                                        isDraggingSheet = false
                                                    }
                                                } else null,
                                                onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) }
                                            )
                                        }

                                        if (canSplitVertically && sheetHeight > 2.dp) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(sheetHeight)
                                                    .background(tokens.surfaceCardColor.copy(alpha = 0.98f))
                                            ) {
                                                // 可直接上下拖曳收合／展開的握把列
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .pointerInput(Unit) {
                                                            detectVerticalDragGestures(
                                                                onDragStart = {
                                                                    isDraggingSheet = true
                                                                    sheetDragFraction = if (showCompactBottomDayPanel || focusedItem != null) 1f else 0f
                                                                },
                                                                onDragEnd = {
                                                                    val shouldOpen = sheetDragFraction >= 0.45f
                                                                    showCompactBottomDayPanel = shouldOpen
                                                                    if (!shouldOpen) viewModel.setFocusedItem(null)
                                                                    sheetDragFraction = if (shouldOpen) 1f else 0f
                                                                    isDraggingSheet = false
                                                                },
                                                                onDragCancel = {
                                                                    isDraggingSheet = false
                                                                },
                                                                onVerticalDrag = { _, dragY ->
                                                                    sheetDragFraction = (sheetDragFraction - dragY / 520f).coerceIn(0f, 1f)
                                                                }
                                                            )
                                                        }
                                                        .padding(top = 6.dp, bottom = 4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(38.dp)
                                                            .height(4.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(tokens.accentColor.copy(alpha = 0.55f))
                                                    )
                                                }
                                                RightDetailPanel(
                                                    selectedDate = selectedDate,
                                                    today = today,
                                                    dayItems = selectedDayItems,
                                                    focusedItem = focusedItem,
                                                    onClearFocusedItem = { viewModel.setFocusedItem(null) },
                                                    onSelectItem = { viewModel.setFocusedItem(it) },
                                                    onAddEventForDate = { viewModel.openNewEditor(date = it, presetWomensHealth = onlyWomensHealthFilter) },
                                                    onEditItem = { viewModel.openEditForUnifiedItem(it) },
                                                    onDeleteItem = { viewModel.deleteUnifiedItem(it) },
                                                    onShareItem = { item ->
                                                        runCatching {
                                                            val meta = richMetaMap[com.dx.calendar.data.local.EventRichMetaStore.makeKey(item.sourceType.name, item.rawId)]
                                                            val commentsBlock = meta?.sharedComments?.takeIf { it.isNotEmpty() }?.joinToString("\n") {
                                                                "・${it.author}：${it.content}"
                                                            }
                                                            val mapUrl = if (item.location.isNotBlank()) "\n導航：https://maps.google.com/?q=${Uri.encode(item.location)}" else ""
                                                            val textBody = buildString {
                                                                append("【${item.title}】\n日期：${item.date}")
                                                                if (item.location.isNotBlank()) append("\n地點：${item.location}$mapUrl")
                                                                if (item.notes.isNotBlank()) append("\n備註：${item.notes}")
                                                                if (!commentsBlock.isNullOrBlank()) append("\n\n💬 協作留言：\n$commentsBlock")
                                                            }
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, textBody)
                                                            }
                                                            context.startActivity(Intent.createChooser(shareIntent, "分享行程給家人／另一半"))
                                                        }
                                                    },
                                                    onToggleComplete = { viewModel.toggleUnifiedItemComplete(it) },
                                                    onHideExternalToRoutine = { viewModel.toggleExternalSeriesOverride(it) },
                                                    showCloseDayHeaderButton = true,
                                                    onCloseDayPanel = {
                                                        viewModel.setFocusedItem(null)
                                                        showCompactBottomDayPanel = false
                                                        sheetDragFraction = 0f
                                                    },
                                                    dayWeather = weatherByDate[selectedDate],
                                                    richMetaMap = richMetaMap,
                                                    onStartPomodoro = { item ->
                                                        pomodoroTargetTitle = item.title
                                                        pomodoroCompleteAction = if (item.isTask) { { viewModel.toggleUnifiedItemComplete(item) } } else null
                                                        showPomodoroDialog = true
                                                    },
                                                    onBeginMoveItem = { item ->
                                                        viewModel.setFocusedItem(null)
                                                        showCompactBottomDayPanel = false
                                                        sheetDragFraction = 0f
                                                        movingItem = item
                                                    },
                                                    onAddSharedComment = { item, text ->
                                                        viewModel.addSharedComment(item, text)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 懸浮毛玻璃雙膠囊導覽列（寬度自適應，避免窄手機／摺疊分割裁切 now 按鈕；隱私入口僅於設定開啟後顯示）
                    if (tokens.useFloatingOriginCapsule && !spec.isShortSplit) {
                        VivoFloatingCapsuleBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp, start = 10.dp, end = 10.dp),
                            currentSection = mainSection,
                            isNotToday = selectedDate != today,
                            narrow = spec.isNarrowSplit,
                            showPrivacyTab = false,
                            isPrivacyUnlocked = isPrivacyUnlocked,
                            onSelectSection = { sec ->
                                viewModel.setMainSection(sec)
                            },
                            onJumpToday = { viewModel.jumpToToday() },
                            onVoiceQuickAdd = {
                                autoLaunchVoiceInNlp = true
                                showQuickNlpDialog = true
                            },
                            onQuickAdd = {
                                viewModel.openNewEditor(
                                    date = selectedDate,
                                    presetRoutine = mainSection == MainSection.ROUTINES,
                                    presetPrivacy = mainSection == MainSection.PRIVACY && isPrivacyUnlocked,
                                    presetTaskMode = false,
                                    presetWomensHealth = onlyWomensHealthFilter
                                )
                            }
                        )
                    }
                }

                // 經典底部導覽列 或 橫扁上下分割視窗（使用 48dp 極簡低高度底欄，保留月曆可視高度）
                if (!tokens.useFloatingOriginCapsule || spec.isShortSplit) {
                    ClassicBottomBar(
                        currentSection = mainSection,
                        compactHeight = spec.isShortSplit,
                        onSelectSection = { sec ->
                            viewModel.setMainSection(sec)
                        },
                        onJumpToday = { viewModel.jumpToToday() },
                        onVoiceQuickAdd = {
                            autoLaunchVoiceInNlp = true
                            showQuickNlpDialog = true
                        },
                        onQuickAdd = {
                            viewModel.openNewEditor(
                                date = selectedDate,
                                presetRoutine = mainSection == MainSection.ROUTINES,
                                presetPrivacy = mainSection == MainSection.PRIVACY && isPrivacyUnlocked,
                                presetTaskMode = false,
                                presetWomensHealth = onlyWomensHealthFilter
                            )
                        }
                    )
                }
            }

            // 仿三星日曆 0:18–0:20 浮動 24 小時時間軸卡片彈窗（Q 彈縮放＋淡入動畫）
            AnimatedVisibility(
                visible = floatingTimelinePopupDate != null,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.82f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.88f),
                modifier = Modifier.fillMaxSize()
            ) {
                floatingTimelinePopupDate?.let { popupDate ->
                    SamsungDayTimelinePopupCard(
                        date = popupDate,
                        dayItems = itemsByDate[popupDate] ?: emptyList(),
                        onDismiss = { floatingTimelinePopupDate = null },
                        onSelectItem = { item ->
                            viewModel.setFocusedItem(item)
                            if (!spec.showDualPane) {
                                showCompactBottomDayPanel = true
                                sheetDragFraction = 1f
                            }
                        },
                        onAddEventForDate = { d -> viewModel.openNewEditor(date = d, presetWomensHealth = onlyWomensHealthFilter) },
                        onToggleTaskComplete = { viewModel.toggleUnifiedItemComplete(it) }
                    )
                }
            }
        }

        // Q 彈全畫面覆蓋：DX 設定工作台
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInHorizontally(spring(dampingRatio = 0.74f, stiffness = 440f)) { it / 2 } +
                scaleIn(spring(dampingRatio = 0.74f, stiffness = 440f), initialScale = 0.94f) +
                fadeIn(),
            exit = slideOutHorizontally(spring(dampingRatio = 0.90f, stiffness = 520f)) { it / 2 } +
                scaleOut(targetScale = 0.96f) +
                fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            BackHandler(enabled = isSettingsOpen && !editorState.isOpen) {
                viewModel.openSettings(false)
            }
            SettingsAndManageScreen(
                settings = settings,
                updateState = updateState,
                categories = categories,
                systemCalendars = systemCalendars,
                hasCalendarPermission = hasCalendarPermission,
                isSyncingTasks = isSyncingTasks,
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
                onSelectWeekStartMode = { viewModel.setWeekStartMode(it) },
                onToggleShowLunar = { viewModel.setShowLunar(it) },
                onSelectAlternateCalendar = { viewModel.setAlternateCalendarMode(it) },
                onSelectDateInfoDisplay = { viewModel.setDateInfoDisplayMode(it) },
                onSelectMonthViewLayout = { viewModel.setMonthViewLayoutMode(it) },
                onSelectTimeZoneOverride = { viewModel.setTimeZoneOverrideId(it) },
                onSelectSecondaryTimeZone = { viewModel.setSecondaryTimeZoneId(it) },
                onChangeBackupDeviceName = { viewModel.setBackupDeviceName(it) },
                onUploadBackupToGoogleDrive = onUploadBackupToGoogleDrive,
                onExportFullBackupFile = onExportFullBackupFile,
                onRestoreFullBackupFile = onRestoreFullBackupFile,
                onImportContactBirthdays = onImportContactBirthdays,
                onOpenFeatureGuide = {
                    viewModel.openSettings(false)
                    showFeatureGuideDialog = true
                },
                onUpdateHolidayPrefs = { pub, trad, solar -> viewModel.setHolidayPreferences(pub, trad, solar) },
                onUpdateDefaultEventReminders = { viewModel.setDefaultEventReminders(it) },
                onUpdateDefaultTaskReminders = { viewModel.setDefaultTaskReminders(it) },
                onToggleShowWeekNum = { viewModel.setShowWeekNum(it) },
                onToggleGridLines = { viewModel.setShowGridLines(it) },
                onTogglePeekHiddenInMonth = { viewModel.setPeekHiddenInMonth(it) },
                onToggleRoutineMedicationFeature = { viewModel.setRoutineMedicationFeatureEnabled(it) },
                onTogglePrivacyFeature = { enable ->
                    if (enable) {
                        onTriggerDualBiometric {
                            viewModel.setPrivacyFeatureEnabled(true)
                            android.widget.Toast.makeText(
                                context,
                                "驗證通過！已建立隱私日曆，請至月曆頂欄點擊眼睛按鈕檢視",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        viewModel.setPrivacyUnlocked(false)
                        viewModel.setPrivacyFeatureEnabled(false)
                    }
                },
                onToggleRequireDualFingerprint = { viewModel.setRequireDualFingerprint(true) },
                onOpenPrivacyVault = {
                    viewModel.openSettings(false)
                    viewModel.setMainSection(MainSection.CALENDAR)
                },
                onToggleShowCompletedTasks = { viewModel.setShowCompletedTasks(it) },
                onSyncGoogleTasks = onLaunchGoogleAccountPicker,
                onSyncGoogleTasksForEmail = { email -> viewModel.syncGoogleTasksWithEmail(email) },
                onAddGoogleAccount = onAddGoogleAccount,
                onAddSystemGoogleAccount = onAddSystemGoogleAccount,
                onRemoveGoogleAccount = { email -> viewModel.removeGoogleAccountEmail(email) },
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
                        runCatching {
                            context.startActivity(Intent.createChooser(it, "匯出分享 DX日曆 (.ics)"))
                        }
                    }
                },
                onTestMultiCallAlarmDemo = { MultiCallAlarmScheduler.triggerImmediateDemoAlarm(context) },
                liveUpdateStatus = com.dx.calendar.alarm.LiveUpdateIslandHelper.getStatus(context),
                onToggleDynamicDayIcon = { viewModel.setDynamicDayIconEnabled(it) },
                onToggleLiveUpdateIsland = { viewModel.setLiveUpdateIslandEnabled(it) },
                onOpenLiveUpdateSettings = { com.dx.calendar.alarm.LiveUpdateIslandHelper.openPromotionSettings(context) },
                onTestLiveUpdateIsland = { viewModel.triggerLiveUpdateIslandDemo() },
                onCancelLiveUpdateIsland = { viewModel.cancelLiveUpdateIslandDemo() }
            )
        }

        // Q 彈全畫面覆蓋：活動／待辦／例行編輯器
        AnimatedVisibility(
            visible = editorState.isOpen,
            enter = slideInVertically(spring(dampingRatio = 0.70f, stiffness = 420f)) { it / 2 } +
                scaleIn(spring(dampingRatio = 0.70f, stiffness = 420f), initialScale = 0.92f) +
                fadeIn(),
            exit = slideOutVertically(spring(dampingRatio = 0.90f, stiffness = 520f)) { it / 2 } +
                scaleOut(targetScale = 0.94f) +
                fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            BackHandler(enabled = editorState.isOpen) {
                viewModel.closeEditor()
            }
            EventEditorScreen(
                initialDate = editorState.initialDate,
                editingEvent = editorState.editingEvent,
                editingTask = editorState.editingTask,
                editingSystemEvent = editorState.editingSystemEvent,
                editingSystemCalendarId = editorState.editingSystemCalendarId,
                presetRoutine = editorState.presetRoutine,
                presetPrivacy = editorState.presetPrivacy,
                presetTaskMode = editorState.presetTaskMode,
                presetWomensHealth = editorState.presetWomensHealth,
                privacyFeatureEnabled = settings.privacyFeatureEnabled,
                allowPrivacyEditing = settings.privacyFeatureEnabled && isPrivacyUnlocked,
                routineDefaultHideFromMonth = settings.routineDefaultHideFromMonth,
                defaultEventReminders = settings.defaultEventReminders,
                defaultTaskReminders = settings.defaultTaskReminders,
                categories = categories,
                writableSystemCalendars = systemCalendars.filter { it.isWritable },
                defaultGoogleEmail = settings.googleAccountEmail,
                hasCalendarPermission = hasCalendarPermission,
                onRequestCalendarPermission = onRequestCalendarPermission,
                titleHistory = titleHistory,
                parseIntList = { viewModel.repo.parseIntList(it) },
                parseDateList = { viewModel.repo.parseDateStrings(it) },
                onCancel = { viewModel.closeEditor() },
                onSave = { payload -> viewModel.saveFromEditor(payload) },
                onDelete = if (editorState.editingEvent != null || editorState.editingTask != null || editorState.editingSystemEvent != null) {
                    { viewModel.deleteEditingItem() }
                } else null,
                initialRichMeta = when {
                    editorState.editingEvent != null -> richMetaMap[com.dx.calendar.data.local.EventRichMetaStore.makeKey(com.dx.calendar.data.repository.UnifiedSourceType.LOCAL_EVENT.name, editorState.editingEvent!!.id)]
                    editorState.editingTask != null -> richMetaMap[com.dx.calendar.data.local.EventRichMetaStore.makeKey(com.dx.calendar.data.repository.UnifiedSourceType.TASK.name, editorState.editingTask!!.id)]
                    editorState.editingSystemEvent != null -> richMetaMap[com.dx.calendar.data.local.EventRichMetaStore.makeKey(com.dx.calendar.data.repository.UnifiedSourceType.SYSTEM_EVENT.name, editorState.editingSystemEvent!!.rawId)]
                    else -> null
                },
                onPickAttachmentImage = onPickEventAttachment,
                onSaveAsTemplate = { tpl -> viewModel.saveEventTemplate(tpl) },
                onLaunchVoiceInput = onLaunchVoiceInput
            )
        }
        }

        // === v2.0 快捷效率彈窗：離線一句話／語音快速建行程、新增範本、番茄鐘專注 ===
        if (showQuickNlpDialog) {
            com.dx.calendar.ui.components.QuickNaturalLanguageDialog(
                today = today,
                autoLaunchVoice = autoLaunchVoiceInNlp,
                onDismiss = {
                    showQuickNlpDialog = false
                    autoLaunchVoiceInNlp = false
                },
                onLaunchVoice = onLaunchVoiceInput,
                onConfirmCreate = { text -> viewModel.quickCreateFromNaturalLanguage(text) }
            )
        }
        if (showNewTemplateDialog) {
            com.dx.calendar.ui.components.NewTemplateDialog(
                onDismiss = { showNewTemplateDialog = false },
                onSave = { tpl -> viewModel.saveEventTemplate(tpl) }
            )
        }
        if (showPomodoroDialog) {
            com.dx.calendar.ui.components.PomodoroFocusDialog(
                taskTitle = pomodoroTargetTitle,
                onDismiss = { showPomodoroDialog = false },
                onCompleteTask = pomodoroCompleteAction
            )
        }
        if (showFeatureGuideDialog) {
            com.dx.calendar.ui.components.FeatureGuideDialog(
                onDismiss = { showFeatureGuideDialog = false },
                onOpenVoiceQuickAdd = {
                    autoLaunchVoiceInNlp = true
                    showQuickNlpDialog = true
                },
                onOpenShiftStamp = {
                    viewModel.setMainSection(MainSection.CALENDAR)
                    viewModel.setViewMode(CalendarViewMode.MONTH)
                    showTemplateStampBar = true
                },
                onOpenPomodoro = {
                    pomodoroTargetTitle = null
                    pomodoroCompleteAction = null
                    showPomodoroDialog = true
                },
                onToggleWomensHealthFilter = {
                    viewModel.setOnlyWomensHealthFilter(true)
                },
                onImportContactBirthdays = onImportContactBirthdays,
                onOpenWeekTimeline = {
                    viewModel.setMainSection(MainSection.CALENDAR)
                    viewModel.setViewMode(CalendarViewMode.WEEK)
                },
                onOpenSettings = { viewModel.openSettings(true) }
            )
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
                                        "正在自動下載更新包：$pct% (" + "%.1f / %.1f MB".format(dlMb, totMb) + ")"
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
    narrow: Boolean,
    privacyFeatureEnabled: Boolean,
    isPrivacyUnlocked: Boolean,
    onlyWomensHealthFilter: Boolean = false,
    showTemplateStampBar: Boolean = false,
    onPrevPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onJumpToToday: () -> Unit = {},
    onOpenQuickNlp: () -> Unit = {},
    onToggleWomensHealthFilter: () -> Unit = {},
    onToggleTemplateBar: () -> Unit = {},
    onOpenPomodoro: () -> Unit = {},
    onImportContactBirthdays: () -> Unit = {},
    onOpenFeatureGuide: () -> Unit = {},
    onJumpToDate: () -> Unit,
    onOpenAlmanacDiscovery: () -> Unit,
    onQuickPrivacyAccess: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    var showOverflow by remember { mutableStateOf(false) }
    val iconBtnSize = if (narrow) 36.dp else 44.dp

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = if (narrow) 6.dp else 10.dp, vertical = 5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 年月標題（雙色年份）
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevPeriod, modifier = Modifier.size(if (narrow) 24.dp else 28.dp)) {
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
                    fontSize = when {
                        narrow -> 18.sp
                        compact -> 20.sp
                        else -> 24.sp
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.clickable { onJumpToDate() }
                )
                IconButton(onClick = onNextPeriod, modifier = Modifier.size(if (narrow) 24.dp else 28.dp)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一期", tint = tokens.secondaryTextColor)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 大螢幕或橫扁寬分割視窗直接於同行顯示「年 / 月 / 週 / 日 / 時間表」＋「女性健康 / 排班 / 番茄」快捷按鈕
            if (!compact && mainSection == MainSection.CALENDAR) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewModeSegmentedPill(current = viewMode, onSelect = onSelectViewMode)
                    QuickToolMiniPill(
                        label = "🌸健康",
                        onClick = onToggleWomensHealthFilter,
                        active = onlyWomensHealthFilter,
                        activeColor = Color(com.dx.calendar.data.model.WomensHealthPresets.CATEGORY_COLOR_ARGB)
                    )
                    QuickToolMiniPill(
                        label = "🏷️排班",
                        onClick = onToggleTemplateBar,
                        active = showTemplateStampBar
                    )
                    QuickToolMiniPill("🍅番茄", onOpenPomodoro)
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // 頂部固定預留「now」快速回到今天按鈕
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.accentColor.copy(alpha = 0.16f))
                    .border(1.dp, tokens.accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable { onJumpToToday() }
                    .padding(horizontal = if (narrow) 7.dp else 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "now",
                    color = tokens.accentColor,
                    fontSize = if (narrow) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 唯有在設定頁建立隱私日曆後，才於月曆頂欄顯示「眼睛」按鈕（點擊閃爍並提示使用指紋，不透露雙指紋機制）
            if (privacyFeatureEnabled && mainSection == MainSection.CALENDAR) {
                var eyePulseStep by remember { mutableIntStateOf(0) }
                val eyeScale by animateFloatAsState(
                    targetValue = if (eyePulseStep % 2 == 1) 1.28f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
                    label = "privacyEyeScale"
                )
                val eyeAlpha by animateFloatAsState(
                    targetValue = if (eyePulseStep % 2 == 1) 0.35f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "privacyEyeAlpha"
                )
                LaunchedEffect(eyePulseStep) {
                    if (eyePulseStep > 0 && eyePulseStep % 2 == 1) {
                        kotlinx.coroutines.delay(140L)
                        eyePulseStep += 1
                    }
                }
                IconButton(
                    onClick = {
                        eyePulseStep += 1
                        onQuickPrivacyAccess()
                    },
                    modifier = Modifier.size(iconBtnSize)
                ) {
                    Icon(
                        imageVector = if (isPrivacyUnlocked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (isPrivacyUnlocked) "隱藏隱私日曆" else "使用指紋檢視隱私日曆",
                        tint = if (isPrivacyUnlocked) tokens.accentColor else tokens.secondaryTextColor,
                        modifier = Modifier.graphicsLayer {
                            scaleX = eyeScale
                            scaleY = eyeScale
                            alpha = eyeAlpha
                        }
                    )
                }
            }

            IconButton(onClick = onOpenSearch, modifier = Modifier.size(iconBtnSize)) {
                Icon(Icons.Filled.Search, contentDescription = "搜尋", tint = tokens.primaryTextColor)
            }

            IconButton(onClick = onOpenSettings, modifier = Modifier.size(iconBtnSize)) {
                Icon(Icons.Filled.Settings, contentDescription = "主題與設定", tint = tokens.primaryTextColor)
            }

            Box {
                IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(iconBtnSize)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多功能", tint = tokens.primaryTextColor)
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text("🎙️ 一句話／語音快速建行程") },
                        leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null, tint = tokens.accentColor) },
                        onClick = { showOverflow = false; onOpenQuickNlp() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (onlyWomensHealthFilter) "🌸 結束女性健康篩選（恢復全部）"
                                else "🌸 臨時只檢視「女性健康」類別"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = Color(com.dx.calendar.data.model.WomensHealthPresets.CATEGORY_COLOR_ARGB)
                            )
                        },
                        onClick = { showOverflow = false; onToggleWomensHealthFilter() }
                    )
                    DropdownMenuItem(
                        text = { Text("🏷️ 輪班／常用範本連點排班") },
                        leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                        onClick = { showOverflow = false; onToggleTemplateBar() }
                    )
                    DropdownMenuItem(
                        text = { Text("🍅 番茄鐘專注計時器") },
                        leadingIcon = { Icon(Icons.Filled.Today, contentDescription = null) },
                        onClick = { showOverflow = false; onOpenPomodoro() }
                    )
                    DropdownMenuItem(
                        text = { Text("🎂 匯入聯絡人生日（自動算歲數）") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                        onClick = { showOverflow = false; onImportContactBirthdays() }
                    )
                    DropdownMenuItem(
                        text = { Text("📖 功能指南與快捷按鈕") },
                        leadingIcon = { Icon(Icons.Filled.Explore, contentDescription = null, tint = tokens.accentColor) },
                        onClick = { showOverflow = false; onOpenFeatureGuide() }
                    )
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
                }
            }
        }

        // 手機與小視窗：第二行顯示「年 / 月 / 週 / 日 / 時間表」＋「健康 / 排班 / 番茄」一鍵快捷膠囊（單層水平滾動，零額外高度！）
        if (compact && mainSection == MainSection.CALENDAR) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                ViewModeSegmentedPill(current = viewMode, onSelect = onSelectViewMode)
                QuickToolMiniPill(
                    label = "🌸健康",
                    onClick = onToggleWomensHealthFilter,
                    active = onlyWomensHealthFilter,
                    activeColor = Color(com.dx.calendar.data.model.WomensHealthPresets.CATEGORY_COLOR_ARGB)
                )
                QuickToolMiniPill(
                    label = "🏷️排班",
                    onClick = onToggleTemplateBar,
                    active = showTemplateStampBar
                )
                QuickToolMiniPill("🍅番茄", onOpenPomodoro)
            }
        }
    }
}

@Composable
private fun QuickToolMiniPill(
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    activeColor: Color? = null
) {
    val tokens = LocalBrandTokens.current
    val highlight = activeColor ?: tokens.accentColor
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) highlight else tokens.surfaceCardColor)
            .border(
                width = if (active) 1.2.dp else 0.8.dp,
                color = if (active) highlight else highlight.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (active) Color.White else highlight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ViewModeSegmentedPill(
    current: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit
) {
    val tokens = LocalBrandTokens.current
    // 注意：外層容器已具備 horizontalScroll，此處嚴禁巢狀 horizontalScroll 以免在手機上引發 Infinity maxWidth 閃退！
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tokens.surfaceCardColor)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CalendarViewMode.entries.forEach { mode ->
            val selected = current == mode
            val pillScale by animateFloatAsState(
                targetValue = if (selected) 1.0f else 0.94f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 460f),
                label = "viewModePillScale_${mode.name}"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pillScale
                        scaleY = pillScale
                    }
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
    weatherByDate: Map<LocalDate, DailyWeatherInfo> = emptyMap(),
    allRangeItems: List<com.dx.calendar.data.repository.UnifiedCalendarItem>,
    focusedItem: com.dx.calendar.data.repository.UnifiedCalendarItem?,
    settings: com.dx.calendar.data.local.UserSettings,
    maxEventsInCell: Int,
    yearColumns: Int,
    compactSheetProgress: Float = 0f,
    hideLunarForCompactHeight: Boolean = false,
    onSelectMonthFromYear: (java.time.YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onTapAlreadySelectedDate: ((LocalDate) -> Unit)? = null,
    onSelectItem: (com.dx.calendar.data.repository.UnifiedCalendarItem) -> Unit,
    onSwipePrevMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit,
    onVerticalDragDelta: ((Float) -> Unit)? = null,
    onVerticalDragEnd: (() -> Unit)? = null,
    onToggleComplete: (com.dx.calendar.data.repository.UnifiedCalendarItem) -> Unit,
    onLongPressItem: ((com.dx.calendar.data.repository.UnifiedCalendarItem) -> Unit)? = null,
    onShiftEventTimeMinutes: ((com.dx.calendar.data.repository.UnifiedCalendarItem, Int) -> Unit)? = null,
    onSelectSecondaryTimeZone: ((String) -> Unit)? = null
) {
    AnimatedContent(
        targetState = viewMode,
        transitionSpec = {
            val zoomingIn = targetState.ordinal > initialState.ordinal
            val bounceSpec = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
            if (zoomingIn) {
                (scaleIn(bounceSpec, initialScale = 0.88f) + fadeIn()).togetherWith(
                    scaleOut(targetScale = 1.08f) + fadeOut()
                )
            } else {
                (scaleIn(bounceSpec, initialScale = 1.12f) + fadeIn()).togetherWith(
                    scaleOut(targetScale = 0.88f) + fadeOut()
                )
            }
        },
        label = "calendarViewModeZoomTransition"
    ) { activeMode ->
        when (activeMode) {
            CalendarViewMode.YEAR -> AnimatedContent(
                targetState = currentYm.year,
                transitionSpec = {
                    val forward = targetState >= initialState
                    val slideSpec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.74f, stiffness = 420f)
                    (slideInHorizontally(slideSpec) { if (forward) it / 3 else -it / 3 } + fadeIn()).togetherWith(
                        slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()
                    )
                },
                label = "yearSlideTransition"
            ) { animYear ->
                YearGridView(
                    year = animYear,
                    today = today,
                    startWeekOnMonday = settings.startWeekOnMonday,
                    columns = yearColumns,
                    onSelectMonth = onSelectMonthFromYear
                )
            }

            CalendarViewMode.MONTH -> AnimatedContent(
                targetState = currentYm,
                transitionSpec = {
                    val forward = !targetState.isBefore(initialState)
                    val slideSpec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.74f, stiffness = 420f)
                    (slideInHorizontally(slideSpec) { if (forward) it / 2 else -it / 2 } + fadeIn()).togetherWith(
                        slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut()
                    )
                },
                label = "monthSlideTransition"
            ) { animYm ->
                MonthGridView(
                    yearMonth = animYm,
                    selectedDate = selectedDate,
                    today = today,
                    itemsByDate = itemsByDate,
                    weatherByDate = weatherByDate,
                    settings = settings,
                    maxEventsPerCell = maxEventsInCell,
                    compactSheetProgress = compactSheetProgress,
                    hideLunarForCompactHeight = hideLunarForCompactHeight,
                    onSelectDate = onSelectDate,
                    onTapAlreadySelectedDate = onTapAlreadySelectedDate,
                    onSwipePrevMonth = onSwipePrevMonth,
                    onSwipeNextMonth = onSwipeNextMonth,
                    onVerticalDragDelta = onVerticalDragDelta,
                    onVerticalDragEnd = onVerticalDragEnd,
                    onToggleTaskQuick = onToggleComplete,
                    onLongPressItem = onLongPressItem
                )
            }

            CalendarViewMode.WEEK -> WeekOrDayTimelineView(
                isSingleDay = false,
                selectedDate = selectedDate,
                today = today,
                startWeekOnMonday = settings.startWeekOnMonday,
                itemsByDate = itemsByDate,
                onSelectDate = onSelectDate,
                onSelectItem = onSelectItem,
                secondaryTimeZoneId = settings.secondaryTimeZoneId,
                onSelectSecondaryTimeZone = onSelectSecondaryTimeZone,
                onShiftEventTimeMinutes = onShiftEventTimeMinutes
            )

            CalendarViewMode.DAY -> WeekOrDayTimelineView(
                isSingleDay = true,
                selectedDate = selectedDate,
                today = today,
                startWeekOnMonday = settings.startWeekOnMonday,
                itemsByDate = itemsByDate,
                onSelectDate = onSelectDate,
                onSelectItem = onSelectItem,
                secondaryTimeZoneId = settings.secondaryTimeZoneId,
                onSelectSecondaryTimeZone = onSelectSecondaryTimeZone,
                onShiftEventTimeMinutes = onShiftEventTimeMinutes
            )

            CalendarViewMode.AGENDA -> AgendaScheduleListView(
                items = allRangeItems,
                selectedItem = focusedItem,
                onSelectItem = onSelectItem,
                onToggleTaskComplete = onToggleComplete
            )
        }
    }
}

@Composable
private fun VivoFloatingCapsuleBar(
    modifier: Modifier,
    currentSection: MainSection,
    isNotToday: Boolean,
    narrow: Boolean,
    showPrivacyTab: Boolean,
    isPrivacyUnlocked: Boolean,
    onSelectSection: (MainSection) -> Unit,
    onJumpToday: () -> Unit,
    onVoiceQuickAdd: () -> Unit = {},
    onQuickAdd: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val tabHPad = if (narrow) 8.dp else if (showPrivacyTab) 10.dp else 13.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 6.dp else 10.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        // 左側毛玻璃懸浮主膠囊：日曆 / 待辦 / 例行中心（隱私僅於設定開啟後顯示）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(tokens.floatingPillColor)
                .border(0.8.dp, tokens.gridLineColor, RoundedCornerShape(30.dp))
                .padding(horizontal = if (narrow) 4.dp else 6.dp, vertical = 5.dp)
        ) {
            val tabs = buildList {
                add(Triple(MainSection.CALENDAR, "日曆", Icons.Filled.CalendarMonth))
                add(Triple(MainSection.TASKS, "待辦事項", Icons.Filled.CheckCircleOutline))
                add(Triple(MainSection.ROUTINES, "例行中心", Icons.Filled.Repeat))
                if (showPrivacyTab) {
                    add(Triple(MainSection.PRIVACY, "隱私", if (isPrivacyUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock))
                }
            }
            tabs.forEach { (sec, label, icon) ->
                val selected = currentSection == sec
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) tokens.elevatedSurfaceColor else Color.Transparent)
                        .clickable { onSelectSection(sec) }
                        .padding(horizontal = tabHPad, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) tokens.accentColor else tokens.secondaryTextColor,
                        modifier = Modifier.size(if (narrow) 17.dp else 19.dp)
                    )
                    Text(
                        text = label,
                        color = if (selected) tokens.accentColor else tokens.secondaryTextColor,
                        fontSize = if (narrow) 9.sp else 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        // 語音一句話快速建行程麥克風按鈕（常駐於 + 按鈕旁，一鍵開口建行程）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (narrow) 42.dp else 46.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(tokens.floatingPillColor)
                .border(1.2.dp, tokens.accentColor, CircleShape)
                .clickable { onVoiceQuickAdd() }
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "語音快速新增行程",
                tint = tokens.accentColor,
                modifier = Modifier.size(if (narrow) 20.dp else 22.dp)
            )
        }

        // 右側圓形「+」快速新增按鈕
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (narrow) 44.dp else 50.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(tokens.accentColor)
                .clickable { onQuickAdd() }
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新增行程", tint = Color.White, modifier = Modifier.size(if (narrow) 22.dp else 26.dp))
        }

        // 當不在今天時，浮現「now」圓形快捷返回鈕
        if (isNotToday) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (narrow) 42.dp else 46.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(tokens.floatingPillColor)
                    .border(1.dp, tokens.accentColor, CircleShape)
                    .clickable { onJumpToday() }
            ) {
                Text("now", color = tokens.accentColor, fontSize = if (narrow) 11.sp else 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClassicBottomBar(
    currentSection: MainSection,
    compactHeight: Boolean = false,
    onSelectSection: (MainSection) -> Unit,
    onJumpToday: () -> Unit,
    onVoiceQuickAdd: () -> Unit = {},
    onQuickAdd: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    if (compactHeight) {
        // 橫扁上下分割視窗專用 46dp 精簡底欄，避免 80dp NavigationBar 吃光月曆高度
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(tokens.surfaceCardColor)
                .padding(horizontal = 8.dp)
        ) {
            TextButton(onClick = onJumpToday) { Text("今天", fontSize = 12.sp, color = tokens.accentColor) }
            TextButton(onClick = { onSelectSection(MainSection.CALENDAR) }) {
                Text("日曆", fontSize = 12.sp, fontWeight = if (currentSection == MainSection.CALENDAR) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { onSelectSection(MainSection.TASKS) }) {
                Text("待辦", fontSize = 12.sp, fontWeight = if (currentSection == MainSection.TASKS) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { onSelectSection(MainSection.ROUTINES) }) {
                Text("例行", fontSize = 12.sp, fontWeight = if (currentSection == MainSection.ROUTINES) FontWeight.Bold else FontWeight.Normal)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, tokens.accentColor, CircleShape)
                    .clickable { onVoiceQuickAdd() }
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "語音快記", tint = tokens.accentColor, modifier = Modifier.size(17.dp))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tokens.accentColor)
                    .clickable { onQuickAdd() }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        return
    }
    NavigationBar(containerColor = tokens.surfaceCardColor, tonalElevation = 4.dp) {
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
            onClick = onVoiceQuickAdd,
            icon = { Icon(Icons.Filled.Mic, contentDescription = "語音快記", tint = tokens.accentColor) },
            label = { Text("語音", fontSize = 11.sp, color = tokens.accentColor) }
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
    onlyWomensHealthFilter: Boolean = false,
    onToggleWomensHealthFilter: () -> Unit = {},
    onToggleCategory: (com.dx.calendar.data.model.CalendarCategoryEntity) -> Unit,
    onOpenManage: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val healthRose = Color(com.dx.calendar.data.model.WomensHealthPresets.CATEGORY_COLOR_ARGB)
    Column(
        modifier = Modifier
            .width(205.dp)
            .fillMaxHeight()
            .background(tokens.surfaceCardColor.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("時間表與 Tasks 篩選", color = tokens.secondaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (onlyWomensHealthFilter) healthRose else healthRose.copy(alpha = 0.14f))
                .border(1.dp, healthRose.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleWomensHealthFilter)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = if (onlyWomensHealthFilter) "🌸 僅看女性健康中（點此恢復）" else "🌸 臨時只檢視女性健康",
                color = if (onlyWomensHealthFilter) Color.White else healthRose,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
        }
        TextButton(onClick = onOpenManage, modifier = Modifier.fillMaxWidth()) {
            Text("管理帳號與匯入 →", fontSize = 12.sp)
        }
    }
}
