package com.mirrornaturallook

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(private val context: Context) {

    companion object {
        // ⚠️ Replace TEST IDs with real AdMob unit IDs before publishing
        const val BANNER_AD_UNIT_ID       = "ca-app-pub-3940256099942544/6300978111"   // TEST
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"   // TEST
        const val REWARDED_AD_UNIT_ID     = "ca-app-pub-3940256099942544/5224354917"   // TEST
    }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var bannerAdView: AdView? = null

    fun loadBanner(container: FrameLayout) {
        bannerAdView = AdView(context).apply {
            adUnitId = BANNER_AD_UNIT_ID
            setAdSize(AdSize.BANNER)
            adListener = object : AdListener() {
                override fun onAdLoaded() { visibility = View.VISIBLE }
                override fun onAdFailedToLoad(e: LoadAdError) { visibility = View.GONE }
            }
            loadAd(AdRequest.Builder().build())
        }
        container.removeAllViews()
        container.addView(bannerAdView)
    }

    fun pauseBanner()  { bannerAdView?.pause() }
    fun resumeBanner() { bannerAdView?.resume() }
    fun destroyBanner() { bannerAdView?.destroy(); bannerAdView = null }

    fun loadInterstitial() {
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitialAd = null }
            })
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        val ad = interstitialAd ?: run { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitialAd = null; loadInterstitial(); onDismissed() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { interstitialAd = null; onDismissed() }
        }
        ad.show(activity)
    }

    fun loadRewarded() {
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
            })
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit = {}) {
        val ad = rewardedAd ?: run { onDismissed(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewardedAd = null; loadRewarded(); onDismissed() }
        }
        ad.show(activity) { onRewarded() }
    }
}
