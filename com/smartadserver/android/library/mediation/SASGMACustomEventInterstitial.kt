package com.smartadserver.android.library.mediation

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.mediation.MediationAdRequest
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitial
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.model.SASAdPlacement
import com.smartadserver.android.library.ui.SASInterstitialManager
import com.smartadserver.android.library.ui.SASInterstitialManager.InterstitialListener
import com.smartadserver.android.library.util.SASUtil

/**
 * Class that handles an adMob mediation interstitial ad call to Smart AdServer SDK.
 */
class SASGMACustomEventInterstitial constructor() : CustomEventInterstitial {
    // Smart interstitial manager that will handle the mediation ad call
    private var interstitialManager: SASInterstitialManager? = null

    /**
     * Implementation of CustomEventInterstitial interface.
     * Delegates the interstitial ad call to Smart AdServer SDK
     */
    override fun requestInterstitialAd(context: Context,
                                              customEventInterstitialListener: CustomEventInterstitialListener,
                                              s: String?,
                                              mediationAdRequest: MediationAdRequest,
                                              bundle: Bundle?) {
        Log.d("CustomEventInterstitial", "requestInterstitialAd for SASGMACustomEventInterstitial")

        // get the smart placement object
        val placementString = s ?: ""

        // Configure the Smart Display SDK and retrieve the ad placement.
        val adPlacement: SASAdPlacement? = SASGMACustomEventUtil.configureSDKAndGetAdPlacement(context, placementString, bundle)
        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventInterstitialListener.onAdFailedToLoad(AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Invalid Smart placement IDs. Please check server parameters string", AdError.UNDEFINED_DOMAIN))
            return
        }
        if (interstitialManager != null) {
            // Quit if there is already an interstitial being handled.
            return
        }

        // instantiate a Smart interstitial manager
        interstitialManager = SASInterstitialManager(context, adPlacement)

        // Set a listener on this manager
        interstitialManager?.interstitialListener = object : InterstitialListener {
            // get a main thread Handler to execute code on this thread
            var handler: Handler = SASUtil.getMainLooperHandler()

            override fun onInterstitialAdLoaded(sasInterstitialManager: SASInterstitialManager, sasAdElement: SASAdElement) {
                // Smart interstitial ad was successfully loaded
                handler.post { customEventInterstitialListener.onAdLoaded() }
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
                    customEventInterstitialListener.onAdFailedToLoad(AdError(errorCode, errorMessage, AdError.UNDEFINED_DOMAIN))
                }
            }

            override fun onInterstitialAdShown(sasInterstitialManager: SASInterstitialManager) {
                // Smart interstitial ad was displayed (full screen)
                handler.post { customEventInterstitialListener.onAdOpened() }
            }

            override fun onInterstitialAdFailedToShow(sasInterstitialManager: SASInterstitialManager, e: Exception) {
                // no dfp counterpart to call
            }

            override fun onInterstitialAdClicked(sasInterstitialManager: SASInterstitialManager) {
                // Smart interstitial ad was clicked
                handler.post { customEventInterstitialListener.onAdClicked() }
            }

            override fun onInterstitialAdDismissed(sasInterstitialManager: SASInterstitialManager) {
                // Smart interstitial ad was closed
                handler.post { customEventInterstitialListener.onAdClosed() }
            }

            override fun onInterstitialAdVideoEvent(sasInterstitialManager: SASInterstitialManager, i: Int) {
                // nothing to do here
            }
        }

        // Now request ad on this SASInterstitialManager
        interstitialManager?.loadAd()
    }

    /**
     * Implementation of CustomEventInterstitial interface method.
     * Displays the previously interstitial ad loaded by the [SASInterstitialManager], if any
     */
    override fun showInterstitial() {
        interstitialManager?.run {
            if (isShowable)(
                    show()
            )
        }
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to SASInterstitialView
     */
    @Synchronized
    override fun onDestroy() {
        interstitialManager?.onDestroy()
        interstitialManager = null
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onPause() call to SASInterstitialView
     */
    override fun onPause() {
        // not supported by SASInterstitialView
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onResume() call to SASInterstitialView
     */
    override fun onResume() {
        // not supported by SASInterstitialView
    }
}