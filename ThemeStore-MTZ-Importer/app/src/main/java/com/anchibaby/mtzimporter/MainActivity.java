package com.anchibaby.mtzimporter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_PICK_MTZ = 1001;
    private static final int REQ_SHIZUKU = 1002;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView picked;
    private File stagedFile;
    private boolean binderReady = false;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> runOnUiThread(() -> {
        binderReady = true;
        appendStatus("✓ 已收到 Shizuku Binder");
        refreshShizukuState();
    });

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> runOnUiThread(() -> {
        binderReady = false;
        appendStatus("! Shizuku Binder 已中斷，請確認 Shizuku 仍在執行");
    });

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) {
            runOnUiThread(() -> appendStatus(grantResult == PackageManager.PERMISSION_GRANTED
                    ? "✓ Shizuku 權限已授予"
                    : "✗ Shizuku 權限被拒絕"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        appendStatus("版本：0.2.0 experimental");
        appendStatus("目標：Xiaomi 個性主題 3.0.5.14-global");
        appendStatus("正在等待 Shizuku Binder…");

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);

        refreshShizukuState();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int p = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("MTZ 正式匯入測試 v0.2");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("這版修正 Shizuku Binder 接收。\n\n選擇任意 .mtz → Shizuku 複製到 Theme Manager 的 Import staging → 呼叫官方 Local Resource 頁面。若官方授權檢查拒絕，不會改走 ApplyThemeForScreenshot 假裝成功。");
        desc.setTextSize(15);
        desc.setPadding(0, 0, 0, dp(16));
        root.addView(desc);

        Button shizuku = new Button(this);
        shizuku.setText("1. 授予 / 檢查 Shizuku");
        shizuku.setOnClickListener(v -> requestShizuku());
        root.addView(shizuku);

        Button choose = new Button(this);
        choose.setText("2. 選擇 MTZ");
        choose.setOnClickListener(v -> chooseMtz());
        root.addView(choose);

        picked = new TextView(this);
        picked.setText("尚未選擇檔案");
        picked.setPadding(0, dp(8), 0, dp(8));
        root.addView(picked);

        Button install = new Button(this);
        install.setText("3. 嘗試正式匯入");
        install.setOnClickListener(v -> importMtz());
        root.addView(install);

        Button openThemes = new Button(this);
        openThemes.setText("開啟個性主題");
        openThemes.setOnClickListener(v -> openThemeManager());
        root.addView(openThemes);

        TextView logTitle = new TextView(this);
        logTitle.setText("\n執行紀錄");
        logTitle.setTextSize(18);
        root.addView(logTitle);

        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setTextSize(13);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void refreshShizukuState() {
        try {
            if (!Shizuku.pingBinder()) {
                binderReady = false;
                appendStatus("! 尚未收到可用的 Shizuku Binder；若 Shizuku 已啟動，請等待數秒或重新開啟本 App");
                return;
            }
            binderReady = true;
            int permission = Shizuku.checkSelfPermission();
            if (permission == PackageManager.PERMISSION_GRANTED) {
                appendStatus("✓ Shizuku Binder 正常，且本 App 已授權");
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                appendStatus("! Shizuku 已連線，但權限曾被拒絕；請到 Shizuku 應用程式管理重新允許");
            } else {
                appendStatus("! Shizuku 已連線，等待授權本 App");
            }
        } catch (Throwable t) {
            appendStatus("! Shizuku 狀態檢查失敗：" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void requestShizuku() {
        try {
            if (!binderReady || !Shizuku.pingBinder()) {
                appendStatus("! Binder 尚未就緒。請確認 Shizuku 顯示『Shizuku 正在執行』，再等幾秒後重試。");
                refreshShizukuState();
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                appendStatus("✓ Shizuku 權限已存在，可以直接選擇 MTZ");
                return;
            }
            appendStatus("→ 正在向 Shizuku 請求權限…");
            Shizuku.requestPermission(REQ_SHIZUKU);
        } catch (Throwable t) {
            appendStatus("✗ Shizuku 授權失敗：" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void chooseMtz() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK_MTZ);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_MTZ || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = getDisplayName(uri);
        if (name == null) name = "selected.mtz";
        if (!name.toLowerCase().endsWith(".mtz")) {
            Toast.makeText(this, "請選擇 .mtz 檔案", Toast.LENGTH_LONG).show();
            return;
        }
        final String finalName = sanitize(name);
        picked.setText("處理中：" + finalName);
        executor.execute(() -> stageUri(uri, finalName));
    }

    private void stageUri(Uri uri, String name) {
        try {
            File stageDir = new File(getExternalFilesDir(null), "stage");
            if (!stageDir.exists() && !stageDir.mkdirs()) throw new IllegalStateException("無法建立暫存資料夾");
            File out = new File(stageDir, System.currentTimeMillis() + "_" + name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) throw new IllegalStateException("無法開啟所選檔案");
                byte[] buf = new byte[131072];
                int n;
                while ((n = in.read(buf)) >= 0) fos.write(buf, 0, n);
            }
            stagedFile = out;
            runOnUiThread(() -> {
                picked.setText("已選擇：" + name + "\n" + out.getAbsolutePath());
                appendStatus("✓ MTZ 已暫存，大小 " + out.length() + " bytes");
            });
        } catch (Throwable t) {
            runOnUiThread(() -> appendStatus("✗ 讀取 MTZ 失敗：" + t));
        }
    }

    private void importMtz() {
        if (stagedFile == null || !stagedFile.isFile()) {
            appendStatus("✗ 請先選擇 MTZ");
            return;
        }
        if (!binderReady || !Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            appendStatus("✗ Shizuku Binder 或權限尚未就緒");
            requestShizuku();
            return;
        }

        appendStatus("→ 開始嘗試官方 Import staging…");
        executor.execute(() -> {
            String importDir = "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.data/import/theme";
            String targetName = System.currentTimeMillis() + "_" + sanitize(stagedFile.getName());
            String targetPath = importDir + "/" + targetName;

            String cmd = "mkdir -p " + shq(importDir)
                    + " && cp " + shq(stagedFile.getAbsolutePath()) + " " + shq(targetPath)
                    + " && chmod 0644 " + shq(targetPath)
                    + " && ls -l " + shq(targetPath);

            CommandResult copy = runShizuku(cmd);
            appendStatusFromWorker("copy exit=" + copy.exitCode + "\n" + copy.output);
            if (copy.exitCode != 0) {
                appendStatusFromWorker("✗ 無法寫入 Xiaomi Import staging。這通常是 Shizuku/ROM 權限限制。");
                return;
            }

            appendStatusFromWorker("✓ 已寫入：" + targetPath);
            String localUri = "ViewLocalResource://view.local.resource/" + targetName;
            String viewCmd = "am start -W -a android.intent.action.VIEW -d " + shq(localUri)
                    + " -n com.android.thememanager/com.android.thememanager.activity.ThemeDetailActivity";
            CommandResult open = runShizuku(viewCmd);
            appendStatusFromWorker("ThemeDetailActivity exit=" + open.exitCode + "\n" + open.output);

            if (open.exitCode != 0) {
                appendStatusFromWorker("! Local Resource 入口沒有接受這個 URI，改為只開啟個性主題首頁。");
                runShizuku("am start -W -n com.android.thememanager/com.android.thememanager.ThemeResourceTabActivity");
            } else {
                appendStatusFromWorker("✓ 已交給官方 Theme Manager。請查看是否出現匯入/主題詳情，或『我的主題』是否新增項目。");
            }
        });
    }

    private void openThemeManager() {
        executor.execute(() -> {
            if (binderReady && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                CommandResult r = runShizuku("am start -W -n com.android.thememanager/com.android.thememanager.ThemeResourceTabActivity");
                appendStatusFromWorker(r.output);
            } else {
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.android.thememanager");
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    runOnUiThread(() -> startActivity(launch));
                } else appendStatusFromWorker("✗ 找不到 com.android.thememanager");
            }
        });
    }

    private CommandResult runShizuku(String command) {
        StringBuilder out = new StringBuilder();
        int code = -1;
        try {
            Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            Process p = (Process) method.invoke(null,
                    new Object[]{new String[]{"sh", "-c", command}, null, null});
            if (p == null) throw new IllegalStateException("Shizuku newProcess returned null");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
                while ((line = er.readLine()) != null) out.append(line).append('\n');
            }
            code = p.waitFor();
        } catch (Throwable t) {
            out.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        return new CommandResult(code, out.toString().trim());
    }

    private String getDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
        if (s.length() > 100) s = s.substring(s.length() - 100);
        return s;
    }

    private static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void appendStatus(String s) {
        if (status == null) return;
        status.append((status.length() == 0 ? "" : "\n") + s);
    }

    private void appendStatusFromWorker(String s) {
        runOnUiThread(() -> appendStatus(s));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class CommandResult {
        final int exitCode;
        final String output;
        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
