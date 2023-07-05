package com.smartadserver.android.library.mediation

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.mediation.MediationAdRequest
import com.google.android.gms.ads.mediation.customevent.CustomEventBanner
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.ui.SASBannerView
import com.smartadserver.android.library.ui.SASBannerView.BannerListener
import com.smartadserver.android.library.util.SASUtil

/**
 * Class that handles Google Mobile Ads mediation banner ad calls to Smart AdServer SDK.
 *
 * @deprecated replaced by com.smartadserver.android.library.mediation.SASGMAMediationBannerAdapter
 */
@Deprecated(message = "replaced by com.smartadserver.android.library.mediation.SASGMAMediationBannerAdapter")
class SASGMACustomEventBanner : CustomEventBanner {
    // Smart banner view that will handle the mediation ad call
    private var sasBannerView: SASBannerView? = null

    /**
     * Implementation of CustomEventBanner interface.
     * Delegates the banner ad call to Smart AdServer SDK
     */
    override fun requestBannerAd(context: Context,
                                 customEventBannerListener: CustomEventBannerListener,
                                 s: String?,
                                 adSize: AdSize,
                                 mediationAdRequest: MediationAdRequest,
                                 bundle: Bundle?) {
        Log.d("CustomEventBanner", "requestBannerAd for SASGMACustomEventBanner")

        // get the smart placement object
        val placementString = s ?: ""

        // Configure the Smart Display SDK and retrieve the ad placement.
        val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(context, placementString, bundle)

        // test if the ad placement is valid
        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventBannerListener.onAdFailedToLoad(AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Invalid Smart placement IDs. Please check server parameters string", AdError.UNDEFINED_DOMAIN))
            return
        }
        if (sasBannerView != null) {
            // Quit if there is already a banner being handled.
            return
        }

        // Instantiate the SASBannerView
        sasBannerView = object : SASBannerView(context) {
            /**
             * Overriden to force banner size to received admob size if not expanded
             * @param params
             */
            override fun setLayoutParams(params: ViewGroup.LayoutParams) {
                if (!this.isExpanded) {
                    params.height = adSize.getHeightInPixels(context)
                    params.width = adSize.getWidthInPixels(context)
                }
                super.setLayoutParams(params)
            }
        }

        // Set a listener on the SASBannerView
        sasBannerView?.bannerListener = object : BannerListener {
            // get a Handler on the main thread to execute code on this thread
            var handler = SASUtil.getMainLooperHandler()
            override fun onBannerAdLoaded(bannerView: SASBannerView, sasAdElement: SASAdElement) {
                // Smart banner ad was successfully loaded
                handler.post { customEventBannerListener.onAdLoaded(bannerView) }
            }

            override fun onBannerAdFailedToLoad(sasBannerView: SASBannerView, e: Exception) {
                // Smart banner ad failed to load
                handler.post {
                    var errorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR
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
                    customEventBannerListener.onAdFailedToLoad(AdError(errorCode, errorMessage, AdError.UNDEFINED_DOMAIN))
                }
            }

            override fun onBannerAdClicked(sasBannerView: SASBannerView) {
                // Smart banner ad was clicked
                handler.post { customEventBannerListener.onAdClicked() }
            }

            override fun onBannerAdExpanded(sasBannerView: SASBannerView) {
                // Smart banner ad was displayed full screen
                handler.post { customEventBannerListener.onAdOpened() }
            }

            override fun onBannerAdCollapsed(sasBannerView: SASBannerView) {
                // Smart banner ad was restored to its default state
                handler.post { customEventBannerListener.onAdClosed() }
            }

            override fun onBannerAdResized(sasBannerView: SASBannerView) {
                // nothing to do here
            }

            override fun onBannerAdClosed(sasBannerView: SASBannerView) {
                // nothing to do here
            }

            override fun onBannerAdVideoEvent(sasBannerView: SASBannerView, i: Int) {
                // nothing to do here
            }
        }

        // Now request ad for this SASBannerView
        sasBannerView?.loadAd(adPlacement)
    }

    /**
     * Implementation of CustomEventBanner interface.
     * Forwards the onDestroy() call to SASBannerView
     */
    @Synchronized
    override fun onDestroy() {
        sasBannerView?.onDestroy()
        sasBannerView = null
    }

    /**
     * Implementation of CustomEventBanner interface.
     * Forwards the onPause() call to SASBannerView
     */
    override fun onPause() {
        // not supported by SASBannerView
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onResume() call to SASBannerView
     */
    override fun onResume() {
        // not supported by SASBannerView
    }
}