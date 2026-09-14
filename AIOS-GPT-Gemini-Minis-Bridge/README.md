# Minis Bridge 1.1 — 自動讀取與標記交接

Android 8+ standalone companion for official ChatGPT / Gemini and installed Minis. Debug / alpha. 不修改 Minis。

## 新版操作
1. 安裝 1.1 APK。在 Bridge 開啟「無障礙設定」，啟用「Minis Bridge（自動交接）」。
2. 按「複製任務格式說明」，貼給 ChatGPT 或 Gemini。一般聊天不使用任務標記。
3. 回 Bridge，開啟「自動送出標記任務」，按「開始自動交接 · 15 分鐘」。這個動作明確授權本次的自動讀取／交接。
4. 回到對話，要求 AI 產生新任務。完整標記範例：

```text
[[MINIS_TASK]]
打開 LINE，找到媽媽，草擬「我晚點回家」。請先讓我確認，不要直接傳送。
[[/MINIS_TASK]]
```

5. 新完整任務文字穩定 3 秒後，浮條顯示 5 秒倒數，然後自動交給 Minis 分享入口。點浮條可取消本次倒數並編輯任務；點「Ⅱ」立即停止本次交接。浮條可拖曳。
6. 在 Minis 選擇對話／確認執行（取決於 Minis 原本分享介面）。浮條的底部面板提供「返回來源對話」。

關閉「自動送出標記任務」仍會自動讀取，但只有在底部面板按「確認並交給 Minis」才送出。沒有兩次彈窗。

## 和 1.0 的差別
- 前景可見文字自動更新，不再需要每次點讀取。
- 使用者啟用自動交接後，標記任務倒數自動分享；無須逐次按確認。
- 深色卡片主畫面、薄型可拖曳狀態浮條、圓角底部編輯面板。
- 新增串流穩定等待、倒數取消、導覽重新建立基準、跨重啟任務 hash 去重。

## 相容性與限制
- 支援 package `com.openai.chatgpt`、`com.google.android.apps.bard`。不開放 `com.google.android.googlequicksearchbox`（Google App 承載的 Gemini）。
- 標記必須各自佔一整行；自然語言內容不限固定指令。不完整／空白／巢狀／多個不同新任務不自動送出。首次讀到的既有任務忽略；開始後請讓 AI 產生新任務。
- Accessibility 沒有通用可信的 AI／使用者角色標籤，本版辨識的是可見完整標記，不能驗證作者。因此不要在啟用期間貼上或引用完整示範區塊，否則也可能被當任務。
- 停止生成控制項只辨識部分中英文文字，不能保證所有 App 版本的串流完成訊號一致。完整結束標記與穩定等待是主要交接條件。
- 部分 App 不暴露可見文字或會把標記拆成不同結構，可能無法自動交接。回覆自動捲動時會重置倒數；未辨識到生成中的捲動會重新建立基準，可能需要請 AI 重新產生任務。
- 只讀目前畫面，不自動捲動，不讀完整歷史。掃描上限 12,000 UTF-16 字元／2,000 節點／60 層；超出範圍的完整標記可能讀不到。
- 已從使用者提供的 Minis 0.9-preview APK Manifest 確認 exported `com.openminis.app.share.ShareReceiverActivity` 支援 `ACTION_SEND`。成功交給 Android 分享入口不等於 Minis 已執行完成。
- Minis 內部的模型、Agent、Accessibility、Shizuku 由 Minis 自己設定；Bridge 不會自動按下它的執行鍵，也不會將結果自動傳回 ChatGPT/Gemini。

## 安全與儲存
- 服務啟用不等於工作階段啟動。每次必須在 Bridge 按開始；最多 15 分鐘，全程可見浮條。鎖屏、服務中斷、重啟即停止，不靜默恢復。
- 不讀私有資料庫、cookie、token、密碼或輸入欄位。不讀通知。沒有網路、檔案讀取、Shizuku、手勢操作權限。
- 可見文字只留在記憶體，離開支援 App／停止即清除。打開的編輯面板在兩分鐘後清除。禁止畫面截圖，不寫入內容日誌。
- 僅保存模式選擇、已交接任務的 SHA-256 去重記錄（不存原文）。Hash 不是加密，不能視為秘密保管方式。
- 僅傳送套件限定 ACTION_SEND / text/plain / EXTRA_TEXT。Minis 缺失／拒收時停止自動交接，不循環重試、不猜測私有入口。
- 任務附帶保留鎖屏、支付、家長控制與私人訊息／不可逆操作確認的指示；Bridge 無法強制外部 Minis Agent 遵守，請保留 Minis 自身的確認機制。

## 安裝新版
GitHub debug APK 每次編譯可能使用不同測試簽章。若出現「與現有套件衝突」，先移除舊版 **Bridge** 再安裝，無須移除 Minis；重新啟用 Bridge 無障礙服務即可。移除會清除 Bridge 的設定與去重記錄。

## 建置與驗證
JDK 17 / AGP 8.10.0 / Gradle wrapper 8.11.1 / Android SDK 35。
`./gradlew testDebugUnitTest lintDebug assembleDebug`

9 個單元測試涵蓋 package 邊界、文字去重、既有內容、完整標記、串流／變更、倒數、取消、導覽與多任務歧義。GitHub Actions 同時跑測試、Lint 與 APK 打包。

沒有連線 Android 手機；尚未實機驗證官方 App → Minis 的端到端流程與各機型的畫面佈局。

Android API: https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
Sharing: https://developer.android.com/training/sharing/send
