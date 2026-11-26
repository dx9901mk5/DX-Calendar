package com.dx.calendar.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowFormFactor {
    MINI_FLOATING,          // 超窄浮動小視窗 (< 340dp 寬) 或 橫扁上下分割 (< 460dp 高)
    PHONE_COMPACT,          // 手機直向 / 摺疊機左右分割 (340dp ~ 599dp 寬 且 >= 460dp 高)
    TABLET_8_DUAL_PANE,     // 8吋平板 / 摺疊機展開 (600dp ~ 959dp) -> 左右雙欄
    TABLET_10_PIXELBOOK     // 10吋平板 / Pixelbook 寬桌面 (>= 960dp) -> 雙欄或三欄 + 鍵鼠優化
}

@Immutable
data class AdaptiveLayoutSpec(
    val formFactor: WindowFormFactor,
    val widthDp: Dp,
    val heightDp: Dp,
    val showDualPane: Boolean,
    val showPermanentCategoryRail: Boolean,
    val leftPaneWeight: Float,
    val rightPaneWeight: Float,
    val maxEventsInMonthCell: Int,
    val compactTopBar: Boolean,
    val isNarrowSplit: Boolean = false,
    val isShortSplit: Boolean = false,
    val yearGridColumns: Int = 3,
    val hideLunarInMonthCell: Boolean = false
)

fun computeAdaptiveLayoutSpec(widthDp: Dp, heightDp: Dp): AdaptiveLayoutSpec {
    // 摺疊機展開/收合與分割線拖曳過渡幀防呆：過濾 NaN/Infinity 並確保長寬永遠為安全正值，絕不產生 0 或負數權重
    val rawW = if (widthDp.value.isFinite()) widthDp.value else 360f
    val rawH = if (heightDp.value.isFinite()) heightDp.value else 720f
    val safeW = rawW.coerceIn(240f, 4096f).dp
    val safeH = rawH.coerceIn(240f, 4096f).dp
    val narrow = safeW < 370.dp
    val shortSplit = safeH < 480.dp
    val yearCols = when {
        safeW < 370.dp -> 2
        safeW >= 960.dp -> 4
        else -> 3
    }
    return when {
        safeW < 340.dp || safeH < 460.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.MINI_FLOATING,
            widthDp = safeW,
            heightDp = safeH,
            showDualPane = safeW >= 560.dp, // 摺疊機/平板上下分割橫扁視窗保留雙欄
            showPermanentCategoryRail = false,
            leftPaneWeight = 0.58f,
            rightPaneWeight = 0.42f,
            maxEventsInMonthCell = if (safeH < 420.dp) 1 else 2,
            compactTopBar = safeW < 640.dp, // 橫扁寬螢幕維持單行頂欄，省下高度給月曆
            isNarrowSplit = narrow,
            isShortSplit = shortSplit,
            yearGridColumns = yearCols,
            hideLunarInMonthCell = safeH < 430.dp
        )

        safeW < 600.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.PHONE_COMPACT,
            widthDp = safeW,
            heightDp = safeH,
            showDualPane = false,
            showPermanentCategoryRail = false,
            leftPaneWeight = 1f,
            rightPaneWeight = 0f,
            maxEventsInMonthCell = if (safeH >= 740.dp) 3 else 2,
            compactTopBar = true,
            isNarrowSplit = narrow,
            isShortSplit = shortSplit,
            yearGridColumns = yearCols,
            hideLunarInMonthCell = false
        )

        safeW < 960.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.TABLET_8_DUAL_PANE,
            widthDp = safeW,
            heightDp = safeH,
            showDualPane = true,
            showPermanentCategoryRail = false,
            leftPaneWeight = 0.57f,
            rightPaneWeight = 0.43f,
            maxEventsInMonthCell = if (safeH > 700.dp) 4 else 3,
            compactTopBar = safeW < 720.dp,
            isNarrowSplit = false,
            isShortSplit = shortSplit,
            yearGridColumns = yearCols,
            hideLunarInMonthCell = false
        )

        else -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.TABLET_10_PIXELBOOK,
            widthDp = safeW,
            heightDp = safeH,
            showDualPane = true,
            showPermanentCategoryRail = safeW >= 1120.dp,
            leftPaneWeight = 0.60f,
            rightPaneWeight = 0.40f,
            maxEventsInMonthCell = if (safeH > 720.dp) 5 else 3,
            compactTopBar = false,
            isNarrowSplit = false,
            isShortSplit = shortSplit,
            yearGridColumns = yearCols,
            hideLunarInMonthCell = false
        )
    }
}
