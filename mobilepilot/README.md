# MobilePilot

Android 原生「手機自己控制手機」專案。

目標：不依賴電腦、不使用 Termux、不需要 root；以 Accessibility + Shizuku + 本機 MCP/IPC 提供手機端自動化能力。

## 目前功能

- Accessibility UI tree 讀取
- 依文字 / resource-id / 座標點擊
- 長按、滑動、文字輸入與清除
- Back / Home / Recents / 通知欄 / 快速設定 / 鎖屏 / 系統截圖
- App 搜尋與啟動
- URL 開啟
- 剪貼簿讀寫
- NotificationListener 最近通知
- Shizuku Binder 狀態與授權
- 浮動球
- 開機自動啟動本機服務
- Audit Log
- Loopback-only MCP JSON-RPC endpoint：`http://127.0.0.1:8473/mcp`

## APK

GitHub Actions 工作流程：`Build MobilePilot APK`

成功後到 Actions 對應的 workflow run 下載 artifact：`MobilePilot-debug-apk`。

> 注意：ChatGPT / Gemini 官方 Android App 目前不提供任意第三方 APK 直接使用其登入帳號額度並掛載 localhost MCP 的公開介面；控制層與 AI 前端因此分離設計。
