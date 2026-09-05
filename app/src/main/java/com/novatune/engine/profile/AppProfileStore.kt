package com.novatune.engine.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LauncherApp(val packageName: String, val label: String)
data class AppProfile(val packageName: String, val label: String, val preLaunchReclaim: Boolean = true, val autoSidebar: Boolean = true, val keepScreenOn: Boolean = false, val sidebarSide: String = "RIGHT")

object AppProfileStore {
    private const val PREFS = "novatune_app_profiles"
    private const val KEY_PACKAGES = "profile_packages"
    fun loadLaunchableApps(context: Context): List<LauncherApp> {
        val pm=context.packageManager; val i=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val r=if(Build.VERSION.SDK_INT>=33) pm.queryIntentActivities(i,PackageManager.ResolveInfoFlags.of(0L)) else { @Suppress("DEPRECATION") pm.queryIntentActivities(i,0) }
        return r.mapNotNull { x -> val a=x.activityInfo?:return@mapNotNull null; val p=a.packageName?:return@mapNotNull null; if(p==context.packageName)return@mapNotNull null; LauncherApp(p,x.loadLabel(pm)?.toString()?.ifBlank{p}?:p) }.distinctBy{it.packageName}.sortedBy{it.label.lowercase()}
    }
    fun loadProfiles(context: Context): List<AppProfile> { val p=prefs(context); return p.getStringSet(KEY_PACKAGES,emptySet()).orEmpty().map{loadProfile(context,it,p.getString(key(it,"label"),it)?:it)}.sortedBy{it.label.lowercase()} }
    fun findSavedProfile(context: Context, packageName: String): AppProfile? { val p=prefs(context); if(packageName !in p.getStringSet(KEY_PACKAGES,emptySet()).orEmpty()) return null; return loadProfile(context,packageName,p.getString(key(packageName,"label"),packageName)?:packageName) }
    fun loadProfile(context: Context, packageName:String,label:String):AppProfile { val p=prefs(context); val side=p.getString(key(packageName,"side"),"RIGHT")?.takeIf{it=="LEFT"||it=="RIGHT"}?:"RIGHT"; return AppProfile(packageName,p.getString(key(packageName,"label"),label)?:label,p.getBoolean(key(packageName,"reclaim"),true),p.getBoolean(key(packageName,"sidebar"),true),p.getBoolean(key(packageName,"screen"),false),side) }
    fun saveProfile(context:Context,profile:AppProfile){ val p=prefs(context); val s=p.getStringSet(KEY_PACKAGES,emptySet()).orEmpty().toMutableSet(); s+=profile.packageName; p.edit().putStringSet(KEY_PACKAGES,s).putString(key(profile.packageName,"label"),profile.label).putBoolean(key(profile.packageName,"reclaim"),profile.preLaunchReclaim).putBoolean(key(profile.packageName,"sidebar"),profile.autoSidebar).putBoolean(key(profile.packageName,"screen"),profile.keepScreenOn).putString(key(profile.packageName,"side"),profile.sidebarSide).apply() }
    private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    private fun key(p:String,s:String)="$p.$s"
}
