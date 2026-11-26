/**
 * DX日曆 Chrome 插件共用核心邏輯（Popup / Side Panel / 獨立桌面視窗 / Background Service Worker 共用）
 * 100% 對齊 Android DX日曆 v2.0.0 DxFullBackupPayload Schema、農曆重複規則、倒數歲數與 ICS 匯出入。
 */
const DXCore = (() => {
  const DEFAULT_TEMPLATES = [
    { id: "tpl_day_shift", name: "早班", startHour: 7, startMinute: 0, durationMinutes: 480, isAllDay: false, colorArgb: -13730510, location: "", notes: "", reminderMinutes: [0, 30], enableMultiCall: false },
    { id: "tpl_evening_shift", name: "小夜班", startHour: 15, startMinute: 0, durationMinutes: 480, isAllDay: false, colorArgb: -15108398, location: "", notes: "", reminderMinutes: [0, 30], enableMultiCall: false },
    { id: "tpl_night_shift", name: "大夜班", startHour: 23, startMinute: 0, durationMinutes: 480, isAllDay: false, colorArgb: -9823334, location: "", notes: "", reminderMinutes: [0, 30], enableMultiCall: false },
    { id: "tpl_day_off", name: "休假", startHour: 9, startMinute: 0, durationMinutes: 60, isAllDay: true, colorArgb: -3631570, location: "", notes: "", reminderMinutes: [], enableMultiCall: false },
    { id: "tpl_workout", name: "健身運動", startHour: 19, startMinute: 30, durationMinutes: 60, isAllDay: false, colorArgb: -1683200, location: "", notes: "", reminderMinutes: [0, 15], enableMultiCall: false }
  ];

  function toIso(d) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function parseIso(iso) {
    const [y, m, d] = String(iso).split("-").map(Number);
    return new Date(y, m - 1, d);
  }

  function epochDayToIso(epochDay) {
    const d = new Date(epochDay * 86400000);
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
  }

  function isoToEpochDay(iso) {
    const [y, m, d] = String(iso).split("-").map(Number);
    return Math.floor(Date.UTC(y, m - 1, d) / 86400000);
  }

  function argbToCss(argb) {
    if (typeof argb !== "number") return "#C8962E";
    const hex = (argb >>> 0).toString(16).padStart(8, "0");
    return "#" + hex.slice(2);
  }

  function cssToArgb(hex) {
    const clean = String(hex || "#C8962E").replace("#", "").trim();
    if (!/^[0-9a-fA-F]{6}$/.test(clean)) return -3631570;
    return (0xFF000000 | parseInt(clean, 16)) | 0;
  }

  function normalizeState(raw) {
    const src = raw && typeof raw === "object" ? raw : {};
    return {
      deviceName: (typeof src.deviceName === "string" && src.deviceName.trim()) ? src.deviceName.trim() : "Chrome-Desktop",
      dualTimeZone: typeof src.dualTimeZone === "string" ? src.dualTimeZone : "",
      categories: Array.isArray(src.categories) ? src.categories : [],
      events: Array.isArray(src.events) ? src.events : [],
      tasks: Array.isArray(src.tasks) ? src.tasks : [],
      routineCheckIns: Array.isArray(src.routineCheckIns) ? src.routineCheckIns : [],
      externalOverrides: Array.isArray(src.externalOverrides) ? src.externalOverrides : [],
      templates: (Array.isArray(src.templates) && src.templates.length) ? src.templates : DEFAULT_TEMPLATES.map((t) => ({ ...t })),
      richMeta: (src.richMeta && typeof src.richMeta === "object" && !Array.isArray(src.richMeta)) ? src.richMeta : {}
    };
  }

  function matchesRecurrence(baseDate, candDate, recRaw, extraDatesJson, recurrenceEndEpochMillis) {
    const candIso = toIso(candDate);
    if (extraDatesJson && extraDatesJson !== "[]") {
      try {
        const extras = JSON.parse(extraDatesJson);
        if (Array.isArray(extras) && extras.includes(candIso)) return true;
      } catch (_) {}
    }

    const baseUtc = Date.UTC(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate());
    const candUtc = Date.UTC(candDate.getFullYear(), candDate.getMonth(), candDate.getDate());
    if (candUtc < baseUtc) return false;
    if (recurrenceEndEpochMillis && candUtc > recurrenceEndEpochMillis) return false;
    if (candUtc === baseUtc) return true;

    const rec = (recRaw || "NONE").trim();
    if (rec === "NONE") return false;
    if (rec === "DAILY") return true;
    const diffDays = Math.round((candUtc - baseUtc) / 86400000);
    if (rec === "WEEKDAYS") return candDate.getDay() >= 1 && candDate.getDay() <= 5;
    if (rec === "WEEKLY") return diffDays % 7 === 0;
    if (rec === "BIWEEKLY") return diffDays % 14 === 0;
    if (rec.startsWith("EVERY_N_WEEKS:")) {
      const n = Math.max(2, parseInt(rec.split(":")[1], 10) || 2);
      return diffDays % (n * 7) === 0;
    }
    if (rec === "MONTHLY") return candDate.getDate() === baseDate.getDate();
    if (rec === "YEARLY") return candDate.getMonth() === baseDate.getMonth() && candDate.getDate() === baseDate.getDate();

    if (typeof DXLunar !== "undefined") {
      if (rec === "LUNAR_MONTHLY") {
        const bL = DXLunar.getLunarInfo(baseDate.getFullYear(), baseDate.getMonth() + 1, baseDate.getDate());
        const cL = DXLunar.getLunarInfo(candDate.getFullYear(), candDate.getMonth() + 1, candDate.getDate());
        return cL.lunarDay === bL.lunarDay && !cL.isLeap;
      }
      if (rec === "LUNAR_FIRST_FIFTEENTH") {
        const cL = DXLunar.getLunarInfo(candDate.getFullYear(), candDate.getMonth() + 1, candDate.getDate());
        return (cL.lunarDay === 1 || cL.lunarDay === 15) && !cL.isLeap;
      }
      if (rec === "LUNAR_YEARLY") {
        const bL = DXLunar.getLunarInfo(baseDate.getFullYear(), baseDate.getMonth() + 1, baseDate.getDate());
        const cL = DXLunar.getLunarInfo(candDate.getFullYear(), candDate.getMonth() + 1, candDate.getDate());
        return cL.lunarMonth === bL.lunarMonth && cL.lunarDay === bL.lunarDay && !cL.isLeap;
      }
    }
    return false;
  }

  function formatCountdownBadge(targetIso, meta, todayIso) {
    if (!meta) return "";
    const parts = [];
    if (meta.isCountdown) {
      const diff = isoToEpochDay(targetIso) - isoToEpochDay(todayIso || toIso(new Date()));
      parts.push(diff === 0 ? "🎯 就是今天" : (diff > 0 ? `⏳ 還有 ${diff} 天` : `📌 已過 ${-diff} 天`));
    }
    if (meta.anniversaryStartYear != null && Number.isFinite(Number(meta.anniversaryStartYear))) {
      const targetYear = parseInt(String(targetIso).slice(0, 4), 10);
      const years = targetYear - Number(meta.anniversaryStartYear);
      if (years > 0) {
        parts.push(meta.isBirthday ? `🎂 ${years} 歲` : `💍 第 ${years} 週年`);
      }
    }
    return parts.join(" · ");
  }

  function buildBackupPayload(state) {
    const s = normalizeState(state);
    return {
      schemaVersion: 2,
      appId: "com.dx.calendar",
      deviceName: s.deviceName,
      exportedAtMillis: Date.now(),
      categories: s.categories,
      events: s.events,
      tasks: s.tasks,
      routineCheckIns: s.routineCheckIns,
      externalOverrides: s.externalOverrides,
      templates: s.templates,
      richMeta: s.richMeta
    };
  }

  function mergeByKey(existingList, incomingList, keyFn) {
    const map = new Map();
    for (const item of existingList || []) map.set(keyFn(item), item);
    for (const item of incomingList || []) map.set(keyFn(item), item);
    return Array.from(map.values());
  }

  function mergeRichMeta(existingMap, incomingMap) {
    const out = { ...(existingMap || {}) };
    for (const [k, v] of Object.entries(incomingMap || {})) {
      if (!out[k]) {
        out[k] = v;
      } else {
        const mergedComments = mergeByKey(
          out[k].sharedComments || [],
          v.sharedComments || [],
          (c) => c.id || `${c.createdAtMillis}_${c.author}_${c.content}`
        );
        const mergedAtt = Array.from(new Set([...(out[k].attachmentUris || []), ...(v.attachmentUris || [])]));
        out[k] = { ...out[k], ...v, sharedComments: mergedComments, attachmentUris: mergedAtt };
      }
    }
    return out;
  }

  /**
   * 無損匯入 Android / Chrome `.dxbackup.json`：
   * - mode === "overwrite": 以備份檔取代全部資料（仍完整保留 Android categories/routineCheckIns/externalOverrides/隱私紀錄）
   * - mode === "merge": 智慧合併雙方行程、待辦、範本、倒數與協作留言，不遺失電腦端或手機端的新增項目
   */
  function importBackupPayload(currentState, payload, mode = "merge") {
    if (!payload || typeof payload !== "object") {
      throw new Error("無效的 .dxbackup.json 備份結構");
    }
    const cur = normalizeState(currentState);
    if (mode === "overwrite") {
      return {
        ...cur,
        categories: Array.isArray(payload.categories) ? payload.categories : cur.categories,
        events: Array.isArray(payload.events) ? payload.events : cur.events,
        tasks: Array.isArray(payload.tasks) ? payload.tasks : cur.tasks,
        routineCheckIns: Array.isArray(payload.routineCheckIns) ? payload.routineCheckIns : cur.routineCheckIns,
        externalOverrides: Array.isArray(payload.externalOverrides) ? payload.externalOverrides : cur.externalOverrides,
        templates: (Array.isArray(payload.templates) && payload.templates.length) ? payload.templates : cur.templates,
        richMeta: (payload.richMeta && typeof payload.richMeta === "object") ? payload.richMeta : cur.richMeta
      };
    }

    return {
      ...cur,
      categories: mergeByKey(cur.categories, payload.categories, (c) => String(c.id)),
      events: mergeByKey(cur.events, payload.events, (e) => String(e.id)),
      tasks: mergeByKey(cur.tasks, payload.tasks, (t) => String(t.id)),
      routineCheckIns: mergeByKey(cur.routineCheckIns, payload.routineCheckIns, (r) => `${r.targetType}:${r.targetId}:${r.epochDay}`),
      externalOverrides: mergeByKey(cur.externalOverrides, payload.externalOverrides, (o) => String(o.externalKey)),
      templates: (Array.isArray(payload.templates) && payload.templates.length)
        ? mergeByKey(cur.templates, payload.templates, (t) => String(t.id))
        : cur.templates,
      richMeta: mergeRichMeta(cur.richMeta, payload.richMeta)
    };
  }

  function escapeIcsText(s) {
    return String(s || "").replace(/\\/g, "\\\\").replace(/\n/g, "\\n").replace(/,/g, "\\,").replace(/;/g, "\\;");
  }

  function unescapeIcsText(s) {
    return String(s || "").replace(/\\n/gi, "\n").replace(/\\,/g, ",").replace(/\\;/g, ";").replace(/\\\\/g, "\\");
  }

  function formatIcsUtc(ms) {
    const d = new Date(ms);
    return `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, "0")}${String(d.getUTCDate()).padStart(2, "0")}T${String(d.getUTCHours()).padStart(2, "0")}${String(d.getUTCMinutes()).padStart(2, "0")}${String(d.getUTCSeconds()).padStart(2, "0")}Z`;
  }

  function formatIcsDateLocal(ms) {
    const d = new Date(ms);
    return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
  }

  function exportToIcs(events) {
    const lines = [
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      "PRODID:-//DX-Calendar//Chrome Extension//ZH"
    ];
    for (const ev of (events || [])) {
      if (ev.isPrivacy) continue;
      lines.push("BEGIN:VEVENT");
      lines.push(`UID:dx-${ev.id}@dx.calendar`);
      lines.push(`DTSTAMP:${formatIcsUtc(ev.createdAt || Date.now())}`);
      if (ev.isAllDay) {
        lines.push(`DTSTART;VALUE=DATE:${formatIcsDateLocal(ev.startEpochMillis)}`);
        const nextDay = new Date(ev.startEpochMillis + 86400000);
        lines.push(`DTEND;VALUE=DATE:${formatIcsDateLocal(nextDay.getTime())}`);
      } else {
        lines.push(`DTSTART:${formatIcsUtc(ev.startEpochMillis)}`);
        lines.push(`DTEND:${formatIcsUtc(Math.max(ev.endEpochMillis || 0, ev.startEpochMillis + 1800000))}`);
      }
      lines.push(`SUMMARY:${escapeIcsText(ev.title)}`);
      if (ev.location) lines.push(`LOCATION:${escapeIcsText(ev.location)}`);
      if (ev.notes) lines.push(`DESCRIPTION:${escapeIcsText(ev.notes)}`);
      const r = ev.recurrenceType || "NONE";
      if (r === "DAILY") lines.push("RRULE:FREQ=DAILY");
      else if (r === "WEEKDAYS") lines.push("RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");
      else if (r === "WEEKLY") lines.push("RRULE:FREQ=WEEKLY");
      else if (r === "BIWEEKLY") lines.push("RRULE:FREQ=WEEKLY;INTERVAL=2");
      else if (r.startsWith("EVERY_N_WEEKS:")) lines.push(`RRULE:FREQ=WEEKLY;INTERVAL=${r.split(":")[1]}`);
      else if (r === "MONTHLY") lines.push("RRULE:FREQ=MONTHLY");
      else if (r === "YEARLY") lines.push("RRULE:FREQ=YEARLY");
      lines.push("END:VEVENT");
    }
    lines.push("END:VCALENDAR");
    return lines.join("\r\n");
  }

  function parseIcsDateVal(val) {
    const v = String(val || "").trim();
    if (/^\d{8}$/.test(v)) {
      const y = Number(v.slice(0, 4)), m = Number(v.slice(4, 6)) - 1, d = Number(v.slice(6, 8));
      return { ms: new Date(y, m, d, 0, 0, 0).getTime(), isAllDay: true };
    }
    const m = v.match(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z)?$/);
    if (m) {
      const ms = m[7]
        ? Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])
        : new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]).getTime();
      return { ms, isAllDay: false };
    }
    return null;
  }

  function importFromIcs(icsText) {
    const unfolded = String(icsText || "").replace(/\r\n[ \t]/g, "").replace(/\n[ \t]/g, "");
    const blocks = unfolded.split(/BEGIN:VEVENT/i).slice(1);
    const out = [];
    let seq = 0;
    for (const blk of blocks) {
      const body = blk.split(/END:VEVENT/i)[0] || "";
      const fields = {};
      for (const line of body.split(/\r?\n/)) {
        const idx = line.indexOf(":");
        if (idx <= 0) continue;
        const key = line.slice(0, idx).split(";")[0].toUpperCase();
        fields[key] = line.slice(idx + 1).trim();
      }
      if (!fields.SUMMARY || !fields.DTSTART) continue;
      const startParsed = parseIcsDateVal(fields.DTSTART);
      if (!startParsed) continue;
      const endParsed = fields.DTEND ? parseIcsDateVal(fields.DTEND) : null;
      let rec = "NONE";
      const rr = (fields.RRULE || "").toUpperCase();
      if (rr.includes("FREQ=DAILY")) rec = "DAILY";
      else if (rr.includes("FREQ=WEEKLY")) {
        const intM = rr.match(/INTERVAL=(\d+)/);
        const interval = intM ? parseInt(intM[1], 10) : 1;
        if (rr.includes("BYDAY=MO,TU,WE,TH,FR")) rec = "WEEKDAYS";
        else if (interval === 2) rec = "BIWEEKLY";
        else if (interval > 2) rec = `EVERY_N_WEEKS:${interval}`;
        else rec = "WEEKLY";
      } else if (rr.includes("FREQ=MONTHLY")) rec = "MONTHLY";
      else if (rr.includes("FREQ=YEARLY")) rec = "YEARLY";

      const endMs = endParsed && endParsed.ms > startParsed.ms
        ? (startParsed.isAllDay ? endParsed.ms - 60000 : endParsed.ms)
        : (startParsed.isAllDay ? startParsed.ms + 86340000 : startParsed.ms + 3600000);

      out.push({
        id: Date.now() + (++seq),
        categoryId: 1,
        title: unescapeIcsText(fields.SUMMARY),
        location: unescapeIcsText(fields.LOCATION || ""),
        url: "",
        notes: unescapeIcsText(fields.DESCRIPTION || ""),
        startEpochMillis: startParsed.ms,
        endEpochMillis: endMs,
        isAllDay: startParsed.isAllDay,
        extraDatesJson: "[]",
        recurrenceType: rec,
        isRoutine: false,
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPaused: false,
        isPrivacy: false,
        reminderMinutesJson: "[0,15]",
        enableMultiCall: false,
        multiCallCount: 3,
        multiCallIntervalMinutes: 3,
        useAlarmStream: false,
        syncSystemAlarm: false,
        customColorArgb: -3631570,
        createdAt: Date.now()
      });
    }
    return out;
  }

  return {
    DEFAULT_TEMPLATES,
    toIso,
    parseIso,
    epochDayToIso,
    isoToEpochDay,
    argbToCss,
    cssToArgb,
    normalizeState,
    matchesRecurrence,
    formatCountdownBadge,
    buildBackupPayload,
    importBackupPayload,
    exportToIcs,
    importFromIcs
  };
})();
