# ThemeStore MTZ Importer

這個分支用來研究並製作 ThemeStore 1.0.8 的「正式 MTZ 匯入」版本。

## 目標

- 保留 ThemeStore 原有功能與 Shizuku 流程。
- 移除把 `ApplyThemeForScreenshot` 當成正式安裝的做法。
- 優先使用 Xiaomi Theme Manager (`com.android.thememanager`) 可用的正式 Import 流程。
- 不偽造 Xiaomi 官方簽章、Rights 或授權資料。
- 對匯入失敗提供清楚原因：格式不相容、Theme Manager 未暴露 Import、授權拒絕等。

## 已確認

ThemeStore 1.0.8 會使用 `theme_file_path` 並呼叫 `ApplyThemeForScreenshot`，因此目前屬於設計師/截圖測試式套用，不會正常登記成「我的主題」中的目前主題。

使用者裝置上的 Xiaomi 個性主題版本為 `3.0.5.14-global`。該 APK 內可見正式 Import、content/meta/rights 與 authorization 相關邏輯，代表正式匯入與 Screenshot Apply 是兩套不同流程。

## 工作分支

`themestore-mtz-importer`
