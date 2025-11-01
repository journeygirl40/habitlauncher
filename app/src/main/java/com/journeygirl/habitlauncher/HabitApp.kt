package com.journeygirl.habitlauncher

import android.app.Application
import androidx.work.*
import java.time.Duration

class HabitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)

// 旧: WorkManager の残骸を掃除（任意）
        WorkManager.getInstance(this).cancelAllWorkByTag("deadline_once_cleanup")

// ▼ 起動時に現在設定で正確アラームを張り直す
        AlarmScheduler.rescheduleAllExact(this)
    }
}