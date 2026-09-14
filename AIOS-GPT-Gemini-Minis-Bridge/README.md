# GPT / Gemini → Minis Bridge

Android 8+ standalone companion; does not modify or repackage Minis. Debug/alpha build.

## 使用
1. 安裝 Bridge APK 與 Minis；在 Minis 設定模型與原有 Agent / Accessibility / Shizuku。
2. 開啟 Bridge，在系統無障礙設定啟用「Minis Bridge（手動讀取）」，再同意開啟 Bridge 開關。
3. 回到官方 ChatGPT / Gemini 對話，點浮動「Bridge 讀取」。
4. 編輯可見文字成為需要的自然語言任務，點「下一步」，核對後「確認送到 Minis」。
5. 在 Minis 的分享介面選擇對話／繼續確認執行。Bridge 不會自動按下 Minis 執行鍵。
6. 點浮動「返回」開啟來源 App；實際回到哪一頁由來源 App 決定。可隨時點「停用」。

## 邊界
- 僅允許 `com.openai.chatgpt` 與 `com.google.android.apps.bard`。Google App 承載的 Gemini (`com.google.android.googlequicksearchbox`) 不開放，以免誤讀其他 Google 畫面。
- 只在點讀取時取得 active root；不使用事件文字、不自動捲動、不讀其他視窗，不含完整歷史。Accessibility 可能無法暴露部分文字，或包含按鈕標籤，必須編輯核對。
- 只擷取可見、位於螢幕範圍內的節點；排除密碼及可編輯子樹。最大 12,000 個 UTF-16 字元、2,000 節點、60 層。
- 開关預設關閉；草稿只在記憶體，兩分鐘逾期、取消、鎖屏、停用、服務關閉即清除。畫面禁止截圖；無網路、檔案、通知讀取、Shizuku 或手勢操作權限。無開機啟動接收器。
- 唯一持久資料為開關與最後一次成功呼叫分享入口的任務 SHA-256，用來阻擋相同任務重送（含空白差異）。不存原文。Hash 不是加密，不應視為秘密儲存。
- 只發出套件限定 `ACTION_SEND` / `text/plain` / `EXTRA_TEXT`，執行前檢查入口是否存在；不猜測私有 deep link、不直接呼叫非公開 Agent service。
- 已從使用者提供的 Minis 0.9-preview APK Manifest 確認 exported `com.openminis.app.share.ShareReceiverActivity` 支援 `ACTION_SEND` (`*/*`)。這證明分享入口存在，不證明任務已執行。
- 傳送內容包含保留系統安全及訊息／不可逆操作再次確認的指示。Bridge 本身沒有執行能力；無法強制約束外部 Minis Agent。請保留 Minis 自身授權與確認。
- 不讀取／搬運 ChatGPT/Gemini 私有資料庫、cookie、token、密碼。不繞過鎖屏、支付、安全與家長控制。不自動傳送訊息或回傳結果給來源 App。

## 建置
JDK 17、Android SDK 35、Gradle wrapper 8.11.1 / AGP 8.10.0。
`./gradlew testDebugUnitTest lintDebug assembleDebug`
GitHub Actions: `.github/workflows/build-minis-bridge.yml`，artifact `GPT-Gemini-Minis-Bridge-debug`。

## 驗證與尚需實機確認
單元測試驗證 package 邊界／回圈排除與空白去重。Lint 與 debug APK 編譯由 CI 執行。
實機必測：開關關閉不讀取；其他 App 拒絕；密碼／輸入欄位排除；取消不送；鎖屏清除；同任務重送阻擋；Minis 未安裝明確失敗；Minis 接收任務與返回來源。
目前環境沒有連線 Android 手機，未宣稱官方 Apps → Minis 的端到端實機測試成功。

Android references: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
and https://developer.android.com/training/sharing/send
