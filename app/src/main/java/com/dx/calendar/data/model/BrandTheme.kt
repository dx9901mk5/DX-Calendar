package com.dx.calendar.data.model

enum class BrandStyle(
    val displayName: String,
    val subtitle: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val cardCornerDp: Int
) {
    GOLD_WHITE(
        displayName = "金白風格 (明亮主題預設)",
        subtitle = "明亮預設：珍珠白底＋18K 香檳金（跟隨系統切暗色時自動轉為金色灰黑底）",
        primaryHex = 0xFFC6922C,
        secondaryHex = 0xFFE0B458,
        cardCornerDp = 22
    ),
    GOLD_DARK(
        displayName = "金色灰黑風格 (暗色主題預設)",
        subtitle = "暗色預設：沉穩質感灰黑底＋璀璨香檳曜金（跟隨系統切明亮時自動轉為金白底）",
        primaryHex = 0xFFD9A83E,
        secondaryHex = 0xFFF0C96B,
        cardCornerDp = 22
    ),
    VIVO(
        displayName = "仿V 風格",
        subtitle = "雙色年標、懸浮雙膠囊與東方節氣美學",
        primaryHex = 0xFFFF5238,
        secondaryHex = 0xFFFF8A65,
        cardCornerDp = 22
    ),
    OPPO(
        displayName = "仿O 風格",
        subtitle = "水生美學：舒緩翡翠青綠、輕盈留白與溫潤圓角",
        primaryHex = 0xFF00B884,
        secondaryHex = 0xFF2EC4B6,
        cardCornerDp = 20
    ),
    XIAOMI(
        displayName = "仿M 風格",
        subtitle = "科技湛藍、純淨幾何排版與精準資訊流",
        primaryHex = 0xFF0D84FF,
        secondaryHex = 0xFF53A8FF,
        cardCornerDp = 18
    ),
    GOOGLE(
        displayName = "仿G 風格",
        subtitle = "滿版高密度網格、多彩膠囊與待辦事項融合",
        primaryHex = 0xFF1A73E8,
        secondaryHex = 0xFF8AB4F8,
        cardCornerDp = 14
    ),
    SAMSUNG(
        displayName = "仿S 風格",
        subtitle = "單手友善：大圓角分組區塊、沉穩靛藍與週末紅藍雙色",
        primaryHex = 0xFF3E69FF,
        secondaryHex = 0xFF6F8EFF,
        cardCornerDp = 26
    )
}

enum class DarkThemeMode(val label: String) {
    SYSTEM("跟隨手機主題 (預設)"),
    LIGHT("固定明亮白底"),
    DARK("固定暗色灰黑底")
}

enum class CalendarViewMode(val label: String) {
    YEAR("年"),
    MONTH("月"),
    WEEK("週"),
    DAY("日"),
    AGENDA("時間表")
}

enum class MainSection(val label: String) {
    CALENDAR("日曆"),
    TASKS("待辦事項"),
    ROUTINES("例行中心"),
    PRIVACY("隱私日曆")
}

enum class RecurrenceType(val label: String) {
    NONE("不重複"),
    DAILY("每天"),
    WEEKDAYS("每個工作日 (週一至週五)"),
    WEEKLY("每週"),
    BIWEEKLY("每兩週"),
    CUSTOM_WEEKS("隔 N 週重複"),
    MONTHLY("每月"),
    YEARLY("每年 (國曆)"),
    LUNAR_YEARLY("每年 (農曆生日/紀念)")
}

object RecurrenceCodec {
    private const val PREFIX_EVERY_N_WEEKS = "EVERY_N_WEEKS:"

    fun encode(type: RecurrenceType, intervalWeeks: Int = 1): String {
        return if (type == RecurrenceType.CUSTOM_WEEKS) {
            "$PREFIX_EVERY_N_WEEKS${intervalWeeks.coerceIn(2, 52)}"
        } else {
            type.name
        }
    }

    fun decode(raw: String?): Pair<RecurrenceType, Int> {
        val text = raw?.trim().orEmpty()
        if (text.startsWith(PREFIX_EVERY_N_WEEKS)) {
            val n = text.removePrefix(PREFIX_EVERY_N_WEEKS).toIntOrNull()?.coerceIn(2, 52) ?: 2
            return RecurrenceType.CUSTOM_WEEKS to n
        }
        val type = runCatching { RecurrenceType.valueOf(text) }.getOrDefault(RecurrenceType.NONE)
        val interval = if (type == RecurrenceType.BIWEEKLY) 2 else 1
        return type to interval
    }

    fun formatLabel(type: RecurrenceType, intervalWeeks: Int = 1): String {
        return if (type == RecurrenceType.CUSTOM_WEEKS) {
            "隔 ${intervalWeeks.coerceIn(2, 52)} 週重複"
        } else {
            type.label
        }
    }

    fun toRRule(type: RecurrenceType, intervalWeeks: Int = 1): String? {
        return when (type) {
            RecurrenceType.DAILY -> "FREQ=DAILY"
            RecurrenceType.WEEKDAYS -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            RecurrenceType.WEEKLY -> "FREQ=WEEKLY"
            RecurrenceType.BIWEEKLY -> "FREQ=WEEKLY;INTERVAL=2"
            RecurrenceType.CUSTOM_WEEKS -> "FREQ=WEEKLY;INTERVAL=${intervalWeeks.coerceIn(2, 52)}"
            RecurrenceType.MONTHLY -> "FREQ=MONTHLY"
            RecurrenceType.YEARLY -> "FREQ=YEARLY"
            else -> null
        }
    }
}
