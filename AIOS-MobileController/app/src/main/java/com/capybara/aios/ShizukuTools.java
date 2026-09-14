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
        String prefix = available() && granted()
                ? "Shizuku 已授權；目前使用安全本機 Shell fallback。\n"
                : "Shizuku 未授權或未啟動；目前使用安全本機 Shell fallback。\n";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            StringBuilder sb = new StringBuilder(prefix);
            BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            while ((line = out.readLine()) != null) sb.append(line).append('\n');
            while ((line = err.readLine()) != null) sb.append("[E] ").append(line).append('\n');
            sb.append("結束碼：").append(p.waitFor());
            return sb.toString();
        } catch (Throwable t) {
            return "Shell 失敗：" + t.getMessage();
        }
    }
}
