/**
 * DX日曆 Chrome 插件 — 1900–2100 精準農曆、二十四節氣與台灣節日引擎
 * （與 Android 端 LunarCalendarEngine.kt 採相同標準資料表）
 */
const DXLunar = (() => {
  const LUNAR_INFO = [
    0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
    0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
    0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
    0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
    0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
    0x06ca0,0x0b550,0x15355,0x04da0,0x0a5b0,0x14573,0x052b0,0x0a9a8,0x0e950,0x06aa0,
    0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
    0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,
    0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
    0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x05ac0,0x0ab60,0x096d5,0x092e0,
    0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
    0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
    0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
    0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
    0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0,
    0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06aa0,0x1a6c4,0x0aae0,
    0x092e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,
    0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,
    0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160,
    0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252,
    0x0d520
  ];

  const TIAN_GAN = ["甲","乙","丙","丁","戊","己","庚","辛","壬","癸"];
  const DI_ZHI = ["子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"];
  const ZODIAC = ["鼠","牛","虎","兔","龍","蛇","馬","羊","猴","雞","狗","豬"];
  const MONTH_NAMES = ["正月","二月","三月","四月","五月","六月","七月","八月","九月","十月","冬月","臘月"];
  const DAY_NAMES = [
    "","初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
    "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
    "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"
  ];
  const SOLAR_TERMS = [
    "小寒","大寒","立春","雨水","驚蟄","春分",
    "清明","穀雨","立夏","小滿","芒種","夏至",
    "小暑","大暑","立秋","處暑","白露","秋分",
    "寒露","霜降","立冬","小雪","大雪","冬至"
  ];
  const TERM_INFO = [
    0,21208,42467,63836,85337,107014,128867,150921,173149,195551,218072,240693,
    263343,285989,308563,331033,353350,375494,397447,419210,440795,462224,483532,504758
  ];

  const SOLAR_HOLIDAYS = {
    "1-1": "元旦",
    "2-28": "和平紀念日",
    "4-4": "兒童節",
    "5-1": "勞動節",
    "10-10": "國慶日",
    "12-25": "行憲紀念日"
  };

  const LUNAR_FESTIVALS = {
    "1-1": "春節",
    "1-15": "元宵節",
    "5-5": "端午節",
    "7-7": "七夕",
    "7-15": "中元節",
    "8-15": "中秋節",
    "9-9": "重陽節",
    "12-8": "臘八"
  };

  function leapMonth(y) { return LUNAR_INFO[y - 1900] & 0xf; }
  function leapDays(y) { return leapMonth(y) ? ((LUNAR_INFO[y - 1900] & 0x10000) ? 30 : 29) : 0; }
  function monthDays(y, m) { return (LUNAR_INFO[y - 1900] & (0x10000 >> m)) ? 30 : 29; }
  function yearDays(y) {
    let sum = 348;
    for (let i = 0x8000; i > 0x8; i >>= 1) sum += (LUNAR_INFO[y - 1900] & i) ? 1 : 0;
    return sum + leapDays(y);
  }

  function getSolarTerm(y, m, d) {
    const baseUtc = Date.UTC(1900, 0, 6, 2, 5);
    const idx1 = (m - 1) * 2;
    const idx2 = idx1 + 1;
    const d1 = new Date(baseUtc + (31556925974.7 * (y - 1900) + TERM_INFO[idx1] * 60000));
    if (d1.getUTCDate() === d) return SOLAR_TERMS[idx1];
    const d2 = new Date(baseUtc + (31556925974.7 * (y - 1900) + TERM_INFO[idx2] * 60000));
    if (d2.getUTCDate() === d) return SOLAR_TERMS[idx2];
    return null;
  }

  function getLunarInfo(year, month, day) {
    if (year < 1900 || year > 2100) {
      return { shortDisplay: "", fullText: "", ganZhi: "", isHoliday: false, lunarMonth: 1, lunarDay: 1, isLeap: false };
    }
    const baseMs = Date.UTC(1900, 0, 31);
    const targetMs = Date.UTC(year, month - 1, day);
    let offset = Math.round((targetMs - baseMs) / 86400000);

    let lYear = 1900;
    while (lYear < 2101 && offset > 0) {
      const days = yearDays(lYear);
      if (offset < days) break;
      offset -= days;
      lYear++;
    }

    const leap = leapMonth(lYear);
    let isLeap = false;
    let lMonth = 1;
    while (lMonth <= 12 && offset > 0) {
      let days;
      if (leap > 0 && lMonth === leap + 1 && !isLeap) {
        --lMonth;
        isLeap = true;
        days = leapDays(lYear);
      } else {
        days = monthDays(lYear, lMonth);
      }
      if (offset < days) break;
      offset -= days;
      if (isLeap && lMonth === leap) isLeap = false;
      lMonth++;
    }
    const lDay = offset + 1;
    const monthLabel = (isLeap ? "閏" : "") + (MONTH_NAMES[lMonth - 1] || "");
    const dayLabel = DAY_NAMES[lDay] || "";
    const term = getSolarTerm(year, month, day);
    const solarHol = SOLAR_HOLIDAYS[`${month}-${day}`] || null;
    const lunarFest = (!isLeap && LUNAR_FESTIVALS[`${lMonth}-${lDay}`]) || null;
    const isHoliday = Boolean(solarHol || lunarFest || term === "清明");

    const shortDisplay = solarHol || lunarFest || term || (lDay === 1 ? monthLabel : dayLabel);
    const ganZhiYear = TIAN_GAN[(lYear - 4) % 10] + DI_ZHI[(lYear - 4) % 12];
    const zodiac = ZODIAC[(lYear - 4) % 12];

    return {
      lunarYear: lYear,
      lunarMonth: lMonth,
      lunarDay: lDay,
      isLeap,
      shortDisplay,
      fullText: `${monthLabel}${dayLabel}`,
      ganZhi: `${ganZhiYear}年 [${zodiac}]`,
      solarTerm: term,
      festival: solarHol || lunarFest,
      isHoliday
    };
  }

  return { getLunarInfo };
})();
