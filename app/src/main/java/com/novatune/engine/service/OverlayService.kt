package com.novatune.engine.service

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
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
    private var sizeAnimator: ValueAnimator? = null

    private val activeLabelState = mutableStateOf("NovaTune")
    private val activePackageState = mutableStateOf("")
    private val keepScreenOnState = mutableStateOf(false)
    private val sideState = mutableStateOf("RIGHT")

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
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return result
        }

        activeLabelState.value = intent?.getStringExtra(EXTRA_LABEL)
            ?.takeIf { it.isNotBlank() } ?: activeLabelState.value
        activePackageState.value = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        keepScreenOnState.value = intent?.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, false) ?: false
        sideState.value = intent?.getStringExtra(EXTRA_SIDE)
            ?.takeIf { it == "LEFT" || it == "RIGHT" } ?: sideState.value

        if (!::composeView.isInitialized) {
            addOverlay()
        } else {
            applyWindowProfile()
        }
        return START_NOT_STICKY
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
            .setContentText("側邊欄 HUD 正在執行")
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
            dp(COLLAPSED_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        applyProfileToParams()

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NovaTuneTheme {
                    val stats by booster.stats.collectAsState()
                    OverlayRoot(
                        stats = stats,
                        activeLabel = activeLabelState.value,
                        activePackage = activePackageState.value,
                        side = sideState.value,
                        keepScreenOn = keepScreenOnState.value,
                        onExpandedChanged = ::animateWindow,
                        onClose = ::stopSelf,
                        onBoost = { booster.runManualLocalReclaim() }
                    )
                }
            }
        }
        windowManager.addView(composeView, params)
    }

    private fun applyProfileToParams() {
        params.gravity = (if (sideState.value == "LEFT") Gravity.START else Gravity.END) or
            Gravity.CENTER_VERTICAL
        params.flags = if (keepScreenOnState.value) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
    }

    private fun applyWindowProfile() {
        if (!::params.isInitialized || !::composeView.isInitialized) return
        applyProfileToParams()
        runCatching { windowManager.updateViewLayout(composeView, params) }
    }

    private fun animateWindow(expanded: Boolean) {
        if (!::composeView.isInitialized) return
        val startWidth = params.width
        val startHeight = params.height
        val targetWidth = dp(if (expanded) EXPANDED_WIDTH_DP else COLLAPSED_WIDTH_DP)
        val targetHeight = if (expanded) expandedHeightPx() else dp(COLLAPSED_HEIGHT_DP)

        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (expanded) 380L else 250L
            interpolator = if (expanded) OvershootInterpolator(0.65f) else DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                params.width = (startWidth + (targetWidth - startWidth) * t).roundToInt()
                params.height = (startHeight + (targetHeight - startHeight) * t).roundToInt()
                applyProfileToParams()
                runCatching { windowManager.updateViewLayout(composeView, params) }
            }
            start()
        }
    }

    private fun expandedHeightPx(): Int = minOf(
        dp(EXPANDED_HEIGHT_DP),
        (resources.displayMetrics.heightPixels * 0.80f).roundToInt()
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NovaTune Sidebar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "User-started NovaTune gaming sidebar overlay"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        sizeAnimator?.cancel()
        if (::composeView.isInitialized) {
            runCatching { windowManager.removeView(composeView) }
        }
        if (::booster.isInitialized) booster.close()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_LABEL = "novatune.extra.LABEL"
        const val EXTRA_PACKAGE = "novatune.extra.PACKAGE"
        const val EXTRA_KEEP_SCREEN_ON = "novatune.extra.KEEP_SCREEN_ON"
        const val EXTRA_SIDE = "novatune.extra.SIDE"

        private const val CHANNEL_ID = "novatune_overlay"
        private const val NOTIFICATION_ID = 1201
        private const val COLLAPSED_WIDTH_DP = 62
        private const val COLLAPSED_HEIGHT_DP = 154
        private const val EXPANDED_WIDTH_DP = 352
        private const val EXPANDED_HEIGHT_DP = 590

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

@Composable
private fun OverlayRoot(
    stats: DeviceStats,
    activeLabel: String,
    activePackage: String,
    side: String,
    keepScreenOn: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onBoost: suspend () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            SidebarHandle(
                side = side,
                onOpen = {
                    onExpandedChanged(true)
                    expanded = true
                }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(
                initialOffsetX = { if (side == "LEFT") -it else it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { if (side == "LEFT") -it else it },
                animationSpec = tween(210)
            ) + fadeOut(tween(170)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NovaBlack.copy(alpha = 0.97f), RoundedCornerShape(26.dp))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(NovaCyan, NovaPurple, NovaCyan)),
                        RoundedCornerShape(26.dp)
                    )
            ) {
                OverlayEdgeLight(Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NOVATUNE SIDEBAR", color = NovaCyan, fontWeight = FontWeight.Black)
                            Text("PROFILE · $activeLabel", color = NovaWhite, fontSize = 12.sp)
                            if (activePackage.isNotBlank()) {
                                Text(activePackage, color = NovaMuted, fontSize = 9.sp, maxLines = 1)
                            }
                        }
                        Text("LIVE", color = NovaPurple, fontWeight = FontWeight.Bold)
                    }

                    HudMetric("RAM", "${(stats.usedRamFraction * 100).toInt()}% used")
                    HudMetric("AVAILABLE", "%.2f GiB".format(stats.availMemBytes / 1073741824.0))
                    HudMetric("THREADS", stats.activeThreadCount.toString())
                    HudMetric("DISPLAY", "%.1f Hz".format(stats.displayRefreshRateHz))
                    HudMetric("NETWORK", stats.network)
                    HudMetric("KEEP SCREEN", if (keepScreenOn) "ON" else "OFF")

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
                        Text("收合側邊欄")
                    }

                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NovaPanel)
                    ) {
                        Text("停止側邊欄", color = NovaWhite)
                    }

                    Text(
                        "Profile 會控制 NovaTune 自身記憶體整理、側欄與保持螢幕；不偽造可直接改其他 App CPU/GPU 的能力。",
                        color = NovaMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarHandle(side: String, onOpen: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sidebar-handle")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sidebar-glow"
    )
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NovaBlack.copy(alpha = 0.96f), NovaPurple.copy(alpha = 0.82f), NovaBlack)
                ),
                RoundedCornerShape(22.dp)
            )
            .border(2.dp, NovaCyan.copy(alpha = glow), RoundedCornerShape(22.dp))
            .pointerInput(side) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, amount -> dragTotal += amount },
                    onDragEnd = {
                        val open = if (side == "LEFT") dragTotal > 18f else dragTotal < -18f
                        if (open) onOpen()
                        dragTotal = 0f
                    }
                )
            }
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp, horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("N", color = NovaCyan, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("O\nV\nA", color = NovaWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 11.sp)
            Text(if (side == "LEFT") "›" else "‹", color = NovaCyan, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("HUD", color = NovaMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
    val transition = rememberInfiniteTransition(label = "overlay-edge")
    val glow by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "edge-glow"
    )
    Canvas(modifier.padding(4.dp)) {
        drawRoundRect(
            brush = Brush.sweepGradient(
                listOf(
                    NovaCyan.copy(alpha = glow),
                    NovaPurple.copy(alpha = glow),
                    NovaCyan.copy(alpha = glow)
                )
            ),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )
    }
}
