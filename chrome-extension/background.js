/**
 * DX日曆 Chrome 插件 — Manifest V3 背景服務
 * 1. 工具列動態當日日期徽章（番茄鐘運作時自動顯示剩餘分鐘，結束恢復今日日期）
 * 2. 支援全部 11 種重複規則（含農曆初一十五／農曆每月／農曆每年／隔 N 週）的桌面提醒通知
 * 3. 背景持久化番茄鐘（即使關閉彈出視窗依然準時響鈴通知！）
 */
importScripts("lunar.js", "core.js");

const STORAGE_STATE_KEY = "dx_calendar_state_v2";
const FIRED_NOTIF_KEY = "dx_fired_notifs_v2";
const POMO_STATE_KEY = "dx_pomo_state_v2";

async function updateToolbarBadge() {
  const data = await chrome.storage.local.get(POMO_STATE_KEY);
  const pomo = data[POMO_STATE_KEY];
  if (pomo && pomo.running && pomo.endAtMs && pomo.endAtMs > Date.now()) {
    const minsLeft = Math.max(1, Math.ceil((pomo.endAtMs - Date.now()) / 60000));
    chrome.action.setBadgeText({ text: `${minsLeft}m` });
    chrome.action.setBadgeBackgroundColor({ color: "#D94A38" });
    chrome.action.setBadgeTextColor({ color: "#FFFFFF" });
    return;
  }
  const dayStr = String(new Date().getDate());
  chrome.action.setBadgeText({ text: dayStr });
  chrome.action.setBadgeBackgroundColor({ color: "#C8962E" });
  chrome.action.setBadgeTextColor({ color: "#101014" });
}

async function checkUpcomingReminders() {
  await updateToolbarBadge();
  const data = await chrome.storage.local.get([STORAGE_STATE_KEY, FIRED_NOTIF_KEY]);
  const state = data[STORAGE_STATE_KEY];
  if (!state || !Array.isArray(state.events)) return;

  const fired = data[FIRED_NOTIF_KEY] || {};
  const now = Date.now();
  const today = new Date();
  const checkDates = [
    new Date(today.getFullYear(), today.getMonth(), today.getDate()),
    new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1)
  ];
  let changed = false;

  for (const ev of state.events) {
    if (ev.isPrivacy || ev.isPaused || ev.isAllDay) continue;
    const origStart = new Date(ev.startEpochMillis);
    const baseDate = new Date(origStart.getFullYear(), origStart.getMonth(), origStart.getDate());
    let offsets = [0];
    try {
      const parsed = JSON.parse(ev.reminderMinutesJson || "[0]");
      if (Array.isArray(parsed) && parsed.length) offsets = parsed;
    } catch (_) {
      offsets = [0];
    }

    for (const candDate of checkDates) {
      if (!DXCore.matchesRecurrence(baseDate, candDate, ev.recurrenceType, ev.extraDatesJson, ev.recurrenceEndEpochMillis)) {
        continue;
      }
      const occStartMs = new Date(
        candDate.getFullYear(), candDate.getMonth(), candDate.getDate(),
        origStart.getHours(), origStart.getMinutes(), 0
      ).getTime();

      for (const minBefore of offsets) {
        const triggerMs = occStartMs - minBefore * 60000;
        const key = `${ev.id}_${DXCore.toIso(candDate)}_${minBefore}`;
        if (!fired[key] && triggerMs <= now && now - triggerMs <= 90000) {
          fired[key] = now;
          changed = true;
          const whenText = minBefore === 0 ? "現在開始" : `還有 ${minBefore} 分鐘`;
          chrome.notifications.create(key, {
            type: "basic",
            iconUrl: "icons/icon128.png",
            title: `DX日曆提醒 · ${whenText}`,
            message: ev.title + (ev.location ? `（地點：${ev.location}）` : ""),
            priority: 2
          });
        }
      }
    }
  }

  for (const k of Object.keys(fired)) {
    if (now - fired[k] > 86400000) {
      delete fired[k];
      changed = true;
    }
  }
  if (changed) {
    await chrome.storage.local.set({ [FIRED_NOTIF_KEY]: fired });
  }
}

async function handlePomodoroDone() {
  const data = await chrome.storage.local.get(POMO_STATE_KEY);
  const pomo = data[POMO_STATE_KEY];
  if (!pomo || !pomo.running) return;
  const label = pomo.taskTitle ? `「${pomo.taskTitle}」專注完成！` : "專注時段結束，起來伸展休息一下吧！";
  await chrome.storage.local.set({
    [POMO_STATE_KEY]: {
      ...pomo,
      running: false,
      endAtMs: null,
      pausedLeftSec: pomo.totalSec || 1500
    }
  });
  chrome.notifications.create(`pomo_${Date.now()}`, {
    type: "basic",
    iconUrl: "icons/icon128.png",
    title: "🍅 DX日曆番茄鐘完成！",
    message: label,
    priority: 2
  });
  await updateToolbarBadge();
}

chrome.runtime.onInstalled.addListener(() => {
  updateToolbarBadge();
  chrome.alarms.create("dx_reminder_tick", { periodInMinutes: 1 });
});

chrome.runtime.onStartup.addListener(() => {
  updateToolbarBadge();
  chrome.alarms.create("dx_reminder_tick", { periodInMinutes: 1 });
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === "dx_reminder_tick") {
    checkUpcomingReminders();
  } else if (alarm.name === "dx_pomo_done") {
    handlePomodoroDone();
  }
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes[POMO_STATE_KEY]) {
    updateToolbarBadge();
  }
});
