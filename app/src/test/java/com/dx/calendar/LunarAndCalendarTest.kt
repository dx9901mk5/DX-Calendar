package com.dx.calendar

import com.dx.calendar.data.model.RecurrenceType
import com.dx.calendar.update.GitHubAutoUpdater
import com.dx.calendar.util.LunarCalendarEngine
import org.junit.Assert.assertEquals
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
}
