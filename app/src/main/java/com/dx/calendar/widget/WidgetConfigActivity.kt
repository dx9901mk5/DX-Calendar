package com.dx.calendar.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dx.calendar.data.local.AppPreferences
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 桌面小工具專屬懸浮設定視窗：
 * - 點擊小工具右上角「⚙」按鈕，或長按桌面小工具選擇系統「設定 / 重新設定」時直接彈出！
 * - 背景保持半透明穿透桌布，支援 0%（完全透明）～ 100%（不透明）無段調整與深色/淺色文字底色切換。
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 預設回傳 RESULT_CANCELED，若使用者完成套用則回傳 RESULT_OK
        val cancelResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, cancelResult)

        val prefs = AppPreferences(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF5238),
                    surface = Color(0xFF1B1B21),
                    onSurface = Color(0xFFF5F5F7)
                )
            ) {
                WidgetConfigDialogScreen(
                    appWidgetId = appWidgetId,
                    prefs = prefs,
                    onDismiss = { finish() },
                    onSaveComplete = { isDark, opacity, applyToAll ->
                        if (applyToAll || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                            WidgetRefreshHelper.clearAllPerWidgetAppearanceOverrides(applicationContext)
                        } else {
                            WidgetRefreshHelper.setPerWidgetAppearance(
                                applicationContext,
                                appWidgetId,
                                isDark,
                                opacity
                            )
                        }
                        WidgetRefreshHelper.refreshAllWidgets(applicationContext)
                        val okResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(Activity.RESULT_OK, okResult)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigDialogScreen(
    appWidgetId: Int,
    prefs: AppPreferences,
    onDismiss: () -> Unit,
    onSaveComplete: (isDark: Boolean, opacity: Float, applyToAll: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var isDark by remember { mutableStateOf(true) }
    var opacity by remember { mutableFloatStateOf(0.85f) } // 0.0f (全透明) .. 1.0f (不透明)
    var applyToAll by remember { mutableStateOf(appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) }

    LaunchedEffect(appWidgetId) {
        val global = prefs.getSettingsOnce()
        val (initDark, initOpacity) = WidgetRefreshHelper.getEffectiveWidgetAppearance(
            context = context,
            appWidgetId = appWidgetId,
            globalDark = global.widgetDarkStyle,
            globalOpacity = global.widgetOpacity
        )
        isDark = initDark
        opacity = initOpacity
    }

    // 外層半透明點擊背景（點擊卡片外部可直接關閉，背後可直接透視桌面桌布！）
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(20.dp)
    ) {
        Surface(
            color = Color(0xF21B1B22),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 攔截卡片內部點擊避免誤關閉 */ }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "桌面小工具透明度與外觀設定",
                    color = Color(0xFFF5F5F7),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // 即時穿透預覽框（棋盤格模擬桌布底 + 當前設定的小工具圓角底色與文字）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(94.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                ) {
                    // 底層棋盤格輔助辨識透明度
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cell = 14.dp.toPx()
                        val cols = (size.width / cell).toInt() + 1
                        val rows = (size.height / cell).toInt() + 1
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                val even = (r + c) % 2 == 0
                                drawRect(
                                    color = if (even) Color(0xFF3A4454) else Color(0xFF28303D),
                                    topLeft = Offset(c * cell, r * cell),
                                    size = Size(cell, cell)
                                )
                            }
                        }
                    }

                    // 模擬小工具本體背景與文字
                    val bgBase = if (isDark) Color(0xFF19191E) else Color(0xFFFFFFFF)
                    val fgTitle = if (isDark) Color(0xFFF5F5F7) else Color(0xFF18181B)
                    val fgSub = if (isDark) Color(0xFFA8A8B4) else Color(0xFF5F6368)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgBase.copy(alpha = opacity))
                            .border(
                                width = 0.8.dp,
                                color = if (opacity > 0.03f) {
                                    if (isDark) Color(0x2EFFFFFF) else Color(0x22000000)
                                } else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("2026/11 · 預覽效果", color = fgTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("不透明度 ${(opacity * 100).roundToInt()}%", color = Color(0xFFFF5238), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("26 十月十八 · 0% 為完全透明僅留文字與日程色塊", color = fgSub, fontSize = 11.sp)
                        }
                    }
                }

                // 0% ~ 100% 自由滑桿（注意：提供「背景不透明度」與「透明程度」雙標示，直覺不混淆）
                val opaquePct = (opacity * 100).roundToInt()
                val transparentPct = 100 - opaquePct
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "背景不透明度：$opaquePct%（透明 $transparentPct%）",
                            color = Color(0xFFF5F5F7),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it.coerceIn(0f, 1f) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF5238),
                            activeTrackColor = Color(0xFFFF5238)
                        )
                    )

                    // 快捷百分比膠囊
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            0f to "0% 全透明",
                            0.25f to "25%",
                            0.50f to "50%",
                            0.75f to "75%",
                            0.85f to "85%",
                            1.0f to "100% 實心"
                        ).forEach { (presetVal, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(opacity - presetVal) < 0.02f,
                                onClick = { opacity = presetVal },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0x24FFFFFF))

                // 深淺色底與字體切換（全透明時亦可藉此決定白字或黑字搭配桌布！）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("深色底／淺色文字模式", color = Color(0xFFF5F5F7), fontSize = 14.sp)
                        Text(
                            text = if (isDark) "目前：深色底＋白色字體（適合深色或花紋桌布）"
                            else "目前：淺色底＋深黑字體（適合明亮淺色桌布）",
                            color = Color(0xFF9E9EA8),
                            fontSize = 11.sp
                        )
                    }
                    Switch(checked = isDark, onCheckedChange = { isDark = it })
                }

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "同時套用至桌面上所有 DX 小工具",
                            color = Color(0xFFF5F5F7),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", color = Color(0xFFD0D0D8))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                if (applyToAll || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    prefs.setWidgetAppearance(isDark, opacity)
                                }
                                onSaveComplete(isDark, opacity, applyToAll)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5238)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("套用完成", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
