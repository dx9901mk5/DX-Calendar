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
        assertEquals(11, RecurrenceType.entries.size)
        assertTrue(RecurrenceType.entries.contains(RecurrenceType.LUNAR_MONTHLY))
        assertTrue(RecurrenceType.entries.contains(RecurrenceType.LUNAR_FIRST_FIFTEENTH))
    }

    @Test
    fun verifyCustomWeeksRecurrenceCodec() {
        val encoded = com.dx.calendar.data.model.RecurrenceCodec.encode(RecurrenceType.CUSTOM_WEEKS, 3)
        assertEquals("EVERY_N_WEEKS:3", encoded)
        val (decodedType, decodedWeeks) = com.dx.calendar.data.model.RecurrenceCodec.decode(encoded)
        assertEquals(RecurrenceType.CUSTOM_WEEKS, decodedType)
        assertEquals(3, decodedWeeks)
        assertEquals("FREQ=WEEKLY;INTERVAL=3", com.dx.calendar.data.model.RecurrenceCodec.toRRule(decodedType, decodedWeeks))
        assertEquals("隔 3 週重複", com.dx.calendar.data.model.RecurrenceCodec.formatLabel(decodedType, decodedWeeks))
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

        // 動畫輕量幀預算（160_000 px）絕不可因 coerceIn 空區間拋出 IllegalArgumentException！
        val animBudget = MonthCanvasRenderer.computeScreenSafePixelBudget(1080, 2400, MonthCanvasRenderer.ANIM_FRAME_MAX_PIXELS)
        assertEquals(MonthCanvasRenderer.ANIM_FRAME_MAX_PIXELS, animBudget)
        val (wAnim, hAnim) = MonthCanvasRenderer.computeSafeCanvasDimensions(320, 320, 3.0f, maxPixels = animBudget)
        assertTrue(wAnim * hAnim <= animBudget + 1500)

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

    @Test
    fun adaptiveLayoutSpecHandlesPhoneTabletFoldableSplitAndTransientBounds() {
        // 主流手機 360x780：正確歸類為 PHONE_COMPACT（不再誤判為 MINI_FLOATING），年檢視 2 欄防擠壓
        val phone360 = com.dx.calendar.ui.adaptive.computeAdaptiveLayoutSpec(
            androidx.compose.ui.unit.Dp(360f),
            androidx.compose.ui.unit.Dp(780f)
        )
        assertEquals(com.dx.calendar.ui.adaptive.WindowFormFactor.PHONE_COMPACT, phone360.formFactor)
        assertFalse(phone360.showDualPane)
        assertTrue(phone360.isNarrowSplit)
        assertEquals(2, phone360.yearGridColumns)

        // 摺疊機上下分割 720x380：保留左右雙欄、單行頂欄省高度、自動隱藏月曆農曆副標
        val foldableTopBottom = com.dx.calendar.ui.adaptive.computeAdaptiveLayoutSpec(
            androidx.compose.ui.unit.Dp(720f),
            androidx.compose.ui.unit.Dp(380f)
        )
        assertEquals(com.dx.calendar.ui.adaptive.WindowFormFactor.MINI_FLOATING, foldableTopBottom.formFactor)
        assertTrue(foldableTopBottom.showDualPane)
        assertFalse(foldableTopBottom.compactTopBar)
        assertTrue(foldableTopBottom.isShortSplit)
        assertTrue(foldableTopBottom.hideLunarInMonthCell)

        // 8吋平板 / 摺疊機展開全螢幕 760x840：雙欄 57/43
        val foldableUnfolded = com.dx.calendar.ui.adaptive.computeAdaptiveLayoutSpec(
            androidx.compose.ui.unit.Dp(760f),
            androidx.compose.ui.unit.Dp(840f)
        )
        assertEquals(com.dx.calendar.ui.adaptive.WindowFormFactor.TABLET_8_DUAL_PANE, foldableUnfolded.formFactor)
        assertTrue(foldableUnfolded.showDualPane)
        assertEquals(3, foldableUnfolded.yearGridColumns)

        // 10吋平板 / Pixelbook 1200x800：三欄（含常駐分類側欄）與 4 欄年檢視
        val tablet10 = com.dx.calendar.ui.adaptive.computeAdaptiveLayoutSpec(
            androidx.compose.ui.unit.Dp(1200f),
            androidx.compose.ui.unit.Dp(800f)
        )
        assertEquals(com.dx.calendar.ui.adaptive.WindowFormFactor.TABLET_10_PIXELBOOK, tablet10.formFactor)
        assertTrue(tablet10.showPermanentCategoryRail)
        assertEquals(4, tablet10.yearGridColumns)

        // 過渡幀極端值（NaN / Infinity / 0dp）安全不崩潰且權重永遠為正
        val nanSpec = com.dx.calendar.ui.adaptive.computeAdaptiveLayoutSpec(
            androidx.compose.ui.unit.Dp(Float.NaN),
            androidx.compose.ui.unit.Dp(Float.POSITIVE_INFINITY)
        )
        assertTrue(nanSpec.leftPaneWeight > 0f)
        assertTrue(nanSpec.widthDp.value >= 240f && nanSpec.heightDp.value >= 240f)
    }

    @Test
    fun alternateCalendarResolvesRegionDefaultToTaiwanLunar() {
        assertEquals(
            com.dx.calendar.data.local.AlternateCalendarMode.TAIWAN_LUNAR,
            com.dx.calendar.data.local.AlternateCalendarMode.resolveRegionDefault(
                locale = java.util.Locale.TAIWAN,
                zoneId = "Asia/Taipei"
            )
        )
        assertEquals(
            com.dx.calendar.data.local.AlternateCalendarMode.HONG_KONG_LUNAR,
            com.dx.calendar.data.local.AlternateCalendarMode.resolveRegionDefault(
                locale = java.util.Locale("zh", "HK"),
                zoneId = "Asia/Hong_Kong"
            )
        )
        val twNationalDay = LocalDate.of(2026, 10, 10)
        val lunar = LunarCalendarEngine.getLunarInfo(twNationalDay)
        val (twText, twHighlight) = LunarCalendarEngine.formatCellSubtitle(
            date = twNationalDay,
            info = lunar,
            altMode = com.dx.calendar.data.local.AlternateCalendarMode.TAIWAN_LUNAR,
            displayMode = com.dx.calendar.data.local.DateInfoDisplayMode.LUNAR_AND_HOLIDAY,
            showPublicHolidays = true,
            showTraditionalFestivals = true,
            showSolarTerms = true
        )
        assertEquals("國慶日", twText)
        assertTrue(twHighlight)
    }

    @Test
    fun offlineNaturalLanguageParser_parsesChineseTimeDateRecurrenceAndReminders() {
        val base = LocalDate.of(2026, 11, 26)
        val p1 = com.dx.calendar.util.NaturalLanguageEventParser.parse("明天下午3點半跟王經理開會 提前15分", base)
        assertEquals(LocalDate.of(2026, 11, 27), p1.date)
        assertEquals(15, p1.startTime.hour)
        assertEquals(30, p1.startTime.minute)
        assertFalse(p1.isAllDay)
        assertTrue(p1.reminderMinutes.contains(15))
        assertTrue(p1.title.contains("王經理開會"))

        val p2 = com.dx.calendar.util.NaturalLanguageEventParser.parse("農曆初一十五拜拜 08:00-09:00", base)
        assertEquals(com.dx.calendar.data.model.RecurrenceType.LUNAR_FIRST_FIFTEENTH, p2.recurrenceType)
        assertEquals(8, p2.startTime.hour)
        assertEquals(9, p2.endTime.hour)
        assertTrue(p2.title.contains("拜拜"))

        val p3 = com.dx.calendar.util.NaturalLanguageEventParser.parse("倒數 12/31 跨年煙火", base)
        assertEquals(LocalDate.of(2026, 12, 31), p3.date)
        assertTrue(p3.isCountdown)
        assertTrue(p3.isAllDay)
    }

    @Test
    fun countdownBadgeAndBackupFileName_formatCorrectly() {
        val today = LocalDate.of(2026, 11, 26)
        val meta = com.dx.calendar.data.local.EventRichMeta(
            key = "LOCAL_EVENT:1",
            isCountdown = true,
            anniversaryStartYear = 1996,
            isBirthday = true
        )
        val badge = com.dx.calendar.data.local.EventRichMetaStore.formatCountdownBadge(
            targetDate = LocalDate.of(2026, 12, 6),
            meta = meta,
            today = today
        )
        assertTrue(badge != null)
        assertTrue(badge!!.contains("10 天"))
        assertTrue(badge.contains("30 歲"))

        val fileName = com.dx.calendar.data.backup.FullBackupManager.buildBackupFileName("我的 vivo X Fold")
        assertTrue(fileName.startsWith("DX-Calendar_我的_vivo_X_Fold_"))
        assertTrue(fileName.endsWith(".dxbackup.json"))
    }

    @Test
    fun customReminderDurationFormatter_andWomensHealthPresets_workAccurately() {
        // 1. 自訂幾天幾時幾分前換算與格式化驗證
        assertEquals(0, com.dx.calendar.util.ReminderTimeFormatter.computeOffsetMinutes(0, 0, 0))
        assertEquals("活動時間", com.dx.calendar.util.ReminderTimeFormatter.formatBeforeLabel(0))
        assertEquals("活動現在開始", com.dx.calendar.util.ReminderTimeFormatter.formatCountdownSubtitle(0, "活動"))

        val customMin = com.dx.calendar.util.ReminderTimeFormatter.computeOffsetMinutes(2, 3, 15)
        assertEquals(2 * 1440 + 3 * 60 + 15, customMin)
        assertEquals(Triple(2, 3, 15), com.dx.calendar.util.ReminderTimeFormatter.decomposeOffsetMinutes(customMin))
        assertEquals("2 天 3 小時 15 分鐘前", com.dx.calendar.util.ReminderTimeFormatter.formatBeforeLabel(customMin))
        assertEquals("2天3時15分前", com.dx.calendar.util.ReminderTimeFormatter.formatCompactChipLabel(customMin))
        assertEquals("2 天 3 小時 15 分鐘後開始", com.dx.calendar.util.ReminderTimeFormatter.formatCountdownSubtitle(customMin, "行程"))
        assertEquals("1 週前", com.dx.calendar.util.ReminderTimeFormatter.formatBeforeLabel(10080))
        assertEquals("14 天前", com.dx.calendar.util.ReminderTimeFormatter.formatBeforeLabel(20160))

        // 邊界夾緊驗證（最多 365 天 23 時 59 分，不溢位）
        val maxOffset = com.dx.calendar.util.ReminderTimeFormatter.computeOffsetMinutes(999, 99, 99)
        assertEquals(365 * 1440 + 23 * 60 + 59, maxOffset)

        // 2. 女性健康內建分類與預設追蹤項目驗證
        val cat = com.dx.calendar.data.model.WomensHealthPresets.createCategoryEntity()
        assertEquals(9L, cat.id)
        assertEquals("女性健康", cat.name)
        assertTrue(com.dx.calendar.data.model.WomensHealthPresets.isWomensHealthCategory(cat))

        val subtypes = com.dx.calendar.data.model.WomensHealthPresets.SUBTYPES
        assertEquals(8, subtypes.size)
        val period = subtypes.first { it.id == "MENSTRUATION" }
        assertEquals(5, period.durationDays)
        assertEquals(RecurrenceType.CUSTOM_WEEKS, period.defaultRecurrence)
        assertEquals(4, period.recurrenceIntervalWeeks)
        assertEquals(listOf(2880, 1440, 0), period.defaultReminderMinutes)

        val edd = subtypes.first { it.id == "DUE_DATE" }
        assertTrue(edd.defaultIsCountdown)
        assertTrue(edd.defaultReminderMinutes.contains(20160))

        // 3. 女性健康設定開關預設關閉 + 桌面小工具霧面卡片調色 + 天氣有霧圖示驗證
        assertFalse(UserSettings().womensHealthFeatureEnabled)
        val darkCardTint = com.dx.calendar.widget.computeFrostedWidgetCardTint(0xFFFFB300.toInt(), darkStyle = true)
        assertEquals(0xBA, (darkCardTint ushr 24) and 0xFF)
        assertEquals("☁️", com.dx.calendar.data.weather.WeatherForecastManager.mapWmoWeatherCode(45).first)
    }

    @Test
    fun lineSharedMultiLineMessage_parsesTitleDateTimeLocationUrlAndNotes() {
        val base = LocalDate.of(2026, 11, 27)
        val lineMsg = """
            【歲末部門聚餐】
            日期：12/5 (六)
            時間：晚上 7:00 - 9:30
            地點：鼎泰豐信義店
            https://maps.app.goo.gl/example123
            備註：記得帶交換禮物 提前30分
        """.trimIndent()

        val parsed = com.dx.calendar.util.NaturalLanguageEventParser.parse(lineMsg, base)
        assertEquals("歲末部門聚餐", parsed.title)
        assertEquals(LocalDate.of(2026, 12, 5), parsed.date)
        assertFalse(parsed.isAllDay)
        assertEquals(19, parsed.startTime.hour)
        assertEquals(0, parsed.startTime.minute)
        assertEquals(21, parsed.endTime.hour)
        assertEquals(30, parsed.endTime.minute)
        assertEquals("鼎泰豐信義店", parsed.location)
        assertEquals("https://maps.app.goo.gl/example123", parsed.url)
        assertTrue(parsed.reminderMinutes.contains(30))
        assertTrue(parsed.notes.contains("交換禮物"))

        // 支援台灣口語「下禮拜三」
        val casual = com.dx.calendar.util.NaturalLanguageEventParser.parse("下禮拜三下午2點半看牙醫", base)
        assertEquals(LocalDate.of(2026, 12, 2), casual.date)
        assertEquals(14, casual.startTime.hour)
        assertEquals(30, casual.startTime.minute)
    }

    @Test
    fun googleTasksDueTimeExtraction_handlesApiMidnightAndTextTimeFallback() {
        val zone = java.time.ZoneId.of("Asia/Taipei")
        // 1. Google Tasks API 預設回傳 00:00:00.000Z，無時間文字時回傳 null
        assertEquals(
            null,
            com.dx.calendar.data.sync.GoogleTasksSyncManager.extractDueMinuteOfDay(
                dueIso = "2026-11-27T00:00:00.000Z",
                title = "去慈惠醫院",
                notes = "",
                zone = zone
            )
        )
        // 2. 標題帶 [13:30] 或 14:30 時自動擷取正確時刻
        assertEquals(
            13 * 60 + 30,
            com.dx.calendar.data.sync.GoogleTasksSyncManager.extractDueMinuteOfDay(
                dueIso = "2026-11-27T00:00:00.000Z",
                title = "[13:30] 去慈惠醫院",
                notes = "",
                zone = zone
            )
        )
        // 3. 備註帶中文時刻（下午3點半）自動擷取 15:30
        assertEquals(
            15 * 60 + 30,
            com.dx.calendar.data.sync.GoogleTasksSyncManager.extractDueMinuteOfDay(
                dueIso = "2026-11-27T00:00:00.000Z",
                title = "去慈惠醫院",
                notes = "下午3點半掛號",
                zone = zone
            )
        )
        // 4. 若未來 API 回傳非午夜 UTC 時間，依時區正確轉換（06:30 UTC = 14:30 Asia/Taipei）
        assertEquals(
            14 * 60 + 30,
            com.dx.calendar.data.sync.GoogleTasksSyncManager.extractDueMinuteOfDay(
                dueIso = "2026-11-27T06:30:00.000Z",
                title = "去慈惠醫院",
                notes = "",
                zone = zone
            )
        )
    }
}
