package com.capybara.gptgeminiminisbridge;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;

public class BridgeService extends AccessibilityService {
    static BridgeService instance;
    private WindowManager windows;
    private LinearLayout bubble;
    private AlertDialog dialog;
    private EditText draft;
    private String source;
    private boolean sent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver screenOff;
    static Intent shareIntent() {
        return new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.openminis.app");
    }
    private boolean enabled() { return getSharedPreferences("bridge", MODE_PRIVATE).getBoolean("enabled", false); }
    private boolean locked() {
        return getSystemService(KeyguardManager.class).isKeyguardLocked()
            || !getSystemService(PowerManager.class).isInteractive();
    }
    @Override protected void onServiceConnected() {
        instance = this;
        windows = getSystemService(WindowManager.class);
        screenOff = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { clearDraft(); source = null; sent = false; }
            refresh();
        }};
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, filter);
        refresh();
    }
    // Events never trigger text extraction or transmission.
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { clearDraft(); }
    void refresh() {
        if (bubble != null) { windows.removeView(bubble); bubble = null; }
        if (!enabled() || locked()) { clearDraft(); source = null; sent = false; return; }
        bubble = new LinearLayout(this);
        Button read = new Button(this); read.setText("Bridge 讀取"); bubble.addView(read);
        read.setOnClickListener(v -> capture());
        if (sent && BridgePolicy.allowed(source)) {
            Button back = new Button(this); back.setText("返回"); bubble.addView(back);
            back.setOnClickListener(v -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage(source);
                if (launch != null && !locked()) {
                    try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); sent = false; refresh(); }
                    catch (ActivityNotFoundException | SecurityException e) { toast("無法返回來源 App"); }
                }
            });
        }
        Button stop = new Button(this); stop.setText("停用"); bubble.addView(stop);
        stop.setOnClickListener(v -> { getSharedPreferences("bridge", MODE_PRIVATE).edit().putBoolean("enabled", false).apply(); refresh(); });
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        windows.addView(bubble, p);
    }
    private void capture() {
        if (!enabled() || locked() || dialog != null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { toast("無法讀取目前畫面"); return; }
        String pkg = String.valueOf(root.getPackageName());
        if (!BridgePolicy.allowed(pkg)) { root.recycle(); toast("請先開啟官方 ChatGPT 或 Gemini 對話（不支援 Google App 承載畫面）"); return; }
        StringBuilder text = new StringBuilder();
        Set<String> seen = new HashSet<>();
        int[] visited = {0};
        try { extract(root, pkg, text, seen, visited, 0); }
        finally { root.recycle(); }
        if (text.length() == 0) { toast("畫面沒有可讀文字；請在 Minis 手動輸入任務"); return; }
        source = pkg; sent = false;
        showReview(text.toString());
    }
    private void extract(AccessibilityNodeInfo node, String pkg, StringBuilder out, Set<String> seen, int[] visited, int depth) {
        if (++visited[0] > 2000 || depth > 60 || out.length() >= BridgePolicy.MAX_TEXT) return;
        if (!pkg.equals(String.valueOf(node.getPackageName())) || node.isPassword() || node.isEditable()) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        Rect screen = new Rect(0, 0, getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        if (node.isVisibleToUser() && Rect.intersects(bounds, screen)) {
            CharSequence raw = node.getText();
            if (raw == null || raw.length() == 0) raw = node.getContentDescription();
            String line = raw == null ? "" : raw.toString().trim();
            // Collapse duplicate accessibility labels only at identical screen bounds.
            if (!line.isEmpty() && seen.add(bounds.toShortString() + "|" + line)) {
                int remaining = BridgePolicy.MAX_TEXT - out.length();
                if (out.length() > 0 && remaining > 0) { out.append('\n'); remaining--; }
                out.append(line, 0, Math.min(line.length(), remaining));
            }
        }
        for (int i=0; i<node.getChildCount() && visited[0]<2000 && out.length()<BridgePolicy.MAX_TEXT; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { try { extract(child, pkg, out, seen, visited, depth+1); } finally { child.recycle(); } }
        }
    }
    private void showReview(String text) {
        draft = new EditText(this); draft.setText(text); draft.setMinLines(4); draft.setMaxLines(12);
        draft.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(BridgePolicy.MAX_TEXT)});
        draft.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        draft.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        draft.setSaveEnabled(false);
        dialog = new AlertDialog.Builder(this).setTitle("編輯要交給 Minis 的任務")
            .setMessage("來源：" + source + "\n這是可見文字，可能包含介面標籤；不是完整歷史。請刪除不需要的內容。最多 12,000 字，草稿 2 分鐘後清除。")
            .setView(draft).setNegativeButton("取消", (d,w) -> clearDraft())
            .setPositiveButton("下一步", null).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(d -> { if (draft != null) draft.setText(""); draft = null; dialog = null; handler.removeCallbacksAndMessages(null); });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> confirm());
        handler.postDelayed(this::clearDraft, 120000);
    }
    private void confirm() {
        if (draft == null || !enabled() || locked()) { clearDraft(); return; }
        final String task = draft.getText().toString().trim();
        if (task.isEmpty()) { toast("請輸入任務"); return; }
        final String hash = BridgePolicy.digest(task);
        if (hash.equals(getSharedPreferences("bridge", MODE_PRIVATE).getString("lastHash", ""))) {
            toast("已送過相同任務，請修改內容後再送，避免重複執行"); return;
        }
        // No transmission occurs until this distinct final confirmation.
        AlertDialog review = new AlertDialog.Builder(this).setTitle("確認交給 Minis")
            .setMessage("Minis 將收到下方任務。它可能需要再次選擇對話／確認執行。私人訊息、支付等動作必須保留確認；不得繞過鎖屏或系統限制。\n\n" + task)
            .setNegativeButton("返回編輯", (d,w) -> showReview(task))
            .setPositiveButton("確認送到 Minis", (d,w) -> send(task, hash)).create();
        clearDraft();
        dialog = review;
        review.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        review.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        review.setOnDismissListener(d -> { if (dialog == review) dialog = null; });
        review.show();
        handler.postDelayed(this::clearDraft, 120000);
    }
    private void send(String task, String hash) {
        if (!enabled() || locked()) { clearDraft(); return; }
        Intent intent = shareIntent().putExtra(Intent.EXTRA_TEXT,
            "使用者已確認交付以下任務。請保留所有系統安全限制；不得繞過鎖屏、支付確認或家長控制；傳送私人訊息及不可逆操作前再次取得使用者確認。\n\n" + task)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) == null) { toast("Minis 未安裝或沒有可用文字分享入口"); clearDraft(); return; }
        try {
            startActivity(intent);
            getSharedPreferences("bridge", MODE_PRIVATE).edit().putString("lastHash", hash).apply();
            sent = true; clearDraft(); refresh();
            toast("已交給 Minis 分享入口；請在 Minis 確認後續執行");
        } catch (ActivityNotFoundException | SecurityException e) { toast("Minis 無法接收，尚未送出"); clearDraft(); }
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void clearDraft() {
        handler.removeCallbacksAndMessages(null);
        if (draft != null) draft.setText("");
        draft = null;
        AlertDialog current = dialog; dialog = null;
        if (current != null) current.dismiss();
    }
    @Override public void onDestroy() {
        clearDraft(); source = null; instance = null;
        if (bubble != null) { windows.removeView(bubble); bubble = null; }
        if (screenOff != null) unregisterReceiver(screenOff);
        super.onDestroy();
    }
}
