package com.dx.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.repository.UnifiedCalendarItem
import com.dx.calendar.data.repository.UnifiedSourceType
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.util.LunarCalendarEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DETAIL_DT_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm")
private val SHORT_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 右欄詳情與當日農曆黃曆面板（完整還原 vivo 摺疊/平板右欄 + 截圖1下方行程新增面板 + 活動檢視面板）
 */
@Composable
fun RightDetailPanel(
    selectedDate: LocalDate,
    today: LocalDate,
    dayItems: List<UnifiedCalendarItem>,
    focusedItem: UnifiedCalendarItem?,
    onClearFocusedItem: () -> Unit,
    onSelectItem: (UnifiedCalendarItem) -> Unit,
    onAddEventForDate: (LocalDate) -> Unit,
    onEditItem: (UnifiedCalendarItem) -> Unit,
    onDeleteItem: (UnifiedCalendarItem) -> Unit,
    onShareItem: (UnifiedCalendarItem) -> Unit,
    onToggleComplete: (UnifiedCalendarItem) -> Unit,
    onHideExternalToRoutine: (UnifiedCalendarItem) -> Unit,
    showCloseDayHeaderButton: Boolean = false,
    onCloseDayPanel: (() -> Unit)? = null
) {
    val tokens = LocalBrandTokens.current

    if (focusedItem != null) {
        FocusedItemDetailCard(
            item = focusedItem,
            onBack = onClearFocusedItem,
            onEdit = { onEditItem(focusedItem) },
            onDelete = { onDeleteItem(focusedItem) },
            onShare = { onShareItem(focusedItem) },
            onHideExternalToRoutine = { onHideExternalToRoutine(focusedItem) }
        )
        return
    }

    val lunar = remember(selectedDate) { LunarCalendarEngine.getLunarInfo(selectedDate) }
    val diffDays = ChronoUnit.DAYS.between(today, selectedDate).toInt()
    val relativeLabel = when {
        diffDays == 0 -> "今天"
        diffDays == 1 -> "明天"
        diffDays == 2 -> "後天"
        diffDays == -1 -> "昨天"
        diffDays == -2 -> "前天"
        diffDays > 0 -> "${diffDays}天後"
        else -> "${-diffDays}天前"
    }

    val zone = ZoneId.systemDefault()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 仿 vivo 大字農曆干支標頭（含單欄模式可選關閉鈕 X，對應截圖 1 標註 2）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedDate.dayOfMonth.toString(),
                color = tokens.primaryTextColor,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relativeLabel,
                    color = if (diffDays == 0) tokens.accentColor else tokens.secondaryTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = lunar.fullLunarText,
                    color = tokens.primaryTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showCloseDayHeaderButton && onCloseDayPanel != null) {
                IconButton(onClick = onCloseDayPanel) {
                    Icon(Icons.Filled.Close, contentDescription = "關閉當日面板", tint = tokens.secondaryTextColor)
                }
            }
        }

        Text(
            text = lunar.fullGanZhiHeader,
            color = tokens.secondaryTextColor,
            fontSize = 13.sp
        )

        // 仿 vivo「今日備忘｜摘要」時段視覺條 (08:00 - 24:00)
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(tokens.cardCorner)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "當日日程摘要 (${dayItems.size} 項)",
                        color = tokens.primaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { onAddEventForDate(selectedDate) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("新增行程", fontSize = 13.sp)
                    }
                }

                // 08:00 | 12:00 | 16:00 | 20:00 | 24:00 迷你時段分佈軸
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("全天", "08:00", "12:00", "16:00", "20:00", "24:00").forEach { t ->
                        Text(text = t, color = tokens.secondaryTextColor, fontSize = 10.sp)
                    }
                }
            }
        }

        // 當日行程與 Google Tasks 清單（仿截圖 1 + vivo 卡片）
        if (dayItems.isEmpty()) {
            Surface(
                color = tokens.surfaceCardColor,
                shape = RoundedCornerShape(tokens.cardCorner),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp)
                ) {
                    Text("當日無行程安排", color = tokens.secondaryTextColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = { onAddEventForDate(selectedDate) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ 新增行程")
                    }
                }
            }
        } else {
            dayItems.forEach { item ->
                Surface(
                    color = tokens.surfaceCardColor,
                    shape = RoundedCornerShape(tokens.cardCorner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectItem(item) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        if (item.isTask) {
                            Icon(
                                imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "打勾",
                                tint = Color(item.colorArgb),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { onToggleComplete(item) }
                            )
                        } else {
                            // 仿截圖 1 的垂直圓角彩色指示條
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(item.colorArgb))
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = tokens.primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                            val sub = if (item.isAllDay) {
                                "全天 · ${item.categoryOrAccountLabel}"
                            } else {
                                val s = Instant.ofEpochMilli(item.startMillis).atZone(zone).toLocalTime().format(SHORT_TIME_FMT)
                                val e = Instant.ofEpochMilli(item.endMillis).atZone(zone).toLocalTime().format(SHORT_TIME_FMT)
                                "$s - $e · ${item.categoryOrAccountLabel}"
                            }
                            Text(text = sub, color = tokens.secondaryTextColor, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 節氣與節日詩詞美學卡片（仿 vivo 01:00 寒衣節 / 01:03 下元節 / 01:35 冬至卡片）
        val highlightTitle = lunar.festival ?: lunar.solarTerm
        if (highlightTitle != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.cardCorner))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                tokens.accentColor.copy(alpha = 0.25f),
                                tokens.surfaceCardColor
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = highlightTitle,
                        color = tokens.accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lunar.solarTermPoem ?: "順應四時節律，珍惜當下美好時光",
                        color = tokens.primaryTextColor,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 傳統黃曆宜忌卡片（仿 vivo 01:09 發現 - 黃曆宜忌）
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(tokens.cardCorner),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("農曆黃曆宜忌", color = tokens.secondaryTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("宜", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = lunar.yiList.joinToString("  "), color = tokens.primaryTextColor, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(0xFFC62828).copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("忌", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = lunar.jiList.joinToString("  "), color = tokens.secondaryTextColor, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
private fun FocusedItemDetailCard(
    item: UnifiedCalendarItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onHideExternalToRoutine: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val zone = ZoneId.systemDefault()
    val startText = Instant.ofEpochMilli(item.startMillis).atZone(zone).format(DETAIL_DT_FMT)
    val endText = Instant.ofEpochMilli(item.endMillis).atZone(zone).format(DETAIL_DT_FMT)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回當日總覽", color = tokens.secondaryTextColor)
            }
            if (item.sourceType != UnifiedSourceType.SYSTEM_EVENT) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("編輯")
                }
            }
        }

        // 標題與時間卡（仿 vivo 00:53 右欄詳情卡）
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(tokens.cardCorner),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = item.title,
                    color = tokens.primaryTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = tokens.gridLineColor)
                if (item.isAllDay) {
                    Text("時間：${item.date} (整日活動)", color = tokens.secondaryTextColor, fontSize = 13.sp)
                } else {
                    Text("開始：$startText", color = tokens.secondaryTextColor, fontSize = 13.sp)
                    Text("結束：$endText", color = tokens.secondaryTextColor, fontSize = 13.sp)
                }
                if (item.recurrenceLabel.isNotBlank()) {
                    Text("重複：${item.recurrenceLabel}", color = tokens.accentColor, fontSize = 13.sp)
                }
                if (item.location.isNotBlank()) {
                    Text("地點：${item.location}", color = tokens.primaryTextColor, fontSize = 13.sp)
                }
                if (item.url.isNotBlank()) {
                    Text("URL：${item.url}", color = tokens.accentColor, fontSize = 13.sp)
                }
                if (item.notes.isNotBlank()) {
                    HorizontalDivider(color = tokens.gridLineColor)
                    Text("備註：${item.notes}", color = tokens.primaryTextColor, fontSize = 14.sp)
                }
            }
        }

        // 類型與多重提醒狀態卡
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(tokens.cardCorner),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("類型 / 帳號", color = tokens.primaryTextColor, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(Color(item.colorArgb), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(item.categoryOrAccountLabel, color = tokens.secondaryTextColor, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = tokens.gridLineColor)
                val remDesc = if (item.reminderMinutes.isEmpty()) {
                    "無提醒"
                } else {
                    item.reminderMinutes.joinToString(", ") { m ->
                        if (m == 0) "活動時間" else "${m}分鐘前"
                    } + if (item.enableMultiCall) " (連段追擊 ${item.multiCallCount} 次)" else ""
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("提醒設定", color = tokens.primaryTextColor, fontSize = 14.sp)
                    Text(remDesc, color = tokens.secondaryTextColor, fontSize = 13.sp)
                }
                HorizontalDivider(color = tokens.gridLineColor)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("月曆格顯示狀態", color = tokens.primaryTextColor, fontSize = 14.sp)
                    Text(
                        text = if (item.showInMonthGrid) "顯示於月曆" else "已收納至例行中心 (不佔月曆)",
                        color = if (item.showInMonthGrid) tokens.secondaryTextColor else tokens.accentColor,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 針對系統同步日曆（如 Google 帳號活動），提供一鍵「不在月曆佔空間，歸入例行中心」！
        if (item.sourceType == UnifiedSourceType.SYSTEM_EVENT) {
            OutlinedButton(
                onClick = onHideExternalToRoutine,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (item.showInMonthGrid) "隱藏此系列於月曆格 (移入例行中心)"
                    else "恢復顯示此系列於月曆格"
                )
            }
        }

        // 分享與刪除
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("分享")
            }
            if (item.sourceType != UnifiedSourceType.SYSTEM_EVENT) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("刪除")
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}
