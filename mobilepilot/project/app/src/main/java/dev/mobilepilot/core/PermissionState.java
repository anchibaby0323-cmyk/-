package dev.mobilepilot.core;

import android.content.Context;
import android.provider.Settings;

public final class PermissionState {
    private PermissionState() {}

    public static boolean notificationListenerEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        String packageName = context.getPackageName();
        for (String component : enabled.split(":")) {
            if (component.startsWith(packageName + "/")) return true;
        }
        return false;
    }
}
