package com.capybara.gptgeminiminisbridge;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status;
    private Button start;
    private Switch auto;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout page = Ui.column(this); page.setBackgroundColor(Ui.BG);
        page.setOnApplyWindowInsetsListener((v,i) -> {
            v.setPadding(Ui.dp(this,24),i.getSystemWindowInsetTop()+Ui.dp(this,24),Ui.dp(this,24),i.getSystemWindowInsetBottom()+Ui.dp(this,24)); return i;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(page); setContentView(scroll);
        page.addView(Ui.text(this,"MINIS  /  BRIDGE",12,Ui.MINT)); Ui.gap(page,14);
        Ui.title(page,"讓對話，\n接上行動。",34); Ui.gap(page,12);
        page.addView(Ui.text(this,"ChatGPT / Gemini → Minis\n自動讀取可見內容，交接你指定的任務。",15,Ui.MUTED)); Ui.gap(page,28);
        LinearLayout mode = Ui.card(this); page.addView(mode);
        Ui.title(mode,"選擇交接方式",18); Ui.gap(mode,12);
        auto = new Switch(this); auto.setText("自動送出標記任務"); auto.setTextColor(Ui.INK); auto.setTextSize(16);
        auto.setChecked(getSharedPreferences("bridge",MODE_PRIVATE).getBoolean("autoChoice",true)); mode.addView(auto); Ui.gap(mode,12);
        mode.addView(Ui.text(this,"開啟：只送新的完整標記任務。文字穩定 3 秒後倒數 5 秒，期間可取消。\n關閉：仍會自動讀取，點浮條預覽並手動送出。",14,Ui.MUTED)); Ui.gap(page,16);
        LinearLayout consent = Ui.card(this); page.addView(consent);
        Ui.title(consent,"只在你開啟時運作",17); Ui.gap(consent,8);
        consent.addView(Ui.text(this,"按下開始，即允許本次最長 15 分鐘自動讀取官方 App 目前可見文字，並依上方模式分享給 Minis。全程顯示狀態浮條；鎖屏即停止。\n\n不讀私有資料庫、密碼或輸入欄位。Minis 內的私人訊息、支付及其他敏感動作仍須確認。",14,Ui.MUTED)); Ui.gap(page,20);
        start = Ui.button(this,"開始自動交接 · 15 分鐘",true); page.addView(start);
        start.setOnClickListener(v -> {
            BridgeService service = BridgeService.instance;
            if (service == null) { Toast.makeText(this,"請先啟用 Minis Bridge 無障礙服務",Toast.LENGTH_LONG).show(); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return; }
            if (service.active()) service.stopSession();
            else { getSharedPreferences("bridge",MODE_PRIVATE).edit().putBoolean("autoChoice",auto.isChecked()).apply(); service.startSession(auto.isChecked()); }
            update();
        }); Ui.gap(page,12);
        Button settings = Ui.button(this,"無障礙設定",false); page.addView(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Ui.gap(page,16); status = Ui.text(this,"",13,Ui.MUTED); page.addView(status); Ui.gap(page,24);
        LinearLayout prompt = Ui.card(this); page.addView(prompt);
        Ui.title(prompt,"對 AI 說一次就好",17); Ui.gap(prompt,8);
        prompt.addView(Ui.text(this,"把下方說明貼到 ChatGPT 或 Gemini，之後任務內容仍可用一般自然語言。",14,Ui.MUTED)); Ui.gap(prompt,12);
        Button copy = Ui.button(this,"複製任務格式說明",false); prompt.addView(copy);
        copy.setOnClickListener(v -> {
            String text = "需要交給 Minis 執行時，只輸出一個完整任務區塊：開頭是 [[MINIS_TASK]]，接著換行寫自然語言任務，最後另起一行寫 [[/MINIS_TASK]]。一般聊天不要使用標記，不要引用或示範完整標記區塊。不要加入帳密。保留鎖屏、支付、家長控制等安全限制；私人訊息與不可逆操作必須先確認。";
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Minis 任務格式",text));
            Toast.makeText(this,"已複製，可貼到 AI 對話",Toast.LENGTH_SHORT).show();
        }); Ui.gap(page,18);
        page.addView(Ui.text(this,"Gemini 若由 Google App 承載，暫不支援。首次看見的既有標記不會送出；開始後請讓 AI 產生新任務。Bridge 自動交給分享入口，Minis 可能仍需選擇對話或按下執行。",12,Ui.MUTED));
    }
    private void update() {
        BridgeService s = BridgeService.instance; boolean running = s != null && s.active();
        start.setText(running ? "停止本次交接" : "開始自動交接 · 15 分鐘"); auto.setEnabled(!running);
        status.setText((s == null ? "○ 無障礙未啟用" : "● 無障礙已連線") + "   ·   "
            + (BridgeService.shareIntent().resolveActivity(getPackageManager()) == null ? "Minis 入口未找到" : "Minis 入口可用"));
    }
    @Override public void onResume() { super.onResume(); update(); }
}
