# 更新日誌 / Changelog

本檔案記錄 **DX日曆 (`DX-Calendar`)** 所有公開發行版本的功能演進與維護紀錄。  
All notable changes to **DX-Calendar** are documented in this file.

---

## [v1.3.0] - 2026-11-25 (`versionCode = 4`)

### ✨ 功能升級與體驗優化 / Improved
- **🧩 桌面小工具全面升級（展示預覽圖 ＋ 時間表可點擊事件 ＋ 類 vivo 單週展開 ＋ `now` 快捷鍵）**：
  - 4 款桌面小工具皆加入專屬向量預覽圖與 `previewLayout` / `previewImage`，於系統小工具挑選清單中一目了然。
  - 修復 **類 Google 時間表小工具** 可捲動但無法點擊活動事件的問題：點擊清單內任意行程將直接開啟 DX日曆 並定位至該日期詳情；待辦小工具則維持一鍵打勾完成。
  - **類 vivo 月曆小工具** 支援在桌面直接點擊切換 **「單週展開（上方 7 日條＋下方本週詳細行程）」↔「月檢視」**、左右切換週／月，並將「今」按鈕全面改為 **`now`**（一鍵返回當週當日；主畫面懸浮返回鈕亦同步改為 `now`）。
- **🎛️ DX 自有原創雙欄設定工作台（平板／摺疊機兩頁並排）**：
  - 重新設計設定頁為 DX 原創工作台風格（避免品牌設計爭議），於平板與摺疊機展開（寬度 ≥ `600dp`）呈現 **「左頁功能分類導覽 ＋ 右頁詳細設定」兩頁並排**。
- **✅ 日曆完整加入 Google Tasks 選擇與同步控制**：
  - 新增／編輯活動頁的「日曆 / 類型」選單可直接選擇 **`To-Do / Google Tasks`**，選中即自動歸屬待辦並同步顯示於日曆。
  - 設定頁「帳號、Google Tasks 與日曆」新增專屬 Google Tasks 綁定、立即同步、月曆顯示開關與已完成顯示開關。
- **🎨 主題與小工具名稱正名為「類品牌風格」**：
  - 五大主題改名為 **類 vivo 風格、類 OPPO 風格、類小米風格、類 Google 風格、類三星風格**，小工具名稱同步調整。

---

## [v1.2.0] - 2026-11-25 (`versionCode = 3`)

### 🆕 新增功能 / Added
- **📅 動態日期 App 圖示（1–31 日即時顯示）(`DynamicAppIconManager`)**：
  - 內建 **31 組高解析度向量自適應日曆圖示 (`1`～`31`)**（vivo OriginOS 紅橘頂欄＋白色日曆頁風格），依據當下日期自動切換桌面圖示顯示「幾日」。
  - 支援午夜跨日自動排程、`DATE_CHANGED` / `TIME_CHANGED` / `TIMEZONE_CHANGED` / 開機與啟動 App 自動同步，並於設定頁提供一鍵開關（可隨時切回預設靜態圖示）。
- **🏝️ Android 16 原生 Live Updates 通知上島（台／陸版 vivo OriginOS 6 原子島通用）(`LiveUpdateIslandHelper`)**：
  - 升級 `compileSdk = 36` / `targetSdk = 36`，宣告 `android.permission.POST_PROMOTED_NOTIFICATIONS` 權限。
  - 日程提醒與連段追擊呼叫同步推播 Android 16 原生 Live Updates（`setOngoing(true)`、`setRequestPromotedOngoing(true)`、`setShortCriticalText` ≤ 7 字元、`Notification.ProgressStyle` 進度條樣式），嚴格遵守系統上島規範（不使用 `RemoteViews`、`setColorized(true)`、`IMPORTANCE_MIN` 或 Group Summary）。
  - 設定頁新增 **「原子島 / Live Updates 通知上島」專區**：即時偵測 `canPostPromotedNotifications()` 授權狀態、提供一鍵導引至 `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`，以及「立即測試上島（回報 `hasPromotableCharacteristics()` 資格）」與「關閉測試通知」按鈕。
- **🧪 單元測試擴充**：新增動態圖示別名邊界測試與膠囊短文字 7 字元限制測試。

---

## [v1.1.0] - 2026-11-25 (`versionCode = 2`)

### 🆕 新增功能 / Added
- **設定頁置頂 GitHub 連結與自動更新專區**：
  - 提供一鍵開啟 **GitHub 儲存庫首頁** 與 **Releases 歷史頁面** 按鈕。
  - 顯示目前安裝版本資訊與自動檢查更新開關。
- **自動版本判讀與即時進度條下載安裝引擎 (`GitHubAutoUpdater`)**：
  - 啟動 App 及進入設定頁時，自動連線 GitHub Releases REST API 進行語意化版本 (`SemVer`) 比對。
  - 若偵測到較新版本，自動串流下載最新 `.apk`，並即時顯示 **`LinearProgressIndicator` 百分比與已下載 MB / 總計 MB 進度條**（支援中途取消或背景下載）。
  - 下載達 `100%` 後自動檢查 `REQUEST_INSTALL_PACKAGES` 權限並喚起系統安裝器覆蓋升級。
- **單元測試擴充**：新增 `verifyGitHubSemanticVersionComparison` 版本比對測試。

---

## [v1.0.0] - 2026-11-25 (`versionCode = 1`)

### 🎉 首發正式版 / Initial Release
- **全尺寸與小視窗響應式排版 (`AdaptiveLayoutSpec`)**：
  - 支援手機直向、8 吋平板／摺疊機左右雙欄、10 吋平板／Pixelbook (Chromebook) 三欄常駐分類篩選與自由縮放小視窗（最小 `300×360dp`），並支援外接鍵盤方向鍵與 `PageUp`/`PageDown` 快捷鍵。
  - 內建 **年、月、週、日、時間表** 五種檢視模式。
- **精準農曆、二十四節氣與傳統黃曆引擎 (`LunarCalendarEngine`)**：
  - 支援 1900–2100 農曆換算、干支生肖（如 `丙午年 [馬] 己亥月 癸卯日`）、節氣詩詞卡片、建除十二神宜忌與台灣節慶假日。
- **五大品牌主題風格與自訂月曆底圖 (`BrandThemeSystem`)**：
  - 以 **vivo OriginOS** 為預設基底，支援切換 **OPPO ColorOS**、**小米 HyperOS**、**Google 日曆**、**Samsung One UI** 五種風格、深淺色模式與自訂月曆半透明背景圖片。
- **四款桌面自由縮放小工具 (`CalendarWidgets`)**：
  - 提供 **vivo 月曆**（支援桌面直接翻月）、**vivo 待辦與例行**、**Google 月檢視**、**Google 日曆可捲動時間表** 四款支援 `horizontal|vertical` 任意縮放的小工具。
- **例行事務獨立隱藏開關與「例行中心」(`RoutinesAndHiddenHubView`)**：
  - 個別決定提醒是否顯示於月曆格（如每日吃藥提醒不佔月曆空間），亦支援覆寫隱藏外部 Google 同步系列活動；於「例行中心」集中提供每日打卡、暫停提醒與過期項目一鍵清理。
- **多重呼叫（連段追擊提醒）與連動系統鬧鐘 (`MultiCallAlarmScheduler`)**：
  - 支援提醒時間多選、多個不連續日期批次建立、連段追擊呼叫、鎖屏全螢幕鬧鐘音軌強喚醒 (`AlarmRingingActivity`) 與同步建立 Android 原生時鐘鬧鐘。
- **Google Tasks 整合顯示與 ICS / URL 匯入匯出**：
  - 支援 Google 帳號同步 Google Tasks 並直接顯示於月曆格、時間表與桌面小工具；支援 `.ics` 匯入匯出與網址訂閱。
- **雙指紋鎖「隱私日曆」加密空間 (`DualBiometricHelper` & `PrivacyCryptoManager`)**：
  - 支援連續兩道指紋驗證（1/2 → 2/2）與雙重 PIN 備援鎖，採用 Android Keystore `AES-256-GCM` 加密，切換背景自動上鎖並嚴格隔離公開視圖與小工具。
