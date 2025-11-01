package com.journeygirl.habitlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?     // ← アイコンを追加
)

fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(mainIntent, 0)
    }

    return resolved.map {
        LaunchableApp(
            label = it.loadLabel(pm).toString(),
            packageName = it.activityInfo.packageName,
            icon = it.loadIcon(pm)        // ← ここでアイコンを読み込み
        )
    }.distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
