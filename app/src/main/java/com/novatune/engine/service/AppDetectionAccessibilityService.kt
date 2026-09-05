package com.novatune.engine.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.novatune.engine.profile.AppProfileStore

class AppDetectionAccessibilityService : AccessibilityService() {
    private var activeProfilePackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        val profile = AppProfileStore.findSavedProfile(this, pkg)
        if (profile == null) {
            if (activeProfilePackage != null && OverlayService.isRunning) stopService(Intent(this, OverlayService::class.java))
            activeProfilePackage = null
            return
        }
        if (activeProfilePackage == pkg) return
        activeProfilePackage = pkg
        if (!profile.autoSidebar || !Settings.canDrawOverlays(this)) return
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_LABEL, profile.label)
            putExtra(OverlayService.EXTRA_PACKAGE, profile.packageName)
            putExtra(OverlayService.EXTRA_KEEP_SCREEN_ON, profile.keepScreenOn)
            putExtra(OverlayService.EXTRA_SIDE, profile.sidebarSide)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onInterrupt() = Unit
}
