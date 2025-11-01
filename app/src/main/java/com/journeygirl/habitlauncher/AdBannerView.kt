package com.journeygirl.habitlauncher

import android.app.Activity
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/** ← UnityのIDはこの1ファイルにだけ直書きして集約 */
object UnityIds {
    const val GAME_ID = "5976562"              // ★ いただいた新ID
    const val TEST_MODE = false                 // 開発中は true / 本番で false
    const val BANNER_PLACEMENT = "Banner_Android"
    const val INTERSTITIAL_PLACEMENT = "Interstitial_Android"

}
private fun Activity.bannerAdSize(): AdSize {
    // 画面幅に合わせたアンカード適応バナー
    val display = windowManager.defaultDisplay
    val outPoint = android.graphics.Point()
    display.getSize(outPoint)
    val adWidth = (outPoint.x / resources.displayMetrics.density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
}

@Composable
fun AdBannerView(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-3334691626809528/4148769233" // ←本番
//    adUnitId: String = "ca-app-pub-3940256099942544/6300978111" // ←まずテストIDで確認

) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            // ▼ ナビゲーションバーの高さぶんだけ自動で余白を確保
            .padding(WindowInsets.navigationBars.asPaddingValues()),
        factory = { ctx ->
            // 全幅コンテナ（AdMob/Unityを差し替え）
            val container = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
            }

            fun loadUnity() {
                val banner = BannerView(
                    activity,                         // 必ず Activity
                    UnityIds.BANNER_PLACEMENT,
                    UnityBannerSize(320, 50)          // Unityは固定(スマホ)。中央寄せで違和感最小化
                )
                banner.listener = object : BannerView.IListener {
                    override fun onBannerLoaded(view: BannerView) {
                        Log.d("AdBanner", "Unity loaded")
                    }
                    override fun onBannerShown(view: BannerView) {}
                    override fun onBannerFailedToLoad(view: BannerView, errorInfo: BannerErrorInfo) {
                        Log.e("AdBanner", "Unity failed: ${errorInfo.errorMessage}")
                        // house未使用: ここで終了（表示なし）
                    }
                    override fun onBannerClick(view: BannerView) {}
                    override fun onBannerLeftApplication(view: BannerView) {}
                }
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                container.removeAllViews()
                container.addView(banner, params)
                banner.load()
            }

            fun loadAdMob() {
                val adView = AdView(ctx).apply {
                    setAdSize(activity.bannerAdSize())
                    this.adUnitId = adUnitId
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() { Log.d("AdBanner", "AdMob loaded") }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e("AdBanner", "AdMob failed: ${error.code} ${error.message}")
                            loadUnity() // ★ フォールバック
                        }
                    }
                }
                container.removeAllViews()
                container.addView(adView)
                adView.loadAd(AdRequest.Builder().build())
            }

            // AdMob優先 → 失敗時Unity
            loadAdMob()
            container
        }
    )
}
