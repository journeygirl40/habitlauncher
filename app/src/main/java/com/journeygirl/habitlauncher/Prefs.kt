package com.journeygirl.habitlauncher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Context.appDataStore by preferencesDataStore(name = "habitlauncher_prefs")
val ALERT_THRESHOLDS = stringSetPreferencesKey("alert_thresholds")

object PrefKeys {
    // 起動対象として“選択された”パッケージ集合
    val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")

    // “今日起動した”パッケージ集合 と その日付 (YYYYMMDD)
    val LAUNCHED_PACKAGES = stringSetPreferencesKey("launched_packages")
    val LAUNCHED_YMD = stringPreferencesKey("launched_ymd")

    // パッケージごとのリセット時刻（"pkg|HH:mm" の文字列セットで保持）
    val RESET_TIMES = stringSetPreferencesKey("reset_times")
    // アプリごとの「最後に起動したサイクルID」を保持（"pkg|cycleId"）
    val LAST_CYCLE_IDS = stringSetPreferencesKey("last_cycle_ids")
}

/* ---------- 選択保存 ---------- */
fun selectedPackagesFlow(context: Context): Flow<Set<String>> =
    context.appDataStore.data.map { prefs -> prefs[PrefKeys.SELECTED_PACKAGES] ?: emptySet() }

suspend fun saveSelectedPackages(context: Context, packages: Set<String>) {
    context.appDataStore.edit { prefs -> prefs[PrefKeys.SELECTED_PACKAGES] = packages }
}

/* ---------- 今日の“起動済み”管理 ---------- */
private fun todayYmd(): String =
    LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.BASIC_ISO_DATE)

fun launchedPackagesFlow(context: Context): Flow<Set<String>> =
    context.appDataStore.data.map { prefs ->
        val ymd = prefs[PrefKeys.LAUNCHED_YMD]
        if (ymd == todayYmd()) prefs[PrefKeys.LAUNCHED_PACKAGES] ?: emptySet()
        else emptySet()
    }

suspend fun markLaunchedToday(context: Context, pkg: String) {
    val today = todayYmd()
    context.appDataStore.edit { prefs ->
        val ymd = prefs[PrefKeys.LAUNCHED_YMD]
        val cur = if (ymd == today) (prefs[PrefKeys.LAUNCHED_PACKAGES] ?: emptySet()) else emptySet()
        prefs[PrefKeys.LAUNCHED_YMD] = today
        prefs[PrefKeys.LAUNCHED_PACKAGES] = cur + pkg
    }
}

/** 特定アプリの“今日の起動済み”を解除（未起動に戻す） */
suspend fun clearLaunchedTodayFor(context: Context, pkg: String) {
    val today = todayYmd()
    context.appDataStore.edit { prefs ->
        val ymd = prefs[PrefKeys.LAUNCHED_YMD]
        val cur = if (ymd == today) (prefs[PrefKeys.LAUNCHED_PACKAGES] ?: emptySet()) else emptySet()
        prefs[PrefKeys.LAUNCHED_YMD] = today
        prefs[PrefKeys.LAUNCHED_PACKAGES] = cur - pkg
    }
}

/** 0:00で全体リセット（必要時にWorkManagerから呼ぶ想定） */
suspend fun resetLaunchedForToday(context: Context) {
    val today = todayYmd()
    context.appDataStore.edit { prefs ->
        prefs[PrefKeys.LAUNCHED_YMD] = today
        prefs[PrefKeys.LAUNCHED_PACKAGES] = emptySet()
    }
}

/* ---------- リセット時刻の保存/読込（Map<String, LocalTime>） ---------- */
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun resetTimeMapFlow(context: Context): Flow<Map<String, LocalTime>> =
    context.appDataStore.data.map { prefs ->
        val raw = prefs[PrefKeys.RESET_TIMES] ?: emptySet()
        buildMap {
            raw.forEach { entry ->
                val idx = entry.indexOf('|')
                if (idx > 0) {
                    val pkg = entry.substring(0, idx)
                    val hhmm = entry.substring(idx + 1)
                    runCatching { LocalTime.parse(hhmm, HHMM) }.getOrNull()?.let { put(pkg, it) }
                }
            }
        }
    }

/** 個別アプリのリセット時刻を設定（HH:mm 保存） */
suspend fun setResetTime(context: Context, pkg: String, time: LocalTime) {
    val t = time.format(HHMM)
    context.appDataStore.edit { prefs ->
        val cur = prefs[PrefKeys.RESET_TIMES] ?: emptySet()
        val without = cur.filterNot { it.startsWith("$pkg|") }.toSet()
        prefs[PrefKeys.RESET_TIMES] = without + "$pkg|$t"
    }
}

// "pkg|12345" 形式のセットを Map<String, Long> に展開
fun lastCycleIdMapFlow(context: Context): Flow<Map<String, Long>> =
    context.appDataStore.data.map { prefs ->
        val raw = prefs[PrefKeys.LAST_CYCLE_IDS] ?: emptySet()
        buildMap {
            raw.forEach { s ->
                val i = s.indexOf('|')
                if (i > 0) {
                    val pkg = s.substring(0, i)
                    val id = s.substring(i + 1).toLongOrNull()
                    if (id != null) put(pkg, id)
                }
            }
        }
    }

// 現在のサイクルIDで「起動済み」を記録
suspend fun saveLaunchedForCurrentCycle(context: Context, pkg: String, cycleId: Long) {
    context.appDataStore.edit { prefs ->
        val cur = prefs[PrefKeys.LAST_CYCLE_IDS] ?: emptySet()
        val without = cur.filterNot { it.startsWith("$pkg|") }.toSet()
        prefs[PrefKeys.LAST_CYCLE_IDS] = without + "$pkg|$cycleId"
    }
}

// 起動状態をクリア（未起動化）
suspend fun clearLaunched(context: Context, pkg: String) {
    context.appDataStore.edit { prefs ->
        val cur = prefs[PrefKeys.LAST_CYCLE_IDS] ?: emptySet()
        prefs[PrefKeys.LAST_CYCLE_IDS] = cur.filterNot { it.startsWith("$pkg|") }.toSet()
    }
}

fun alertThresholdMapFlow(context: Context): Flow<Map<String, Int>> =
    context.appDataStore.data.map { prefs ->
        val raw = prefs[ALERT_THRESHOLDS] ?: emptySet()
        buildMap {
            raw.forEach { s ->
                val i = s.indexOf('|')
                if (i > 0) {
                    val pkg = s.substring(0, i)
                    val min = s.substring(i + 1).toIntOrNull()
                    if (min != null) put(pkg, min)
                }
            }
        }
    }

suspend fun setAlertThresholdMinutes(context: Context, pkg: String, minutes: Int) {
    context.appDataStore.edit { prefs ->
        val cur = prefs[ALERT_THRESHOLDS] ?: emptySet()
        val without = cur.filterNot { it.startsWith("$pkg|") }.toSet()
        prefs[ALERT_THRESHOLDS] = without + "$pkg|$minutes"
    }
}

// ▼ グローバル通知設定（全体一律）
val ALERT_ENABLED = booleanPreferencesKey("alert_enabled")
val ALERT_THRESHOLD_MINUTES = intPreferencesKey("alert_threshold_minutes")

fun alertEnabledFlow(context: Context) =
    context.appDataStore.data.map { it[ALERT_ENABLED] ?: false }

fun alertThresholdMinutesFlow(context: Context) =
    context.appDataStore.data.map { it[ALERT_THRESHOLD_MINUTES] ?: 60 } // 既定=60分

suspend fun setAlertEnabled(context: Context, enabled: Boolean) {
    context.appDataStore.edit { it[ALERT_ENABLED] = enabled }
}

suspend fun setAlertThresholdMinutes(context: Context, minutes: Int) {
    context.appDataStore.edit { it[ALERT_THRESHOLD_MINUTES] = minutes.coerceAtLeast(1) }
}
