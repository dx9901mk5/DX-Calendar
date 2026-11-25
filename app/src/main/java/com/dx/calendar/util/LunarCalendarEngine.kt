package com.dx.calendar.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

data class LunarDateInfo(
    val lunarYear: Int,
    val lunarMonth: Int,
    val lunarDay: Int,
    val isLeapMonth: Boolean,
    val lunarMonthName: String,
    val lunarDayName: String,
    val shortDisplay: String,       // 月曆格下方短標籤：優先順序 國定假日 > 傳統節日 > 節氣 > 初一顯示月份 > 農曆日
    val fullLunarText: String,      // 例如：十月十七
    val ganZhiYear: String,         // 例如：丙午
    val zodiac: String,             // 例如：馬
    val ganZhiMonth: String,        // 例如：己亥
    val ganZhiDay: String,          // 例如：癸卯
    val fullGanZhiHeader: String,   // 例如：丙午年 [馬] 己亥月 癸卯日
    val solarTerm: String?,         // 二十四節氣
    val solarTermPoem: String?,     // 節氣詩詞小語（仿 vivo 節氣卡）
    val festival: String?,          // 台灣節慶或傳統節日
    val isTaiwanHoliday: Boolean,   // 是否為台灣重要節假日
    val yiList: List<String>,       // 黃曆宜
    val jiList: List<String>        // 黃曆忌
)

object LunarCalendarEngine {

    // 1900-2100 標準農曆資料表
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06aa0, 0x1a6c4, 0x0aae0,
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )

    private val TIAN_GAN = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val DI_ZHI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val ZODIAC = arrayOf("鼠", "牛", "虎", "兔", "龍", "蛇", "馬", "羊", "猴", "雞", "狗", "豬")
    private val LUNAR_MONTH_NAMES = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "臘月")
    private val LUNAR_MONTH_STANDARD = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")

    private val SOLAR_TERMS = arrayOf(
        "小寒", "大寒", "立春", "雨水", "驚蟄", "春分",
        "清明", "穀雨", "立夏", "小滿", "芒種", "夏至",
        "小暑", "大暑", "立秋", "處暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    private val TERM_INFO = intArrayOf(
        0, 21208, 42467, 63836, 85337, 107014,
        128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494,
        397447, 419210, 440795, 462224, 483532, 504758
    )

    private val SOLAR_TERM_POEMS = mapOf(
        "立春" to "春回大地，萬象更新",
        "雨水" to "好雨知時節，當春乃發生",
        "驚蟄" to "春雷響，萬物長",
        "春分" to "仲春初四日，春色正中分",
        "清明" to "梨花風起正清明，慎終追遠踏青時",
        "穀雨" to "雨生百穀，春意闌珊",
        "立夏" to "綠樹陰濃夏日長，萬物繁茂",
        "小滿" to "小得盈滿，恰是人生好時節",
        "芒種" to "家家麥飯美，處處菱歌長",
        "夏至" to "綠筠尚含粉，圓荷始散芳",
        "小暑" to "荷風送香氣，竹露滴清響",
        "大暑" to "赤日幾時過，清風無處尋",
        "立秋" to "雲天收夏色，木葉動秋聲",
        "處暑" to "離離暑雲散，嫋嫋涼風起",
        "白露" to "露從今夜白，月是故鄉明",
        "秋分" to "金氣秋分，風清露冷秋期半",
        "寒露" to "裊裊涼風動，淒淒寒露零",
        "霜降" to "霜葉紅於二月花，秋深添衣",
        "立冬" to "細雨生寒未有霜，萬物收藏待春光",
        "小雪" to "晚來天欲雪，能飲一杯無",
        "大雪" to "瑞雪兆豐年，圍爐話家常",
        "冬至" to "冬至陽生春又來，團圓添歲平安夜",
        "小寒" to "小寒連大呂，歡鵲築新巢",
        "大寒" to "大寒歲底慶團圓，靜待春風叩門來",
        "寒衣節" to "歲寒添衣，敬思故人",
        "下元節" to "水官解厄，祈福安康"
    )

    private val YI_JI_PRESETS = listOf(
        Pair(listOf("出行", "會友", "簽約", "祈福", "訂盟"), listOf("動土", "破土", "安葬")),
        Pair(listOf("掃除", "解除", "求醫", "祭祀", "沐浴"), listOf("嫁娶", "遠行", "開市")),
        Pair(listOf("開市", "交易", "立券", "納財", "修造"), listOf("訴訟", "栽種", "詞訟")),
        Pair(listOf("修飾", "塗泥", "整手足甲", "平治道塗"), listOf("祈福", "掘井", "嫁娶")),
        Pair(listOf("嫁娶", "訂盟", "入宅", "安床", "冠笄"), listOf("訴訟", "出行", "開渠")),
        Pair(listOf("祭祀", "祈福", "造作", "捕捉", "納畜"), listOf("移徙", "入宅", "開倉")),
        Pair(listOf("求醫", "治病", "破屋", "壞垣"), listOf("嫁娶", "簽約", "開市", "搬遷")),
        Pair(listOf("安床", "祭祀", "祈福", "納采"), listOf("登高", "行船", "動土")),
        Pair(listOf("開市", "入學", "嫁娶", "移徙", "簽約", "求財"), listOf("訴訟", "爭執")),
        Pair(listOf("納財", "收債", "進人口", "納畜", "修倉"), listOf("安葬", "破土", "針灸")),
        Pair(listOf("嫁娶", "入宅", "開市", "出行", "動土", "祈福"), listOf("安葬", "伐木")),
        Pair(listOf("築堤", "補垣", "塞穴", "靜養", "收納"), listOf("開市", "出行", "求醫"))
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<Long, LunarDateInfo>()

    fun getLunarInfo(date: LocalDate): LunarDateInfo {
        val epochDay = date.toEpochDay()
        cache[epochDay]?.let { return it }

        val info = computeLunarInfo(date)
        if (cache.size > 2500) cache.clear()
        cache[epochDay] = info
        return info
    }

    private fun computeLunarInfo(date: LocalDate): LunarDateInfo {
        val baseDate = LocalDate.of(1900, 1, 31)
        var offset = ChronoUnit.DAYS.between(baseDate, date).toInt()

        var lunarYear = 1900
        var daysInYear = 0
        if (offset >= 0 && date.year <= 2100) {
            while (lunarYear < 2100 && offset > 0) {
                daysInYear = lYearDays(lunarYear)
                if (offset < daysInYear) break
                offset -= daysInYear
                lunarYear++
            }
        } else {
            lunarYear = date.year
            offset = 0
        }

        val leap = leapMonth(lunarYear)
        var isLeap = false
        var lunarMonth = 1
        var daysInMonth = 0

        while (lunarMonth <= 12 && offset > 0) {
            if (leap > 0 && lunarMonth == leap + 1 && !isLeap) {
                --lunarMonth
                isLeap = true
                daysInMonth = leapDays(lunarYear)
            } else {
                daysInMonth = monthDays(lunarYear, lunarMonth)
            }
            if (isLeap && lunarMonth == leap + 1) isLeap = false
            if (offset < daysInMonth) break
            offset -= daysInMonth
            if (isLeap) isLeap = false
            lunarMonth++
        }

        if (offset == 0 && leap > 0 && lunarMonth == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                --lunarMonth
            }
        }
        if (offset < 0) {
            offset += daysInMonth
            --lunarMonth
        }
        val lunarDay = (offset + 1).coerceIn(1, 30)

        val monthStr = (if (isLeap) "閏" else "") + LUNAR_MONTH_STANDARD[(lunarMonth - 1).coerceIn(0, 11)]
        val dayStr = getChinaDayString(lunarDay)
        val fullLunarText = "$monthStr$dayStr"

        // 年干支與生肖（以農曆年為準，與 vivo 日曆一致）
        val yearOffset = lunarYear - 1864
        val ganZhiYear = TIAN_GAN[modPos(yearOffset, 10)] + DI_ZHI[modPos(yearOffset, 12)]
        val zodiac = ZODIAC[modPos(yearOffset, 12)]

        // 二十四節氣與干支月（干支月以節氣「節」為分界）
        val solarTerm = getSolarTerm(date.year, date.monthValue, date.dayOfMonth)
        val gzMonthPair = computeGanZhiMonth(date)
        val ganZhiMonth = gzMonthPair

        // 日干支：已知 1900-01-31 為甲辰日 (index 40 in 60 JiaZi: 甲=0, 辰=4 -> 40)
        val totalDaysFrom1900Jan31 = ChronoUnit.DAYS.between(baseDate, date).toInt()
        val dayCyclical = totalDaysFrom1900Jan31 + 40
        val ganZhiDay = TIAN_GAN[modPos(dayCyclical, 10)] + DI_ZHI[modPos(dayCyclical, 12)]

        val fullGanZhiHeader = "${ganZhiYear}年 [$zodiac] ${ganZhiMonth}月 ${ganZhiDay}日"

        // 節慶與台灣國定假日判斷
        val (festival, isHoliday) = detectFestival(date, lunarYear, lunarMonth, lunarDay, isLeap, solarTerm)

        val shortDisplay = when {
            !festival.isNullOrEmpty() -> festival
            !solarTerm.isNullOrEmpty() -> solarTerm
            lunarDay == 1 -> (if (isLeap) "閏" else "") + LUNAR_MONTH_NAMES[(lunarMonth - 1).coerceIn(0, 11)]
            else -> dayStr
        }

        val poem = SOLAR_TERM_POEMS[festival] ?: SOLAR_TERM_POEMS[solarTerm]

        // 建除十二神宜忌推算
        val zhiDayIdx = modPos(dayCyclical, 12)
        val jianChuIdx = modPos(zhiDayIdx - lunarMonth, 12)
        val (yi, ji) = YI_JI_PRESETS[jianChuIdx]

        return LunarDateInfo(
            lunarYear = lunarYear,
            lunarMonth = lunarMonth,
            lunarDay = lunarDay,
            isLeapMonth = isLeap,
            lunarMonthName = monthStr,
            lunarDayName = dayStr,
            shortDisplay = shortDisplay,
            fullLunarText = fullLunarText,
            ganZhiYear = ganZhiYear,
            zodiac = zodiac,
            ganZhiMonth = ganZhiMonth,
            ganZhiDay = ganZhiDay,
            fullGanZhiHeader = fullGanZhiHeader,
            solarTerm = solarTerm,
            solarTermPoem = poem,
            festival = festival,
            isTaiwanHoliday = isHoliday,
            yiList = yi,
            jiList = ji
        )
    }

    private fun computeGanZhiMonth(date: LocalDate): String {
        val year = date.year
        val month = date.monthValue
        val firstJieDay = getTermDay(year, (month - 1) * 2) // 當月第一個節氣（「節」）
        val adjustedYear: Int
        val monthIndexFromYin: Int // 寅月(正月)=0, 卯月=1 ... 丑月=11

        // 2月立春為寅月起點
        val liChunDay = getTermDay(year, 2)
        val isBeforeLiChun = (month == 1) || (month == 2 && date.dayOfMonth < liChunDay)
        adjustedYear = if (isBeforeLiChun) year - 1 else year

        val rawMonthShift = if (date.dayOfMonth >= firstJieDay) month else month - 1
        // rawMonthShift: 2->寅(0), 3->卯(1)... 12->丑(10), 1->子(9)? Wait:
        // 1月小寒起為丑月(11)，12月大雪起為子月(10)，11月立冬起為亥月(9)！
        // 驗證：2026年(丙午) 11月25日 >= 立冬(11月7日)，rawMonthShift = 11 -> 11 - 2 = 9 (亥)!
        // 丙辛之歲尋庚上：丙年寅月為庚寅(gan=6)，+9 -> (6+9)%10 = 5 (己)！支：(2+9)%12 = 11 (亥) -> 己亥月！完全吻合影片 2026/11/25 的「己亥月」！
        monthIndexFromYin = modPos(rawMonthShift - 2, 12)

        val yearGanIdx = modPos(adjustedYear - 1864, 10)
        // 五虎遁月口訣：甲己之年丙作首(2)，乙庚之歲戊為頭(4)，丙辛必定尋庚起(6)，丁壬壬位順行流(8)，戊癸何方發，甲寅之上好追求(0)
        val firstYinGan = ((yearGanIdx % 5) * 2 + 2) % 10
        val mGan = TIAN_GAN[(firstYinGan + monthIndexFromYin) % 10]
        val mZhi = DI_ZHI[(2 + monthIndexFromYin) % 12]
        return "$mGan$mZhi"
    }

    private fun detectFestival(
        date: LocalDate,
        lunarYear: Int,
        lunarMonth: Int,
        lunarDay: Int,
        isLeap: Boolean,
        solarTerm: String?
    ): Pair<String?, Boolean> {
        // 農曆節日（非閏月）
        if (!isLeap) {
            when {
                lunarMonth == 1 && lunarDay == 1 -> return "春節" to true
                lunarMonth == 1 && lunarDay == 2 -> return "初二" to true
                lunarMonth == 1 && lunarDay == 3 -> return "初三" to true
                lunarMonth == 1 && lunarDay == 15 -> return "元宵節" to false
                lunarMonth == 5 && lunarDay == 5 -> return "端午節" to true
                lunarMonth == 7 && lunarDay == 7 -> return "七夕" to false
                lunarMonth == 7 && lunarDay == 15 -> return "中元節" to false
                lunarMonth == 8 && lunarDay == 15 -> return "中秋節" to true
                lunarMonth == 9 && lunarDay == 9 -> return "重陽節" to false
                lunarMonth == 10 && lunarDay == 1 -> return "寒衣節" to false
                lunarMonth == 10 && lunarDay == 15 -> return "下元節" to false
                lunarMonth == 12 && lunarDay == 8 -> return "臘八節" to false
                lunarMonth == 12 && lunarDay == monthDays(lunarYear, 12) -> return "除夕" to true
            }
        }

        // 清明節既是節氣也是國定假日
        if (solarTerm == "清明") return "清明節" to true

        // 國曆台灣節慶與國際節日
        val m = date.monthValue
        val d = date.dayOfMonth
        when {
            m == 1 && d == 1 -> return "元旦" to true
            m == 2 && d == 14 -> return "情人節" to false
            m == 2 && d == 28 -> return "和平紀念" to true
            m == 3 && d == 8 -> return "婦女節" to false
            m == 3 && d == 29 -> return "青年節" to false
            m == 4 && d == 4 -> return "兒童節" to true
            m == 5 && d == 1 -> return "勞動節" to true
            m == 8 && d == 8 -> return "父親節" to false
            m == 9 && d == 28 -> return "教師節" to true
            m == 10 && d == 10 -> return "國慶日" to true
            m == 10 && d == 25 -> return "台灣光復" to true
            m == 10 && d == 31 -> return "萬聖節" to false
            m == 12 && d == 24 -> return "平安夜" to false
            m == 12 && d == 25 -> return "聖誕節" to true
        }

        // 母親節（5月第2個週日）與感恩節（11月第4個週四）
        if (m == 5 && date.dayOfWeek.value == 7 && d in 8..14) return "母親節" to false
        if (m == 11 && date.dayOfWeek.value == 4 && d in 22..28) return "感恩節" to false

        return null to false
    }

    private fun getSolarTerm(year: Int, month: Int, day: Int): String? {
        if (year !in 1900..2100) return null
        val idx1 = (month - 1) * 2
        val idx2 = idx1 + 1
        if (day == getTermDay(year, idx1)) return SOLAR_TERMS[idx1]
        if (day == getTermDay(year, idx2)) return SOLAR_TERMS[idx2]
        return null
    }

    private fun getTermDay(year: Int, n: Int): Int {
        // 1900-01-06 02:05:00 為基準毫秒推算各節氣日期
        val baseMillis = -2208549300000L
        val millis = 31556925974.7 * (year - 1900) + TERM_INFO[n] * 60000.0 + baseMillis
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = floor(millis).toLong()
        return cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    private fun lYearDays(y: Int): Int {
        val info = LUNAR_INFO[(y - 1900).coerceIn(0, LUNAR_INFO.lastIndex)]
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if ((info and i) != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    private fun leapMonth(y: Int): Int {
        return LUNAR_INFO[(y - 1900).coerceIn(0, LUNAR_INFO.lastIndex)] and 0xf
    }

    private fun leapDays(y: Int): Int {
        return if (leapMonth(y) != 0) {
            if ((LUNAR_INFO[(y - 1900).coerceIn(0, LUNAR_INFO.lastIndex)] and 0x10000) != 0) 30 else 29
        } else 0
    }

    private fun monthDays(y: Int, m: Int): Int {
        val info = LUNAR_INFO[(y - 1900).coerceIn(0, LUNAR_INFO.lastIndex)]
        return if ((info and (0x10000 shr m)) != 0) 30 else 29
    }

    private fun getChinaDayString(day: Int): String {
        if (day == 10) return "初十"
        if (day == 20) return "二十"
        if (day == 30) return "三十"
        val prefix = when (day / 10) {
            0 -> "初"
            1 -> "十"
            2 -> "廿"
            else -> "三"
        }
        val num = arrayOf("十", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        return prefix + num[day % 10]
    }

    private fun modPos(a: Int, b: Int): Int {
        val r = a % b
        return if (r < 0) r + b else r
    }
}
