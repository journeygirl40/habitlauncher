package com.journeygirl.habitlauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object AlarmScheduler {
    // 追加
    fun scheduleFromNextCycle(
        context: Context,
        pkg: String,
        minDelaySeconds: Long = 0L
    ) {
        val ctx = context.applicationContext
        val thresholdMin = runBlocking { alertThresholdMinutesFlow(ctx).first() }
        val resetMap     = runBlocking { resetTimeMapFlow(ctx).first() }
        val reset = resetMap[pkg] ?: LocalTime.MIDNIGHT

        val now = ZonedDateTime.now()
        val todayReset = now.withHour(reset.hour).withMinute(reset.minute).withSecond(0).withNano(0)
        var target = todayReset.minusMinutes(thresholdMin.toLong()).plusDays(1) // ★常に翌日

        val minTarget = now.plusSeconds(minDelaySeconds)
        if (target.isBefore(minTarget)) target = minTarget

        cancelForPackage(ctx, pkg)
        val pi = pendingIntentFor(ctx, pkg)
        val triggerAt = target.toInstant().toEpochMilli()

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION") am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun rescheduleAllExact(context: Context) {
        val ctx = context.applicationContext
        val enabled = runBlocking { alertEnabledFlow(ctx).first() }   // ← 置き換え
        if (!enabled) {
            cancelAll(ctx)
            return
        }
        val selected = runBlocking { selectedPackagesFlow(ctx).first() } // ← 置き換え
        cancelAll(ctx)
        selected.forEach { pkg -> scheduleExactForPackage(ctx, pkg) }
    }


    fun scheduleExactForPackage(
        context: Context,
        pkg: String,
        minDelaySeconds: Long = 0L
    ) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val thresholdMin = runBlocking { alertThresholdMinutesFlow(ctx).first() } // ← 置き換え
        val resetMap     = runBlocking { resetTimeMapFlow(ctx).first() }          // ← 置き換え
        val reset = resetMap[pkg] ?: LocalTime.MIDNIGHT

        val now = ZonedDateTime.now()
        var target = computeNextTrigger(reset, now, thresholdMin)

        // ★ 安全ガード：最低でも「今+minDelaySeconds」以降に補正
        val minTarget = now.plusSeconds(minDelaySeconds)
        if (target.isBefore(minTarget)) target = minTarget

        cancelForPackage(ctx, pkg)

        val pi = pendingIntentFor(ctx, pkg)
        val triggerAt = target.toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 12+ は正確アラームの可否を明示チェック
                if ((am as AlarmManager).canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    // 権限がない場合はフォールバック（できるだけ近い時間で起こす）
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // 例外が来たら確実にフォールバック
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelForPackage(context: Context, pkg: String) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntentFor(ctx, pkg))
    }

    fun cancelAll(context: Context) {
        val selected = runBlocking { selectedPackagesFlow(context).first() } // ← 置き換え
        selected.forEach { cancelForPackage(context, it) }
    }

    private fun pendingIntentFor(context: Context, pkg: String): PendingIntent {
        val intent = Intent(context, DeadlineAlarmReceiver::class.java).apply {
            action = "com.journeygirl.habitlauncher.ALARM_DEADLINE"
            putExtra("target_pkg", pkg)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            2000 + pkg.hashCode(),
            intent,
            flags
        )
    }

    private fun computeNextTrigger(reset: LocalTime, now: ZonedDateTime, thresholdMin: Int): ZonedDateTime {
        val todayReset = now.withHour(reset.hour).withMinute(reset.minute).withSecond(0).withNano(0)
        val candidate = todayReset.minusMinutes(thresholdMin.toLong())
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }
}
