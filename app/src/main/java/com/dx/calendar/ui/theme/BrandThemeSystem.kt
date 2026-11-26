package com.dx.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.DarkThemeMode

@Immutable
data class BrandVisualTokens(
    val brandStyle: BrandStyle,
    val isDark: Boolean,
    val accentColor: Color,
    val secondaryAccent: Color,
    val backgroundColor: Color,
    val surfaceCardColor: Color,
    val elevatedSurfaceColor: Color,
    val floatingPillColor: Color,
    val gridLineColor: Color,
    val primaryTextColor: Color,
    val secondaryTextColor: Color,
    val weekendSundayColor: Color,
    val weekendSaturdayColor: Color,
    val holidayBadgeColor: Color,
    val selectedCellFill: Color,
    val selectedCellBorder: Color,
    val cardCorner: Dp,
    val cellCorner: Dp,
    val useDualToneYearHeader: Boolean,     // 雙色年標：20(深/白) + 26(主題金/橘紅)/11
    val useFloatingOriginCapsule: Boolean,  // 懸浮毛玻璃雙膠囊導覽
    val inlineDateAndLunar: Boolean         // 仿G 特色：25 (十七) 同行緊湊排列
)

val LocalBrandTokens = staticCompositionLocalOf {
    buildBrandTokens(BrandStyle.GOLD_WHITE, isDark = false)
}

fun buildBrandTokens(style: BrandStyle, isDark: Boolean): BrandVisualTokens {
    return when (style) {
        BrandStyle.GOLD_WHITE -> BrandVisualTokens(
            brandStyle = style,
            isDark = isDark,
            accentColor = if (isDark) Color(0xFFD8A63A) else Color(0xFFB8841E),
            secondaryAccent = if (isDark) Color(0xFFF0C96B) else Color(0xFFD9A948),
            backgroundColor = if (isDark) Color(0xFF0F0E0B) else Color(0xFFFAF8F3),
            surfaceCardColor = if (isDark) Color(0xFF1B1914) else Color(0xFFFFFFFF),
            elevatedSurfaceColor = if (isDark) Color(0xFF28241B) else Color(0xFFF4EEE1),
            floatingPillColor = if (isDark) Color(0xEE211E17) else Color(0xF7FFFFFF),
            gridLineColor = if (isDark) Color(0x2BD8A63A) else Color(0x28C6922C),
            primaryTextColor = if (isDark) Color(0xFFFAF6EE) else Color(0xFF221E17),
            secondaryTextColor = if (isDark) Color(0xFFA89E8C) else Color(0xFF7A6F5D),
            weekendSundayColor = if (isDark) Color(0xFFE57368) else Color(0xFFC45646),
            weekendSaturdayColor = if (isDark) Color(0xFFD8A63A) else Color(0xFFB8841E),
            holidayBadgeColor = if (isDark) Color(0xFFD8A63A) else Color(0xFFB8841E),
            selectedCellFill = if (isDark) Color(0xFF332B19) else Color(0xFFFFF5DC),
            selectedCellBorder = if (isDark) Color(0xFFD8A63A) else Color(0xFFC6922C),
            cardCorner = 22.dp,
            cellCorner = 12.dp,
            useDualToneYearHeader = true,
            useFloatingOriginCapsule = true,
            inlineDateAndLunar = false
        )

        BrandStyle.VIVO -> BrandVisualTokens(
            brandStyle = style,
            isDark = isDark,
            accentColor = Color(0xFFFF5238),
            secondaryAccent = Color(0xFFFF8A65),
            backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFF5F6F8),
            surfaceCardColor = if (isDark) Color(0xFF18181C) else Color(0xFFFFFFFF),
            elevatedSurfaceColor = if (isDark) Color(0xFF24242A) else Color(0xFFECEEF2),
            floatingPillColor = if (isDark) Color(0xE6222228) else Color(0xF0FFFFFF),
            gridLineColor = if (isDark) Color(0x22FFFFFF) else Color(0x18000000),
            primaryTextColor = if (isDark) Color(0xFFF5F5F7) else Color(0xFF141416),
            secondaryTextColor = if (isDark) Color(0xFF9A9AA2) else Color(0xFF6E6E77),
            weekendSundayColor = if (isDark) Color(0xFFB5B5BE) else Color(0xFF55555E),
            weekendSaturdayColor = if (isDark) Color(0xFFB5B5BE) else Color(0xFF55555E),
            holidayBadgeColor = Color(0xFFFF5238),
            selectedCellFill = if (isDark) Color(0xFF2E2E36) else Color(0xFFFFEAE6),
            selectedCellBorder = Color(0xFFFF5238),
            cardCorner = 22.dp,
            cellCorner = 12.dp,
            useDualToneYearHeader = true,
            useFloatingOriginCapsule = true,
            inlineDateAndLunar = false
        )

        BrandStyle.OPPO -> BrandVisualTokens(
            brandStyle = style,
            isDark = isDark,
            accentColor = Color(0xFF00B884),
            secondaryAccent = Color(0xFF2EC4B6),
            backgroundColor = if (isDark) Color(0xFF09110F) else Color(0xFFF2F8F6),
            surfaceCardColor = if (isDark) Color(0xFF14211E) else Color(0xFFFFFFFF),
            elevatedSurfaceColor = if (isDark) Color(0xFF1E302C) else Color(0xFFE4F2EE),
            floatingPillColor = if (isDark) Color(0xE6182824) else Color(0xF2FFFFFF),
            gridLineColor = if (isDark) Color(0x22A8E6CF) else Color(0x1800695C),
            primaryTextColor = if (isDark) Color(0xFFEFF7F5) else Color(0xFF10201C),
            secondaryTextColor = if (isDark) Color(0xFF8FAEA6) else Color(0xFF5A756E),
            weekendSundayColor = Color(0xFFFF6B6B),
            weekendSaturdayColor = Color(0xFF2EC4B6),
            holidayBadgeColor = Color(0xFF00B884),
            selectedCellFill = if (isDark) Color(0xFF1C3B33) else Color(0xFFDDF7EF),
            selectedCellBorder = Color(0xFF00B884),
            cardCorner = 20.dp,
            cellCorner = 14.dp,
            useDualToneYearHeader = false,
            useFloatingOriginCapsule = true,
            inlineDateAndLunar = false
        )

        BrandStyle.XIAOMI -> BrandVisualTokens(
            brandStyle = style,
            isDark = isDark,
            accentColor = Color(0xFF0D84FF),
            secondaryAccent = Color(0xFF53A8FF),
            backgroundColor = if (isDark) Color(0xFF0A0D12) else Color(0xFFF6F7FB),
            surfaceCardColor = if (isDark) Color(0xFF161A22) else Color(0xFFFFFFFF),
            elevatedSurfaceColor = if (isDark) Color(0xFF202632) else Color(0xFFEBEEF5),
            floatingPillColor = if (isDark) Color(0xE61A202C) else Color(0xF2FFFFFF),
            gridLineColor = if (isDark) Color(0x1FFFFFFF) else Color(0x16000000),
            primaryTextColor = if (isDark) Color(0xFFF2F5FA) else Color(0xFF12161F),
            secondaryTextColor = if (isDark) Color(0xFF8C95A6) else Color(0xFF687080),
            weekendSundayColor = Color(0xFFFF5E5E),
            weekendSaturdayColor = Color(0xFF53A8FF),
            holidayBadgeColor = Color(0xFF0D84FF),
            selectedCellFill = if (isDark) Color(0xFF173254) else Color(0xFFE3F0FF),
            selectedCellBorder = Color(0xFF0D84FF),
            cardCorner = 18.dp,
            cellCorner = 10.dp,
            useDualToneYearHeader = false,
            useFloatingOriginCapsule = false,
            inlineDateAndLunar = false
        )

        BrandStyle.GOOGLE -> BrandVisualTokens(
            brandStyle = style,
            isDark = isDark,
            accentColor = if (isDark) Color(0xFFA8C7FA) else Color(0xFF1A73E8),
            secondaryAccent = Color(0xFF7FCFFF),
            backgroundColor = if (isDark) Color(0xFF131314) else Color(0xFFFFFFFF),
            surfaceCardColor = if (isDark) Color(0xFF1E1F20) else Color(0xFFF8FAFD),
            elevatedSurfaceColor = if (isDark) Color(0xFF282A2C) else Color(0xFFEFF3F8),
            floatingPillColor = if (isDark) Color(0xEE282A2C) else Color(0xF5F0F4F9),
            gridLineColor = if (isDark) Color(0x36FFFFFF) else Color(0x24000000),
            primaryTextColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            secondaryTextColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368),
            weekendSundayColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            weekendSaturdayColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            holidayBadgeColor = Color(0xFF34A853),
            selectedCellFill = if (isDark) Color(0xFF004A77) else Color(0xFFD3E3FD),
            selectedCellBorder = if (isDark) Color(0xFFA8C7FA) else Color(0xFF1A73E8),
            cardCorner = 16.dp,
            cellCorner = 8.dp,
            useDualToneYearHeader = false,
            useFloatingOriginCapsule = false,
            inlineDateAndLunar = true
        )

        BrandStyle.SAMSUNG -> BrandVisualTokens(
            brandStyle = style,
            isDark = isDark,
            accentColor = Color(0xFF3E69FF),
            secondaryAccent = Color(0xFF7B9CFF),
            backgroundColor = if (isDark) Color(0xFF080E10) else Color(0xFFF2F2F7),
            surfaceCardColor = if (isDark) Color(0xFF172224) else Color(0xFFFFFFFF),
            elevatedSurfaceColor = if (isDark) Color(0xFF243336) else Color(0xFFE6ECEE),
            floatingPillColor = if (isDark) Color(0xEE1B282B) else Color(0xF5FFFFFF),
            gridLineColor = if (isDark) Color(0x24FFFFFF) else Color(0x18000000),
            primaryTextColor = if (isDark) Color(0xFFFAFAFC) else Color(0xFF111114),
            secondaryTextColor = if (isDark) Color(0xFF9BA8AC) else Color(0xFF636E72),
            weekendSundayColor = Color(0xFFFF5252),
            weekendSaturdayColor = Color(0xFF448AFF),
            holidayBadgeColor = Color(0xFFFF5252),
            selectedCellFill = if (isDark) Color(0xFF253A5E) else Color(0xFFE2EAFF),
            selectedCellBorder = Color(0xFF3E69FF),
            cardCorner = 26.dp,
            cellCorner = 14.dp,
            useDualToneYearHeader = false,
            useFloatingOriginCapsule = false,
            inlineDateAndLunar = false
        )
    }
}

@Composable
fun DXCalendarTheme(
    brandStyle: BrandStyle,
    darkThemeMode: DarkThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkThemeMode) {
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.SYSTEM -> systemDark
    }

    val tokens = buildBrandTokens(brandStyle, useDark)

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = tokens.accentColor,
            onPrimary = Color.White,
            secondary = tokens.secondaryAccent,
            background = tokens.backgroundColor,
            surface = tokens.surfaceCardColor,
            surfaceVariant = tokens.elevatedSurfaceColor,
            onBackground = tokens.primaryTextColor,
            onSurface = tokens.primaryTextColor,
            onSurfaceVariant = tokens.secondaryTextColor
        )
    } else {
        lightColorScheme(
            primary = tokens.accentColor,
            onPrimary = Color.White,
            secondary = tokens.secondaryAccent,
            background = tokens.backgroundColor,
            surface = tokens.surfaceCardColor,
            surfaceVariant = tokens.elevatedSurfaceColor,
            onBackground = tokens.primaryTextColor,
            onSurface = tokens.primaryTextColor,
            onSurfaceVariant = tokens.secondaryTextColor
        )
    }

    val shapes = Shapes(
        small = RoundedCornerShape(tokens.cellCorner),
        medium = RoundedCornerShape(tokens.cardCorner),
        large = RoundedCornerShape(tokens.cardCorner + 4.dp)
    )

    CompositionLocalProvider(LocalBrandTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            content = content
        )
    }
}
