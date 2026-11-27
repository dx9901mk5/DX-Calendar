/**
 * DX日曆 Chrome 插件主控制器（Popup / Side Panel / 獨立桌面雙欄視窗 三形態）
 * 與 Android DX日曆 v2.0.0 共用 100% 無損相容的 DxFullBackupPayload v2 Schema！
 */
(() => {
  const STORAGE_KEY = "dx_calendar_state_v2";
  const THEME_KEY = "dx_theme_mode";
  const POMO_STATE_KEY = "dx_pomo_state_v2";

  const {
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
  } = DXCore;

  let state = normalizeState(null);
  let weatherByDate = {};
  const now = new Date();
  let viewYear = now.getFullYear();
  let viewMonth = now.getMonth() + 1;
  let selectedIso = toIso(now);
  let activeStampTemplate = null;
  let dayViewMode = "list"; // "list" | "timeline"
  let pendingImportMode = "merge";
  let editingEventId = null;

  // 背景持久化番茄鐘本地渲染狀態
  let pomoState = { running: false, totalSec: 25 * 60, pausedLeftSec: 25 * 60, endAtMs: null, taskTitle: "" };
  let pomoUiInterval = null;

  const urlMode = new URLSearchParams(location.search).get("mode");
  if (urlMode === "sidepanel") document.body.classList.add("sidepanel-mode");
  if (urlMode === "app") document.body.classList.add("app-window-mode");

  let toastTimer = null;
  function showToast(msg) {
    const el = document.getElementById("toast");
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.add("hidden"), 2600);
  }

  async function loadState() {
    const res = await chrome.storage.local.get([STORAGE_KEY, THEME_KEY, POMO_STATE_KEY]);
    state = normalizeState(res[STORAGE_KEY]);
    if (res[THEME_KEY] && res[THEME_KEY] !== "auto") {
      document.documentElement.setAttribute("data-theme", res[THEME_KEY]);
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
    if (res[POMO_STATE_KEY]) {
      pomoState = { ...pomoState, ...res[POMO_STATE_KEY] };
    }
    document.getElementById("deviceNameInput").value = state.deviceName;
    document.getElementById("dualTzSelect").value = state.dualTimeZone || "";
  }

  async function saveState() {
    await chrome.storage.local.set({ [STORAGE_KEY]: state });
  }

  async function savePomoState() {
    await chrome.storage.local.set({ [POMO_STATE_KEY]: pomoState });
    if (chrome.alarms) {
      if (pomoState.running && pomoState.endAtMs && pomoState.endAtMs > Date.now()) {
        chrome.alarms.create("dx_pomo_done", { when: pomoState.endAtMs });
      } else {
        chrome.alarms.clear("dx_pomo_done");
      }
    }
  }

  function getItemsForDate(iso) {
    const candDate = parseIso(iso);
    const list = [];
    for (const ev of state.events) {
      if (ev.isPrivacy || ev.isPaused) continue;
      const startD = new Date(ev.startEpochMillis);
      const baseDate = new Date(startD.getFullYear(), startD.getMonth(), startD.getDate());
      if (matchesRecurrence(baseDate, candDate, ev.recurrenceType, ev.extraDatesJson, ev.recurrenceEndEpochMillis)) {
        const startMin = ev.isAllDay ? -1 : (startD.getHours() * 60 + startD.getMinutes());
        const endD = new Date(Math.max(ev.endEpochMillis || 0, ev.startEpochMillis + 1800000));
        list.push({
          kind: "LOCAL_EVENT",
          rawId: ev.id,
          raw: ev,
          title: ev.title,
          isAllDay: Boolean(ev.isAllDay),
          startHour: startD.getHours(),
          startMin,
          timeLabel: ev.isAllDay
            ? "全天"
            : `${String(startD.getHours()).padStart(2, "0")}:${String(startD.getMinutes()).padStart(2, "0")}–${String(endD.getHours()).padStart(2, "0")}:${String(endD.getMinutes()).padStart(2, "0")}`,
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
        const hasTime = tk.dueMinuteOfDay != null;
        const hh = hasTime ? Math.floor(tk.dueMinuteOfDay / 60) : 9;
        const mm = hasTime ? (tk.dueMinuteOfDay % 60) : 0;
        list.push({
          kind: "TASK",
          rawId: tk.id,
          raw: tk,
          title: (tk.isCompleted ? "✓ " : "○ ") + tk.title,
          isAllDay: !hasTime,
          startHour: hh,
          startMin: hasTime ? tk.dueMinuteOfDay : -1,
          timeLabel: hasTime ? `${String(hh).padStart(2, "0")}:${String(mm).padStart(2, "0")} 待辦` : "待辦",
          color: argbToCss(tk.colorArgb || -12417548),
          location: "",
          isCompleted: Boolean(tk.isCompleted)
        });
      }
    }
    list.sort((a, b) => a.startMin - b.startMin);
    return list;
  }

  function renderAll() {
    renderHeader();
    renderMonthGrid();
    renderSelectedDay();
    renderTasks();
    renderTemplatesAndCountdowns();
    renderSyncStats();
    updatePomoUi();
  }

  function renderHeader() {
    const el = document.getElementById("ymTitle");
    el.innerHTML = "";
    const yStr = String(viewYear);
    el.appendChild(document.createTextNode(yStr.slice(0, 2)));
    const yAcc = document.createElement("span");
    yAcc.className = "accent";
    yAcc.textContent = yStr.slice(2);
    el.appendChild(yAcc);
    el.appendChild(document.createTextNode("/"));
    const mAcc = document.createElement("span");
    mAcc.className = "accent";
    mAcc.textContent = String(viewMonth).padStart(2, "0");
    el.appendChild(mAcc);

    const banner = document.getElementById("stampBanner");
    if (activeStampTemplate) {
      banner.classList.remove("hidden");
      document.getElementById("stampText").textContent = `🏷️ 連點排班中：點擊日期直接排入「${activeStampTemplate.name}」`;
    } else {
      banner.classList.add("hidden");
    }
  }

  async function moveItemToIso(kind, rawId, targetIso) {
    const [y, m, d] = targetIso.split("-").map(Number);
    if (kind === "LOCAL_EVENT") {
      const ev = state.events.find((e) => String(e.id) === String(rawId));
      if (!ev) return;
      const oldStart = new Date(ev.startEpochMillis);
      const duration = Math.max(1800000, (ev.endEpochMillis || ev.startEpochMillis + 3600000) - ev.startEpochMillis);
      const newStart = ev.isAllDay
        ? new Date(y, m - 1, d, 0, 0, 0).getTime()
        : new Date(y, m - 1, d, oldStart.getHours(), oldStart.getMinutes(), 0).getTime();
      ev.startEpochMillis = newStart;
      ev.endEpochMillis = ev.isAllDay ? new Date(y, m - 1, d, 23, 59, 0).getTime() : (newStart + duration);
      await saveState();
      selectedIso = targetIso;
      showToast(`已將「${ev.title}」改期至 ${targetIso}`);
      renderAll();
    } else if (kind === "TASK") {
      const tk = state.tasks.find((t) => String(t.id) === String(rawId));
      if (!tk) return;
      tk.dueEpochDay = isoToEpochDay(targetIso);
      await saveState();
      selectedIso = targetIso;
      showToast(`已將待辦「${tk.title}」改期至 ${targetIso}`);
      renderAll();
    }
  }

  function renderMonthGrid() {
    const grid = document.getElementById("monthGrid");
    grid.innerHTML = "";
    const first = new Date(viewYear, viewMonth - 1, 1);
    const startOffset = first.getDay();
    const todayIso = toIso(new Date());
    const isDesktopApp = document.body.classList.contains("app-window-mode");

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
      cell.dataset.iso = iso;

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
        if (isDesktopApp) {
          const chips = document.createElement("div");
          chips.className = "day-mini-chips";
          dayItems.slice(0, 2).forEach((it) => {
            const c = document.createElement("div");
            c.className = "day-mini-chip";
            c.style.background = it.color;
            c.textContent = it.title;
            chips.appendChild(c);
          });
          if (dayItems.length > 2) {
            const more = document.createElement("div");
            more.className = "day-lunar";
            more.textContent = `+${dayItems.length - 2} 項`;
            chips.appendChild(more);
          }
          cell.appendChild(chips);
        } else {
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
      }

      // 拖曳行程到月曆格改期 (Drag & Drop)
      cell.addEventListener("dragover", (e) => {
        e.preventDefault();
        cell.classList.add("drag-over");
      });
      cell.addEventListener("dragleave", () => cell.classList.remove("drag-over"));
      cell.addEventListener("drop", async (e) => {
        e.preventDefault();
        cell.classList.remove("drag-over");
        const raw = e.dataTransfer.getData("application/dx-item");
        if (!raw) return;
        try {
          const parsed = JSON.parse(raw);
          await moveItemToIso(parsed.kind, parsed.rawId, iso);
        } catch (_) {}
      });

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

    const listEl = document.getElementById("dayEventsList");
    const tlWrap = document.getElementById("dayTimelineWrap");
    document.getElementById("btnViewList").classList.toggle("active", dayViewMode === "list");
    document.getElementById("btnViewTimeline").classList.toggle("active", dayViewMode === "timeline");

    const items = getItemsForDate(selectedIso);
    const todayIso = toIso(new Date());

    if (dayViewMode === "timeline") {
      listEl.classList.add("hidden");
      tlWrap.classList.remove("hidden");
      renderDayTimeline(items, d);
      return;
    }

    tlWrap.classList.add("hidden");
    listEl.classList.remove("hidden");
    listEl.innerHTML = "";

    if (!items.length) {
      const empty = document.createElement("div");
      empty.className = "day-header-sub";
      empty.style.padding = "6px 0";
      empty.textContent = "這天沒有安排行程；可於上方一句話快記，或按「＋ 新增」建立（提示：既有行程可拖曳到月曆日期快速改期）。";
      listEl.appendChild(empty);
      return;
    }

    for (const item of items) {
      const row = document.createElement("div");
      row.className = "event-row";
      row.draggable = true;
      row.title = "點擊編輯／留言；拖曳到上方月曆日期可快速改期";
      row.addEventListener("dragstart", (e) => {
        e.dataTransfer.setData("application/dx-item", JSON.stringify({ kind: item.kind, rawId: item.rawId }));
      });

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
      const badge = formatCountdownBadge(selectedIso, meta, todayIso);
      const cmtCount = meta && Array.isArray(meta.sharedComments) && meta.sharedComments.length
        ? ` · 💬${meta.sharedComments.length}` : "";
      const metaEl = document.createElement("div");
      metaEl.className = "event-meta";
      metaEl.textContent = `${item.timeLabel}${item.location ? " · " + item.location : ""}${badge ? " · " + badge : ""}${cmtCount}`;
      body.appendChild(titleEl);
      body.appendChild(metaEl);

      row.appendChild(bar);
      row.appendChild(body);

      if (item.kind === "LOCAL_EVENT") {
        row.addEventListener("click", () => openEventModal(item.rawId));
      } else {
        row.addEventListener("click", async () => {
          item.raw.isCompleted = !item.raw.isCompleted;
          await saveState();
          renderAll();
        });
      }

      if (item.location) {
        const navBtn = document.createElement("button");
        navBtn.className = "icon-btn";
        navBtn.title = "Google Maps 導航";
        navBtn.textContent = "🗺️";
        navBtn.addEventListener("click", (e) => {
          e.stopPropagation();
          chrome.tabs.create({ url: `https://maps.google.com/?q=${encodeURIComponent(item.location)}` });
        });
        row.appendChild(navBtn);
      }

      const delBtn = document.createElement("button");
      delBtn.className = "icon-btn";
      delBtn.title = "刪除";
      delBtn.textContent = "🗑️";
      delBtn.addEventListener("click", async (e) => {
        e.stopPropagation();
        if (item.kind === "LOCAL_EVENT") {
          state.events = state.events.filter((ev) => ev.id !== item.rawId);
        } else {
          state.tasks = state.tasks.filter((tk) => tk.id !== item.rawId);
        }
        await saveState();
        showToast("已刪除項目");
        renderAll();
      });
      row.appendChild(delBtn);

      listEl.appendChild(row);
    }
  }

  function renderDayTimeline(items, dateObj) {
    const grid = document.getElementById("dayTimelineGrid");
    grid.innerHTML = "";
    const dualTz = state.dualTimeZone || "";
    const dualFmt = dualTz
      ? new Intl.DateTimeFormat("en-GB", { timeZone: dualTz, hour: "2-digit", minute: "2-digit", hour12: false })
      : null;

    const allDayItems = items.filter((it) => it.isAllDay);
    if (allDayItems.length) {
      const row = document.createElement("div");
      row.className = "timeline-hour-row";
      const col = document.createElement("div");
      col.className = "timeline-tz-col";
      col.textContent = "全天";
      row.appendChild(col);
      if (dualFmt) {
        const dCol = document.createElement("div");
        dCol.className = "timeline-tz-col dual";
        dCol.textContent = "—";
        row.appendChild(dCol);
      }
      const box = document.createElement("div");
      box.className = "timeline-slot-events";
      for (const it of allDayItems) {
        const p = document.createElement("div");
        p.className = "timeline-pill";
        p.style.background = it.color;
        p.textContent = it.title;
        if (it.kind === "LOCAL_EVENT") p.addEventListener("click", () => openEventModal(it.rawId));
        box.appendChild(p);
      }
      row.appendChild(box);
      grid.appendChild(row);
    }

    for (let h = 0; h < 24; h++) {
      const row = document.createElement("div");
      row.className = "timeline-hour-row";
      const col = document.createElement("div");
      col.className = "timeline-tz-col";
      col.textContent = `${String(h).padStart(2, "0")}:00`;
      row.appendChild(col);

      if (dualFmt) {
        const sample = new Date(dateObj.getFullYear(), dateObj.getMonth(), dateObj.getDate(), h, 0, 0);
        const dCol = document.createElement("div");
        dCol.className = "timeline-tz-col dual";
        dCol.textContent = dualFmt.format(sample);
        row.appendChild(dCol);
      }

      const box = document.createElement("div");
      box.className = "timeline-slot-events";
      const hourItems = items.filter((it) => !it.isAllDay && it.startHour === h);
      for (const it of hourItems) {
        const p = document.createElement("div");
        p.className = "timeline-pill";
        p.style.background = it.color;
        p.textContent = `${it.timeLabel} ${it.title}`;
        if (it.kind === "LOCAL_EVENT") p.addEventListener("click", () => openEventModal(it.rawId));
        box.appendChild(p);
      }
      row.appendChild(box);
      grid.appendChild(row);
    }
  }

  function renderTasks() {
    const list = document.getElementById("tasksList");
    list.innerHTML = "";
    const visible = state.tasks.filter((t) => !t.isPrivacy);
    const activeCount = visible.filter((t) => !t.isCompleted).length;
    document.getElementById("taskCountLabel").textContent = `${activeCount} 項未完成`;

    if (!visible.length) {
      const empty = document.createElement("div");
      empty.className = "day-header-sub";
      empty.textContent = "尚無待辦事項。";
      list.appendChild(empty);
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
        tk.completedAt = tk.isCompleted ? Date.now() : null;
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
      const timeStr = tk.dueMinuteOfDay != null
        ? ` · ${String(Math.floor(tk.dueMinuteOfDay / 60)).padStart(2, "0")}:${String(tk.dueMinuteOfDay % 60).padStart(2, "0")}`
        : "";
      mEl.textContent = (tk.dueEpochDay != null ? epochDayToIso(tk.dueEpochDay) : "今日顯示") + timeStr;
      body.appendChild(tEl);
      body.appendChild(mEl);

      // ⏰ 待辦排時段 (Time Blocking)
      const blockBtn = document.createElement("button");
      blockBtn.className = "icon-btn";
      blockBtn.title = "排入當日 24h 時間軸";
      blockBtn.textContent = "⏰";
      blockBtn.addEventListener("click", async () => {
        const nextHour = Math.min(22, new Date().getHours() + 1);
        tk.dueEpochDay = isoToEpochDay(selectedIso);
        tk.dueMinuteOfDay = nextHour * 60;
        await saveState();
        dayViewMode = "timeline";
        switchTab("tabCalendar");
        renderAll();
        showToast(`已將「${tk.title}」排入 ${selectedIso} ${String(nextHour).padStart(2, "0")}:00`);
      });

      // 🍅 一鍵對該待辦啟動背景番茄鐘
      const pomoBtn = document.createElement("button");
      pomoBtn.className = "icon-btn";
      pomoBtn.title = "對此待辦開始 25 分鐘番茄鐘";
      pomoBtn.textContent = "🍅";
      pomoBtn.addEventListener("click", async () => {
        pomoState = {
          running: true,
          totalSec: 25 * 60,
          pausedLeftSec: 25 * 60,
          endAtMs: Date.now() + 25 * 60 * 1000,
          taskTitle: tk.title
        };
        await savePomoState();
        updatePomoUi();
        showToast(`🍅 開始專注「${tk.title}」(25 分鐘)`);
      });

      const delBtn = document.createElement("button");
      delBtn.className = "icon-btn";
      delBtn.title = "刪除待辦";
      delBtn.textContent = "🗑️";
      delBtn.addEventListener("click", async () => {
        state.tasks = state.tasks.filter((x) => x.id !== tk.id);
        await saveState();
        renderAll();
      });

      row.appendChild(checkBtn);
      row.appendChild(body);
      row.appendChild(blockBtn);
      row.appendChild(pomoBtn);
      row.appendChild(delBtn);
      list.appendChild(row);
    }
  }

  function renderTemplatesAndCountdowns() {
    const chipRow = document.getElementById("templatesChipRow");
    chipRow.innerHTML = "";
    for (const tpl of state.templates) {
      const btn = document.createElement("div");
      btn.className = `chip${activeStampTemplate && activeStampTemplate.id === tpl.id ? " active" : ""}`;
      btn.style.borderColor = argbToCss(tpl.colorArgb);

      const labelSpan = document.createElement("span");
      const timeTag = tpl.isAllDay ? "全天" : `${String(tpl.startHour).padStart(2, "0")}:${String(tpl.startMinute).padStart(2, "0")}`;
      labelSpan.textContent = `● ${tpl.name} (${timeTag})`;
      btn.appendChild(labelSpan);

      const delX = document.createElement("span");
      delX.className = "chip-del";
      delX.textContent = "✕";
      delX.title = "刪除範本";
      delX.addEventListener("click", async (e) => {
        e.stopPropagation();
        if (activeStampTemplate && activeStampTemplate.id === tpl.id) activeStampTemplate = null;
        state.templates = state.templates.filter((t) => t.id !== tpl.id);
        await saveState();
        showToast(`已刪除範本「${tpl.name}」`);
        renderAll();
      });
      btn.appendChild(delX);

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
    const todayIso = toIso(new Date());
    const todayEpoch = isoToEpochDay(todayIso);
    const countdownEvents = state.events
      .filter((ev) => !ev.isPrivacy)
      .map((ev) => {
        const meta = state.richMeta[`LOCAL_EVENT:${ev.id}`];
        const d = new Date(ev.startEpochMillis);
        const iso = toIso(d);
        const diff = isoToEpochDay(iso) - todayEpoch;
        return { ev, meta, iso, diff };
      })
      .filter((x) => (x.meta && (x.meta.isCountdown || x.meta.isBirthday || x.meta.anniversaryStartYear != null)) || /倒數|生日|紀念/.test(x.ev.title))
      .sort((a, b) => a.diff - b.diff);

    if (!countdownEvents.length) {
      const empty = document.createElement("div");
      empty.className = "day-header-sub";
      empty.textContent = "尚未建立倒數日；試著輸入「倒數 12/31 跨年煙火」或在編輯行程勾選倒數／生日！";
      cdBox.appendChild(empty);
      return;
    }

    for (const item of countdownEvents) {
      const row = document.createElement("div");
      row.className = "event-row";
      row.addEventListener("click", () => openEventModal(item.ev.id));

      const body = document.createElement("div");
      body.className = "event-body";
      const tEl = document.createElement("div");
      tEl.className = "event-title";
      tEl.textContent = item.ev.title;
      const mEl = document.createElement("div");
      mEl.className = "event-meta";
      mEl.textContent = item.iso;
      body.appendChild(tEl);
      body.appendChild(mEl);

      const badge = document.createElement("div");
      badge.style.fontWeight = "800";
      badge.style.color = "var(--accent)";
      badge.style.fontSize = "13px";
      badge.style.textAlign = "right";
      const richBadge = formatCountdownBadge(item.iso, item.meta, todayIso);
      badge.textContent = richBadge || (item.diff === 0 ? "🎯 就是今天" : (item.diff > 0 ? `⏳ 還有 ${item.diff} 天` : `已過 ${-item.diff} 天`));

      row.appendChild(body);
      row.appendChild(badge);
      cdBox.appendChild(row);
    }
  }

  function renderSyncStats() {
    const el = document.getElementById("syncStatsText");
    const visEvents = state.events.filter((e) => !e.isPrivacy).length;
    const privCount = state.events.filter((e) => e.isPrivacy).length + state.tasks.filter((t) => t.isPrivacy).length;
    el.textContent = `目前本機：${visEvents} 筆行程 · ${state.tasks.filter((t) => !t.isPrivacy).length} 項待辦 · ${state.templates.length} 組範本` +
      (privCount > 0 ? `（另有 ${privCount} 筆 Android 加密項目原樣保留）` : "");
  }

  function openEventModal(eventId = null) {
    editingEventId = eventId;
    const modal = document.getElementById("eventModal");
    const delBtn = document.getElementById("btnDeleteEvent");
    const cmtSection = document.getElementById("commentsSection");
    const ev = eventId != null ? state.events.find((e) => e.id === eventId) : null;

    if (ev) {
      document.getElementById("modalTitleHeader").textContent = "編輯行程與協作留言";
      const sd = new Date(ev.startEpochMillis);
      const ed = new Date(Math.max(ev.endEpochMillis || 0, ev.startEpochMillis + 1800000));
      document.getElementById("evTitle").value = ev.title || "";
      document.getElementById("evDate").value = toIso(sd);
      document.getElementById("evAllDay").checked = Boolean(ev.isAllDay);
      document.getElementById("evStartTime").value = `${String(sd.getHours()).padStart(2, "0")}:${String(sd.getMinutes()).padStart(2, "0")}`;
      document.getElementById("evEndTime").value = `${String(ed.getHours()).padStart(2, "0")}:${String(ed.getMinutes()).padStart(2, "0")}`;
      document.getElementById("evLocation").value = ev.location || "";
      document.getElementById("evRecurrence").value = ev.recurrenceType || "NONE";
      document.getElementById("evColor").value = argbToCss(ev.customColorArgb || -3631570);
      try {
        const arr = JSON.parse(ev.reminderMinutesJson || "[0,15]");
        document.getElementById("evReminder").value = Array.isArray(arr) ? arr.join(",") : "0,15";
      } catch (_) {
        document.getElementById("evReminder").value = "0,15";
      }
      document.getElementById("evNotes").value = ev.notes || "";

      const meta = state.richMeta[`LOCAL_EVENT:${ev.id}`] || {};
      document.getElementById("evIsCountdown").checked = Boolean(meta.isCountdown);
      document.getElementById("evIsBirthday").checked = Boolean(meta.isBirthday);
      document.getElementById("evBirthYear").value = meta.anniversaryStartYear != null ? String(meta.anniversaryStartYear) : "";

      delBtn.classList.remove("hidden");
      cmtSection.classList.remove("hidden");
      renderModalComments(`LOCAL_EVENT:${ev.id}`);
    } else {
      document.getElementById("modalTitleHeader").textContent = "新增行程";
      document.getElementById("evTitle").value = "";
      document.getElementById("evDate").value = selectedIso;
      document.getElementById("evAllDay").checked = false;
      document.getElementById("evStartTime").value = "09:00";
      document.getElementById("evEndTime").value = "10:00";
      document.getElementById("evLocation").value = "";
      document.getElementById("evRecurrence").value = "NONE";
      document.getElementById("evColor").value = "#c8962e";
      document.getElementById("evReminder").value = "0,15";
      document.getElementById("evNotes").value = "";
      document.getElementById("evIsCountdown").checked = false;
      document.getElementById("evIsBirthday").checked = false;
      document.getElementById("evBirthYear").value = "";
      delBtn.classList.add("hidden");
      cmtSection.classList.add("hidden");
    }

    document.getElementById("evTimeRow").classList.toggle("hidden", document.getElementById("evAllDay").checked);
    modal.classList.remove("hidden");
    document.getElementById("evTitle").focus();
  }

  function closeEventModal() {
    document.getElementById("eventModal").classList.add("hidden");
    editingEventId = null;
  }

  function renderModalComments(metaKey) {
    const box = document.getElementById("commentsList");
    box.innerHTML = "";
    const comments = (state.richMeta[metaKey] && state.richMeta[metaKey].sharedComments) || [];
    if (!comments.length) {
      const empty = document.createElement("div");
      empty.className = "day-header-sub";
      empty.style.marginBottom = "6px";
      empty.textContent = "尚無留言，可在此留下備註同步給家人或另一半。";
      box.appendChild(empty);
      return;
    }
    for (const c of comments) {
      const div = document.createElement("div");
      div.className = "comment-item";
      const head = document.createElement("div");
      head.style.fontWeight = "700";
      head.style.color = "var(--accent)";
      const dt = new Date(c.createdAtMillis || Date.now());
      head.textContent = `${c.author || "我"} · ${dt.getMonth() + 1}/${dt.getDate()} ${String(dt.getHours()).padStart(2, "0")}:${String(dt.getMinutes()).padStart(2, "0")}`;
      const body = document.createElement("div");
      body.textContent = c.content || "";
      div.appendChild(head);
      div.appendChild(body);
      box.appendChild(div);
    }
  }

  async function saveEventFromModal() {
    const title = document.getElementById("evTitle").value.trim();
    if (!title) {
      showToast("請輸入行程標題");
      return;
    }
    const dateVal = document.getElementById("evDate").value || selectedIso;
    const [y, m, d] = dateVal.split("-").map(Number);
    const isAllDay = document.getElementById("evAllDay").checked;
    const [sh, sm] = (document.getElementById("evStartTime").value || "09:00").split(":").map(Number);
    const [eh, em] = (document.getElementById("evEndTime").value || "10:00").split(":").map(Number);
    const startMs = isAllDay ? new Date(y, m - 1, d, 0, 0, 0).getTime() : new Date(y, m - 1, d, sh || 0, sm || 0, 0).getTime();
    const endMs = isAllDay
      ? new Date(y, m - 1, d, 23, 59, 0).getTime()
      : Math.max(startMs + 1800000, new Date(y, m - 1, d, eh || 0, em || 0, 0).getTime());

    const remArr = document.getElementById("evReminder").value
      .split(/[,，\s]+/)
      .map((s) => parseInt(s, 10))
      .filter((n) => Number.isFinite(n) && n >= 0);
    const reminderMinutesJson = JSON.stringify(remArr.length ? Array.from(new Set(remArr)).sort((a, b) => a - b) : [0]);

    const colorArgb = cssToArgb(document.getElementById("evColor").value);
    const location = document.getElementById("evLocation").value.trim();
    const notes = document.getElementById("evNotes").value.trim();
    const recurrenceType = document.getElementById("evRecurrence").value || "NONE";

    let targetId = editingEventId;
    if (editingEventId != null) {
      const ev = state.events.find((e) => e.id === editingEventId);
      if (ev) {
        ev.title = title;
        ev.startEpochMillis = startMs;
        ev.endEpochMillis = endMs;
        ev.isAllDay = isAllDay;
        ev.location = location;
        ev.notes = notes;
        ev.recurrenceType = recurrenceType;
        ev.customColorArgb = colorArgb;
        ev.reminderMinutesJson = reminderMinutesJson;
      }
    } else {
      targetId = Date.now();
      state.events.push({
        id: targetId,
        categoryId: 1,
        title,
        location,
        url: "",
        notes,
        startEpochMillis: startMs,
        endEpochMillis: endMs,
        isAllDay,
        extraDatesJson: "[]",
        recurrenceType,
        isRoutine: false,
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPaused: false,
        isPrivacy: false,
        reminderMinutesJson,
        enableMultiCall: false,
        multiCallCount: 3,
        multiCallIntervalMinutes: 3,
        useAlarmStream: false,
        syncSystemAlarm: false,
        customColorArgb: colorArgb,
        createdAt: Date.now()
      });
    }

    const metaKey = `LOCAL_EVENT:${targetId}`;
    const existingMeta = state.richMeta[metaKey] || { key: metaKey, attachmentUris: [], sharedComments: [] };
    const isCountdown = document.getElementById("evIsCountdown").checked;
    const isBirthday = document.getElementById("evIsBirthday").checked;
    const birthYearRaw = parseInt(document.getElementById("evBirthYear").value, 10);
    const anniversaryStartYear = Number.isFinite(birthYearRaw) ? birthYearRaw : null;

    if (isCountdown || isBirthday || anniversaryStartYear != null || (existingMeta.sharedComments && existingMeta.sharedComments.length) || (existingMeta.attachmentUris && existingMeta.attachmentUris.length)) {
      state.richMeta[metaKey] = {
        ...existingMeta,
        key: metaKey,
        isCountdown,
        isBirthday,
        anniversaryStartYear
      };
    } else {
      delete state.richMeta[metaKey];
    }

    selectedIso = dateVal;
    viewYear = y;
    viewMonth = m;
    await saveState();
    closeEventModal();
    showToast(`已儲存「${title}」`);
    renderAll();
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
      reminderMinutesJson: JSON.stringify(Array.isArray(tpl.reminderMinutes) ? tpl.reminderMinutes : [0, 15]),
      enableMultiCall: Boolean(tpl.enableMultiCall),
      multiCallCount: 3,
      multiCallIntervalMinutes: 3,
      useAlarmStream: false,
      syncSystemAlarm: false,
      customColorArgb: tpl.colorArgb || -3631570,
      createdAt: Date.now()
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
        isRoutine: false,
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPaused: false,
        isPrivacy: false,
        reminderMinutesJson: JSON.stringify(p.reminderMinutes),
        colorArgb: -12417548,
        updatedAt: Date.now()
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
        enableMultiCall: false,
        multiCallCount: 3,
        multiCallIntervalMinutes: 3,
        useAlarmStream: false,
        syncSystemAlarm: false,
        customColorArgb: -3631570,
        createdAt: Date.now()
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

  function getPomoLeftSeconds() {
    if (pomoState.running && pomoState.endAtMs) {
      return Math.max(0, Math.ceil((pomoState.endAtMs - Date.now()) / 1000));
    }
    return Math.max(0, pomoState.pausedLeftSec || 0);
  }

  function updatePomoUi() {
    const left = getPomoLeftSeconds();
    if (pomoState.running && left === 0) {
      pomoState.running = false;
      pomoState.endAtMs = null;
      pomoState.pausedLeftSec = pomoState.totalSec;
      savePomoState();
    }
    const m = String(Math.floor(left / 60)).padStart(2, "0");
    const s = String(left % 60).padStart(2, "0");
    document.getElementById("pomoDisplay").textContent = `${m}:${s}`;
    document.getElementById("btnPomoToggle").textContent = pomoState.running ? "暫停" : "開始專注";
    document.getElementById("pomoTaskLabel").textContent = pomoState.taskTitle
      ? `🍅 專注中：${pomoState.taskTitle}（關閉視窗仍持續倒數）`
      : "🍅 番茄鐘專注計時（關閉視窗仍於背景倒數）";
  }

  function downloadFile(filename, content, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
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
          if (!urlMode) window.close();
        }
      } catch (_) {
        showToast("請點擊瀏覽器右上角側邊欄切換 DX日曆");
      }
    });

    document.getElementById("btnAppWindow").addEventListener("click", async () => {
      try {
        await chrome.windows.create({
          url: chrome.runtime.getURL("popup.html?mode=app"),
          type: "popup",
          width: 1060,
          height: 760
        });
        if (!urlMode) window.close();
      } catch (_) {
        chrome.tabs.create({ url: chrome.runtime.getURL("popup.html?mode=app") });
      }
    });

    // 鍵盤左右換月、Esc 關閉彈窗
    document.addEventListener("keydown", (e) => {
      const tag = (document.activeElement && document.activeElement.tagName) || "";
      if (e.key === "Escape" && !document.getElementById("eventModal").classList.contains("hidden")) {
        closeEventModal();
        return;
      }
      if (["INPUT", "TEXTAREA", "SELECT"].includes(tag)) return;
      if (e.key === "ArrowLeft") document.getElementById("btnPrev").click();
      if (e.key === "ArrowRight") document.getElementById("btnNext").click();
    });

    // NLP 快記與語音
    const nlpInput = document.getElementById("nlpInput");
    const nlpPreview = document.getElementById("nlpPreview");
    nlpInput.addEventListener("input", () => {
      const val = nlpInput.value.trim();
      if (!val) { nlpPreview.classList.add("hidden"); return; }
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
      if (!SR) { showToast("此瀏覽器不支援語音輸入，請直接打字一句話快記"); return; }
      const rec = new SR();
      rec.lang = "zh-TW";
      rec.onresult = (ev) => {
        nlpInput.value = ev.results[0][0].transcript;
        nlpInput.dispatchEvent(new Event("input"));
      };
      rec.onerror = () => showToast("語音辨識未取得麥克風權限（建議於獨立視窗或側邊欄授權一次麥克風）");
      rec.start();
      showToast("🎙️ 請說出行程...");
    });

    document.getElementById("btnQuickEventOnDate").addEventListener("click", () => openEventModal(null));
    document.getElementById("btnViewList").addEventListener("click", () => { dayViewMode = "list"; renderSelectedDay(); });
    document.getElementById("btnViewTimeline").addEventListener("click", () => { dayViewMode = "timeline"; renderSelectedDay(); });
    document.getElementById("dualTzSelect").addEventListener("change", async (e) => {
      state.dualTimeZone = e.target.value;
      await saveState();
      renderSelectedDay();
    });

    document.getElementById("btnStopStamp").addEventListener("click", () => {
      activeStampTemplate = null;
      renderAll();
    });

    // Event Editor Modal
    document.getElementById("btnCloseModal").addEventListener("click", closeEventModal);
    document.getElementById("eventModal").addEventListener("click", (e) => {
      if (e.target.id === "eventModal") closeEventModal();
    });
    document.getElementById("evAllDay").addEventListener("change", (e) => {
      document.getElementById("evTimeRow").classList.toggle("hidden", e.target.checked);
    });
    document.getElementById("btnModalNavMaps").addEventListener("click", () => {
      const loc = document.getElementById("evLocation").value.trim();
      if (!loc) { showToast("請先輸入地點"); return; }
      chrome.tabs.create({ url: `https://maps.google.com/?q=${encodeURIComponent(loc)}` });
    });
    document.getElementById("btnSaveEvent").addEventListener("click", saveEventFromModal);
    document.getElementById("btnDeleteEvent").addEventListener("click", async () => {
      if (editingEventId == null) return;
      state.events = state.events.filter((e) => e.id !== editingEventId);
      delete state.richMeta[`LOCAL_EVENT:${editingEventId}`];
      await saveState();
      closeEventModal();
      showToast("已刪除行程");
      renderAll();
    });
    document.getElementById("btnAddComment").addEventListener("click", async () => {
      if (editingEventId == null) return;
      const inp = document.getElementById("commentInput");
      const content = inp.value.trim();
      if (!content) return;
      const key = `LOCAL_EVENT:${editingEventId}`;
      const existing = state.richMeta[key] || { key, isCountdown: false, isBirthday: false, attachmentUris: [], sharedComments: [] };
      existing.sharedComments = [...(existing.sharedComments || []), {
        id: `cmt_${Date.now()}`,
        author: state.deviceName || "Chrome-Desktop",
        content,
        createdAtMillis: Date.now()
      }];
      state.richMeta[key] = existing;
      inp.value = "";
      await saveState();
      renderModalComments(key);
      renderSelectedDay();
    });

    // Bottom navigation
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.addEventListener("click", () => switchTab(btn.dataset.tab));
    });

    // Tasks
    const addTaskAction = async () => {
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
        isRoutine: false,
        showInMonthGrid: true,
        showInAgendaAndWidget: true,
        isPaused: false,
        isPrivacy: false,
        reminderMinutesJson: "[0]",
        colorArgb: -12417548,
        updatedAt: Date.now()
      });
      inp.value = "";
      await saveState();
      renderAll();
    };
    document.getElementById("btnAddTask").addEventListener("click", addTaskAction);
    document.getElementById("taskInput").addEventListener("keydown", (e) => { if (e.key === "Enter") addTaskAction(); });

    // Background-persisted Pomodoro
    document.querySelectorAll("[data-pomo]").forEach((chip) => {
      chip.addEventListener("click", async () => {
        document.querySelectorAll("[data-pomo]").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        const sec = parseInt(chip.dataset.pomo, 10) * 60;
        pomoState = { running: false, totalSec: sec, pausedLeftSec: sec, endAtMs: null, taskTitle: "" };
        await savePomoState();
        updatePomoUi();
      });
    });
    document.getElementById("btnPomoToggle").addEventListener("click", async () => {
      if (pomoState.running) {
        pomoState.pausedLeftSec = getPomoLeftSeconds();
        pomoState.running = false;
        pomoState.endAtMs = null;
      } else {
        const left = getPomoLeftSeconds() || pomoState.totalSec;
        pomoState.running = true;
        pomoState.endAtMs = Date.now() + left * 1000;
      }
      await savePomoState();
      updatePomoUi();
    });
    document.getElementById("btnPomoReset").addEventListener("click", async () => {
      pomoState = { ...pomoState, running: false, endAtMs: null, pausedLeftSec: pomoState.totalSec, taskTitle: "" };
      await savePomoState();
      updatePomoUi();
    });

    // Templates creator
    document.getElementById("btnAddTpl").addEventListener("click", async () => {
      const name = document.getElementById("newTplName").value.trim();
      const timeVal = document.getElementById("newTplTime").value || "08:00";
      const dur = Math.max(15, parseInt(document.getElementById("newTplDuration").value, 10) || 480);
      const isAllDay = document.getElementById("newTplAllDay").checked;
      if (!name) return;
      const [h, m] = timeVal.split(":").map(Number);
      state.templates.unshift({
        id: `tpl_${Date.now()}`,
        name,
        startHour: h || 8,
        startMinute: m || 0,
        durationMinutes: dur,
        isAllDay,
        colorArgb: -3631570,
        location: "",
        notes: "",
        reminderMinutes: [0, 15],
        enableMultiCall: false
      });
      document.getElementById("newTplName").value = "";
      await saveState();
      showToast(`已新增範本「${name}」`);
      renderAll();
    });

    // Backup & Sync (100% lossless Android v2.0.0 DxFullBackupPayload)
    const devInput = document.getElementById("deviceNameInput");
    devInput.addEventListener("change", async () => {
      state.deviceName = devInput.value.trim() || "Chrome-Desktop";
      await saveState();
    });

    document.getElementById("btnExportBackup").addEventListener("click", () => {
      state.deviceName = devInput.value.trim() || "Chrome-Desktop";
      const dev = state.deviceName.replace(/[\\/:*?"<>|\s]+/g, "_");
      const nowD = new Date();
      const ts = `${nowD.getFullYear()}${String(nowD.getMonth() + 1).padStart(2, "0")}${String(nowD.getDate()).padStart(2, "0")}_${String(nowD.getHours()).padStart(2, "0")}${String(nowD.getMinutes()).padStart(2, "0")}`;
      const payload = buildBackupPayload(state);
      downloadFile(`DX-Calendar_${dev}_${ts}.dxbackup.json`, JSON.stringify(payload, null, 2), "application/json");
      showToast("已無損匯出相容 Android v2.0 的備份檔！");
    });

    const fileInput = document.getElementById("backupFileInput");
    document.getElementById("btnImportMerge").addEventListener("click", () => { pendingImportMode = "merge"; fileInput.click(); });
    document.getElementById("btnImportOverwrite").addEventListener("click", () => { pendingImportMode = "overwrite"; fileInput.click(); });
    fileInput.addEventListener("change", async () => {
      const file = fileInput.files && fileInput.files[0];
      if (!file) return;
      try {
        const payload = JSON.parse(await file.text());
        state = importBackupPayload(state, payload, pendingImportMode);
        await saveState();
        const modeLabel = pendingImportMode === "merge" ? "智慧合併" : "完整覆蓋";
        showToast(`已${modeLabel}「${payload.deviceName || "Android"}」備份（共 ${state.events.length} 筆活動／${state.tasks.length} 項待辦）`);
        renderAll();
      } catch (_) {
        showToast("還原失敗：請選擇有效的 .dxbackup.json 檔案");
      }
      fileInput.value = "";
    });

    document.getElementById("btnOpenDrive").addEventListener("click", () => {
      chrome.tabs.create({ url: "https://drive.google.com/drive/my-drive" });
    });

    // .ics Export / Import
    document.getElementById("btnExportIcs").addEventListener("click", () => {
      const ics = exportToIcs(state.events);
      downloadFile(`DX-Calendar_${toIso(new Date())}.ics`, ics, "text/calendar;charset=utf-8");
      showToast("已匯出標準 .ics 行事曆檔");
    });
    const icsInput = document.getElementById("icsFileInput");
    document.getElementById("btnImportIcs").addEventListener("click", () => icsInput.click());
    icsInput.addEventListener("change", async () => {
      const file = icsInput.files && icsInput.files[0];
      if (!file) return;
      try {
        const imported = importFromIcs(await file.text());
        state.events.push(...imported);
        await saveState();
        showToast(`已匯入 ${imported.length} 筆 .ics 行程`);
        renderAll();
      } catch (_) {
        showToast("ICS 解析失敗，請確認檔案格式");
      }
      icsInput.value = "";
    });

    document.getElementById("btnRefreshWeather").addEventListener("click", async () => {
      weatherByDate = await DXWeather.getSevenDayForecast(true);
      document.getElementById("weatherStatusText").textContent = `已更新 ${Object.keys(weatherByDate).length} 天天氣預報`;
      renderAll();
      showToast("7 天天氣已更新");
    });

    // 🌸 女性健康追蹤開關（預設關閉）與 8 大預設追蹤範本（與 Android App 100% 對齊）
    const WOMENS_HEALTH_PRESETS = [
      { id: "period", name: "🩸 生理期", days: 5, rec: "EVERY_N_WEEKS:4", rem: [0, 1440, 2880] },
      { id: "ovulation", name: "✨ 排卵期", days: 1, rec: "EVERY_N_WEEKS:4", rem: [0, 1440] },
      { id: "pregnancy", name: "🤰 懷孕週數", days: 1, rec: "WEEKLY", rem: [0] },
      { id: "prenatal", name: "🩺 產檢預約", days: 1, rec: "NONE", rem: [0, 60, 1440] },
      { id: "edd", name: "👶 預產期 (EDD)", days: 1, rec: "NONE", rem: [0, 10080], isCountdown: true },
      { id: "postpartum", name: "💐 孕產／產後護理", days: 1, rec: "NONE", rem: [0] },
      { id: "pill", name: "💊 事前藥／葉酸", days: 1, rec: "DAILY", rem: [0, 15] },
      { id: "gyn", name: "🏥 婦科回診／篩檢", days: 1, rec: "NONE", rem: [0, 1440] }
    ];
    let onlyWomensHealthFilter = false;
    const chkWh = document.getElementById("chkWomensHealthEnable");
    const whBody = document.getElementById("womensHealthBody");
    const whRow = document.getElementById("womensPresetsRow");
    const whBanner = document.getElementById("womensHealthBanner");

    function syncWomensHealthUi() {
      const enabled = Boolean(state.womensHealthEnabled);
      if (chkWh) chkWh.checked = enabled;
      if (whBody) whBody.classList.toggle("hidden", !enabled);
      if (!enabled) onlyWomensHealthFilter = false;
      if (whBanner) whBanner.classList.toggle("hidden", !onlyWomensHealthFilter);
      if (whRow && !whRow.childElementCount) {
        for (const preset of WOMENS_HEALTH_PRESETS) {
          const chip = document.createElement("button");
          chip.className = "chip";
          chip.style.borderColor = "#ec407a";
          chip.textContent = preset.name;
          chip.addEventListener("click", async () => {
            const baseDate = new Date(`${selectedIso}T09:00:00`);
            const id = Date.now() + Math.floor(Math.random() * 1000);
            state.events.push({
              id,
              title: preset.name,
              startEpochMillis: baseDate.getTime(),
              endEpochMillis: baseDate.getTime() + preset.days * 86400000 - 3600000,
              isAllDay: preset.id !== "pill",
              colorArgb: -1294214, // #EC407A 玫瑰粉
              recurrenceType: preset.rec,
              reminderMinutes: preset.rem,
              categoryId: 9,
              isPrivacy: false,
              showInMonthGrid: true,
              notes: "女性健康追蹤項目（與 Android DX日曆同步）"
            });
            if (preset.isCountdown) {
              state.richMeta[`LOCAL_EVENT:${id}`] = { key: `LOCAL_EVENT:${id}`, isCountdown: true };
            }
            await saveState();
            renderAll();
            showToast(`已於 ${selectedIso} 建立「${preset.name}」`);
          });
          whRow.appendChild(chip);
        }
      }
    }

    if (chkWh) {
      chkWh.addEventListener("change", async () => {
        state.womensHealthEnabled = chkWh.checked;
        await saveState();
        syncWomensHealthUi();
      });
    }
    const btnToggleWh = document.getElementById("btnToggleWomensOnly");
    if (btnToggleWh) {
      btnToggleWh.addEventListener("click", () => {
        onlyWomensHealthFilter = !onlyWomensHealthFilter;
        syncWomensHealthUi();
        switchTab("tabCalendar");
        showToast(onlyWomensHealthFilter ? "🌸 已切換為只檢視女性健康項目" : "已恢復顯示全部行程");
      });
    }
    const btnClearWh = document.getElementById("btnClearWomensFilter");
    if (btnClearWh) {
      btnClearWh.addEventListener("click", () => {
        onlyWomensHealthFilter = false;
        syncWomensHealthUi();
      });
    }
    syncWomensHealthUi();

    // ⏰ 自訂幾天幾時幾分前多提示 & 預設不提示
    const btnAddRem = document.getElementById("btnAddCustomRem");
    const btnClearRem = document.getElementById("btnClearRem");
    if (btnAddRem) {
      btnAddRem.addEventListener("click", () => {
        const d = Math.max(0, parseInt(document.getElementById("remDays").value, 10) || 0);
        const h = Math.max(0, parseInt(document.getElementById("remHours").value, 10) || 0);
        const m = Math.max(0, parseInt(document.getElementById("remMins").value, 10) || 0);
        const totalMin = d * 1440 + h * 60 + m;
        const input = document.getElementById("evReminder");
        const existing = input.value.split(",").map((x) => parseInt(x.trim(), 10)).filter((n) => !Number.isNaN(n) && n >= 0);
        if (!existing.includes(totalMin)) existing.push(totalMin);
        existing.sort((a, b) => a - b);
        input.value = existing.join(",");
        showToast(`已加入提前 ${d ? `${d}天` : ""}${h ? `${h}時` : ""}${m ? `${m}分` : "" || "準時"} 提醒`);
      });
    }
    if (btnClearRem) {
      btnClearRem.addEventListener("click", () => {
        document.getElementById("evReminder").value = "";
        showToast("🔕 已設為不提示（無提醒）");
      });
    }

    // 📱 Android App 雙向互推連結（Google Play 測試三連結）
    const openExternal = (url) => chrome.tabs.create({ url });
    const btnAndGroup = document.getElementById("btnAndroidGroup");
    const btnAndOptIn = document.getElementById("btnAndroidOptIn");
    const btnAndPlay = document.getElementById("btnAndroidPlay");
    if (btnAndGroup) btnAndGroup.addEventListener("click", () => openExternal("https://groups.google.com/g/dx-file-manager-testers"));
    if (btnAndOptIn) btnAndOptIn.addEventListener("click", () => openExternal("https://play.google.com/apps/testing/com.dx.calendar"));
    if (btnAndPlay) btnAndPlay.addEventListener("click", () => openExternal("https://play.google.com/store/apps/details?id=com.dx.calendar"));

    // 🌐 四語系介面切換（繁體中文 / 简体中文 / English / 日本語）
    const I18N_MAP = {
      "zh-TW": {
        promoTitle: "📱 同步推出：DX日曆 Android App（手機／摺疊機／平板）",
        promoDesc: "在手機、摺疊機與平板上享受完整原生體驗：5 款高清晰向量桌面小工具、Google 日曆／Tasks 雙向同步、多重連段鬧鐘、離線語音快記與前 7 天無廣告全功能試用（多裝置體驗一律免費）。",
        promoHint: "⏳ 提示：加入群組後系統將於 10 分鐘～3 小時內批量授予測試資格，建議稍候再點擊步驟 2 與 3。",
        langTitle: "🌐 介面語言（四語系同步）",
        tabs: ["📅 月曆", "✅ 待辦/番茄", "🏷️ 排班/倒數", "☁️ 同步/App"]
      },
      "zh-CN": {
        promoTitle: "📱 同步推出：DX日历 Android App（手机／折叠屏／平板）",
        promoDesc: "在手机、折叠屏与平板上享受完整原生体验：5 款高清矢量桌面小组件、Google 日历／Tasks 双向同步、多重连段闹钟、离线语音快记与前 7 天无广告全功能试用（多设备体验一律免费）。",
        promoHint: "⏳ 提示：加入群组后系统将在 10 分钟～3 小时内批量授予测试资格，建议稍后再点击步骤 2 与 3。",
        langTitle: "🌐 界面语言（四语系同步）",
        tabs: ["📅 月历", "✅ 待办/番茄", "🏷️ 排班/倒数", "☁️ 同步/App"]
      },
      "en": {
        promoTitle: "📱 Also on Android: DX-Calendar App (Phone / Foldable / Tablet)",
        promoDesc: "Get the full native Android experience: 5 crisp vector home widgets, two-way Google Calendar & Tasks sync, multi-burst alarms, offline voice quick-add, and a 7-day ad-free full trial (multi-device always free).",
        promoHint: "⏳ Note: After joining the Google Group, Play test access is batch-granted within 10 min – 3 hrs. Please wait briefly before tapping steps 2 & 3.",
        langTitle: "🌐 Interface Language (4 Languages)",
        tabs: ["📅 Calendar", "✅ Tasks/Pomo", "🏷️ Shifts/Health", "☁️ Sync/App"]
      },
      "ja": {
        promoTitle: "📱 Android版も公開中：DXカレンダー（スマホ／折りたたみ／タブレット）",
        promoDesc: "高精細ネイティブウィジェット5種、Googleカレンダー＆Tasks双方向同期、連続アラーム、オフライン音声入力、広告なしの7日間無料フル体験（マルチデバイス体験は完全無料）。",
        promoHint: "⏳ ご案内：グループ参加後、テスト権限の付与に10分〜3時間ほどかかります。少し待ってから手順2・3を開いてください。",
        langTitle: "🌐 表示言語（4言語対応）",
        tabs: ["📅 カレンダー", "✅ タスク/集中", "🏷️ シフト/健康", "☁️ 同期/App"]
      }
    };
    const langSel = document.getElementById("uiLangSelect");
    function applyI18n(lang) {
      const pack = I18N_MAP[lang] || I18N_MAP["zh-TW"];
      const t1 = document.getElementById("androidPromoTitle");
      const t2 = document.getElementById("androidPromoDesc");
      const t3 = document.getElementById("androidPromoHint");
      const t4 = document.getElementById("langCardTitle");
      if (t1) t1.textContent = pack.promoTitle;
      if (t2) t2.textContent = pack.promoDesc;
      if (t3) t3.textContent = pack.promoHint;
      if (t4) t4.textContent = pack.langTitle;
      document.querySelectorAll(".bottom-nav .nav-btn").forEach((btn, idx) => {
        if (pack.tabs[idx]) btn.textContent = pack.tabs[idx];
      });
    }
    if (langSel) {
      const browserLang = (navigator.language || "zh-TW").toLowerCase();
      const detected = state.uiLang || (browserLang.startsWith("zh-cn") || browserLang.startsWith("zh-sg") ? "zh-CN" : browserLang.startsWith("ja") ? "ja" : browserLang.startsWith("en") ? "en" : "zh-TW");
      langSel.value = detected;
      applyI18n(detected);
      langSel.addEventListener("change", async () => {
        state.uiLang = langSel.value;
        await saveState();
        applyI18n(langSel.value);
      });
    }

    // 跨視窗即時同步（Popup ⇄ Side Panel ⇄ 獨立桌面視窗）
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area !== "local") return;
      if (changes[STORAGE_KEY]) {
        state = normalizeState(changes[STORAGE_KEY].newValue);
        renderAll();
      }
      if (changes[POMO_STATE_KEY]) {
        pomoState = { ...pomoState, ...(changes[POMO_STATE_KEY].newValue || {}) };
        updatePomoUi();
      }
    });
  }

  async function init() {
    await loadState();
    bindEvents();
    renderAll();
    clearInterval(pomoUiInterval);
    pomoUiInterval = setInterval(updatePomoUi, 1000);
    weatherByDate = await DXWeather.getSevenDayForecast(false);
    const cnt = Object.keys(weatherByDate).length;
    document.getElementById("weatherStatusText").textContent = cnt
      ? `已載入今日起連續 ${cnt} 天預報（Open-Meteo）`
      : "目前離線或尚未取得天氣";
    renderAll();
  }

  init();
})();
