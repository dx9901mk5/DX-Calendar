package com.dx.calendar.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowFormFactor {
    MINI_FLOATING,          // 小視窗 / 分割畫面 (< 420dp 寬 或 < 460dp 高)
    PHONE_COMPACT,          // 手機直向 (420dp ~ 599dp)
    TABLET_8_DUAL_PANE,     // 8吋平板 / 摺疊機展開 (600dp ~ 959dp) -> 仿示範影片 vivo 左右雙欄
    TABLET_10_PIXELBOOK     // 10吋平板 / Pixelbook 寬桌面 (>= 960dp) -> 支援三欄或寬版雙欄 + 鍵鼠優化
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
    val compactTopBar: Boolean
)

fun computeAdaptiveLayoutSpec(widthDp: Dp, heightDp: Dp): AdaptiveLayoutSpec {
    // 摺疊機展開/收合過渡幀防呆：確保長寬永遠為安全正值，絕不產生 0 或負數權重
    val safeW = widthDp.coerceAtLeast(240.dp)
    val safeH = heightDp.coerceAtLeast(240.dp)
    return when {
        safeW < 420.dp || safeH < 460.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.MINI_FLOATING,
            widthDp = safeW,
            heightDp = safeH,
            showDualPane = safeW >= 560.dp, // 橫扁小視窗仍可雙欄
            showPermanentCategoryRail = false,
            leftPaneWeight = 0.58f,
            rightPaneWeight = 0.42f,
            maxEventsInMonthCell = if (safeH < 420.dp) 1 else 2,
            compactTopBar = true
        )

        safeW < 600.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.PHONE_COMPACT,
            widthDp = safeW,
            heightDp = safeH,
            showDualPane = false,
            showPermanentCategoryRail = false,
            leftPaneWeight = 1f,
            rightPaneWeight = 0f,
            maxEventsInMonthCell = if (safeH > 760.dp) 3 else 2,
            compactTopBar = true
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
            compactTopBar = safeW < 720.dp
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
            compactTopBar = false
        )
    }
}
