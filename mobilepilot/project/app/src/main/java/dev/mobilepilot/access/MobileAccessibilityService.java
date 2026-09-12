package dev.mobilepilot.access;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.mobilepilot.core.AuditLog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.List;

public class MobileAccessibilityService extends AccessibilityService {
    private static volatile MobileAccessibilityService instance;
    public static MobileAccessibilityService get() { return instance; }

    @Override protected void onServiceConnected() {
        instance = this;
        AuditLog.add("accessibility", "connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public JSONObject snapshot(int limit) {
        JSONObject out = new JSONObject();
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            JSONArray nodes = new JSONArray();
            if (root != null) {
                ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
                q.add(root);
                int index = 0;
                while (!q.isEmpty() && nodes.length() < limit) {
                    AccessibilityNodeInfo n = q.removeFirst();
                    nodes.put(nodeToJson(n, index++));
                    for (int i = 0; i < n.getChildCount(); i++) {
                        AccessibilityNodeInfo c = n.getChild(i);
                        if (c != null) q.addLast(c);
                    }
                }
            }
            out.put("ok", true);
            out.put("package", root == null || root.getPackageName() == null ? "" : root.getPackageName().toString());
            out.put("nodes", nodes);
        } catch (Exception e) {
            try { out.put("ok", false).put("error", e.toString()); } catch (Exception ignored) {}
        }
        return out;
    }

    private JSONObject nodeToJson(AccessibilityNodeInfo n, int index) throws Exception {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return new JSONObject()
                .put("index", index)
                .put("text", safe(n.getText()))
                .put("desc", safe(n.getContentDescription()))
                .put("id", safe(n.getViewIdResourceName()))
                .put("class", safe(n.getClassName()))
                .put("package", safe(n.getPackageName()))
                .put("clickable", n.isClickable())
                .put("focusable", n.isFocusable())
                .put("editable", n.isEditable())
                .put("scrollable", n.isScrollable())
                .put("enabled", n.isEnabled())
                .put("bounds", new JSONObject().put("left", r.left).put("top", r.top).put("right", r.right).put("bottom", r.bottom));
    }

    private String safe(CharSequence x) { return x == null ? "" : x.toString(); }

    public boolean clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo n : list) {
            AccessibilityNodeInfo cur = n;
            while (cur != null) {
                if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AuditLog.add("tap_text", text);
                    return true;
                }
                cur = cur.getParent();
            }
        }
        return false;
    }

    public boolean clickId(String id) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(id);
        for (AccessibilityNodeInfo n : list) {
            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                AuditLog.add("tap_id", id);
                return true;
            }
        }
        return false;
    }

    public boolean clickAt(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        return dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null);
    }

    public boolean longPressAt(int x, int y, int ms) {
        Path p = new Path();
        p.moveTo(x, y);
        return dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(400, ms))).build(), null, null);
    }

    public boolean swipe(int sx, int sy, int ex, int ey, int ms) {
        Path p = new Path();
        p.moveTo(sx, sy);
        p.lineTo(ex, ey);
        return dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(100, ms))).build(), null, null);
    }

    public boolean typeIntoFocused(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (n == null) return false;
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        AuditLog.add("type", "len=" + text.length() + " ok=" + ok);
        return ok;
    }

    public boolean clearFocused() { return typeIntoFocused(""); }

    public boolean global(String action) {
        int a;
        switch (action) {
            case "back": a = GLOBAL_ACTION_BACK; break;
            case "home": a = GLOBAL_ACTION_HOME; break;
            case "recents": a = GLOBAL_ACTION_RECENTS; break;
            case "notifications": a = GLOBAL_ACTION_NOTIFICATIONS; break;
            case "quick_settings": a = GLOBAL_ACTION_QUICK_SETTINGS; break;
            case "power_dialog": a = GLOBAL_ACTION_POWER_DIALOG; break;
            case "lock_screen":
                if (android.os.Build.VERSION.SDK_INT < 28) return false;
                a = GLOBAL_ACTION_LOCK_SCREEN; break;
            case "take_screenshot":
                if (android.os.Build.VERSION.SDK_INT < 28) return false;
                a = GLOBAL_ACTION_TAKE_SCREENSHOT; break;
            default: return false;
        }
        boolean ok = performGlobalAction(a);
        AuditLog.add("global", action + " ok=" + ok);
        return ok;
    }
}
