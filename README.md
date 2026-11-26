# DX日曆 (DX-Calendar) | 全尺寸多主題智慧日曆 · Adaptive Multi-Theme Smart Calendar

[🇹🇼 繁體中文](#繁體中文) | [🇺🇸 English](#english) | [👨‍💻 關於我 / About Me](#about-me)

[![Release](https://img.shields.io/github/v/release/dx9901mk5/DX-Calendar?color=FF5238&label=Latest%20Release)](https://github.com/dx9901mk5/DX-Calendar/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%7C%20Tablet%20%7C%20Chromebook-00B884)](https://github.com/dx9901mk5/DX-Calendar)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-0D84FF)](https://developer.android.com/jetpack/compose)

---

<a id="繁體中文"></a>
## 🇹🇼 繁體中文

> **以 vivo OriginOS 日曆美學為基底、融合 Google 日曆高效率體驗的全尺寸 Android 智慧日曆。專為手機、摺疊機、8 吋平板、10 吋平板與 Pixelbook (Chromebook) 自由視窗打造，內建五大品牌主題、桌面任意縮放小工具、Google Tasks 整合、例行事務獨立隱藏中心、多重連段鬧鐘呼叫與雙指紋鎖隱私日曆。**

### 📲 下載安裝 (GitHub Releases)

前往 [**GitHub Releases 最新發行頁面**](https://github.com/dx9901mk5/DX-Calendar/releases/latest) 下載最新簽章版 APK 直接安裝即可使用（App 設定內建 **GitHub 自動檢查與進度條一鍵下載更新**）。

### ✨ 核心特色

- **🖥️ 全尺寸與自由小視窗響應式設計（Phone / 8" & 10" Tablet / Pixelbook）**
  - 自動偵測視窗實際寬高動態切換版面：**8 吋平板與摺疊機展開**採用 vivo 經典左右雙欄（左主視圖＋右農曆干支、節氣詩詞卡與活動詳情）；**10 吋大平板與 Pixelbook** 支援三欄常駐分類篩選與外接鍵盤方向鍵／翻頁快捷鍵；**手機與懸浮小視窗**（最小支援至 `300×360dp`）則提供緊湊單欄與可一鍵收合的當日行程面板。
  - 完整支援 **年、月、週、日、時間表** 五種檢視模式，內建精準 1900–2100 農曆、天干地支生肖、二十四節氣、傳統黃曆宜忌與台灣節慶假日。
- **🎨 金白／金色灰黑跟隨系統預設主題 ＋ 仿V/O/M/G/S 主題風格**
  - 預設採用**跟隨手機系統主題自動移動切換**：明亮模式為 **金白風格**（純淨珍珠白底＋18K 香檳曜金），暗色模式自動切換為 **金色灰黑風格**（沉穩石墨灰黑底 `#16171B`＋璀璨香檳金，溫潤不死黑）；另可自由切換 **仿V**、**仿O**、**仿M**、**仿G**、**仿S** 等風格與自訂月曆底圖。
- **🧩 精選兩款高解析桌面自由縮放小工具（DX 月曆 ＆ DX 時間表）**
  - **DX 月曆小工具**：2K 級原生高解析次像素抗鋸齒字體渲染，支援桌面點擊任意一週平滑展開／收合當週詳細行程動畫、點 `‹` `›` 翻月、`now` 回當日、`⚙` 直調 0%–100% 透明度與自由縮放。
  - **DX 時間表小工具**：可上下捲動瀏覽近期行程與待辦事項，點擊事件直達詳情，支援 0%–100% 自由透明度與縮放。
- **🔄 例行事務「月曆顯示獨立開關」＋「例行事務與隱藏提醒中心」**
  - 徹底解決傳統日曆（如簡單日曆）只能按整個帳號開關、或每日例行事項把月曆塞滿的痛點！建立提醒時可直接選擇 **「顯示於月曆格」** 或 **「不在月曆佔空間」**（如每日吃藥提醒不佔月曆空間，仍照常響鈴提醒）；連 Google 帳號同步進來的重複活動也能單獨將該系列隱藏收納。
  - 專屬 **「例行中心」** 集中納管所有例行事務與隱藏提醒，支援**每日完成打卡**、**一鍵暫停／恢復提醒**與**一鍵清理過期累積舊項目**，並可在主畫面頂部點選眼睛圖示「暫時透視」所有隱藏例行項目。
- **⏰ 多重呼叫（連段追擊提醒）與連動系統鬧鐘**
  - 提醒時間**支援多選**（活動時間、1/3/5/10/15/20/30/45 分、1～6 小時、1～7 天前可複選），並支援**多個不連續日期批次建立活動**。
  - 可開啟 **「連段追擊呼叫」**（設定連響次數與追擊間隔分鐘，直到手動確認停止為止）、**鎖屏全螢幕鬧鐘音軌強提醒**，以及勾選 **「同步連動手機系統鬧鐘 App」** 自動建立原生時鐘鬧鐘。
- **✅ Google Tasks 待辦整合 ＋ 系統日曆雙向同步 ＋ ICS / URL 匯入匯出**
  - 連結裝置 Google 帳號即可雙向同步 **Google Tasks**，將待辦圓圈直接混排顯示於月曆格、時間表與桌面小工具中並支援一鍵打勾；同時支援標準 `.ics` 檔案匯入匯出與網址訂閱。
- **🔐 雙指紋鎖「隱私日曆」獨立加密保險箱**
  - 開啟隱私日曆時強制執行 **第一道指紋 (1/2) → 第二道指紋 (2/2)** 連續雙重生物辨識驗證（防止單指誤觸，並提供雙重隱私 PIN 碼備援）。
  - 隱私密件採用 Android Keystore `AES-256-GCM` 硬體級加密儲存，未解鎖或切換至背景時立即自動上鎖，絕不外洩於一般月曆、搜尋結果或桌面小工具。
- **📅 動態日期 App 圖示（1–31 日）＋ 🏝️ Android 16 Live Updates 原子島通知上島**
  - 內建 31 組向量自適應日曆圖示，依當下日期自動切換桌面 App 圖示顯示當日日期（1–31），並支援跨日午夜自動刷新與設定頁自由開關。
  - 原生實作 **Android 16 Live Updates**（`POST_PROMOTED_NOTIFICATIONS`、`setRequestPromotedOngoing(true)`、`setShortCriticalText`、`Notification.ProgressStyle`），免白名單即可同時登上**台版與陸版 vivo OriginOS 6 原子島**，設定頁內建權限狀態檢查、系統上島開關直達與一鍵測試上島功能。
- **🔄 內建 GitHub 連結與自動更新下載器（附即時進度條）**
  - 設定頁內建專案 GitHub 直達連結，支援啟動與進入設定時**自動判讀 GitHub 最新 Release 版本**，發現新版即自動下載並顯示即時百分比／MB 進度條，完成後自動喚起系統安裝覆蓋更新。

---

<a id="english"></a>
## 🇺🇸 English

> **An adaptive, multi-theme Android smart calendar built upon the vivo OriginOS aesthetic and supercharged with Google Calendar productivity. Engineered seamlessly for phones, foldables, 8" & 10" tablets, Pixelbook (Chromebook), and freeform multi-window environments—featuring 5 brand themes, freely resizable widgets, Google Tasks integration, an independent Routine & Hidden Reminders Hub, multi-call escalating alarms, and a Dual-Fingerprint Privacy Vault.**

### 📲 Download & Install

Grab the latest signed APK directly from [**GitHub Releases**](https://github.com/dx9901mk5/DX-Calendar/releases/latest) (includes a built-in **GitHub Auto-Updater with live download progress bar** inside Settings).

### ✨ Key Features

- **🖥️ Full-Spectrum Adaptive & Freeform Window UI (Phone / 8" & 10" Tablet / Pixelbook)**
  - Dynamically adapts to any window size: **8" tablets & unfolded foldables** feature a vivo-inspired master-detail split pane (calendar viewport on the left; Lunar stem-branch header, solar-term poetry card, almanac & event details on the right); **10" tablets & Pixelbooks** unlock a 3-pane layout with a persistent category filter rail and hardware keyboard shortcuts (`←`/`→`, `PageUp`/`PageDown`); **phones & mini floating windows** (down to `300×360dp`) use a clean single-column layout with a collapsible daily panel.
  - Supports **Year, Month, Week, Day, and Schedule (Agenda)** views with accurate 1900–2100 Lunar calendar, 24 Solar Terms, traditional Yi/Ji almanac, and regional holidays.
- **🎨 6 Signature Visual Themes (Gold-White Default) + Custom Calendar Wallpaper**
  - Defaults to **Gold & White** (pearl white surfaces, 18K champagne gold accents, dual-tone year header & frosted floating navigation capsules), plus one-tap switching across **V-Style**, **O-Style**, **M-Style**, **G-Style**, and **S-Style** themes, alongside custom semi-transparent calendar background images.
- **🧩 4 Freely Resizable Home Screen Widgets (0%–100% Opacity & Week-Expand Animation)**
  - Includes **DX Month Widget (V-Style)** (interactive tap-to-expand week accordion animation, `<` `>` month switching, `now` reset, and `⚙` 0%–100% live opacity popup), **DX Tasks & Routines Widget (V-Style)**, **DX Month Grid Widget (G-Style)**, and **DX Scrollable Schedule Widget (G-Style)**.
  - Every widget supports smooth `horizontal|vertical` resizing from compact `2×2` tiles up to full-screen tablet/desktop dashboards.
- **🔄 Per-Reminder Month Visibility Toggle & Centralized Routine Hub**
  - Say goodbye to cluttered month grids or apps that only let you hide entire accounts! Choose per item whether to **Show on Month Grid** or **Keep Hidden from Grid (Alert Only)**—ideal for daily medication or recurring chores, and even works as an override for synced Google Calendar series.
  - All routines and hidden reminders live cleanly inside the **Routine Hub** with daily check-in tracking, one-tap pause/resume, a temporary "Peek Hidden Items" toggle on the top bar, and one-tap cleanup for expired reminders.
- **⏰ Multi-Call Escalating Reminders & Native System Alarm Sync**
  - Multi-select reminder offsets (At event time, 1/3/5/10/15/20/30/45 mins, 1–6 hours, 1–7 days before) and batch-create events across **multiple non-consecutive dates**.
  - Enable **Multi-Call Burst Escalation** (repeatedly calls every $N$ minutes up to $K$ times until explicitly dismissed), full-screen lock-screen alarm wake-up on the Alarm audio stream, and automatic **Android System Clock Alarm integration**.
- **✅ Google Tasks Integration, 2-Way System Calendar Sync & ICS Import/Export**
  - Displays **Google Tasks** directly inside month cells, timelines, and home widgets with one-tap completion sync, alongside `.ics` file import/export and webcal/URL subscriptions.
- **🔐 Dual-Fingerprint Privacy Calendar Vault**
  - Protected by sequential **Two-Step Biometric Verification (Fingerprint 1/2 → Fingerprint 2/2)** with dual-entry PIN fallback.
  - Encrypts sensitive titles, locations, and notes via Android Keystore `AES-256-GCM`, auto-locks when backgrounded, and strictly isolates private items from public views and widgets.
- **🔄 Built-in GitHub Link & Auto-Updater with Progress Bar**
  - Direct links to the GitHub repository & Releases inside Settings, with automatic semantic version checking, automatic APK streaming download with a live percentage/MB progress bar, and one-tap package installation.

---

<a id="about-me"></a>
## 👨‍💻 關於我 / About Me

### 🇹🇼 關於開發者 (`dx9901mk5`)
我是 **dx9901mk5**，熱愛深度打磨 Android 原生體驗、跨裝置生產力（手機／摺疊機／平板／車機／Chromebook）與解決系統日常痛點的獨立開發者。致力於將各家旗艦系統最順手的互動細節融合進乾淨、高效、尊重使用者隱私的應用程式中。

歡迎造訪我的 GitHub 主頁探索更多 **DX 系列開源與工具專案**：
- 🔗 **GitHub 主頁**：[https://github.com/dx9901mk5](https://github.com/dx9901mk5)
- 🚗 [**DX-Car-Launcher**](https://github.com/dx9901mk5/DX-Car-Launcher) — Android Auto (Coolwalk) 風格車載 AI Box 桌面啟動器
- 🎙️ [**DxVoice-IME**](https://github.com/dx9901mk5/DxVoice-IME) — 離線 AI 智慧語音輸入法（說完自動切回原鍵盤＋懸浮語音球）
- 📁 [**DX_fileManager**](https://github.com/dx9901mk5/DX_fileManager) — 多面板與自由小視窗 Android 檔案管理器
- 🌀 [**FanRecentSwitcher**](https://github.com/dx9901mk5/FanRecentSwitcher) — 單手扇形最近任務切換器與邊緣手勢桌面
- 🛡️ [**PhotoSyncGuard**](https://github.com/dx9901mk5/PhotoSyncGuard) / [**CallMusicGuard**](https://github.com/dx9901mk5/CallMusicGuard) / [**PixelAI-Bridge**](https://github.com/dx9901mk5/PixelAI-Bridge)

### 🇺🇸 About the Developer (`dx9901mk5`)
Hi, I'm **dx9901mk5** — an independent Android developer passionate about crafting refined, cross-form-factor productivity tools (phones, foldables, tablets, head units, and Chromebooks) and solving real-world usability pain points without bloat.

Explore more projects from the **DX Ecosystem** on my GitHub profile: [https://github.com/dx9901mk5](https://github.com/dx9901mk5).

---

## 🛠️ Build from Source

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```
