package com.capybara.aios;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private TextView status;
    private TextView log;
    private EditText input;
    private final Map<String, String> appMap = new HashMap<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initApps();
        buildUi();
        refresh();
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
    }

    private void initApps() {
        appMap.put("line", "jp.naver.line.android");
        appMap.put("賴", "jp.naver.line.android");
        appMap.put("赖", "jp.naver.line.android");
        appMap.put("gmail", "com.google.android.gm");
        appMap.put("信箱", "com.google.android.gm");
        appMap.put("郵件", "com.google.android.gm");
        appMap.put("邮件", "com.google.android.gm");
        appMap.put("gemini", "com.google.android.apps.bard");
        appMap.put("bard", "com.google.android.apps.bard");
        appMap.put("設定", "com.android.settings");
        appMap.put("设置", "com.android.settings");
        appMap.put("原神", "com.miHoYo.Yuanshen");
        appMap.put("genshin", "com.miHoYo.GenshinImpact");
        appMap.put("chrome", "com.android.chrome");
        appMap.put("瀏覽器", "com.android.chrome");
        appMap.put("浏览器", "com.android.chrome");
        appMap.put("youtube", "com.google.android.youtube");
        appMap.put("yt", "com.google.android.youtube");
        appMap.put("地圖", "com.google.android.apps.maps");
        appMap.put("地图", "com.google.android.apps.maps");
        appMap.put("maps", "com.google.android.apps.maps");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("AIOS 手機控制中心 v0.5 語意版");
        title.setTextSize(22);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 16, 0, 16);
        root.addView(status);

        input = new EditText(this);
        input.setMinLines(4);
        input.setHint("直接講人話：\n打開line\n幫我開賴\n回到桌面\n往下滑通知欄\n點一下傳送\n輸入你好\n點擊500,1200\n多指令用 ； 分隔");
        root.addView(input);

        row(root, btn("執行", v -> runCmd(input.getText().toString())), btn("清空", v -> log.setText("日誌：\n")));
        row(root, btn("無障礙設定", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), btn("Shizuku 授權", v -> { ShizukuTools.request(); refresh(); }));
        row(root, btn("開 LINE", v -> runCmd("打開line")), btn("開原神", v -> runCmd("幫我打開原神")), btn("返回", v -> runCmd("回上一頁")));
        row(root, btn("首頁", v -> runCmd("回到桌面")), btn("通知欄", v -> runCmd("打開通知欄")), btn("讀畫面", v -> runCmd("看一下畫面")));
        row(root, btn("亮度", v -> runCmd("Shell settings get system screen_brightness")), btn("解析度", v -> runCmd("Shell wm size")));

        TextView help = new TextView(this);
        help.setText("\n這版不需要固定空格或大小寫。\n可懂：打開line、開 LINE、幫我開賴、打开Gmail、回到桌面、回上一頁、打開通知欄、點一下傳送、輸入你好、點擊500 1200。\n\n目前不是直接連 GPT/Gemini 模型資料庫；那需要官方 API 或官方支援。這版是 App 內建模糊語意解析，先讓日常指令不要死板。\n\n安全限制：不會繞過鎖屏、密碼、支付確認、遊戲防作弊或系統安全限制。");
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

    private String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("　", "")
                .replace("，", ",")
                .replace("。", "")
                .replace("請", "")
                .replace("帮", "幫")
                .replace("打开", "打開")
                .replace("啟動", "打開")
                .replace("启动", "打開")
                .replace("开启", "開啟")
                .replace("開啟", "打開")
                .replace("開", "打開");
    }

    private void runCmd(String raw) {
        refresh();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String part : raw.split("[；;\n]+")) {
            String c = part.trim();
            if (c.isEmpty()) continue;
            append("指令：" + c);
            append(execOne(c));
        }
    }

    private String execOne(String c) {
        try {
            String n = norm(c);
            if (n.startsWith("shell")) return ShizukuTools.shell(c.replaceFirst("(?i)^\\s*shell\\s*", ""));
            if (n.startsWith("等待")) { Thread.sleep(Long.parseLong(n.replace("等待", ""))); return "等待完成"; }

            String app = findLaunchTarget(n);
            if (app != null) return launch(app);

            AiosAccessibilityService s = AiosAccessibilityService.get();
            if (s == null) return "請先開啟 AIOS 無障礙服務";

            if (hasAny(n, "返回", "回上一頁", "回上頁", "上一頁", "後退", "back")) return s.back();
            if (hasAny(n, "首頁", "主畫面", "桌面", "回家", "home", "回到桌面")) return s.home();
            if (hasAny(n, "最近任務", "多工", "後台", "后台", "recents")) return s.recents();
            if (hasAny(n, "通知欄", "通知栏", "通知中心")) return s.notifications();
            if (hasAny(n, "快捷設定", "快捷设置", "控制中心", "快速設定", "快速设置")) return s.quickSettings();
            if (hasAny(n, "讀取畫面", "讀畫面", "看畫面", "看一下畫面", "畫面內容")) return s.dump();

            String textToInput = extractAfterAny(c, "輸入", "输入", "打字", "幫我打", "帮我打");
            if (textToInput != null && !textToInput.trim().isEmpty()) return s.input(cleanTextValue(textToInput));

            String textToClick = extractAfterAny(c, "點擊文字", "点击文字", "點一下", "点一下", "按一下", "點", "点", "按");
            if (textToClick != null && !textToClick.trim().isEmpty() && !looksLikeCoordinate(textToClick)) return s.clickText(cleanTextValue(textToClick));

            float[] xy = extractTwoNumbers(c);
            if (xy != null && hasAny(n, "點擊", "点击", "點一下", "点一下", "按一下", "tap")) return s.tap(xy[0], xy[1]);
            if (xy != null && hasAny(n, "長按", "长按", "longpress")) return s.longTap(xy[0], xy[1]);

            float[] swipe = extractFiveNumbers(c);
            if (swipe != null && hasAny(n, "滑動", "滑动", "swipe")) return s.swipe(swipe[0], swipe[1], swipe[2], swipe[3], (long) swipe[4]);

            return "聽不懂：" + c + "\n我目前能懂開 App、返回、首頁、通知欄、輸入、點文字、點座標。";
        } catch (Throwable t) { return "執行失敗：" + t.getMessage(); }
    }

    private boolean hasAny(String n, String... keys) {
        for (String k : keys) if (n.contains(norm(k))) return true;
        return false;
    }

    private String findLaunchTarget(String n) {
        if (!(n.contains("打開") || n.contains("幫我打開") || n.contains("啟動") || n.contains("启动") || n.contains("open"))) return null;
        for (String key : appMap.keySet()) if (n.contains(norm(key))) return key;
        return null;
    }

    private String extractAfterAny(String raw, String... prefixes) {
        for (String p : prefixes) {
            int i = raw.indexOf(p);
            if (i >= 0) return raw.substring(i + p.length()).trim();
        }
        return null;
    }

    private String cleanTextValue(String s) {
        return s.replaceFirst("^[：:，,\s]+", "").replaceAll("[。\s]+$", "");
    }

    private boolean looksLikeCoordinate(String s) { return extractTwoNumbers(s) != null; }

    private float[] extractTwoNumbers(String s) {
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(s);
        float[] r = new float[2]; int i = 0;
        while (m.find() && i < 2) r[i++] = Float.parseFloat(m.group(1));
        return i >= 2 ? r : null;
    }

    private float[] extractFiveNumbers(String s) {
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(s);
        float[] r = new float[5]; int i = 0;
        while (m.find() && i < 5) r[i++] = Float.parseFloat(m.group(1));
        return i >= 5 ? r : null;
    }

    private String launch(String name) {
        String key = norm(name);
        String pkg = appMap.get(key);
        if (pkg == null) {
            for (String k : appMap.keySet()) if (key.contains(norm(k))) { pkg = appMap.get(k); name = k; break; }
        }
        if (pkg == null) pkg = name;
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null && pkg.equals("com.miHoYo.Yuanshen")) i = getPackageManager().getLaunchIntentForPackage("com.miHoYo.GenshinImpact");
        if (i == null && pkg.equals("com.google.android.apps.bard")) i = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
        if (i == null) {
            try {
                Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
                market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(market);
                return "找不到 App，已嘗試開商店：" + name;
            } catch (Throwable ignored) {}
            Toast.makeText(this, "找不到 App：" + name, Toast.LENGTH_SHORT).show();
            return "找不到 App：" + name;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return "已開啟：" + name;
    }
}
