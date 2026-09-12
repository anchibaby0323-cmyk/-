package dev.mobilepilot.shizuku;

import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public static final int REQUEST_CODE = 4201;

    public static boolean isRunning() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean isGranted() {
        try {
            return isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    public static void requestPermission() {
        if (isRunning() && !isGranted()) Shizuku.requestPermission(REQUEST_CODE);
    }
}
