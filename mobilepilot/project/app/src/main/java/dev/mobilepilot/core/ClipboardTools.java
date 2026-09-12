package dev.mobilepilot.core;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public final class ClipboardTools {
    public static void set(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("MobilePilot", text));
    }

    public static String get(Context c) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (!cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
        return s == null ? "" : s.toString();
    }
}
