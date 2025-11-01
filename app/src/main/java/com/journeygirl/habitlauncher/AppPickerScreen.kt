package com.journeygirl.habitlauncher

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toBitmap
import android.app.Activity
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity          // ★ これを追加
    val scope = rememberCoroutineScope()

    // ★ あなたのインタースティシャル広告ID（まずはテストIDで確認）
    val interstitialUnitId = remember {
        "ca-app-pub-3334691626809528/9792263564"   // ← 本番
//        "ca-app-pub-3940256099942544/1033173712"   // ← テストID。表示OK後に本番IDへ
    }
    var interstitialReady by remember { mutableStateOf(false) }

    var allApps by remember { mutableStateOf(listOf<LaunchableApp>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }                   // ← 検索文字列

    LaunchedEffect(Unit) {
        allApps = loadLaunchableApps(ctx)
        selected = selectedPackagesFlow(ctx).first()
        // ★ 画面表示時にあらかじめ読み込み
        InterstitialAdHelper.load(ctx, interstitialUnitId, UnityIds.INTERSTITIAL_PLACEMENT) {
            interstitialReady = true
        }
    }

    // 入力に応じてリアルタイムでフィルタ（ラベル or パッケージ名）
    val filteredApps by remember(allApps, query) {
        val q = query.trim().lowercase()
        mutableStateOf(
            if (q.isEmpty()) allApps
            else allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Apps") },
                actions = {
                    // ▼ キャンセルボタンを追加
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        // ★ Unit を返すラムダにする（launch の戻り Job を外へ返さない）
                        val afterAd: () -> Unit = {
                            scope.launch {
                                saveSelectedPackages(ctx, selected)
                                nav.popBackStack("home", false)
                            }
                        }

                        if (activity != null) {
                            InterstitialAdHelper.show(activity, onFinished = afterAd)
                        } else {
                            afterAd()
                        }

                        // ★ 次回表示に備えても Unity placement を渡す（フォールバック有効化）
                        InterstitialAdHelper.load(ctx, interstitialUnitId, UnityIds.INTERSTITIAL_PLACEMENT)
                    }) {
                        Text("Save")
                    }
                }
            )
        },
        bottomBar = {
            // ✅ 広告を常時下部に
            AdBannerView()
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 🔎 検索ボックス
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true,
                label = { Text("Search") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { query = "" }) { Text("クリア") }
                    }
                }
            )

            // 絞り込み結果リスト
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    AppRowWithIcon(
                        label = app.label,
                        icon = app.icon,
                        checked = checked,
                        onChange = { isCheck ->
                            selected = if (isCheck) selected + app.packageName else selected - app.packageName
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRowWithIcon(
    label: String,
    icon: Drawable?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(onClick = { onChange(!checked) }) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(
                        bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            Checkbox(checked = checked, onCheckedChange = onChange)
        }
    }
}
