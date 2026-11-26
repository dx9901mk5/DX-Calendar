package com.dx.calendar.data.model

enum class BrandStyle(
    val displayName: String,
    val subtitle: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val cardCornerDp: Int
) {
    GOLD_WHITE(
        displayName = "金白風格 (預設)",
        subtitle = "預設基底：純淨珍珠白底、18K 香檳曜金點綴與溫潤懸浮卡片",
        primaryHex = 0xFFC6922C,
        secondaryHex = 0xFFE0B458,
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
    LIGHT("淺色白底 (預設)"),
    DARK("深色模式"),
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
