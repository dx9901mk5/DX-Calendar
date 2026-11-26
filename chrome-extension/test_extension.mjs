/**
 * DX日曆 Chrome 擴充功能自動化驗證測試套件（純 Node.js，免外部相依套件）
 * 執行方式：node chrome-extension/test_extension.mjs
 */
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const EXT_DIR = path.dirname(fileURLToPath(import.meta.url));

function loadScriptIntoContext(filename, ctx) {
  const code = fs.readFileSync(path.join(EXT_DIR, filename), "utf8");
  vm.runInContext(code, ctx, { filename });
}

let passed = 0;
function test(name, fn) {
  fn();
  passed++;
  console.log(`  [PASS] ${name}`);
}

console.log("Running DX-Calendar Chrome Extension test suite...");

// 1. Manifest V3 & files integrity
const manifest = JSON.parse(fs.readFileSync(path.join(EXT_DIR, "manifest.json"), "utf8"));
test("manifest.json is valid MV3 with icons, side_panel, commands, and service_worker", () => {
  assert.equal(manifest.manifest_version, 3);
  assert.ok(manifest.description.length <= 132, `Chrome Web Store description limit is 132 chars, got ${manifest.description.length}`);
  assert.equal(manifest.background.service_worker, "background.js");
  assert.equal(manifest.side_panel.default_path, "popup.html?mode=sidepanel");
  assert.ok(manifest.commands._execute_action);
  for (const size of ["16", "48", "128"]) {
    const iconPath = path.join(EXT_DIR, manifest.icons[size]);
    assert.ok(fs.existsSync(iconPath), `Missing icon ${iconPath}`);
    const png = fs.readFileSync(iconPath);
    assert.equal(png.readUInt32BE(0), 0x89504e47, "Must be valid PNG");
    assert.equal(png.readUInt32BE(16), Number(size), `Width must be ${size}`);
    assert.equal(png.readUInt32BE(20), Number(size), `Height must be ${size}`);
  }
});

// 2. Load lunar.js + core.js + nlp.js into VM
const ctx = vm.createContext({ console, Date, Intl, Math, JSON, Number, String, Array, Object, Set, Map, RegExp, Error, parseInt });
loadScriptIntoContext("lunar.js", ctx);
loadScriptIntoContext("core.js", ctx);
loadScriptIntoContext("nlp.js", ctx);

const { DXLunar, DXCore, DXNlp } = vm.runInContext("({ DXLunar, DXCore, DXNlp })", ctx);

test("DXLunar accurately computes Taiwan National Day & Lunar info", () => {
  const oct10 = DXLunar.getLunarInfo(2026, 10, 10);
  assert.equal(oct10.shortDisplay, "國慶日");
  assert.equal(oct10.isHoliday, true);
  assert.ok(oct10.ganZhi.length >= 2);
});

test("DXNlp parses Chinese date, time, reminder, lunar recurrence, countdown, and task", () => {
  const base = new Date(2026, 10, 26); // 2026-11-26
  const p1 = DXNlp.parse("明天下午3點半牙醫回診 提前15分", base);
  assert.equal(p1.dateIso, "2026-11-27");
  assert.equal(p1.startTime, "15:30");
  assert.equal(p1.title, "牙醫回診");
  assert.deepEqual(p1.reminderMinutes, [0, 15]);
  assert.equal(p1.isTaskMode, false);

  const p2 = DXNlp.parse("農曆初一十五拜拜 09:00", base);
  assert.equal(p2.recurrenceType, "LUNAR_FIRST_FIFTEENTH");
  assert.equal(p2.startTime, "09:00");
  assert.equal(p2.title, "拜拜");

  const p3 = DXNlp.parse("待辦 繳交水電費 12/05", base);
  assert.equal(p3.isTaskMode, true);
  assert.equal(p3.dateIso, "2026-12-05");
  assert.equal(p3.title, "繳交水電費");

  const p4 = DXNlp.parse("倒數 12/31 跨年煙火", base);
  assert.equal(p4.isCountdown, true);
  assert.equal(p4.dateIso, "2026-12-31");
});

test("DXCore.matchesRecurrence supports EVERY_N_WEEKS, LUNAR_FIRST_FIFTEENTH, and extraDatesJson", () => {
  const base = new Date(2026, 10, 1);
  assert.equal(DXCore.matchesRecurrence(base, new Date(2026, 10, 22), "EVERY_N_WEEKS:3"), true);
  assert.equal(DXCore.matchesRecurrence(base, new Date(2026, 10, 15), "EVERY_N_WEEKS:3"), false);
  assert.equal(DXCore.matchesRecurrence(base, new Date(2026, 10, 18), "NONE", '["2026-11-18"]'), true);

  // Verify at least 2 lunar 1st/15th matches within a 32-day span
  let lunarHits = 0;
  for (let d = 1; d <= 32; d++) {
    if (DXCore.matchesRecurrence(new Date(2026, 0, 1), new Date(2026, 0, d), "LUNAR_FIRST_FIFTEENTH")) {
      lunarHits++;
    }
  }
  assert.ok(lunarHits >= 2, `Expected >=2 lunar 1st/15th hits, got ${lunarHits}`);
});

test("DXCore.formatCountdownBadge calculates countdown days, birthday age, and anniversary years", () => {
  const bday = DXCore.formatCountdownBadge(
    "2026-12-01",
    { isCountdown: true, isBirthday: true, anniversaryStartYear: 1996 },
    "2026-11-26"
  );
  assert.ok(bday.includes("還有 5 天"));
  assert.ok(bday.includes("🎂 30 歲"));

  const anniv = DXCore.formatCountdownBadge(
    "2026-11-26",
    { isCountdown: true, isBirthday: false, anniversaryStartYear: 2020 },
    "2026-11-26"
  );
  assert.ok(anniv.includes("🎯 就是今天"));
  assert.ok(anniv.includes("💍 第 6 週年"));
});

test("DXCore lossless Android v2.0.0 .dxbackup.json round-trip & smart merge preserves all Android fields", () => {
  const androidPayload = {
    schemaVersion: 2,
    appId: "com.dx.calendar",
    deviceName: "vivo_X200",
    exportedAtMillis: 1795600000000,
    categories: [{ id: 1, name: "本機活動", colorArgb: -3631570 }],
    events: [
      { id: 101, title: "手機行程", startEpochMillis: 1795651200000, endEpochMillis: 1795654800000, isPrivacy: false },
      { id: 102, title: "ENC:AES256_SECRET", startEpochMillis: 1795651200000, endEpochMillis: 1795654800000, isPrivacy: true }
    ],
    tasks: [{ id: 201, title: "買咖啡", dueEpochDay: 20783, isCompleted: false, isPrivacy: false }],
    routineCheckIns: [{ targetType: "EVENT", targetId: 101, epochDay: 20783, completedAt: 1795651200000 }],
    externalOverrides: [{ externalKey: "EVENT_ID:99", showInMonthGrid: false, isRoutine: true }],
    templates: [{ id: "tpl_custom", name: "自訂值班", startHour: 8, startMinute: 0, durationMinutes: 480, colorArgb: -3631570 }],
    richMeta: {
      "LOCAL_EVENT:101": {
        key: "LOCAL_EVENT:101",
        isCountdown: true,
        sharedComments: [{ id: "c1", author: "vivo_X200", content: "記得帶證件", createdAtMillis: 1795651200000 }]
      }
    }
  };

  const chromeInitial = DXCore.normalizeState({
    deviceName: "Chrome-Office",
    events: [{ id: 501, title: "電腦端簡報", startEpochMillis: 1795700000000, endEpochMillis: 1795703600000, isPrivacy: false }],
    richMeta: {
      "LOCAL_EVENT:101": {
        key: "LOCAL_EVENT:101",
        isCountdown: true,
        sharedComments: [{ id: "c2", author: "Chrome-Office", content: "已影印備份", createdAtMillis: 1795655000000 }]
      }
    }
  });

  const merged = DXCore.importBackupPayload(chromeInitial, androidPayload, "merge");
  const exported = DXCore.buildBackupPayload(merged);

  assert.equal(exported.schemaVersion, 2);
  assert.equal(exported.appId, "com.dx.calendar");
  assert.equal(exported.categories.length, 1);
  assert.equal(exported.routineCheckIns.length, 1);
  assert.equal(exported.externalOverrides.length, 1);
  assert.equal(exported.events.length, 3, "Should keep Chrome event + Android public + Android privacy event");
  assert.ok(exported.events.some((e) => e.id === 102 && e.isPrivacy === true), "Privacy event must survive round-trip");
  assert.equal(exported.richMeta["LOCAL_EVENT:101"].sharedComments.length, 2, "Comments from both devices should merge");
});

test("DXCore ICS export and re-import round-trip skips privacy items", () => {
  const ics = DXCore.exportToIcs([
    { id: 1, title: "團隊週會", location: "會議室A", notes: "帶筆電", startEpochMillis: Date.UTC(2026, 10, 26, 2, 0, 0), endEpochMillis: Date.UTC(2026, 10, 26, 3, 0, 0), isAllDay: false, recurrenceType: "EVERY_N_WEEKS:3", isPrivacy: false },
    { id: 2, title: "最高機密", startEpochMillis: Date.UTC(2026, 10, 26, 4, 0, 0), endEpochMillis: Date.UTC(2026, 10, 26, 5, 0, 0), isAllDay: false, isPrivacy: true }
  ]);
  assert.ok(ics.includes("SUMMARY:團隊週會"));
  assert.ok(ics.includes("RRULE:FREQ=WEEKLY;INTERVAL=3"));
  assert.ok(!ics.includes("最高機密"), "Privacy events must NEVER be exported to ICS");

  const reimported = DXCore.importFromIcs(ics);
  assert.equal(reimported.length, 1);
  assert.equal(reimported[0].title, "團隊週會");
  assert.equal(reimported[0].location, "會議室A");
  assert.equal(reimported[0].recurrenceType, "EVERY_N_WEEKS:3");
});

test("popup.html DOM IDs match all getElementById calls in app.js", () => {
  const html = fs.readFileSync(path.join(EXT_DIR, "popup.html"), "utf8");
  const appJs = fs.readFileSync(path.join(EXT_DIR, "app.js"), "utf8");
  const ids = new Set(Array.from(appJs.matchAll(/getElementById\("([^"]+)"\)/g)).map((m) => m[1]));
  for (const id of ids) {
    assert.ok(html.includes(`id="${id}"`), `popup.html is missing id="${id}" referenced in app.js`);
  }
});

console.log(`\nAll ${passed} Chrome Extension tests PASSED!`);
