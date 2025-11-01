package com.journeygirl.habitlauncher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import kotlinx.coroutines.flow.map
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.core.view.WindowCompat
import java.time.Duration
import java.time.ZonedDateTime
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ElevatedCard
import com.unity3d.ads.UnityAds
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration


// reset を 0時とみなすため、時刻を reset 分だけマイナスした日の epochDay を使う
fun cycleId(zdt: ZonedDateTime, reset: LocalTime): Long {
    val shift = Duration.ofHours(reset.hour.toLong()).plusMinutes(reset.minute.toLong())
    return zdt.minus(shift).toLocalDate().toEpochDay()
}




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ① コンテンツをシステムバーの下まで広げる（Edge-to-Edge）
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ② ステータスバーを透明に
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // ③ ステータスバーのアイコンを「ダーク（黒寄り）」に
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true   // ← これが肝
        // （必要ならナビゲーションバーも）
        controller.isAppearanceLightNavigationBars = true

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        MobileAds.initialize(this)

        // ★ Unityの初期化（IDは AdBannerView.kt にだけ置いた UnityIds を参照）
        UnityAds.initialize(
            /* activity = */ this,
            /* gameId   = */ UnityIds.GAME_ID,
            /* testMode = */ UnityIds.TEST_MODE
        )
        setContent {
            MaterialTheme {
                val navController = rememberNavController()   // ★ これだけに統一

                NavHost(navController = navController, startDestination = "home") {
                    composable("home")   { HomeScreen(navController) }
                    composable("picker") { AppPickerScreen(navController) }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },     // ← 戻る動作を実装
                            onManageApps = {
                                navController.navigate("picker") {
                                    // ★ Home より上（= settings）を消してから picker を積む
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1) Pickerの結果と起動可能アプリ
    var selected by remember { mutableStateOf(setOf<String>()) }
    var allLaunchables by remember { mutableStateOf(listOf<LaunchableApp>()) }
    LaunchedEffect(Unit) {
        selected = selectedPackagesFlow(ctx).first()
        allLaunchables = loadLaunchableApps(ctx)
    }

    // 2) タイマー（現在時刻）
    val vm = remember { HabitLauncherViewModel() }
    val now by vm.now.collectAsState()

    // 3) 今日の起動済み集合 と 個別リセット時刻マップ
    val resetTimeMap by resetTimeMapFlow(ctx).collectAsState(initial = emptyMap())
    val lastCycleMap by lastCycleIdMapFlow(ctx).collectAsState(initial = emptyMap())

    // 4) 表示データ（未起動→残り時間短い順、Doneは一番下）
    val displayApps = remember(selected, allLaunchables, resetTimeMap, lastCycleMap, now) {
        allLaunchables
            .filter { it.packageName in selected }
            .map { la ->
                val rt = resetTimeMap[la.packageName] ?: LocalTime.MIDNIGHT
                val currentCid = cycleId(now, rt)
                val launched = lastCycleMap[la.packageName] == currentCid
                TrackedApp(
                    name = la.label,
                    packageName = la.packageName,
                    resetTime = rt,
                    launchedFlagToday = launched,
                    icon = la.icon
                )
            }
            .sortedWith(
                compareBy<TrackedApp> { it.launchedFlagToday } // 未起動(false)が先
                    .thenBy { t ->
                        if (!t.launchedFlagToday)
                            remainingUntilReset(t.resetTime, now).seconds
                        else
                            Long.MAX_VALUE
                    }
                    // ★ Doneの中だけリセット時間の昇順（早い順）
                    .thenBy { t ->
                        if (t.launchedFlagToday) t.resetTime else LocalTime.MAX
                    }
            )

    }
    // 5) 設定対象の選択状態（カード押下でセット）
    var settingsTarget by remember { mutableStateOf<TrackedApp?>(null) }
    val notifyLeadMinutes by alertThresholdMinutesFlow(ctx)
        .map { (it as? Number)?.toLong() ?: 60L }
        .collectAsState(initial = 60L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Dashboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "App Tracker",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // ✅ 広告を常時下部に
            AdBannerView()
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)      // Scaffoldの余白を反映
                .fillMaxSize(),
            contentPadding = PaddingValues(   // 画面内の余白
                start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayApps, key = { it.packageName }) { app ->
                AppItemCard(
                    app = app,
                    now = now,
                    // ★ アイコンを押したら起動＋起動済みに反映
                    onIconLaunchClick = { clicked ->
                        val intent = ctx.packageManager.getLaunchIntentForPackage(clicked.packageName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                            // 起動済みとして保存 → 画面の Done 表示が即反映される
                            scope.launch {
                                val cid = cycleId(now, clicked.resetTime)
                                saveLaunchedForCurrentCycle(ctx, clicked.packageName, cid)
                            }                        } else {
                            Toast.makeText(ctx, "起動できません: ${clicked.name}", Toast.LENGTH_SHORT).show()
                        }
                    },

                    // ★ カードを押したら設定ダイアログを開く
                    onCardSettingsClick = { target ->
                        settingsTarget = target
                    },
                    notifyLeadMinutes = notifyLeadMinutes // ★ 追加
                )
            }
            // 必要なら一番下に余白を追加
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // 6) 設定ダイアログ（カードタップで開く）
    val target = settingsTarget
    if (target != null) {
        AppSettingSheet(
            app = target,
            onDismiss = { settingsTarget = null },
            onSave = { launched, newTime ->
                scope.launch {
                    val resetChanged = newTime != target.resetTime

                    // 1) 先に新しいリセット時刻を保存
                    setResetTime(ctx, target.packageName, newTime)

                    if (launched) {
                        // 2-A) 起動済みにした
                        val cid = cycleId(now, newTime)
                        saveLaunchedForCurrentCycle(ctx, target.packageName, cid)

                        if (resetChanged) {
                            // ★ 追加：このアプリの今サイクル通知済みをクリア
                            setLastNotifiedCid(ctx, target.packageName, -1)

                            // ★ その日は通知不要、翌日から新しい時刻で再スケ
                            AlarmScheduler.scheduleFromNextCycle(
                                ctx, target.packageName, minDelaySeconds = 10
                            )
                        } else {
                            // 既存どおり当日の通知は要らない
                            AlarmScheduler.cancelForPackage(ctx, target.packageName)
                        }
                    } else {
                        // 2-B) doneを解除（未起動化）
                        clearLaunched(ctx, target.packageName)
                        // ★ 追加
                        setLastNotifiedCid(ctx, target.packageName, -1)

                        // ★ 直ちに（最低10秒先で）新しい時刻に合わせてスケ
                        AlarmScheduler.scheduleExactForPackage(
                            ctx, target.packageName, minDelaySeconds = 10
                        )
                    }
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingSheet(
    app: TrackedApp,
    onDismiss: () -> Unit,
    onSave: (launched: Boolean, newTime: LocalTime) -> Unit
) {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf(app.resetTime) }
    var tempLaunched by remember { mutableStateOf(app.launchedFlagToday) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ヘッダー行：アプリ名 + 保存ボタン
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // アイコン＋名前
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f) // ← 名前部分を可変にしてボタンを右端へ
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dashboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Saveボタン（右端）
                TextButton(onClick = { onSave(tempLaunched, time); onDismiss() }) {
                    Text("Save")
                }
            }

            // ヘッダー：アイコン + アプリ名
            Row(verticalAlignment = Alignment.CenterVertically) {
                // アイコン（なければダッシュボードアイコン）
                Column {
                    Text(
                        text = if (tempLaunched) "起動済み / Done today" else "未起動 / Not launched",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }

            // ステータス切替：2チップ（未起動 / 起動済み）
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center, // ← 中央寄せ
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !tempLaunched,
                    onClick = { tempLaunched = false },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("未起動", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Not launched",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = if (!tempLaunched)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                Spacer(Modifier.width(16.dp))

                FilterChip(
                    selected = tempLaunched,
                    onClick = { tempLaunched = true },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("起動済み", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Launched",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (tempLaunched)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }


            // リセット時刻：大きめ表示 + 変更ボタン
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Reset Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "%02d:%02d".format(time.hour, time.minute),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    TextButton(
                        onClick = {
                            android.app.TimePickerDialog(
                                ctx, { _, h, m -> time = LocalTime.of(h, m) },
                                time.hour, time.minute, true
                            ).show()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Edit Reset Time")
                    }
                }
            }

            // アクション：保存・閉じる
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("キャンセル / Cancel") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}


