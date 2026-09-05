package com.novatune.engine

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.novatune.engine.core.PerformanceBooster
import com.novatune.engine.service.OverlayService
import com.novatune.engine.ui.GamingDashboardScreen
import com.novatune.engine.ui.NovaTuneTheme

class MainActivity : ComponentActivity() {
    private lateinit var booster: PerformanceBooster
    private var overlayGranted by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        booster = PerformanceBooster(applicationContext)
        booster.requestHighestRefreshRate(this)

        setContent {
            NovaTuneTheme {
                GamingDashboardScreen(
                    statsFlow = booster.stats,
                    overlayGranted = overlayGranted,
                    overlayRunning = overlayRunning,
                    onRequestOverlayPermission = ::openOverlayPermission,
                    onToggleOverlay = ::toggleOverlay,
                    onBoost = booster::runManualLocalReclaim
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
        overlayRunning = OverlayService.isRunning
    }

    override fun onDestroy() {
        booster.close()
        super.onDestroy()
    }

    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
            overlayRunning = false
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        overlayRunning = true
    }
}
