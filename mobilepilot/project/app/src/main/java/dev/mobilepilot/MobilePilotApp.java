package dev.mobilepilot;

import android.app.Application;
import dev.mobilepilot.core.AuditLog;

public class MobilePilotApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        AuditLog.init(this);
    }
}
