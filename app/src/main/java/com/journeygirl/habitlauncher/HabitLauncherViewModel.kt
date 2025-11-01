package com.journeygirl.habitlauncher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import android.graphics.drawable.Drawable

data class TrackedApp(
    val name: String,
    val packageName: String,
    val resetTime: LocalTime,   // アプリごとのリセット時刻
    val launchedFlagToday: Boolean,
    val icon: Drawable?            // ★ 追加
)

class HabitLauncherViewModel : ViewModel() {

    private val _now = MutableStateFlow(ZonedDateTime.now(ZoneId.systemDefault()))
    val now: StateFlow<ZonedDateTime> = _now

    init {
        viewModelScope.launch {
            while (true) {
                _now.value = ZonedDateTime.now(ZoneId.systemDefault())
                delay(1000L) // 1秒ごとに更新
            }
        }
    }
}

// 残り時間の計算ヘルパー
fun nextResetAt(resetTime: LocalTime, now: ZonedDateTime): ZonedDateTime {
    val candidate = now.toLocalDate().atTime(resetTime).atZone(now.zone)
    return if (now <= candidate) candidate else candidate.plusDays(1)
}

fun remainingUntilReset(resetTime: LocalTime, now: ZonedDateTime): Duration {
    val next = nextResetAt(resetTime, now)
    return Duration.between(now, next).coerceAtLeast(Duration.ZERO)
}

fun formatDurationShort(d: Duration): String {
    val total = d.seconds
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
