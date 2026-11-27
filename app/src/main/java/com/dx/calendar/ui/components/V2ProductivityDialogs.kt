package com.dx.calendar.ui.components

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.local.EventTemplateItem
import com.dx.calendar.data.model.RecurrenceCodec
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.util.NaturalLanguageEventParser
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val NLP_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * #1 + #13 離線自然語言與語音一句話快速新增（0 費用、免 LLM API、即時預覽解析結果）
 */
@Composable
fun QuickNaturalLanguageDialog(
    today: LocalDate,
    autoLaunchVoice: Boolean = false,
    onDismiss: () -> Unit,
    onLaunchVoice: ((String) -> Unit) -> Unit,
    onConfirmCreate: (String) -> Unit
) {
    val tokens = LocalBrandTokens.current
    var inputText by remember { mutableStateOf("") }
    val preview = remember(inputText, today) {
        if (inputText.isBlank()) null else NaturalLanguageEventParser.parse(inputText, today)
    }

    LaunchedEffect(autoLaunchVoice) {
        if (autoLaunchVoice) {
            onLaunchVoice { spoken ->
                inputText = spoken
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎙️ 一句話／語音快速建行程", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "100% 本機離線解析（零費用），支援自然時間、重複與提醒，例如：\n「明天下午3點半開會 提前15分」\n「農曆初一十五拜拜」\n「下週二 14:00-15:30 牙醫」",
                    color = tokens.secondaryTextColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("輸入或點擊右側麥克風說出行程...") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onLaunchVoice { spoken ->
                                    inputText = spoken
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "語音輸入",
                                tint = tokens.accentColor
                            )
                        }
                    },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        onLaunchVoice { spoken ->
                            inputText = spoken
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = tokens.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("點擊說出行程（系統語音輸入）", color = tokens.accentColor, fontWeight = FontWeight.Bold)
                }

                // 即時本機解析結果預覽卡
                AnimatedVisibility(
                    visible = preview != null,
                    enter = fadeIn() + scaleIn(spring(0.72f, 440f), 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f)
                ) {
                    preview?.let { p ->
                        Surface(
                            color = tokens.elevatedSurfaceColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (p.isTaskMode) "✅ 待辦：${p.title}" else "📌 行程：${p.title}",
                                    color = tokens.primaryTextColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val timeDesc = if (p.isAllDay) "全天" else "${p.startTime.format(NLP_TIME_FMT)} - ${p.endTime.format(NLP_TIME_FMT)}"
                                Text(
                                    text = "📅 ${p.date} · $timeDesc",
                                    color = tokens.accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val recText = RecurrenceCodec.formatLabel(p.recurrenceType, p.recurrenceIntervalWeeks)
                                Text(
                                    text = "🔁 $recText · ⏰ 提醒：${p.reminderMinutes.sorted().joinToString("/") { com.dx.calendar.util.ReminderTimeFormatter.formatCompactChipLabel(it) }}",
                                    color = tokens.secondaryTextColor,
                                    fontSize = 11.sp
                                )
                                if (p.isCountdown) {
                                    Text("⏳ 自動標記為倒數日／重要紀念", color = tokens.accentColor, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                onClick = {
                    onConfirmCreate(inputText.trim())
                    onDismiss()
                }
            ) {
                Text("一鍵建立")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * #4 輪班與常用範本連點排班工具列（Stamp Mode）
 */
@Composable
fun ShiftTemplateStampBar(
    templates: List<EventTemplateItem>,
    activeTemplate: EventTemplateItem?,
    onSelectTemplate: (EventTemplateItem?) -> Unit,
    onAddTemplateClick: () -> Unit,
    onCloseBar: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    Surface(
        color = tokens.surfaceCardColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .border(1.dp, tokens.accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (activeTemplate != null) {
                        "🏷️ 連點排班中：點擊任意日期直接排入「${activeTemplate.name}」"
                    } else {
                        "🏷️ 輪班／範本快排：先選一個範本再點日期"
                    },
                    color = tokens.accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCloseBar, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "結束排班", tint = tokens.secondaryTextColor, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                templates.forEach { tpl ->
                    val selected = activeTemplate?.id == tpl.id
                    val dotColor = Color(tpl.colorArgb)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) dotColor.copy(alpha = 0.28f) else tokens.elevatedSurfaceColor)
                            .border(
                                width = if (selected) 1.5.dp else 0.5.dp,
                                color = if (selected) dotColor else tokens.gridLineColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectTemplate(if (selected) null else tpl) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tpl.name,
                                color = tokens.primaryTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.accentColor.copy(alpha = 0.14f))
                        .clickable { onAddTemplateClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("新增範本", color = tokens.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NewTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (EventTemplateItem) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalBrandTokens.current
    var name by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(8) }
    var startMinute by remember { mutableIntStateOf(0) }
    var durationHours by remember { mutableIntStateOf(8) }
    val palette = listOf(0xFF2E7D32.toInt(), 0xFF1976D2.toInt(), 0xFF6A1B9A.toInt(), 0xFFC8962E.toInt(), 0xFFE65100.toInt(), 0xFFC62828.toInt())
    var pickedColor by remember { mutableIntStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增輪班／常用範本", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("範本名稱（如：白班、中班、家教）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("開始時間：%02d:%02d".format(startHour, startMinute), color = tokens.primaryTextColor, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                TimePickerDialog(context, { _, h, m ->
                                    startHour = h; startMinute = m
                                }, startHour, startMinute, true).show()
                            }
                        }
                    ) { Text("設定時間") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("時長：", color = tokens.secondaryTextColor, fontSize = 13.sp)
                    listOf(1, 2, 4, 8).forEach { hrs ->
                        FilterChip(
                            selected = durationHours == hrs,
                            onClick = { durationHours = hrs },
                            label = { Text("${hrs}h", fontSize = 12.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (pickedColor == argb) 2.5.dp else 0.dp,
                                    color = if (pickedColor == argb) tokens.primaryTextColor else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { pickedColor = argb }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                onClick = {
                    onSave(
                        EventTemplateItem(
                            name = name.trim(),
                            startHour = startHour,
                            startMinute = startMinute,
                            durationMinutes = durationHours * 60,
                            colorArgb = pickedColor
                        )
                    )
                    onDismiss()
                }
            ) { Text("儲存範本") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * #8 番茄鐘專注計時器彈窗（支援 25/15/45 分專注與 5 分休息，完成可一鍵勾選待辦完成）
 */
@Composable
fun PomodoroFocusDialog(
    taskTitle: String?,
    onDismiss: () -> Unit,
    onCompleteTask: (() -> Unit)? = null
) {
    val tokens = LocalBrandTokens.current
    var totalSeconds by remember { mutableIntStateOf(25 * 60) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running, remainingSeconds) {
        if (running && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        } else if (running && remainingSeconds == 0) {
            running = false
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
        label = "pomodoroProgress"
    )
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🍅 番茄鐘專注計時", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!taskTitle.isNullOrBlank()) {
                    Text(
                        text = "專注目標：$taskTitle",
                        color = tokens.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(164.dp)) {
                    val accent = tokens.accentColor
                    val track = tokens.elevatedSurfaceColor
                    Canvas(modifier = Modifier.size(152.dp)) {
                        drawArc(
                            color = track,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "%02d:%02d".format(mins, secs),
                        color = tokens.primaryTextColor,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25 to "25分", 15 to "15分", 45 to "45分", 5 to "休5分").forEach { (m, label) ->
                        FilterChip(
                            selected = totalSeconds == m * 60,
                            onClick = {
                                running = false
                                totalSeconds = m * 60
                                remainingSeconds = m * 60
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { running = !running },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                    ) {
                        Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (running) "暫停" else "開始專注")
                    }
                    OutlinedButton(
                        onClick = {
                            running = false
                            remainingSeconds = totalSeconds
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重設")
                    }
                }

                if (onCompleteTask != null) {
                    TextButton(
                        onClick = {
                            onCompleteTask()
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("完成並勾選此待辦", color = tokens.accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}

/**
 * 📖 DX日曆全功能操作指南與一鍵直達面板（審視全部 23 項功能入口與快捷啟動）
 */
@Composable
fun FeatureGuideDialog(
    onDismiss: () -> Unit,
    onOpenVoiceQuickAdd: () -> Unit,
    onOpenShiftStamp: () -> Unit,
    onOpenPomodoro: () -> Unit,
    womensHealthFeatureEnabled: Boolean = false,
    onToggleWomensHealthFilter: () -> Unit = {},
    onImportContactBirthdays: () -> Unit,
    onOpenWeekTimeline: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val scroll = androidx.compose.foundation.rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📖 DX日曆功能指南與快捷按鈕", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(scroll)
            ) {
                Text(
                    text = "以下列出全部主要功能按鈕位置，點擊按鈕即可直接體驗：",
                    color = tokens.secondaryTextColor,
                    fontSize = 12.sp
                )
                GuideActionRow("🎙️ 離線語音／一句話快記", "主畫面底部 + 旁麥克風、或編輯頁標題欄右側麥克風", "立即說", tokens.accentColor) {
                    onDismiss(); onOpenVoiceQuickAdd()
                }
                GuideActionRow(
                    title = "🌸 女性健康追蹤與臨時檢視",
                    desc = if (womensHealthFeatureEnabled) {
                        "支援生理期/排卵期/懷孕/產檢/預產期/產後預設追蹤，頂欄「🌸健康」一鍵臨時只看健康"
                    } else {
                        "請先至設定頁「日曆檢視與提醒偏好」開啟女性健康開關，開啟後新增事件即自動出現"
                    },
                    actionLabel = if (womensHealthFeatureEnabled) "僅看健康" else "前往開啟",
                    accent = Color(0xFFEC407A)
                ) {
                    onDismiss(); onToggleWomensHealthFilter()
                }
                GuideActionRow("🏷️ 輪班範本連點排班", "主畫面「排班」鈕，點選早/小夜/大夜班後連點日期蓋章", "開啟排班", tokens.accentColor) {
                    onDismiss(); onOpenShiftStamp()
                }
                GuideActionRow("🍅 番茄鐘專注計時", "主畫面「番茄」鈕、待辦頁「🍅專注」或行程詳情頁", "開番茄鐘", tokens.accentColor) {
                    onDismiss(); onOpenPomodoro()
                }
                GuideActionRow("🎂 聯絡人生日自動算歲數", "一鍵讀取手機聯絡人生日並自動計算倒數與今年幾歲", "匯入生日", tokens.accentColor) {
                    onDismiss(); onImportContactBirthdays()
                }
                GuideActionRow("🌐 雙時區 24h 時間軸 ＋ 拖曳", "切到「週 / 日」點左上「🌐雙時區」；上下拖曳事件調 15 分刻度", "前往週視圖", tokens.accentColor) {
                    onDismiss(); onOpenWeekTimeline()
                }
                GuideActionRow("☁️ 雲端硬碟備份／雙指紋隱私／主題", "設定頁含自訂裝置名稱備份、1-31 動態圖示與隱私眼睛", "前往設定", tokens.accentColor) {
                    onDismiss(); onOpenSettings()
                }
                Surface(color = tokens.elevatedSurfaceColor, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("💡 手勢與編輯頁內建技巧", color = tokens.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("• 月檢視：上下滑壓縮月曆；長按行程改期；再點已選日開 24h 浮動卡。", color = tokens.primaryTextColor, fontSize = 11.sp)
                        Text("• 編輯頁：含「＋自訂幾天幾時幾分前多提示」、女性健康追蹤範本、農曆重複、圖片附件與「存為範本」。", color = tokens.primaryTextColor, fontSize = 11.sp)
                        Text("• 詳情頁：地點附「🗺️ 導航」直開 Google Maps，底部附「💬 共用協作留言板」。", color = tokens.primaryTextColor, fontSize = 11.sp)
                        Text("• 桌面小工具：提供月曆(now+7天天氣)、時間表(now)、下一行程(4×1)、倒數日(2×2)與單週天氣(4×2)。", color = tokens.primaryTextColor, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
private fun GuideActionRow(
    title: String,
    desc: String,
    actionLabel: String,
    accent: Color,
    onClick: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = tokens.primaryTextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = tokens.secondaryTextColor, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text(actionLabel, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
