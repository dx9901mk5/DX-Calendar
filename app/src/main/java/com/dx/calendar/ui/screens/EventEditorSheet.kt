package com.dx.calendar.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.EventTitleHistoryEntity
import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.data.model.TaskItemEntity
import com.dx.calendar.data.sync.SystemCalendarAccountInfo
import com.dx.calendar.data.sync.SystemCalendarBridge
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.util.PrivacyCryptoManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_LABEL_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日(E)")
private val TIME_LABEL_FMT = DateTimeFormatter.ofPattern("HH:mm")

// 完整還原截圖 3 的可多選提醒選項清單！
val REMINDER_PRESET_OPTIONS = listOf(
    0 to "活動時間",
    1 to "1 分鐘前",
    3 to "3 分鐘前",
    5 to "5 分鐘前",
    10 to "10 分鐘前",
    15 to "15 分鐘前",
    20 to "20 分鐘前",
    30 to "30 分鐘前",
    45 to "45 分鐘前",
    60 to "1 小時前",
    120 to "2 小時前",
    180 to "3 小時前",
    240 to "4 小時前",
    300 to "5 小時前",
    360 to "6 小時前",
    1440 to "1 天前",
    2880 to "2 天前",
    4320 to "3 天前",
    10080 to "1 週前"
)

private val QUICK_EARLY_REMINDER_CHIPS = listOf(
    0 to "準時",
    15 to "15 分前",
    60 to "1 小時前",
    1440 to "1 天前",
    2880 to "2 天前",
    4320 to "3 天前",
    10080 to "1 週前"
)

private val CUSTOM_WEEK_INTERVAL_OPTIONS = listOf(2, 3, 4, 6, 8, 12)

data class EditorSavePayload(
    val isTaskMode: Boolean,
    val editingEventId: Long?,
    val editingTaskId: Long?,
    val editingSystemEventId: Long? = null,
    val categoryId: Long,
    val writeToSystemCalendarId: Long?,
    val title: String,
    val isAllDay: Boolean,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val extraDates: List<LocalDate>,
    val recurrenceType: RecurrenceType,
    val recurrenceIntervalWeeks: Int = 1,
    val reminderMinutes: List<Int>,
    val enableMultiCall: Boolean,
    val multiCallCount: Int,
    val multiCallIntervalMinutes: Int,
    val useAlarmStream: Boolean,
    val syncSystemAlarm: Boolean,
    val isRoutine: Boolean,
    val showInMonthGrid: Boolean,
    val showInAgendaAndWidget: Boolean,
    val isPrivacy: Boolean,
    val location: String,
    val url: String,
    val notes: String,
    val isCountdown: Boolean = false,
    val anniversaryStartYear: Int? = null,
    val isBirthday: Boolean = false,
    val attachmentUris: List<String> = emptyList()
)

/**
 * 完整還原截圖 2「新的活動」+ 截圖 3「提醒可多選」+ 多個日期 + 例行月曆顯示開關按鈕組 + 多重呼叫連段鬧鐘！
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorScreen(
    initialDate: LocalDate,
    editingEvent: CalendarEventEntity?,
    editingTask: TaskItemEntity?,
    editingSystemEvent: com.dx.calendar.data.repository.UnifiedCalendarItem? = null,
    editingSystemCalendarId: Long? = null,
    presetRoutine: Boolean,
    presetPrivacy: Boolean,
    presetTaskMode: Boolean,
    privacyFeatureEnabled: Boolean = false,
    allowPrivacyEditing: Boolean = false,
    routineDefaultHideFromMonth: Boolean,
    defaultEventReminders: List<Int> = listOf(0, 5),
    defaultTaskReminders: List<Int> = listOf(0),
    categories: List<CalendarCategoryEntity>,
    writableSystemCalendars: List<SystemCalendarAccountInfo>,
    defaultGoogleEmail: String = "",
    hasCalendarPermission: Boolean = true,
    onRequestCalendarPermission: () -> Unit = {},
    titleHistory: List<EventTitleHistoryEntity>,
    parseIntList: (String) -> List<Int>,
    parseDateList: (String) -> List<LocalDate>,
    onCancel: () -> Unit,
    onSave: (EditorSavePayload) -> Unit,
    onDelete: (() -> Unit)? = null,
    initialRichMeta: com.dx.calendar.data.local.EventRichMeta? = null,
    onPickAttachmentImage: ((String) -> Unit) -> Unit = {},
    onSaveAsTemplate: ((com.dx.calendar.data.local.EventTemplateItem) -> Unit)? = null
) {
    val tokens = LocalBrandTokens.current
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    var entryMode by remember {
        mutableIntStateOf(
            when {
                editingTask != null || presetTaskMode -> 1
                editingEvent?.isRoutine == true || presetRoutine -> 2
                else -> 0
            }
        )
    } // 0: 日曆活動, 1: 待辦事項(Task), 2: 例行事務(Routine)

    val initStartZdt = editingEvent?.let { Instant.ofEpochMilli(it.startEpochMillis).atZone(zone) }
        ?: editingSystemEvent?.let { Instant.ofEpochMilli(it.startMillis).atZone(zone) }
    val initEndZdt = editingEvent?.let { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone) }
        ?: editingSystemEvent?.let { Instant.ofEpochMilli(it.endMillis).atZone(zone) }

    var title by remember {
        mutableStateOf(
            when {
                editingEvent != null -> PrivacyCryptoManager.decryptIfNeeded(editingEvent.title)
                editingTask != null -> PrivacyCryptoManager.decryptIfNeeded(editingTask.title)
                editingSystemEvent != null -> editingSystemEvent.title
                else -> ""
            }
        )
    }
    var categoryId by remember {
        mutableLongStateOf(editingEvent?.categoryId ?: categories.firstOrNull { !it.isTaskCategory }?.id ?: 1L)
    }
    val isNewRegularEvent = editingEvent == null && editingTask == null && editingSystemEvent == null && !presetPrivacy && !presetTaskMode && !presetRoutine
    val defaultGoogleCal = remember(writableSystemCalendars, defaultGoogleEmail) {
        SystemCalendarBridge.pickDefaultGoogleCalendar(writableSystemCalendars, defaultGoogleEmail)
    }
    var userManuallyPickedCalendar by remember { mutableStateOf(editingSystemEvent != null) }
    var selectedSystemCalId by remember {
        mutableStateOf<Long?>(
            when {
                editingSystemEvent != null -> editingSystemCalendarId ?: defaultGoogleCal?.id
                isNewRegularEvent -> defaultGoogleCal?.id
                else -> null
            }
        )
    }

    // 若進入新增頁時系統剛載入完裝置已登記的 Gmail Google 行事曆，且使用者尚未手動切換，自動設為預設！
    LaunchedEffect(defaultGoogleCal?.id, isNewRegularEvent) {
        if (isNewRegularEvent && !userManuallyPickedCalendar && selectedSystemCalId == null && defaultGoogleCal != null) {
            selectedSystemCalId = defaultGoogleCal.id
        }
    }

    var isAllDay by remember { mutableStateOf(editingEvent?.isAllDay ?: editingSystemEvent?.isAllDay ?: false) }
    var startDate by remember {
        mutableStateOf(
            initStartZdt?.toLocalDate()
                ?: editingTask?.dueEpochDay?.let { LocalDate.ofEpochDay(it) }
                ?: initialDate
        )
    }
    var startTime by remember {
        mutableStateOf(
            initStartZdt?.toLocalTime()
                ?: editingTask?.dueMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) }
                ?: LocalTime.of((LocalTime.now().hour + 1) % 24, 0)
        )
    }
    var endDate by remember { mutableStateOf(initEndZdt?.toLocalDate() ?: startDate) }
    var endTime by remember { mutableStateOf(initEndZdt?.toLocalTime() ?: startTime.plusHours(1)) }

    val extraDates = remember {
        mutableStateListOf<LocalDate>().apply {
            if (editingEvent != null) addAll(parseDateList(editingEvent.extraDatesJson))
        }
    }

    val initialRecPair = remember(editingEvent?.id, editingTask?.id, presetRoutine) {
        com.dx.calendar.data.model.RecurrenceCodec.decode(
            editingEvent?.recurrenceType ?: editingTask?.recurrenceType ?: if (presetRoutine) "DAILY" else "NONE"
        )
    }
    var recurrence by remember { mutableStateOf(initialRecPair.first) }
    var recurrenceIntervalWeeks by remember { mutableIntStateOf(initialRecPair.second.coerceAtLeast(2)) }

    val selectedReminders = remember {
        mutableStateListOf<Int>().apply {
            val raw = when {
                editingEvent != null -> parseIntList(editingEvent.reminderMinutesJson)
                editingTask != null -> parseIntList(editingTask.reminderMinutesJson)
                editingSystemEvent != null -> editingSystemEvent.reminderMinutes
                presetTaskMode -> defaultTaskReminders
                else -> defaultEventReminders
            }
            addAll(raw)
        }
    }

    // 多重連段呼叫與連動鬧鐘
    var enableMultiCall by remember {
        mutableStateOf(editingEvent?.enableMultiCall ?: editingTask?.enableMultiCall ?: editingSystemEvent?.enableMultiCall ?: false)
    }
    var multiCallCount by remember {
        mutableIntStateOf(editingEvent?.multiCallCount ?: editingTask?.multiCallCount ?: editingSystemEvent?.multiCallCount ?: 3)
    }
    var multiCallInterval by remember {
        mutableIntStateOf(editingEvent?.multiCallIntervalMinutes ?: editingTask?.multiCallIntervalMinutes ?: editingSystemEvent?.multiCallIntervalMinutes ?: 3)
    }
    var useAlarmStream by remember {
        mutableStateOf(editingEvent?.useAlarmStream ?: editingTask?.useAlarmStream ?: editingSystemEvent?.useAlarmStream ?: false)
    }
    var syncSystemAlarm by remember {
        mutableStateOf(editingEvent?.syncSystemAlarm ?: editingTask?.syncSystemAlarm ?: editingSystemEvent?.syncSystemAlarm ?: false)
    }

    // 細緻顯示控制按鈕狀態（核心需求！）
    var isRoutine by remember { mutableStateOf(editingEvent?.isRoutine ?: editingTask?.isRoutine ?: presetRoutine) }
    var showInMonthGrid by remember {
        mutableStateOf(
            editingEvent?.showInMonthGrid
                ?: editingTask?.showInMonthGrid
                ?: if (presetRoutine && routineDefaultHideFromMonth) false else true
        )
    }
    var showInAgendaAndWidget by remember {
        mutableStateOf(editingEvent?.showInAgendaAndWidget ?: editingTask?.showInAgendaAndWidget ?: true)
    }
    var isPrivacy by remember { mutableStateOf(editingEvent?.isPrivacy ?: editingTask?.isPrivacy ?: presetPrivacy) }

    var location by remember {
        mutableStateOf(
            editingEvent?.let { PrivacyCryptoManager.decryptIfNeeded(it.location) }
                ?: editingSystemEvent?.location
                ?: ""
        )
    }
    var url by remember { mutableStateOf(editingEvent?.url ?: "") }
    var notes by remember {
        mutableStateOf(
            when {
                editingEvent != null -> PrivacyCryptoManager.decryptIfNeeded(editingEvent.notes)
                editingTask != null -> PrivacyCryptoManager.decryptIfNeeded(editingTask.notes)
                editingSystemEvent != null -> editingSystemEvent.notes
                else -> ""
            }
        )
    }

    var showHistoryMenu by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showMultiReminderDialog by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }
    var showExtraDatesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isCountdown by remember { mutableStateOf(initialRichMeta?.isCountdown ?: false) }
    var isBirthday by remember { mutableStateOf(initialRichMeta?.isBirthday ?: false) }
    var anniversaryYearText by remember { mutableStateOf(initialRichMeta?.anniversaryStartYear?.toString().orEmpty()) }
    val attachmentUris = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            initialRichMeta?.attachmentUris?.let { addAll(it) }
        }
    }
    val isEditingExisting = editingEvent != null || editingTask != null || editingSystemEvent != null

    Surface(color = tokens.backgroundColor, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 頂部導覽列（←  編輯項目  [刪除]  保存，避開系統狀態列）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surfaceCardColor)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = tokens.primaryTextColor)
                }
                Text(
                    text = if (isEditingExisting) "編輯項目" else "新的活動 / 提醒",
                    color = tokens.primaryTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEditingExisting && onDelete != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "刪除項目",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            val finalTitle = title.trim().ifBlank { "未命名提醒" }
                            onSave(
                                EditorSavePayload(
                                    isTaskMode = entryMode == 1,
                                    editingEventId = editingEvent?.id,
                                    editingTaskId = editingTask?.id,
                                    editingSystemEventId = editingSystemEvent?.rawId,
                                    categoryId = categoryId,
                                    writeToSystemCalendarId = if (isPrivacy) null else selectedSystemCalId,
                                    title = finalTitle,
                                    isAllDay = isAllDay,
                                    startDate = startDate,
                                    startTime = startTime,
                                    endDate = if (endDate.isBefore(startDate)) startDate else endDate,
                                    endTime = endTime,
                                    extraDates = extraDates.toList(),
                                    recurrenceType = recurrence,
                                    recurrenceIntervalWeeks = if (recurrence == RecurrenceType.CUSTOM_WEEKS) recurrenceIntervalWeeks else 1,
                                    reminderMinutes = selectedReminders.toList(),
                                    enableMultiCall = enableMultiCall,
                                    multiCallCount = multiCallCount,
                                    multiCallIntervalMinutes = multiCallInterval,
                                    useAlarmStream = useAlarmStream,
                                    syncSystemAlarm = syncSystemAlarm,
                                    isRoutine = isRoutine || entryMode == 2,
                                    showInMonthGrid = showInMonthGrid,
                                    showInAgendaAndWidget = showInAgendaAndWidget,
                                    isPrivacy = isPrivacy,
                                    location = location.trim(),
                                    url = url.trim(),
                                    notes = notes.trim(),
                                    isCountdown = isCountdown,
                                    anniversaryStartYear = anniversaryYearText.trim().toIntOrNull()?.takeIf { it in 1900..2100 },
                                    isBirthday = isBirthday,
                                    attachmentUris = attachmentUris.toList()
                                )
                            )
                        }
                    ) {
                        Text("保存", color = tokens.accentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val leftEditorSection: @Composable () -> Unit = {
                // 模式快速切換：一般活動 / 待辦(Task) / 例行事務(Routine)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("一般活動", "待辦 Task", "例行事務").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = entryMode == index,
                            onClick = {
                                entryMode = index
                                if (index == 2) {
                                    isRoutine = true
                                    if (routineDefaultHideFromMonth) showInMonthGrid = false
                                    if (recurrence == RecurrenceType.NONE) recurrence = RecurrenceType.DAILY
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }

                // 區塊 1：日曆選擇（仿截圖 2）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryDialog = true }
                            .padding(horizontal = 16.dp, vertical = 15.dp)
                    ) {
                        Text("行事曆", color = tokens.primaryTextColor, fontSize = 15.sp)
                        val taskCat = categories.find { it.isTaskCategory }
                        val currentCat = if (entryMode == 1 && taskCat != null) taskCat else categories.find { it.id == categoryId }
                        val sysCal = if (entryMode == 1) null else writableSystemCalendars.find { it.id == selectedSystemCalId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        Color(sysCal?.colorArgb ?: currentCat?.colorArgb ?: 0xFFFF5238.toInt()),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sysCal?.let {
                                    if (it.isGoogleAccount) "Google · ${it.displayName}" else it.displayName
                                } ?: (currentCat?.name ?: "個人"),
                                color = tokens.primaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // 區塊 2：標題 + 歷史範本按鈕（仿截圖 2 右側時鐘圖示）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("標題 (例如：開會、每日吃藥提醒)") },
                            trailingIcon = {
                                Box {
                                    IconButton(onClick = { showHistoryMenu = true }) {
                                        Icon(Icons.Filled.History, contentDescription = "常用標題紀錄")
                                    }
                                    DropdownMenu(
                                        expanded = showHistoryMenu,
                                        onDismissRequest = { showHistoryMenu = false }
                                    ) {
                                        titleHistory.forEach { h ->
                                            DropdownMenuItem(
                                                text = { Text(h.title) },
                                                onClick = {
                                                    title = h.title
                                                    showHistoryMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 區塊 3（用戶核心需求）：「多個按鈕讓用戶決定該提醒是否顯示在月曆上，或歸入例行中心 / 隱私日曆」
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "月曆顯示與收納方式 (個別控制，無須關閉整個帳號)",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            // 按鈕 A：顯示在月曆上
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (showInMonthGrid) tokens.accentColor.copy(alpha = 0.22f) else tokens.elevatedSurfaceColor)
                                    .border(
                                        width = if (showInMonthGrid) 1.5.dp else 0.dp,
                                        color = if (showInMonthGrid) tokens.accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { showInMonthGrid = true }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = if (showInMonthGrid) tokens.accentColor else tokens.secondaryTextColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("顯示於月曆格", color = tokens.primaryTextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // 按鈕 B：不在月曆佔空間（僅在設定內啟用隱私開關後顯示，預設隱藏）
                            if (privacyFeatureEnabled) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (!showInMonthGrid) tokens.accentColor.copy(alpha = 0.22f) else tokens.elevatedSurfaceColor)
                                        .border(
                                            width = if (!showInMonthGrid) 1.5.dp else 0.dp,
                                            color = if (!showInMonthGrid) tokens.accentColor else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            showInMonthGrid = false
                                            isRoutine = true
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = if (!showInMonthGrid) tokens.accentColor else tokens.secondaryTextColor, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("不在月曆佔空間", color = tokens.primaryTextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isRoutine,
                                onClick = { isRoutine = !isRoutine },
                                leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("收錄至「例行中心」集中管理", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = showInAgendaAndWidget,
                                onClick = { showInAgendaAndWidget = !showInAgendaAndWidget },
                                label = { Text("在時間表與小工具顯示", fontSize = 12.sp) }
                            )
                            if (allowPrivacyEditing) {
                                FilterChip(
                                    selected = isPrivacy,
                                    onClick = { isPrivacy = !isPrivacy },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("隱私加密", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            val rightEditorSection: @Composable () -> Unit = {
                // 區塊 4：整日活動、開始、結束（仿截圖 2）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("整日活動", color = tokens.primaryTextColor, fontSize = 15.sp)
                            Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                        }

                        HorizontalDivider(color = tokens.gridLineColor, modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text("開始", color = tokens.primaryTextColor, fontSize = 15.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = startDate.format(DATE_LABEL_FMT),
                                    color = tokens.primaryTextColor,
                                    fontSize = 14.sp,
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    val picked = LocalDate.of(y, m + 1, d)
                                                    startDate = picked
                                                    if (endDate.isBefore(picked)) endDate = picked
                                                },
                                                startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
                                            ).show()
                                        }
                                    }
                                )
                                if (!isAllDay) {
                                    Text(
                                        text = startTime.format(TIME_LABEL_FMT),
                                        color = tokens.accentColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                TimePickerDialog(
                                                    context,
                                                    { _, h, min ->
                                                        startTime = LocalTime.of(h, min)
                                                        endTime = startTime.plusHours(1)
                                                    },
                                                    startTime.hour, startTime.minute, true
                                                ).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (entryMode != 1) {
                            HorizontalDivider(color = tokens.gridLineColor, modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text("結束", color = tokens.primaryTextColor, fontSize = 15.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = endDate.format(DATE_LABEL_FMT),
                                        color = tokens.primaryTextColor,
                                        fontSize = 14.sp,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                DatePickerDialog(
                                                    context,
                                                    { _, y, m, d -> endDate = LocalDate.of(y, m + 1, d) },
                                                    endDate.year, endDate.monthValue - 1, endDate.dayOfMonth
                                                ).show()
                                            }
                                        }
                                    )
                                    if (!isAllDay) {
                                        Text(
                                            text = endTime.format(TIME_LABEL_FMT),
                                            color = tokens.accentColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                runCatching {
                                                    TimePickerDialog(
                                                        context,
                                                        { _, h, min -> endTime = LocalTime.of(h, min) },
                                                        endTime.hour, endTime.minute, true
                                                    ).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 區塊 5：多個日期、重複、提醒（仿截圖 2 & 截圖 3 可多選）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (entryMode != 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showExtraDatesDialog = true }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text("多個日期", color = tokens.primaryTextColor, fontSize = 15.sp)
                                Text(
                                    text = if (extraDates.isEmpty()) "無" else "額外 ${extraDates.size} 個日期",
                                    color = tokens.secondaryTextColor,
                                    fontSize = 14.sp
                                )
                            }
                            HorizontalDivider(color = tokens.gridLineColor)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRecurrenceDialog = true }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("重複", color = tokens.primaryTextColor, fontSize = 15.sp)
                            Text(
                                text = com.dx.calendar.data.model.RecurrenceCodec.formatLabel(recurrence, recurrenceIntervalWeeks),
                                color = if (recurrence == RecurrenceType.NONE) tokens.secondaryTextColor else tokens.accentColor,
                                fontSize = 14.sp
                            )
                        }

                        // 隔幾週提醒一次（FREQ=WEEKLY;INTERVAL=N）快捷晶片列
                        Text("隔幾週提醒一次：", color = tokens.secondaryTextColor, fontSize = 11.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        ) {
                            CUSTOM_WEEK_INTERVAL_OPTIONS.forEach { weeks ->
                                val isPicked = (recurrence == RecurrenceType.CUSTOM_WEEKS && recurrenceIntervalWeeks == weeks) ||
                                    (weeks == 2 && recurrence == RecurrenceType.BIWEEKLY)
                                FilterChip(
                                    selected = isPicked,
                                    onClick = {
                                        if (isPicked) {
                                            recurrence = RecurrenceType.NONE
                                        } else {
                                            recurrence = RecurrenceType.CUSTOM_WEEKS
                                            recurrenceIntervalWeeks = weeks
                                        }
                                    },
                                    label = { Text("隔 $weeks 週", fontSize = 12.sp) }
                                )
                            }
                        }

                        HorizontalDivider(color = tokens.gridLineColor)

                        val remSummary = if (selectedReminders.isEmpty()) {
                            "無"
                        } else {
                            selectedReminders.sorted().joinToString(", ") { min ->
                                REMINDER_PRESET_OPTIONS.find { it.first == min }?.second ?: "${min}分鐘前"
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMultiReminderDialog = true }
                                .padding(vertical = 12.dp)
                        ) {
                            Text("提醒 (可多選)", color = tokens.primaryTextColor, fontSize = 15.sp)
                            Text(
                                text = remSummary,
                                color = tokens.accentColor,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }

                        // 提早通知快捷多選晶片
                        Text("提早通知快捷多選：", color = tokens.secondaryTextColor, fontSize = 11.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        ) {
                            QUICK_EARLY_REMINDER_CHIPS.forEach { (mins, label) ->
                                val checked = selectedReminders.contains(mins)
                                FilterChip(
                                    selected = checked,
                                    onClick = {
                                        if (checked) selectedReminders.remove(mins)
                                        else selectedReminders.add(mins)
                                    },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                // 區塊 5.5：啟用吃藥／例行提醒（新增事件時可點選啟用，立即展開專屬邏輯與自定義重複邏輯！）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Repeat, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("啟用吃藥／例行提醒", color = tokens.primaryTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "開啟後套用例行打卡邏輯、自定義重複週期，並預設不塞爆月曆格",
                                        color = tokens.secondaryTextColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = isRoutine,
                                onCheckedChange = { enabled ->
                                    isRoutine = enabled
                                    if (enabled) {
                                        if (recurrence == RecurrenceType.NONE) recurrence = RecurrenceType.DAILY
                                        if (routineDefaultHideFromMonth) showInMonthGrid = false
                                        enableMultiCall = true
                                    } else {
                                        showInMonthGrid = true
                                    }
                                }
                            )
                        }

                        if (isRoutine) {
                            HorizontalDivider(color = tokens.gridLineColor)
                            Text("自定義重複週期：", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    RecurrenceType.DAILY to "每天",
                                    RecurrenceType.WEEKDAYS to "週一至週五",
                                    RecurrenceType.WEEKLY to "每週",
                                    RecurrenceType.BIWEEKLY to "每 2 週",
                                    RecurrenceType.MONTHLY to "每月"
                                ).forEach { (rec, lbl) ->
                                    FilterChip(
                                        selected = recurrence == rec,
                                        onClick = { recurrence = rec },
                                        label = { Text(lbl, fontSize = 12.sp) }
                                    )
                                }
                                FilterChip(
                                    selected = extraDates.isNotEmpty(),
                                    onClick = { showExtraDatesDialog = true },
                                    label = { Text("自訂多個日期 (${extraDates.size})", fontSize = 12.sp) }
                                )
                            }

                            if (!isAllDay) {
                                Text("服藥／例行快捷時段：", color = tokens.secondaryTextColor, fontSize = 12.sp)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "早 08:00" to LocalTime.of(8, 0),
                                        "午 12:30" to LocalTime.of(12, 30),
                                        "晚 19:00" to LocalTime.of(19, 0),
                                        "睡前 22:00" to LocalTime.of(22, 0)
                                    ).forEach { (lbl, tm) ->
                                        FilterChip(
                                            selected = startTime == tm,
                                            onClick = {
                                                startTime = tm
                                                endTime = tm.plusMinutes(30)
                                            },
                                            label = { Text(lbl, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("不在月曆格佔空間（僅於例行中心與提醒顯示）", color = tokens.primaryTextColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Switch(checked = !showInMonthGrid, onCheckedChange = { showInMonthGrid = !it })
                            }
                        }
                    }
                }

                // 區塊 6：多重呼叫（連段追擊提醒）& 連動鬧鐘設定（核心需求！）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = tokens.accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "多重呼叫 (連段提醒) 與連動鬧鐘",
                                color = tokens.primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("啟用連段追擊呼叫", color = tokens.primaryTextColor, fontSize = 14.sp)
                                Text("未手動確認前，依間隔自動連續多次呼叫提醒", color = tokens.secondaryTextColor, fontSize = 11.sp)
                            }
                            Switch(checked = enableMultiCall, onCheckedChange = { enableMultiCall = it })
                        }

                        if (enableMultiCall) {
                            Text("連段呼叫次數：", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(2, 3, 5, 8, 10).forEach { cnt ->
                                    FilterChip(
                                        selected = multiCallCount == cnt,
                                        onClick = { multiCallCount = cnt },
                                        label = { Text("連響 $cnt 次") }
                                    )
                                }
                            }
                            Text("每次連段間隔：", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3, 5, 10).forEach { iv ->
                                    FilterChip(
                                        selected = multiCallInterval == iv,
                                        onClick = { multiCallInterval = iv },
                                        label = { Text("每 $iv 分鐘") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = tokens.gridLineColor)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("使用全螢幕鬧鐘級強提醒", color = tokens.primaryTextColor, fontSize = 14.sp)
                                Text("鎖屏全螢幕彈出 + 鬧鐘音軌響鈴震動 (不怕靜音漏看)", color = tokens.secondaryTextColor, fontSize = 11.sp)
                            }
                            Switch(checked = useAlarmStream, onCheckedChange = { useAlarmStream = it })
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Alarm, contentDescription = null, tint = tokens.secondaryTextColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("同步連動手機系統鬧鐘 App", color = tokens.primaryTextColor, fontSize = 14.sp)
                                    Text("保存時自動在系統原生「時鐘」建立對應時間鬧鐘", color = tokens.secondaryTextColor, fontSize = 11.sp)
                                }
                            }
                            Switch(checked = syncSystemAlarm, onCheckedChange = { syncSystemAlarm = it })
                        }
                    }
                }

                // 區塊 7：地點、URL、筆記（仿截圖 2）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (entryMode != 1) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("地點") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("筆記 / 備註") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 區塊 8（v2.0 新增）：倒數日／生日週年計算 ＋ 圖片附件 ＋ 存為範本
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "倒數日、紀念日與圖片附件",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isCountdown,
                                onClick = { isCountdown = !isCountdown },
                                label = { Text("⏳ 標記為倒數日", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = isBirthday,
                                onClick = {
                                    isBirthday = !isBirthday
                                    if (isBirthday) isCountdown = true
                                },
                                label = { Text("🎂 生日自動算歲數", fontSize = 12.sp) }
                            )
                        }
                        if (isCountdown || isBirthday) {
                            OutlinedTextField(
                                value = anniversaryYearText,
                                onValueChange = { s -> anniversaryYearText = s.filter { it.isDigit() }.take(4) },
                                label = { Text(if (isBirthday) "出生西元年（選填，如 1995）" else "紀念起始西元年（選填，如 2020）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        HorizontalDivider(color = tokens.gridLineColor)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("圖片附件 (${attachmentUris.size})", color = tokens.primaryTextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            OutlinedButton(
                                onClick = {
                                    onPickAttachmentImage { uriStr ->
                                        if (uriStr.isNotBlank() && !attachmentUris.contains(uriStr)) {
                                            attachmentUris.add(uriStr)
                                        }
                                    }
                                }
                            ) {
                                Text("＋ 附加圖片 / 截圖", fontSize = 12.sp)
                            }
                        }
                        if (attachmentUris.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                attachmentUris.forEach { uriStr ->
                                    Box(modifier = Modifier.size(64.dp)) {
                                        coil.compose.AsyncImage(
                                            model = android.net.Uri.parse(uriStr),
                                            contentDescription = "附件縮圖",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, tokens.gridLineColor, RoundedCornerShape(8.dp))
                                        )
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                                .clickable { attachmentUris.remove(uriStr) }
                                        ) {
                                            Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        if (onSaveAsTemplate != null) {
                            OutlinedButton(
                                onClick = {
                                    val durMin = java.time.Duration.between(startTime, endTime).toMinutes().toInt()
                                        .let { if (it <= 0) 60 else it }
                                    onSaveAsTemplate(
                                        com.dx.calendar.data.local.EventTemplateItem(
                                            name = title.trim().ifBlank { "自訂範本" },
                                            startHour = startTime.hour,
                                            startMinute = startTime.minute,
                                            durationMinutes = durMin,
                                            isAllDay = isAllDay,
                                            location = location.trim(),
                                            notes = notes.trim(),
                                            reminderMinutes = selectedReminders.toList(),
                                            enableMultiCall = enableMultiCall
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🏷️ 存為常用／輪班排班範本", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (isEditingExisting && onDelete != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刪除此項目", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val useSideBySide = maxWidth >= 600.dp
                if (useSideBySide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            leftEditorSection()
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rightEditorSection()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        leftEditorSection()
                        rightEditorSection()
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("確認刪除此項目？") },
            text = { Text("刪除後將同時取消關聯的多重呼叫提醒，且無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    }
                ) { Text("確認刪除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    // === 截圖 3：提醒「可多選。」對話框 ===
    if (showMultiReminderDialog) {
        AlertDialog(
            onDismissRequest = { showMultiReminderDialog = false },
            title = {
                Column {
                    Text("提醒", fontWeight = FontWeight.Bold)
                    Text("可多選。", fontSize = 13.sp, color = tokens.secondaryTextColor)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .height(380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    REMINDER_PRESET_OPTIONS.forEach { (minutes, label) ->
                        val checked = selectedReminders.contains(minutes)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedReminders.remove(minutes)
                                    else selectedReminders.add(minutes)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(label, fontSize = 15.sp, color = tokens.primaryTextColor)
                            if (checked) {
                                Icon(Icons.Filled.Check, contentDescription = "已選", tint = tokens.accentColor)
                            }
                        }
                        HorizontalDivider(color = tokens.gridLineColor)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMultiReminderDialog = false }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { selectedReminders.clear() }) { Text("清空無提醒") }
            }
        )
    }

    // === 重複規則選擇對話框 ===
    if (showRecurrenceDialog) {
        AlertDialog(
            onDismissRequest = { showRecurrenceDialog = false },
            title = { Text("重複規則") },
            text = {
                Column {
                    RecurrenceType.entries.forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    recurrence = type
                                    showRecurrenceDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(type.label, fontSize = 15.sp)
                            if (recurrence == type) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = tokens.accentColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecurrenceDialog = false }) { Text("關閉") }
            }
        )
    }

    // === 截圖 2：「多個日期」挑選對話框 ===
    if (showExtraDatesDialog) {
        AlertDialog(
            onDismissRequest = { showExtraDatesDialog = false },
            title = { Text("多個日期 (批次建立相同活動)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("除了主日期 ($startDate) 外，還可加入任意多個不連續日期：", fontSize = 13.sp)
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val picked = LocalDate.of(y, m + 1, d)
                                    if (!extraDates.contains(picked) && picked != startDate) {
                                        extraDates.add(picked)
                                    }
                                },
                                startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ 點此挑選額外日期")
                    }
                    extraDates.sorted().forEach { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(d.format(DATE_LABEL_FMT), fontSize = 14.sp)
                            TextButton(onClick = { extraDates.remove(d) }) { Text("移除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExtraDatesDialog = false }) { Text("完成") }
            }
        )
    }

    // === 行事曆選擇對話框（Google 行事曆置頂優先可選，簡潔不露帳號） ===
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("選擇行事曆") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (!isPrivacy) {
                        Text(
                            text = "Google 行事曆（預設同步至已登記 Gmail）",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.accentColor
                        )
                        if (!hasCalendarPermission) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    showCategoryDialog = false
                                    onRequestCalendarPermission()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("授權並顯示 Google 行事曆", fontSize = 13.sp)
                            }
                        } else if (writableSystemCalendars.isEmpty()) {
                            Text(
                                text = "裝置尚未偵測到可寫入的 Google 行事曆",
                                fontSize = 12.sp,
                                color = tokens.secondaryTextColor,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            writableSystemCalendars.forEach { sc ->
                                val label = if (sc.isGoogleAccount) "Google · ${sc.displayName}" else sc.displayName
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            userManuallyPickedCalendar = true
                                            selectedSystemCalId = sc.id
                                            if (entryMode == 1) entryMode = 0
                                            showCategoryDialog = false
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Box(modifier = Modifier.size(12.dp).background(Color(sc.colorArgb), CircleShape))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    if (entryMode != 1 && selectedSystemCalId == sc.id) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = tokens.accentColor)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = tokens.gridLineColor)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text("本機行事曆與待辦", fontSize = 12.sp, color = tokens.secondaryTextColor)
                    categories.filter { allowPrivacyEditing || !it.isPrivacyCategory }.forEach { cat ->
                        val isSelectedCat = if (cat.isTaskCategory) {
                            entryMode == 1
                        } else {
                            entryMode != 1 && selectedSystemCalId == null && categoryId == cat.id
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    userManuallyPickedCalendar = true
                                    categoryId = cat.id
                                    selectedSystemCalId = null
                                    if (cat.isTaskCategory) {
                                        entryMode = 1
                                    } else {
                                        if (entryMode == 1) entryMode = 0
                                        if (cat.isPrivacyCategory) isPrivacy = true
                                    }
                                    showCategoryDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(Color(cat.colorArgb), CircleShape))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(cat.name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            if (isSelectedCat) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = tokens.accentColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("關閉") }
            }
        )
    }
}
