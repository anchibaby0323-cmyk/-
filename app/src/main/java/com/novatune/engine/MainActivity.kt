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
import androidx.lifecycle.lifecycleScope
import com.novatune.engine.core.PerformanceBooster
import com.novatune.engine.profile.AppProfile
import com.novatune.engine.profile.AppProfileStore
import com.novatune.engine.service.OverlayService
import com.novatune.engine.ui.GamingDashboardScreen
import com.novatune.engine.ui.NovaTuneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                    onLaunchProfile = ::launchProfile,
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
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
            overlayRunning = false
            return
        }
        startOverlay(null)
    }

    private fun launchProfile(profile: AppProfile) {
        lifecycleScope.launch {
            AppProfileStore.saveProfile(this@MainActivity, profile)

            if (profile.preLaunchReclaim) {
                booster.runManualLocalReclaim()
            }

            if (profile.autoSidebar) {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    openOverlayPermission()
                    return@launch
                }
                startOverlay(profile)
                delay(180)
            } else if (OverlayService.isRunning) {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                overlayRunning = false
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(profile.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        }
    }

    private fun startOverlay(profile: AppProfile?) {
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

        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            if (profile != null) {
                putExtra(OverlayService.EXTRA_LABEL, profile.label)
                putExtra(OverlayService.EXTRA_PACKAGE, profile.packageName)
                putExtra(OverlayService.EXTRA_KEEP_SCREEN_ON, profile.keepScreenOn)
                putExtra(OverlayService.EXTRA_SIDE, profile.sidebarSide)
            }
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        overlayRunning = true
    }
}
