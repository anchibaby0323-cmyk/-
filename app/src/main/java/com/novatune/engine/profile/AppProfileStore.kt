package com.novatune.engine.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LauncherApp(
    val packageName: String,
    val label: String
)

data class AppProfile(
    val packageName: String,
    val label: String,
    val preLaunchReclaim: Boolean = true,
    val autoSidebar: Boolean = true,
    val keepScreenOn: Boolean = false,
    val sidebarSide: String = "RIGHT"
)

object AppProfileStore {
    private const val PREFS = "novatune_app_profiles"
    private const val KEY_PACKAGES = "profile_packages"

    fun loadLaunchableApps(context: Context): List<LauncherApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        return resolved.mapNotNull { resolveInfo ->
            val info = resolveInfo.activityInfo ?: return@mapNotNull null
            val pkg = info.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            LauncherApp(
                packageName = pkg,
                label = resolveInfo.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
            )
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun loadProfiles(context: Context): List<AppProfile> {
        val prefs = prefs(context)
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()
        return packages.map { pkg ->
            loadProfile(context, pkg, prefs.getString(key(pkg, "label"), pkg) ?: pkg)
        }.sortedBy { it.label.lowercase() }
    }

    fun loadProfile(context: Context, packageName: String, label: String): AppProfile {
        val prefs = prefs(context)
        val side = prefs.getString(key(packageName, "side"), "RIGHT")
            ?.takeIf { it == "LEFT" || it == "RIGHT" } ?: "RIGHT"
        return AppProfile(
            packageName = packageName,
            label = prefs.getString(key(packageName, "label"), label) ?: label,
            preLaunchReclaim = prefs.getBoolean(key(packageName, "reclaim"), true),
            autoSidebar = prefs.getBoolean(key(packageName, "sidebar"), true),
            keepScreenOn = prefs.getBoolean(key(packageName, "screen"), false),
            sidebarSide = side
        )
    }

    fun saveProfile(context: Context, profile: AppProfile) {
        val prefs = prefs(context)
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toMutableSet()
        packages += profile.packageName
        prefs.edit()
            .putStringSet(KEY_PACKAGES, packages)
            .putString(key(profile.packageName, "label"), profile.label)
            .putBoolean(key(profile.packageName, "reclaim"), profile.preLaunchReclaim)
            .putBoolean(key(profile.packageName, "sidebar"), profile.autoSidebar)
            .putBoolean(key(profile.packageName, "screen"), profile.keepScreenOn)
            .putString(key(profile.packageName, "side"), profile.sidebarSide)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(packageName: String, suffix: String) = "$packageName.$suffix"
}
