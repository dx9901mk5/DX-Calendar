package com.dx.calendar.data.model

enum class BrandStyle(
    val displayName: String,
    val subtitle: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val cardCornerDp: Int
) {
    VIVO(
        displayName = "類 vivo 風格",
        subtitle = "預設基底：黑紅橘雙色年標、懸浮雙膠囊與東方節氣美學",
        primaryHex = 0xFFFF5238,
        secondaryHex = 0xFFFF8A65,
        cardCornerDp = 22
    ),
    OPPO(
        displayName = "類 OPPO 風格",
        subtitle = "水生美學：舒緩翡翠青綠、輕盈留白與溫潤圓角",
        primaryHex = 0xFF00B884,
        secondaryHex = 0xFF2EC4B6,
        cardCornerDp = 20
    ),
    XIAOMI(
        displayName = "類小米風格",
        subtitle = "澎湃科技藍、純淨幾何排版與精準資訊流",
        primaryHex = 0xFF0D84FF,
        secondaryHex = 0xFF53A8FF,
        cardCornerDp = 18
    ),
    GOOGLE(
        displayName = "類 Google 風格",
        subtitle = "Material You：滿版高密度網格、多彩膠囊與 Tasks 融合",
        primaryHex = 0xFF1A73E8,
        secondaryHex = 0xFF8AB4F8,
        cardCornerDp = 14
    ),
    SAMSUNG(
        displayName = "類三星風格",
        subtitle = "單手友善：大圓角分組區塊、沉穩靛藍與週末紅藍雙色",
        primaryHex = 0xFF3E69FF,
        secondaryHex = 0xFF6F8EFF,
        cardCornerDp = 26
    )
}

enum class DarkThemeMode(val label: String) {
    DARK("深色模式 (預設)"),
    LIGHT("淺色模式"),
    SYSTEM("跟隨系統")
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
    MONTHLY("每月"),
    YEARLY("每年 (國曆)"),
    LUNAR_YEARLY("每年 (農曆生日/紀念)")
}
