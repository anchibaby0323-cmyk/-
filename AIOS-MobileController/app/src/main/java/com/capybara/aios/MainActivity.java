package com.capybara.aios;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private TextView log;
    private EditText input;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        refresh();
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("AIOS 手機控制中心 v0.3 GitHub Alpha");
        title.setTextSize(22);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 16, 0, 16);
        root.addView(status);

        input = new EditText(this);
        input.setMinLines(4);
        input.setHint("輸入指令：\n開啟 LINE\n返回\n首頁\n通知欄\n點擊 500 1200\n輸入 你好\n點擊文字 傳送\nShell wm size\n多指令用 ； 分隔");
        root.addView(input);

        row(root, btn("執行", v -> runCmd(input.getText().toString())), btn("清空", v -> log.setText("日誌：\n")));
        row(root, btn("無障礙設定", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), btn("Shizuku 授權", v -> { ShizukuTools.request(); refresh(); }));
        row(root, btn("開 LINE", v -> runCmd("開啟 LINE")), btn("開原神", v -> runCmd("開啟 原神")), btn("返回", v -> runCmd("返回")));
        row(root, btn("首頁", v -> runCmd("首頁")), btn("通知欄", v -> runCmd("通知欄")), btn("讀畫面", v -> runCmd("讀取畫面")));
        row(root, btn("亮度", v -> runCmd("Shell settings get system screen_brightness")), btn("解析度", v -> runCmd("Shell wm size")));

        TextView help = new TextView(this);
        help.setText("\n支援指令：\n• 開啟 LINE / 原神 / Gmail / Gemini / 設定\n• 返回 / 首頁 / 最近任務 / 通知欄 / 快捷設定\n• 點擊 x y / 長按 x y / 滑動 x1 y1 x2 y2 毫秒\n• 點擊文字 xxx / 輸入 xxx / 讀取畫面\n• Shell xxx，需要 Shizuku 授權\n\n安全限制：不會繞過鎖屏、密碼、支付確認、遊戲防作弊或系統安全限制。");
        root.addView(help);

        log = new TextView(this);
        log.setText("日誌：\n");
        root.addView(log);
        setContentView(scroll);
    }

    private Button btn(String text, View.OnClickListener l) { Button b = new Button(this); b.setText(text); b.setOnClickListener(l); return b; }
    private void row(LinearLayout root, View... views) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); for (View v: views) r.addView(v, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(r); }
    private void refresh() { status.setText("Shizuku：" + (ShizukuTools.available() ? "已執行" : "未執行") + "｜授權：" + (ShizukuTools.granted() ? "已授權" : "未授權") + "\n無障礙：" + (AiosAccessibilityService.get()!=null ? "已啟動" : "未啟動")); }
    private void append(String s) { log.append("\n> " + s + "\n"); }

    private void runCmd(String raw) {
        refresh();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String part : raw.split("[；;]")) {
            String c = part.trim();
            if (c.isEmpty()) continue;
            append("指令：" + c);
            append(execOne(c));
        }
    }

    private String execOne(String c) {
        try {
            if (c.startsWith("等待 ")) { Thread.sleep(Long.parseLong(c.substring(3).trim())); return "等待完成"; }
            if (c.startsWith("Shell ")) return ShizukuTools.shell(c.substring(6));
            if (c.startsWith("開啟 ")) return launch(c.substring(3).trim());
            AiosAccessibilityService s = AiosAccessibilityService.get();
            if (s == null) return "請先開啟 AIOS 無障礙服務";
            if (c.equals("返回")) return s.back();
            if (c.equals("首頁")) return s.home();
            if (c.equals("最近任務")) return s.recents();
            if (c.equals("通知欄")) return s.notifications();
            if (c.equals("快捷設定")) return s.quickSettings();
            if (c.equals("讀取畫面")) return s.dump();
            if (c.startsWith("輸入 ")) return s.input(c.substring(3));
            if (c.startsWith("點擊文字 ")) return s.clickText(c.substring(5));
            String[] p = c.split("\\s+");
            if (p[0].equals("點擊") && p.length >= 3) return s.tap(Float.parseFloat(p[1]), Float.parseFloat(p[2]));
            if (p[0].equals("長按") && p.length >= 3) return s.longTap(Float.parseFloat(p[1]), Float.parseFloat(p[2]));
            if (p[0].equals("滑動") && p.length >= 6) return s.swipe(Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4]), Long.parseLong(p[5]));
            return "未知指令";
        } catch (Throwable t) { return "執行失敗：" + t.getMessage(); }
    }

    private String launch(String name) {
        String pkg = name;
        if (name.equalsIgnoreCase("line")) pkg = "jp.naver.line.android";
        else if (name.equals("原神")) pkg = "com.miHoYo.Yuanshen";
        else if (name.equalsIgnoreCase("gmail")) pkg = "com.google.android.gm";
        else if (name.equalsIgnoreCase("gemini")) pkg = "com.google.android.apps.bard";
        else if (name.equals("設定")) pkg = "com.android.settings";
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null && pkg.equals("com.miHoYo.Yuanshen")) i = getPackageManager().getLaunchIntentForPackage("com.miHoYo.GenshinImpact");
        if (i == null) { Toast.makeText(this, "找不到 App：" + name, Toast.LENGTH_SHORT).show(); return "找不到 App：" + name; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return "已開啟：" + name;
    }
}
