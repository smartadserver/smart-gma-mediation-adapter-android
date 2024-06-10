package com.equativ.displaysdk.mediation

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.equativ.displaysdk.ad.banner.SASBannerView
import com.equativ.displaysdk.exception.SASException
import com.equativ.displaysdk.model.SASAdInfo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Class that handles Google Mobile Ads mediation banner ad calls to Equativ Display SDK.
 */
class SASGMAMediationBannerAdapter : Adapter(), MediationBannerAd {

    // Equativ banner view that will handle the mediation ad call
    private var sasBannerView: SASBannerView? = null

    // Callback instance from Google SDK in case of banner ad loading success
    var mediationBannerAdCallback: MediationBannerAdCallback? = null
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
        if (applicationContextWeakReference == null) {
            applicationContextWeakReference = WeakReference<Context>(context.applicationContext)

            // Nothing more to do here, Equativ banner does not require initialization
            initializationCompleteCallback.onInitializationSucceeded()
        }
    }

    override fun loadBannerAd(
        mediationAdConfiguration: MediationBannerAdConfiguration,
        mediationAdLoadCallback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>
    ) {
        Log.d("SASGMAMediationBannerAdapter", "loadBannerAd for SASGMAMediationBannerAdapter")

        // Get the Equativ placement string parameter
        val equativPlacementString = mediationAdConfiguration.serverParameters.getString("parameter") ?: ""
        val size: AdSize = mediationAdConfiguration.adSize

        // Configure the Equativ Display SDK and retrieve the ad placement.
        applicationContextWeakReference?.get()?.let { context ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(
                context,
                equativPlacementString,
                mediationAdConfiguration.mediationExtras
            )

            adPlacement?.let {

                // clean up any previous SASBannerView
                sasBannerView?.onDestroy()

                // Instantiate the SASBannerView
                sasBannerView = SASBannerView(context).also { banner ->

                    banner.layoutParams = ViewGroup.LayoutParams(
                        size.getWidthInPixels(context),
                        size.getHeightInPixels(context)
                    )
                    // Set a listener on the SASBannerView
                    banner.bannerListener = object : SASBannerView.BannerListener {
                        // get a Handler on the main thread to execute code on this thread

                        override fun onBannerAdLoaded(adInfo: SASAdInfo) {
                            // Equativ banner ad was successfully loaded
                            CoroutineScope(Dispatchers.Main).launch {
                                mediationBannerAdCallback = mediationAdLoadCallback.onSuccess(this@SASGMAMediationBannerAdapter)
                            }
                        }

                        override fun onBannerAdRequestClose() {
                            // Nothing to do
                        }

                        override fun onBannerAdFailedToLoad(exception: SASException) {
                            // Equativ banner ad failed to load
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

                        override fun onBannerAdClicked() {
                            // Equativ banner ad was clicked
                            CoroutineScope(Dispatchers.Main).launch {
                                mediationBannerAdCallback?.run {
                                    reportAdClicked()
                                }
                            }
                        }
                    }
                    // Now request ad for this SASBannerView
                    banner.loadAd(adPlacement)
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

    companion object {
        private var applicationContextWeakReference: WeakReference<Context>? = null
    }

    override fun getView(): View {
        return sasBannerView as View
    }
}