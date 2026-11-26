package com.dx.calendar.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dx.calendar.BuildConfig
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
import com.dx.calendar.data.sync.GoogleTasksConsentRequiredException
import com.dx.calendar.data.sync.GoogleTasksSyncManager
import com.dx.calendar.data.sync.IcsManager
import com.dx.calendar.data.sync.SystemCalendarAccountInfo
import com.dx.calendar.ui.screens.EditorSavePayload
import com.dx.calendar.update.GitHubAutoUpdater
import com.dx.calendar.update.ReleaseUpdateInfo
import com.dx.calendar.update.UpdateCheckResult
import com.dx.calendar.util.PrivacyCryptoManager
import com.dx.calendar.widget.WidgetRefreshHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

data class AppUpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val currentVersion: String = "1.1.0",
    val latestVersion: String = "",
    val releaseInfo: ReleaseUpdateInfo? = null,
    val progressFraction: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val statusMessage: String = "",
    val showAutoPromptDialog: Boolean = false
)

data class EditorLaunchState(
    val isOpen: Boolean = false,
    val initialDate: LocalDate = LocalDate.now(),
    val editingEvent: CalendarEventEntity? = null,
    val editingTask: TaskItemEntity? = null,
    val editingSystemEvent: UnifiedCalendarItem? = null,
    val editingSystemCalendarId: Long? = null,
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

    private data class PendingWidgetEventFocus(
        val date: LocalDate,
        val rawId: Long,
        val isTask: Boolean
    )
    private var pendingWidgetFocus: PendingWidgetEventFocus? = null

    private val _editorState = MutableStateFlow(EditorLaunchState())
    val editorState: StateFlow<EditorLaunchState> = _editorState.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _systemCalendars = MutableStateFlow<List<SystemCalendarAccountInfo>>(emptyList())
    val systemCalendars: StateFlow<List<SystemCalendarAccountInfo>> = _systemCalendars.asStateFlow()

    private val _isSyncingTasks = MutableStateFlow(false)
    val isSyncingTasks: StateFlow<Boolean> = _isSyncingTasks.asStateFlow()

    private val _updateState = MutableStateFlow(
        AppUpdateUiState(currentVersion = GitHubAutoUpdater.getCurrentVersionName(app))
    )
    val updateState: StateFlow<AppUpdateUiState> = _updateState.asStateFlow()
    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var lastAutoTasksSyncMillis: Long = 0L
    private var calendarContentObserver: android.database.ContentObserver? = null
    private var calendarObserverDebounceJob: Job? = null

    private val _pendingInstallApkPath = MutableStateFlow<String?>(null)
    val pendingInstallApkPath: StateFlow<String?> = _pendingInstallApkPath.asStateFlow()

    private val _pendingGoogleTasksConsentIntent = MutableStateFlow<Intent?>(null)
    val pendingGoogleTasksConsentIntent: StateFlow<Intent?> = _pendingGoogleTasksConsentIntent.asStateFlow()

    private val _weatherByDate = MutableStateFlow<Map<LocalDate, com.dx.calendar.data.weather.DailyWeatherInfo>>(
        com.dx.calendar.data.weather.WeatherForecastManager.loadCachedForecast(app)
    )
    val weatherByDate: StateFlow<Map<LocalDate, com.dx.calendar.data.weather.DailyWeatherInfo>> = _weatherByDate.asStateFlow()

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
            pendingWidgetFocus?.let { target ->
                val matched = built.firstOrNull {
                    it.date == target.date && it.rawId == target.rawId && it.isTask == target.isTask
                } ?: built.firstOrNull {
                    it.date == target.date && it.rawId == target.rawId
                }
                if (matched != null) {
                    _focusedItem.value = matched
                    pendingWidgetFocus = null
                }
            }
            emit(built)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSystemCalendars()
        viewModelScope.launch {
            repo.prefs.applyGoldWhiteDefaultMigrationOnce()
            cleanupLegacyMedicationDemoOnce()
            registerSystemCalendarObserverIfPermitted()
            MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(getApplication())
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
            val initSettings = repo.prefs.settingsFlow.first()
            refreshWeatherForecast(initSettings.effectiveZoneId)
            if (initSettings.googleAccountEmail.isNotBlank()) {
                autoSyncGoogleTasksSilently(initSettings.googleAccountEmail)
            }
            if (initSettings.autoCheckUpdate && !BuildConfig.IS_PLAY_BUILD) {
                checkGitHubUpdate(autoDownloadIfFound = true, showDialogIfOnMain = true)
            }
        }
    }

    fun refreshWeatherForecast(zone: ZoneId = settings.value.effectiveZoneId, force: Boolean = false) {
        viewModelScope.launch {
            val map = com.dx.calendar.data.weather.WeatherForecastManager.fetchSevenDayForecast(
                context = getApplication(),
                zone = zone,
                forceRefresh = force
            )
            if (map.isNotEmpty()) {
                _weatherByDate.value = map
            }
        }
    }

    fun onAppForegroundResumed() {
        refreshSystemCalendars()
        registerSystemCalendarObserverIfPermitted()
        viewModelScope.launch {
            val s = repo.prefs.getSettingsOnce()
            refreshWeatherForecast(s.effectiveZoneId)
            val email = s.googleAccountEmail
            if (email.isNotBlank() && System.currentTimeMillis() - lastAutoTasksSyncMillis >= 120_000L) {
                autoSyncGoogleTasksSilently(email)
            }
        }
    }

    private suspend fun autoSyncGoogleTasksSilently(email: String) {
        if (_isSyncingTasks.value || email.isBlank()) return
        lastAutoTasksSyncMillis = System.currentTimeMillis()
        _isSyncingTasks.value = true
        val res = googleTasksSync.syncWithGoogleAccount(email)
        _isSyncingTasks.value = false
        res.onSuccess {
            _refreshTick.value += 1
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
    }

    /**
     * 一次性清理舊版自動建立的「每日維他命與保健吃藥提醒」示範項目與其排程鬧鐘，
     * 確保升級後不再誤響，且僅保留使用者自行開啟與新增的選項與規則。
     */
    private suspend fun cleanupLegacyMedicationDemoOnce() = withContext(Dispatchers.IO) {
        if (repo.prefs.isLegacyMedicationDemoCleaned()) return@withContext
        val app = getApplication<Application>()
        val legacyDemos = repo.dao.getAllEventsOnce().filter { ev ->
            ev.title == "每日維他命與保健吃藥提醒" &&
                ev.notes.startsWith("此為例行事務示範")
        }
        legacyDemos.forEach { ev ->
            MultiCallAlarmScheduler.cancelEventAlarms(app, ev)
            repo.dao.deleteEventById(ev.id)
        }
        repo.prefs.markLegacyMedicationDemoCleaned()
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

    fun openDateFromWidget(epochDay: Long, switchToDayView: Boolean = true) {
        val date = LocalDate.ofEpochDay(epochDay)
        pendingWidgetFocus = null
        _mainSection.value = MainSection.CALENDAR
        _selectedDate.value = date
        _currentYearMonth.value = YearMonth.from(date)
        _focusedItem.value = null
        if (switchToDayView) {
            _viewMode.value = CalendarViewMode.DAY
        }
    }

    fun openEventFromWidget(epochDay: Long, rawId: Long, isTask: Boolean) {
        val date = LocalDate.ofEpochDay(epochDay)
        _mainSection.value = MainSection.CALENDAR
        _selectedDate.value = date
        _currentYearMonth.value = YearMonth.from(date)
        val alreadyLoaded = visibleRangeItems.value.firstOrNull {
            it.date == date && it.rawId == rawId && it.isTask == isTask
        } ?: visibleRangeItems.value.firstOrNull {
            it.date == date && it.rawId == rawId
        }
        if (alreadyLoaded != null) {
            pendingWidgetFocus = null
            _focusedItem.value = alreadyLoaded
        } else {
            pendingWidgetFocus = PendingWidgetEventFocus(date, rawId, isTask)
            _refreshTick.value += 1
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
        if (open && !BuildConfig.IS_PLAY_BUILD && _updateState.value.phase != UpdatePhase.DOWNLOADING) {
            checkGitHubUpdate(
                autoDownloadIfFound = settings.value.autoCheckUpdate,
                showDialogIfOnMain = false
            )
        }
    }

    fun openNewEditor(
        date: LocalDate = _selectedDate.value,
        presetRoutine: Boolean = false,
        presetPrivacy: Boolean = false,
        presetTaskMode: Boolean = false
    ) {
        refreshSystemCalendars()
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
                UnifiedSourceType.SYSTEM_EVENT -> {
                    refreshSystemCalendars()
                    val calId = withContext(Dispatchers.IO) {
                        repo.systemBridge.queryEventCalendarId(item.rawId)
                    }
                    _editorState.value = EditorLaunchState(
                        isOpen = true,
                        initialDate = item.date,
                        editingSystemEvent = item,
                        editingSystemCalendarId = calId
                    )
                }
            }
        }
    }

    fun openEditEventEntity(ev: CalendarEventEntity) {
        _editorState.value = EditorLaunchState(isOpen = true, editingEvent = ev)
    }

    fun closeEditor() {
        _editorState.value = EditorLaunchState(isOpen = false)
    }

    fun deleteEditingItem() {
        val state = _editorState.value
        val ev = state.editingEvent
        val tk = state.editingTask
        val sysEv = state.editingSystemEvent
        viewModelScope.launch {
            if (ev != null) {
                repo.dao.deleteEvent(ev)
                toast("已刪除活動")
            } else if (tk != null) {
                repo.dao.deleteTaskById(tk.id)
                toast("已刪除待辦事項")
            } else if (sysEv != null) {
                val ok = withContext(Dispatchers.IO) {
                    repo.systemBridge.deleteEventInSystemCalendar(sysEv.rawId)
                }
                if (ok) {
                    com.dx.calendar.data.sync.SystemEventAlarmPrefs.remove(getApplication(), sysEv.rawId)
                    _refreshTick.value += 1
                    toast("已刪除 Google 行事曆活動")
                } else {
                    toast("刪除失敗，請確認日曆寫入權限")
                }
            }
            _focusedItem.value = null
            _editorState.value = EditorLaunchState(isOpen = false)
            WidgetRefreshHelper.refreshAllWidgets(getApplication())
        }
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
                if (!savedTask.isRoutine || settings.value.routineMedicationFeatureEnabled) {
                    MultiCallAlarmScheduler.scheduleForTask(app, savedTask)
                } else {
                    MultiCallAlarmScheduler.cancelTaskAlarms(app, savedTask)
                }
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

                // 若使用者編輯既有 Google / 系統行事曆，或新增至已登記之 Google / 同步行事曆（含提醒與連段鬧鐘完整同步）
                val encodedRecurrence = com.dx.calendar.data.model.RecurrenceCodec.encode(
                    payload.recurrenceType,
                    payload.recurrenceIntervalWeeks
                )
                val targetSysCalId = payload.writeToSystemCalendarId
                if ((payload.editingSystemEventId != null || targetSysCalId != null) && !payload.isPrivacy) {
                    val rrule = com.dx.calendar.data.model.RecurrenceCodec.toRRule(
                        payload.recurrenceType,
                        payload.recurrenceIntervalWeeks
                    )
                    val alarmMeta = com.dx.calendar.data.sync.SystemEventAlarmMeta(
                        enableMultiCall = payload.enableMultiCall,
                        multiCallCount = payload.multiCallCount,
                        multiCallIntervalMinutes = payload.multiCallIntervalMinutes,
                        useAlarmStream = payload.useAlarmStream,
                        syncSystemAlarm = payload.syncSystemAlarm
                    )
                    if (payload.editingSystemEventId != null) {
                        val updated = withContext(Dispatchers.IO) {
                            repo.systemBridge.updateEventInSystemCalendar(
                                eventId = payload.editingSystemEventId,
                                systemCalendarId = targetSysCalId,
                                title = payload.title,
                                description = payload.notes,
                                location = payload.location,
                                startMillis = startMs,
                                endMillis = endMs,
                                isAllDay = payload.isAllDay,
                                reminderMinutes = payload.reminderMinutes,
                                rrule = rrule
                            )
                        }
                        if (updated) {
                            com.dx.calendar.data.sync.SystemEventAlarmPrefs.save(app, payload.editingSystemEventId, alarmMeta)
                            MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(app)
                            _refreshTick.value += 1
                            toast("已更新 Google 行事曆與鬧鐘提醒")
                        } else {
                            toast("更新失敗，請確認日曆寫入權限")
                        }
                    } else if (targetSysCalId != null) {
                        val targetCalId = targetSysCalId
                        val insertedId = withContext(Dispatchers.IO) {
                            val mainId = repo.systemBridge.insertEventToSystemCalendar(
                                systemCalendarId = targetCalId,
                                title = payload.title,
                                description = payload.notes,
                                location = payload.location,
                                startMillis = startMs,
                                endMillis = endMs,
                                isAllDay = payload.isAllDay,
                                reminderMinutes = payload.reminderMinutes,
                                rrule = rrule
                            )
                            if (mainId != null) {
                                com.dx.calendar.data.sync.SystemEventAlarmPrefs.save(app, mainId, alarmMeta)
                            }
                            if (mainId != null && rrule == null) {
                                val spanMillis = (endMs - startMs).coerceAtLeast(1_800_000L)
                                payload.extraDates.forEach { exDate ->
                                    if (exDate != payload.startDate) {
                                        val exStart = if (payload.isAllDay) {
                                            exDate.atStartOfDay(zone).toInstant().toEpochMilli()
                                        } else {
                                            exDate.atTime(payload.startTime).atZone(zone).toInstant().toEpochMilli()
                                        }
                                        val exId = repo.systemBridge.insertEventToSystemCalendar(
                                            systemCalendarId = targetCalId,
                                            title = payload.title,
                                            description = payload.notes,
                                            location = payload.location,
                                            startMillis = exStart,
                                            endMillis = exStart + spanMillis,
                                            isAllDay = payload.isAllDay,
                                            reminderMinutes = payload.reminderMinutes
                                        )
                                        if (exId != null) {
                                            com.dx.calendar.data.sync.SystemEventAlarmPrefs.save(app, exId, alarmMeta)
                                        }
                                    }
                                }
                            }
                            mainId
                        }
                        if (insertedId != null) {
                            MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(app)
                            _refreshTick.value += 1
                        } else {
                            // 若系統尚未授予寫入日曆權限，改以本機安全備援保存不遺失資料
                            val fallbackEvent = CalendarEventEntity(
                                id = payload.editingEventId ?: 0L,
                                categoryId = payload.categoryId,
                                title = encTitle,
                                location = encLoc,
                                url = payload.url,
                                notes = encNotes,
                                startEpochMillis = startMs,
                                endEpochMillis = endMs,
                                isAllDay = payload.isAllDay,
                                extraDatesJson = repo.toDateStringsJson(payload.extraDates),
                                recurrenceType = encodedRecurrence,
                                isRoutine = payload.isRoutine,
                                showInMonthGrid = payload.showInMonthGrid,
                                showInAgendaAndWidget = payload.showInAgendaAndWidget,
                                isPrivacy = false,
                                reminderMinutesJson = remJson,
                                enableMultiCall = payload.enableMultiCall,
                                multiCallCount = payload.multiCallCount,
                                multiCallIntervalMinutes = payload.multiCallIntervalMinutes,
                                useAlarmStream = payload.useAlarmStream,
                                syncSystemAlarm = payload.syncSystemAlarm
                            )
                            val newId = repo.dao.upsertEvent(fallbackEvent)
                            MultiCallAlarmScheduler.scheduleForEvent(app, fallbackEvent.copy(id = newId))
                            toast("尚未授予系統日曆寫入權限，已先為您保存至本機行事曆")
                        }
                    }
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
                        recurrenceType = encodedRecurrence,
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
                    if (savedEv.isRoutine && !settings.value.routineMedicationFeatureEnabled) {
                        repo.prefs.setRoutineMedicationFeatureEnabled(true)
                    }
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
                UnifiedSourceType.SYSTEM_EVENT -> {
                    val ok = withContext(Dispatchers.IO) {
                        repo.systemBridge.deleteEventInSystemCalendar(item.rawId)
                    }
                    if (ok) {
                        com.dx.calendar.data.sync.SystemEventAlarmPrefs.remove(getApplication(), item.rawId)
                        _refreshTick.value += 1
                        toast("已刪除 Google 行事曆活動")
                    } else {
                        toast("刪除失敗，請確認日曆寫入權限")
                    }
                }
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
            lastAutoTasksSyncMillis = System.currentTimeMillis()
            val res = googleTasksSync.syncWithGoogleAccount(email)
            _isSyncingTasks.value = false
            res.onSuccess { cnt ->
                _refreshTick.value += 1
                toast("Google Tasks 已自動同步完成（共 $cnt 項任務，無日期項目將預設顯示於今日）")
                WidgetRefreshHelper.refreshAllWidgets(getApplication())
            }.onFailure { err ->
                if (err is GoogleTasksConsentRequiredException) {
                    _pendingGoogleTasksConsentIntent.value = err.consentIntent
                } else {
                    toast("同步提示：${err.message ?: "請確認網路或授權狀態"}")
                }
            }
        }
    }

    fun consumeGoogleTasksConsentIntent() {
        _pendingGoogleTasksConsentIntent.value = null
    }

    fun removeGoogleAccountEmail(email: String) = viewModelScope.launch {
        repo.prefs.removeGoogleAccountEmail(email)
        toast("已移除帳號：$email")
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
    fun setWeekStartMode(mode: com.dx.calendar.data.local.WeekStartMode) = viewModelScope.launch {
        repo.prefs.setWeekStartMode(mode)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setShowLunar(v: Boolean) = viewModelScope.launch {
        repo.prefs.setShowLunar(v)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setAlternateCalendarMode(mode: com.dx.calendar.data.local.AlternateCalendarMode) = viewModelScope.launch {
        repo.prefs.setAlternateCalendarMode(mode)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setDateInfoDisplayMode(mode: com.dx.calendar.data.local.DateInfoDisplayMode) = viewModelScope.launch {
        repo.prefs.setDateInfoDisplayMode(mode)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setMonthViewLayoutMode(mode: com.dx.calendar.data.local.MonthViewLayoutMode) = viewModelScope.launch {
        repo.prefs.setMonthViewLayoutMode(mode)
    }
    fun setTimeZoneOverrideId(zoneId: String) = viewModelScope.launch {
        repo.prefs.setTimeZoneOverrideId(zoneId)
    }
    fun setHolidayPreferences(publicHolidays: Boolean, traditional: Boolean, solarTerms: Boolean) = viewModelScope.launch {
        repo.prefs.setHolidayPreferences(publicHolidays, traditional, solarTerms)
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
    }
    fun setDefaultEventReminders(minutes: List<Int>) = viewModelScope.launch {
        repo.prefs.setDefaultEventReminders(minutes)
    }
    fun setDefaultTaskReminders(minutes: List<Int>) = viewModelScope.launch {
        repo.prefs.setDefaultTaskReminders(minutes)
    }
    fun setShowWeekNum(v: Boolean) = viewModelScope.launch { repo.prefs.setShowWeekNumber(v) }
    fun setShowCompletedTasks(v: Boolean) = viewModelScope.launch { repo.prefs.setShowCompletedTasks(v) }
    fun setPeekHiddenInMonth(v: Boolean) = viewModelScope.launch { repo.prefs.setPeekHiddenInMonth(v) }
    fun setRoutineDefaultHide(v: Boolean) = viewModelScope.launch { repo.prefs.setRoutineDefaultHideFromMonth(v) }
    fun setShowGridLines(v: Boolean) = viewModelScope.launch { repo.prefs.setShowMonthGridLines(v) }
    fun setWallpaper(uri: String, alpha: Float) = viewModelScope.launch { repo.prefs.setWallpaper(uri, alpha) }
    fun setRequireDualFingerprint(v: Boolean) = viewModelScope.launch { repo.prefs.setRequireDualFingerprint(v) }
    fun setPrivacyFeatureEnabled(v: Boolean) = viewModelScope.launch {
        repo.prefs.setPrivacyFeatureEnabled(v)
        if (!v) {
            setPrivacyUnlocked(false)
            if (_mainSection.value == MainSection.PRIVACY) {
                _mainSection.value = MainSection.CALENDAR
            }
        }
    }
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
        WidgetRefreshHelper.clearAllPerWidgetAppearanceOverrides(getApplication())
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

    fun setAutoCheckUpdate(enabled: Boolean) = viewModelScope.launch {
        repo.prefs.setAutoCheckUpdate(enabled)
    }

    fun setRoutineMedicationFeatureEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.prefs.setRoutineMedicationFeatureEnabled(enabled)
        MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(getApplication())
        WidgetRefreshHelper.refreshAllWidgets(getApplication())
        toast(
            if (enabled) "已啟用吃藥／例行提醒功能，可自由新增與套用提醒規則"
            else "已關閉吃藥／例行提醒功能（已停止相關例行鬧鐘排程）"
        )
    }

    fun syncDynamicAppIconNow() = viewModelScope.launch(Dispatchers.IO) {
        val enabled = repo.prefs.getSettingsOnce().dynamicDayIconEnabled
        com.dx.calendar.util.DynamicAppIconManager.syncIcon(getApplication(), enabled)
        com.dx.calendar.util.DynamicAppIconManager.scheduleMidnightRefresh(getApplication())
    }

    fun setDynamicDayIconEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.prefs.setDynamicDayIconEnabled(enabled)
        withContext(Dispatchers.IO) {
            com.dx.calendar.util.DynamicAppIconManager.syncIcon(getApplication(), enabled)
            com.dx.calendar.util.DynamicAppIconManager.scheduleMidnightRefresh(getApplication())
        }
        toast(
            if (enabled) "已開啟 1–31 動態日期圖示，桌面圖示將自動顯示今日日期"
            else "已切回預設靜態 App 圖示"
        )
    }

    fun setLiveUpdateIslandEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.prefs.setLiveUpdateIslandEnabled(enabled)
        toast(
            if (enabled) "已開啟原子島 / Live Updates 通知上島"
            else "已關閉通知上島"
        )
    }

    fun triggerLiveUpdateIslandDemo() {
        val app = getApplication<Application>()
        val res = com.dx.calendar.alarm.LiveUpdateIslandHelper.postDemoIslandNotification(app)
        val msg = when {
            !res.isAndroid16OrHigher ->
                "已發送相容即時通知（目前系統非 Android 16，於 Android 16 / OriginOS 6 將自動以 Live Updates 上島）"
            !res.canPostPromoted ->
                "已發送通知，但系統「即時更新 / 上島」開關未開啟，請點上方按鈕前往開啟！"
            res.hasPromotableCharacteristics ->
                "✓ 符合 Android 16 上島資格 (hasPromotableCharacteristics=true)，已推播至原子島！"
            else ->
                "已發送測試通知 (hasPromotableCharacteristics=false)，請檢查通知頻道與權限設定"
        }
        toast(msg)
    }

    fun cancelLiveUpdateIslandDemo() {
        com.dx.calendar.alarm.LiveUpdateIslandHelper.cancelIslandNotification(getApplication())
        toast("已關閉原子島測試通知")
    }

    fun checkGitHubUpdate(autoDownloadIfFound: Boolean = true, showDialogIfOnMain: Boolean = false) {
        if (_updateState.value.phase == UpdatePhase.DOWNLOADING) return
        activeCheckJob?.cancel()
        activeCheckJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val curVer = GitHubAutoUpdater.getCurrentVersionName(app)
            _updateState.value = _updateState.value.copy(
                phase = UpdatePhase.CHECKING,
                currentVersion = curVer,
                statusMessage = "正在連線 GitHub 判讀是否有新版本..."
            )
            when (val res = GitHubAutoUpdater.checkLatestRelease(app)) {
                is UpdateCheckResult.UpToDate -> {
                    _updateState.value = _updateState.value.copy(
                        phase = UpdatePhase.UP_TO_DATE,
                        currentVersion = res.currentVersion,
                        latestVersion = res.latestVersion,
                        statusMessage = "目前已是最新版本 (v${res.currentVersion})",
                        showAutoPromptDialog = false
                    )
                }

                is UpdateCheckResult.UpdateAvailable -> {
                    _updateState.value = _updateState.value.copy(
                        phase = UpdatePhase.UPDATE_AVAILABLE,
                        currentVersion = res.currentVersion,
                        latestVersion = res.info.cleanVersion,
                        releaseInfo = res.info,
                        totalBytes = res.info.apkSizeBytes,
                        statusMessage = "發現新版本 ${res.info.tagName}！即將自動下載安裝...",
                        showAutoPromptDialog = showDialogIfOnMain && !_isSettingsOpen.value
                    )
                    if (autoDownloadIfFound) {
                        startUpdateDownload(res.info)
                    }
                }

                is UpdateCheckResult.Error -> {
                    _updateState.value = _updateState.value.copy(
                        phase = UpdatePhase.ERROR,
                        currentVersion = curVer,
                        statusMessage = res.message
                    )
                }
            }
        }
    }

    fun startUpdateDownload(info: ReleaseUpdateInfo? = _updateState.value.releaseInfo) {
        val targetInfo = info ?: return
        if (_updateState.value.phase == UpdatePhase.DOWNLOADING) return
        activeDownloadJob?.cancel()
        activeDownloadJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _updateState.value = _updateState.value.copy(
                phase = UpdatePhase.DOWNLOADING,
                releaseInfo = targetInfo,
                progressFraction = 0f,
                downloadedBytes = 0L,
                totalBytes = targetInfo.apkSizeBytes,
                statusMessage = "正在自動下載 ${targetInfo.tagName}..."
            )

            val dlResult = GitHubAutoUpdater.downloadReleaseApk(app, targetInfo) { downloaded, total, frac ->
                _updateState.value = _updateState.value.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    progressFraction = frac,
                    statusMessage = "正在自動下載 ${targetInfo.tagName} (${(frac * 100).toInt()}%)"
                )
            }

            dlResult.onSuccess { apkFile ->
                _updateState.value = _updateState.value.copy(
                    phase = UpdatePhase.READY_TO_INSTALL,
                    progressFraction = 1f,
                    downloadedBytes = apkFile.length(),
                    totalBytes = apkFile.length(),
                    downloadedApkPath = apkFile.absolutePath,
                    statusMessage = "下載完成！正在自動開啟安裝畫面..."
                )
                // 優先由前景 MainActivity 直接喚起系統安裝器（解決 Android 14–16 非 Activity Context 安裝攔截）
                _pendingInstallApkPath.value = apkFile.absolutePath
            }.onFailure { err ->
                _updateState.value = _updateState.value.copy(
                    phase = UpdatePhase.ERROR,
                    statusMessage = "下載中斷或失敗：${err.localizedMessage ?: "請重試"}"
                )
            }
        }
    }

    fun consumePendingInstallApkPath() {
        _pendingInstallApkPath.value = null
    }

    fun cancelUpdateDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        val info = _updateState.value.releaseInfo
        _updateState.value = _updateState.value.copy(
            phase = if (info != null) UpdatePhase.UPDATE_AVAILABLE else UpdatePhase.IDLE,
            progressFraction = 0f,
            statusMessage = "已取消下載"
        )
    }

    fun installDownloadedUpdate() {
        val path = _updateState.value.downloadedApkPath ?: return
        val file = File(path)
        if (file.exists()) {
            _pendingInstallApkPath.value = file.absolutePath
        } else {
            startUpdateDownload()
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = _updateState.value.copy(showAutoPromptDialog = false)
    }

    /**
     * 監聽 Android 系統日曆 (`CalendarContract.CONTENT_URI`) 變更（包含 Gemini 語音助理 / Google 日曆外部寫入），
     * 透過 600ms 防抖即時刷新月曆視圖、桌面小工具與多重鬧鐘／動態島排程！
     */
    private fun registerSystemCalendarObserverIfPermitted() {
        if (calendarContentObserver != null) return
        val app = getApplication<Application>()
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                calendarObserverDebounceJob?.cancel()
                calendarObserverDebounceJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(600L)
                    _systemCalendars.value = repo.getSystemCalendars()
                    _refreshTick.value += 1
                    MultiCallAlarmScheduler.rescheduleAllUpcomingReminders(app)
                    WidgetRefreshHelper.refreshAllWidgets(app)
                }
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(
                android.provider.CalendarContract.CONTENT_URI,
                true,
                observer
            )
            calendarContentObserver = observer
        }
    }

    override fun onCleared() {
        super.onCleared()
        calendarObserverDebounceJob?.cancel()
        calendarContentObserver?.let { obs ->
            runCatching {
                getApplication<Application>().contentResolver.unregisterContentObserver(obs)
            }
        }
        calendarContentObserver = null
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
