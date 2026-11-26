# DX日曆 (DX-Calendar) | 全尺寸奢華金主題智慧日曆 ＋ Chrome 跨系統伴侶插件

[🇹🇼 繁體中文](#繁體中文) | [🧩 Chrome 擴充功能](#chrome-extension) | [🇺🇸 English](#english) | [👨‍💻 關於我 / About Me](#about-me)

[![Release](https://img.shields.io/github/v/release/dx9901mk5/DX-Calendar?color=C8962E&label=Latest%20Release)](https://github.com/dx9901mk5/DX-Calendar/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%7C%20Foldable%20%7C%20Tablet%20%7C%20Chrome%20Extension-00B884)](https://github.com/dx9901mk5/DX-Calendar)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3%20%2B%20Manifest%20V3-0D84FF)](https://developer.android.com/jetpack/compose)

---

<a id="繁體中文"></a>
## 🇹🇼 繁體中文

> **一款專為手機、摺疊機、8 吋／10 吋平板、Chromebook 與跨系統 Chrome／Edge 瀏覽器打造的次世代智慧日曆。融合奢華金白／金色黑底自動主題、全 App Q 彈流暢手勢動畫、60fps 高解析含天氣桌面小工具、100% 離線自然語言與語音快記、農曆初一十五與生日算歲數、輪班連點排班、雙時區 24h 時間軸、番茄鐘與 Google 雲端硬碟無損跨裝置備份，以及無痕雙指紋隱私日曆。**

### 📲 下載安裝

- **Android App（手機／摺疊機／平板／Chromebook）**：前往 [**GitHub Releases 最新發行頁面**](https://github.com/dx9901mk5/DX-Calendar/releases/latest) 下載最新正式簽章版 APK（內建 **GitHub 自動檢查更新**，發現新版會彈窗提示確認並附進度條下載安裝）。
- **電腦端跨系統伴侶（Chrome / Edge / Brave 擴充功能）**：已全面改採跨平台 **Manifest V3 Chrome 插件**（免安裝原生桌面程式，一次通吃 Windows / macOS / Linux / Chromebook），詳見下方 [🧩 Chrome 擴充功能安裝說明](#chrome-extension) 或 [`chrome-extension/README_CHROME_EXTENSION.md`](chrome-extension/README_CHROME_EXTENSION.md)。

---

### ✨ Android `v2.0.0` 核心功能特色（已全部實裝）

#### 1. 🎙️ 100% 本機離線自然語言＋語音快速建行程（0 費用、免 API）
- 純本機時間詞法引擎，免外接 LLM API、免額外 OAuth、零延遲。
- 輸入或語音說出一句話（如「`明天下午3點半開會 提前15分`」、「`農曆初一十五拜拜`」、「`下週二 14:00-15:30 牙醫`」、「`倒數 12/31 跨年煙火`」），即時預覽並一鍵建立行程、待辦或倒數日。

#### 2. 🎬 Q 彈手勢動畫、拖曳改期與全尺寸響應式體驗
- **全 App 統一 Q 彈物理動畫**：設定頁、編輯頁、浮動 24 小時時間軸卡片、行程詳情卡皆具流暢彈性過渡；`年 / 月 / 週 / 日 / 時間表` 支援方向性縮放過渡與跟手水平滑動換月。
- **滿版月曆 ⇄ 彩條壓縮手勢連動**：月檢視 1:1 跟手上下滑動壓縮為彩條提示＋下方當日時間表；再點已選日期 Q 彈展開 24h 浮動時間軸卡片。
- **行程拖曳改期（Drag & Drop）**：月檢視長按事件即可點擊目標日期快速搬移；週／日 24h 時間軸支援直接上下拖曳以 15 分鐘刻度調整時段。
- **全裝置與摺疊機專屬優化**：手機常駐 `now` 回今天與醒目今日膠囊；摺疊機展開與 8 吋平板左右雙欄＋編輯頁雙欄並排；10 吋平板／Chromebook 三欄分類側欄＋鍵盤快捷鍵。

#### 3. 🎨 奢華金色亮暗雙主題 ＋ 1–31 動態日期 App 圖示
- 跟隨系統自動切換「**金色白底（亮色）**」與「**金色灰黑底（暗色）**」，內建多套風格與自訂半透明月曆背景底圖。
- 全新金色幾何日曆 App 圖示同步隨系統亮暗色變換，並內建 **1–31 日動態日期向量圖示**，午夜自動跨日刷新。

#### 4. 🌤️ 預設當日起 7 天月曆天氣 ＋ 高解析含天氣桌面小工具
- 內建免 API Key Open-Meteo 7 天預報（依時區自動對應＋3 小時快取），於月曆與小工具日期格右上角呈現 7 天天氣圖示與詳情高低溫。
- **DX 月曆小工具**：**2,400,000 px** 旗艦高 DPI 1:1 渲染，背景 `0%–100%` 透明而文字維持 100% 實心高對比＋微距陰影；支援 **60fps 點擊週次展開**，**直接點擊展開週中的事件卡片即直達 App 事件詳情**。
- **擴充桌面小工具**：同步提供 **DX 時間表小工具**、**DX 下一行程與天氣橫條（4×1）** 與 **DX 倒數日與天氣小工具（2×2）**。

#### 5. 🏷️ 輪班範本連點排班 ＋ 倒數日算歲數 ＋ 圖片附件與導航 ＋ 協作留言
- **輪班與常用範本（Stamp Mode）**：內建早班／小夜班／大夜班／休假／健身並可自訂新增，啟用連點排班後直接點擊日期格即蓋章排完整月班表。
- **倒數日、紀念日與生日自動算歲數**：標記起始西元年自動計算「還有 X 天」、「🎂 今年 N 歲」或「💍 第 N 週年」。
- **行程圖片附件與一鍵 Google Maps 導航**：活動可附加多張車票／預約照片截圖，點擊地點直接開啟 Google Maps 導航。
- **家庭／情侶協作留言板**：行程詳情內建署名留言板，隨整庫備份跨裝置同步。

#### 6. 🌙 完整農曆重複（初一十五／農曆生日）＋ 雙時區時間軸 ＋ 番茄鐘
- **11 種重複規則**：支援每天／平日／每週／**隔 N 週（`FREQ=WEEKLY;INTERVAL=N`）**／每月／每年／**農曆每月**／**農曆初一與十五**／**農曆每年**，以及多組不連續日期批次建立。
- **雙時區 24h 時間軸**：週／日時間軸左側可並排對照第二時區刻度，跨國會議與出差一目了然。
- **待辦時間塊（Time Blocking）＋ 環形番茄鐘**：待辦可一鍵「⏰ 排時段」排入當日時間軸，或開啟「🍅 專注」倒數計時器（25/15/45/5 分）。

#### 7. 🔄 例行事務不佔月曆格 ＋ 多重連段追擊鬧鐘 ＋ 國際版 Live Updates
- **例行中心**：每日吃藥、繳費、運動可個別設為「不在月曆佔空間」（連外部同步的 Google 重複行程也可單獨隱藏收納），集中打卡、暫停與清理。
- **多重連段追擊強提醒**：複選多組提早時間、每隔數分鐘連響追擊直到確認、鎖屏全螢幕鬧鐘聲道強響、連動系統時鐘鬧鐘，並遵循國際版 **Android 16+ Live Updates** 即時動態通知規範。

#### 8. ✅ Google 行事曆／Tasks 雙向同步 ＋ 自訂裝置名稱雲端硬碟備份 ＋ 雙指紋隱私日曆
- **Google 生態與自動時區**：防抖即時監聽 Google 行事曆（含語音助理新增）、支援多組 Gmail 帳號與 Google Tasks、標準 `.ics` 匯出入與 URL 訂閱、自動判定所在地時區與 1900–2100 區域農曆黃曆。
- **自訂裝置名稱整庫備份 ＋ Google 雲端硬碟連動**：上方自訂「裝置名稱」自動寫入 `.dxbackup.json` 檔名與協作留言署名，一鍵連動 Google 雲端硬碟 App 上傳並與 Chrome 插件無損互通。
- **零痕跡雙指紋「隱私日曆」**：設定頁啟用前全 App 零痕跡；啟用後僅呈現閃爍「眼睛」按鈕，底層強制連續 2 枚指紋驗證＋Keystore `AES-256-GCM` 硬體加密，關閉 App、切換視窗或關閉螢幕瞬間自動隱藏。

---

<a id="chrome-extension"></a>
## 🧩 Chrome 擴充功能 — 官方電腦端跨系統伴侶版（v2.1.0）

我們已正式放棄單一平台的 Windows 桌面程式，改以 **Chrome / Edge / Brave 擴充功能（Manifest V3）** 作為 DX日曆官方電腦端伴侶，一次支援 **Windows / macOS / Linux / Chromebook**！

- **三形態桌面體驗**：
  1. **工具列彈出月曆**（快捷鍵 `Alt+Shift+D`，工具列圖示即時顯示今日日期徽章，番茄鐘運作中自動顯示剩餘分鐘）。
  2. **Chrome 側邊欄（Side Panel）**：常駐於瀏覽器右側一邊上網一邊排程。
  3. **獨立雙欄桌面視窗（點右上角 `⛶`）**：無網址列干擾的獨立視窗，左欄大月曆直接呈現行程晶片、右欄並排顯示詳情與 24h 雙時區時間軸，完整取代原生桌面日曆！
- **完整電腦端效率功能**：1900–2100 農曆節氣、7 天月曆天氣、離線自然語言與語音快記、完整編輯器與家庭協作留言板、月曆拖曳改期（Drag & Drop）、11 種重複規則背景桌面提醒、輪班連點排班、倒數日自動算歲數、背景持久化番茄鐘（關掉視窗依然準時響鈴）、標準 `.ics` 匯出入，以及與 Android `v2.0.0` **100% 無損互通的 `.dxbackup.json` 智慧合併／覆蓋還原**。

### 🚀 10 秒載入使用（Chrome / Edge）
1. 開啟 `chrome://extensions/`（Edge 開啟 `edge://extensions/`）。
2. 開啟右上角 **「開發人員模式」** → 點擊 **「載入未封裝項目」**。
3. 選擇本專案內的 [`chrome-extension`](chrome-extension/) 資料夾即可立即使用！

---

<a id="english"></a>
## 🇺🇸 English

> **A next-generation adaptive smart calendar for Android phones, foldables, tablets, Chromebooks, and cross-platform Chrome/Edge browsers. Featuring luxury Gold-on-White / Gold-on-Black auto themes, playful spring-physics animations, 60fps high-DPI weather widgets, 100% offline natural-language & voice quick add, lunar recurrences (1st/15th & lunar birthdays), shift stamping, dual-timezone 24h timelines, Pomodoro focus timers, lossless Google Drive cross-device backups, and a zero-trace Dual-Fingerprint Privacy Vault.**

### 📲 Download & Install
- **Android App**: Download the latest signed APK from [**GitHub Releases**](https://github.com/dx9901mk5/DX-Calendar/releases/latest) (includes built-in **GitHub Update Checker** with confirmation dialog and live progress bar).
- **Cross-Platform Desktop Companion (Chrome / Edge / Brave Extension)**: Replaces the former Windows-only desktop app plan so **Windows, macOS, Linux, and Chromebook** users all get the full companion experience in [`chrome-extension/`](chrome-extension/README_CHROME_EXTENSION.md) (`chrome://extensions/` → Developer Mode → Load unpacked).

### ✨ Shipped Highlights (`Android v2.0.0` + `Chrome Extension v2.1.0`)
- **🎙️ 100% Offline Natural-Language & Voice Quick Add** (zero LLM API cost / zero extra OAuth): Type or speak phrases like *"Tomorrow 3:30pm dentist remind 15m early"* or *"Lunar 1st & 15th offering"* for instant zero-latency parsing.
- **🎬 Spring Animations, Drag & Drop Rescheduling & Adaptive Multi-Pane UI**: Unified bouncy transitions, 1:1 vertical month-to-agenda swipe gesture, drag-to-reschedule on Month cells & 15-min snap dragging on 24h timelines, plus dual-pane / 3-pane layouts for foldables, tablets, and Chromebooks.
- **🎨 Luxury Gold Auto Theme & Dynamic 1–31 Day Icons**: Automatic Gold-on-White (Light) / Gold-on-Graphite-Black (Dark) themes and 31 dynamic day-of-month adaptive vector icons.
- **🌤️ Built-in 7-Day Weather & Expanded High-DPI Widgets**: Zero-API-key Open-Meteo 7-day forecast on Month view & widgets, **2,400,000 px** transparent Month Widget with 60fps week-expand + direct event tap, Schedule Widget, **Next Event + Weather Bar (4×1)**, and **Countdown + Weather Widget (2×2)**.
- **🏷️ Shift Stamping, Countdowns/Age Tracker, Attachments, Maps & Family Comments**: One-tap shift template stamping, automatic birthday age / anniversary year calculation, photo attachments, one-tap Google Maps navigation, and synced family/couple event comments.
- **🌙 Lunar Recurrences, Dual-Timezone Timeline & Task Time-Blocking + Pomodoro**: Native Lunar Monthly / Lunar 1st & 15th / Lunar Yearly / Every-N-Weeks recurrences, side-by-side dual-timezone 24h scale, one-tap task time-blocking, and focus Pomodoro timer.
- **🔄 Clutter-Free Routine Hub, Escalating Alarms & International Android 16+ Live Updates**: Per-item hide-from-month-grid control (even for external Google Calendar series), multi-call burst alarms, native Clock sync, and standard Android 16+ Live Updates.
- **☁️ Lossless Android ⇄ Chrome Backup Sync & Dual-Fingerprint Privacy Vault**: Named-device `.dxbackup.json` (Schema v2) with direct Google Drive app upload and lossless smart-merge in Chrome Extension (`Popup`, `Side Panel`, and standalone dual-pane `App Window` modes), plus hardware `AES-256-GCM` Dual-Fingerprint Privacy Vault with instant auto-lock on screen off or app switch.

---

<a id="about-me"></a>
## 👨‍💻 關於我 / About Me

### 🇹🇼 關於開發者 (`dx9901mk5`)
我是 **dx9901mk5**，熱愛深度打磨原生介面動畫、跨裝置生產力（手機／摺疊機／平板／車機／Chromebook／Chrome 瀏覽器擴充功能）與解決真實日常痛點的獨立開發者。堅持「無廣告干擾、尊重隱私、高效率與極致順手」的設計理念，持續打造乾淨俐落的 **DX 系列工具生態**。

歡迎造訪我的 GitHub 主頁探索更多專案：
- 🔗 **GitHub 主頁**：[https://github.com/dx9901mk5](https://github.com/dx9901mk5)
- 📅 [**DX-Calendar**](https://github.com/dx9901mk5/DX-Calendar) — 全尺寸奢華金主題智慧日曆 ＋ Chrome 跨系統插件（本專案）
- 🚗 [**DX-Car-Launcher**](https://github.com/dx9901mk5/DX-Car-Launcher) — Android Auto (Coolwalk) 風格車載 AI Box 桌面啟動器
- 🎙️ [**DxVoice-IME**](https://github.com/dx9901mk5/DxVoice-IME) — 離線 AI 智慧語音輸入法（說完自動切回原鍵盤＋懸浮語音球）
- 📁 [**DX_fileManager**](https://github.com/dx9901mk5/DX_fileManager) — 多面板與自由小視窗 Android 檔案管理器
- 🌀 [**FanRecentSwitcher**](https://github.com/dx9901mk5/FanRecentSwitcher) — 單手扇形最近任務切換器與邊緣手勢桌面
- 🛡️ [**PhotoSyncGuard**](https://github.com/dx9901mk5/PhotoSyncGuard) / [**CallMusicGuard**](https://github.com/dx9901mk5/CallMusicGuard) / [**PixelAI-Bridge**](https://github.com/dx9901mk5/PixelAI-Bridge)

### 🇺🇸 About the Developer (`dx9901mk5`)
Hi, I'm **dx9901mk5** — an independent developer dedicated to crafting fluid, privacy-respecting productivity tools across phones, foldables, tablets, car head units, Chromebooks, and Chrome browser extensions.

Explore more projects from the **DX Ecosystem** at [https://github.com/dx9901mk5](https://github.com/dx9901mk5).

---

## 🛠️ Build & Test from Source

```powershell
# Android APK & Unit Tests
.\gradlew.bat testGithubDebugUnitTest assembleGithubRelease

# Chrome Extension Automated Tests & Chrome Web Store ZIP Packaging
node chrome-extension/test_extension.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File chrome-extension/package_extension.ps1
```
