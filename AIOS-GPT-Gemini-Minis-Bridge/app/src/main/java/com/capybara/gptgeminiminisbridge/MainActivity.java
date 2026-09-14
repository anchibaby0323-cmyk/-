package com.capybara.gptgeminiminisbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.*;

public class MainActivity extends Activity {
    private Switch toggle;
    private TextView status;
    private boolean syncing;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(24, 48, 24, 32);
        column.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(24, 24 + insets.getSystemWindowInsetTop(), 24, 24 + insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.addView(column); setContentView(scroll);
        TextView title = new TextView(this); title.setText("GPT / Gemini → Minis"); title.setTextSize(25); column.addView(title);
        TextView help = new TextView(this);
        help.setText("1. 在系統設定啟用 Minis Bridge 無障礙服務。\n2. 開啟下方開關，回到官方 ChatGPT / Gemini 對話。\n3. 點浮動「讀取」，刪除不需要分享的內容，確認後送到 Minis。\n4. 在 Minis 選擇對話／執行任務；完成後點浮動「返回」。\n\n只讀點選當下可見文字，不讀歷史資料庫、密碼或輸入欄位。草稿只留在記憶體，取消／離開即清除。Bridge 無網路權限、不自動執行、不傳送私人訊息。Minis 的 Agent／模型與 Accessibility／Shizuku 需自行設定。\n\nGemini 若由 Google App 承載，為避免讀取搜尋等其他畫面，本版會拒絕讀取。可在 Minis 手動貼上任務。\n");
        column.addView(help);
        toggle = new Switch(this); toggle.setText("啟用手動 Bridge（顯示浮動按鈕）"); column.addView(toggle);
        toggle.setChecked(getSharedPreferences("bridge", MODE_PRIVATE).getBoolean("enabled", false));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return;
            if (checked) new AlertDialog.Builder(this).setTitle("允許手動讀取？")
                .setMessage("點選讀取後，Bridge 可查看目前可見對話；您再次確認才分享給 Minis。請勿分享帳密或不需要的私人內容。")
                .setPositiveButton("同意並啟用", (d,w) -> enable(true))
                .setNegativeButton("取消", (d,w) -> toggle.setChecked(false))
                .setOnCancelListener(d -> toggle.setChecked(false)).show();
            else enable(false);
        });
        Button settings = new Button(this); settings.setText("開啟系統無障礙設定"); column.addView(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        status = new TextView(this); column.addView(status);
    }
    private void enable(boolean enabled) {
        getSharedPreferences("bridge", MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply();
        if (BridgeService.instance != null) BridgeService.instance.refresh();
    }
    @Override public void onResume() {
        super.onResume();
        syncing = true;
        toggle.setChecked(getSharedPreferences("bridge", MODE_PRIVATE).getBoolean("enabled", false));
        syncing = false;
        status.setText("\n無障礙服務：" + (BridgeService.instance == null ? "尚未連線" : "已連線")
            + "\nMinis 文字分享入口：" + (BridgeService.shareIntent().resolveActivity(getPackageManager()) == null ? "未找到，請安裝 Minis" : "可用"));
    }
}
