package com.dzboot.ovpn.helpers

import android.app.Activity
import android.app.Application
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.viewbinding.ViewBinding
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.base.BaseActivity
import com.dzboot.ovpn.base.BaseMainActivity
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadCallback
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdLoadCallback
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.MobileAds
import timber.log.Timber

class AdsManager private constructor() : DefaultLifecycleObserver {

	private enum class AdType {
		BANNER,
		INTERSTITIAL,
		REWARDED
	}

	private var isShowingAd = false
	private var showAds = false
	private var interstitialAd: InterstitialAd? = null
	private var rewardedAd: RewardedAd? = null
	private var bannerAd: BannerAdView? = null

	private fun useLocalAds() = BuildConfig.DEBUG && FORCE_TEST_ADS_WHEN_DEBUG

	private fun loadBannerAd(activity: BaseMainActivity<out ViewBinding>, bannerAdLayout: FrameLayout) {
		if (DISABLE_ADS || !showAds) return

		bannerAd = BannerAdView(activity)
		bannerAdLayout.removeAllViews()
		bannerAdLayout.addView(bannerAd)
		bannerAd?.adUnitId = if (useLocalAds()) BuildConfig.YANDEX_BANNER_KEY else PrefsHelper.getYandexBannerId()

		bannerAd?.setAdListener(object : com.yandex.mobile.ads.banner.BannerAdListener() {
			override fun onAdLoaded() {
				Timber.d("BannerAd loaded successfully")
			}

			override fun onAdFailedToLoad(adError: AdError) {
				Timber.w("BannerAd failed to load: ${adError.message}")
			}
		})

		bannerAd?.loadAd(AdRequest.Builder().build())
	}

	private fun loadInterstitialAd(activity: Activity) {
		InterstitialAd.load(activity, if (useLocalAds()) BuildConfig.YANDEX_INTERSTITIAL_KEY else PrefsHelper.getYandexInterId(),
			AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
				override fun onAdLoaded(ad: InterstitialAd) {
					Timber.d("InterstitialAd loaded successfully")
					interstitialAd = ad
				}

				override fun onAdFailedToLoad(adError: AdError) {
					Timber.w("InterstitialAd failed to load: ${adError.message}")
				}
			})
	}

	private fun loadRewardedAd(activity: Activity) {
		RewardedAd.load(activity, if (useLocalAds()) BuildConfig.YANDEX_REWARDED_KEY else PrefsHelper.getYandexRewardedId(),
			AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
				override fun onAdLoaded(ad: RewardedAd) {
					Timber.d("RewardedAd loaded successfully")
					rewardedAd = ad
				}

				override fun onAdFailedToLoad(adError: AdError) {
					Timber.w("RewardedAd failed to load: ${adError.message}")
				}
			})
	}

	fun showInterstitialAd(activity: Activity) {
		if (isShowingAd || interstitialAd == null) return

		interstitialAd?.show(activity)
		isShowingAd = true
		interstitialAd = null // Reset after showing
	}

	fun showRewardedAd(activity: Activity, onReward: () -> Unit) {
		if (rewardedAd == null) {
			Toast.makeText(activity, "Rewarded ad not loaded", Toast.LENGTH_SHORT).show()
			return
		}

		rewardedAd?.show(activity) {
			onReward()
			rewardedAd = null // Reset after showing
			loadRewardedAd(activity) // Load next ad
		}
	}

	fun deliverContent(activity: BaseMainActivity<out ViewBinding>) {
		if (SubscriptionManager.getInstance().isSubscribed) {
			showAds = false
			interstitialAd = null
			rewardedAd = null
			activity.hideNativeAd()
			activity.hidePurchase(true)
			activity.updateServersList()
		} else {
			showAds = true
			loadAds(activity)
			if (SubscriptionManager.getInstance().isFeatureSupported())
				activity.showPurchase()
			else
				activity.hidePurchase(false)
		}
	}

	private fun loadAds(activity: BaseMainActivity<out ViewBinding>) {
		if (DISABLE_ADS) return
		loadBannerAd(activity, activity.findViewById(R.id.bannerAdLayout)) // Убедитесь, что вы добавили этот ID в ваш layout
		loadInterstitialAd(activity)
		loadRewardedAd(activity)
	}

	companion object {
		private const val DISABLE_ADS = !BuildConfig.SHOW_ADS
		private const val FORCE_TEST_ADS_WHEN_DEBUG = true

		@Volatile
		lateinit var instance: AdsManager

		fun init(appContext: Application) {
			MobileAds.initialize(appContext) {
				Timber.d("Yandex Ads initialized")
			}
			instance = AdsManager()
		}
	}
}
