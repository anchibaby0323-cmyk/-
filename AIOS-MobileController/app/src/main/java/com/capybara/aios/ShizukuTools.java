package com.capybara.aios;

import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;

public final class ShizukuTools {
    private ShizukuTools() {}
    public static boolean available() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }
    public static boolean granted() {
        try { return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; } catch (Throwable t) { return false; }
    }
    public static void request() {
        try { Shizuku.requestPermission(4621); } catch (Throwable ignored) {}
    }
    public static String shell(String cmd) {
        if (!available()) return "Shizuku 尚未啟動";
        if (!granted()) return "Shizuku 尚未授權";
        try {
            Process p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            StringBuilder sb = new StringBuilder();
            BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            while ((line = out.readLine()) != null) sb.append(line).append('\n');
            while ((line = err.readLine()) != null) sb.append("[E] ").append(line).append('\n');
            sb.append("結束碼：").append(p.waitFor());
            return sb.toString();
        } catch (Throwable t) { return "Shell 失敗：" + t.getMessage(); }
    }
}
