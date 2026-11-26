/**
 * DX日曆 Chrome 插件主控制器（Popup + Side Panel 雙形態）
 * 與 Android DX日曆 v2.0.0 共用完全相容的 DxFullBackupPayload JSON 備份結構！
 */
(() => {
  const STORAGE_KEY = "dx_calendar_state_v2";
  const THEME_KEY = "dx_theme_mode";

  const DEFAULT_TEMPLATES = [
    { id: "tpl_day_shift", name: "早班", startHour: 7, startMinute: 0, durationMinutes: 480, isAllDay: false, colorArgb: -13730510 },
    { id: "tpl_evening_shift", name: "小夜班", startHour: 15, startMinute: 0, durationMinutes: 480, isAllDay: false, colorArgb: -15108398 },
    { id: "tpl_night_shift", name: "大夜班", startHour: 23, startMinute: 0, durationMinutes: 480, isAllDay: false, colorArgb: -9823334 },
    { id: "tpl_day_off", name: "休假", startHour: 9, startMinute: 0, durationMinutes: 60, isAllDay: true, colorArgb: -3631570 },
    { id: "tpl_workout", name: "健身運動", startHour: 19, startMinute: 30, durationMinutes: 60, isAllDay: false, colorArgb: -1683200 }
  ];

  let state = {
    deviceName: "Chrome-Desktop",
    events: [],
    tasks: [],
    templates: [...DEFAULT_TEMPLATES],
    richMeta: {}
  };

  let weatherByDate = {};
  const now = new Date();
  let viewYear = now.getFullYear();
  let viewMonth = now.getMonth() + 1; // 1..12
  let selectedIso = toIso(now);
  let activeStampTemplate = null;

  // Pomodoro state
  let pomoTotalSec = 25 * 60;
  let pomoLeftSec = 25 * 60;
  let pomoTimer = null;

  if (new URLSearchParams(location.search).get("mode") === "sidepanel") {
    document.body.classList.add("sidepanel-mode");
  }

  function toIso(d) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function parseIso(iso) {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, m - 1, d);
  }

  function epochDayToIso(epochDay) {
    const d = new Date(epochDay * 86400000);
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
  }

  function isoToEpochDay(iso) {
    const [y, m, d] = iso.split("-").map(Number);
    return Math.floor(Date.UTC(y, m - 1, d) / 86400000);
  }

  function argbToCss(argb) {
    if (typeof argb !== "number") return "#C8962E";
    const hex = (argb >>> 0).toString(16).padStart(8, "0");
    return "#" + hex.slice(2);
  }

  let toastTimer = null;
  function showToast(msg) {
    const el = document.getElementById("toast");
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.add("hidden"), 2400);
  }

  async function loadState() {
    const res = await chrome.storage.local.get([STORAGE_KEY, THEME_KEY]);
    if (res[STORAGE_KEY]) {
      state = {
        deviceName: res[STORAGE_KEY].deviceName || "Chrome-Desktop",
        events: Array.isArray(res[STORAGE_KEY].events) ? res[STORAGE_KEY].events : [],
        tasks: Array.isArray(res[STORAGE_KEY].tasks) ? res[STORAGE_KEY].tasks : [],
        templates: (Array.isArray(res[STORAGE_KEY].templates) && res[STORAGE_KEY].templates.length)
          ? res[STORAGE_KEY].templates : [...DEFAULT_TEMPLATES],
        richMeta: res[STORAGE_KEY].richMeta || {}
      };
    }
    if (res[THEME_KEY]) {
      document.documentElement.setAttribute("data-theme", res[THEME_KEY]);
    }
    document.getElementById("deviceNameInput").value = state.deviceName;
  }

  async function saveState() {
    await chrome.storage.local.set({ [STORAGE_KEY]: state });
  }

  function matchesRecurrence(baseDate, candDate, recRaw) {
    if (candDate < baseDate) return false;
    const baseIso = toIso(baseDate);
    const candIso = toIso(candDate);
    if (baseIso === candIso) return true;
    const rec = (recRaw || "NONE").trim();
    if (rec === "NONE") return false;
    if (rec === "DAILY") return true;
    const diffDays = Math.round((Date.UTC(candDate.getFullYear(), candDate.getMonth(), candDate.getDate()) -
      Date.UTC(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate())) / 86400000);
    if (rec === "WEEKDAYS") return candDate.getDay() >= 1 && candDate.getDay() <= 5;
    if (rec === "WEEKLY") return diffDays % 7 === 0;
    if (rec === "BIWEEKLY") return diffDays % 14 === 0;
    if (rec.startsWith("EVERY_N_WEEKS:")) {
      const n = Math.max(2, parseInt(rec.split(":")[1], 10) || 2);
      return diffDays % (n * 7) === 0;
    }
    if (rec === "MONTHLY") return candDate.getDate() === baseDate.getDate();
    if (rec === "YEARLY") return candDate.getMonth() === baseDate.getMonth() && candDate.getDate() === baseDate.getDate();
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
    return false;
  }

  function getItemsForDate(iso) {
    const candDate = parseIso(iso);
    const list = [];
    for (const ev of state.events) {
      if (ev.isPrivacy || ev.isPaused) continue;
      const startD = new Date(ev.startEpochMillis);
      const baseDate = new Date(startD.getFullYear(), startD.getMonth(), startD.getDate());
      if (matchesRecurrence(baseDate, candDate, ev.recurrenceType)) {
        list.push({
          kind: "LOCAL_EVENT",
          rawId: ev.id,
          title: ev.title,
          isAllDay: Boolean(ev.isAllDay),
          timeLabel: ev.isAllDay ? "全天" : `${String(startD.getHours()).padStart(2, "0")}:${String(startD.getMinutes()).padStart(2, "0")}`,
          color: argbToCss(ev.customColorArgb || -3631570),
          location: ev.location || "",
          recurrenceType: ev.recurrenceType || "NONE"
        });
      }
    }
    const todayIso = toIso(new Date());
    for (const tk of state.tasks) {
      if (tk.isPrivacy || tk.isPaused) continue;
      const tkIso = tk.dueEpochDay != null ? epochDayToIso(tk.dueEpochDay) : (!tk.isCompleted ? todayIso : null);
      if (tkIso === iso) {
        const tm = tk.dueMinuteOfDay != null
          ? `${String(Math.floor(tk.dueMinuteOfDay / 60)).padStart(2, "0")}:${String(tk.dueMinuteOfDay % 60).padStart(2, "0")}`
          : "待辦";
        list.push({
          kind: "TASK",
          rawId: tk.id,
          title: (tk.isCompleted ? "✓ " : "○ ") + tk.title,
          isAllDay: tk.dueMinuteOfDay == null,
          timeLabel: tm,
          color: argbToCss(tk.colorArgb || -12417548),
          location: "",
          isCompleted: Boolean(tk.isCompleted)
        });
      }
    }
    return list;
  }

  function renderAll() {
    renderHeader();
    renderMonthGrid();
    renderSelectedDay();
    renderTasks();
    renderTemplatesAndCountdowns();
  }

  function renderHeader() {
    const el = document.getElementById("ymTitle");
    const yStr = String(viewYear);
    el.innerHTML = `${yStr.slice(0, 2)}<span class="accent">${yStr.slice(2)}</span>/<span class="accent">${String(viewMonth).padStart(2, "0")}</span>`;

    const banner = document.getElementById("stampBanner");
    if (activeStampTemplate) {
      banner.classList.remove("hidden");
      document.getElementById("stampText").textContent = `🏷️ 連點排班中：點擊日期直接排入「${activeStampTemplate.name}」`;
    } else {
      banner.classList.add("hidden");
    }
  }

  function renderMonthGrid() {
    const grid = document.getElementById("monthGrid");
    grid.innerHTML = "";
    const first = new Date(viewYear, viewMonth - 1, 1);
    const startOffset = first.getDay();
    const todayIso = toIso(new Date());

    for (let i = 0; i < 42; i++) {
      const cellDate = new Date(viewYear, viewMonth - 1, 1 - startOffset + i);
      const iso = toIso(cellDate);
      const inMonth = (cellDate.getMonth() + 1) === viewMonth;
      const isToday = iso === todayIso;
      const isSelected = iso === selectedIso;
      const lunar = DXLunar.getLunarInfo(cellDate.getFullYear(), cellDate.getMonth() + 1, cellDate.getDate());
      const weather = weatherByDate[iso];
      const dayItems = getItemsForDate(iso);

      const cell = document.createElement("div");
      cell.className = `day-cell${inMonth ? "" : " out-month"}${isToday ? " today" : ""}${isSelected ? " selected" : ""}`;

      if (weather) {
        const wEl = document.createElement("span");
        wEl.className = "day-weather";
        wEl.textContent = weather.icon;
        wEl.title = `${weather.label} ${weather.minC}°~${weather.maxC}°`;
        cell.appendChild(wEl);
      }

      const pill = document.createElement("div");
      pill.className = "day-pill";
      const num = document.createElement("div");
      num.className = "day-num";
      num.textContent = String(cellDate.getDate());
      const lun = document.createElement("div");
      lun.className = `day-lunar${lunar.isHoliday ? " holiday" : ""}`;
      lun.textContent = lunar.shortDisplay;
      pill.appendChild(num);
      pill.appendChild(lun);
      if (isToday) {
        const bar = document.createElement("div");
        bar.className = "today-bar";
        pill.appendChild(bar);
      }
      cell.appendChild(pill);

      if (dayItems.length > 0) {
        const dots = document.createElement("div");
        dots.className = "day-dots";
        dayItems.slice(0, 4).forEach((it) => {
          const d = document.createElement("span");
          d.className = "day-dot";
          d.style.background = it.color;
          dots.appendChild(d);
        });
        cell.appendChild(dots);
      }

      cell.addEventListener("click", async () => {
        selectedIso = iso;
        if (!inMonth) {
          viewYear = cellDate.getFullYear();
          viewMonth = cellDate.getMonth() + 1;
        }
        if (activeStampTemplate) {
          await stampTemplateOnIso(activeStampTemplate, iso);
        }
        renderAll();
      });

      grid.appendChild(cell);
    }
  }

  function renderSelectedDay() {
    const d = parseIso(selectedIso);
    const lunar = DXLunar.getLunarInfo(d.getFullYear(), d.getMonth() + 1, d.getDate());
    const weather = weatherByDate[selectedIso];
    document.getElementById("selectedDateTitle").textContent = `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · 農曆 ${lunar.fullText}`;
    document.getElementById("selectedDateSub").textContent =
      lunar.ganZhi + (weather ? `  ·  ${weather.icon} ${weather.label} ${weather.minC}°~${weather.maxC}°` : "");

    const container = document.getElementById("dayEventsList");
    container.innerHTML = "";
    const items = getItemsForDate(selectedIso);
    if (!items.length) {
      container.innerHTML = `<div class="day-header-sub" style="padding:6px 0;">這天沒有安排行程，可於上方一句話快速新增。</div>`;
      return;
    }

    for (const item of items) {
      const row = document.createElement("div");
      row.className = "event-row";

      const bar = document.createElement("div");
      bar.className = "event-bar";
      bar.style.background = item.color;

      const body = document.createElement("div");
      body.className = "event-body";
      const titleEl = document.createElement("div");
      titleEl.className = "event-title";
      titleEl.textContent = item.title;

      const metaKey = `${item.kind}:${item.rawId}`;
      const meta = state.richMeta[metaKey];
      let badge = "";
      if (meta && meta.isCountdown) {
        const diff = isoToEpochDay(selectedIso) - isoToEpochDay(toIso(new Date()));
        badge = diff === 0 ? " · 🎯今天" : (diff > 0 ? ` · ⏳還有${diff}天` : ` · 已過${-diff}天`);
      }
      const metaEl = document.createElement("div");
      metaEl.className = "event-meta";
      metaEl.textContent = `${item.timeLabel}${item.location ? " · " + item.location : ""}${badge}`;
      body.appendChild(titleEl);
      body.appendChild(metaEl);

      row.appendChild(bar);
      row.appendChild(body);

      if (item.location) {
        const navBtn = document.createElement("button");
        navBtn.className = "icon-btn";
        navBtn.title = "Google Maps 導航";
        navBtn.textContent = "🗺️";
        navBtn.addEventListener("click", () => {
          chrome.tabs.create({ url: `https://maps.google.com/?q=${encodeURIComponent(item.location)}` });
        });
        row.appendChild(navBtn);
      }

      const delBtn = document.createElement("button");
      delBtn.className = "icon-btn";
      delBtn.title = "刪除";
      delBtn.textContent = "🗑️";
      delBtn.addEventListener("click", async () => {
        if (item.kind === "LOCAL_EVENT") {
          state.events = state.events.filter((e) => e.id !== item.rawId);
        } else {
          state.tasks = state.tasks.filter((t) => t.id !== item.rawId);
        }
        await saveState();
        showToast("已刪除項目");
        renderAll();
      });
      row.appendChild(delBtn);

      container.appendChild(row);
    }
  }

  function renderTasks() {
    const list = document.getElementById("tasksList");
    list.innerHTML = "";
    const activeCount = state.tasks.filter((t) => !t.isCompleted && !t.isPrivacy).length;
    document.getElementById("taskCountLabel").textContent = `${activeCount} 項未完成`;

    const visible = state.tasks.filter((t) => !t.isPrivacy);
    if (!visible.length) {
      list.innerHTML = `<div class="day-header-sub">尚無待辦事項。</div>`;
      return;
    }

    for (const tk of visible) {
      const row = document.createElement("div");
      row.className = "event-row";

      const checkBtn = document.createElement("button");
      checkBtn.className = "icon-btn";
      checkBtn.textContent = tk.isCompleted ? "✅" : "⭕";
      checkBtn.addEventListener("click", async () => {
        tk.isCompleted = !tk.isCompleted;
        await saveState();
        renderAll();
      });

      const body = document.createElement("div");
      body.className = "event-body";
      const tEl = document.createElement("div");
      tEl.className = "event-title";
      tEl.style.textDecoration = tk.isCompleted ? "line-through" : "none";
      tEl.textContent = tk.title;
      const mEl = document.createElement("div");
      mEl.className = "event-meta";
      mEl.textContent = tk.dueEpochDay != null ? epochDayToIso(tk.dueEpochDay) : "今日顯示";
      body.appendChild(tEl);
      body.appendChild(mEl);

      const delBtn = document.createElement("button");
      delBtn.className = "icon-btn";
      delBtn.textContent = "🗑️";
      delBtn.addEventListener("click", async () => {
        state.tasks = state.tasks.filter((x) => x.id !== tk.id);
        await saveState();
        renderAll();
      });

      row.appendChild(checkBtn);
      row.appendChild(body);
      row.appendChild(delBtn);
      list.appendChild(row);
    }
  }

  function renderTemplatesAndCountdowns() {
    const chipRow = document.getElementById("templatesChipRow");
    chipRow.innerHTML = "";
    for (const tpl of state.templates) {
      const btn = document.createElement("button");
      btn.className = `chip${activeStampTemplate && activeStampTemplate.id === tpl.id ? " active" : ""}`;
      btn.textContent = `● ${tpl.name}`;
      btn.style.borderColor = argbToCss(tpl.colorArgb);
      btn.addEventListener("click", () => {
        activeStampTemplate = (activeStampTemplate && activeStampTemplate.id === tpl.id) ? null : tpl;
        if (activeStampTemplate) {
          switchTab("tabCalendar");
          showToast(`已啟用「${tpl.name}」連點排班，請點擊月曆日期`);
        }
        renderAll();
      });
      chipRow.appendChild(btn);
    }

    const cdBox = document.getElementById("countdownList");
    cdBox.innerHTML = "";
    const todayEpoch = isoToEpochDay(toIso(new Date()));
    const countdownEvents = state.events
      .filter((ev) => !ev.isPrivacy)
      .map((ev) => {
        const meta = state.richMeta[`LOCAL_EVENT:${ev.id}`];
        const d = new Date(ev.startEpochMillis);
        const iso = toIso(d);
        const diff = isoToEpochDay(iso) - todayEpoch;
        return { ev, meta, iso, diff };
      })
      .filter((x) => (x.meta && (x.meta.isCountdown || x.meta.isBirthday)) || /倒數|生日|紀念/.test(x.ev.title))
      .sort((a, b) => a.diff - b.diff);

    if (!countdownEvents.length) {
      cdBox.innerHTML = `<div class="day-header-sub">尚未建立倒數日；試著在上方輸入「倒數 12/31 跨年煙火」！</div>`;
      return;
    }

    for (const item of countdownEvents) {
      const row = document.createElement("div");
      row.className = "event-row";
      const body = document.createElement("div");
      body.className = "event-body";
      body.innerHTML = `<div class="event-title">${item.ev.title}</div><div class="event-meta">${item.iso}</div>`;
      const badge = document.createElement("div");
      badge.style.fontWeight = "800";
      badge.style.color = "var(--accent)";
      badge.style.fontSize = "14px";
      badge.textContent = item.diff === 0 ? "就是今天" : (item.diff > 0 ? `還有 ${item.diff} 天` : `已過 ${-item.diff} 天`);
      row.appendChild(body);
      row.appendChild(badge);
      cdBox.appendChild(row);
    }
  }

  async function stampTemplateOnIso(tpl, iso) {
    const [y, m, d] = iso.split("-").map(Number);
    const startMs = tpl.isAllDay
      ? new Date(y, m - 1, d, 0, 0, 0).getTime()
      : new Date(y, m - 1, d, tpl.startHour, tpl.startMinute, 0).getTime();
    const endMs = tpl.isAllDay
      ? new Date(y, m - 1, d, 23, 59, 0).getTime()
      : startMs + (tpl.durationMinutes || 60) * 60000;
    state.events.push({
      id: Date.now() + Math.floor(Math.random() * 1000),
      categoryId: 1,
      title: tpl.name,
      location: tpl.location || "",
      url: "",
      notes: tpl.notes || "",
      startEpochMillis: startMs,
      endEpochMillis: endMs,
      isAllDay: Boolean(tpl.isAllDay),
      extraDatesJson: "[]",
      recurrenceType: "NONE",
      isRoutine: false,
      showInMonthGrid: true,
      showInAgendaAndWidget: true,
      isPaused: false,
      isPrivacy: false,
      reminderMinutesJson: "[0,15]",
      customColorArgb: tpl.colorArgb || -3631570
    });
    await saveState();
    showToast(`已排入 ${m}/${d}「${tpl.name}」`);
  }

  async function createFromNlp(raw) {
    const p = DXNlp.parse(raw, new Date());
    const [y, m, d] = p.dateIso.split("-").map(Number);
    const [sh, sm] = p.startTime.split(":").map(Number);
    const [eh, em] = p.endTime.split(":").map(Number);
    const newId = Date.now();

    if (p.isTaskMode) {
      state.tasks.push({
        id: newId,
        categoryId: 6,
        title: p.title,
        notes: "",
        dueEpochDay: isoToEpochDay(p.dateIso),
        dueMinuteOfDay: p.isAllDay ? null : (sh * 60 + sm),
        isCompleted: false,
        recurrenceType: p.recurrenceType,
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPrivacy: false,
        colorArgb: -12417548
      });
    } else {
      const startMs = p.isAllDay
        ? new Date(y, m - 1, d, 0, 0, 0).getTime()
        : new Date(y, m - 1, d, sh, sm, 0).getTime();
      const endMs = p.isAllDay
        ? new Date(y, m - 1, d, 23, 59, 0).getTime()
        : new Date(y, m - 1, d, eh, em, 0).getTime();
      state.events.push({
        id: newId,
        categoryId: 1,
        title: p.title,
        location: "",
        url: "",
        notes: "",
        startEpochMillis: startMs,
        endEpochMillis: Math.max(endMs, startMs + 1800000),
        isAllDay: p.isAllDay,
        extraDatesJson: "[]",
        recurrenceType: p.recurrenceType,
        isRoutine: false,
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPaused: false,
        isPrivacy: false,
        reminderMinutesJson: JSON.stringify(p.reminderMinutes),
        customColorArgb: -3631570
      });
      if (p.isCountdown) {
        state.richMeta[`LOCAL_EVENT:${newId}`] = {
          key: `LOCAL_EVENT:${newId}`,
          isCountdown: true,
          isBirthday: p.title.includes("生日"),
          attachmentUris: [],
          sharedComments: []
        };
      }
    }
    selectedIso = p.dateIso;
    viewYear = y;
    viewMonth = m;
    await saveState();
    showToast(`已建立：${p.title}（${p.dateIso}）`);
    renderAll();
  }

  function switchTab(tabId) {
    document.querySelectorAll(".tab-panel").forEach((el) => el.classList.toggle("active", el.id === tabId));
    document.querySelectorAll(".nav-btn").forEach((btn) => btn.classList.toggle("active", btn.dataset.tab === tabId));
  }

  function updatePomoUi() {
    const m = String(Math.floor(pomoLeftSec / 60)).padStart(2, "0");
    const s = String(pomoLeftSec % 60).padStart(2, "0");
    document.getElementById("pomoDisplay").textContent = `${m}:${s}`;
    document.getElementById("btnPomoToggle").textContent = pomoTimer ? "暫停" : "開始專注";
  }

  function bindEvents() {
    document.getElementById("btnPrev").addEventListener("click", () => {
      viewMonth--;
      if (viewMonth < 1) { viewMonth = 12; viewYear--; }
      renderAll();
    });
    document.getElementById("btnNext").addEventListener("click", () => {
      viewMonth++;
      if (viewMonth > 12) { viewMonth = 1; viewYear++; }
      renderAll();
    });
    document.getElementById("btnNow").addEventListener("click", () => {
      const t = new Date();
      viewYear = t.getFullYear();
      viewMonth = t.getMonth() + 1;
      selectedIso = toIso(t);
      renderAll();
    });

    document.getElementById("btnTheme").addEventListener("click", async () => {
      const cur = document.documentElement.getAttribute("data-theme") || "auto";
      const next = cur === "dark" ? "light" : (cur === "light" ? "auto" : "dark");
      if (next === "auto") document.documentElement.removeAttribute("data-theme");
      else document.documentElement.setAttribute("data-theme", next);
      await chrome.storage.local.set({ [THEME_KEY]: next });
      showToast(next === "dark" ? "已切換：金色黑底暗色" : (next === "light" ? "已切換：金色白底亮色" : "主題：跟隨系統"));
    });

    document.getElementById("btnSidePanel").addEventListener("click", async () => {
      try {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (tab && chrome.sidePanel && chrome.sidePanel.open) {
          await chrome.sidePanel.open({ windowId: tab.windowId });
          window.close();
        }
      } catch (_) {
        showToast("請點擊瀏覽器右上角側邊欄切換 DX日曆");
      }
    });

    const nlpInput = document.getElementById("nlpInput");
    const nlpPreview = document.getElementById("nlpPreview");
    nlpInput.addEventListener("input", () => {
      const val = nlpInput.value.trim();
      if (!val) {
        nlpPreview.classList.add("hidden");
        return;
      }
      const p = DXNlp.parse(val, new Date());
      nlpPreview.textContent = `${p.isTaskMode ? "✅待辦" : "📌行程"}「${p.title}」 · 📅 ${p.dateIso} ${p.isAllDay ? "全天" : p.startTime + "-" + p.endTime}${p.recurrenceType !== "NONE" ? " · 🔁" + p.recurrenceType : ""}`;
      nlpPreview.classList.remove("hidden");
    });

    const submitNlp = async () => {
      const val = nlpInput.value.trim();
      if (!val) return;
      await createFromNlp(val);
      nlpInput.value = "";
      nlpPreview.classList.add("hidden");
    };
    document.getElementById("btnQuickAdd").addEventListener("click", submitNlp);
    nlpInput.addEventListener("keydown", (e) => { if (e.key === "Enter") submitNlp(); });

    document.getElementById("btnVoice").addEventListener("click", () => {
      const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SR) {
        showToast("此瀏覽器不支援語音輸入，請直接打字一句話快記");
        return;
      }
      const rec = new SR();
      rec.lang = "zh-TW";
      rec.onresult = (ev) => {
        const spoken = ev.results[0][0].transcript;
        nlpInput.value = spoken;
        nlpInput.dispatchEvent(new Event("input"));
      };
      rec.onerror = () => showToast("語音辨識未取得麥克風權限");
      rec.start();
      showToast("🎙️ 請說出行程...");
    });

    document.getElementById("btnQuickEventOnDate").addEventListener("click", () => {
      nlpInput.value = `${selectedIso} `;
      nlpInput.focus();
    });

    document.getElementById("btnStopStamp").addEventListener("click", () => {
      activeStampTemplate = null;
      renderAll();
    });

    // Bottom navigation
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.addEventListener("click", () => switchTab(btn.dataset.tab));
    });

    // Tasks
    document.getElementById("btnAddTask").addEventListener("click", async () => {
      const inp = document.getElementById("taskInput");
      const title = inp.value.trim();
      if (!title) return;
      state.tasks.push({
        id: Date.now(),
        categoryId: 6,
        title,
        notes: "",
        dueEpochDay: isoToEpochDay(selectedIso),
        dueMinuteOfDay: null,
        isCompleted: false,
        recurrenceType: "NONE",
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPrivacy: false,
        colorArgb: -12417548
      });
      inp.value = "";
      await saveState();
      renderAll();
    });

    // Pomodoro
    document.querySelectorAll("[data-pomo]").forEach((chip) => {
      chip.addEventListener("click", () => {
        clearInterval(pomoTimer);
        pomoTimer = null;
        document.querySelectorAll("[data-pomo]").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        pomoTotalSec = parseInt(chip.dataset.pomo, 10) * 60;
        pomoLeftSec = pomoTotalSec;
        updatePomoUi();
      });
    });
    document.getElementById("btnPomoToggle").addEventListener("click", () => {
      if (pomoTimer) {
        clearInterval(pomoTimer);
        pomoTimer = null;
      } else {
        pomoTimer = setInterval(() => {
          if (pomoLeftSec > 0) {
            pomoLeftSec--;
            updatePomoUi();
          } else {
            clearInterval(pomoTimer);
            pomoTimer = null;
            updatePomoUi();
            chrome.notifications.create(`pomo_${Date.now()}`, {
              type: "basic",
              iconUrl: "icons/icon128.png",
              title: "🍅 DX日曆番茄鐘完成！",
              message: "專注時段結束，起來伸展休息一下吧！"
            });
          }
        }, 1000);
      }
      updatePomoUi();
    });
    document.getElementById("btnPomoReset").addEventListener("click", () => {
      clearInterval(pomoTimer);
      pomoTimer = null;
      pomoLeftSec = pomoTotalSec;
      updatePomoUi();
    });

    // Templates creator
    document.getElementById("btnAddTpl").addEventListener("click", async () => {
      const name = document.getElementById("newTplName").value.trim();
      const timeVal = document.getElementById("newTplTime").value || "08:00";
      if (!name) return;
      const [h, m] = timeVal.split(":").map(Number);
      state.templates.unshift({
        id: `tpl_${Date.now()}`,
        name,
        startHour: h || 8,
        startMinute: m || 0,
        durationMinutes: 480,
        isAllDay: false,
        colorArgb: -3631570
      });
      document.getElementById("newTplName").value = "";
      await saveState();
      showToast(`已新增範本「${name}」`);
      renderAll();
    });

    // Backup & Sync (100% compatible with Android v2.0.0 DxFullBackupPayload)
    const devInput = document.getElementById("deviceNameInput");
    devInput.addEventListener("change", async () => {
      state.deviceName = devInput.value.trim() || "Chrome-Desktop";
      await saveState();
    });

    document.getElementById("btnExportBackup").addEventListener("click", () => {
      const dev = (devInput.value.trim() || "Chrome-Desktop").replace(/[\\/:*?"<>|\s]+/g, "_");
      const nowD = new Date();
      const ts = `${nowD.getFullYear()}${String(nowD.getMonth() + 1).padStart(2, "0")}${String(nowD.getDate()).padStart(2, "0")}_${String(nowD.getHours()).padStart(2, "0")}${String(nowD.getMinutes()).padStart(2, "0")}`;
      const payload = {
        schemaVersion: 2,
        appId: "com.dx.calendar",
        deviceName: state.deviceName,
        exportedAtMillis: Date.now(),
        categories: [],
        events: state.events,
        tasks: state.tasks,
        routineCheckIns: [],
        externalOverrides: [],
        templates: state.templates,
        richMeta: state.richMeta
      };
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `DX-Calendar_${dev}_${ts}.dxbackup.json`;
      a.click();
      URL.revokeObjectURL(url);
      showToast("已匯出相容 Android 的備份檔！");
    });

    const fileInput = document.getElementById("backupFileInput");
    document.getElementById("btnImportBackup").addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", async () => {
      const file = fileInput.files && fileInput.files[0];
      if (!file) return;
      try {
        const text = await file.text();
        const payload = JSON.parse(text);
        if (Array.isArray(payload.events)) state.events = payload.events;
        if (Array.isArray(payload.tasks)) state.tasks = payload.tasks;
        if (Array.isArray(payload.templates) && payload.templates.length) state.templates = payload.templates;
        if (payload.richMeta && typeof payload.richMeta === "object") state.richMeta = payload.richMeta;
        await saveState();
        showToast(`已從「${payload.deviceName || "Android"}」還原 ${state.events.length} 筆活動與 ${state.tasks.length} 項待辦`);
        renderAll();
      } catch (e) {
        showToast("還原失敗：請選擇有效的 .dxbackup.json 檔案");
      }
      fileInput.value = "";
    });

    document.getElementById("btnOpenDrive").addEventListener("click", () => {
      chrome.tabs.create({ url: "https://drive.google.com/drive/my-drive" });
    });

    document.getElementById("btnRefreshWeather").addEventListener("click", async () => {
      weatherByDate = await DXWeather.getSevenDayForecast(true);
      document.getElementById("weatherStatusText").textContent = `已更新 ${Object.keys(weatherByDate).length} 天天氣預報`;
      renderAll();
      showToast("7 天天氣已更新");
    });
  }

  async function init() {
    await loadState();
    bindEvents();
    updatePomoUi();
    renderAll();
    weatherByDate = await DXWeather.getSevenDayForecast(false);
    const cnt = Object.keys(weatherByDate).length;
    document.getElementById("weatherStatusText").textContent = cnt
      ? `已載入今日起連續 ${cnt} 天預報（Open-Meteo）`
      : "目前離線或尚未取得天氣";
    renderAll();
  }

  init();
})();
