package com.dx.calendar.util

import com.dx.calendar.data.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class ParsedQuickEvent(
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isAllDay: Boolean,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceIntervalWeeks: Int = 1,
    val reminderMinutes: List<Int> = listOf(0, 15),
    val isTaskMode: Boolean = false,
    val isCountdown: Boolean = false,
    val location: String = "",
    val url: String = "",
    val notes: String = ""
)

/**
 * 100% 本機離線自然語言＋語音事件解析引擎（免 LLM API、免額外 OAuth、零延遲零費用）。
 * 範例：
 * - 「明天下午3點半跟王經理開會 提前15分」
 * - 「下週二 14:00-15:30 牙醫檢查」
 * - 「每週五晚上8點健身」
 * - 「農曆初一十五拜拜」
 * - 「12/25 聖誕聚餐 19:00」
 * - 「倒數 12/31 跨年煙火」
 */
object NaturalLanguageEventParser {

    private val CN_NUM_MAP = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '兩' to 2, '两' to 2,
        '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
    )

    fun parse(rawInput: String, today: LocalDate = LocalDate.now()): ParsedQuickEvent {
        val originalTrimmed = rawInput.trim()
        var text = originalTrimmed.replace("　", " ")
        if (text.isBlank()) {
            return ParsedQuickEvent(
                title = "新行程",
                date = today,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                isAllDay = false
            )
        }

        val isMultiLine = text.contains('\n')
        var extractedUrl = ""
        var extractedLocation = ""

        // -2. 擷取 LINE 訊息或通知中的網址（如 Google Maps / 活動報名連結）
        val urlRegex = Regex("https?://[^\\s\\u3000<>\"'）)】」』]+", RegexOption.IGNORE_CASE)
        urlRegex.find(text)?.let { m ->
            extractedUrl = m.value.trimEnd('.', ',', '，', '。', ')', '）')
        }
        text = text.replace(urlRegex, " ")

        // -1. 擷取結構化或口語「地點」欄位（支援多行 LINE 通知如「地點：鼎泰豐信義店」或「📍台北101」）
        val locLineRegex = Regex("(?m)^\\s*(?:地點|地点|地址|集合地點|集合|場地|位置|📍|Location)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE)
        val locLineMatch = locLineRegex.find(text)
        if (locLineMatch != null) {
            extractedLocation = locLineMatch.groupValues[1].trim()
            text = text.replace(locLineMatch.value, " ")
        } else {
            val inlineLocRegex = Regex("(?:地點|地点|地址|在)\\s*[:：]\\s*([^\\n，,。]+)")
            inlineLocRegex.find(text)?.let { m ->
                extractedLocation = m.groupValues[1].trim()
                text = text.replaceFirst(m.value, " ")
            }
        }

        // 先擷取多行通知中的「主題／活動／標題」或第一行有意義文字作為標題候選
        val rawLines = originalTrimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val explicitTitleLine = Regex("(?m)^\\s*(?:主題|活動|活動名稱|標題|行程|事件|Event|Title)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()

        // 清除結構化欄位前綴（日期：、時間：、備註：）以便精準擷取數值與乾淨標題
        val notesLineMatch = Regex("(?m)^\\s*(?:備註|备注|說明|说明|Notes?)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)
        text = text.replace(Regex("(?m)^\\s*(?:日期|時間|时间|主題|活動|活動名稱|標題|行程|事件|備註|备注|說明|说明)\\s*[:：]\\s*"), " ")

        var date = today
        var startTime: LocalTime? = null
        var endTime: LocalTime? = null
        var recurrence = RecurrenceType.NONE
        var intervalWeeks = 1
        val reminders = mutableSetOf<Int>()
        var isTask = false
        var isCountdown = false

        // 0. 識別待辦或倒數關鍵字
        if (Regex("^(待辦|任務|todo|task)[:：\\s]?", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            isTask = true
            text = text.replaceFirst(Regex("^(待辦|任務|todo|task)[:：\\s]?", RegexOption.IGNORE_CASE), "")
        }
        if (Regex("(倒數|紀念日|生日)").containsMatchIn(text)) {
            isCountdown = true
        }

        // 1. 解析重複規則
        when {
            Regex("(農曆|农历)?(初一(和|與|跟|、)?十五|十五(和|與|跟|、)?初一)").containsMatchIn(text) -> {
                recurrence = RecurrenceType.LUNAR_FIRST_FIFTEENTH
                text = text.replace(Regex("(農曆|农历)?(每月)?(初一(和|與|跟|、)?十五|十五(和|與|跟|、)?初一)"), " ")
            }
            Regex("(每年農曆|每年农历|農曆每年|农历每年)").containsMatchIn(text) -> {
                recurrence = RecurrenceType.LUNAR_YEARLY
                text = text.replace(Regex("(每年農曆|每年农历|農曆每年|农历每年)"), " ")
            }
            Regex("(每月農曆|每月农历|農曆每月|农历每月)").containsMatchIn(text) -> {
                recurrence = RecurrenceType.LUNAR_MONTHLY
                text = text.replace(Regex("(每月農曆|每月农历|農曆每月|农历每月)"), " ")
            }
            Regex("隔\\s*(\\d+|[二兩两三四五六七八九十]+)\\s*[週周]").containsMatchIn(text) -> {
                val m = Regex("隔\\s*(\\d+|[二兩两三四五六七八九十]+)\\s*[週周]").find(text)
                val n = parseSmallNumber(m?.groupValues?.get(1).orEmpty()).coerceIn(2, 52)
                recurrence = RecurrenceType.CUSTOM_WEEKS
                intervalWeeks = n
                text = text.replace(m!!.value, " ")
            }
            Regex("(每天|每日|天天|every\\s*day|daily)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.DAILY
                text = text.replace(Regex("(每天|每日|天天|every\\s*day|daily)", RegexOption.IGNORE_CASE), " ")
            }
            Regex("(每個工作日|每個平日|平日|工作日|weekdays)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.WEEKDAYS
                text = text.replace(Regex("(每個工作日|每個平日|平日|工作日|weekdays)", RegexOption.IGNORE_CASE), " ")
            }
            Regex("(每[週周]|每星期)([一二三四五六日天])?").containsMatchIn(text) -> {
                val m = Regex("(每[週周]|每星期)([一二三四五六日天])?").find(text)!!
                recurrence = RecurrenceType.WEEKLY
                val dowChar = m.groupValues.getOrNull(2).orEmpty()
                if (dowChar.isNotEmpty()) {
                    parseChineseDow(dowChar[0])?.let { targetDow ->
                        date = nextOrSameDow(today, targetDow)
                    }
                }
                text = text.replace(m.value, " ")
            }
            Regex("(每月|每個月|monthly)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.MONTHLY
                text = text.replace(Regex("(每月|每個月|monthly)", RegexOption.IGNORE_CASE), " ")
            }
            Regex("(每年|yearly|annually)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                recurrence = RecurrenceType.YEARLY
                text = text.replace(Regex("(每年|yearly|annually)", RegexOption.IGNORE_CASE), " ")
            }
        }

        // 2. 解析提早提醒（如 提前15分、前1小時、前2天）
        val remMinRegex = Regex("(提[前早]|前)\\s*(\\d+)\\s*(分鐘|分|min)", RegexOption.IGNORE_CASE)
        remMinRegex.findAll(text).forEach { m ->
            m.groupValues[2].toIntOrNull()?.let { reminders.add(it.coerceIn(0, 10080)) }
        }
        text = text.replace(remMinRegex, " ")

        val remHourRegex = Regex("(提[前早]|前)\\s*(\\d+)\\s*(小時|個小時|時|hr|hour)", RegexOption.IGNORE_CASE)
        remHourRegex.findAll(text).forEach { m ->
            m.groupValues[2].toIntOrNull()?.let { reminders.add((it * 60).coerceIn(0, 10080)) }
        }
        text = text.replace(remHourRegex, " ")

        val remDayRegex = Regex("(提[前早]|前)\\s*(\\d+)\\s*(天|日|day)", RegexOption.IGNORE_CASE)
        remDayRegex.findAll(text).forEach { m ->
            m.groupValues[2].toIntOrNull()?.let { reminders.add((it * 1440).coerceIn(0, 20160)) }
        }
        text = text.replace(remDayRegex, " ")

        // 3. 解析日期
        val fullDateRegex = Regex("(\\d{4})\\s*[年/\\-.]\\s*(\\d{1,2})\\s*[月/\\-.]\\s*(\\d{1,2})\\s*[日號号]?")
        val mdDateRegex = Regex("(\\d{1,2})\\s*[月/]\\s*(\\d{1,2})\\s*[日號号]?")
        val inDaysRegex = Regex("(\\d+|[一二兩两三四五六七八九十]+)\\s*天[後后]")
        val nextWeekDowRegex = Regex("(下個?[週周]|下星期|下個?禮拜|下個?礼拜)([一二三四五六日天])")
        val thisWeekDowRegex = Regex("(這個?[週周]|本[週周]|這星期|這禮拜|本禮拜|[週周]|星期|禮拜|礼拜)([一二三四五六日天])")

        when {
            fullDateRegex.containsMatchIn(text) -> {
                val m = fullDateRegex.find(text)!!
                val y = m.groupValues[1].toIntOrNull()
                val mo = m.groupValues[2].toIntOrNull()
                val d = m.groupValues[3].toIntOrNull()
                if (y != null && mo != null && d != null) {
                    runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.let { date = it }
                }
                text = text.replaceFirst(m.value, " ")
            }
            mdDateRegex.containsMatchIn(text) -> {
                val m = mdDateRegex.find(text)!!
                val mo = m.groupValues[1].toIntOrNull()
                val d = m.groupValues[2].toIntOrNull()
                if (mo != null && d != null && mo in 1..12 && d in 1..31) {
                    val candidate = runCatching { LocalDate.of(today.year, mo, d) }.getOrNull()
                    if (candidate != null) {
                        date = if (candidate.isBefore(today.minusDays(1))) candidate.plusYears(1) else candidate
                    }
                }
                text = text.replaceFirst(m.value, " ")
            }
            inDaysRegex.containsMatchIn(text) -> {
                val m = inDaysRegex.find(text)!!
                val days = parseSmallNumber(m.groupValues[1]).coerceIn(1, 365)
                date = today.plusDays(days.toLong())
                text = text.replaceFirst(m.value, " ")
            }
            nextWeekDowRegex.containsMatchIn(text) -> {
                val m = nextWeekDowRegex.find(text)!!
                parseChineseDow(m.groupValues[2][0])?.let { targetDow ->
                    val mondayNextWeek = today.with(DayOfWeek.MONDAY).plusWeeks(1)
                    date = mondayNextWeek.plusDays((targetDow.value - 1).toLong())
                }
                text = text.replaceFirst(m.value, " ")
            }
            thisWeekDowRegex.containsMatchIn(text) -> {
                val m = thisWeekDowRegex.find(text)!!
                parseChineseDow(m.groupValues[2][0])?.let { targetDow ->
                    date = nextOrSameDow(today, targetDow)
                }
                text = text.replaceFirst(m.value, " ")
            }
            text.contains("大後天") || text.contains("大后天") -> {
                date = today.plusDays(3)
                text = text.replace(Regex("大[後后]天"), " ")
            }
            text.contains("後天") || text.contains("后天") -> {
                date = today.plusDays(2)
                text = text.replace(Regex("[後后]天"), " ")
            }
            text.contains("明天") || text.contains("明日") || Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                date = today.plusDays(1)
                text = text.replace(Regex("(明天|明日|\\btomorrow\\b)", RegexOption.IGNORE_CASE), " ")
            }
            text.contains("今天") || text.contains("今日") || Regex("\\btoday\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> {
                date = today
                text = text.replace(Regex("(今天|今日|\\btoday\\b)", RegexOption.IGNORE_CASE), " ")
            }
        }

        // 4. 解析時間區間（如 14:00-15:30 或 下午3點到5點）與單一時間
        val rangeColonRegex = Regex("(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\\s*(\\d{1,2})[:：](\\d{2})\\s*[-~～到至]\\s*(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\\s*(\\d{1,2})[:：](\\d{2})", RegexOption.IGNORE_CASE)
        val singleColonRegex = Regex("(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\\s*(\\d{1,2})[:：](\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val cnTimeRegex = Regex("(上午|早上|清晨|中午|下午|晚上|傍晚|凌晨)?\\s*(\\d{1,2}|[零〇一二兩两三四五六七八九十]+)\\s*[點点時时]\\s*(半|(\\d{1,2}|[零〇一二兩两三四五六七八九十]+)分?)?")

        when {
            rangeColonRegex.containsMatchIn(text) -> {
                val m = rangeColonRegex.find(text)!!
                val h1 = adjustHourForPeriod(m.groupValues[2].toIntOrNull() ?: 9, m.groupValues[1])
                val min1 = (m.groupValues[3].toIntOrNull() ?: 0).coerceIn(0, 59)
                val p2 = m.groupValues[4].ifBlank { m.groupValues[1] }
                val h2 = adjustHourForPeriod(m.groupValues[5].toIntOrNull() ?: (h1 + 1), p2)
                val min2 = (m.groupValues[6].toIntOrNull() ?: 0).coerceIn(0, 59)
                startTime = LocalTime.of(h1.coerceIn(0, 23), min1)
                endTime = LocalTime.of(h2.coerceIn(0, 23), min2)
                text = text.replaceFirst(m.value, " ")
            }
            singleColonRegex.containsMatchIn(text) -> {
                val m = singleColonRegex.find(text)!!
                val period = m.groupValues[1].ifBlank { m.groupValues[4] }
                val h = adjustHourForPeriod(m.groupValues[2].toIntOrNull() ?: 9, period)
                val min = (m.groupValues[3].toIntOrNull() ?: 0).coerceIn(0, 59)
                startTime = LocalTime.of(h.coerceIn(0, 23), min)
                endTime = startTime.plusHours(1)
                text = text.replaceFirst(m.value, " ")
            }
            cnTimeRegex.containsMatchIn(text) -> {
                val m = cnTimeRegex.find(text)!!
                val period = m.groupValues[1]
                val rawH = parseSmallNumber(m.groupValues[2])
                if (rawH in 0..23) {
                    val h = adjustHourForPeriod(rawH, period)
                    val minPart = m.groupValues[3]
                    val min = when {
                        minPart == "半" -> 30
                        m.groupValues[4].isNotBlank() -> parseSmallNumber(m.groupValues[4]).coerceIn(0, 59)
                        else -> 0
                    }
                    startTime = LocalTime.of(h.coerceIn(0, 23), min)
                    endTime = startTime.plusHours(1)
                    text = text.replaceFirst(m.value, " ")
                }
            }
        }

        val isAllDay = startTime == null
        val finalStart = startTime ?: LocalTime.of(9, 0)
        val finalEnd = endTime ?: finalStart.plusHours(1)

        // 清除像 "(六)"、"（週六）" 這類殘留星期括號，並決定乾淨標題
        val strippedRemainder = text
            .replace(Regex("[（(]\\s*(?:[週周]|星期|禮拜)?[一二三四五六日天]\\s*[）)]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '，', ',', '、', '。', ':', '：', '-', '—')

        val cleanedTitle = when {
            !explicitTitleLine.isNullOrBlank() -> explicitTitleLine.trim('【', '】', '[', ']', ' ')
            isMultiLine -> {
                val firstHeadline = rawLines.firstOrNull { line ->
                    val trimmedLine = line.replace(urlRegex, "").trim()
                    trimmedLine.isNotBlank() &&
                        !Regex("^(?:日期|時間|时间|地點|地点|地址|備註|备注|說明|说明|📍)\\s*[:：]?", RegexOption.IGNORE_CASE).containsMatchIn(trimmedLine) &&
                        !Regex("^\\d{4}[/\\-.年]\\d{1,2}[/\\-.月]\\d{1,2}").containsMatchIn(trimmedLine) &&
                        !Regex("^\\d{1,2}[:：]\\d{2}").containsMatchIn(trimmedLine)
                }?.trim('【', '】', '[', ']', ' ')
                firstHeadline?.takeIf { it.length <= 48 } ?: strippedRemainder.take(48).ifBlank { originalTrimmed.lines().first().take(48) }
            }
            else -> strippedRemainder.ifBlank { originalTrimmed }
        }

        val finalNotes = when {
            isMultiLine || extractedLocation.isNotBlank() || extractedUrl.isNotBlank() || notesLineMatch != null -> originalTrimmed
            else -> ""
        }

        return ParsedQuickEvent(
            title = cleanedTitle,
            date = date,
            startTime = finalStart,
            endTime = if (finalEnd.isBefore(finalStart)) finalStart.plusHours(1) else finalEnd,
            isAllDay = isAllDay,
            recurrenceType = recurrence,
            recurrenceIntervalWeeks = intervalWeeks,
            reminderMinutes = if (reminders.isEmpty()) listOf(0, 15) else (reminders + 0).sorted(),
            isTaskMode = isTask,
            isCountdown = isCountdown,
            location = extractedLocation,
            url = extractedUrl,
            notes = finalNotes
        )
    }

    private fun adjustHourForPeriod(hour: Int, period: String): Int {
        val p = period.trim().lowercase()
        return when {
            p in listOf("下午", "晚上", "傍晚", "pm") && hour in 1..11 -> hour + 12
            p == "中午" && hour in 1..5 -> hour + 12
            p in listOf("凌晨", "am") && hour == 12 -> 0
            else -> hour
        }
    }

    private fun parseChineseDow(ch: Char): DayOfWeek? = when (ch) {
        '一' -> DayOfWeek.MONDAY
        '二' -> DayOfWeek.TUESDAY
        '三' -> DayOfWeek.WEDNESDAY
        '四' -> DayOfWeek.THURSDAY
        '五' -> DayOfWeek.FRIDAY
        '六' -> DayOfWeek.SATURDAY
        '日', '天' -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun nextOrSameDow(today: LocalDate, target: DayOfWeek): LocalDate {
        val diff = (target.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(diff.toLong())
    }

    private fun parseSmallNumber(raw: String): Int {
        raw.toIntOrNull()?.let { return it }
        val s = raw.trim()
        if (s.isEmpty()) return 0
        if (s == "十") return 10
        if (s.length == 1) return CN_NUM_MAP[s[0]] ?: 0
        if (s.startsWith("十")) return 10 + (CN_NUM_MAP[s[1]] ?: 0)
        if (s.endsWith("十")) return (CN_NUM_MAP[s[0]] ?: 0) * 10
        val idx = s.indexOf('十')
        if (idx > 0 && idx < s.length - 1) {
            return (CN_NUM_MAP[s[0]] ?: 0) * 10 + (CN_NUM_MAP[s[idx + 1]] ?: 0)
        }
        return CN_NUM_MAP[s[0]] ?: 0
    }
}
