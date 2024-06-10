package com.smartadserver.android.library.mediation

import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import com.google.android.gms.ads.rewarded.RewardItem
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.model.SASReward
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager.RewardedVideoListener
import com.smartadserver.android.library.ui.SASAdView
import com.smartadserver.android.library.util.SASUtil
import java.lang.ref.WeakReference

/**
 * Class that handles Google Mobile Ads mediation rewarded video ad calls to Smart AdServer SDK.
 */
class SASGMAMediationRewardedVideoAdAdapter : Adapter(), MediationRewardedAd {

    // Smart rewarded video manager that will handle the mediation ad call
    private var rewardedVideoManager: SASRewardedVideoManager? = null

    // callback instance from Google SDK in case of rewarded ad loading success
    var mediationRewardedAdCallback: MediationRewardedAdCallback? = null
    override fun initialize(context: Context,
                            initializationCompleteCallback: InitializationCompleteCallback,
                            list: List<MediationConfiguration>) {
        if (applicationContextWeakReference == null) {
            applicationContextWeakReference = WeakReference<Context>(context.applicationContext)

            // Nothing more to do here, Smart rewarded videos does not require initialization
            initializationCompleteCallback.onInitializationSucceeded()
        }
    }

    override fun getVersionInfo(): VersionInfo {
        return SASGMAUtils.versionInfo
    }

    override fun getSDKVersionInfo(): VersionInfo {
        return SASGMAUtils.SDKVersionInfo
    }

    override fun loadRewardedAd(mediationRewardedAdConfiguration: MediationRewardedAdConfiguration,
                                mediationAdLoadCallback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>
    ) {

        // get the smart placement object
        val smartPlacementString = mediationRewardedAdConfiguration.serverParameters.getString("parameter") ?: ""

        // Configure the Smart Display SDK and retrieve the ad placement.
        applicationContextWeakReference?.get()?.let { context ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(context,
                smartPlacementString, mediationRewardedAdConfiguration.mediationExtras)

            adPlacement?.let { placement ->
                // clean up any previous SASRewardedVideoManager
                rewardedVideoManager?.onDestroy()

                // instantiate the rewarded video manager
                rewardedVideoManager = SASRewardedVideoManager(context, placement).apply {
                    // set a listener on this rewarded video manager
                    rewardedVideoListener = object : RewardedVideoListener {
                        // get a main thread Handler to execute code on this thread
                        var handler = SASUtil.getMainLooperHandler()
                        override fun onRewardedVideoAdLoaded(sasRewardedVideoManager: SASRewardedVideoManager, sasAdElement: SASAdElement) {
                            // Smart rewarded video ad was successfully loaded
                            handler.post {
                                synchronized(this@SASGMAMediationRewardedVideoAdAdapter) {
                                    mediationRewardedAdCallback = mediationAdLoadCallback.onSuccess(this@SASGMAMediationRewardedVideoAdAdapter)
                                }
                            }
                        }

                        override fun onRewardedVideoAdFailedToLoad(sasRewardedVideoManager: SASRewardedVideoManager, e: Exception) {
                            // Smart rewarded video ad failed to load
                            handler.post {
                                var errorMessage: String = "Smart internal error"
                                if (e is SASNoAdToDeliverException) {
                                    // no ad to deliver
                                    errorMessage = "No fill"
                                } else if (e is SASAdTimeoutException) {
                                    // ad request timeout translates to admob network error
                                    errorMessage = "Smart ad request did not complete before timemout"
                                }
                                mediationAdLoadCallback.onFailure(createAdError(errorMessage))
                            }
                        }

                        @Synchronized
                        override fun onRewardedVideoAdShown(sasRewardedVideoManager: SASRewardedVideoManager) {
                            mediationRewardedAdCallback?.run {
                                onAdOpened()
                                onVideoStart()
                                reportAdImpression()
                            }
                        }

                        @Synchronized
                        override fun onRewardedVideoAdFailedToShow(sasRewardedVideoManager: SASRewardedVideoManager, e: Exception) {
                            mediationRewardedAdCallback?.onAdFailedToShow(createAdError(e.message ?: e.toString()))
                        }

                        override fun onRewardedVideoAdClosed(sasRewardedVideoManager: SASRewardedVideoManager) {

                            mediationRewardedAdCallback?.run {
                                // Smart rewarded video ad was closed
                                handler.post {
                                    synchronized(this@SASGMAMediationRewardedVideoAdAdapter) {
                                        onAdClosed()
                                    }
                                }
                            }
                        }

                        override fun onRewardReceived(sasRewardedVideoManager: SASRewardedVideoManager, sasReward: SASReward) {

                            mediationRewardedAdCallback?.run {
                                // Smart reward was granted
                                handler.post {
                                    synchronized(this@SASGMAMediationRewardedVideoAdAdapter) {
                                        this.onUserEarnedReward(object : RewardItem {
                                            override fun getType(): String {
                                                return sasReward.currency
                                            }

                                            override fun getAmount(): Int {
                                                return sasReward.amount.toInt()
                                            }
                                        })
                                    }
                                }
                            }
                        }

                        override fun onRewardedVideoAdClicked(sasRewardedVideoManager: SASRewardedVideoManager) {

                            mediationRewardedAdCallback?.run {
                                // Smart rewarded video ad was clicked
                                handler.post {
                                    synchronized(this@SASGMAMediationRewardedVideoAdAdapter) {
                                        reportAdClicked()
                                    }
                                }
                            }
                        }

                        override fun onRewardedVideoEvent(sasRewardedVideoManager: SASRewardedVideoManager, i: Int) {
                            mediationRewardedAdCallback?.run {
                                // filter video events from Smart rewarded video
                                handler.post {
                                    synchronized(this@SASGMAMediationRewardedVideoAdAdapter) {
                                        when (i) {
                                            SASAdView.VideoEvents.VIDEO_START -> onVideoStart()
                                            SASAdView.VideoEvents.VIDEO_COMPLETE -> onVideoComplete()
                                        }
                                    }
                                }
                            }
                        }

                        override fun onRewardedVideoEndCardDisplayed(sasRewardedVideoManager: SASRewardedVideoManager, viewGroup: ViewGroup) {
                            // noting to report
                        }
                    }

                    // Now request ad on this SASRewardedVideoManager
                    loadRewardedVideo()
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

    override fun loadRewardedInterstitialAd(mediationRewardedAdConfiguration: MediationRewardedAdConfiguration,
                                mediationAdLoadCallback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>
    ) {
        loadRewardedAd(mediationRewardedAdConfiguration, mediationAdLoadCallback)
    }

    private fun createAdError(errorMessage: String): AdError {
        return AdError(AdRequest.ERROR_CODE_INTERNAL_ERROR, errorMessage, AdError.UNDEFINED_DOMAIN)
    }

    override fun showAd(context: Context) {
        rewardedVideoManager?.run {
            if (hasRewardedVideo()) {
                showRewardedVideo()
            }
        }
    }

    companion object {
        private var applicationContextWeakReference: WeakReference<Context>? = null
    }
}