package com.smartadserver.android.library.mediation

import android.content.Context
import android.os.Handler
import android.util.Log
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
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.ui.SASInterstitialManager
import com.smartadserver.android.library.util.SASUtil
import java.lang.ref.WeakReference

/**
 * Class that handles Google Mobile Ads mediation interstitial ad calls to Smart AdServer SDK.
 */
class SASGMAMediationInterstitialAdapter : Adapter(), MediationInterstitialAd {

    // Smart  interstitial manager that will handle the mediation ad call
    private var sasInterstitialManager: SASInterstitialManager? = null

    // Callback instance from Google SDK in case of interstitial ad loading success
    var mediationInterstitialAdCallback: MediationInterstitialAdCallback? = null

    override fun getVersionInfo(): VersionInfo {
        return SASGMAUtils.versionInfo
    }

    override fun getSDKVersionInfo(): VersionInfo {
        return SASGMAUtils.SDKVersionInfo
    }

    override fun initialize(context: Context,
                            initializationCompleteCallback: InitializationCompleteCallback,
                            list: List<MediationConfiguration>) {
        if (applicationContextWeakReference == null) {
            applicationContextWeakReference = WeakReference<Context>(context.applicationContext)

            // Nothing more to do here, Smart Interstitial does not require initialization
            initializationCompleteCallback.onInitializationSucceeded()
        }
    }

    override fun loadInterstitialAd(
        mediationAdConfiguration: MediationInterstitialAdConfiguration,
        mediationAdLoadCallback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>
    ) {
        Log.d("SASGMAMediationInterstitialAdapter", "loadInterstitialAd for SASGMAMediationInterstitialAdapter")

        // Get the Smart placement string parameter
        val smartPlacementString = mediationAdConfiguration.serverParameters.getString("parameter") ?: ""

        // Configure the Smart Display SDK and retrieve the ad placement.
        applicationContextWeakReference?.get()?.let { context ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(
                context,
                smartPlacementString, mediationAdConfiguration.mediationExtras
            )

            adPlacement?.let {

                // clean up any previous SASInterstitialManager
                sasInterstitialManager?.onDestroy()

                // Instantiate the SASInterstitialManager
                sasInterstitialManager = SASInterstitialManager(context, adPlacement).apply {

                    // Set a listener on this manager
                    interstitialListener = object :
                        SASInterstitialManager.InterstitialListener {
                        // get a main thread Handler to execute code on this thread
                        var handler: Handler = SASUtil.getMainLooperHandler()

                        override fun onInterstitialAdLoaded(sasInterstitialManager: SASInterstitialManager, sasAdElement: SASAdElement) {
                            // Smart interstitial ad was successfully loaded
                            handler.post {
                                mediationInterstitialAdCallback = mediationAdLoadCallback.onSuccess(this@SASGMAMediationInterstitialAdapter)
                            }
                        }

                        override fun onInterstitialAdFailedToLoad(sasInterstitialManager: SASInterstitialManager, e: Exception) {
                            // Smart interstitial ad failed to load
                            handler.post {
                                var errorCode: Int = AdRequest.ERROR_CODE_INTERNAL_ERROR
                                var errorMessage = e.message ?: ""
                                if (e is SASNoAdToDeliverException) {
                                    // no ad to deliver
                                    errorCode = AdRequest.ERROR_CODE_NO_FILL
                                    errorMessage = "No ad to deliver"
                                } else if (e is SASAdTimeoutException) {
                                    // ad request timeout translates to admob network error
                                    errorCode = AdRequest.ERROR_CODE_NETWORK_ERROR
                                    errorMessage = "Timeout while waiting ad call response"
                                }
                                mediationAdLoadCallback.onFailure(
                                    AdError(
                                        errorCode,
                                        errorMessage,
                                        AdError.UNDEFINED_DOMAIN)
                                )
                            }
                        }

                        override fun onInterstitialAdShown(sasInterstitialManager: SASInterstitialManager) {
                            // Smart interstitial ad was displayed (full screen)
                            handler.post { mediationInterstitialAdCallback?.onAdOpened() }
                        }

                        override fun onInterstitialAdFailedToShow(sasInterstitialManager: SASInterstitialManager, e: Exception) {
                            // Smart interstitial ad failed to show
                            handler.post {
                                mediationInterstitialAdCallback?.onAdFailedToShow(
                                    AdError(
                                        AdRequest.ERROR_CODE_INTERNAL_ERROR,
                                        e.message ?: "",
                                        AdError.UNDEFINED_DOMAIN
                                    )
                                )
                            }
                        }

                        override fun onInterstitialAdClicked(sasInterstitialManager: SASInterstitialManager) {
                            // Smart interstitial ad was clicked
                            handler.post { mediationInterstitialAdCallback?.reportAdClicked() }
                        }

                        override fun onInterstitialAdDismissed(sasInterstitialManager: SASInterstitialManager) {
                            // Smart interstitial ad was closed
                            handler.post { mediationInterstitialAdCallback?.onAdClosed() }
                        }

                        override fun onInterstitialAdVideoEvent(sasInterstitialManager: SASInterstitialManager, i: Int) {
                            // nothing to do here
                        }
                    }

                    // Now request ad on this SASInterstitialManager
                    loadAd()
                }
            }?: run {
                // incorrect smart placement : exit in error
                mediationAdLoadCallback.onFailure(
                    AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                        "Invalid Smart placement IDs. Please check server parameters string", AdError.UNDEFINED_DOMAIN))

            }
        } ?: run {
            mediationAdLoadCallback.onFailure(
                AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Context is null", AdError.UNDEFINED_DOMAIN))
        }
    }

    override fun showAd(context: Context) {
        sasInterstitialManager?.run {
            if (isShowable) {
                show()
            }
        }
    }

    companion object {
        private var applicationContextWeakReference: WeakReference<Context>? = null
    }

}