package dev.mobilepilot.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import dev.mobilepilot.R;

public class LocalMcpService extends Service {
    private static final int ID = 4101;
    private static final String CHANNEL = "mobilepilot_mcp";
    private McpHttpServer server;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "MobilePilot 本機服務", NotificationManager.IMPORTANCE_LOW));
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("MobilePilot")
                .setContentText("本機 MCP：127.0.0.1:8473/mcp")
                .setOngoing(true)
                .build();
        startForeground(ID, n);
        server = new McpHttpServer(this);
        try { server.start(8473); } catch (Exception e) { stopSelf(); }
    }

    @Override public void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
