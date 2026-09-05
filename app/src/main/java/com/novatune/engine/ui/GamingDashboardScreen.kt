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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.novatune.engine.profile.AppProfile
import com.novatune.engine.profile.AppProfileStore
import com.novatune.engine.profile.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onLaunchProfile: (AppProfile) -> Unit,
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
                text = "APP PROFILES + SIDEBAR",
                color = NovaWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "每個 App 可單獨設定 NovaTune 啟動流程與霓虹側邊欄",
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

            AppProfilesPanel(
                overlayGranted = overlayGranted,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onLaunchProfile = onLaunchProfile
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NovaPanel, RoundedCornerShape(20.dp))
                    .border(1.dp, NovaPurple.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("SIDEBAR HUD", color = NovaWhite, fontWeight = FontWeight.Bold)
                Text(
                    if (overlayGranted) "懸浮窗權限：已授權" else "懸浮窗權限：尚未授權",
                    color = if (overlayGranted) NovaCyan else NovaMuted,
                    fontSize = 13.sp
                )
                Text(
                    "新版側欄是 62dp 霓虹浮動把手，可直接點擊或向內滑動展開。",
                    color = NovaMuted,
                    fontSize = 12.sp
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
                            if (overlayRunning) "停止側欄" else "啟動側欄",
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
                    "只整理 NovaTune 自己的 cache / Java heap；也可以設成指定 App 啟動前自動執行。",
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
                "無 Root／Shizuku／ADB 時，Android 不允許 NovaTune 強制改其他 App 的 CPU、GPU、GC 或 Socket；Profile 只使用公開 API 做可驗證的啟動前自我整理與 Overlay 行為。",
                color = NovaMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppProfilesPanel(
    overlayGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onLaunchProfile: (AppProfile) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var profiles by remember { mutableStateOf(AppProfileStore.loadProfiles(context)) }
    var pickerOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppProfile?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { AppProfileStore.loadLaunchableApps(context) }
        profiles = AppProfileStore.loadProfiles(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaPanel.copy(alpha = 0.96f), RoundedCornerShape(22.dp))
            .border(1.dp, NovaCyan.copy(alpha = 0.38f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("APP PROFILES", color = NovaCyan, fontWeight = FontWeight.Black)
                Text("為不同 App 儲存獨立側欄與啟動設定", color = NovaMuted, fontSize = 11.sp)
            }
            Text("${profiles.size}", color = NovaPurple, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }

        if (profiles.isEmpty()) {
            Text("還沒有設定。先選一個遊戲或 App。", color = NovaMuted, fontSize = 12.sp)
        } else {
            profiles.forEach { profile ->
                SavedProfileCard(
                    profile = profile,
                    onEdit = { editing = profile },
                    onLaunch = { onLaunchProfile(profile) }
                )
            }
        }

        Button(
            onClick = { pickerOpen = !pickerOpen },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NovaPurple)
        ) {
            Text(if (pickerOpen) "關閉 App 選擇器" else "＋ 新增 / 選擇 App")
        }

        if (pickerOpen) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜尋 App") },
                singleLine = true
            )
            val filtered = apps.filter {
                search.isBlank() ||
                    it.label.contains(search, ignoreCase = true) ||
                    it.packageName.contains(search, ignoreCase = true)
            }
            filtered.take(18).forEach { app ->
                AppChoiceRow(app) {
                    editing = AppProfileStore.loadProfile(context, app.packageName, app.label)
                    pickerOpen = false
                }
            }
            if (filtered.size > 18) {
                Text("還有 ${filtered.size - 18} 個結果，輸入名稱可縮小範圍。", color = NovaMuted, fontSize = 10.sp)
            }
        }

        editing?.let { profile ->
            ProfileEditor(
                profile = profile,
                overlayGranted = overlayGranted,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onChange = { editing = it },
                onSave = {
                    AppProfileStore.saveProfile(context, it)
                    profiles = AppProfileStore.loadProfiles(context)
                    editing = it
                },
                onLaunch = {
                    AppProfileStore.saveProfile(context, it)
                    profiles = AppProfileStore.loadProfiles(context)
                    onLaunchProfile(it)
                }
            )
        }
    }
}

@Composable
private fun SavedProfileCard(
    profile: AppProfile,
    onEdit: () -> Unit,
    onLaunch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaBlack.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
            .border(1.dp, NovaPurple.copy(alpha = 0.34f), RoundedCornerShape(15.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(profile.label, color = NovaWhite, fontWeight = FontWeight.Bold)
        Text(profile.packageName, color = NovaMuted, fontSize = 9.sp, maxLines = 1)
        Text(
            "側欄 ${if (profile.autoSidebar) "ON" else "OFF"} · ${if (profile.sidebarSide == "LEFT") "左側" else "右側"} · 啟動前整理 ${if (profile.preLaunchReclaim) "ON" else "OFF"}",
            color = NovaCyan,
            fontSize = 10.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onLaunch,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NovaCyan)
            ) {
                Text("啟動", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NovaPanel)
            ) {
                Text("編輯", color = NovaWhite)
            }
        }
    }
}

@Composable
private fun AppChoiceRow(app: LauncherApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaBlack.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, color = NovaWhite, fontWeight = FontWeight.SemiBold)
            Text(app.packageName, color = NovaMuted, fontSize = 9.sp, maxLines = 1)
        }
        Text("選擇 ›", color = NovaCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileEditor(
    profile: AppProfile,
    overlayGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onChange: (AppProfile) -> Unit,
    onSave: (AppProfile) -> Unit,
    onLaunch: (AppProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaBlack.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .border(1.dp, NovaCyan.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("EDIT PROFILE // ${profile.label}", color = NovaCyan, fontWeight = FontWeight.Black)

        ToggleRow(
            label = "啟動前降低 NovaTune 記憶體占用",
            description = "手動執行 NovaTune 自身 local reclaim 後再開 App",
            checked = profile.preLaunchReclaim,
            onCheckedChange = { onChange(profile.copy(preLaunchReclaim = it)) }
        )
        ToggleRow(
            label = "啟動時顯示側邊欄",
            description = "在目標 App 上方保留可點擊的 Nova 浮動把手",
            checked = profile.autoSidebar,
            onCheckedChange = { onChange(profile.copy(autoSidebar = it)) }
        )
        ToggleRow(
            label = "保持螢幕亮起",
            description = "側邊欄顯示期間對 Overlay 使用 KEEP_SCREEN_ON",
            checked = profile.keepScreenOn,
            onCheckedChange = { onChange(profile.copy(keepScreenOn = it)) }
        )

        Text("側邊欄位置", color = NovaMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onChange(profile.copy(sidebarSide = "LEFT")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (profile.sidebarSide == "LEFT") NovaCyan else NovaPanel
                )
            ) {
                Text("左側", color = if (profile.sidebarSide == "LEFT") Color.Black else NovaWhite)
            }
            Button(
                onClick = { onChange(profile.copy(sidebarSide = "RIGHT")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (profile.sidebarSide == "RIGHT") NovaCyan else NovaPanel
                )
            ) {
                Text("右側", color = if (profile.sidebarSide == "RIGHT") Color.Black else NovaWhite)
            }
        }

        if (profile.autoSidebar && !overlayGranted) {
            Text("要顯示側邊欄，需要先授權『顯示在其他 App 上層』。", color = NovaMuted, fontSize = 10.sp)
            Button(
                onClick = onRequestOverlayPermission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NovaPurple)
            ) {
                Text("開啟懸浮窗權限")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(profile) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NovaPanel)
            ) {
                Text("儲存", color = NovaWhite)
            }
            Button(
                onClick = { onLaunch(profile) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NovaCyan)
            ) {
                Text("儲存並啟動", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = NovaWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = NovaMuted, fontSize = 9.sp, lineHeight = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
