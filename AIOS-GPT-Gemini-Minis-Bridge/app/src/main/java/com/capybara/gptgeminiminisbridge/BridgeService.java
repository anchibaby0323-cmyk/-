package com.capybara.gptgeminiminisbridge;

import android.accessibilityservice.AccessibilityService;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class BridgeService extends AccessibilityService {
    static BridgeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private WindowManager windows;
    private LinearLayout bubble;
    private TextView chip;
    private WindowManager.LayoutParams bubbleParams;
    private Dialog panel;
    private EditText draft;
    private BroadcastReceiver screenOff;
    private AutoGate gate;
    private boolean running, automatic, busy, sent, composing;
    private long expires, revision;
    private String source = "", currentPackage = "", preview = "", label = "等待對話";
    private final Runnable tick = new Runnable() { @Override public void run() {
        if (!active()) { stopSession(); return; }
        if (panel == null && !busy) readFrame();
        handler.postDelayed(this,750);
    }};
    private final Runnable expirePanel = this::closePanel;

    static Intent shareIntent() { return new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.openminis.app"); }
    boolean active() { return running && SystemClock.elapsedRealtime() < expires && !locked(); }
    private boolean locked() { return getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive(); }
    @Override protected void onServiceConnected() {
        instance = this; windows = getSystemService(WindowManager.class);
        // Never inherit a running session from the previous version or a process restart.
        getSharedPreferences("bridge",MODE_PRIVATE).edit().remove("enabled").apply();
        screenOff = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { stopSession(); }};
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
    void startSession(boolean auto) {
        stopSession(); if (locked()) return;
        running = true; automatic = auto; expires = SystemClock.elapsedRealtime()+15*60*1000;
        Set<String> delivered = new HashSet<>(getSharedPreferences("bridge",MODE_PRIVATE).getStringSet("delivered",Collections.emptySet()));
        String last = getSharedPreferences("bridge",MODE_PRIVATE).getString("lastHash",""); if (!last.isEmpty()) delivered.add(last);
        gate = new AutoGate(delivered); label = auto ? "自動交接 · 等待對話" : "自動讀取 · 等待對話";
        showBubble(); handler.post(tick);
    }
    void stopSession() {
        running = false; revision++; handler.removeCallbacks(tick); closePanel();
        preview = ""; source = ""; currentPackage = ""; sent = false; composing = false;
        if (gate != null) gate.cancel(); gate = null;
        if (bubble != null) { windows.removeView(bubble); bubble = null; chip = null; }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (!running) return;
        // Navigation/scroll must establish a fresh baseline, never replay history.
        if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED || e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            revision++; preview = "";
            if (gate != null) {
                // Streaming responses often scroll themselves; cancel their countdown without losing the new task.
                if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED && composing) gate.cancel();
                else { gate.rebaseline(); composing = false; }
            }
        }
    }
    @Override public void onInterrupt() { stopSession(); }
    private static final class Frame { String pkg = "", text = ""; boolean generating; }
    private void readFrame() {
        busy = true; final long token = revision;
        reader.execute(() -> {
            Frame frame = new Frame();
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    try {
                        frame.pkg = String.valueOf(root.getPackageName());
                        if (BridgePolicy.allowed(frame.pkg)) {
                            StringBuilder text = new StringBuilder();
                            extract(root,frame,text,new HashSet<>(),new int[]{0},0);
                            frame.text = text.toString();
                        }
                    } finally { root.recycle(); }
                }
            } catch (RuntimeException ignored) { frame.pkg = ""; frame.text = ""; }
            handler.post(() -> { busy = false; if (active() && token == revision && panel == null) accept(frame); });
        });
    }
    private void accept(Frame frame) {
        if (!frame.pkg.equals(currentPackage)) { currentPackage = frame.pkg; gate.rebaseline(); preview = ""; composing = false; }
        if (!BridgePolicy.allowed(frame.pkg)) {
            preview = ""; gate.cancel(); setLabel(sent ? "已交接 · 點此返回" : "等待 ChatGPT / Gemini"); return;
        }
        source = frame.pkg; preview = frame.text;
        if (frame.generating || (frame.text.contains(AutoGate.OPEN) && !frame.text.contains(AutoGate.CLOSE))) composing = true;
        if (!automatic) { setLabel(preview.isEmpty() ? "尚無可見文字" : "已讀取 · 點此預覽"); return; }
        long now = SystemClock.elapsedRealtime();
        String ready = gate.observe(frame.text,frame.generating,now);
        if (!gate.candidate().isEmpty()) composing = true;
        int countdown = gate.countdown(now);
        if (!ready.isEmpty()) { send(ready,true); return; }
        if (countdown >= 0) setLabel(countdown + " 秒後交接 · 點此取消");
        else if (!gate.candidate().isEmpty()) setLabel("任務已讀取 · 等待穩定");
        else setLabel("自動交接 · 等待新標記");
    }
    private void extract(AccessibilityNodeInfo node, Frame f, StringBuilder out, Set<String> seen, int[] visited, int depth) {
        if (++visited[0]>2000 || depth>60 || out.length()>=BridgePolicy.MAX_TEXT) return;
        if (!f.pkg.equals(String.valueOf(node.getPackageName())) || node.isPassword() || node.isEditable()) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        Rect screen = new Rect(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
        if (node.isVisibleToUser() && Rect.intersects(bounds,screen)) {
            CharSequence raw = node.getText(); if (raw == null || raw.length()==0) raw = node.getContentDescription();
            String line = raw == null ? "" : raw.toString().trim();
            String lower = line.toLowerCase(Locale.ROOT);
            if (Arrays.asList("stop generating","stop response","stop responding","停止生成","停止回應","停止回答","停止回应","停止回答生成").contains(lower)) f.generating = true;
            if (!line.isEmpty() && seen.add(bounds.toShortString()+"|"+line)) {
                int remaining = BridgePolicy.MAX_TEXT-out.length();
                if (out.length()>0 && remaining>0) { out.append('\n'); remaining--; }
                out.append(line,0,Math.min(line.length(),remaining));
            }
        }
        for (int i=0;i<node.getChildCount() && visited[0]<2000 && out.length()<BridgePolicy.MAX_TEXT;i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { try { extract(child,f,out,seen,visited,depth+1); } finally { child.recycle(); } }
        }
    }
    private void setLabel(String next) { label = next; if (chip != null) chip.setText("●  " + next); }
    private void showBubble() {
        bubble = new LinearLayout(this); bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackground(Ui.shape(this,Ui.CARD,28)); bubble.setElevation(Ui.dp(this,8));
        chip = Ui.text(this,"●  "+label,13,Ui.MINT); chip.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
        bubble.addView(chip);
        TextView stop = Ui.text(this,"Ⅱ",20,Ui.INK); stop.setContentDescription("停止自動交接");
        stop.setGravity(Gravity.CENTER); bubble.addView(stop,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        stop.setOnClickListener(v -> stopSession());
        bubbleParams = new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START; bubbleParams.x=Ui.dp(this,12); bubbleParams.y=Ui.dp(this,140);
        chip.setOnClickListener(v -> openPanel());
        chip.setOnTouchListener(new View.OnTouchListener() {
            float downX,downY; int x,y; boolean moved;
            @Override public boolean onTouch(View view, android.view.MotionEvent e) {
                if (e.getAction()==MotionEvent.ACTION_DOWN) { downX=e.getRawX(); downY=e.getRawY(); x=bubbleParams.x; y=bubbleParams.y; moved=false; return true; }
                if (e.getAction()==MotionEvent.ACTION_MOVE) {
                    float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                    if (Math.abs(dx)+Math.abs(dy)>Ui.dp(BridgeService.this,8)) moved=true;
                    if (moved && bubble != null) {
                        bubbleParams.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-bubble.getWidth(),x+(int)dx));
                        bubbleParams.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-bubble.getHeight(),y+(int)dy));
                        windows.updateViewLayout(bubble,bubbleParams);
                    } return true;
                }
                if (e.getAction()==MotionEvent.ACTION_UP) { if (!moved) view.performClick(); return true; }
                return true;
            }
        });
        windows.addView(bubble,bubbleParams);
    }
    private void openPanel() {
        if (!active() || panel != null) return;
        String text = gate.candidate().isEmpty() ? preview : gate.candidate();
        // Opening the panel cancels this automatic candidate, including during countdown.
        gate.skip(); revision++; setLabel("已暫停 · 檢視任務");
        Dialog sheet = new Dialog(this); sheet.requestWindowFeature(Window.FEATURE_NO_TITLE); panel = sheet;
        sheet.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        sheet.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout body = Ui.card(this); body.setBackground(Ui.shape(this,Ui.BG,28));
        body.addView(Ui.text(this,"MINIS  /  HANDOFF",11,Ui.MINT)); Ui.gap(body,10);
        Ui.title(body,"準備交接",25); Ui.gap(body,8);
        body.addView(Ui.text(this,"".equals(source) ? "目前沒有支援的對話" : ("com.openai.chatgpt".equals(source) ? "來自 ChatGPT" : "來自 Gemini"),13,Ui.MUTED)); Ui.gap(body,16);
        draft = new EditText(this); draft.setText(text); draft.setTextColor(Ui.INK); draft.setTextSize(15);
        draft.setGravity(Gravity.TOP); draft.setBackground(Ui.shape(this,Ui.CARD,16)); draft.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));
        draft.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        draft.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(BridgePolicy.MAX_TEXT)});
        draft.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); draft.setSaveEnabled(false);
        body.addView(draft,new LinearLayout.LayoutParams(-1,Ui.dp(this,150))); Ui.gap(body,14);
        body.addView(Ui.text(this,"開啟此面板已取消本次自動倒數。核對後可直接交給 Minis；敏感操作仍由 Minis 確認。",12,Ui.MUTED)); Ui.gap(body,16);
        Button send = Ui.button(this,"確認並交給 Minis  →",true); body.addView(send);
        send.setOnClickListener(v -> { if (draft != null) send(draft.getText().toString().trim(),false); }); Ui.gap(body,8);
        Button resume = Ui.button(this,"關閉 · 等待下一個新任務",false); body.addView(resume); resume.setOnClickListener(v -> closePanel());
        if (sent && BridgePolicy.allowed(source)) {
            Ui.gap(body,8); Button back = Ui.button(this,"返回來源對話",false); body.addView(back); back.setOnClickListener(v -> returnToSource());
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(body); sheet.setContentView(scroll);
        sheet.setOnDismissListener(d -> {
            if (panel == sheet) { if (draft != null) draft.setText(""); draft=null; panel=null; handler.removeCallbacks(expirePanel); }
        });
        sheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        sheet.show();
        Window w=sheet.getWindow(); w.setGravity(Gravity.BOTTOM);
        w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-Ui.dp(this,16),Ui.dp(this,480)),Math.min(Ui.dp(this,610),getResources().getDisplayMetrics().heightPixels-Ui.dp(this,80)));
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        handler.postDelayed(expirePanel,120000);
    }
    private void returnToSource() {
        if (!active() || !BridgePolicy.allowed(source)) return;
        Intent launch=getPackageManager().getLaunchIntentForPackage(source);
        if (launch != null) { try { closePanel(); startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); sent=false; gate.rebaseline(); } catch (RuntimeException e) { toast("無法返回來源 App"); } }
    }
    private void send(String task, boolean auto) {
        if (!active() || task.isEmpty()) return;
        if (auto) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) { gate.cancel(); return; }
            boolean sameSource;
            try { sameSource = source.equals(String.valueOf(root.getPackageName())) && BridgePolicy.allowed(source); }
            finally { root.recycle(); }
            if (!sameSource) { preview=""; gate.rebaseline(); return; }
        }
        String hash=BridgePolicy.digest(task);
        Set<String> delivered=new HashSet<>(getSharedPreferences("bridge",MODE_PRIVATE).getStringSet("delivered",Collections.emptySet()));
        if (delivered.contains(hash) || hash.equals(getSharedPreferences("bridge",MODE_PRIVATE).getString("lastHash",""))) { gate.skip(); toast("相同任務已交接，不重複送出"); return; }
        Intent intent=shareIntent().putExtra(Intent.EXTRA_TEXT,
            (auto ? "使用者已啟用標記任務自動交接。" : "使用者已確認本次任務交接。")
            + "請保留系統安全限制，不得繞過鎖屏、支付確認或家長控制；傳送私人訊息及不可逆操作前必須再次取得使用者確認。\n\n"+task).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) == null) { gate.skip(); setLabel("Minis 入口不可用 · 點此查看"); automatic=false; toast("找不到 Minis 文字分享入口，已暫停自動送出"); return; }
        try {
            startActivity(intent); gate.skip(); delivered.add(hash);
            getSharedPreferences("bridge",MODE_PRIVATE).edit().putStringSet("delivered",delivered).putString("lastHash",hash).apply();
            sent=true; preview=""; composing=false; revision++; closePanel(); gate.rebaseline(); setLabel("已交接 · 點此返回");
        } catch (ActivityNotFoundException | SecurityException e) { gate.skip(); automatic=false; toast("Minis 無法接收，已暫停自動送出"); }
    }
    private void closePanel() {
        handler.removeCallbacks(expirePanel); if (draft != null) draft.setText(""); draft=null;
        Dialog old=panel; panel=null; if (old != null) old.dismiss();
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    @Override public void onDestroy() { stopSession(); instance=null; if (screenOff != null) unregisterReceiver(screenOff); reader.shutdownNow(); super.onDestroy(); }
}
