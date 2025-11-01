@file:OptIn(ExperimentalMaterial3Api::class)

package com.journeygirl.habitlauncher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.Context
import androidx.compose.ui.text.style.TextAlign
import androidx.work.*
import java.time.*
// 追加する import
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.PrivacyTip
import kotlinx.coroutines.flow.first


@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageApps: () -> Unit) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current   // ← ここで取得

    val scope = rememberCoroutineScope()

    // 現在値をDataStoreから取得
    val enabled by alertEnabledFlow(ctx).collectAsState(initial = false)
    val minutes by alertThresholdMinutesFlow(ctx).collectAsState(initial = 60)

    // 編集用の一時値
    var tmpEnabled by remember { mutableStateOf(false) }
    var tmpHoursText by remember { mutableStateOf("1") }

// ★ ここがポイント：Flow が更新されたら一時変数へ同期
    LaunchedEffect(enabled, minutes) {
        tmpEnabled = enabled
        tmpHoursText = ((minutes / 60).coerceAtLeast(1)).toString()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val hrs = tmpHoursText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        scope.launch {
                            setAlertEnabled(ctx, tmpEnabled)
                            setAlertThresholdMinutes(ctx, hrs * 60)

                            // ▼ 正確アラームに切り替え
                            AlarmScheduler.rescheduleAllExact(ctx)
// ★ 追加：通知時刻（リード時間）を変えたら「今サイクルの通知済みフラグ」を全アプリでクリア
                            val pkgs = selectedPackagesFlow(ctx).first()
                            pkgs.forEach { setLastNotifiedCid(ctx, it, -1) }

                            onBack()
                        }
                    }) { Text("Save") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()                           // 画面全体を使う
                .verticalScroll(rememberScrollState())   // ★ 縦スクロールを有効化
                .padding(16.dp),                         // パディングは最後でもOK
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ========== Manage Apps ==========
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Manage Apps",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        "Add / remove apps to monitor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onManageApps, // ← 親から渡された遷移コールバックを叩く
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            "Open App List",
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // ========== Alert セクション ==========
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Reminder",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // ON/OFF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Alert", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Notify unlaunched apps before reset",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = tmpEnabled, onCheckedChange = { tmpEnabled = it })
                    }

                    // 時間入力
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Notification Time", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Alert when within this time before reset",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = tmpHoursText,
                            onValueChange = { s -> tmpHoursText = s.filter { it.isDigit() }.take(3) },
                            label = { Text("hours") },  // ← 左上ラベルに変更
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                            leadingIcon = { Text("⏱") },
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }

            // ========== Privacy Policy ==========
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://journeygirl40.github.io/habitlauncher/") },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(), // ← 背景なし
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary   // ← 文字色に合わせて
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Policy Page",
                            color = MaterialTheme.colorScheme.primary, // ← テキストも塗りつぶし色をやめる
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }


            // ========== Music Links ==========
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "JourneyGirl Music",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // ▼ ここからグリッド配置（Row 2本は削除）
                    val musicButtons = listOf(
                        Triple("Spotify",
                            "https://open.spotify.com/intl-ja/artist/3PqlK4aFXeyyU1tWeJsCQw",
                            Color(0xFF1DB954) // Spotify
                        ),
                        Triple("Apple Music",
                            "https://music.apple.com/jp/artist/journeygirl/1820600004",
                            Color(0xFFFA2D48) // Apple
                        ),
                        Triple("Amazon Music",
                            "https://www.amazon.co.jp/music/player/browse/tracks/artist/B0FD7MFV84/popular-songs",
                            Color(0xFFC0C0C0) // Amazon
                        ),
                        Triple("LINE Music",
                            "https://music.line.me/webapp/artist/mi00000000280cfdb6",
                            Color(0xFF00C300) // LINE
                        )
                    )

                    val rows = musicButtons.chunked(2)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { (label, url, bg) ->
                                    Button(
                                        onClick = { uriHandler.openUri(url) },
                                        colors = ButtonDefaults.buttonColors(containerColor = bg),
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier
                                            .weight(1f)         // ← 2列均等
                                            .height(44.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (label == "Amazon Music") Color.Black else Color.White
                                        )
                                    }
                                }
                                // 片方だけの行の埋め草
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    // ▲ グリッドここまで
                }
            }

        }
    }
}


