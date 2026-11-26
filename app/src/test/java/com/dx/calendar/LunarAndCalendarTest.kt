package com.dx.calendar

import android.content.pm.PackageManager
import com.dx.calendar.alarm.LiveUpdateIslandHelper
import com.dx.calendar.data.local.UserSettings
import com.dx.calendar.data.model.BrandStyle
import com.dx.calendar.data.model.DarkThemeMode
import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.data.sync.SystemCalendarBridge
import com.dx.calendar.update.GitHubAutoUpdater
import com.dx.calendar.util.DynamicAppIconManager
import com.dx.calendar.util.LunarCalendarEngine
import com.dx.calendar.widget.MonthCanvasRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LunarAndCalendarTest {

    @Test
    fun verifyVivoVideoReferenceDate_2026_11_25() {
        // 對照用戶示範影片 01:05 的真實日期：2026年11月25日 -> 十月十七 / 丙午年 [馬] 己亥月 癸卯日
        val info = LunarCalendarEngine.getLunarInfo(LocalDate.of(2026, 11, 25))
        assertEquals("十月十七", info.fullLunarText)
        assertEquals("丙午", info.ganZhiYear)
        assertEquals("馬", info.zodiac)
        assertEquals("己亥", info.ganZhiMonth)
        assertEquals("癸卯", info.ganZhiDay)
        assertEquals("丙午年 [馬] 己亥月 癸卯日", info.fullGanZhiHeader)
    }

    @Test
    fun verifyFestivalsAndSolarTerms_2026() {
        // 對照示範影片 01:00 (2026/11/09 寒衣節 十月初一) & 01:03 (2026/11/23 下元節 十月十五) & 截圖1 (10/25 台灣光復)
        val hanYi = LunarCalendarEngine.getLunarInfo(LocalDate.of(2026, 11, 9))
        assertEquals("寒衣節", hanYi.festival)

        val xiaYuan = LunarCalendarEngine.getLunarInfo(LocalDate.of(2026, 11, 23))
        assertEquals("下元節", xiaYuan.festival)

        val retrocession = LunarCalendarEngine.getLunarInfo(LocalDate.of(2026, 10, 25))
        assertEquals("台灣光復", retrocession.festival)
        assertTrue(retrocession.isTaiwanHoliday)

        val dongZhi = LunarCalendarEngine.getLunarInfo(LocalDate.of(2026, 12, 22))
        assertEquals("冬至", dongZhi.solarTerm)
    }

    @Test
    fun verifyRecurrenceTypesExist() {
        assertEquals(8, RecurrenceType.entries.size)
    }

    @Test
    fun verifyGitHubSemanticVersionComparison() {
        assertTrue(GitHubAutoUpdater.compareSemVer("v1.1.0", "1.0.0") > 0)
        assertTrue(GitHubAutoUpdater.compareSemVer("v1.10.0", "v1.2.0") > 0)
        assertEquals(0, GitHubAutoUpdater.compareSemVer("v1.1.0", "1.1.0"))
        assertTrue(GitHubAutoUpdater.compareSemVer("v1.0.0", "1.1.0") < 0)
    }

    @Test
    fun verifyDynamicDayIconAliasNaming() {
        assertEquals("com.dx.calendar.MainActivityAliasDay01", DynamicAppIconManager.dayAliasName(1))
        assertEquals("com.dx.calendar.MainActivityAliasDay09", DynamicAppIconManager.dayAliasName(9))
        assertEquals("com.dx.calendar.MainActivityAliasDay25", DynamicAppIconManager.dayAliasName(25))
        assertEquals("com.dx.calendar.MainActivityAliasDay31", DynamicAppIconManager.dayAliasName(31))
        // 邊界夾緊測試
        assertEquals("com.dx.calendar.MainActivityAliasDay01", DynamicAppIconManager.dayAliasName(0))
        assertEquals("com.dx.calendar.MainActivityAliasDay31", DynamicAppIconManager.dayAliasName(99))
    }

    @Test
    fun verifyLiveUpdateShortCriticalTextConstraint() {
        assertEquals("3 min", LiveUpdateIslandHelper.formatShortCriticalText("3 min"))
        assertEquals("連段1/3", LiveUpdateIslandHelper.formatShortCriticalText("連段1/3"))
        assertEquals("提醒中", LiveUpdateIslandHelper.formatShortCriticalText("   "))
        assertTrue(
            LiveUpdateIslandHelper.formatShortCriticalText("這是一段超過七個字元的超長提醒").length <=
                LiveUpdateIslandHelper.MAX_SHORT_CRITICAL_CHARS
        )
    }

    @Test
    fun verifyWidgetCanvasFoldableMemoryBudgetAndAccordionWeights() {
        // 摺疊機展開大桌面 (例如 680x650dp, density=3.0f) 必須嚴格限制在像素預算上限內，根絕 TransactionTooLargeException
        val (wLarge, hLarge) = MonthCanvasRenderer.computeSafeCanvasDimensions(
            widthDp = 680,
            heightDp = 650,
            density = 3.0f,
            maxPixels = MonthCanvasRenderer.DEFAULT_MAX_PIXELS
        )
        assertTrue(wLarge * hLarge <= MonthCanvasRenderer.DEFAULT_MAX_PIXELS + 1500)
        assertTrue(wLarge >= 160 && hLarge >= 120)

        // 極端過渡 0dp 防呆保護
        val (wZero, hZero) = MonthCanvasRenderer.computeSafeCanvasDimensions(0, -10, 2.5f)
        assertTrue(wZero >= 160 && hZero >= 120)

        // 手風琴展開與切換週次動畫權重平滑插值驗證 (-1 -> 第2週, 第2週 -> 第4週)
        val wMidExpand = MonthCanvasRenderer.computeAccordionRowWeights(6, -1, 2, 0.5f)
        assertEquals(6, wMidExpand.size)
        assertEquals(2.5f, wMidExpand[2], 0.001f)
        assertEquals(1.0f, wMidExpand[0], 0.001f)

        val wSwitch = MonthCanvasRenderer.computeAccordionRowWeights(6, 1, 4, 0.34f)
        assertTrue(wSwitch[1] in 1.0f..4.0f)
        assertTrue(wSwitch[4] in 1.0f..4.0f)
        assertEquals(9.0f, wSwitch.sum(), 0.001f)
    }

    @Test
    fun verifyDynamicAliasEffectiveStateAvoidsRedundantDisables() {
        val def = DynamicAppIconManager.DEFAULT_ALIAS
        val day26 = DynamicAppIconManager.dayAliasName(26)
        assertTrue(DynamicAppIconManager.isAliasEffectivelyEnabled(def, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
        assertFalse(DynamicAppIconManager.isAliasEffectivelyEnabled(day26, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
        assertTrue(DynamicAppIconManager.isAliasEffectivelyEnabled(day26, PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
        assertFalse(DynamicAppIconManager.isAliasEffectivelyEnabled(def, PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
    }

    @Test
    fun verifyConciseCalendarNameAndVivoCalendarRemoval() {
        // 1. 確保內建 vivo 日曆在各種命名/帳號形式下皆被過濾移除
        assertTrue(SystemCalendarBridge.isExcludedVendorCalendar("vivo日曆", "LOCAL", "LOCAL"))
        assertTrue(SystemCalendarBridge.isExcludedVendorCalendar("vivo 日曆", "Phone", "LOCAL"))
        assertTrue(SystemCalendarBridge.isExcludedVendorCalendar("我的日曆", "vivo", "com.bbk.calendar"))
        assertFalse(SystemCalendarBridge.isExcludedVendorCalendar("家庭", "user@gmail.com", "com.google"))

        // 2. 確保行事曆名稱完全不顯示 Email / 帳號，呈現簡潔
        assertEquals("主要行事曆", SystemCalendarBridge.formatConciseCalendarName("dx9901mk5@gmail.com", "dx9901mk5@gmail.com"))
        assertEquals("學務處", SystemCalendarBridge.formatConciseCalendarName("學務處 (dx9901mk5@gmail.com)", "dx9901mk5@gmail.com"))
        assertEquals("節慶假日", SystemCalendarBridge.formatConciseCalendarName("zh_tw.taiwan#holiday@group.v.calendar.google.com", "user@gmail.com"))
        assertEquals("家庭", SystemCalendarBridge.formatConciseCalendarName("家庭", "user@gmail.com"))
    }

    @Test
    fun verifyGoldWhiteAndGoldDarkDefaultSystemThemeAndBrandRenaming() {
        val defaultSettings = UserSettings()
        assertEquals(BrandStyle.GOLD_WHITE, defaultSettings.brandStyle)
        assertEquals(DarkThemeMode.SYSTEM, defaultSettings.darkThemeMode)
        assertTrue(defaultSettings.widgetFollowSystemDark)

        val entries = BrandStyle.entries
        assertEquals(7, entries.size)
        assertEquals(BrandStyle.GOLD_WHITE, entries[0])
        assertEquals(BrandStyle.GOLD_DARK, entries[1])
        assertEquals("金白風格 (明亮主題預設)", BrandStyle.GOLD_WHITE.displayName)
        assertEquals("金色灰黑風格 (暗色主題預設)", BrandStyle.GOLD_DARK.displayName)
        assertEquals("仿V 風格", BrandStyle.VIVO.displayName)
        assertEquals("仿O 風格", BrandStyle.OPPO.displayName)
        assertEquals("仿M 風格", BrandStyle.XIAOMI.displayName)
        assertEquals("仿G 風格", BrandStyle.GOOGLE.displayName)
        assertEquals("仿S 風格", BrandStyle.SAMSUNG.displayName)

        // 驗證明亮模式為白底、暗色模式自動切換為沉穩灰黑底 (#16171B)
        val lightTokens = com.dx.calendar.ui.theme.buildBrandTokens(BrandStyle.GOLD_WHITE, isDark = false)
        val darkTokens = com.dx.calendar.ui.theme.buildBrandTokens(BrandStyle.GOLD_WHITE, isDark = true)
        assertFalse(lightTokens.isDark)
        assertTrue(darkTokens.isDark)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF16171B), darkTokens.backgroundColor)
    }

    @Test
    fun verifyDefaultGmailGoogleCalendarSelection() {
        val cals = listOf(
            com.dx.calendar.data.sync.SystemCalendarAccountInfo(
                id = 10L,
                displayName = "家庭",
                accountName = "dx9901mk5@gmail.com",
                accountType = "com.google",
                colorArgb = 0xFF00B884.toInt(),
                isWritable = true,
                isPrimary = false,
                isGoogleAccount = true
            ),
            com.dx.calendar.data.sync.SystemCalendarAccountInfo(
                id = 20L,
                displayName = "主要行事曆",
                accountName = "dx9901mk5@gmail.com",
                accountType = "com.google",
                colorArgb = 0xFF4285F4.toInt(),
                isWritable = true,
                isPrimary = true,
                isGoogleAccount = true
            ),
            com.dx.calendar.data.sync.SystemCalendarAccountInfo(
                id = 30L,
                displayName = "公司帳號",
                accountName = "work@company.com",
                accountType = "com.google",
                colorArgb = 0xFFFF5238.toInt(),
                isWritable = true,
                isPrimary = true,
                isGoogleAccount = true
            )
        )
        // 未指定特定信箱時，優先挑選裝置已登記的 @gmail.com 主行事曆
        assertEquals(20L, SystemCalendarBridge.pickDefaultGoogleCalendar(cals)?.id)
        // 指定已連結帳號時，精準對應其主行事曆
        assertEquals(30L, SystemCalendarBridge.pickDefaultGoogleCalendar(cals, "work@company.com")?.id)
    }
}
