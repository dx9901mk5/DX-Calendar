package com.dx.calendar.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dx.calendar.alarm.MultiCallAlarmScheduler
import com.dx.calendar.data.local.AppPreferences
import com.dx.calendar.data.local.UserSettings
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.CalendarViewMode
import com.dx.calendar.data.model.DarkThemeMode
import com.dx.calendar.data.model.EventTitleHistoryEntity
import com.dx.calendar.data.model.ExternalEventOverrideEntity
import com.dx.calendar.data.model.MainSection
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.model.TaskItemEntity
import com.dx.calendar.data.repository.CalendarRepository
import com.dx.calendar.data.repository.UnifiedCalendarItem
import com.dx.calendar.data.repository.UnifiedSourceType
import com.dx.calendar.data.sync.GoogleTasksSyncManager
import com.dx.calendar.data.sync.IcsManager
import com.dx.calendar.data.sync.SystemCalendarAccountInfo
import com.dx.calendar.ui.screens.EditorSavePayload
import com.dx.calendar.util.PrivacyCryptoManager
import com.dx.calendar.widget.WidgetRefreshHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class EditorLaunchState(
    val isOpen: Boolean = false,
    val initialDate: LocalDate = LocalDate.now(),
    val editingEvent: CalendarEventEntity? = null,
    val editingTask: TaskItemEntity? = null,
    val presetRoutine: Boolean = false,
    val presetPrivacy: Boolean = false,
    val presetTaskMode: Boolean = false
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    val repo = CalendarRepository(app)
    private val googleTasksSync = GoogleTasksSyncManager(app, repo.dao)

    val settings: StateFlow<UserSettings> = repo.prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val categories: StateFlow<List<CalendarCategoryEntity>> = repo.dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents: StateFlow<List<CalendarEventEntity>> = repo.dao.observeAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskItemEntity>> = repo.dao.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkIns: StateFlow<List<RoutineCheckInEntity>> = repo.dao.observeRoutineCheckIns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val externalOverrides: StateFlow<List<ExternalEventOverrideEntity>> = repo.dao.observeExternalOverrides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val titleHistory: StateFlow<List<EventTitleHistoryEntity>> = repo.dao.observeTitleHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    val currentYearMonth: StateFlow<YearMonth> = _currentYearMonth.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _mainSection = MutableStateFlow(MainSection.CALENDAR)
    val mainSection: StateFlow<MainSection> = _mainSection.asStateFlow()

    private val _isPrivacyUnlocked = MutableStateFlow(false)
    val isPrivacyUnlocked: StateFlow<Boolean> = _isPrivacyUnlocked.asStateFlow()

    private val _focusedItem = MutableStateFlow<UnifiedCalendarItem?>(null)
    val focusedItem: StateFlow<UnifiedCalendarItem?> = _focusedItem.asStateFlow()

    private val _editorState = MutableStateFlow(EditorLaunchState())
    val editorState: StateFlow<EditorLaunchState> = _editorState.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _systemCalendars = MutableStateFlow<List<SystemCalendarAccountInfo>>(emptyList())
    val systemCalendars: StateFlow<List<SystemCalendarAccountInfo>> = _systemCalendars.asStateFlow()

    private val _isSyncingTasks = MutableStateFlow(false)
    val isSyncingTasks: StateFlow<Boolean> = _isSyncingTasks.asStateFlow()

    private val _refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val visibleRangeItems: StateFlow<List<UnifiedCalendarItem>> = combine(
        _currentYearMonth,
        categories,
        allEvents,
        allTasks,
        combine(checkIns, externalOverrides, settings, _isPrivacyUnlocked, _refreshTick) { c, o, s, p, _ ->
            QuadData(c, o, s, p)
        }
    ) { ym, cats, evs, tks, quad ->
        RangeQueryInput(ym, cats, evs, tks, quad.checkIns, quad.overrides, quad.settings, quad.privacyUnlocked)
    }.flatMapLatest { input ->
        flow {
            val rangeStart = input.ym.atDay(1).minusDays(14)
            val rangeEnd = input.ym.atEndOfMonth().plusDays(45)
            val built = repo.buildItemsForRange(
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                categories = input.cats,
                localEvents = input.evs,
                tasks = input.tks,
                checkIns = input.checkIns,
                overrides = input.overrides,
                settings = input.settings,
                isPrivacyUnlocked = input.privacyUnlocked
            )
            emit(built)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSystemCalendars()
        viewModelScope.launch {
            MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(getApplication())
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun refreshSystemCalendars() {
        viewModelScope.launch {
            _systemCalendars.value = repo.getSystemCalendars()
            _refreshTick.value += 1
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _focusedItem.value = null
        val ym = YearMonth.from(date)
        if (ym != _currentYearMonth.value) {
            _currentYearMonth.value = ym
        }
    }

    fun shiftMonth(delta: Long) {
        val nextYm = _currentYearMonth.value.plusMonths(delta)
        _currentYearMonth.value = nextYm
        val day = _selectedDate.value.dayOfMonth.coerceAtMost(nextYm.lengthOfMonth())
        _selectedDate.value = nextYm.atDay(day)
        _focusedItem.value = null
    }

    fun shiftYear(delta: Long) {
        val nextYm = _currentYearMonth.value.plusYears(delta)
        _currentYearMonth.value = nextYm
        val day = _selectedDate.value.dayOfMonth.coerceAtMost(nextYm.lengthOfMonth())
        _selectedDate.value = nextYm.atDay(day)
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _currentYearMonth.value = YearMonth.from(today)
        _focusedItem.value = null
    }

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
        _mainSection.value = MainSection.CALENDAR
    }

    fun setMainSection(section: MainSection) {
        _mainSection.value = section
    }

    fun setFocusedItem(item: UnifiedCalendarItem?) {
        _focusedItem.value = item
    }

    fun setPrivacyUnlocked(unlocked: Boolean) {
        _isPrivacyUnlocked.value = unlocked
        if (!unlocked && _focusedItem.value?.isPrivacy == true) {
            _focusedItem.value = null
        }
    }

    fun openSettings(open: Boolean) {
        _isSettingsOpen.value = open
    }

    fun openNewEditor(
        date: LocalDate = _selectedDate.value,
        presetRoutine: Boolean = false,
        presetPrivacy: Boolean = false,
        presetTaskMode: Boolean = false
    ) {
        _editorState.value = EditorLaunchState(
            isOpen = true,
            initialDate = date,
            presetRoutine = presetRoutine,
            presetPrivacy = presetPrivacy,
            presetTaskMode = presetTaskMode
        )
    }

    fun openEditForUnifiedItem(item: UnifiedCalendarItem) {
        viewModelScope.launch {
            when (item.sourceType) {
                UnifiedSourceType.LOCAL_EVENT -> {
                    val ev = repo.dao.getEventById(item.rawId) ?: return@launch
                    _editorState.value = EditorLaunchState(isOpen = true, initialDate = item.date, editingEvent = ev)
                }
                UnifiedSourceType.TASK -> {
                    val tk = repo.dao.getTaskById(item.rawId) ?: return@launch
                    _editorState.value = EditorLaunchState(isOpen = true, initialDate = item.date, editingTask = tk, presetTaskMode = true)
                }
                UnifiedSourceType.SYSTEM_EVENT -> Unit
            }
        }
    }

    fun openEditEventEntity(ev: CalendarEventEntity) {
        _editorState.value = EditorLaunchState(isOpen = true, editingEvent = ev)
    }

    fun closeEditor() {
        _editorState.value = EditorLaunchState(isOpen = false)
    }

    fun saveFromEditor(payload: EditorSavePayload) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val zone = ZoneId.systemDefault()

            // 紀錄常用標題歷史
            val oldHist = repo.dao.getTitleHistoryItem(payload.title)
            repo.dao.upsertTitleHistory(
                EventTitleHistoryEntity(
                    title = payload.title,
                    usageCount = (oldHist?.usageCount ?: 0) + 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            )

            val encTitle = PrivacyCryptoManager.encryptIfNeeded(payload.title, payload.isPrivacy)
            val encNotes = PrivacyCryptoManager.encryptIfNeeded(payload.notes, payload.isPrivacy)
            val encLoc = PrivacyCryptoManager.encryptIfNeeded(payload.location, payload.isPrivacy)
            val remJson = repo.toIntListJson(payload.reminderMinutes)

            if (payload.isTaskMode) {
                val existing = payload.editingTaskId?.let { repo.dao.getTaskById(it) }
                val minuteOfDay = if (payload.isAllDay) null else (payload.startTime.hour * 60 + payload.startTime.minute)
                val task = TaskItemEntity(
                    id = existing?.id ?: 0L,
                    title = encTitle,
                    notes = encNotes,
                    dueEpochDay = payload.startDate.toEpochDay(),
                    dueMinuteOfDay = minuteOfDay,
                    isCompleted = existing?.isCompleted ?: false,
                    recurrenceType = payload.recurrenceType.name,
                    isRoutine = payload.isRoutine,
                    showInMonthGrid = payload.showInMonthGrid,
                    showInAgendaAndWidget = payload.showInAgendaAndWidget,
                    isPrivacy = payload.isPrivacy,
                    reminderMinutesJson = remJson,
                    enableMultiCall = payload.enableMultiCall,
                    multiCallCount = payload.multiCallCount,
                    multiCallIntervalMinutes = payload.multiCallIntervalMinutes,
                    useAlarmStream = payload.useAlarmStream,
                    syncSystemAlarm = payload.syncSystemAlarm,
                    googleTaskId = existing?.googleTaskId,
                    googleTaskListId = existing?.googleTaskListId,
                    googleTaskListTitle = existing?.googleTaskListTitle ?: "待辦事項"
                )
                val savedId = repo.dao.upsertTask(task)
                val savedTask = task.copy(id = if (task.id == 0L) savedId else task.id)
                MultiCallAlarmScheduler.scheduleForTask(app, savedTask)
            } else {
                val startMs = if (payload.isAllDay) {
                    payload.startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                } else {
                    payload.startDate.atTime(payload.startTime).atZone(zone).toInstant().toEpochMilli()
                }
                val endMs = if (payload.isAllDay) {
                    payload.endDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
                } else {
                    payload.endDate.atTime(payload.endTime).atZone(zone).toInstant().toEpochMilli()
                        .coerceAtLeast(startMs + 1_800_000L)
                }

                // 若使用者選擇直接寫入設備 Google / 系統日曆
                if (payload.writeToSystemCalendarId != null && !payload.isPrivacy) {
                    withContext(Dispatchers.IO) {
                        repo.systemBridge.insertEventToSystemCalendar(
                            systemCalendarId = payload.writeToSystemCalendarId,
                            title = payload.title,
                            description = payload.notes,
                            location = payload.location,
                            startMillis = startMs,
                            endMillis = endMs,
                            isAllDay = payload.isAllDay,
                            reminderMinutes = payload.reminderMinutes
                        )
                    }
                    _refreshTick.value += 1
                } else {
                    val event = CalendarEventEntity(
                        id = payload.editingEventId ?: 0L,
                        categoryId = if (payload.isPrivacy) 8L else payload.categoryId,
                        title = encTitle,
                        location = encLoc,
                        url = payload.url,
                        notes = encNotes,
                        startEpochMillis = startMs,
                        endEpochMillis = endMs,
                        isAllDay = payload.isAllDay,
                        extraDatesJson = repo.toDateStringsJson(payload.extraDates),
                        recurrenceType = payload.recurrenceType.name,
                        isRoutine = payload.isRoutine,
                        showInMonthGrid = payload.showInMonthGrid,
                        showInAgendaAndWidget = payload.showInAgendaAndWidget,
                        isPrivacy = payload.isPrivacy,
                        reminderMinutesJson = remJson,
                        enableMultiCall = payload.enableMultiCall,
                        multiCallCount = payload.multiCallCount,
                        multiCallIntervalMinutes = payload.multiCallIntervalMinutes,
                        useAlarmStream = payload.useAlarmStream,
                        syncSystemAlarm = payload.syncSystemAlarm
                    )
                    val newId = repo.dao.upsertEvent(event)
                    val savedEv = event.copy(id = if (event.id == 0L) newId else event.id)
                    MultiCallAlarmScheduler.scheduleForEvent(app, savedEv)
                }
            }

            // 同步連動手機系統原生「時鐘」鬧鐘 App！
            if (payload.syncSystemAlarm && !payload.isAllDay) {
                MultiCallAlarmScheduler.syncToSystemClockAlarm(
                    context = app,
                    hour = payload.startTime.hour,
                    minute = payload.startTime.minute,
                    label = if (payload.isPrivacy) "DX隱私提醒" else payload.title
                )
            }

            _editorState.value = EditorLaunchState(isOpen = false)
            _focusedItem.value = null
            WidgetRefreshHelper.refreshAllWidgets(app)
        }
    }

    fun quickAddTask(title: String, dueToday: Boolean, showInMonth: Boolean) {
        viewModelScope.launch {
            val task = TaskItemEntity(
                title = title,
                dueEpochDay = if (dueToday) _selectedDate.value.toEpochDay() else null,
                showInMonthGrid = showInMonth,
                isRoutine = !showInMonth
            )
            repo.dao.upsertTask(task)
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun toggleUnifiedItemComplete(item: UnifiedCalendarItem) {
        viewModelScope.launch {
            when (item.sourceType) {
                UnifiedSourceType.TASK -> {
                    val tk = repo.dao.getTaskById(item.rawId) ?: return@launch
                    if (tk.recurrenceType != "NONE") {
                        toggleRoutineCheckIn("TASK", tk.id, item.date.toEpochDay(), item.isCompleted)
                    } else {
                        val updated = tk.copy(
                            isCompleted = !tk.isCompleted,
                            completedAt = if (!tk.isCompleted) System.currentTimeMillis() else null
                        )
                        repo.dao.upsertTask(updated)
                        googleTasksSync.pushTaskCompletionToCloud(settings.value.googleAccountEmail, updated)
                    }
                }
                UnifiedSourceType.LOCAL_EVENT -> {
                    toggleRoutineCheckIn("EVENT", item.rawId, item.date.toEpochDay(), item.isCompleted)
                }
                UnifiedSourceType.SYSTEM_EVENT -> Unit
            }
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun toggleTaskEntity(task: TaskItemEntity) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
            repo.dao.upsertTask(updated)
            googleTasksSync.pushTaskCompletionToCloud(settings.value.googleAccountEmail, updated)
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun toggleTaskMonthVisible(task: TaskItemEntity) {
        viewModelScope.launch {
            repo.dao.upsertTask(task.copy(showInMonthGrid = !task.showInMonthGrid))
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun toggleEventMonthVisible(ev: CalendarEventEntity) {
        viewModelScope.launch {
            repo.dao.upsertEvent(ev.copy(showInMonthGrid = !ev.showInMonthGrid))
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun toggleEventPause(ev: CalendarEventEntity) {
        viewModelScope.launch {
            val nextPaused = !ev.isPaused
            repo.dao.upsertEvent(ev.copy(isPaused = nextPaused))
            MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(getApplication())
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun toggleRoutineCheckIn(targetType: String, targetId: Long, epochDay: Long, currentlyDone: Boolean) {
        viewModelScope.launch {
            if (currentlyDone) {
                repo.dao.removeCheckIn(targetType, targetId, epochDay)
            } else {
                repo.dao.insertCheckIn(RoutineCheckInEntity(targetType, targetId, epochDay))
            }
        }
    }

    fun toggleExternalSeriesOverride(item: UnifiedCalendarItem) {
        val key = item.externalOverrideKey ?: return
        viewModelScope.launch {
            if (item.showInMonthGrid) {
                repo.dao.upsertExternalOverride(
                    ExternalEventOverrideEntity(
                        externalKey = key,
                        showInMonthGrid = false,
                        showInAgendaAndWidget = true,
                        isRoutine = true
                    )
                )
                toast("已將「${item.title}」系列自月曆格隱藏並收納至例行中心")
            } else {
                repo.dao.deleteExternalOverride(key)
                toast("已恢復於月曆格顯示")
            }
            _focusedItem.value = null
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun restoreExternalOverride(key: String) {
        viewModelScope.launch {
            repo.dao.deleteExternalOverride(key)
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun deleteUnifiedItem(item: UnifiedCalendarItem) {
        viewModelScope.launch {
            when (item.sourceType) {
                UnifiedSourceType.LOCAL_EVENT -> repo.dao.deleteEventById(item.rawId)
                UnifiedSourceType.TASK -> repo.dao.deleteTaskById(item.rawId)
                UnifiedSourceType.SYSTEM_EVENT -> Unit
            }
            _focusedItem.value = null
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun deleteEventEntity(ev: CalendarEventEntity) {
        viewModelScope.launch {
            repo.dao.deleteEvent(ev)
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun deleteTaskEntity(tk: TaskItemEntity) {
        viewModelScope.launch {
            repo.dao.deleteTaskById(tk.id)
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val count = repo.dao.clearCompletedPublicTasks()
            toast("已清理 $count 項已完成待辦")
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun cleanupExpiredAccumulatedItems() {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 7L * 86_400_000L // 清理超過 7 天的單次過期舊提醒
            val evCount = repo.dao.cleanupExpiredSingleEvents(cutoff)
            val tkCount = repo.dao.clearCompletedPublicTasks()
            toast("清理完成：移除 $evCount 筆過期單次提醒、$tkCount 筆已完成舊待辦")
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    fun syncGoogleTasksWithEmail(email: String) {
        viewModelScope.launch {
            _isSyncingTasks.value = true
            repo.prefs.setGoogleAccountEmail(email)
            val res = googleTasksSync.syncWithGoogleAccount(email)
            _isSyncingTasks.value = false
            res.onSuccess { cnt ->
                toast("Google Tasks 同步成功，共更新 $cnt 項任務")
                WidgetRefreshHelper.refreshAllWidgets(getApplication())
            }.onFailure { err ->
                toast("同步提示：${err.message ?: "請確認網路或授權狀態"}")
            }
        }
    }

    // === 設定與匯入匯出操作 ===
    fun setBrandStyle(style: BrandStyle) = viewModelScope.launch {
        repo.prefs.setBrandStyle(style)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }

    fun setDarkThemeMode(mode: DarkThemeMode) = viewModelScope.launch { repo.prefs.setDarkThemeMode(mode) }
    fun setStartMonday(v: Boolean) = viewModelScope.launch {
        repo.prefs.setStartWeekOnMonday(v)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setShowLunar(v: Boolean) = viewModelScope.launch {
        repo.prefs.setShowLunar(v)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setShowWeekNum(v: Boolean) = viewModelScope.launch { repo.prefs.setShowWeekNumber(v) }
    fun setShowCompletedTasks(v: Boolean) = viewModelScope.launch { repo.prefs.setShowCompletedTasks(v) }
    fun setPeekHiddenInMonth(v: Boolean) = viewModelScope.launch { repo.prefs.setPeekHiddenInMonth(v) }
    fun setRoutineDefaultHide(v: Boolean) = viewModelScope.launch { repo.prefs.setRoutineDefaultHideFromMonth(v) }
    fun setShowGridLines(v: Boolean) = viewModelScope.launch { repo.prefs.setShowMonthGridLines(v) }
    fun setWallpaper(uri: String, alpha: Float) = viewModelScope.launch { repo.prefs.setWallpaper(uri, alpha) }
    fun setRequireDualFingerprint(v: Boolean) = viewModelScope.launch { repo.prefs.setRequireDualFingerprint(v) }
    fun setPrivacyPin(pin: String) = viewModelScope.launch {
        repo.prefs.setPrivacyPin(pin)
        toast("已儲存隱私 PIN 碼")
    }
    fun verifyPrivacyPin(pin: String): Boolean {
        val hash = settings.value.privacyPinHash
        return hash.isNotBlank() && AppPreferences.sha256(pin.trim()) == hash
    }

    fun setWidgetAppearance(dark: Boolean, opacity: Float) = viewModelScope.launch {
        repo.prefs.setWidgetAppearance(dark, opacity)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }

    fun toggleCategoryVisible(cat: CalendarCategoryEntity) = viewModelScope.launch {
        repo.dao.upsertCategory(cat.copy(isVisible = !cat.isVisible))
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }

    fun createCategory(name: String, colorArgb: Int) = viewModelScope.launch {
        repo.dao.upsertCategory(CalendarCategoryEntity(name = name, colorArgb = colorArgb))
    }

    fun toggleSystemCalVisible(id: Long, vis: Boolean) = viewModelScope.launch {
        repo.prefs.toggleSystemCalendarVisibility(id.toString(), vis)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }

    fun importIcsFromUri(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                val parsed = IcsManager.parseIcsContent(text)
                parsed.forEach { repo.dao.upsertEvent(it) }
                WidgetRefreshHelper.refreshAllWidgets(app)
                toast("成功匯入 ${parsed.size} 筆日程！")
            }.onFailure {
                toast("匯入 ICS 失敗：${it.message}")
            }
        }
    }

    fun importIcsFromUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                val text = IcsManager.fetchIcsFromUrl(url)
                val parsed = IcsManager.parseIcsContent(text)
                parsed.forEach { repo.dao.upsertEvent(it) }
                WidgetRefreshHelper.refreshAllWidgets(getApplication())
                toast("成功從 URL 匯入 ${parsed.size} 筆日程！")
            }.onFailure {
                toast("URL 匯入失敗：${it.message}")
            }
        }
    }

    fun buildShareIcsIntent(): Intent? {
        return runCatching {
            val app = getApplication<Application>()
            val icsText = IcsManager.exportEventsToIcs(allEvents.value)
            val outFile = File(app.cacheDir, "dx_calendar_export.ics")
            outFile.writeText(icsText, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }
}

private data class QuadData(
    val checkIns: List<RoutineCheckInEntity>,
    val overrides: List<ExternalEventOverrideEntity>,
    val settings: UserSettings,
    val privacyUnlocked: Boolean
)

private data class RangeQueryInput(
    val ym: YearMonth,
    val cats: List<CalendarCategoryEntity>,
    val evs: List<CalendarEventEntity>,
    val tks: List<TaskItemEntity>,
    val checkIns: List<RoutineCheckInEntity>,
    val overrides: List<ExternalEventOverrideEntity>,
    val settings: UserSettings,
    val privacyUnlocked: Boolean
)
