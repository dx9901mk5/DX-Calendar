package com.dx.calendar.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import com.dx.calendar.data.weather.DailyWeatherInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dx.calendar.data.local.UserSettings
import com.dx.calendar.data.repository.UnifiedCalendarItem
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.util.LunarCalendarEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 1. 年檢視 (仿 vivo 00:05 12個月網格 + 農曆生肖馬年標題)
 */
@Composable
fun YearGridView(
    year: Int,
    today: LocalDate,
    startWeekOnMonday: Boolean,
    columns: Int,
    onSelectMonth: (YearMonth) -> Unit
) {
    val tokens = LocalBrandTokens.current
    val midYearLunar = remember(year) { LunarCalendarEngine.getLunarInfo(LocalDate.of(year, 6, 15)) }
    val months = remember(year) { (1..12).map { YearMonth.of(year, it) } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "— ${midYearLunar.ganZhiYear}${midYearLunar.zodiac}年 —",
                color = tokens.accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            contentPadding = PaddingValues(bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(months) { ym ->
                MiniMonthCard(
                    yearMonth = ym,
                    today = today,
                    startWeekOnMonday = startWeekOnMonday,
                    onClick = { onSelectMonth(ym) }
                )
            }
        }
    }
}

@Composable
private fun MiniMonthCard(
    yearMonth: YearMonth,
    today: LocalDate,
    startWeekOnMonday: Boolean,
    onClick: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    val isCurrentMonth = yearMonth.year == today.year && yearMonth.monthValue == today.monthValue
    val weekHeaders = if (startWeekOnMonday) {
        listOf("一", "二", "三", "四", "五", "六", "日")
    } else {
        listOf("日", "一", "二", "三", "四", "五", "六")
    }

    Surface(
        color = tokens.surfaceCardColor.copy(alpha = 0.65f),
        shape = RoundedCornerShape(tokens.cellCorner),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "%02d".format(yearMonth.monthValue),
                color = if (isCurrentMonth) tokens.accentColor else tokens.primaryTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weekHeaders.forEach { h ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = h, color = tokens.secondaryTextColor, fontSize = 9.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            val firstDay = yearMonth.atDay(1)
            val shift = if (startWeekOnMonday) {
                firstDay.dayOfWeek.value - 1
            } else {
                firstDay.dayOfWeek.value % 7
            }
            val totalDays = yearMonth.lengthOfMonth()
            val rows = 6
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - shift + 1
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..totalDays) {
                                val d = yearMonth.atDay(dayNum)
                                val isToday = d == today
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (isToday) tokens.accentColor else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        color = if (isToday) Color.White else tokens.primaryTextColor,
                                        fontSize = 10.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. 月檢視 (融合 vivo 月曆、Google Tasks 圓圈顯示、截圖1自訂背景底圖與格線、左右滑動切月、例行事務過濾)
 */
@Composable
fun MonthGridView(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    itemsByDate: Map<LocalDate, List<UnifiedCalendarItem>>,
    weatherByDate: Map<LocalDate, DailyWeatherInfo> = emptyMap(),
    settings: UserSettings,
    maxEventsPerCell: Int,
    compactSheetProgress: Float = 0f,
    hideLunarForCompactHeight: Boolean = false,
    onSelectDate: (LocalDate) -> Unit,
    onTapAlreadySelectedDate: ((LocalDate) -> Unit)? = null,
    onSwipePrevMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit,
    onVerticalDragDelta: ((Float) -> Unit)? = null,
    onVerticalDragEnd: (() -> Unit)? = null,
    onToggleTaskQuick: (UnifiedCalendarItem) -> Unit
) {
    val tokens = LocalBrandTokens.current
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    var lockedAxis by remember { mutableStateOf(0) } // 0=未定, 1=水平翻月, 2=垂直展開/收合時間表
    var isHorizontalDragging by remember { mutableStateOf(false) }
    val animatedDragX by animateFloatAsState(
        targetValue = if (isHorizontalDragging) (dragAccumX * 0.42f).coerceIn(-180f, 180f) else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 420f),
        label = "samsungMonthDragX"
    )

    val datesGrid = remember(yearMonth, settings.startWeekOnMonday) {
        buildSixWeekGrid(yearMonth, settings.startWeekOnMonday)
    }

    val weekHeaders = if (settings.startWeekOnMonday) {
        listOf("一", "二", "三", "四", "五", "六", "日")
    } else {
        listOf("日", "一", "二", "三", "四", "五", "六")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = animatedDragX }
            .pointerInput(yearMonth, onVerticalDragDelta != null) {
                detectDragGestures(
                    onDragStart = {
                        dragAccumX = 0f
                        dragAccumY = 0f
                        lockedAxis = 0
                        isHorizontalDragging = false
                    },
                    onDragEnd = {
                        if (lockedAxis == 1) {
                            if (dragAccumX > 80f) onSwipePrevMonth()
                            else if (dragAccumX < -80f) onSwipeNextMonth()
                        } else if (lockedAxis == 2) {
                            onVerticalDragEnd?.invoke()
                        }
                        dragAccumX = 0f
                        dragAccumY = 0f
                        lockedAxis = 0
                        isHorizontalDragging = false
                    },
                    onDragCancel = {
                        if (lockedAxis == 2) onVerticalDragEnd?.invoke()
                        dragAccumX = 0f
                        dragAccumY = 0f
                        lockedAxis = 0
                        isHorizontalDragging = false
                    },
                    onDrag = { _, dragAmount ->
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y
                        if (lockedAxis == 0) {
                            val absX = kotlin.math.abs(dragAccumX)
                            val absY = kotlin.math.abs(dragAccumY)
                            if (absX > 14f || absY > 14f) {
                                lockedAxis = if (onVerticalDragDelta != null && absY > absX * 1.15f) 2 else 1
                            }
                        }
                        if (lockedAxis == 1) {
                            isHorizontalDragging = true
                        } else if (lockedAxis == 2) {
                            onVerticalDragDelta?.invoke(dragAmount.y)
                        }
                    }
                )
            }
    ) {
        // 截圖 1 致敬：支援自訂月曆背景圖片 + 可調透明度
        if (settings.customWallpaperUri.isNotBlank()) {
            AsyncImage(
                model = Uri.parse(settings.customWallpaperUri),
                contentDescription = "日曆背景圖",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(settings.wallpaperAlpha)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 星期標頭列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surfaceCardColor.copy(alpha = 0.55f))
                    .padding(vertical = 6.dp)
            ) {
                if (settings.showWeekNumber) {
                    Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        Text("週", color = tokens.secondaryTextColor, fontSize = 11.sp)
                    }
                }
                weekHeaders.forEachIndexed { idx, label ->
                    val isSunday = (!settings.startWeekOnMonday && idx == 0) || (settings.startWeekOnMonday && idx == 6)
                    val isSaturday = (!settings.startWeekOnMonday && idx == 6) || (settings.startWeekOnMonday && idx == 5)
                    val headerColor = when {
                        isSunday -> tokens.weekendSundayColor
                        isSaturday -> tokens.weekendSaturdayColor
                        else -> tokens.secondaryTextColor
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tokens.inlineDateAndLunar) "週$label" else label,
                            color = headerColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 6 週網格
            val wf = WeekFields.of(Locale.TAIWAN)
            for (weekIdx in 0 until 6) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val weekDates = datesGrid.subList(weekIdx * 7, weekIdx * 7 + 7)
                    if (settings.showWeekNumber) {
                        val weekNum = weekDates.first().get(wf.weekOfWeekBasedYear())
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight()
                                .background(tokens.surfaceCardColor.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = "$weekNum",
                                color = tokens.secondaryTextColor,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    weekDates.forEach { cellDate ->
                        val isInMonth = cellDate.month == yearMonth.month
                        val isToday = cellDate == today
                        val isSelected = cellDate == selectedDate
                        val lunar = remember(cellDate) { LunarCalendarEngine.getLunarInfo(cellDate) }

                        // 核心過濾：若項目設為不在月曆顯示（如例行吃藥提醒），除非開啟「暫時透視」，否則絕不塞爆月曆格！
                        val visibleCellItems = remember(itemsByDate[cellDate], settings.peekHiddenInMonth) {
                            (itemsByDate[cellDate] ?: emptyList()).filter {
                                it.showInMonthGrid || settings.peekHiddenInMonth
                            }
                        }

                        val (cellSubText, cellSubHighlight) = remember(
                            cellDate,
                            settings.alternateCalendarMode,
                            settings.dateInfoDisplayMode,
                            settings.showPublicHolidays,
                            settings.showTraditionalFestivals,
                            settings.showSolarTerms
                        ) {
                            LunarCalendarEngine.formatCellSubtitle(
                                date = cellDate,
                                info = lunar,
                                altMode = settings.effectiveAlternateCalendar,
                                displayMode = settings.dateInfoDisplayMode,
                                showPublicHolidays = settings.showPublicHolidays,
                                showTraditionalFestivals = settings.showTraditionalFestivals,
                                showSolarTerms = settings.showSolarTerms
                            )
                        }

                        MonthDayCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            date = cellDate,
                            isInMonth = isInMonth,
                            isToday = isToday,
                            isSelected = isSelected,
                            showLunar = settings.showLunar && !hideLunarForCompactHeight && cellSubText.isNotEmpty(),
                            lunarShort = cellSubText,
                            isHoliday = cellSubHighlight,
                            showGridLine = settings.showMonthGridLines,
                            compactSheetProgress = compactSheetProgress.coerceIn(0f, 1f),
                            weather = weatherByDate[cellDate],
                            items = visibleCellItems,
                            maxVisible = maxEventsPerCell,
                            onClick = {
                                if (isSelected && onTapAlreadySelectedDate != null) {
                                    onTapAlreadySelectedDate(cellDate)
                                } else {
                                    onSelectDate(cellDate)
                                }
                            },
                            onToggleTaskQuick = onToggleTaskQuick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    modifier: Modifier,
    date: LocalDate,
    isInMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    showLunar: Boolean,
    lunarShort: String,
    isHoliday: Boolean,
    showGridLine: Boolean,
    compactSheetProgress: Float,
    weather: DailyWeatherInfo? = null,
    items: List<UnifiedCalendarItem>,
    maxVisible: Int,
    onClick: () -> Unit,
    onToggleTaskQuick: (UnifiedCalendarItem) -> Unit
) {
    val tokens = LocalBrandTokens.current
    val contentAlpha = if (isInMonth) 1f else 0.36f
    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.985f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 460f),
        label = "cellSelectBounce"
    )
    val dow = date.dayOfWeek.value
    val dayNumberColor = when {
        isToday -> tokens.accentColor
        dow == 7 -> tokens.weekendSundayColor
        dow == 6 -> tokens.weekendSaturdayColor
        else -> tokens.primaryTextColor
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .then(
                if (showGridLine) Modifier.border(0.5.dp, tokens.gridLineColor)
                else Modifier.padding(1.dp)
            )
            .clip(RoundedCornerShape(if (showGridLine) 2.dp else tokens.cellCorner))
            .background(
                when {
                    isSelected -> tokens.selectedCellFill
                    else -> Color.Transparent
                }
            )
            .then(
                if (isSelected) Modifier.border(
                    1.2.dp,
                    tokens.selectedCellBorder,
                    RoundedCornerShape(if (showGridLine) 2.dp else tokens.cellCorner)
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .alpha(contentAlpha)
    ) {
        // 當日起 7 天預設天氣預報圖示（顯示於日期格右上角）
        if (weather != null) {
            Text(
                text = weather.icon,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 1.dp, top = 0.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = if (tokens.inlineDateAndLunar) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // 日期與農曆標頭
            if (tokens.inlineDateAndLunar) {
                // Google 日曆風格：25 (十七)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .then(
                                if (isToday) Modifier
                                    .background(tokens.accentColor, CircleShape)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = if (isToday) Color.Black else dayNumberColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (showLunar) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "($lunarShort)",
                            color = if (isHoliday) tokens.holidayBadgeColor else tokens.secondaryTextColor,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            } else {
                // vivo / OPPO / 小米 / 三星 風格：上國曆大字、下農曆/節氣
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = dayNumberColor,
                        fontSize = 15.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                    if (showLunar && compactSheetProgress < 0.72f) {
                        Text(
                            text = lunarShort,
                            color = if (isHoliday) tokens.holidayBadgeColor else tokens.secondaryTextColor,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha((1f - compactSheetProgress * 1.4f).coerceIn(0f, 1f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compactSheetProgress > 0.5f) 1.dp else 2.dp))

            // 仿三星日曆手勢壓縮動畫：月曆上滑壓縮時，行程文字膠囊平滑淡出，轉為日期下方的彩色細條指示列！
            if (compactSheetProgress >= 0.45f) {
                val barAlpha = ((compactSheetProgress - 0.38f) / 0.62f).coerceIn(0f, 1f)
                if (items.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                            .alpha(barAlpha)
                    ) {
                        items.take(2).forEach { ev ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(ev.colorArgb))
                            )
                        }
                    }
                }
            } else {
                // 一般全高月檢視：顯示活動與 Google Tasks 彩色文字膠囊條
                val chipAlpha = (1f - compactSheetProgress * 2.1f).coerceIn(0f, 1f)
                val shown = items.take(maxVisible)
                shown.forEach { item ->
                    val chipColor = Color(item.colorArgb)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(chipAlpha)
                            .padding(vertical = 0.8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (item.isTask) chipColor.copy(alpha = 0.22f)
                                else chipColor.copy(alpha = 0.30f)
                            )
                            .then(
                                if (item.isTask) Modifier.clickable { onToggleTaskQuick(item) }
                                else Modifier
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        if (item.isTask) {
                            Text(
                                text = if (item.isCompleted) "✓" else "○",
                                color = chipColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        } else if (item.isPrivacy) {
                            Text("🔒", fontSize = 7.sp)
                        }
                        Text(
                            text = item.title,
                            color = tokens.primaryTextColor,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }

                val overflow = items.size - shown.size
                if (overflow > 0) {
                    Text(
                        text = "+$overflow",
                        color = tokens.secondaryTextColor,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 3.dp).alpha(chipAlpha)
                    )
                }
            }
        }
    }
}

/**
 * 3. 週檢視與日檢視 (24小時時間軸 + 當前時間紅線 + 全天/Tasks置頂區，仿 vivo 00:08 & 00:18)
 */
@Composable
fun WeekOrDayTimelineView(
    isSingleDay: Boolean,
    selectedDate: LocalDate,
    today: LocalDate,
    startWeekOnMonday: Boolean,
    itemsByDate: Map<LocalDate, List<UnifiedCalendarItem>>,
    onSelectDate: (LocalDate) -> Unit,
    onSelectItem: (UnifiedCalendarItem) -> Unit
) {
    val tokens = LocalBrandTokens.current
    val zone = ZoneId.systemDefault()
    val daysToShow = remember(isSingleDay, selectedDate, startWeekOnMonday) {
        if (isSingleDay) {
            listOf(selectedDate)
        } else {
            val shift = if (startWeekOnMonday) {
                selectedDate.dayOfWeek.value - 1
            } else {
                selectedDate.dayOfWeek.value % 7
            }
            val startOfWeek = selectedDate.minusDays(shift.toLong())
            (0 until 7).map { startOfWeek.plusDays(it.toLong()) }
        }
    }

    // 週/日檢視頂部也總是顯示 7 天快速切換條（仿 vivo 00:18 日檢視頂部仍有一排 1~7 日期列！）
    val headerWeekDays = remember(selectedDate, startWeekOnMonday) {
        val shift = if (startWeekOnMonday) {
            selectedDate.dayOfWeek.value - 1
        } else {
            selectedDate.dayOfWeek.value % 7
        }
        val startOfWeek = selectedDate.minusDays(shift.toLong())
        (0 until 7).map { startOfWeek.plusDays(it.toLong()) }
    }

    val hourHeight: Dp = 54.dp
    val scrollState = rememberScrollState(initial = 420) // 預設捲動至早上 7~8 點附近

    Column(modifier = Modifier.fillMaxSize()) {
        // 頂部 7 日農曆切換條
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surfaceCardColor.copy(alpha = 0.7f))
                .padding(start = 42.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            val dowLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            headerWeekDays.forEach { d ->
                val lunar = remember(d) { LunarCalendarEngine.getLunarInfo(d) }
                val isSel = d == selectedDate
                val isTod = d == today
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) tokens.selectedCellFill else Color.Transparent)
                        .clickable { onSelectDate(d) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = dowLabels[d.dayOfWeek.value - 1],
                        color = tokens.secondaryTextColor,
                        fontSize = 11.sp
                    )
                    Text(
                        text = d.dayOfMonth.toString(),
                        color = if (isTod) tokens.accentColor else tokens.primaryTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lunar.shortDisplay,
                        color = if (lunar.isTaiwanHoliday) tokens.holidayBadgeColor else tokens.secondaryTextColor,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // 全天活動與無特定時刻 Tasks 橫列
        val hasAnyAllDay = daysToShow.any { d -> (itemsByDate[d] ?: emptyList()).any { it.isAllDay } }
        if (hasAnyAllDay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.elevatedSurfaceColor.copy(alpha = 0.45f))
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全天",
                    color = tokens.secondaryTextColor,
                    fontSize = 10.sp,
                    modifier = Modifier.width(38.dp)
                )
                daysToShow.forEach { d ->
                    val allDays = (itemsByDate[d] ?: emptyList()).filter { it.isAllDay }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        allDays.take(3).forEach { ev ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(ev.colorArgb).copy(alpha = 0.35f))
                                    .clickable { onSelectItem(ev) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = (if (ev.isTask) "○ " else "") + ev.title,
                                    color = tokens.primaryTextColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 24 小時可捲動時間軸
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 84.dp)
        ) {
            val totalHeight = hourHeight * 24
            Row(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
                // 左側小時刻度 (00:00 ~ 23:00)
                Column(modifier = Modifier.width(42.dp).fillMaxHeight()) {
                    for (h in 0..23) {
                        Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                            Text(
                                text = "%02d:00".format(h),
                                color = tokens.secondaryTextColor,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }

                // 各日時間軸欄位
                daysToShow.forEach { d ->
                    val timedItems = (itemsByDate[d] ?: emptyList()).filter { !it.isAllDay }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.4.dp, tokens.gridLineColor)
                    ) {
                        // 橫向小時輔助線
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stepPx = size.height / 24f
                            for (h in 1..23) {
                                drawLine(
                                    color = tokens.gridLineColor,
                                    start = Offset(0f, h * stepPx),
                                    end = Offset(size.width, h * stepPx),
                                    strokeWidth = 1f
                                )
                            }
                        }

                        // 時間區塊卡片
                        timedItems.forEach { ev ->
                            val startLocal = Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalTime()
                            val endLocal = Instant.ofEpochMilli(ev.endMillis).atZone(zone).toLocalTime()
                            val startMin = startLocal.hour * 60 + startLocal.minute
                            val endMinRaw = endLocal.hour * 60 + endLocal.minute
                            val durationMin = (if (endMinRaw > startMin) endMinRaw - startMin else 45).coerceAtLeast(30)
                            val topOffset = hourHeight * (startMin / 60f)
                            val blockHeight = hourHeight * (durationMin / 60f)
                            val baseColor = Color(ev.colorArgb)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp)
                                    .offset(y = topOffset)
                                    .height(blockHeight)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(baseColor.copy(alpha = 0.28f))
                                    .border(1.dp, baseColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                    .clickable { onSelectItem(ev) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Column {
                                    Text(
                                        text = (if (ev.isTask) "○ " else "") + ev.title,
                                        color = tokens.primaryTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${startLocal.format(TIME_FMT)}-${endLocal.format(TIME_FMT)}",
                                        color = tokens.secondaryTextColor,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 當前時間紅線指示器 (仿 vivo 00:18 `01:56` 紅線)
            val nowTime = LocalTime.now()
            val nowMinutes = nowTime.hour * 60 + nowTime.minute
            val nowOffset = hourHeight * (nowMinutes / 60f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = nowOffset - 6.dp)
            ) {
                Text(
                    text = nowTime.format(TIME_FMT),
                    color = tokens.accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(38.dp).padding(start = 2.dp)
                )
                Box(modifier = Modifier.size(6.dp).background(tokens.accentColor, CircleShape))
                Box(modifier = Modifier.fillMaxWidth().height(1.2.dp).background(tokens.accentColor))
            }
        }
    }
}

/**
 * 4. 時間表 / 日程清單檢視 (仿 vivo 00:48~00:59 依日期與農曆分組卡片)
 */
@Composable
fun AgendaScheduleListView(
    items: List<UnifiedCalendarItem>,
    selectedItem: UnifiedCalendarItem?,
    onSelectItem: (UnifiedCalendarItem) -> Unit,
    onToggleTaskComplete: (UnifiedCalendarItem) -> Unit
) {
    val tokens = LocalBrandTokens.current
    val zone = ZoneId.systemDefault()
    val grouped = remember(items) {
        items.filter { it.showInAgendaAndWidget }.groupBy { it.date }.toSortedMap()
    }
    val dowNames = arrayOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")

    if (grouped.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("近期尚無日程安排，點擊右下角 + 建立新活動", color = tokens.secondaryTextColor, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        grouped.forEach { (date, dayItems) ->
            item(key = "hdr_$date") {
                val lunar = LunarCalendarEngine.getLunarInfo(date)
                val dow = dowNames[date.dayOfWeek.value - 1]
                Text(
                    text = "%02d/%02d  %s  %s".format(date.monthValue, date.dayOfMonth, lunar.fullLunarText, dow),
                    color = if (date == LocalDate.now()) tokens.accentColor else tokens.secondaryTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }

            itemsIndexed(dayItems, key = { idx, item -> "${item.uniqueKey}_$idx" }) { _, item ->
                val isSelected = selectedItem?.uniqueKey == item.uniqueKey
                Surface(
                    color = if (isSelected) tokens.selectedCellFill else tokens.surfaceCardColor,
                    shape = RoundedCornerShape(tokens.cardCorner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(1.dp, tokens.accentColor, RoundedCornerShape(tokens.cardCorner))
                            else Modifier
                        )
                        .clickable { onSelectItem(item) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (item.isTask) {
                            Icon(
                                imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "完成待辦",
                                tint = Color(item.colorArgb),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { onToggleTaskComplete(item) }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(item.colorArgb), CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.isPrivacy) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = tokens.accentColor,
                                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                    )
                                }
                                Text(
                                    text = item.title,
                                    color = tokens.primaryTextColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(3.dp))
                            val timeStr = if (item.isAllDay) {
                                "全天"
                            } else {
                                val s = Instant.ofEpochMilli(item.startMillis).atZone(zone).toLocalTime().format(TIME_FMT)
                                val e = Instant.ofEpochMilli(item.endMillis).atZone(zone).toLocalTime().format(TIME_FMT)
                                if (item.isTask) s else "$s - $e"
                            }
                            val subText = buildString {
                                append(timeStr)
                                if (item.location.isNotBlank()) append(" · ${item.location}")
                                if (item.isRoutine) append(" · 例行事務")
                            }
                            Text(text = subText, color = tokens.secondaryTextColor, fontSize = 12.sp)
                        }

                        if (item.enableMultiCall || item.useAlarmStream) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "連段鬧鐘",
                                tint = tokens.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(84.dp)) }
    }
}

fun buildSixWeekGrid(yearMonth: YearMonth, startMonday: Boolean): List<LocalDate> {
    val firstOfMonth = yearMonth.atDay(1)
    val shift = if (startMonday) {
        firstOfMonth.dayOfWeek.value - 1
    } else {
        firstOfMonth.dayOfWeek.value % 7
    }
    val gridStart = firstOfMonth.minusDays(shift.toLong())
    return (0 until 42).map { gridStart.plusDays(it.toLong()) }
}

/**
 * 5. 仿三星日曆 0:18–0:20「再點日期彈出之 24 小時浮動時間軸／清單卡片」
 * - 圓角毛玻璃風格卡片浮於微暗月曆上方，右上角可一鍵切換「24h 時間軸 ⇄ 清單視圖」
 * - 內建 24 小時平滑捲動時間軸（自動對齊當日第一個行程時刻），底部附「於 M月d日 新增 ＋」膠囊按鈕
 */
@Composable
fun SamsungDayTimelinePopupCard(
    date: LocalDate,
    dayItems: List<UnifiedCalendarItem>,
    onDismiss: () -> Unit,
    onSelectItem: (UnifiedCalendarItem) -> Unit,
    onAddEventForDate: (LocalDate) -> Unit,
    onToggleTaskComplete: (UnifiedCalendarItem) -> Unit
) {
    val tokens = LocalBrandTokens.current
    val zone = ZoneId.systemDefault()
    val lunar = remember(date) { LunarCalendarEngine.getLunarInfo(date) }
    val dowNames = arrayOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
    var showTimelineMode by remember(date) { mutableStateOf(true) }

    val timedItems = remember(dayItems) { dayItems.filter { !it.isAllDay } }
    val allDayItems = remember(dayItems) { dayItems.filter { it.isAllDay } }

    val initialHour = remember(date, timedItems) {
        timedItems.firstOrNull()?.let {
            (Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalTime().hour - 1).coerceIn(0, 20)
        } ?: 8
    }
    val hourHeight = 52.dp
    val scrollState = rememberScrollState(initial = initialHour * 140)

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(
                horizontal = 16.dp,
                vertical = if (/* short split */ false) 12.dp else 24.dp
            )
    ) {
        val isShortWindow = maxHeight < 480.dp
        Surface(
            color = tokens.surfaceCardColor,
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .fillMaxHeight(if (isShortWindow) 0.92f else 0.78f)
                .border(1.dp, tokens.gridLineColor, RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                // 頂部標題：13 週四  +  切換時間軸/清單鈕  +  關閉
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${date.dayOfMonth}",
                            color = tokens.primaryTextColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dowNames[date.dayOfWeek.value - 1],
                            color = tokens.primaryTextColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showTimelineMode = !showTimelineMode }) {
                            Icon(
                                imageVector = if (showTimelineMode) Icons.Filled.FormatListBulleted else Icons.Filled.Schedule,
                                contentDescription = "切換時間軸或清單",
                                tint = tokens.accentColor
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "關閉", tint = tokens.secondaryTextColor)
                        }
                    }
                }

                Text(
                    text = "農 ${lunar.fullLunarText}" + (lunar.festival?.let { " · $it" } ?: lunar.solarTerm?.let { " · $it" } ?: ""),
                    color = tokens.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                // 全天行程橫條
                if (allDayItems.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        allDayItems.forEach { ev ->
                            val c = Color(ev.colorArgb)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.copy(alpha = 0.22f))
                                    .clickable {
                                        onSelectItem(ev)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(modifier = Modifier.width(3.5.dp).height(16.dp).background(c, RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = (if (ev.isTask) "○ " else "") + ev.title,
                                    color = tokens.primaryTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.gridLineColor)

                // 中間主體：24 小時可捲動時間軸 OR 清單模式
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                    if (showTimelineMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            val totalH = hourHeight * 24
                            Row(modifier = Modifier.fillMaxWidth().height(totalH)) {
                                Column(modifier = Modifier.width(30.dp).fillMaxHeight()) {
                                    for (h in 0..23) {
                                        Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                                            Text(
                                                text = "$h",
                                                color = tokens.secondaryTextColor,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val step = size.height / 24f
                                        for (h in 0..23) {
                                            drawLine(
                                                color = tokens.gridLineColor,
                                                start = Offset(0f, h * step),
                                                end = Offset(size.width, h * step),
                                                strokeWidth = 1f
                                            )
                                        }
                                    }
                                    timedItems.forEach { ev ->
                                        val sLocal = Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalTime()
                                        val eLocal = Instant.ofEpochMilli(ev.endMillis).atZone(zone).toLocalTime()
                                        val sMin = sLocal.hour * 60 + sLocal.minute
                                        val eMinRaw = eLocal.hour * 60 + eLocal.minute
                                        val dur = (if (eMinRaw > sMin) eMinRaw - sMin else 60).coerceAtLeast(35)
                                        val topOff = hourHeight * (sMin / 60f)
                                        val blkH = hourHeight * (dur / 60f)
                                        val baseColor = Color(ev.colorArgb)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp)
                                                .offset(y = topOff)
                                                .height(blkH)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(baseColor.copy(alpha = 0.85f))
                                                .clickable {
                                                    onSelectItem(ev)
                                                    onDismiss()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = (if (ev.isTask) "○ " else "") + ev.title,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${sLocal.format(TIME_FMT)} - ${eLocal.format(TIME_FMT)}",
                                                    color = Color.White.copy(alpha = 0.86f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (dayItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("當日無行程安排", color = tokens.secondaryTextColor, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(dayItems, key = { it.uniqueKey }) { ev ->
                                    val timeStr = if (ev.isAllDay) "全天" else {
                                        val s = Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalTime().format(TIME_FMT)
                                        val e = Instant.ofEpochMilli(ev.endMillis).atZone(zone).toLocalTime().format(TIME_FMT)
                                        "$s - $e"
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(tokens.elevatedSurfaceColor.copy(alpha = 0.7f))
                                            .clickable {
                                                onSelectItem(ev)
                                                onDismiss()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        if (ev.isTask) {
                                            Icon(
                                                imageVector = if (ev.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = Color(ev.colorArgb),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable { onToggleTaskComplete(ev) }
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(28.dp)
                                                    .background(Color(ev.colorArgb), RoundedCornerShape(2.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(ev.title, color = tokens.primaryTextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text(timeStr, color = tokens.secondaryTextColor, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部仿三星「於 M月d日 新增 ＋」膠囊按鈕
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(tokens.elevatedSurfaceColor)
                        .clickable {
                            onDismiss()
                            onAddEventForDate(date)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "於 ${date.monthValue}月${date.dayOfMonth}日 新增",
                        color = tokens.secondaryTextColor,
                        fontSize = 14.sp
                    )
                    Icon(Icons.Filled.Add, contentDescription = "新增", tint = tokens.accentColor, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
