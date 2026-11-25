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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.local.UserSettings
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.DarkThemeMode
import com.dx.calendar.data.sync.SystemCalendarAccountInfo
import com.dx.calendar.ui.theme.LocalBrandTokens

private val CATEGORY_COLOR_PALETTE = listOf(
    0xFFFF5238.toInt(),
    0xFF00B884.toInt(),
    0xFF0D84FF.toInt(),
    0xFFAB47BC.toInt(),
    0xFFFFB300.toInt(),
    0xFFEC407A.toInt(),
    0xFF26A69A.toInt(),
    0xFF5C6BC0.toInt()
)

/**
 * 設定、五大品牌主題切換、日程與待辦事項管理、ICS/URL 匯入匯出頁面（仿 vivo 00:22~00:36 & 01:12 + 五大品牌主題中心）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndManageScreen(
    settings: UserSettings,
    categories: List<CalendarCategoryEntity>,
    systemCalendars: List<SystemCalendarAccountInfo>,
    hasCalendarPermission: Boolean,
    onClose: () -> Unit,
    onSelectBrand: (BrandStyle) -> Unit,
    onSelectDarkMode: (DarkThemeMode) -> Unit,
    onToggleStartMonday: (Boolean) -> Unit,
    onToggleShowLunar: (Boolean) -> Unit,
    onToggleShowWeekNum: (Boolean) -> Unit,
    onToggleGridLines: (Boolean) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onChangeWallpaperAlpha: (Float) -> Unit,
    onChangeWidgetAppearance: (Boolean, Float) -> Unit,
    onToggleCategoryVisible: (CalendarCategoryEntity) -> Unit,
    onCreateCategory: (name: String, colorArgb: Int) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onToggleSystemCalendarVisible: (Long, Boolean) -> Unit,
    onImportIcsFile: () -> Unit,
    onImportIcsUrl: (String) -> Unit,
    onExportIcsShare: () -> Unit,
    onTestMultiCallAlarmDemo: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    var showNewCatDialog by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }

    Surface(color = tokens.backgroundColor, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 頂部標題列
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surfaceCardColor)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = tokens.primaryTextColor)
                }
                Text(
                    text = "主題風格、日程管理與設定",
                    color = tokens.primaryTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. 五大品牌主題風格選擇器 (vivo 基底 + OPPO, 小米, Google, 三星)
                item {
                    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Palette, contentDescription = null, tint = tokens.accentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("五大品牌主題風格 (以 vivo 為基底)", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            BrandStyle.entries.forEach { style ->
                                val selected = settings.brandStyle == style
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) Color(style.primaryHex).copy(alpha = 0.16f)
                                            else tokens.elevatedSurfaceColor.copy(alpha = 0.6f)
                                        )
                                        .border(
                                            width = if (selected) 1.5.dp else 0.dp,
                                            color = if (selected) Color(style.primaryHex) else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectBrand(style) }
                                        .padding(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color(style.primaryHex), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(style.displayName, color = tokens.primaryTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(style.subtitle, color = tokens.secondaryTextColor, fontSize = 11.sp)
                                    }
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = "已選用", tint = Color(style.primaryHex))
                                    }
                                }
                            }

                            HorizontalDivider(color = tokens.gridLineColor)

                            Text("深淺外觀模式", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DarkThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.darkThemeMode == mode,
                                        onClick = { onSelectDarkMode(mode) },
                                        label = { Text(mode.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 自訂月曆背景圖片（致敬截圖 1 背景圖層）與桌面小工具外觀
                item {
                    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = tokens.accentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("月曆自訂背景圖與桌面小工具風格", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onPickWallpaper, modifier = Modifier.weight(1f)) {
                                    Text("選擇月曆背景圖片")
                                }
                                if (settings.customWallpaperUri.isNotBlank()) {
                                    OutlinedButton(onClick = onClearWallpaper) {
                                        Text("移除背景")
                                    }
                                }
                            }
                            if (settings.customWallpaperUri.isNotBlank()) {
                                Text("背景圖透明度：${(settings.wallpaperAlpha * 100).toInt()}%", color = tokens.secondaryTextColor, fontSize = 12.sp)
                                Slider(
                                    value = settings.wallpaperAlpha,
                                    onValueChange = onChangeWallpaperAlpha,
                                    valueRange = 0.08f..0.65f
                                )
                            }

                            HorizontalDivider(color = tokens.gridLineColor)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("桌面小工具採用深色毛玻璃底 (vivo 風格)", color = tokens.primaryTextColor, fontSize = 14.sp)
                                Switch(
                                    checked = settings.widgetDarkStyle,
                                    onCheckedChange = { onChangeWidgetAppearance(it, settings.widgetOpacity) }
                                )
                            }
                        }
                    }
                }

                // 3. 日曆檢視偏好設定（仿 vivo 設定 & Google 設定）
                item {
                    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("日曆檢視偏好", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                            SettingSwitchRow("一週起始日設為週一 (關閉則為週日)", settings.startWeekOnMonday, onToggleStartMonday)
                            SettingSwitchRow("顯示農曆、二十四節氣與台灣節慶", settings.showLunar, onToggleShowLunar)
                            SettingSwitchRow("顯示全年週次編號", settings.showWeekNumber, onToggleShowWeekNum)
                            SettingSwitchRow("顯示月曆格線", settings.showMonthGridLines, onToggleGridLines)

                            HorizontalDivider(color = tokens.gridLineColor)
                            OutlinedButton(
                                onClick = onTestMultiCallAlarmDemo,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("測試「多重呼叫全螢幕鬧鐘提醒」立即預覽")
                            }
                        }
                    }
                }

                // 4. 日程與待辦事項管理（仿 vivo 00:22 本機時間表類型 + 外部 Google 帳號日曆勾選）
                item {
                    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("本機時間表類型管理", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { showNewCatDialog = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("新增類型")
                                }
                            }

                            categories.filter { !it.isPrivacyCategory }.forEach { cat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleCategoryVisible(cat) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = cat.isVisible,
                                        onCheckedChange = { onToggleCategoryVisible(cat) }
                                    )
                                    Box(modifier = Modifier.size(12.dp).background(Color(cat.colorArgb), CircleShape))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("${cat.name} (${cat.accountName})", color = tokens.primaryTextColor, fontSize = 14.sp)
                                }
                            }

                            HorizontalDivider(color = tokens.gridLineColor)
                            Text("設備 Google / 系統日曆同步管理", color = tokens.primaryTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (!hasCalendarPermission) {
                                Text("授權日曆權限即可載入裝置上所有 Google 日曆與子日曆（學務處、家庭、節慶等）", color = tokens.secondaryTextColor, fontSize = 12.sp)
                                Button(
                                    onClick = onRequestCalendarPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                                ) {
                                    Text("授權讀取與同步系統日曆")
                                }
                            } else if (systemCalendars.isEmpty()) {
                                Text("此裝置目前尚未登入系統 Google 日曆帳號", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            } else {
                                systemCalendars.groupBy { it.accountName }.forEach { (acc, cals) ->
                                    Text(acc, color = tokens.accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                                    cals.forEach { sc ->
                                        val isVis = !settings.hiddenSystemCalIds.contains(sc.id.toString())
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onToggleSystemCalendarVisible(sc.id, !isVis) }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Checkbox(
                                                checked = isVis,
                                                onCheckedChange = { onToggleSystemCalendarVisible(sc.id, it) }
                                            )
                                            Box(modifier = Modifier.size(10.dp).background(Color(sc.colorArgb), CircleShape))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(sc.displayName, color = tokens.primaryTextColor, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 匯入與匯出日程（仿 vivo 00:34 本機 ICS 匯入 / URL 訂閱連結匯入 / 匯出備份）
                item {
                    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("匯入與匯出日程 (ICS / URL)", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onImportIcsFile, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("匯入 ICS 檔案")
                                }
                                OutlinedButton(onClick = { showUrlImportDialog = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URL 連結匯入")
                                }
                            }
                            OutlinedButton(onClick = onExportIcsShare, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("匯出本機日程為標準 .ics 檔案分享")
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }

    if (showNewCatDialog) {
        var catName by remember { mutableStateOf("") }
        var pickedColor by remember { mutableIntStateOf(CATEGORY_COLOR_PALETTE.first()) }
        AlertDialog(
            onDismissRequest = { showNewCatDialog = false },
            title = { Text("新增日程類型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("類型名稱 (如：醫療、健身、家庭)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CATEGORY_COLOR_PALETTE.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(c), CircleShape)
                                    .then(
                                        if (pickedColor == c) Modifier.border(2.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { pickedColor = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (catName.isNotBlank()) {
                            onCreateCategory(catName.trim(), pickedColor)
                            showNewCatDialog = false
                        }
                    }
                ) { Text("新增") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCatDialog = false }) { Text("取消") }
            }
        )
    }

    if (showUrlImportDialog) {
        var urlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlImportDialog = false },
            title = { Text("透過 URL 連結匯入日程") },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("輸入 https:// 或 webcal:// ICS 連結") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onImportIcsUrl(urlInput.trim())
                            showUrlImportDialog = false
                        }
                    }
                ) { Text("匯入") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlImportDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val tokens = LocalBrandTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, color = tokens.primaryTextColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
