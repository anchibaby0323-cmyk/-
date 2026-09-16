package dev.mobilepilot.notify;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;

public class MobileNotificationListener extends NotificationListenerService {
    private static final ArrayDeque<JSONObject> recent = new ArrayDeque<>();
    private static volatile boolean connected;

    @Override public void onListenerConnected() {
        connected = true;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification sbn : active) add(sbn, "active");
        } catch (Exception ignored) {}
    }

    @Override public void onListenerDisconnected() { connected = false; }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        add(sbn, "posted");
    }

    private static void add(StatusBarNotification sbn, String event) {
        try {
            Notification n = sbn.getNotification();
            JSONObject j = new JSONObject();
            j.put("event", event);
            j.put("key", sbn.getKey());
            j.put("package", sbn.getPackageName());
            j.put("time", sbn.getPostTime());
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            j.put("title", title == null ? "" : title.toString());
            j.put("text", text == null ? "" : text.toString());
            synchronized (recent) {
                recent.addFirst(j);
                while (recent.size() > 100) recent.removeLast();
            }
        } catch (Exception ignored) {}
    }

    public static JSONArray getRecent(int limit) {
        JSONArray a = new JSONArray();
        synchronized (recent) {
            int i = 0;
            for (JSONObject j : recent) {
                if (i++ >= limit) break;
                a.put(j);
            }
        }
        return a;
    }

    public static boolean isConnected() { return connected; }
}
