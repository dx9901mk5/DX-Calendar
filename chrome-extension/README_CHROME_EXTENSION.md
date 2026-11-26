# DX日曆 Chrome 擴充功能 (Manifest V3) — 官方電腦端跨系統伴侶版

本資料夾為 **DX日曆 (`DX-Calendar`)** 官方 **Chrome / Edge / Brave 擴充功能**（正式取代原本的 Windows 獨立程式規劃，一次開發跨 **Windows / macOS / Linux / Chromebook** 全平台通用），完整延續 Android `v2.0.0` 的奢華金色美學與核心引擎。

## ✨ 核心功能（v2.1.0 桌面完整版）
- **三形態自由切換（彈出視窗 / 側邊欄 / 獨立桌面雙欄視窗）**：
  - 工具列點擊開啟輕巧彈窗（支援快捷鍵 `Alt+Shift+D`），並於圖示徽章即時顯示當天日期（番茄鐘運作時自動切換為剩餘分鐘倒數）。
  - 一鍵切換 **Chrome 側邊欄（Side Panel）** 常駐在網頁旁。
  - 一鍵點擊頂欄 **`⛶` 獨立桌面雙欄大視窗**：無網址列干擾，左側月曆直接顯示行程卡片、右側並排顯示詳情與 24h 時間軸，完全取代原生桌面日曆 App！
- **奢華金色亮暗自動主題**：跟隨系統自動切換「金色白底／金色黑底」，亦支援手動一鍵切換。
- **1900–2100 精準農曆、二十四節氣與台灣節慶** ＋ **7 天月曆天氣預報**（Open-Meteo 免費直連，3 小時快取）。
- **100% 離線自然語言＋語音快速建行程**（0 費用免 LLM API），支援 **11 種重複規則**（含農曆每月、農曆初一十五、農曆每年、隔 N 週）與背景定時桌面提醒通知。
- **完整行程編輯器 ＋ 拖曳改期 ＋ 24h 雙時區時間軸**：
  - 直接將當日清單中的行程拖曳到月曆上任一天快速改期。
  - 點擊行程開啟編輯彈窗，支援自訂顏色、多組提早提醒、地點一鍵 Google Maps 導航、**倒數日／生日自動算歲數**，以及 **家庭／情侶協作留言板**。
  - 支援切換 **24h 時間軸並對照第二時區**（東京／首爾／新加坡／美西／美東／倫敦／雪梨）。
- **待辦時間塊（⏰ 一鍵排時段）＋ 背景持久化番茄鐘（🍅）＋ 輪班連點排班（Stamp Mode）**：
  - 番茄鐘採用 `chrome.alarms` 背景持久化，即使關閉彈窗仍準時響鈴並推送系統通知。
- **與 Android DX日曆 v2.0.0 100% 無損備份互通 ＋ 標準 `.ics` 匯出入**：
  - 採用完全相同的 `.dxbackup.json`（Schema v2），支援 **「智慧合併匯入」** 與 **「完整覆蓋還原」**，並無損保留 Android 端之分類、例行打卡與加密隱私紀錄，搭配 Google 雲端硬碟實現 0 伺服器費用跨系統同步！

## 🚀 10 秒本機安裝使用（Chrome / Edge）
1. 在 Chrome 位址列輸入 `chrome://extensions/`（Edge 輸入 `edge://extensions/`）。
2. 開啟右上角 **「開發人員模式 (Developer mode)」**。
3. 點擊左上角 **「載入未封裝項目 (Load unpacked)」**，選取本專案的 `chrome-extension` 資料夾即可立即使用！
4. 建議將 **DX日曆** 釘選至工具列，並嘗試點擊右上角 **`⛶`** 開啟獨立雙欄桌面大視窗或按 `Alt+Shift+D` 隨時呼叫。

## 📦 打包 Chrome Web Store 上架套件與驗證
```powershell
# 執行自動化單元測試
node chrome-extension/test_extension.mjs

# 產出乾淨的 Chrome Web Store 上架用 .zip（輸出至 chrome-extension/dist/）
powershell -NoProfile -ExecutionPolicy Bypass -File chrome-extension/package_extension.ps1
```
