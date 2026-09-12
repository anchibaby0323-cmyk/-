package dev.mobilepilot.core;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class AuditLog {
    private static File file;
    private static final Object LOCK = new Object();

    public static void init(Context context) {
        file = new File(context.getFilesDir(), "mobilepilot-audit.log");
    }

    public static void add(String event, String detail) {
        synchronized (LOCK) {
            if (file == null) return;
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(ts + "\t" + event + "\t" + detail.replace("\n", " ") + "\n");
            } catch (IOException ignored) {}
        }
    }

    public static String tail(int maxLines) {
        synchronized (LOCK) {
            if (file == null || !file.exists()) return "";
            ArrayDeque<String> q = new ArrayDeque<>();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    q.addLast(line);
                    while (q.size() > maxLines) q.removeFirst();
                }
            } catch (IOException ignored) {}
            return String.join("\n", q);
        }
    }
}
