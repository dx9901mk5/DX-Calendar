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
    return when {
        widthDp < 420.dp || heightDp < 460.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.MINI_FLOATING,
            widthDp = widthDp,
            heightDp = heightDp,
            showDualPane = widthDp >= 560.dp, // 橫扁小視窗仍可雙欄
            showPermanentCategoryRail = false,
            leftPaneWeight = 0.58f,
            rightPaneWeight = 0.42f,
            maxEventsInMonthCell = if (heightDp < 420.dp) 1 else 2,
            compactTopBar = true
        )

        widthDp < 600.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.PHONE_COMPACT,
            widthDp = widthDp,
            heightDp = heightDp,
            showDualPane = false,
            showPermanentCategoryRail = false,
            leftPaneWeight = 1f,
            rightPaneWeight = 0f,
            maxEventsInMonthCell = if (heightDp > 760.dp) 3 else 2,
            compactTopBar = true
        )

        widthDp < 960.dp -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.TABLET_8_DUAL_PANE,
            widthDp = widthDp,
            heightDp = heightDp,
            showDualPane = true,
            showPermanentCategoryRail = false,
            leftPaneWeight = 0.57f,
            rightPaneWeight = 0.43f,
            maxEventsInMonthCell = if (heightDp > 700.dp) 4 else 3,
            compactTopBar = false
        )

        else -> AdaptiveLayoutSpec(
            formFactor = WindowFormFactor.TABLET_10_PIXELBOOK,
            widthDp = widthDp,
            heightDp = heightDp,
            showDualPane = true,
            showPermanentCategoryRail = widthDp >= 1120.dp,
            leftPaneWeight = 0.60f,
            rightPaneWeight = 0.40f,
            maxEventsInMonthCell = if (heightDp > 720.dp) 5 else 3,
            compactTopBar = false
        )
    }
}
