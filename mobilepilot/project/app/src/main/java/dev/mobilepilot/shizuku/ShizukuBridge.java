package dev.mobilepilot.shizuku;

import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public static final int REQUEST_CODE = 4201;
    private static volatile boolean initialized;
    private static volatile boolean binderAvailable;
    private static volatile int lastPermissionResult = PackageManager.PERMISSION_DENIED;

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        Shizuku.addBinderReceivedListener(() -> binderAvailable = true);
        Shizuku.addBinderDeadListener(() -> {
            binderAvailable = false;
            lastPermissionResult = PackageManager.PERMISSION_DENIED;
        });
        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
            if (requestCode == REQUEST_CODE) lastPermissionResult = grantResult;
        });
        try { binderAvailable = Shizuku.pingBinder(); } catch (Throwable ignored) {}
    }

    public static boolean isRunning() {
        try {
            binderAvailable = Shizuku.pingBinder();
            return binderAvailable;
        } catch (Throwable t) {
            binderAvailable = false;
            return false;
        }
    }

    public static boolean isGranted() {
        try {
            return isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    public static void requestPermission() {
        initialize();
        if (isRunning() && !isGranted()) Shizuku.requestPermission(REQUEST_CODE);
    }

    public static int lastPermissionResult() { return lastPermissionResult; }
}
