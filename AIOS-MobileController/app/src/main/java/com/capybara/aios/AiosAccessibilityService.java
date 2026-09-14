package com.capybara.aios;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public class AiosAccessibilityService extends AccessibilityService {
    private static AiosAccessibilityService instance;
    public static AiosAccessibilityService get() { return instance; }
    @Override public void onServiceConnected() { instance = this; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    public String back() { return performGlobalAction(GLOBAL_ACTION_BACK) ? "已返回" : "返回失敗"; }
    public String home() { return performGlobalAction(GLOBAL_ACTION_HOME) ? "已回首頁" : "首頁失敗"; }
    public String recents() { return performGlobalAction(GLOBAL_ACTION_RECENTS) ? "已開最近任務" : "最近任務失敗"; }
    public String notifications() { return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ? "已展開通知欄" : "通知欄失敗"; }
    public String quickSettings() { return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) ? "已展開快捷設定" : "快捷設定失敗"; }

    public String tap(float x, float y) { return gesture(x, y, x, y, 60) ? "已點擊 " + x + "," + y : "點擊失敗"; }
    public String longTap(float x, float y) { return gesture(x, y, x, y, 650) ? "已長按 " + x + "," + y : "長按失敗"; }
    public String swipe(float x1, float y1, float x2, float y2, long ms) { return gesture(x1, y1, x2, y2, ms) ? "已滑動" : "滑動失敗"; }

    private boolean gesture(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(p, 0, ms);
        return dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    public String input(String text) {
        AccessibilityNodeInfo node = findInput();
        if (node != null) {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) return "已輸入文字";
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("AIOS", text));
        return "已複製到剪貼簿，請點到輸入框後再貼上";
    }

    public String clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "讀不到畫面";
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo n : list) {
            AccessibilityNodeInfo c = n;
            while (c != null) {
                if (c.isClickable() && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "已點擊文字：" + text;
                c = c.getParent();
            }
        }
        return "找不到可點擊文字：" + text;
    }

    public String dump() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "讀不到畫面";
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);
        StringBuilder sb = new StringBuilder("畫面文字：\n");
        int count = 0;
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            if (t != null && t.length() > 0) { sb.append("• ").append(t).append('\n'); count++; }
            else if (d != null && d.length() > 0) { sb.append("• ").append(d).append('\n'); count++; }
            if (count >= 80) break;
        }
        return sb.toString();
    }

    private AccessibilityNodeInfo findInput() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f != null && f.isEditable()) return f;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); collect(root, nodes);
        for (AccessibilityNodeInfo n : nodes) if (n.isEditable()) return n;
        return null;
    }
    private void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return; out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out);
    }
}
