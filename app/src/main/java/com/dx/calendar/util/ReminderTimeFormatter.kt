package com.dx.calendar.util

/**
 * 提醒提前時間（幾天幾小時幾分鐘前）計算與格式化工具。
 * 支援標準預設值與任意自訂組合（如：2 天 3 小時 15 分鐘前）。
 */
object ReminderTimeFormatter {

    const val MAX_CUSTOM_DAYS = 365
    const val MINUTES_PER_HOUR = 60
    const val MINUTES_PER_DAY = 1440

    fun computeOffsetMinutes(days: Int, hours: Int, minutes: Int): Int {
        val d = days.coerceIn(0, MAX_CUSTOM_DAYS)
        val h = hours.coerceIn(0, 23)
        val m = minutes.coerceIn(0, 59)
        return d * MINUTES_PER_DAY + h * MINUTES_PER_HOUR + m
    }

    fun decomposeOffsetMinutes(totalMinutes: Int): Triple<Int, Int, Int> {
        val safe = totalMinutes.coerceAtLeast(0)
        val days = safe / MINUTES_PER_DAY
        val remAfterDays = safe % MINUTES_PER_DAY
        val hours = remAfterDays / MINUTES_PER_HOUR
        val mins = remAfterDays % MINUTES_PER_HOUR
        return Triple(days, hours, mins)
    }

    fun formatDuration(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        if (safe == 0) return "0 分鐘"
        if (safe == 7 * MINUTES_PER_DAY) return "1 週"
        val (days, hours, mins) = decomposeOffsetMinutes(safe)
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days 天")
        if (hours > 0) parts.add("$hours 小時")
        if (mins > 0) parts.add("$mins 分鐘")
        return parts.joinToString(" ")
    }

    fun formatBeforeLabel(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        if (safe == 0) return "活動時間"
        return "${formatDuration(safe)}前"
    }

    fun formatCompactChipLabel(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        if (safe == 0) return "準時"
        if (safe == 7 * MINUTES_PER_DAY) return "1 週前"
        val (days, hours, mins) = decomposeOffsetMinutes(safe)
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}天")
        if (hours > 0) parts.add("${hours}時")
        if (mins > 0) parts.add("${mins}分")
        return "${parts.joinToString("")}前"
    }

    fun formatCountdownSubtitle(totalMinutes: Int, noun: String = "活動"): String {
        val safe = totalMinutes.coerceAtLeast(0)
        if (safe == 0) return "${noun}現在開始"
        return "${formatDuration(safe)}後開始"
    }
}
