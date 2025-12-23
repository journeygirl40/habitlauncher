package com.journeygirl.habitlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZonedDateTime
import android.content.pm.PackageManager
class DeadlineAlarmReceiver : BroadcastReceiver() {

    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("target_pkg") ?: return

        // ✅ ★ここが「通知発火時の回避」本体：まず存在チェック
        if (!isPackageInstalled(context, pkg)) {
            // ① 将来の発火を止める（AlarmManagerのPendingIntentをキャンセル）
            AlarmScheduler.cancelForPackage(context, pkg)

            // ② 追跡対象から外す（※この関数はあなたのDataStore実装に合わせて用意）
            // removeSelectedPackage(context, pkg)

            // ③（任意）関連データも掃除したいなら（存在する関数だけ呼んでOK）
            // clearLaunched(context, pkg)
            // setLastNotifiedCid(context, pkg, -1)

            return
        }

        ensureNotificationChannel(context)

        val enabled = alertEnabledFlow(context).blockingFirst()
        if (!enabled) return

        val resetTimeMap = resetTimeMapFlow(context).blockingFirst()
        val lastCycleMap = lastCycleIdMapFlow(context).blockingFirst()

        val resetTime = resetTimeMap[pkg] ?: LocalTime.MIDNIGHT
        val currentCid = cycleId(ZonedDateTime.now(), resetTime)

        val launchedToday = (lastCycleMap[pkg]?.toLong() == currentCid)
        if (launchedToday) return

        // ★ 追加：同一サイクルで既に通知済みならスキップ
        val lastNotifiedMap = lastNotifiedCidMapFlow(context).blockingFirst()
        if (lastNotifiedMap[pkg]?.toLong() == currentCid) return

        val thresholdMin: Int = alertThresholdMinutesFlow(context).blockingFirst()
        val timeText = if (thresholdMin % 60 == 0) {
            val h = thresholdMin / 60
            "$h hour" + if (h == 1) "" else "s"
        } else {
            "$thresholdMin minutes"
        }

        val appLabel = pkgToLabel(context, pkg) ?: pkg
        notifyDeadline(
            context,
            id = (1000 + pkg.hashCode().absoluteValue),
            title = "App Reminder",
            text = "$appLabel: ${timeText} left until reset."
        )

        // ★ 追加：このサイクルで通知済みとして記録
        kotlinx.coroutines.runBlocking {
            setLastNotifiedCid(context, pkg, currentCid)
        }
        AlarmScheduler.scheduleExactForPackage(context, pkg, minDelaySeconds = 10)
    }
}



// Flowを同期取得したいだけの拡張（小道具）
private fun <T> kotlinx.coroutines.flow.Flow<T>.blockingFirst(): T =
    kotlinx.coroutines.runBlocking { this@blockingFirst.first() }

private val Int.absoluteValue get() = if (this < 0) -this else this
