# 更新日誌 / Changelog

本檔案記錄 **DX日曆 (`DX-Calendar`)** 所有公開發行版本的功能演進與維護紀錄。  
All notable changes to **DX-Calendar** are documented in this file.

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
