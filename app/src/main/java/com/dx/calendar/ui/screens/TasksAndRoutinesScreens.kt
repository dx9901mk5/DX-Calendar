package com.dx.calendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.model.CalendarEventEntity
import com.dx.calendar.data.model.ExternalEventOverrideEntity
import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.data.model.RoutineCheckInEntity
import com.dx.calendar.data.model.TaskItemEntity
import com.dx.calendar.data.repository.UnifiedCalendarItem
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.util.PrivacyCryptoManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 1. Google Tasks 與待辦事項中心（仿 vivo 待辦事項 + Google Tasks 雲端同步）
 */
@Composable
fun TasksCenterView(
    tasks: List<TaskItemEntity>,
    isPrivacyUnlocked: Boolean,
    showCompleted: Boolean,
    googleEmail: String,
    isSyncingTasks: Boolean,
    onQuickAddTask: (title: String, dueToday: Boolean, showInMonth: Boolean) -> Unit,
    onToggleTask: (TaskItemEntity) -> Unit,
    onToggleTaskMonthVisible: (TaskItemEntity) -> Unit,
    onDeleteTask: (TaskItemEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onToggleShowCompleted: (Boolean) -> Unit,
    onSyncGoogleTasks: () -> Unit,
    onOpenFullEditorForTask: () -> Unit,
    onScheduleTaskTimeBlock: ((TaskItemEntity, Int, Int) -> Unit)? = null,
    onStartPomodoroForTask: ((TaskItemEntity) -> Unit)? = null
) {
    val tokens = LocalBrandTokens.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var quickText by remember { mutableStateOf("") }
    var quickShowInMonth by remember { mutableStateOf(true) }

    val visibleTasks = remember(tasks, isPrivacyUnlocked, showCompleted) {
        tasks.filter { (!it.isPrivacy || isPrivacyUnlocked) && (showCompleted || !it.isCompleted) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // 頂部 Google Tasks 同步狀態與操作列
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(tokens.cardCorner),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "待辦事項 & Google Tasks",
                            color = tokens.primaryTextColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (googleEmail.isNotBlank()) "已連結雲端同步 · 可自由決定待辦是否顯示於月曆格"
                            else "支援本機待辦與連結 Google Tasks 同步顯示於日曆",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onSyncGoogleTasks,
                        enabled = !isSyncingTasks
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSyncingTasks) "同步中..." else "同步 Google Tasks", fontSize = 12.sp)
                    }
                }

                // 快速新增待辦輸入列 + 月曆顯示決定鈕
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = quickText,
                        onValueChange = { quickText = it },
                        placeholder = { Text("快速新增今日待辦事項...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (quickText.isNotBlank()) {
                                onQuickAddTask(quickText.trim(), true, quickShowInMonth)
                                quickText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                    ) {
                        Text("新增")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = quickShowInMonth,
                        onClick = { quickShowInMonth = !quickShowInMonth },
                        label = {
                            Text(
                                if (quickShowInMonth) "新待辦：顯示於月曆格" else "新待辦：不在月曆佔空間",
                                fontSize = 11.sp
                            )
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpenFullEditorForTask) {
                            Text("進階提醒設定", fontSize = 12.sp)
                        }
                        TextButton(onClick = onClearCompleted) {
                            Text("清除已完成", fontSize = 12.sp, color = tokens.secondaryTextColor)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "待辦清單 (${visibleTasks.count { !it.isCompleted }} 項未完成)",
                color = tokens.secondaryTextColor,
                fontSize = 13.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("顯示已完成", color = tokens.secondaryTextColor, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(checked = showCompleted, onCheckedChange = onToggleShowCompleted)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 92.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visibleTasks, key = { it.id }) { task ->
                val title = PrivacyCryptoManager.decryptIfNeeded(task.title)
                val dueStr = task.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "未設期限"
                val timeStr = task.dueMinuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: ""

                Surface(
                    color = tokens.surfaceCardColor,
                    shape = RoundedCornerShape(tokens.cardCorner),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = "完成",
                            tint = Color(task.colorArgb),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onToggleTask(task) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = tokens.primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Text(
                                text = "$dueStr $timeStr · ${task.googleTaskListTitle}" +
                                    if (!task.showInMonthGrid) " · 月曆已隱藏" else "",
                                color = tokens.secondaryTextColor,
                                fontSize = 12.sp
                            )
                        }
                        if (onScheduleTaskTimeBlock != null) {
                            TextButton(
                                onClick = {
                                    val initH = (task.dueMinuteOfDay ?: 540) / 60
                                    val initM = (task.dueMinuteOfDay ?: 540) % 60
                                    runCatching {
                                        android.app.TimePickerDialog(context, { _, h, m ->
                                            onScheduleTaskTimeBlock(task, h, m)
                                        }, initH, initM, true).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) { Text("⏰排時段", fontSize = 11.sp, color = tokens.accentColor) }
                        }
                        if (onStartPomodoroForTask != null) {
                            TextButton(
                                onClick = { onStartPomodoroForTask(task) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) { Text("🍅專注", fontSize = 11.sp, color = tokens.accentColor) }
                        }
                        IconButton(onClick = { onToggleTaskMonthVisible(task) }) {
                            Icon(
                                imageVector = if (task.showInMonthGrid) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "切換月曆顯示",
                                tint = if (task.showInMonthGrid) tokens.secondaryTextColor else tokens.accentColor
                            )
                        }
                        IconButton(onClick = { onDeleteTask(task) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除", tint = tokens.secondaryTextColor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. 例行事務與隱藏提醒集中管理中心 (Routine & Hidden Reminders Hub)
 * 解決用戶核心痛點：
 * - 每日吃藥提醒、換防蚊液等例行事務可關閉月曆顯示，不佔月曆空間！
 * - 所有不在月曆顯示或設為例行的提醒全部集中在此統一管理、打卡、暫停、清理，絕不累積遺忘！
 * - 突破「簡單日曆只能用帳號區分是否顯示」的限制！
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutinesAndHiddenHubView(
    allEvents: List<CalendarEventEntity>,
    allTasks: List<TaskItemEntity>,
    checkIns: List<RoutineCheckInEntity>,
    externalOverrides: List<ExternalEventOverrideEntity>,
    isPrivacyUnlocked: Boolean,
    routineMedicationFeatureEnabled: Boolean,
    routineDefaultHideFromMonth: Boolean,
    onToggleRoutineMedicationFeature: (Boolean) -> Unit,
    onToggleDefaultRoutineHide: (Boolean) -> Unit,
    onAddRoutineQuick: () -> Unit,
    onToggleTodayCheckIn: (targetType: String, targetId: Long, currentlyDone: Boolean) -> Unit,
    onToggleEventMonthShow: (CalendarEventEntity) -> Unit,
    onToggleEventPause: (CalendarEventEntity) -> Unit,
    onEditEvent: (CalendarEventEntity) -> Unit,
    onDeleteEvent: (CalendarEventEntity) -> Unit,
    onToggleTaskMonthShow: (TaskItemEntity) -> Unit,
    onRestoreExternalOverride: (String) -> Unit,
    onCleanupExpiredItems: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val todayEpoch = LocalDate.now().toEpochDay()
    val zone = ZoneId.systemDefault()
    var tabFilter by remember { mutableIntStateOf(0) } // 0: 全部集中管理, 1: 僅月曆隱藏項, 2: 外部帳號隱藏規則

    val checkInSet = remember(checkIns) {
        checkIns.map { "${it.targetType}:${it.targetId}:${it.epochDay}" }.toHashSet()
    }

    val managedEvents = remember(allEvents, isPrivacyUnlocked, tabFilter) {
        allEvents.filter { ev ->
            (!ev.isPrivacy || isPrivacyUnlocked) &&
                (ev.isRoutine || !ev.showInMonthGrid) &&
                (tabFilter != 1 || !ev.showInMonthGrid)
        }
    }

    val managedTasks = remember(allTasks, isPrivacyUnlocked, tabFilter) {
        allTasks.filter { t ->
            (!t.isPrivacy || isPrivacyUnlocked) &&
                (t.isRoutine || !t.showInMonthGrid) &&
                (tabFilter != 1 || !t.showInMonthGrid)
        }
    }

    val hiddenCount = managedEvents.count { !it.showInMonthGrid } +
        managedTasks.count { !it.showInMonthGrid } +
        externalOverrides.count { !it.showInMonthGrid }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 頂部統計與智慧清理儀表板
        item {
            Surface(
                color = tokens.surfaceCardColor,
                shape = RoundedCornerShape(tokens.cardCorner),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "例行事務與隱藏提醒中心",
                                color = tokens.primaryTextColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "預設關閉且不自動添加任何吃藥提醒；開啟後可依需求自行新增與設定不佔月曆規則",
                                color = tokens.secondaryTextColor,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = onAddRoutineQuick,
                            enabled = routineMedicationFeatureEnabled,
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新增例行")
                        }
                    }

                    HorizontalDivider(color = tokens.gridLineColor)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "啟用吃藥／例行提醒功能（預設關閉）",
                                color = tokens.primaryTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (routineMedicationFeatureEnabled) "已開啟：可自行新增項目並依規則排程提醒"
                                else "已關閉：不會建立或觸發任何吃藥／例行提醒",
                                color = tokens.secondaryTextColor,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = routineMedicationFeatureEnabled,
                            onCheckedChange = onToggleRoutineMedicationFeature
                        )
                    }

                    // 三格統計數據
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatMiniBox(
                            modifier = Modifier.weight(1f),
                            value = "${managedEvents.size + managedTasks.size}",
                            label = "集中納管項目"
                        )
                        StatMiniBox(
                            modifier = Modifier.weight(1f),
                            value = "$hiddenCount",
                            label = "已省下月曆顯示"
                        )
                        StatMiniBox(
                            modifier = Modifier.weight(1f),
                            value = "${managedEvents.count { it.isPaused }}",
                            label = "暫停中提醒"
                        )
                    }

                    HorizontalDivider(color = tokens.gridLineColor)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "新增例行事務時預設不在月曆格佔空間",
                            color = tokens.primaryTextColor,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = routineDefaultHideFromMonth,
                            onCheckedChange = onToggleDefaultRoutineHide
                        )
                    }

                    OutlinedButton(
                        onClick = onCleanupExpiredItems,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("一鍵清理過期單次提醒與已完成舊項目 (避免長期累積)")
                    }
                }
            }
        }

        // 篩選標籤列
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tabFilter == 0,
                    onClick = { tabFilter = 0 },
                    label = { Text("全部例行與隱藏提醒 (${managedEvents.size + managedTasks.size})") }
                )
                FilterChip(
                    selected = tabFilter == 1,
                    onClick = { tabFilter = 1 },
                    label = { Text("僅月曆隱藏項") }
                )
                FilterChip(
                    selected = tabFilter == 2,
                    onClick = { tabFilter = 2 },
                    label = { Text("外部帳號隱藏規則 (${externalOverrides.size})") }
                )
            }
        }

        if (tabFilter == 2) {
            if (externalOverrides.isEmpty()) {
                item {
                    Text(
                        text = "目前尚無外部帳號隱藏規則。您可以在月曆點擊任何 Google 帳號同步進來的重複活動，選擇「隱藏此系列於月曆格」即可單獨收納至此！",
                        color = tokens.secondaryTextColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(externalOverrides, key = { it.externalKey }) { ov ->
                    Surface(
                        color = tokens.surfaceCardColor,
                        shape = RoundedCornerShape(tokens.cardCorner),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ov.externalKey.substringAfterLast(":"),
                                    color = tokens.primaryTextColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "外部同步系列 · 月曆顯示：${if (ov.showInMonthGrid) "開啟" else "已隱藏"}",
                                    color = tokens.secondaryTextColor,
                                    fontSize = 12.sp
                                )
                            }
                            OutlinedButton(onClick = { onRestoreExternalOverride(ov.externalKey) }) {
                                Text("恢復月曆顯示", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // 例行活動與隱藏提醒卡片列表
            items(managedEvents, key = { "ev_${it.id}" }) { ev ->
                val title = PrivacyCryptoManager.decryptIfNeeded(ev.title)
                val doneToday = checkInSet.contains("EVENT:${ev.id}:$todayEpoch")
                val startT = Instant.ofEpochMilli(ev.startEpochMillis).atZone(zone).toLocalTime().format(HM_FMT)
                val recName = runCatching { RecurrenceType.valueOf(ev.recurrenceType).label }.getOrDefault("單次")

                Surface(
                    color = tokens.surfaceCardColor,
                    shape = RoundedCornerShape(tokens.cardCorner),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (doneToday) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "今日打卡",
                                tint = if (doneToday) Color(0xFF00C853) else tokens.accentColor,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { onToggleTodayCheckIn("EVENT", ev.id, doneToday) }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        color = if (ev.isPaused) tokens.secondaryTextColor else tokens.primaryTextColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (doneToday) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("✓ 今日已完成", color = Color(0xFF00C853), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = "$startT · $recName" +
                                        (if (ev.enableMultiCall) " · 連段呼叫 ${ev.multiCallCount} 次" else "") +
                                        (if (ev.isPaused) " · [已暫停]" else ""),
                                    color = tokens.secondaryTextColor,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { onEditEvent(ev) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "編輯", tint = tokens.secondaryTextColor)
                            }
                            IconButton(onClick = { onDeleteEvent(ev) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "刪除", tint = tokens.secondaryTextColor)
                            }
                        }

                        // 快速控制按鈕列：月曆顯示切換 + 暫停/啟用開關
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = ev.showInMonthGrid,
                                onClick = { onToggleEventMonthShow(ev) },
                                leadingIcon = {
                                    Icon(
                                        if (ev.showInMonthGrid) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        if (ev.showInMonthGrid) "月曆格：顯示中" else "月曆格：已隱藏省空間",
                                        fontSize = 11.sp
                                    )
                                }
                            )

                            FilterChip(
                                selected = !ev.isPaused,
                                onClick = { onToggleEventPause(ev) },
                                leadingIcon = {
                                    Icon(
                                        if (ev.isPaused) Icons.Filled.PlayCircle else Icons.Filled.PauseCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(if (ev.isPaused) "恢復提醒" else "提醒運作中", fontSize = 11.sp)
                                }
                            )
                        }
                    }
                }
            }

            // 被設為隱藏於月曆或例行的 Tasks
            items(managedTasks, key = { "tk_${it.id}" }) { tk ->
                val title = PrivacyCryptoManager.decryptIfNeeded(tk.title)
                Surface(
                    color = tokens.surfaceCardColor,
                    shape = RoundedCornerShape(tokens.cardCorner),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "待辦：$title", color = tokens.primaryTextColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (tk.showInMonthGrid) "顯示於月曆格" else "已隱藏於月曆格 (僅在清單與提醒運作)",
                                color = tokens.secondaryTextColor,
                                fontSize = 12.sp
                            )
                        }
                        FilterChip(
                            selected = tk.showInMonthGrid,
                            onClick = { onToggleTaskMonthShow(tk) },
                            label = { Text(if (tk.showInMonthGrid) "月曆顯示" else "月曆隱藏", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(84.dp)) }
    }
}

@Composable
private fun StatMiniBox(modifier: Modifier, value: String, label: String) {
    val tokens = LocalBrandTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.elevatedSurfaceColor)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Text(text = value, color = tokens.accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = tokens.secondaryTextColor, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * 3. 雙指紋鎖「隱私日曆」獨立加密保險箱介面
 */
@Composable
fun PrivacyVaultView(
    isUnlocked: Boolean,
    requireDualFingerprint: Boolean,
    hasPinConfigured: Boolean,
    privacyItems: List<UnifiedCalendarItem>,
    onTriggerDualFingerprintUnlock: () -> Unit,
    onLockImmediately: () -> Unit,
    onAddPrivacyEvent: () -> Unit,
    onSetupOrVerifyPin: () -> Unit,
    onToggleRequireDualFingerprint: (Boolean) -> Unit,
    onSelectItem: (UnifiedCalendarItem) -> Unit
) {
    val tokens = LocalBrandTokens.current

    if (!isUnlocked) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Surface(
                color = tokens.surfaceCardColor,
                shape = RoundedCornerShape(tokens.cardCorner),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .background(tokens.accentColor.copy(alpha = 0.16f), CircleShape)
                                .border(1.5.dp, tokens.accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = "第一道指紋", tint = tokens.accentColor, modifier = Modifier.size(34.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .background(tokens.accentColor.copy(alpha = 0.16f), CircleShape)
                                .border(1.5.dp, tokens.accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = "第二道指紋", tint = tokens.accentColor, modifier = Modifier.size(34.dp))
                        }
                    }

                    Text(
                        text = "雙指紋鎖隱私日曆",
                        color = tokens.primaryTextColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "隱私行程採 Android Keystore AES-256 加密保護，未解鎖時完全不會出現在一般月曆、搜尋結果或桌面小工具中。",
                        color = tokens.secondaryTextColor,
                        fontSize = 13.sp
                    )

                    Button(
                        onClick = onTriggerDualFingerprintUnlock,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (requireDualFingerprint) "啟動雙指紋鎖連續驗證 (1/2 → 2/2)" else "指紋驗證解鎖")
                    }

                    OutlinedButton(
                        onClick = onSetupOrVerifyPin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (hasPinConfigured) "使用雙重隱私 PIN 碼驗證" else "設定備援隱私 PIN 碼")
                    }
                }
            }
        }
        return
    }

    // 已通過雙指紋解鎖狀態
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            color = tokens.accentColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(tokens.cardCorner),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, tokens.accentColor, RoundedCornerShape(tokens.cardCorner))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, tint = tokens.accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("隱私日曆已解鎖", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("切回背景或點擊右側上鎖後將立即重新隱藏所有密件", color = tokens.secondaryTextColor, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = onLockImmediately,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即上鎖")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("強制連續雙指紋驗證 (兩道指紋鎖)", color = tokens.primaryTextColor, fontSize = 13.sp)
                    Switch(checked = requireDualFingerprint, onCheckedChange = onToggleRequireDualFingerprint)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddPrivacyEvent, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新增隱私行程")
                    }
                    OutlinedButton(onClick = onSetupOrVerifyPin, modifier = Modifier.weight(1f)) {
                        Text(if (hasPinConfigured) "變更隱私 PIN 碼" else "設定隱私 PIN 碼")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (privacyItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("目前尚無隱私行程，點擊上方「新增隱私行程」建立加密紀錄", color = tokens.secondaryTextColor, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(privacyItems, key = { it.uniqueKey }) { item ->
                    Surface(
                        color = tokens.surfaceCardColor,
                        shape = RoundedCornerShape(tokens.cardCorner),
                        modifier = Modifier.fillMaxWidth().clickable { onSelectItem(item) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = tokens.accentColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = tokens.primaryTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${item.date} · 加密隱私密件", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
