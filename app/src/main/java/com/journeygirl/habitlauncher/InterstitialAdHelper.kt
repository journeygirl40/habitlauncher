package com.journeygirl.habitlauncher

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.unity3d.ads.UnityAds
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.IUnityAdsLoadListener

object InterstitialAdHelper {
    private var interstitialAd: InterstitialAd? = null
    private const val TAG = "InterstitialAd"
    // ★ 追加：Unity フォールバック管理
    private var unityPlacementForFallback: String? = null
    private var useUnityFallback: Boolean = false
    private var unityReady: Boolean = false

    // ★ 引数を拡張：Unity の placement を任意指定可
    fun load(context: Context, adUnitId: String, unityPlacementId: String? = null, onLoaded: (() -> Unit)? = null) {
        unityPlacementForFallback = unityPlacementId
        useUnityFallback = false
        unityReady = false

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    useUnityFallback = false
                    onLoaded?.invoke()
                    Log.d(TAG, "onAdLoaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    // ★ フォールバックを有効化
                    useUnityFallback = (unityPlacementId != null)
                    Log.e(TAG, "onAdFailedToLoad: ${error.message}  -> fallbackToUnity=$useUnityFallback")
                    // ★ ここで Unity をプリロードしておく（重要）
                    if (useUnityFallback) {
                        UnityAds.load(unityPlacementId!!, object : IUnityAdsLoadListener {
                            override fun onUnityAdsAdLoaded(placementId: String) {
                                unityReady = true
                                Log.d(TAG, "Unity interstitial loaded: $placementId")
                            }
                            override fun onUnityAdsFailedToLoad(
                                placementId: String,
                                error: UnityAds.UnityAdsLoadError,
                                message: String
                            ) {
                                unityReady = false
                                Log.e(TAG, "Unity interstitial load failed: $error $message")
                            }
                        })
                    }
                }
            }
        )
    }

    fun show(activity: Activity, onFinished: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onFinished()
                }
                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    interstitialAd = null

                    val unityPlacement = unityPlacementForFallback
                    if (useUnityFallback && unityPlacement != null) {
                        if (unityReady) {
                            // すでにロード済みならそのまま表示
                            showUnity(activity, unityPlacement, onFinished)
                        } else {
                            // 未ロードならここでロードしてから表示
                            UnityAds.load(unityPlacement, object : IUnityAdsLoadListener {
                                override fun onUnityAdsAdLoaded(placementId: String) {
                                    unityReady = true
                                    showUnity(activity, placementId, onFinished)
                                }
                                override fun onUnityAdsFailedToLoad(
                                    placementId: String,
                                    error: UnityAds.UnityAdsLoadError,
                                    message: String
                                ) {
                                    unityReady = false
                                    Log.e(TAG, "Unity load failed (after AdMob show fail): $error $message")
                                    onFinished()
                                }
                            })
                        }
                    } else {
                        onFinished()
                    }
                }


            }
            ad.show(activity)
            return
        }

        // ★ AdMob 未ロード時：Unity フォールバック
        val unityPlacement = unityPlacementForFallback
        if (useUnityFallback && unityPlacement != null) {
            if (unityReady) {
                showUnity(activity, unityPlacement, onFinished)
            } else {
                UnityAds.load(unityPlacement, object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        unityReady = true
                        showUnity(activity, placementId, onFinished)
                    }
                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        unityReady = false
                        Log.e(TAG, "Unity load failed: $error $message")
                        onFinished()
                    }
                })
            }
        } else {
            onFinished()
        }
    }
    // --- Unity interstitial を表示する共通ラッパー ---
    private fun showUnity(
        activity: Activity,
        placementId: String,
        onFinished: () -> Unit
    ) {
        // 既に load 済み前提で呼ばれる想定だが、万一に備えて Show だけでハンドリング
        UnityAds.show(
            activity,
            placementId,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowComplete(pId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    onFinished()
                }
                override fun onUnityAdsShowFailure(
                    pId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    Log.e(TAG, "Unity show failed: $error $message")
                    onFinished()
                }
                override fun onUnityAdsShowStart(pId: String) { /* no-op */ }
                override fun onUnityAdsShowClick(pId: String) { /* no-op */ }
            }
        )
    }

}

