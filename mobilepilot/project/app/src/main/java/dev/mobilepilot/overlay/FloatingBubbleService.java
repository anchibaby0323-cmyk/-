package dev.mobilepilot.overlay;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.core.app.NotificationCompat;
import dev.mobilepilot.MainActivity;
import dev.mobilepilot.R;
import dev.mobilepilot.access.MobileAccessibilityService;

public class FloatingBubbleService extends Service {
    private WindowManager wm;
    private View bubble;

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        String ch = "mobilepilot_bubble";
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(ch, "MobilePilot 浮動球", NotificationManager.IMPORTANCE_MIN));
        startForeground(4102, new NotificationCompat.Builder(this, ch)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("MobilePilot 浮動球")
                .setOngoing(true)
                .build());

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Button main = new Button(this); main.setText("AI");
        Button back = new Button(this); back.setText("←");
        box.addView(main, new LinearLayout.LayoutParams(150,100));
        box.addView(back, new LinearLayout.LayoutParams(150,100));

        main.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        back.setOnClickListener(v -> {
            MobileAccessibilityService a = MobileAccessibilityService.get();
            if (a != null) a.global("back");
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 20;
        lp.y = 300;
        bubble = box;
        wm.addView(bubble, lp);

        main.setOnTouchListener(new View.OnTouchListener() {
            int ox, oy; float sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    ox = lp.x; oy = lp.y; sx = e.getRawX(); sy = e.getRawY(); return false;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    lp.x = ox - (int)(e.getRawX() - sx);
                    lp.y = oy + (int)(e.getRawY() - sy);
                    wm.updateViewLayout(bubble, lp);
                    return true;
                }
                return false;
            }
        });
    }

    @Override public void onDestroy() {
        if (wm != null && bubble != null) try { wm.removeView(bubble); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
