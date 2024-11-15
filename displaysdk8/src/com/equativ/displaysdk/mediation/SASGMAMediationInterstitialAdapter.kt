package com.equativ.displaysdk.mediation

import android.app.Activity
import android.content.Context
import android.util.Log
import com.equativ.displaysdk.ad.interstitial.SASInterstitialManager
import com.equativ.displaysdk.exception.SASException
import com.equativ.displaysdk.model.SASAdInfo
import com.equativ.displaysdk.model.SASAdStatus
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Class that handles Google Mobile Ads mediation interstitial ad calls to Equativ AdServer SDK.
 */
class SASGMAMediationInterstitialAdapter : Adapter(), MediationInterstitialAd {

    // Equativ interstitial manager that will handle the mediation ad call
    private var sasInterstitialManager: SASInterstitialManager? = null

    // Callback instance from Google SDK in case of interstitial ad loading success
    var mediationInterstitialAdCallback: MediationInterstitialAdCallback? = null

    override fun getVersionInfo(): VersionInfo {
        return SASGMAUtils.versionInfo
    }

    override fun getSDKVersionInfo(): VersionInfo {
        return SASGMAUtils.SDKVersionInfo
    }

    override fun initialize(
        context: Context,
        initializationCompleteCallback: InitializationCompleteCallback,
        list: List<MediationConfiguration>
    ) {
        if (context is Activity) {
            if (activityWeakReference == null) {
                activityWeakReference = WeakReference<Activity>(context)

                // Nothing more to do here, the Equativ Display SDK does not require initialization at this stage
                initializationCompleteCallback.onInitializationSucceeded()
            }
        } else {
            initializationCompleteCallback.onInitializationFailed("Can not initialize SASGMAMediationInterstitialAdapter as passed Context is not an Activity")
        }
    }

    override fun loadInterstitialAd(
        mediationAdConfiguration: MediationInterstitialAdConfiguration,
        mediationAdLoadCallback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>
    ) {
        Log.d(
            "SASGMAMediationInterstitialAdapter",
            "loadInterstitialAd for SASGMAMediationInterstitialAdapter"
        )

        // Get the Equativ placement string parameter
        val equativPlacementString = mediationAdConfiguration.serverParameters.getString("parameter") ?: ""

        // Configure the Equativ Display SDK and retrieve the ad placement.
        activityWeakReference?.get()?.let { activity ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(
                activity,
                equativPlacementString,
                mediationAdConfiguration.mediationExtras
            )

            adPlacement?.let {

                // clean up any previous SASInterstitialManager
                sasInterstitialManager?.onDestroy()

                // Instantiate the SASInterstitialManager
                sasInterstitialManager = SASInterstitialManager(activity, adPlacement).also { interstitialManager ->

                    // Set a listener on this manager
                    interstitialManager.interstitialManagerListener = object :
                        SASInterstitialManager.InterstitialManagerListener {
                        // get a main thread Handler to execute code on this thread

                        override fun onInterstitialAdLoaded(adInfo: SASAdInfo) {
                            // Equativ interstitial ad was successfully loaded
                            CoroutineScope(Dispatchers.Main).launch {
                                mediationInterstitialAdCallback = mediationAdLoadCallback.onSuccess(this@SASGMAMediationInterstitialAdapter)
                            }
                        }

                        override fun onInterstitialAdFailedToLoad(exception: SASException) {
                            // Equativ interstitial ad failed to load
                            CoroutineScope(Dispatchers.Main).launch {
                                var errorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR
                                var errorMessage = exception.message ?: ""

                                when (exception.type) {
                                    SASException.Type.NO_AD -> {
                                        // no ad to deliver
                                        errorCode = AdRequest.ERROR_CODE_NO_FILL
                                        errorMessage = "No ad to deliver"
                                    }

                                    SASException.Type.TIMEOUT -> {
                                        // ad request timeout translates to admob network error
                                        errorCode = AdRequest.ERROR_CODE_NETWORK_ERROR
                                        errorMessage = "Timeout while waiting ad call response"
                                    }

                                    else -> {
                                        // keep message and code init values
                                    }
                                }

                                mediationAdLoadCallback.onFailure(
                                    AdError(errorCode, errorMessage, AdError.UNDEFINED_DOMAIN)
                                )
                            }
                        }

                        override fun onInterstitialAdShown() {
                            // Equativ interstitial ad was displayed (full screen)
                            CoroutineScope(Dispatchers.Main).launch { mediationInterstitialAdCallback?.onAdOpened() }
                        }

                        override fun onInterstitialAdFailedToShow(exception: SASException) {
                            // Equativ interstitial ad failed to show
                            CoroutineScope(Dispatchers.Main).launch {
                                mediationInterstitialAdCallback?.onAdFailedToShow(
                                    AdError(
                                        AdRequest.ERROR_CODE_INTERNAL_ERROR,
                                        exception.message ?: "",
                                        AdError.UNDEFINED_DOMAIN
                                    )
                                )
                            }
                        }

                        override fun onInterstitialAdClicked() {
                            // Equativ interstitial ad was clicked
                            CoroutineScope(Dispatchers.Main).launch { mediationInterstitialAdCallback?.reportAdClicked() }
                        }

                        override fun onInterstitialAdClosed() {
                            // Equativ interstitial ad was closed
                            CoroutineScope(Dispatchers.Main).launch { mediationInterstitialAdCallback?.onAdClosed() }
                        }
                    }

                    // Now request ad on this SASInterstitialManager
                    interstitialManager.loadAd()
                }
            } ?: run {
                // incorrect Equativ placement : exit in error
                mediationAdLoadCallback.onFailure(
                    AdError(
                        AdRequest.ERROR_CODE_INVALID_REQUEST,
                        "Invalid Equativ placement IDs. Please check server parameters string",
                        AdError.UNDEFINED_DOMAIN
                    )
                )

            }
        } ?: run {
            mediationAdLoadCallback.onFailure(
                AdError(
                    AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Context is null", AdError.UNDEFINED_DOMAIN
                )
            )
        }
    }

    override fun showAd(context: Context) {
        sasInterstitialManager?.run {
            if (getAdStatus() == SASAdStatus.READY) {
                show()
            }
        }
    }

    companion object {
        private var activityWeakReference: WeakReference<Activity>? = null
    }

}