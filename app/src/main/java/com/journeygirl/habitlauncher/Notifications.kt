package com.journeygirl.habitlauncher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// 通知チャンネルID
const val CHANNEL_ID_DEADLINE = "deadline_alerts"

// --- チャンネル作成（O+ 必須） ---
fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID_DEADLINE,
            "アプリ起動リマインダー",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "未起動アプリの残り時間がしきい値を下回ったときの通知"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}

// --- パッケージ名 -> 表示ラベル ---
fun pkgToLabel(context: Context, pkg: String): String? = try {
    val pm = context.packageManager
    val ai = pm.getApplicationInfo(pkg, 0)
    pm.getApplicationLabel(ai)?.toString()
} catch (_: PackageManager.NameNotFoundException) {
    null
}

// --- 通知表示ヘルパー ---
fun notifyDeadline(context: Context, id: Int, title: String, text: String) {
    // Android 13+ は POST_NOTIFICATIONS の実行時許可が必要
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return // 許可が無ければ黙ってスキップ
    }

    // 通知タップでアプリへ
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID_DEADLINE)
        // 小アイコンは必須。無ければ一旦ランチャーアイコンでOK
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    NotificationManagerCompat.from(context).notify(id, builder.build())
}
