package com.journeygirl.habitlauncher

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// 既にどこかで Context.dataStore を定義済みなら、この行は重複させないでください。
private val Context.dataStore by preferencesDataStore(name = "habit_prefs")

// "このサイクルで通知済み" を保持するキー: notified::<packageName> -> Long(cycleId)
private fun notifiedKey(pkg: String) = longPreferencesKey("notified::$pkg")

/**
 * 通知済みサイクルIDのマップを取得
 * 例: { "com.foo" to 20123L, "com.bar" to 20123L }
 */
fun lastNotifiedCidMapFlow(ctx: Context): Flow<Map<String, Long>> =
    ctx.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs: Preferences ->
            prefs.asMap().mapNotNull { (k, v) ->
                val key = k.name
                if (key.startsWith("notified::") && v is Long) {
                    // "notified::<pkg>" → <pkg> へ
                    key.removePrefix("notified::") to v
                } else null
            }.toMap()
        }

/** 指定パッケージの "このサイクルは通知済み" を記録 */
suspend fun setLastNotifiedCid(ctx: Context, pkg: String, cid: Long) {
    ctx.dataStore.edit { prefs ->
        prefs[notifiedKey(pkg)] = cid
    }
}

/** （任意）クリアしたいときのユーティリティ */
suspend fun clearLastNotifiedCid(ctx: Context, pkg: String) {
    ctx.dataStore.edit { prefs ->
        prefs.remove(notifiedKey(pkg))
    }
}
