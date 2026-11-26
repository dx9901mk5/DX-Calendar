# DX日曆 (DX-Calendar) | 全尺寸奢華金主題智慧日曆 · Adaptive Smart Calendar

[🇹🇼 繁體中文](#繁體中文) | [🇺🇸 English](#english) | [🗺️ 開發路線圖 / Roadmap](#roadmap) | [👨‍💻 關於我 / About Me](#about-me)

[![Release](https://img.shields.io/github/v/release/dx9901mk5/DX-Calendar?color=C8962E&label=Latest%20Release)](https://github.com/dx9901mk5/DX-Calendar/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%7C%20Foldable%20%7C%20Tablet%20%7C%20Chromebook-00B884)](https://github.com/dx9901mk5/DX-Calendar)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-0D84FF)](https://developer.android.com/jetpack/compose)

---

<a id="繁體中文"></a>
## 🇹🇼 繁體中文

> **一款專為手機、摺疊機、8 吋／10 吋平板與 Chromebook 自由視窗打造的次世代 Android 智慧日曆。融合奢華金白／金色黑底自動主題、全 App Q 彈流暢手勢動畫、60fps 手風琴高解析桌面小工具、當日起 7 天月曆天氣、Google 行事曆與 Google Tasks 雙向整合、例行事務不佔月曆中心、多重連段追擊鬧鐘，以及無痕雙指紋隱私日曆。**

### 📲 下載安裝 (GitHub Releases)

前往 [**GitHub Releases 最新發行頁面**](https://github.com/dx9901mk5/DX-Calendar/releases/latest) 下載最新正式簽章版 APK 安裝即可使用（App 內建 **GitHub 自動檢查更新**，發現新版會彈窗提示並由您確認後附即時進度條一鍵下載安裝）。

---

### ✨ 核心功能特色（已實裝）

#### 1. 🎬 Q 彈手勢動畫與全尺寸響應式體驗
- **全 App 統一 Q 彈物理動畫**：點開設定頁、編輯頁、浮動 24 小時時間軸卡片、行程詳情卡與設定內頁每個分類皆具備一致的彈性過渡；`年 / 月 / 週 / 日 / 時間表` 切換支援方向性縮放過渡，切月／切年支援跟手水平滑動回彈。
- **滿版月曆 ⇄ 彩條壓縮手勢連動**：月檢視支援 1:1 跟手上下滑動，將滿版月曆平滑壓縮為彩條提示＋下方當日時間表；再次點擊已選日期即 Q 彈展開 24 小時浮動時間軸卡片。
- **全裝置與摺疊機專屬優化**：
  - **手機與自由小視窗**（最小支援至 `300×360dp`）：緊湊單欄、頂部常駐 `now` 一鍵回今天、今日圓角高亮膠囊＋強調色底條。
  - **摺疊機展開與 8 吋平板**：左主視圖＋右欄詳情（大字農曆干支、節氣詩詞、黃曆宜忌與天氣高低溫），新增／編輯頁自動切換為**左右雙欄並排**。
  - **10 吋平板與 Pixelbook / Chromebook**：三欄常駐分類篩選側欄＋外接鍵盤方向鍵／翻頁快捷鍵。

#### 2. 🎨 奢華金色亮暗雙主題 ＋ 1–31 動態日期 App 圖示
- **跟隨系統自動切換主題**：亮色預設「**金色白底**」（純淨珍珠白＋18K 香檳金），暗色預設「**金色灰黑底**」（沉穩深灰黑 `#16171B`＋高光香檳金）；同時提供多套風格與自訂月曆背景底圖（可調透明度）。
- **全新重新設計的金色 App 圖示**：同步對應系統亮暗色自動呈現「金色白底」與「金色黑底」，並內建 **1–31 日動態日期向量圖示**，午夜自動跨日刷新。

#### 3. 🌤️ 預設當日起 7 天月曆天氣預報
- 內建免 API Key 的 7 天天氣預報引擎（依所在地時區自動對應地區＋3 小時本機快取，免跳擾人定位權限），於月曆今日起連續 7 天日期格右上角呈現對應天氣圖示，並於當日詳情標頭顯示天氣狀態與高低溫。

#### 4. 🧩 高解析度透明背景桌面小工具（60fps 週次展開＋點擊直達事件）
- **DX 月曆小工具**：支援 **2,400,000 px** 旗艦高 DPI 1:1 銳利渲染，背景可自由調整 `0%–100%` 透明度，文字維持 100% 實心高對比＋微距陰影不發虛。
- **60fps 單週展開與直達詳情**：在桌面點擊任一週次即可流暢展開當週詳細行程，**直接點擊展開週中的事件卡片即可跳轉 App 顯示當下事件詳情**。
- **DX 時間表小工具**：可捲動瀏覽近期行程與待辦事項，點擊直達詳情，支援自由縮放與透明度調整。

#### 5. 🔄 例行事務「不佔月曆格」獨立管理 ＋ 進階重複規則
- **個別項目顯示控制**：建立活動或例行提醒（如每日吃藥、繳費、喝水）時可單獨決定是否佔用月曆格，連同步進來的外部 Google 行事曆重複活動也能單獨隱藏收納，無須關閉整個帳號。
- **例行中心**：集中打卡、一鍵暫停／恢復提醒與清理過期累積舊項目。
- **完整進階重複與日期**：支援每天／平日／每週／**隔 N 週（`FREQ=WEEKLY;INTERVAL=N`，如隔 2/3/4/6/8/12 週）**／每月／每年，以及**多個不連續日期**批次建立。

#### 6. ⏰ 多重連段追擊鬧鐘 ＋ 國際版 Android Live Updates 即時動態通知
- **多選提早提醒**：支援同時複選多組提早時間（準時、15 分前、1 小時前、1/2/3 天前、1 週前等），並可設定預設活動／待辦提醒時間。
- **連段追擊強提醒**：可開啟多重連響追擊（自訂次數與間隔分鐘，直到手動確認停止）、鎖屏全螢幕鬧鐘聲道強響，並可同步建立手機系統原生鬧鐘。
- **國際版 Android 16+ Live Updates**：符合 Android 國際版即時動態通知規範（`POST_PROMOTED_NOTIFICATIONS` / `requestPromotedOngoing`）。

#### 7. ✅ Google 行事曆即時聯動 ＋ Google Tasks ＋ 自動時區 ＋ 農曆黃曆
- **系統行事曆與語音助理即時聯動**：註冊 `CalendarContract` 防抖監聽，透過 Gemini 語音或 Google 行事曆新增事件後即時同步刷新月曆、小工具與鬧鐘。
- **多 Gmail 帳號與 Google Tasks 整合**：將待辦事項圓圈混排於月曆、時間表與小工具（未設日期之未完成任務預設顯示於今日），並支援標準 `.ics` 匯入匯出與網址訂閱。
- **自動判定時區與在地農曆**：自動依手機所在地判定時區（出國跨時區自動重排鬧鐘），預設依區域自動選用台灣／中國／香港農曆、國定假日、傳統節日、二十四節氣與傳統黃曆宜忌。

#### 8. 👁️ 零痕跡高隱蔽「隱私日曆」（雙指紋＋離場秒隱藏）
- **建立前完全零痕跡**：於「設定 → 隱私日曆設定」啟用前，全 App 隱藏所有隱私相關元件與按鈕。
- **閃爍眼睛入口與無痕驗證**：啟用後僅於月曆頂欄呈現一枚「眼睛」按鈕，點擊閃爍並僅提示「請使用指紋驗證」（底層強制連續通過 2 枚指紋驗證，不透露雙指紋機制），密件採 Android Keystore `AES-256-GCM` 硬體級加密。
- **離開即刻自動隱藏**：只要關閉 App、切換至其他 App 或**關閉螢幕**，立刻自動重新隱藏所有隱私內容，絕不外洩於公開檢視或桌面小工具。

---

<a id="roadmap"></a>
## 🗺️ 開發路線圖 / Roadmap（規劃實作中）

以下進階功能已排入後續版本開發計畫：

| # | 功能項目 | 重點規劃 |
| :---: | :--- | :--- |
| **1** | **本機離線自然語言＋語音快速新增** | 免 LLM API、免額外 OAuth！輸入或說出一句話（如「明天下午3點半開會 提前15分」），純本機零延遲解析時間、重複與提醒 |
| **2** | **行程長按拖曳改期 (Drag & Drop)** | 月檢視長按跨日拖曳改期；週／日 24h 時間軸拖曳調整開始時間與拉長縮短時長 |
| **3** | **倒數日／紀念日／生日自動算歲數** | 標記目標倒數天數、自動讀取聯絡人生日並計算今年幾歲與週年數 |
| **4** | **輪班與常用行程範本 (Templates)** | 預設整組事件範本（標題＋時長＋顏色＋連段提醒），支援輪班族一鍵連點快速排班 |
| **5** | **行程圖片附件 ＋ 地點一鍵導航** | 活動內可附加車票／預約截圖照片，點擊地點直接開啟 Google Maps 導航 |
| **6** | **農曆重複規則（初一十五／農曆生日）** | 原生支援「農曆每月（初一、十五）」與「農曆每年（長輩生日、祭祀、節慶）」跨年精準展開 |
| **7** | **雙時區 24h 時間軸對照** | 週／日時間軸左側並排顯示雙時區刻度，跨國會議與出差一目了然 |
| **8** | **待辦 × 時間塊 (Time Blocking) ＋ 番茄鐘** | 待辦事項直接排入 24h 時間軸執行時段，並內建專注倒數番茄鐘 |
| **9** | **家庭／情侶共用群組行事曆** | 支援共享行程協作、行程下備註留言與變更即時提醒 |
| **10** | **更多高質感桌面小工具（含天氣顯示）** | 新增倒數日小工具、下一行程迷你橫條、單週 7 天時間軸小工具，並全面支援顯示即時天氣 |
| **11** | **自訂裝置名稱整庫加密備份 ＋ Google 雲端硬碟連動** | 一鍵打包行程、例行打卡、設定與加密資料；備份上方可自訂「裝置名稱」標記來源，並直接連動 Google 雲端硬碟 App 上傳與還原 |
| **12** | **Windows Store 電腦端跨系統同步版** | 規劃推出 Microsoft Store 桌面端程式，透過 Google 行事曆／Tasks 與 Google 雲端硬碟加密同步檔實現 Android ⇄ Windows 無縫跨系統同步 |

---

<a id="english"></a>
## 🇺🇸 English

> **A next-generation adaptive Android smart calendar engineered for phones, foldables, 8"/10" tablets, and Chromebooks. Built with luxury Gold-on-White / Gold-on-Black auto themes, playful spring-physics animations, 60fps high-DPI accordion widgets with direct event launch, 7-day calendar weather, Google Calendar & Tasks sync, a clutter-free Routine Hub, multi-call escalating alarms, and a zero-trace Dual-Fingerprint Privacy Vault.**

### 📲 Download & Install

Download the latest signed APK from [**GitHub Releases**](https://github.com/dx9901mk5/DX-Calendar/releases/latest). Built-in **GitHub Update Checker** automatically checks for updates and prompts for your confirmation before downloading with a live progress bar.

### ✨ Key Features (Shipped)

- **🎬 Spring-Physics Animations & Full-Spectrum Adaptive UI**
  - Unified bouncy spring transitions across Settings, Event Editor, floating 24h timeline cards, detail panels, and directional zoom transitions between **Year / Month / Week / Day / Schedule** views.
  - Restores smooth 1:1 vertical drag gesture to compress the full-screen month grid into color-bar indicators + daily agenda sheet, plus swipe-to-switch-month spring feedback.
  - Responsive layouts for **phones** (persistent top-bar `now` button & highlighted Today pill), **unfolded foldables & 8" tablets** (dual-pane calendar + side-by-side Event Editor), and **10" tablets / Chromebooks** (3-pane layout + hardware keyboard navigation).
- **🎨 Luxury Gold Light/Dark Auto Theme & Dynamic 1–31 Day Icons**
  - Automatically follows system Light (**Gold on White**) and Dark (**Gold on Graphite Black**) themes, with multiple preset styles and custom semi-transparent calendar wallpapers.
  - Redesigned luxury gold adaptive app icon with **31 dynamic day-of-month vector icons (01–31)** that auto-match system Light/Dark mode and refresh at midnight.
- **🌤️ Built-in 7-Day Weather Forecast on Month Calendar**
  - Zero-API-key Open-Meteo 7-day forecast with timezone-based location defaults and 3-hour local caching—displays crisp weather icons directly on the next 7 days in Month view and high/low temperatures in the daily header.
- **🧩 High-DPI Transparent Widgets with 60fps Week-Expand & Direct Event Click**
  - **2,400,000 px** native high-DPI canvas rendering with `0%–100%` background opacity while keeping text 100% solid high-contrast with micro-shadows.
  - Tap any week row on the home screen to smoothly expand it at 60fps, and **tap any event card inside the expanded week to jump straight to that event in the app**.
- **🔄 Clutter-Free Routine Hub & Advanced Recurrence**
  - Control per-item whether reminders appear in the month grid or stay cleanly tucked into the **Routine Hub** (with daily check-ins, pause/resume, and expired cleanup).
  - Supports **Every N Weeks (`FREQ=WEEKLY;INTERVAL=N`)** and **multiple non-consecutive dates**.
- **⏰ Multi-Call Escalating Alarms & International Android 16+ Live Updates**
  - Multi-select early reminders, repeating burst alarms until dismissed, full-screen alarm-stream alerts, native Clock App sync, and standard international **Android 16+ Live Updates**.
- **✅ Real-Time Google Calendar & Tasks Sync + Auto Timezone & Lunar Almanac**
  - Debounced `CalendarContract` observer for instant sync with Google Calendar & voice assistants, Google Tasks integration (undated active tasks surface on Today), `.ics` import/export, automatic timezone detection, and 1900–2100 regional Lunar/Solar-Term/Almanac data.
- **👁️ Zero-Trace Stealth Privacy Calendar (Dual-Fingerprint + Instant Auto-Lock)**
  - Completely invisible until enabled in Settings; reveals only a subtle blinking **Eye** icon on the calendar bar (discreet fingerprint prompt backed by sequential dual-fingerprint verification + `AES-256-GCM` Keystore encryption).
  - Automatically re-locks and hides immediately upon closing the app, switching apps, or turning off the screen.

### 🗺️ Planned Roadmap (English Summary)
- **Offline Natural Language & Voice Quick Add** (zero LLM API / zero OAuth required)
- **Drag & Drop Event Rescheduling** across Month cells and Week/Day 24h timelines
- **Countdowns, Anniversaries & Contact Birthday Age Calculator**
- **Shift & Event Templates** for one-tap schedule stamping
- **Image Attachments & One-Tap Google Maps Navigation**
- **Lunar Recurrence Rules** (monthly 1st/15th & annual lunar birthdays/festivals)
- **Dual-Timezone 24h Timeline**, **Task Time-Blocking + Pomodoro**, & **Shared Family/Couple Calendars**
- **Expanded Weather-Enabled Widgets** (Countdown, Next-Up Bar, 7-Day Week Timeline)
- **Named-Device Encrypted Full Backup & Google Drive App Upload**
- **Windows Microsoft Store Desktop Companion** with cross-system sync

---

<a id="about-me"></a>
## 👨‍💻 關於我 / About Me

### 🇹🇼 關於開發者 (`dx9901mk5`)
我是 **dx9901mk5**，熱愛深度打磨原生介面動畫、跨裝置生產力（手機／摺疊機／平板／車機／Chromebook／Windows 桌面）與解決真實日常痛點的獨立開發者。堅持「無廣告干擾、尊重隱私、高效率與極致順手」的設計理念，持續打造乾淨俐落的 **DX 系列工具生態**。

歡迎造訪我的 GitHub 主頁探索更多專案：
- 🔗 **GitHub 主頁**：[https://github.com/dx9901mk5](https://github.com/dx9901mk5)
- 📅 [**DX-Calendar**](https://github.com/dx9901mk5/DX-Calendar) — 全尺寸奢華金主題智慧日曆（本專案）
- 🚗 [**DX-Car-Launcher**](https://github.com/dx9901mk5/DX-Car-Launcher) — Android Auto (Coolwalk) 風格車載 AI Box 桌面啟動器
- 🎙️ [**DxVoice-IME**](https://github.com/dx9901mk5/DxVoice-IME) — 離線 AI 智慧語音輸入法（說完自動切回原鍵盤＋懸浮語音球）
- 📁 [**DX_fileManager**](https://github.com/dx9901mk5/DX_fileManager) — 多面板與自由小視窗 Android 檔案管理器
- 🌀 [**FanRecentSwitcher**](https://github.com/dx9901mk5/FanRecentSwitcher) — 單手扇形最近任務切換器與邊緣手勢桌面
- 🛡️ [**PhotoSyncGuard**](https://github.com/dx9901mk5/PhotoSyncGuard) / [**CallMusicGuard**](https://github.com/dx9901mk5/CallMusicGuard) / [**PixelAI-Bridge**](https://github.com/dx9901mk5/PixelAI-Bridge)

### 🇺🇸 About the Developer (`dx9901mk5`)
Hi, I'm **dx9901mk5** — an independent developer dedicated to crafting fluid, privacy-respecting productivity tools across phones, foldables, tablets, car head units, Chromebooks, and Windows desktops.

Explore more projects from the **DX Ecosystem** at [https://github.com/dx9901mk5](https://github.com/dx9901mk5).

---

## 🛠️ Build from Source

```powershell
.\gradlew.bat testGithubDebugUnitTest assembleGithubRelease
```
