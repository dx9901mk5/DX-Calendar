/**
 * DX日曆 Chrome 插件 — Manifest V3 背景服務（動態當日日期徽章＋桌面提醒通知）
 */
const STORAGE_STATE_KEY = "dx_calendar_state_v2";
const FIRED_NOTIF_KEY = "dx_fired_notifs_v2";

function updateToolbarDayBadge() {
  const dayStr = String(new Date().getDate());
  chrome.action.setBadgeText({ text: dayStr });
  chrome.action.setBadgeBackgroundColor({ color: "#C8962E" });
  chrome.action.setBadgeTextColor({ color: "#101014" });
}

async function checkUpcomingReminders() {
  updateToolbarDayBadge();
  const data = await chrome.storage.local.get([STORAGE_STATE_KEY, FIRED_NOTIF_KEY]);
  const state = data[STORAGE_STATE_KEY];
  if (!state || !Array.isArray(state.events)) return;

  const fired = data[FIRED_NOTIF_KEY] || {};
  const now = Date.now();
  let changed = false;

  for (const ev of state.events) {
    if (ev.isPrivacy || ev.isPaused || ev.isAllDay) continue;
    let offsets = [0];
    try {
      offsets = JSON.parse(ev.reminderMinutesJson || "[0]");
    } catch (_) {
      offsets = [0];
    }
    for (const minBefore of offsets) {
      const triggerMs = ev.startEpochMillis - minBefore * 60000;
      const key = `${ev.id}_${triggerMs}`;
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

  // 定期清理 24 小時前的觸發記錄
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

chrome.runtime.onInstalled.addListener(() => {
  updateToolbarDayBadge();
  chrome.alarms.create("dx_reminder_tick", { periodInMinutes: 1 });
});

chrome.runtime.onStartup.addListener(() => {
  updateToolbarDayBadge();
  chrome.alarms.create("dx_reminder_tick", { periodInMinutes: 1 });
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === "dx_reminder_tick") {
    checkUpcomingReminders();
  }
});
