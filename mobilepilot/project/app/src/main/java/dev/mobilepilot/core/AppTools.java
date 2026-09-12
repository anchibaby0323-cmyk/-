package dev.mobilepilot.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public final class AppTools {
    public static boolean launch(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        AuditLog.add("launch_app", pkg);
        return true;
    }

    @SuppressWarnings("deprecation")
    public static JSONArray listApps(Context c, String query, int limit) {
        JSONArray out = new JSONArray();
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps;
        if (Build.VERSION.SDK_INT >= 33) {
            apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        } else {
            apps = pm.getInstalledApplications(0);
        }
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        for (ApplicationInfo ai : apps) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            String label = pm.getApplicationLabel(ai).toString();
            if (!q.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(q)
                    && !ai.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
            try { out.put(new JSONObject().put("label", label).put("package", ai.packageName)); }
            catch (Exception ignored) {}
            if (out.length() >= limit) break;
        }
        return out;
    }

    public static void openUrl(Context c, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    public static void openSettings(Context c, String action) {
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    public static void openAppDetails(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
