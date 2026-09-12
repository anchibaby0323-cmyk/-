package dev.mobilepilot.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import dev.mobilepilot.server.LocalMcpService;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (c.getSharedPreferences("mp",0).getBoolean("auto_start",false)) {
            ContextCompat.startForegroundService(c, new Intent(c, LocalMcpService.class));
        }
    }
}
