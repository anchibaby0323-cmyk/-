package dev.mobilepilot;

import android.app.Application;
import dev.mobilepilot.core.AuditLog;
import dev.mobilepilot.shizuku.ShizukuBridge;

public class MobilePilotApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        AuditLog.init(this);
        ShizukuBridge.initialize();
    }
}
