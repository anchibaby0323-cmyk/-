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
import android.widget.ProgressBar;
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
    private static final long IMPORT_TIMEOUT_MS = 12000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView picked;
    private TextView progressText;
    private ProgressBar progressBar;
    private Button installButton;
    private File stagedFile;
    private volatile boolean binderReady = false;
    private volatile boolean importRunning = false;
    private volatile String lastTargetPath = null;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> runOnUiThread(() -> {
        binderReady = true;
        appendStatus("✓ 已收到 Shizuku Binder");
        setProgress(8, "Shizuku 已連線");
        refreshShizukuState();
    });

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> runOnUiThread(() -> {
        binderReady = false;
        appendStatus("! Shizuku Binder 已中斷");
        setProgress(0, "Shizuku 已中斷");
    });

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == REQ_SHIZUKU) {
            runOnUiThread(() -> {
                boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                appendStatus(granted ? "✓ Shizuku 權限已授予" : "✗ Shizuku 權限被拒絕");
                setProgress(granted ? 12 : 0, granted ? "Shizuku 權限完成" : "需要 Shizuku 權限");
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        appendStatus("版本：0.3.0 experimental");
        appendStatus("已移除會卡在『正在更新數據』的 ThemeDetailActivity 直跳流程。");
        appendStatus("現在只寫入 Import staging、開啟個性主題首頁並監看是否被官方 Import 流程接手。");

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
        title.setText("MTZ 正式匯入測試 v0.3");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(6));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("安全匯入模式：不再直接打開 ThemeDetailActivity。\n若官方個性主題沒有接手 MTZ，12 秒後會自動判定失敗並清掉本次 staging，避免再次卡死。");
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, dp(12));
        root.addView(desc);

        progressText = new TextView(this);
        progressText.setText("待命");
        progressText.setTextSize(16);
        progressText.setPadding(0, dp(4), 0, dp(4));
        root.addView(progressText);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));

        Button shizuku = new Button(this);
        shizuku.setText("1. 檢查 Shizuku");
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

        installButton = new Button(this);
        installButton.setText("3. 嘗試正式匯入");
        installButton.setOnClickListener(v -> importMtz());
        root.addView(installButton);

        Button cleanup = new Button(this);
        cleanup.setText("清除本次匯入暫存");
        cleanup.setOnClickListener(v -> cleanupLastTarget());
        root.addView(cleanup);

        Button openThemes = new Button(this);
        openThemes.setText("開啟個性主題首頁");
        openThemes.setOnClickListener(v -> openThemeManagerHome());
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

    private void setProgress(int value, String text) {
        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setProgress(Math.max(0, Math.min(100, value)));
            if (progressText != null) progressText.setText(value + "% · " + text);
        });
    }

    private void setImportRunning(boolean running) {
        importRunning = running;
        runOnUiThread(() -> {
            if (installButton != null) installButton.setEnabled(!running);
        });
    }

    private void refreshShizukuState() {
        try {
            if (!Shizuku.pingBinder()) {
                binderReady = false;
                appendStatus("! 尚未收到可用的 Shizuku Binder");
                setProgress(0, "等待 Shizuku");
                return;
            }
            binderReady = true;
            int permission = Shizuku.checkSelfPermission();
            if (permission == PackageManager.PERMISSION_GRANTED) {
                appendStatus("✓ Shizuku Binder 正常，且本 App 已授權");
                setProgress(12, "Shizuku 就緒");
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                appendStatus("! Shizuku 已連線，但權限曾被拒絕");
                setProgress(5, "Shizuku 等待授權");
            } else {
                appendStatus("! Shizuku 已連線，等待授權本 App");
                setProgress(5, "Shizuku 等待授權");
            }
        } catch (Throwable t) {
            appendStatus("! Shizuku 狀態檢查失敗：" + shortError(t));
            setProgress(0, "Shizuku 檢查失敗");
        }
    }

    private void requestShizuku() {
        try {
            if (!binderReady || !Shizuku.pingBinder()) {
                appendStatus("! Binder 尚未就緒，請確認 Shizuku 正在執行");
                refreshShizukuState();
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                appendStatus("✓ Shizuku 權限已存在");
                setProgress(12, "Shizuku 就緒");
                return;
            }
            appendStatus("→ 正在向 Shizuku 請求權限…");
            Shizuku.requestPermission(REQ_SHIZUKU);
        } catch (Throwable t) {
            appendStatus("✗ Shizuku 授權失敗：" + shortError(t));
        }
    }

    private void chooseMtz() {
        if (importRunning) return;
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
        picked.setText("正在讀取：" + finalName);
        setProgress(18, "正在讀取 MTZ");
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
            if (out.length() < 64) throw new IllegalStateException("MTZ 檔案太小或讀取失敗");
            stagedFile = out;
            runOnUiThread(() -> picked.setText("已選擇：" + name + "\n" + out.length() + " bytes"));
            appendStatusFromWorker("✓ MTZ 已暫存：" + out.getAbsolutePath());
            setProgress(25, "MTZ 已就緒");
        } catch (Throwable t) {
            appendStatusFromWorker("✗ 讀取 MTZ 失敗：" + shortError(t));
            setProgress(0, "MTZ 讀取失敗");
        }
    }

    private void importMtz() {
        if (importRunning) return;
        if (stagedFile == null || !stagedFile.isFile()) {
            appendStatus("✗ 請先選擇 MTZ");
            setProgress(0, "請先選擇 MTZ");
            return;
        }
        if (!binderReady || !Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            appendStatus("✗ Shizuku Binder 或權限尚未就緒");
            requestShizuku();
            return;
        }

        setImportRunning(true);
        setProgress(30, "準備匯入");
        appendStatus("→ 開始安全匯入流程");

        executor.execute(() -> {
            String importDir = "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.data/import/theme";
            String targetName = "chatgpt_" + System.currentTimeMillis() + "_" + sanitize(stagedFile.getName());
            String targetPath = importDir + "/" + targetName;
            lastTargetPath = targetPath;

            try {
                setProgress(40, "寫入 Xiaomi Import staging");
                CommandResult copy = runShizuku("mkdir -p " + shq(importDir)
                        + " && cp " + shq(stagedFile.getAbsolutePath()) + " " + shq(targetPath)
                        + " && chmod 0644 " + shq(targetPath)
                        + " && test -s " + shq(targetPath));

                if (copy.exitCode != 0) {
                    appendStatusFromWorker("✗ 無法寫入 staging：" + copy.output);
                    setProgress(0, "寫入 staging 失敗");
                    return;
                }

                appendStatusFromWorker("✓ 已寫入 staging：" + targetPath);
                setProgress(58, "已交給個性主題待處理");

                // v0.2 直接打開 ThemeDetailActivity 會卡在「正在更新數據」。
                // v0.3 只開主頁，避免把不存在的 Local Resource 詳情頁硬打開。
                CommandResult open = runShizuku("am start -W -n com.android.thememanager/com.android.thememanager.ThemeResourceTabActivity");
                if (open.exitCode != 0) {
                    appendStatusFromWorker("! 無法開啟個性主題首頁：" + open.output);
                } else {
                    appendStatusFromWorker("✓ 個性主題首頁已開啟，開始監看官方 Import 是否接手");
                }

                long start = System.currentTimeMillis();
                int tick = 0;
                while (System.currentTimeMillis() - start < IMPORT_TIMEOUT_MS) {
                    Thread.sleep(1000L);
                    tick++;
                    CommandResult exists = runShizuku("test -e " + shq(targetPath));
                    int p = Math.min(88, 58 + tick * 2);
                    setProgress(p, "等待個性主題處理（" + tick + "s）");

                    if (exists.exitCode != 0) {
                        appendStatusFromWorker("✓ staging 檔案已被移走/接手，官方 Import 流程有反應");
                        setProgress(92, "個性主題已接手 MTZ");
                        Thread.sleep(900L);
                        setProgress(100, "已完成交接，請到『我的主題』確認");
                        return;
                    }
                }

                appendStatusFromWorker("✗ 12 秒內官方 Import 沒有接手這個 MTZ。已停止等待，不再開啟會卡死的詳情頁。");
                CommandResult cleanup = runShizuku("rm -f " + shq(targetPath));
                appendStatusFromWorker(cleanup.exitCode == 0
                        ? "✓ 已清除本次 staging，避免下次重複卡住"
                        : "! 自動清理 staging 失敗：" + cleanup.output);
                setProgress(100, "未被官方 Import 接手（已安全結束）");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                appendStatusFromWorker("! 匯入等待被中止");
                setProgress(0, "匯入已中止");
            } catch (Throwable t) {
                appendStatusFromWorker("✗ 匯入錯誤：" + shortError(t));
                setProgress(0, "匯入失敗");
            } finally {
                setImportRunning(false);
            }
        });
    }

    private void cleanupLastTarget() {
        if (lastTargetPath == null) {
            appendStatus("沒有本次可清除的 staging 路徑");
            return;
        }
        if (!binderReady || !Shizuku.pingBinder()) {
            appendStatus("✗ Shizuku 尚未連線，無法清除");
            return;
        }
        String target = lastTargetPath;
        executor.execute(() -> {
            CommandResult r = runShizuku("rm -f " + shq(target));
            appendStatusFromWorker(r.exitCode == 0 ? "✓ 已清除：" + target : "✗ 清除失敗：" + r.output);
        });
    }

    private void openThemeManagerHome() {
        executor.execute(() -> {
            if (binderReady && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                CommandResult r = runShizuku("am start -W -n com.android.thememanager/com.android.thememanager.ThemeResourceTabActivity");
                appendStatusFromWorker(r.exitCode == 0 ? "✓ 已開啟個性主題首頁" : "! 開啟失敗：" + r.output);
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
            out.append(shortError(t));
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

    private static String shortError(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
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
