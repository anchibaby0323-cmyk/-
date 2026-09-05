package com.novatune.engine.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatune.engine.core.BoostResult
import com.novatune.engine.core.DeviceStats
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GamingDashboardScreen(
    statsFlow: StateFlow<DeviceStats>,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleOverlay: () -> Unit,
    onBoost: suspend () -> BoostResult
) {
    val stats by statsFlow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var burstKey by remember { mutableIntStateOf(0) }
    var boostText by remember { mutableStateOf("等待手動觸發") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaBlack)
    ) {
        EdgeLighting(Modifier.fillMaxSize())
        BoostBurstFx(trigger = burstKey, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "NOVATUNE // ENGINE",
                color = NovaCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "REAL-TIME SYSTEM HUD",
                color = NovaWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "真實 Android 公開 API 遙測 · 無 Root · 無 Shizuku · 無 ADB",
                color = NovaMuted,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonGaugeCard(
                    modifier = Modifier.weight(1f),
                    title = "SYSTEM RAM",
                    fraction = stats.usedRamFraction,
                    mainText = "${(stats.usedRamFraction * 100).toInt()}%",
                    subText = "可用 ${formatGiB(stats.availMemBytes)} GiB"
                )
                NeonGaugeCard(
                    modifier = Modifier.weight(1f),
                    title = "THREADS",
                    fraction = (stats.activeThreadCount / 64f).coerceIn(0f, 1f),
                    mainText = stats.activeThreadCount.toString(),
                    subText = "NovaTune 活躍執行緒"
                )
            }

            TelemetryPanel(stats)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NovaPanel, RoundedCornerShape(20.dp))
                    .border(1.dp, NovaPurple.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("GAMING OVERLAY", color = NovaWhite, fontWeight = FontWeight.Bold)
                Text(
                    if (overlayGranted) "懸浮窗權限：已授權" else "懸浮窗權限：尚未授權",
                    color = if (overlayGranted) NovaCyan else NovaMuted,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = NovaPurple)
                    ) {
                        Text("權限設定")
                    }
                    Button(
                        enabled = overlayGranted,
                        onClick = onToggleOverlay,
                        colors = ButtonDefaults.buttonColors(containerColor = NovaCyan)
                    ) {
                        Text(
                            if (overlayRunning) "停止 HUD" else "啟動 HUD",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NovaPanel, RoundedCornerShape(24.dp))
                    .border(1.dp, NovaCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("LOCAL RECLAIM", color = NovaCyan, fontWeight = FontWeight.Bold)
                Text(
                    "只整理 NovaTune 自己的 cache / Java heap，不偽裝成能清別的遊戲。",
                    color = NovaMuted,
                    fontSize = 13.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.horizontalGradient(listOf(NovaCyan, NovaPurple)),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            heavyHaptic(context)
                            burstKey++
                            scope.launch {
                                val result = onBoost()
                                boostText = "NovaTune heap 釋放 ${formatMiB(result.localHeapFreedBytes)} MiB · " +
                                    "系統可用 RAM 取樣差 ${signedMiB(result.availableRamDeltaBytes)} MiB"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "BOOST LOCAL",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(boostText, color = NovaWhite, fontSize = 12.sp)
            }

            Text(
                "GC 可能造成短暫停頓，因此只在你手動觸發時執行；RAM 前後差值是真實即時取樣。",
                color = NovaMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TelemetryPanel(stats: DeviceStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaPanel.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .border(1.dp, NovaCyan.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryRow("NETWORK", stats.network)
        TelemetryRow("DISPLAY", "%.1f Hz".format(stats.displayRefreshRateHz))
        TelemetryRow("JAVA HEAP", "${formatMiB(stats.javaHeapUsedBytes)} MiB")
        TelemetryRow("NATIVE HEAP", "${formatMiB(stats.nativeHeapUsedBytes)} MiB")
        TelemetryRow(
            "MEMORY STATE",
            if (stats.lowMemory) "LOW MEMORY" else "NORMAL",
            if (stats.lowMemory) NovaDanger else NovaCyan
        )
        TelemetryRow("LMK THRESHOLD", "${formatGiB(stats.lowMemoryThresholdBytes)} GiB avail")
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, valueColor: Color = NovaWhite) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = NovaMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NeonGaugeCard(
    modifier: Modifier,
    title: String,
    fraction: Float,
    mainText: String,
    subText: String
) {
    Column(
        modifier = modifier
            .background(NovaPanel, RoundedCornerShape(20.dp))
            .border(1.dp, NovaPurple.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = NovaMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        CircularGauge(fraction, mainText)
        Spacer(Modifier.height(8.dp))
        Text(subText, color = NovaMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CircularGauge(fraction: Float, text: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(118.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -225f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(NovaCyan, NovaPurple, NovaCyan)),
                startAngle = -225f,
                sweepAngle = 270f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(text, color = NovaWhite, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun EdgeLighting(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "edge")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge-angle"
    )

    Canvas(modifier.padding(7.dp)) {
        rotate(angle, pivot = center) {
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = listOf(NovaCyan, NovaPurple, NovaCyan, Color.Transparent, NovaCyan),
                    center = center
                ),
                topLeft = Offset(1f, 1f),
                size = Size(size.width - 2f, size.height - 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun BoostBurstFx(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(620))
        }
    }

    val p = progress.value
    if (p >= 1f) return

    Canvas(modifier) {
        val radius = size.minDimension * 0.48f * p
        drawCircle(
            color = NovaCyan.copy(alpha = (1f - p) * 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = (8f * (1f - p)).coerceAtLeast(1f))
        )
        repeat(28) { i ->
            val particleAngle = (2.0 * PI * i / 28.0) + (i % 3) * 0.07
            val distance = radius * (0.45f + (i % 5) * 0.08f)
            val x = center.x + cos(particleAngle).toFloat() * distance
            val y = center.y + sin(particleAngle).toFloat() * distance
            drawCircle(
                color = if (i % 2 == 0) NovaCyan.copy(alpha = 1f - p)
                else NovaPurple.copy(alpha = 1f - p),
                radius = 2.5.dp.toPx() * (1f - p * 0.6f),
                center = Offset(x, y)
            )
        }
    }
}

private fun heavyHaptic(context: Context) {
    @Suppress("DEPRECATION")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }
}

private fun formatGiB(bytes: Long): String = "%.2f".format(bytes / 1073741824.0)
private fun formatMiB(bytes: Long): String = "%.1f".format(bytes / 1048576.0)
private fun signedMiB(bytes: Long): String = "%+.1f".format(bytes / 1048576.0)
