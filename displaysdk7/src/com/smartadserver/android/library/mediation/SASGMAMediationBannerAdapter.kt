package com.smartadserver.android.library.mediation

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.ui.SASBannerView
import com.smartadserver.android.library.util.SASUtil
import java.lang.ref.WeakReference

/**
 * Class that handles Google Mobile Ads mediation banner ad calls to Smart AdServer SDK.
 */
class SASGMAMediationBannerAdapter : Adapter(), MediationBannerAd {

    // Smart banner view that will handle the mediation ad call
    private var sasBannerView: SASBannerView? = null

    // Callback instance from Google SDK in case of banner ad loading success
    var mediationBannerAdCallback: MediationBannerAdCallback? = null
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

            // Nothing more to do here, Smart banner does not require initialization
            initializationCompleteCallback.onInitializationSucceeded()
        }
    }
    override fun loadBannerAd(
        mediationAdConfiguration: MediationBannerAdConfiguration,
        mediationAdLoadCallback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>
    ) {
        Log.d("SASGMAMediationBannerAdapter", "loadBannerAd for SASGMAMediationBannerAdapter")

        // Get the Smart placement string parameter
        val smartPlacementString = mediationAdConfiguration.serverParameters.getString("parameter") ?: ""
        val size: AdSize = mediationAdConfiguration.adSize

        // Configure the Smart Display SDK and retrieve the ad placement.
        applicationContextWeakReference?.get()?.let { context ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(
                context,
                smartPlacementString, mediationAdConfiguration.mediationExtras
            )

            adPlacement?.let {

                // clean up any previous SASBannerView
                sasBannerView?.onDestroy()

                // Instantiate the SASBannerView
                sasBannerView = object : SASBannerView(context) {
                    /**
                     * Overridden to force banner size to received admob size if not expanded
                     * @param params
                     */
                    override fun setLayoutParams(params: ViewGroup.LayoutParams) {
                        if (!this.isExpanded) {
                            params.height = size.getHeightInPixels(context)
                            params.width = size.getWidthInPixels(context)
                        }
                        super.setLayoutParams(params)
                    }
                }.apply {
                    // Set a listener on the SASBannerView
                    bannerListener = object : SASBannerView.BannerListener {
                        // get a Handler on the main thread to execute code on this thread
                        var handler = SASUtil.getMainLooperHandler()
                        override fun onBannerAdLoaded(
                            bannerView: SASBannerView,
                            sasAdElement: SASAdElement
                        ) {
                            // Smart banner ad was successfully loaded
                            handler.post {
                                mediationBannerAdCallback = mediationAdLoadCallback.onSuccess(this@SASGMAMediationBannerAdapter)
                            }
                        }

                        override fun onBannerAdFailedToLoad(
                            sasBannerView: SASBannerView,
                            e: Exception
                        ) {
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
                                mediationAdLoadCallback.onFailure(
                                    AdError(
                                        errorCode,
                                        errorMessage,
                                        AdError.UNDEFINED_DOMAIN
                                    )
                                )
                            }
                        }

                        override fun onBannerAdClicked(sasBannerView: SASBannerView) {
                            // Smart banner ad was clicked
                            handler.post {
                                mediationBannerAdCallback?.run {
                                    reportAdClicked()
                                }
                            }
                        }

                        override fun onBannerAdExpanded(sasBannerView: SASBannerView) {
                            // Smart banner ad was displayed full screen
                            handler.post {
                                mediationBannerAdCallback?.run {
                                    onAdOpened()
                                }
                            }
                        }

                        override fun onBannerAdCollapsed(sasBannerView: SASBannerView) {
                            // Smart banner ad was restored to its default state
                            handler.post {
                                mediationBannerAdCallback?.run {
                                    onAdClosed()
                                }
                            }
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
                    loadAd(adPlacement)
                }
            } ?: run {
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

    companion object {
        private var applicationContextWeakReference: WeakReference<Context>? = null
    }
    override fun getView(): View {
        return sasBannerView as View
    }
}