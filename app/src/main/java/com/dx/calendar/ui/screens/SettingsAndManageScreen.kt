package com.dx.calendar.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.BuildConfig
import com.dx.calendar.alarm.LiveUpdateIslandStatus
import com.dx.calendar.data.local.UserSettings
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.CalendarCategoryEntity
import com.dx.calendar.data.model.DarkThemeMode
import com.dx.calendar.data.sync.SystemCalendarAccountInfo
import com.dx.calendar.ui.AppUpdateUiState
import com.dx.calendar.ui.UpdatePhase
import com.dx.calendar.ui.theme.LocalBrandTokens
import com.dx.calendar.update.GitHubAutoUpdater

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

private enum class DxSettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    ABOUT_UPDATE("GitHub 與自動更新", "GitHub 專案連結、點擊檢查更新與自動下載安裝", Icons.Filled.SystemUpdate),
    APPEARANCE("外觀主題與底圖", "金白明亮預設、金色灰黑暗色預設與跟隨系統", Icons.Filled.Palette),
    ACCOUNTS_AND_TASKS("行事曆與 Google Tasks", "行事曆顯示開關、本機類型與 Tasks 自動同步", Icons.Filled.AccountCircle),
    ICON_AND_ISLAND("原子島與動態圖示", "日曆通知上原子島、1–31 日期圖示", Icons.Filled.NotificationsActive),
    CALENDAR_PREFS("日曆檢視與提醒偏好", "週一起始、農曆節氣、格線、透視例行與連段測試", Icons.Filled.Tune),
    IMPORT_EXPORT("匯入與匯出備份", "標準 .ics 檔案匯入匯出與 URL 訂閱", Icons.Filled.ImportExport),
    PRIVACY("隱私日曆設定", "預設隱藏入口；於此啟用後才顯示雙指紋隱私日曆", Icons.Filled.Lock)
}

/**
 * DX日曆自有原創設定中心（DX Studio Workspace 風格）：
 * - 平板與摺疊機展開（寬度 >= 600dp）：呈現「左頁功能選擇導覽 + 右頁詳細設定」兩頁並排！
 * - 手機與窄視窗：頂部膠囊分類跳轉 + 乾淨自有卡片排版。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndManageScreen(
    settings: UserSettings,
    updateState: AppUpdateUiState,
    categories: List<CalendarCategoryEntity>,
    systemCalendars: List<SystemCalendarAccountInfo>,
    hasCalendarPermission: Boolean,
    isSyncingTasks: Boolean,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleAutoCheckUpdate: (Boolean) -> Unit,
    onManualCheckUpdate: () -> Unit,
    onStartUpdateDownload: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onSelectBrand: (BrandStyle) -> Unit,
    onSelectDarkMode: (DarkThemeMode) -> Unit,
    onToggleStartMonday: (Boolean) -> Unit,
    onToggleShowLunar: (Boolean) -> Unit,
    onToggleShowWeekNum: (Boolean) -> Unit,
    onToggleGridLines: (Boolean) -> Unit,
    onTogglePeekHiddenInMonth: (Boolean) -> Unit,
    onToggleRoutineMedicationFeature: (Boolean) -> Unit,
    onTogglePrivacyFeature: (Boolean) -> Unit,
    onToggleRequireDualFingerprint: (Boolean) -> Unit,
    onOpenPrivacyVault: () -> Unit,
    onToggleShowCompletedTasks: (Boolean) -> Unit,
    onSyncGoogleTasks: () -> Unit,
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
    onTestMultiCallAlarmDemo: () -> Unit,
    liveUpdateStatus: LiveUpdateIslandStatus,
    onToggleDynamicDayIcon: (Boolean) -> Unit,
    onToggleLiveUpdateIsland: (Boolean) -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onTestLiveUpdateIsland: () -> Unit,
    onCancelLiveUpdateIsland: () -> Unit
) {
    val tokens = LocalBrandTokens.current
    var selectedSection by remember { mutableStateOf(DxSettingsSection.ABOUT_UPDATE) }
    val phoneListState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    var showNewCatDialog by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }

    val taskCategory = remember(categories) { categories.find { it.isTaskCategory } }

    Surface(color = tokens.backgroundColor, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val isNarrowHeader = maxWidth < 380.dp
            val isTwoPane = maxWidth >= 640.dp && maxHeight >= 400.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // DX 原創頂部工作列（帶品牌徽章與版本標記，窄分割畫面自動精簡不擠壓）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.surfaceCardColor)
                        .padding(horizontal = if (isNarrowHeader) 6.dp else 10.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(if (isNarrowHeader) 36.dp else 44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = tokens.primaryTextColor)
                    }
                    if (!isNarrowHeader) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(tokens.accentColor)
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DX 設定工作台",
                            color = tokens.primaryTextColor,
                            fontSize = if (isNarrowHeader) 16.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (!isNarrowHeader) {
                            Text(
                                text = "v${updateState.currentVersion} · 跟隨系統金白／金色灰黑主題",
                                color = tokens.secondaryTextColor,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    if (!BuildConfig.IS_PLAY_BUILD) {
                        if (isNarrowHeader) {
                            IconButton(
                                onClick = { onOpenUrl(GitHubAutoUpdater.GITHUB_REPO_URL) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "GitHub", modifier = Modifier.size(18.dp), tint = tokens.accentColor)
                            }
                        } else {
                            TextButton(
                                onClick = { onOpenUrl(GitHubAutoUpdater.GITHUB_REPO_URL) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = tokens.accentColor)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("GitHub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.accentColor)
                            }
                        }
                        Button(
                            onClick = {
                                selectedSection = DxSettingsSection.ABOUT_UPDATE
                                if (!isTwoPane) {
                                    uiScope.launch { runCatching { phoneListState.animateScrollToItem(0) } }
                                }
                                onManualCheckUpdate()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                            contentPadding = PaddingValues(horizontal = if (isNarrowHeader) 8.dp else 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("檢查更新", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = tokens.gridLineColor)

                // 響應式內容：平板與摺疊機 (>= 640dp 且高度足夠) 兩頁並排；手機與分割畫面採上下垂直單頁捲動
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isTwoPane) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左頁：功能分類選擇清單
                        LazyColumn(
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(0.37f)
                                .fillMaxHeight()
                                .background(tokens.surfaceCardColor.copy(alpha = 0.45f))
                        ) {
                            item {
                                Text(
                                    text = "功能分類",
                                    color = tokens.secondaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            DxSettingsSection.entries.forEach { sec ->
                                item {
                                    val active = selectedSection == sec
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (active) tokens.accentColor.copy(alpha = 0.16f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = if (active) 1.2.dp else 0.dp,
                                                color = if (active) tokens.accentColor.copy(alpha = 0.7f) else Color.Transparent,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable { selectedSection = sec }
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (active) tokens.accentColor
                                                    else tokens.elevatedSurfaceColor
                                                )
                                        ) {
                                            Icon(
                                                imageVector = sec.icon,
                                                contentDescription = null,
                                                tint = if (active) Color.White else tokens.secondaryTextColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sec.title,
                                                color = if (active) tokens.accentColor else tokens.primaryTextColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = sec.subtitle,
                                                color = tokens.secondaryTextColor,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        VerticalDivider(color = tokens.gridLineColor)

                        // 右頁：所選分類的詳細設定頁
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .weight(0.63f)
                                .fillMaxHeight()
                        ) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(selectedSection.icon, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(selectedSection.title, color = tokens.primaryTextColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                        Text(selectedSection.subtitle, color = tokens.secondaryTextColor, fontSize = 12.sp)
                                    }
                                }
                            }
                            item {
                                DxSettingsSectionContent(
                                    section = selectedSection,
                                    settings = settings,
                                    updateState = updateState,
                                    categories = categories,
                                    taskCategory = taskCategory,
                                    systemCalendars = systemCalendars,
                                    hasCalendarPermission = hasCalendarPermission,
                                    isSyncingTasks = isSyncingTasks,
                                    liveUpdateStatus = liveUpdateStatus,
                                    onOpenUrl = onOpenUrl,
                                    onToggleAutoCheckUpdate = onToggleAutoCheckUpdate,
                                    onManualCheckUpdate = onManualCheckUpdate,
                                    onStartUpdateDownload = onStartUpdateDownload,
                                    onCancelUpdateDownload = onCancelUpdateDownload,
                                    onInstallDownloadedApk = onInstallDownloadedApk,
                                    onSelectBrand = onSelectBrand,
                                    onSelectDarkMode = onSelectDarkMode,
                                    onToggleStartMonday = onToggleStartMonday,
                                    onToggleShowLunar = onToggleShowLunar,
                                    onToggleShowWeekNum = onToggleShowWeekNum,
                                    onToggleGridLines = onToggleGridLines,
                                    onTogglePeekHiddenInMonth = onTogglePeekHiddenInMonth,
                                    onToggleRoutineMedicationFeature = onToggleRoutineMedicationFeature,
                                    onTogglePrivacyFeature = onTogglePrivacyFeature,
                                    onToggleRequireDualFingerprint = onToggleRequireDualFingerprint,
                                    onOpenPrivacyVault = onOpenPrivacyVault,
                                    onToggleShowCompletedTasks = onToggleShowCompletedTasks,
                                    onSyncGoogleTasks = onSyncGoogleTasks,
                                    onPickWallpaper = onPickWallpaper,
                                    onClearWallpaper = onClearWallpaper,
                                    onChangeWallpaperAlpha = onChangeWallpaperAlpha,
                                    onChangeWidgetAppearance = onChangeWidgetAppearance,
                                    onToggleCategoryVisible = onToggleCategoryVisible,
                                    onOpenNewCategoryDialog = { showNewCatDialog = true },
                                    onRequestCalendarPermission = onRequestCalendarPermission,
                                    onToggleSystemCalendarVisible = onToggleSystemCalendarVisible,
                                    onImportIcsFile = onImportIcsFile,
                                    onOpenUrlImportDialog = { showUrlImportDialog = true },
                                    onExportIcsShare = onExportIcsShare,
                                    onTestMultiCallAlarmDemo = onTestMultiCallAlarmDemo,
                                    onToggleDynamicDayIcon = onToggleDynamicDayIcon,
                                    onToggleLiveUpdateIsland = onToggleLiveUpdateIsland,
                                    onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
                                    onTestLiveUpdateIsland = onTestLiveUpdateIsland,
                                    onCancelLiveUpdateIsland = onCancelLiveUpdateIsland
                                )
                            }
                            item { Spacer(modifier = Modifier.height(36.dp)) }
                        }
                    }
                } else {
                    // 手機單欄：全設定區塊由上往下垂直單頁捲動排列（無左右橫向分頁切換）
                    LazyColumn(
                        state = phoneListState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        DxSettingsSection.entries.forEach { sec ->
                            item(key = "hdr_${sec.name}") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(sec.icon, contentDescription = null, tint = tokens.accentColor, modifier = Modifier.size(19.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = sec.title,
                                        color = tokens.primaryTextColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            item(key = "body_${sec.name}") {
                                DxSettingsSectionContent(
                                    section = sec,
                                    settings = settings,
                                    updateState = updateState,
                                    categories = categories,
                                    taskCategory = taskCategory,
                                    systemCalendars = systemCalendars,
                                    hasCalendarPermission = hasCalendarPermission,
                                    isSyncingTasks = isSyncingTasks,
                                    liveUpdateStatus = liveUpdateStatus,
                                    onOpenUrl = onOpenUrl,
                                    onToggleAutoCheckUpdate = onToggleAutoCheckUpdate,
                                    onManualCheckUpdate = onManualCheckUpdate,
                                    onStartUpdateDownload = onStartUpdateDownload,
                                    onCancelUpdateDownload = onCancelUpdateDownload,
                                    onInstallDownloadedApk = onInstallDownloadedApk,
                                    onSelectBrand = onSelectBrand,
                                    onSelectDarkMode = onSelectDarkMode,
                                    onToggleStartMonday = onToggleStartMonday,
                                    onToggleShowLunar = onToggleShowLunar,
                                    onToggleShowWeekNum = onToggleShowWeekNum,
                                    onToggleGridLines = onToggleGridLines,
                                    onTogglePeekHiddenInMonth = onTogglePeekHiddenInMonth,
                                    onToggleRoutineMedicationFeature = onToggleRoutineMedicationFeature,
                                    onTogglePrivacyFeature = onTogglePrivacyFeature,
                                    onToggleRequireDualFingerprint = onToggleRequireDualFingerprint,
                                    onOpenPrivacyVault = onOpenPrivacyVault,
                                    onToggleShowCompletedTasks = onToggleShowCompletedTasks,
                                    onSyncGoogleTasks = onSyncGoogleTasks,
                                    onPickWallpaper = onPickWallpaper,
                                    onClearWallpaper = onClearWallpaper,
                                    onChangeWallpaperAlpha = onChangeWallpaperAlpha,
                                    onChangeWidgetAppearance = onChangeWidgetAppearance,
                                    onToggleCategoryVisible = onToggleCategoryVisible,
                                    onOpenNewCategoryDialog = { showNewCatDialog = true },
                                    onRequestCalendarPermission = onRequestCalendarPermission,
                                    onToggleSystemCalendarVisible = onToggleSystemCalendarVisible,
                                    onImportIcsFile = onImportIcsFile,
                                    onOpenUrlImportDialog = { showUrlImportDialog = true },
                                    onExportIcsShare = onExportIcsShare,
                                    onTestMultiCallAlarmDemo = onTestMultiCallAlarmDemo,
                                    onToggleDynamicDayIcon = onToggleDynamicDayIcon,
                                    onToggleLiveUpdateIsland = onToggleLiveUpdateIsland,
                                    onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
                                    onTestLiveUpdateIsland = onTestLiveUpdateIsland,
                                    onCancelLiveUpdateIsland = onCancelLiveUpdateIsland
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(40.dp)) }
                    }
                }
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DxSettingsSectionContent(
    section: DxSettingsSection,
    settings: UserSettings,
    updateState: AppUpdateUiState,
    categories: List<CalendarCategoryEntity>,
    taskCategory: CalendarCategoryEntity?,
    systemCalendars: List<SystemCalendarAccountInfo>,
    hasCalendarPermission: Boolean,
    isSyncingTasks: Boolean,
    liveUpdateStatus: LiveUpdateIslandStatus,
    onOpenUrl: (String) -> Unit,
    onToggleAutoCheckUpdate: (Boolean) -> Unit,
    onManualCheckUpdate: () -> Unit,
    onStartUpdateDownload: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onSelectBrand: (BrandStyle) -> Unit,
    onSelectDarkMode: (DarkThemeMode) -> Unit,
    onToggleStartMonday: (Boolean) -> Unit,
    onToggleShowLunar: (Boolean) -> Unit,
    onToggleShowWeekNum: (Boolean) -> Unit,
    onToggleGridLines: (Boolean) -> Unit,
    onTogglePeekHiddenInMonth: (Boolean) -> Unit,
    onToggleRoutineMedicationFeature: (Boolean) -> Unit,
    onTogglePrivacyFeature: (Boolean) -> Unit,
    onToggleRequireDualFingerprint: (Boolean) -> Unit,
    onOpenPrivacyVault: () -> Unit,
    onToggleShowCompletedTasks: (Boolean) -> Unit,
    onSyncGoogleTasks: () -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onChangeWallpaperAlpha: (Float) -> Unit,
    onChangeWidgetAppearance: (Boolean, Float) -> Unit,
    onToggleCategoryVisible: (CalendarCategoryEntity) -> Unit,
    onOpenNewCategoryDialog: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onToggleSystemCalendarVisible: (Long, Boolean) -> Unit,
    onImportIcsFile: () -> Unit,
    onOpenUrlImportDialog: () -> Unit,
    onExportIcsShare: () -> Unit,
    onTestMultiCallAlarmDemo: () -> Unit,
    onToggleDynamicDayIcon: (Boolean) -> Unit,
    onToggleLiveUpdateIsland: (Boolean) -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onTestLiveUpdateIsland: () -> Unit,
    onCancelLiveUpdateIsland: () -> Unit
) {
    val tokens = LocalBrandTokens.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        when (section) {
            DxSettingsSection.ACCOUNTS_AND_TASKS -> {
                // 專屬 Google Tasks 整合與日曆顯示控制卡
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = Color(0xFFFFAB00))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Tasks 待辦與日曆整合", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = if (settings.googleAccountEmail.isNotBlank()) {
                                "已連結自動同步：每次開啟／切回 App 皆會於背景自動判讀並同步 Google Tasks 至月曆與小工具。"
                            } else {
                                "只需首次點選下方按鈕連結授權一次，之後每次開啟 App 即會自動判讀同步 Google Tasks！"
                            },
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onSyncGoogleTasks,
                            enabled = !isSyncingTasks,
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSyncingTasks) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在同步 Google Tasks...")
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (settings.googleAccountEmail.isNotBlank()) "立即同步 Google Tasks"
                                    else "連結 Google 帳號並啟用 Google Tasks"
                                )
                            }
                        }

                        if (taskCategory != null) {
                            SettingSwitchRow(
                                label = "於日曆月檢視與時間表顯示 Google Tasks",
                                checked = taskCategory.isVisible,
                                onCheckedChange = { onToggleCategoryVisible(taskCategory) }
                            )
                        }
                        SettingSwitchRow(
                            label = "於日曆中顯示已完成的待辦任務",
                            checked = settings.showCompletedTasks,
                            onCheckedChange = onToggleShowCompletedTasks
                        )
                    }
                }

                // 本機行事曆 + 雲端同步行事曆簡潔管理（不顯示繁雜帳號，且已移除內建 vivo 日曆）
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("本機行事曆", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onOpenNewCategoryDialog) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("新增行事曆")
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
                                Text(cat.name, color = tokens.primaryTextColor, fontSize = 14.sp)
                            }
                        }

                        HorizontalDivider(color = tokens.gridLineColor)
                        Text("同步行事曆", color = tokens.primaryTextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (!hasCalendarPermission) {
                            Text("授權日曆權限即可載入裝置上的 Google 行事曆（主要行事曆、家庭、節慶假日等）", color = tokens.secondaryTextColor, fontSize = 12.sp)
                            Button(
                                onClick = onRequestCalendarPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor)
                            ) {
                                Text("授權讀取與同步行事曆")
                            }
                        } else if (systemCalendars.isEmpty()) {
                            Text("目前尚無可顯示的同步行事曆", color = tokens.secondaryTextColor, fontSize = 12.sp)
                        } else {
                            systemCalendars.forEach { sc ->
                                val isVis = !settings.hiddenSystemCalIds.contains(sc.id.toString())
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleSystemCalendarVisible(sc.id, !isVis) }
                                        .padding(vertical = 3.dp)
                                ) {
                                    Checkbox(
                                        checked = isVis,
                                        onCheckedChange = { onToggleSystemCalendarVisible(sc.id, it) }
                                    )
                                    Box(modifier = Modifier.size(11.dp).background(Color(sc.colorArgb), CircleShape))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(sc.displayName, color = tokens.primaryTextColor, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            DxSettingsSection.APPEARANCE -> {
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Palette, contentDescription = null, tint = tokens.accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("主題視覺風格（預設跟隨手機主題移動）", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Image, contentDescription = null, tint = tokens.accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("月曆自訂背景圖與小工具外觀", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        SettingSwitchRow(
                            label = "桌面小工具採用深色底白字模式（關閉為淺色底黑字）",
                            checked = settings.widgetDarkStyle,
                            onCheckedChange = { onChangeWidgetAppearance(it, settings.widgetOpacity) }
                        )
                        val opaquePct = (settings.widgetOpacity * 100).toInt()
                        Text(
                            text = "桌面小工具背景不透明度：$opaquePct%（透明 ${100 - opaquePct}%，亦可直接點擊桌面小工具右上角 ⚙ 單獨調整）",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = settings.widgetOpacity,
                            onValueChange = { onChangeWidgetAppearance(settings.widgetDarkStyle, it) },
                            valueRange = 0.0f..1.0f
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                0f to "0% 全透明",
                                0.25f to "25%",
                                0.50f to "50%",
                                0.75f to "75%",
                                0.85f to "85%",
                                1.0f to "100% 實心"
                            ).forEach { (preset, label) ->
                                FilterChip(
                                    selected = kotlin.math.abs(settings.widgetOpacity - preset) < 0.02f,
                                    onClick = { onChangeWidgetAppearance(settings.widgetDarkStyle, preset) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            DxSettingsSection.ICON_AND_ISLAND -> {
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "動態日期圖示 (1–31) 與原子島通知上島",
                            color = tokens.primaryTextColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        SettingSwitchRow(
                            label = "桌面 App 圖示自動顯示今日日期 (1–31 日)",
                            checked = settings.dynamicDayIconEnabled,
                            onCheckedChange = onToggleDynamicDayIcon
                        )
                        Text(
                            text = "每日 00:00 跨日與開啟 App 時自動切換為當日數字圖示；關閉則恢復預設靜態圖示。",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )

                        HorizontalDivider(color = tokens.gridLineColor)

                        SettingSwitchRow(
                            label = "日程提醒上島 (Android 16 Live Updates / OriginOS 6 原子島)",
                            checked = settings.liveUpdateIslandEnabled,
                            onCheckedChange = onToggleLiveUpdateIsland
                        )

                        val statusText = when {
                            liveUpdateStatus.isAndroid16OrHigher && liveUpdateStatus.canPostPromoted ->
                                "✓ Android 16+ 系統已授權 Live Updates 上島通知（台/陸版 OriginOS 6 通用）"
                            liveUpdateStatus.isAndroid16OrHigher ->
                                "⚠ 尚未開啟系統「即時更新 / 上島通知」開關，請點擊下方按鈕前往開啟"
                            else ->
                                "ℹ 目前系統為 Android ${Build.VERSION.RELEASE}（升級至 Android 16 / OriginOS 6 即自動以原生 Live Updates 上島，目前以相容持續進度通知呈現）"
                        }
                        val statusColor = when {
                            liveUpdateStatus.isAndroid16OrHigher && liveUpdateStatus.canPostPromoted -> Color(0xFF00C853)
                            liveUpdateStatus.isAndroid16OrHigher -> Color(0xFFFFB300)
                            else -> tokens.secondaryTextColor
                        }
                        Text(text = statusText, color = statusColor, fontSize = 12.sp)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onOpenLiveUpdateSettings, modifier = Modifier.weight(1f)) {
                                Text("系統上島權限", fontSize = 12.sp)
                            }
                            Button(
                                onClick = onTestLiveUpdateIsland,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("立即測試上島", fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = onCancelLiveUpdateIsland,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("關閉測試上島通知", fontSize = 12.sp)
                        }
                    }
                }
            }

            DxSettingsSection.CALENDAR_PREFS -> {
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("日曆檢視偏好", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                        SettingSwitchRow("一週起始日設為週一 (關閉則為週日)", settings.startWeekOnMonday, onToggleStartMonday)
                        SettingSwitchRow("顯示農曆、二十四節氣與台灣節慶", settings.showLunar, onToggleShowLunar)
                        SettingSwitchRow("顯示全年週次編號", settings.showWeekNumber, onToggleShowWeekNum)
                        SettingSwitchRow("顯示月曆格線", settings.showMonthGridLines, onToggleGridLines)
                        SettingSwitchRow("在月曆暫時透視隱藏例行事務", settings.peekHiddenInMonth, onTogglePeekHiddenInMonth)

                        HorizontalDivider(color = tokens.gridLineColor)
                        SettingSwitchRow(
                            label = "啟用吃藥／例行提醒功能（預設關閉）",
                            checked = settings.routineMedicationFeatureEnabled,
                            onCheckedChange = onToggleRoutineMedicationFeature
                        )
                        Text(
                            text = "預設關閉且不自動建立任何吃藥項目；開啟後可依需求於「例行中心」自行新增與管理提醒規則。",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )

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

            DxSettingsSection.IMPORT_EXPORT -> {
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("匯入與匯出日程 (ICS / URL)", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onImportIcsFile, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("匯入 ICS 檔案")
                            }
                            OutlinedButton(onClick = onOpenUrlImportDialog, modifier = Modifier.weight(1f)) {
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

            DxSettingsSection.PRIVACY -> {
                Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("隱私日曆設定", color = tokens.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "預設隱藏主畫面與選單中的隱私日曆入口，保護隱私不留痕跡；開啟下方開關後才會顯示隱私入口。",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )
                        SettingSwitchRow(
                            label = "啟用隱私日曆功能（開啟後顯示入口）",
                            checked = settings.privacyFeatureEnabled,
                            onCheckedChange = onTogglePrivacyFeature
                        )
                        if (settings.privacyFeatureEnabled) {
                            HorizontalDivider(color = tokens.gridLineColor)
                            SettingSwitchRow(
                                label = "解鎖時需要連續雙指紋驗證",
                                checked = settings.requireDualFingerprint,
                                onCheckedChange = onToggleRequireDualFingerprint
                            )
                            Button(
                                onClick = onOpenPrivacyVault,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("前往開啟隱私日曆", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            DxSettingsSection.ABOUT_UPDATE -> {
                GitHubUpdateCard(
                    autoCheckEnabled = settings.autoCheckUpdate,
                    updateState = updateState,
                    onOpenGitHubRepo = { onOpenUrl(GitHubAutoUpdater.GITHUB_REPO_URL) },
                    onOpenGitHubReleases = {
                        if (BuildConfig.IS_PLAY_BUILD) {
                            onOpenUrl("https://play.google.com/store/apps/details?id=com.dx.calendar")
                        } else {
                            onOpenUrl(GitHubAutoUpdater.GITHUB_RELEASES_URL)
                        }
                    },
                    onToggleAutoCheck = onToggleAutoCheckUpdate,
                    onCheckNow = onManualCheckUpdate,
                    onStartDownload = onStartUpdateDownload,
                    onCancelDownload = onCancelUpdateDownload,
                    onInstallApk = onInstallDownloadedApk
                )
            }
        }
    }
}

@Composable
private fun GitHubUpdateCard(
    autoCheckEnabled: Boolean,
    updateState: AppUpdateUiState,
    onOpenGitHubRepo: () -> Unit,
    onOpenGitHubReleases: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: () -> Unit
) {
    val tokens = LocalBrandTokens.current

    Surface(color = tokens.surfaceCardColor, shape = RoundedCornerShape(tokens.cardCorner)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = tokens.accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "關於 DX日曆 & GitHub 自動更新",
                            color = tokens.primaryTextColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "目前版本：v${updateState.currentVersion} · 開發者：dx9901mk5",
                            color = tokens.secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenGitHubRepo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GitHub 專案連結", fontSize = 13.sp)
                }
                OutlinedButton(onClick = onOpenGitHubReleases, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (BuildConfig.IS_PLAY_BUILD) "Google Play 商店" else "GitHub Releases", fontSize = 13.sp)
                }
            }

            if (BuildConfig.IS_PLAY_BUILD) {
                Text(
                    text = "此版本為 Google Play 發行版，更新將由 Google Play 商店統一安全派送。",
                    color = tokens.secondaryTextColor,
                    fontSize = 12.sp
                )
                return@Column
            }

            // 醒目的一鍵「點擊檢查自動更新（有更新自動下載並直接進入安裝）」主按鈕！
            Button(
                onClick = onCheckNow,
                enabled = updateState.phase != UpdatePhase.CHECKING && updateState.phase != UpdatePhase.DOWNLOADING,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (updateState.phase) {
                        UpdatePhase.CHECKING -> "正在檢查 GitHub 最新版本..."
                        UpdatePhase.DOWNLOADING -> "正在自動下載新版 APK..."
                        else -> "點擊檢查自動更新（有新版自動下載安裝）"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = tokens.gridLineColor)

            SettingSwitchRow(
                label = "自動判讀 GitHub 新版並直接下載更新",
                checked = autoCheckEnabled,
                onCheckedChange = onToggleAutoCheck
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.elevatedSurfaceColor.copy(alpha = 0.75f))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when (updateState.phase) {
                        UpdatePhase.CHECKING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = tokens.accentColor
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = updateState.statusMessage.ifBlank { "正在連線 GitHub 判讀最新版本..." },
                                    color = tokens.primaryTextColor,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        UpdatePhase.DOWNLOADING -> {
                            val pct = (updateState.progressFraction * 100).toInt()
                            val dlMb = updateState.downloadedBytes / (1024f * 1024f)
                            val totMb = updateState.totalBytes / (1024f * 1024f)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "正在下載新版本 ${updateState.releaseInfo?.tagName.orEmpty()}",
                                    color = tokens.accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (updateState.totalBytes > 0) {
                                        "$pct% (%.1f / %.1f MB)".format(dlMb, totMb)
                                    } else "$pct%",
                                    color = tokens.primaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { updateState.progressFraction },
                                color = tokens.accentColor,
                                trackColor = tokens.gridLineColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = onCancelDownload) {
                                    Text("取消下載", color = tokens.secondaryTextColor, fontSize = 12.sp)
                                }
                            }
                        }

                        UpdatePhase.READY_TO_INSTALL -> {
                            Text(
                                text = "✓ 新版本 ${updateState.releaseInfo?.tagName.orEmpty()} 下載完成 (100%)",
                                color = Color(0xFF00C853),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = { 1f },
                                color = Color(0xFF00C853),
                                trackColor = tokens.gridLineColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Button(
                                onClick = onInstallApk,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("立即安裝新版本覆蓋更新")
                            }
                        }

                        UpdatePhase.UPDATE_AVAILABLE -> {
                            Text(
                                text = "🎉 發現新版本：${updateState.releaseInfo?.tagName.orEmpty()}",
                                color = tokens.accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val notesPreview = updateState.releaseInfo?.releaseNotes.orEmpty()
                            if (notesPreview.isNotBlank()) {
                                Text(
                                    text = notesPreview.lines().take(5).joinToString("\n"),
                                    color = tokens.secondaryTextColor,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = onStartDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即下載並更新")
                            }
                        }

                        UpdatePhase.UP_TO_DATE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "目前已是最新版本 (v${updateState.currentVersion})",
                                        color = tokens.primaryTextColor,
                                        fontSize = 13.sp
                                    )
                                }
                                TextButton(onClick = onCheckNow) {
                                    Text("重新檢查", fontSize = 12.sp)
                                }
                            }
                        }

                        UpdatePhase.ERROR, UpdatePhase.IDLE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = updateState.statusMessage.ifBlank { "點擊右側按鈕檢查 GitHub 最新版本" },
                                    color = tokens.secondaryTextColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = onCheckNow) {
                                    Text("檢查更新", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
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
