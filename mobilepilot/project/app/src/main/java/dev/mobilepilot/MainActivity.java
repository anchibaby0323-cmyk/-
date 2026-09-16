package dev.mobilepilot;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import dev.mobilepilot.access.MobileAccessibilityService;
import dev.mobilepilot.core.AppTools;
import dev.mobilepilot.core.PermissionState;
import dev.mobilepilot.notify.MobileNotificationListener;
import dev.mobilepilot.overlay.FloatingBubbleService;
import dev.mobilepilot.server.LocalMcpService;
import dev.mobilepilot.shizuku.ShizukuBridge;

public class MainActivity extends AppCompatActivity {
    TextView status, result;
    CheckBox autoStart;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        autoStart = findViewById(R.id.autoStart);
        autoStart.setChecked(getSharedPreferences("mp", 0).getBoolean("auto_start", false));

        findViewById(R.id.btnAccessibility).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        findViewById(R.id.btnShizuku).setOnClickListener(v -> ShizukuBridge.requestPermission());
        findViewById(R.id.btnOverlay).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.btnStartServer).setOnClickListener(v ->
                ContextCompat.startForegroundService(this, new Intent(this, LocalMcpService.class)));
        findViewById(R.id.btnStopServer).setOnClickListener(v ->
                stopService(new Intent(this, LocalMcpService.class)));
        findViewById(R.id.btnBubble).setOnClickListener(v ->
                ContextCompat.startForegroundService(this, new Intent(this, FloatingBubbleService.class)));
        autoStart.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences("mp", 0).edit().putBoolean("auto_start", checked).apply());
        findViewById(R.id.btnRunCommand).setOnClickListener(v -> runShortcut());
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean access = MobileAccessibilityService.get() != null;
        boolean notificationAccess = PermissionState.notificationListenerEnabled(this);
        boolean overlay = Settings.canDrawOverlays(this);
        status.setText("無障礙：" + (access ? "✅" : "❌") +
                "\n通知存取：" + (notificationAccess ? "✅" : "❌") +
                "\n通知服務：" + (MobileNotificationListener.isConnected() ? "✅ 已連線" : "❌ 未連線") +
                "\nShizuku：" + (ShizukuBridge.isRunning() ? "✅ 運作中" : "❌ 未運作") +
                "\nShizuku 授權：" + (ShizukuBridge.isGranted() ? "✅" : "❌") +
                "\n浮窗：" + (overlay ? "✅" : "❌") +
                "\nMCP：http://127.0.0.1:8473/mcp");
    }

    private void runShortcut() {
        EditText e = findViewById(R.id.testCommand);
        String q = e.getText().toString().trim();
        MobileAccessibilityService a = MobileAccessibilityService.get();
        boolean ok = false;
        try {
            if (q.equalsIgnoreCase("返回") || q.equalsIgnoreCase("back")) ok = a != null && a.global("back");
            else if (q.equalsIgnoreCase("home") || q.equals("主畫面")) ok = a != null && a.global("home");
            else if (q.contains("通知")) ok = a != null && a.global("notifications");
            else if (q.contains("快速設定")) ok = a != null && a.global("quick_settings");
            else if (q.contains("設定")) { AppTools.openSettings(this, Settings.ACTION_SETTINGS); ok = true; }
            else result.setText("完整自然語言控制會由 AI 前端呼叫 MCP tools。");
            if (ok) result.setText("✅ 已執行：" + q);
        } catch (Exception ex) {
            result.setText("❌ " + ex);
        }
    }
}
