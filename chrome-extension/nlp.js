/**
 * DX日曆 Chrome 插件 — 100% 本機離線自然語言與語音解析引擎（0 費用、免 LLM API）
 * 與 Android 端 NaturalLanguageEventParser.kt 規則完全同步！
 */
const DXNlp = (() => {
  const CN_NUM = { "零":0,"〇":0,"一":1,"二":2,"兩":2,"两":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10 };
  const DOW_MAP = { "一":1,"二":2,"三":3,"四":4,"五":5,"六":6,"日":0,"天":0 };

  function parseSmallNum(raw) {
    if (/^\d+$/.test(raw)) return parseInt(raw, 10);
    const s = (raw || "").trim();
    if (!s) return 0;
    if (s === "十") return 10;
    if (s.length === 1) return CN_NUM[s] || 0;
    if (s.startsWith("十")) return 10 + (CN_NUM[s[1]] || 0);
    if (s.endsWith("十")) return (CN_NUM[s[0]] || 0) * 10;
    const i = s.indexOf("十");
    if (i > 0 && i < s.length - 1) return (CN_NUM[s[0]] || 0) * 10 + (CN_NUM[s[i + 1]] || 0);
    return CN_NUM[s[0]] || 0;
  }

  function adjustHour(h, period) {
    const p = (period || "").trim().toLowerCase();
    if (["下午","晚上","傍晚","pm"].includes(p) && h >= 1 && h <= 11) return h + 12;
    if (p === "中午" && h >= 1 && h <= 5) return h + 12;
    if (["凌晨","am"].includes(p) && h === 12) return 0;
    return h;
  }

  function formatDateIso(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  }

  function addDays(base, days) {
    const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + days);
    return d;
  }

  function nextOrSameDow(base, targetJsDow) {
    const diff = (targetJsDow - base.getDay() + 7) % 7;
    return addDays(base, diff);
  }

  function parse(rawInput, baseDate = new Date()) {
    let text = (rawInput || "").trim().replace(/\u3000/g, " ");
    const today = new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate());
    let targetDate = new Date(today);
    let startHour = null, startMin = 0, endHour = null, endMin = 0;
    let recurrenceType = "NONE";
    let reminders = [];
    let isTaskMode = false;
    let isCountdown = false;

    if (/^(待辦|任務|todo|task)[:：\s]?/i.test(text)) {
      isTaskMode = true;
      text = text.replace(/^(待辦|任務|todo|task)[:：\s]?/i, "");
    }
    if (/(倒數|紀念日|生日)/.test(text)) isCountdown = true;

    // 1. 重複規則
    if (/(農曆|农历)?(每月)?(初一[和與跟、]?十五|十五[和與跟、]?初一)/.test(text)) {
      recurrenceType = "LUNAR_FIRST_FIFTEENTH";
      text = text.replace(/(農曆|农历)?(每月)?(初一[和與跟、]?十五|十五[和與跟、]?初一)/g, " ");
    } else if (/(每年農曆|每年农历|農曆每年|农历每年)/.test(text)) {
      recurrenceType = "LUNAR_YEARLY";
      text = text.replace(/(每年農曆|每年农历|農曆每年|农历每年)/g, " ");
    } else if (/(每月農曆|每月农历|農曆每月|农历每月)/.test(text)) {
      recurrenceType = "LUNAR_MONTHLY";
      text = text.replace(/(每月農曆|每月农历|農曆每月|农历每月)/g, " ");
    } else if (/隔\s*(\d+|[二兩两三四五六七八九十]+)\s*[週周]/.test(text)) {
      const m = text.match(/隔\s*(\d+|[二兩两三四五六七八九十]+)\s*[週周]/);
      const n = Math.max(2, Math.min(52, parseSmallNum(m[1])));
      recurrenceType = `EVERY_N_WEEKS:${n}`;
      text = text.replace(m[0], " ");
    } else if (/(每天|每日|天天|daily)/i.test(text)) {
      recurrenceType = "DAILY";
      text = text.replace(/(每天|每日|天天|daily)/gi, " ");
    } else if (/(每[週周]|每星期)([一二三四五六日天])?/.test(text)) {
      const m = text.match(/(每[週周]|每星期)([一二三四五六日天])?/);
      recurrenceType = "WEEKLY";
      if (m[2] && DOW_MAP[m[2]] !== undefined) targetDate = nextOrSameDow(today, DOW_MAP[m[2]]);
      text = text.replace(m[0], " ");
    } else if (/(每月|每個月|monthly)/i.test(text)) {
      recurrenceType = "MONTHLY";
      text = text.replace(/(每月|每個月|monthly)/gi, " ");
    } else if (/(每年|yearly)/i.test(text)) {
      recurrenceType = "YEARLY";
      text = text.replace(/(每年|yearly)/gi, " ");
    }

    // 2. 提醒
    text = text.replace(/(提[前早]|前)\s*(\d+)\s*(分鐘|分|min)/gi, (_, __, num) => {
      reminders.push(parseInt(num, 10)); return " ";
    });
    text = text.replace(/(提[前早]|前)\s*(\d+)\s*(小時|個小時|時|hr|hour)/gi, (_, __, num) => {
      reminders.push(parseInt(num, 10) * 60); return " ";
    });
    text = text.replace(/(提[前早]|前)\s*(\d+)\s*(天|日|day)/gi, (_, __, num) => {
      reminders.push(parseInt(num, 10) * 1440); return " ";
    });

    // 3. 日期
    const fullM = text.match(/(\d{4})\s*[年/\-.]\s*(\d{1,2})\s*[月/\-.]\s*(\d{1,2})\s*[日號号]?/);
    const mdM = text.match(/(\d{1,2})\s*[月/]\s*(\d{1,2})\s*[日號号]?/);
    const inDaysM = text.match(/(\d+|[一二兩两三四五六七八九十]+)\s*天[後后]/);
    const nextDowM = text.match(/(下個?[週周]|下星期)([一二三四五六日天])/);
    const thisDowM = text.match(/(這個?[週周]|本[週周]|這星期|[週周]|星期)([一二三四五六日天])/);

    if (fullM) {
      targetDate = new Date(parseInt(fullM[1], 10), parseInt(fullM[2], 10) - 1, parseInt(fullM[3], 10));
      text = text.replace(fullM[0], " ");
    } else if (mdM) {
      const mo = parseInt(mdM[1], 10), d = parseInt(mdM[2], 10);
      let cand = new Date(today.getFullYear(), mo - 1, d);
      if (cand < addDays(today, -1)) cand = new Date(today.getFullYear() + 1, mo - 1, d);
      targetDate = cand;
      text = text.replace(mdM[0], " ");
    } else if (inDaysM) {
      targetDate = addDays(today, Math.max(1, parseSmallNum(inDaysM[1])));
      text = text.replace(inDaysM[0], " ");
    } else if (nextDowM) {
      const jsDow = DOW_MAP[nextDowM[2]];
      const mondayOffset = ((today.getDay() + 6) % 7);
      const nextMonday = addDays(today, 7 - mondayOffset);
      const isoOffset = jsDow === 0 ? 6 : jsDow - 1;
      targetDate = addDays(nextMonday, isoOffset);
      text = text.replace(nextDowM[0], " ");
    } else if (thisDowM) {
      targetDate = nextOrSameDow(today, DOW_MAP[thisDowM[2]]);
      text = text.replace(thisDowM[0], " ");
    } else if (/大[後后]天/.test(text)) {
      targetDate = addDays(today, 3);
      text = text.replace(/大[後后]天/g, " ");
    } else if (/[後后]天/.test(text)) {
      targetDate = addDays(today, 2);
      text = text.replace(/[後后]天/g, " ");
    } else if (/(明天|明日|\btomorrow\b)/i.test(text)) {
      targetDate = addDays(today, 1);
      text = text.replace(/(明天|明日|\btomorrow\b)/gi, " ");
    } else if (/(今天|今日|\btoday\b)/i.test(text)) {
      targetDate = today;
      text = text.replace(/(今天|今日|\btoday\b)/gi, " ");
    }

    // 4. 時間
    const rangeM = text.match(/(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\s*(\d{1,2})[:：](\d{2})\s*[-~～到至]\s*(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\s*(\d{1,2})[:：](\d{2})/i);
    const singleM = text.match(/(上午|早上|中午|下午|晚上|傍晚|凌晨|am|pm)?\s*(\d{1,2})[:：](\d{2})\s*(am|pm)?/i);
    const cnTimeM = text.match(/(上午|早上|清晨|中午|下午|晚上|傍晚|凌晨)?\s*(\d{1,2}|[零〇一二兩两三四五六七八九十]+)\s*[點点時时]\s*(半|(\d{1,2}|[零〇一二兩两三四五六七八九十]+)分?)?/);

    if (rangeM) {
      startHour = Math.min(23, adjustHour(parseInt(rangeM[2], 10), rangeM[1]));
      startMin = Math.min(59, parseInt(rangeM[3], 10));
      endHour = Math.min(23, adjustHour(parseInt(rangeM[5], 10), rangeM[4] || rangeM[1]));
      endMin = Math.min(59, parseInt(rangeM[6], 10));
      text = text.replace(rangeM[0], " ");
    } else if (singleM) {
      startHour = Math.min(23, adjustHour(parseInt(singleM[2], 10), singleM[1] || singleM[4]));
      startMin = Math.min(59, parseInt(singleM[3], 10));
      endHour = Math.min(23, startHour + 1);
      endMin = startMin;
      text = text.replace(singleM[0], " ");
    } else if (cnTimeM) {
      const rh = parseSmallNum(cnTimeM[2]);
      if (rh >= 0 && rh <= 23) {
        startHour = Math.min(23, adjustHour(rh, cnTimeM[1]));
        startMin = cnTimeM[3] === "半" ? 30 : (cnTimeM[4] ? Math.min(59, parseSmallNum(cnTimeM[4])) : 0);
        endHour = Math.min(23, startHour + 1);
        endMin = startMin;
        text = text.replace(cnTimeM[0], " ");
      }
    }

    const isAllDay = startHour === null;
    const sH = isAllDay ? 9 : startHour;
    const sM = isAllDay ? 0 : startMin;
    const eH = isAllDay ? 10 : (endHour ?? Math.min(23, sH + 1));
    const eM = isAllDay ? 0 : endMin;
    const title = text.replace(/\s+/g, " ").replace(/^[\s,，、。:：]+|[\s,，、。:：]+$/g, "") || rawInput.trim();
    const finalReminders = reminders.length ? Array.from(new Set([...reminders, 0])).sort((a, b) => a - b) : [0, 15];

    return {
      title,
      dateIso: formatDateIso(targetDate),
      startTime: `${String(sH).padStart(2, "0")}:${String(sM).padStart(2, "0")}`,
      endTime: `${String(eH).padStart(2, "0")}:${String(eM).padStart(2, "0")}`,
      isAllDay,
      recurrenceType,
      reminderMinutes: finalReminders,
      isTaskMode,
      isCountdown
    };
  }

  return { parse };
})();
