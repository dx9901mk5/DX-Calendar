package com.dx.calendar.data.model

import com.dx.calendar.data.repository.UnifiedCalendarItem

data class WomensHealthSubtypePreset(
    val id: String,
    val emoji: String,
    val shortLabel: String,
    val defaultTitle: String,
    val isAllDay: Boolean,
    val durationDays: Int = 1,
    val defaultRecurrence: RecurrenceType = RecurrenceType.NONE,
    val recurrenceIntervalWeeks: Int = 1,
    val defaultReminderMinutes: List<Int> = listOf(1440, 0),
    val defaultIsCountdown: Boolean = false,
    val defaultIsRoutine: Boolean = false,
    val defaultEnableMultiCall: Boolean = false,
    val defaultNotes: String = "",
    val trackingSummary: String = ""
)

/**
 * 女性健康內建分類與預設追蹤提醒規則（生理期、排卵期、懷孕週數、產檢、預產期、產後護理、每日保健／服藥、婦科回診）
 */
object WomensHealthPresets {

    const val CATEGORY_ID: Long = 9L
    const val CATEGORY_NAME: String = "女性健康"
    const val CATEGORY_ACCOUNT_LABEL: String = "健康追蹤"
    val CATEGORY_COLOR_ARGB: Int = 0xFFEC407A.toInt()

    val SUBTYPES: List<WomensHealthSubtypePreset> = listOf(
        WomensHealthSubtypePreset(
            id = "MENSTRUATION",
            emoji = "🩸",
            shortLabel = "生理期",
            defaultTitle = "生理期（月經來潮）",
            isAllDay = true,
            durationDays = 5,
            defaultRecurrence = RecurrenceType.CUSTOM_WEEKS,
            recurrenceIntervalWeeks = 4,
            defaultReminderMinutes = listOf(2880, 1440, 0),
            defaultNotes = "生理期追蹤（預設 5 天、每 28 天週期自動追蹤；可記錄經量、不適症狀與備註）",
            trackingSummary = "預設 5 天 · 每 4 週（28 天）循環 · 提前 2 天、1 天、當天提醒"
        ),
        WomensHealthSubtypePreset(
            id = "OVULATION",
            emoji = "✨",
            shortLabel = "排卵期",
            defaultTitle = "排卵期／易孕期追蹤",
            isAllDay = true,
            durationDays = 5,
            defaultRecurrence = RecurrenceType.CUSTOM_WEEKS,
            recurrenceIntervalWeeks = 4,
            defaultReminderMinutes = listOf(1440, 0),
            defaultNotes = "排卵期追蹤（可記錄基礎體溫、排卵試紙結果與身體狀態）",
            trackingSummary = "預設 5 天 · 每 4 週循環 · 提前 1 天與當天提醒"
        ),
        WomensHealthSubtypePreset(
            id = "PREGNANCY_WEEKLY",
            emoji = "🤰",
            shortLabel = "懷孕週數",
            defaultTitle = "懷孕週數與狀態紀錄",
            isAllDay = true,
            durationDays = 1,
            defaultRecurrence = RecurrenceType.WEEKLY,
            recurrenceIntervalWeeks = 1,
            defaultReminderMinutes = listOf(0),
            defaultNotes = "每週懷孕追蹤（可記錄目前週數、體重、胎動與營養補充）",
            trackingSummary = "每週固定追蹤提醒 · 當天準時提示紀錄"
        ),
        WomensHealthSubtypePreset(
            id = "PRENATAL_CHECKUP",
            emoji = "🩺",
            shortLabel = "產檢預約",
            defaultTitle = "產檢預約",
            isAllDay = false,
            durationDays = 1,
            defaultRecurrence = RecurrenceType.CUSTOM_WEEKS,
            recurrenceIntervalWeeks = 4,
            defaultReminderMinutes = listOf(4320, 1440, 60, 0),
            defaultIsCountdown = true,
            defaultNotes = "產檢追蹤（請攜帶媽媽手冊、健保卡與檢驗資料；孕後期可調整為隔 2 週或每週）",
            trackingSummary = "預設隔 4 週＋倒數標記 · 提前 3 天、1 天、1 小時、準時提醒"
        ),
        WomensHealthSubtypePreset(
            id = "DUE_DATE",
            emoji = "👶",
            shortLabel = "預產期",
            defaultTitle = "預產期 (EDD)",
            isAllDay = true,
            durationDays = 1,
            defaultRecurrence = RecurrenceType.NONE,
            defaultReminderMinutes = listOf(20160, 10080, 4320, 1440, 0),
            defaultIsCountdown = true,
            defaultNotes = "預產期倒數追蹤（建議提前 14 天備妥待產包與聯繫醫院確認）",
            trackingSummary = "自動開啟倒數天數 · 提前 14 天、1 週、3 天、1 天與當天提醒"
        ),
        WomensHealthSubtypePreset(
            id = "POSTPARTUM",
            emoji = "💐",
            shortLabel = "孕產／產後護理",
            defaultTitle = "產後護理與回診追蹤",
            isAllDay = true,
            durationDays = 1,
            defaultRecurrence = RecurrenceType.NONE,
            defaultReminderMinutes = listOf(2880, 1440, 60, 0),
            defaultIsCountdown = true,
            defaultNotes = "產後修復、月子照護、傷口／哺乳觀察與產後 6 週回診追蹤",
            trackingSummary = "開啟倒數標記 · 提前 2 天、1 天、1 小時與準時提醒"
        ),
        WomensHealthSubtypePreset(
            id = "PILL_SUPPLEMENT",
            emoji = "💊",
            shortLabel = "事前藥／葉酸",
            defaultTitle = "每日保健／事前藥追蹤",
            isAllDay = false,
            durationDays = 1,
            defaultRecurrence = RecurrenceType.DAILY,
            defaultReminderMinutes = listOf(0, 15),
            defaultIsRoutine = true,
            defaultEnableMultiCall = true,
            defaultNotes = "事前避孕藥、葉酸、鐵劑、鈣片或孕期維他命每日定時追蹤",
            trackingSummary = "每日定時＋連段追擊防漏吃 · 準時與 15 分前提醒"
        ),
        WomensHealthSubtypePreset(
            id = "GYN_CHECKUP",
            emoji = "🏥",
            shortLabel = "婦科回診／篩檢",
            defaultTitle = "婦科檢查／抹片追蹤",
            isAllDay = false,
            durationDays = 1,
            defaultRecurrence = RecurrenceType.YEARLY,
            defaultReminderMinutes = listOf(10080, 2880, 1440, 60),
            defaultIsCountdown = true,
            defaultNotes = "婦科定期檢查、超音波、子宮頸抹片或疫苗接種追蹤",
            trackingSummary = "預設每年例行追蹤＋倒數 · 提前 1 週、2 天、1 天、1 小時提醒"
        )
    )

    fun isWomensHealthCategory(cat: CalendarCategoryEntity): Boolean {
        return cat.id == CATEGORY_ID || cat.name == CATEGORY_NAME
    }

    fun isWomensHealthItem(item: UnifiedCalendarItem): Boolean {
        return item.categoryId == CATEGORY_ID || item.categoryOrAccountLabel == CATEGORY_NAME
    }

    fun createCategoryEntity(): CalendarCategoryEntity = CalendarCategoryEntity(
        id = CATEGORY_ID,
        name = CATEGORY_NAME,
        colorArgb = CATEGORY_COLOR_ARGB,
        accountName = CATEGORY_ACCOUNT_LABEL,
        isBuiltIn = true
    )
}
