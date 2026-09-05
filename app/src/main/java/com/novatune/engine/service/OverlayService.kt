package com.novatune.engine.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.novatune.engine.MainActivity
import com.novatune.engine.core.DeviceStats
import com.novatune.engine.core.PerformanceBooster
import com.novatune.engine.ui.NovaBlack
import com.novatune.engine.ui.NovaCyan
import com.novatune.engine.ui.NovaMuted
import com.novatune.engine.ui.NovaPanel
import com.novatune.engine.ui.NovaPurple
import com.novatune.engine.ui.NovaTuneTheme
import com.novatune.engine.ui.NovaWhite
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayService : LifecycleService() {
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var booster: PerformanceBooster

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createNotificationChannel()
        startForegroundCompat()
        booster = PerformanceBooster(applicationContext)
        windowManager = getSystemService(WindowManager::class.java)
        addOverlay()
        isRunning = true
    }

    private fun startForegroundCompat() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("NovaTune Engine")
            .setContentText("Gaming HUD 正在顯示")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun addOverlay() {
        params = WindowManager.LayoutParams(
            dp(COLLAPSED_WIDTH_DP),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NovaTuneTheme {
                    val stats by booster.stats.collectAsState()
                    OverlayRoot(
                        stats = stats,
                        onExpandedChanged = ::setExpandedWidth,
                        onClose = ::stopSelf,
                        onBoost = { booster.runManualLocalReclaim() }
                    )
                }
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun setExpandedWidth(expanded: Boolean) {
        if (!::composeView.isInitialized) return
        params.width = dp(if (expanded) EXPANDED_WIDTH_DP else COLLAPSED_WIDTH_DP)
        runCatching { windowManager.updateViewLayout(composeView, params) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NovaTune HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "User-started NovaTune gaming overlay"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        if (::composeView.isInitialized) {
            runCatching { windowManager.removeView(composeView) }
        }
        if (::booster.isInitialized) booster.close()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHANNEL_ID = "novatune_overlay"
        private const val NOTIFICATION_ID = 1201
        private const val COLLAPSED_WIDTH_DP = 24
        private const val EXPANDED_WIDTH_DP = 330

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

@Composable
private fun OverlayRoot(
    stats: DeviceStats,
    onExpandedChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onBoost: suspend () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(expanded) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, amount -> dragTotal += amount },
                    onDragEnd = {
                        val next = when {
                            dragTotal < -24f -> true
                            dragTotal > 24f -> false
                            else -> expanded
                        }
                        expanded = next
                        onExpandedChanged(next)
                        dragTotal = 0f
                    }
                )
            }
    ) {
        if (!expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.36f)
                    .width(12.dp)
                    .background(
                        Brush.verticalGradient(listOf(NovaCyan, NovaPurple, NovaCyan)),
                        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                    )
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NovaBlack.copy(alpha = 0.97f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(NovaCyan, NovaPurple, NovaCyan)),
                        RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp)
                    )
            ) {
                OverlayEdgeLight(Modifier.fillMaxSize())
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NOVATUNE HUD", color = NovaCyan, fontWeight = FontWeight.Black)
                            Text("REAL TELEMETRY", color = NovaMuted, fontSize = 10.sp)
                        }
                        Text("LIVE", color = NovaPurple, fontWeight = FontWeight.Bold)
                    }

                    HudMetric("RAM", "${(stats.usedRamFraction * 100).toInt()}% used")
                    HudMetric("AVAILABLE", "%.2f GiB".format(stats.availMemBytes / 1073741824.0))
                    HudMetric("THREADS", stats.activeThreadCount.toString())
                    HudMetric("DISPLAY", "%.1f Hz".format(stats.displayRefreshRateHz))
                    HudMetric("NETWORK", stats.network)
                    HudMetric("MEMORY", if (stats.lowMemory) "LOW" else "NORMAL")

                    Button(
                        onClick = { scope.launch { onBoost() } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NovaCyan)
                    ) {
                        Text("LOCAL RECLAIM", color = Color.Black, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = {
                            expanded = false
                            onExpandedChanged(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NovaPurple)
                    ) {
                        Text("收合 HUD")
                    }

                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NovaPanel)
                    ) {
                        Text("停止服務", color = NovaWhite)
                    }

                    Text(
                        "HUD 只讀系統公開遙測；不會修改其他 App 的 CPU、GC、Socket 或畫面模式。",
                        color = NovaMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaPanel, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = NovaMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = NovaWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OverlayEdgeLight(modifier: Modifier = Modifier) {
    Canvas(modifier.padding(4.dp)) {
        drawRoundRect(
            brush = Brush.sweepGradient(listOf(NovaCyan, NovaPurple, NovaCyan)),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )
    }
}
