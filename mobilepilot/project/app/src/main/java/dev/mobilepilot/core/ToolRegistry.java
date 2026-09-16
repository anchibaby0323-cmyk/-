package dev.mobilepilot.core;

import android.content.Context;
import dev.mobilepilot.access.MobileAccessibilityService;
import dev.mobilepilot.notify.MobileNotificationListener;
import dev.mobilepilot.shizuku.ShizukuBridge;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolRegistry {
    private final Context context;
    public ToolRegistry(Context context) { this.context = context.getApplicationContext(); }

    private JSONObject tool(String name, String description, JSONObject props, JSONArray required) throws Exception {
        JSONObject schema = new JSONObject().put("type", "object").put("properties", props);
        if (required != null) schema.put("required", required);
        return new JSONObject().put("name", name).put("description", description).put("inputSchema", schema);
    }

    public JSONArray listTools() throws Exception {
        JSONArray a = new JSONArray();
        a.put(tool("status", "取得 MobilePilot / Accessibility / Shizuku 狀態", new JSONObject(), null));
        a.put(tool("permission_status", "取得無障礙、通知存取與 Shizuku 的授權狀態", new JSONObject(), null));
        a.put(tool("snapshot", "讀取目前 Android 畫面 UI tree", new JSONObject()
                .put("limit", new JSONObject().put("type","integer").put("default",200)), null));
        a.put(tool("tap_text", "依畫面文字點擊", new JSONObject()
                .put("text", new JSONObject().put("type","string")), new JSONArray().put("text")));
        a.put(tool("tap_id", "依 Android resource id 點擊", new JSONObject()
                .put("id", new JSONObject().put("type","string")), new JSONArray().put("id")));
        a.put(tool("tap", "依螢幕座標點擊", new JSONObject()
                .put("x", new JSONObject().put("type","integer"))
                .put("y", new JSONObject().put("type","integer")), new JSONArray().put("x").put("y")));
        a.put(tool("long_press", "長按螢幕座標", new JSONObject()
                .put("x", new JSONObject().put("type","integer"))
                .put("y", new JSONObject().put("type","integer"))
                .put("ms", new JSONObject().put("type","integer").put("default",800)), new JSONArray().put("x").put("y")));
        a.put(tool("swipe", "滑動螢幕", new JSONObject()
                .put("sx", new JSONObject().put("type","integer"))
                .put("sy", new JSONObject().put("type","integer"))
                .put("ex", new JSONObject().put("type","integer"))
                .put("ey", new JSONObject().put("type","integer"))
                .put("ms", new JSONObject().put("type","integer").put("default",400)),
                new JSONArray().put("sx").put("sy").put("ex").put("ey")));
        a.put(tool("type_text", "在目前焦點欄位輸入或替換文字", new JSONObject()
                .put("text", new JSONObject().put("type","string")), new JSONArray().put("text")));
        a.put(tool("clear_text", "清除目前焦點輸入欄", new JSONObject(), null));
        a.put(tool("global_action", "執行 back/home/recents/notifications/quick_settings/power_dialog/lock_screen/take_screenshot", new JSONObject()
                .put("action", new JSONObject().put("type","string")), new JSONArray().put("action")));
        a.put(tool("list_apps", "搜尋已安裝且可啟動 App", new JSONObject()
                .put("query", new JSONObject().put("type","string"))
                .put("limit", new JSONObject().put("type","integer").put("default",100)), null));
        a.put(tool("launch_app", "以 package name 啟動 App", new JSONObject()
                .put("package", new JSONObject().put("type","string")), new JSONArray().put("package")));
        a.put(tool("open_url", "以預設瀏覽器或 App 開啟 HTTP/HTTPS URL", new JSONObject()
                .put("url", new JSONObject().put("type","string")), new JSONArray().put("url")));
        a.put(tool("open_app_details", "開啟指定 App 的 Android 詳細設定頁", new JSONObject()
                .put("package", new JSONObject().put("type","string")), new JSONArray().put("package")));
        a.put(tool("clipboard_set", "寫入剪貼簿", new JSONObject()
                .put("text", new JSONObject().put("type","string")), new JSONArray().put("text")));
        a.put(tool("clipboard_get", "讀取目前剪貼簿", new JSONObject(), null));
        a.put(tool("recent_notifications", "取得最近通知的 App、標題與文字", new JSONObject()
                .put("limit", new JSONObject().put("type","integer").put("default",20)), null));
        a.put(tool("audit_log", "取得最近操作紀錄", new JSONObject()
                .put("limit", new JSONObject().put("type","integer").put("default",100)), null));
        return a;
    }

    public JSONObject call(String name, JSONObject args) {
        JSONObject r = new JSONObject();
        try {
            MobileAccessibilityService a = MobileAccessibilityService.get();
            switch (name) {
                case "status":
                    return r.put("ok", true)
                            .put("accessibility", a != null)
                            .put("shizuku_running", ShizukuBridge.isRunning())
                            .put("shizuku_granted", ShizukuBridge.isGranted())
                            .put("notification_listener_enabled", PermissionState.notificationListenerEnabled(context))
                            .put("notification_listener_connected", MobileNotificationListener.isConnected())
                            .put("package", a == null ? "" : a.snapshot(1).optString("package"));
                case "permission_status":
                    return r.put("ok", true)
                            .put("accessibility", a != null)
                            .put("notification_listener_enabled", PermissionState.notificationListenerEnabled(context))
                            .put("notification_listener_connected", MobileNotificationListener.isConnected())
                            .put("shizuku_running", ShizukuBridge.isRunning())
                            .put("shizuku_granted", ShizukuBridge.isGranted());
                case "snapshot":
                    if (a == null) return err("Accessibility 未連線");
                    return a.snapshot(Math.min(1000, Math.max(1, args.optInt("limit", 200))));
                case "tap_text":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.clickText(args.getString("text")));
                case "tap_id":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.clickId(args.getString("id")));
                case "tap":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.clickAt(args.getInt("x"), args.getInt("y")));
                case "long_press":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.longPressAt(args.getInt("x"), args.getInt("y"), args.optInt("ms",800)));
                case "swipe":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.swipe(args.getInt("sx"), args.getInt("sy"), args.getInt("ex"), args.getInt("ey"), args.optInt("ms",400)));
                case "type_text":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.typeIntoFocused(args.getString("text")));
                case "clear_text":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.clearFocused());
                case "global_action":
                    if (a == null) return err("Accessibility 未連線");
                    return r.put("ok", a.global(args.getString("action")));
                case "list_apps":
                    return r.put("ok",true).put("apps",AppTools.listApps(context,args.optString("query",""),args.optInt("limit",100)));
                case "launch_app":
                    return r.put("ok",AppTools.launch(context,args.getString("package")));
                case "open_url":
                    String url = args.getString("url");
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) return err("只允許 http/https URL");
                    AppTools.openUrl(context,url); return r.put("ok",true);
                case "open_app_details":
                    AppTools.openAppDetails(context,args.getString("package")); return r.put("ok",true);
                case "clipboard_set":
                    ClipboardTools.set(context,args.getString("text")); return r.put("ok",true);
                case "clipboard_get":
                    return r.put("ok",true).put("text",ClipboardTools.get(context));
                case "recent_notifications":
                    return r.put("ok",true).put("notifications",MobileNotificationListener.getRecent(args.optInt("limit",20)));
                case "audit_log":
                    return r.put("ok",true).put("text",AuditLog.tail(args.optInt("limit",100)));
                default:
                    return err("未知工具：" + name);
            }
        } catch (Exception e) {
            return err(e.toString());
        }
    }

    private JSONObject err(String msg) {
        try { return new JSONObject().put("ok",false).put("error",msg); }
        catch (Exception e) { return new JSONObject(); }
    }
}
