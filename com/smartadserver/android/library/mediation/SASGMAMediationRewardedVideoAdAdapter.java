package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration;
import com.google.android.gms.ads.mediation.VersionInfo;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASReward;
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager;
import com.smartadserver.android.library.ui.SASAdView;
import com.smartadserver.android.library.util.SASLibraryInfo;
import com.smartadserver.android.library.util.SASUtil;

import java.lang.ref.WeakReference;
import java.util.List;

public class SASGMAMediationRewardedVideoAdAdapter extends Adapter implements MediationRewardedAd {

    private static WeakReference<Context> applicationContextWeakReference;
    boolean isInitialized = false;

    // Smart rewarded video manager that will handle the mediation ad call
    private SASRewardedVideoManager rewardedVideoManager;

    // callback instance from Google SDK in case of rewarded ad loading success
    MediationRewardedAdCallback mediationRewardedAdCallback = null;

    @Override
    public void initialize(@NonNull Context context,
                           @NonNull InitializationCompleteCallback initializationCompleteCallback,
                           @NonNull List<MediationConfiguration> list) {

        this.applicationContextWeakReference = new WeakReference(context.getApplicationContext());
        isInitialized = true;

        // Nothing more to do here, Smart rewarded videos does not require initialization
        initializationCompleteCallback.onInitializationSucceeded();
    }

    @NonNull
    @Override
    public VersionInfo getVersionInfo() {
        return SASGMACustomEventUtil.getVersionInfo();
    }

    @NonNull
    @Override
    public VersionInfo getSDKVersionInfo() {
        return SASGMACustomEventUtil.getSDKVersionInfo();
    }

    @Override
    public void loadRewardedAd(@NonNull MediationRewardedAdConfiguration mediationRewardedAdConfiguration,
                               @NonNull final MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> mediationAdLoadCallback) {


        String smartParameters = mediationRewardedAdConfiguration.getServerParameters().getString("parameter");

        // get the smart placement object
        if (smartParameters == null) {
            smartParameters = "";
        }

        // Configure the Smart Display SDK and retrieve the ad placement.

        SASAdPlacement adPlacement = null;
        Context context = applicationContextWeakReference.get();
        if (context != null) {
            adPlacement = SASGMACustomEventUtil.configureSDKAndGetAdPlacement(context,
                    smartParameters, mediationRewardedAdConfiguration.getMediationExtras());
        }

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            mediationAdLoadCallback.onFailure(
                    createAdError("Invalid Smart placement IDs. Please check server parameters string"));
            return;
        }

        if (rewardedVideoManager != null && adPlacement != rewardedVideoManager.getAdPlacement()) {
            // the placement is different, need to release previous SASRewardedVideoManager
            rewardedVideoManager.onDestroy();
            rewardedVideoManager = null;
        }

        // instantiate a Smart interstitial manager
        if (rewardedVideoManager == null) {
            rewardedVideoManager = new SASRewardedVideoManager(context, adPlacement);
        }


        // set a listener on this rewarded video manager
        rewardedVideoManager.setRewardedVideoListener(new SASRewardedVideoManager.RewardedVideoListener() {

            // get a main thread Handler to execute code on this thread
            Handler handler = SASUtil.getMainLooperHandler();

            @Override
            public void onRewardedVideoAdLoaded(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull SASAdElement sasAdElement) {
                // Smart rewarded video ad was successfully loaded
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (SASGMAMediationRewardedVideoAdAdapter.this) {
                            mediationRewardedAdCallback = mediationAdLoadCallback.onSuccess(SASGMAMediationRewardedVideoAdAdapter.this);
                        }
                    }
                });
            }

            @Override
            public void onRewardedVideoAdFailedToLoad(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull final Exception e) {
                // Smart rewarded video ad failed to load
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String errorMessage = "Smart internal error";
                        if (e instanceof SASNoAdToDeliverException) {
                            // no ad to deliver
                            errorMessage = "No fill";
                        } else if (e instanceof SASAdTimeoutException) {
                            // ad request timeout translates to admob network error
                            errorMessage = "Smart ad request did not complete before timemout";
                        }
                        mediationAdLoadCallback.onFailure(createAdError(errorMessage));
                    }
                });
            }

            @Override
            public synchronized void onRewardedVideoAdShown(@NonNull SASRewardedVideoManager sasRewardedVideoManager) {
                // TODO : check main thread ?
                // no GMA method to call
                if (mediationRewardedAdCallback != null) {
                    mediationRewardedAdCallback.onAdOpened();
                    mediationRewardedAdCallback.onVideoStart();
                    mediationRewardedAdCallback.reportAdImpression();
                }
            }

            @Override
            public synchronized void onRewardedVideoAdFailedToShow(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull Exception e) {
                // TODO : check main thread ?
                if (mediationRewardedAdCallback != null) {
                    mediationRewardedAdCallback.onAdFailedToShow(createAdError(e.getMessage()));
                }
            }

            @Override
            public void onRewardedVideoAdClosed(@NonNull SASRewardedVideoManager sasRewardedVideoManager) {

                // Smart rewarded video ad was closed
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (SASGMAMediationRewardedVideoAdAdapter.this) {
                            if (mediationRewardedAdCallback != null) {
                                mediationRewardedAdCallback.onAdClosed();
                            }
                        }
                    }
                });
            }

            @Override
            public void onRewardReceived(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull final SASReward sasReward) {
                // Smart reward was granted
                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        synchronized (SASGMAMediationRewardedVideoAdAdapter.this) {
                            if (mediationRewardedAdCallback != null) {
                                mediationRewardedAdCallback.onUserEarnedReward(new RewardItem() {
                                    @NonNull
                                    @Override
                                    public String getType() {
                                        return sasReward.getCurrency();
                                    }

                                    @Override
                                    public int getAmount() {
                                        return (int)sasReward.getAmount();
                                    }
                                });
                            }
                        }
                    }
                });
            }

            @Override
            public void onRewardedVideoAdClicked(@NonNull SASRewardedVideoManager sasRewardedVideoManager) {
                // Smart rewarded video ad was clicked
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (SASGMAMediationRewardedVideoAdAdapter.this) {
                            if (mediationRewardedAdCallback != null) {
                                mediationRewardedAdCallback.reportAdClicked();
                            }
                        }
                    }
                });
            }

            @Override
            public void onRewardedVideoEvent(@NonNull SASRewardedVideoManager sasRewardedVideoManager, final int i) {
                // filter video events from Smart rewarded video
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (SASGMAMediationRewardedVideoAdAdapter.this) {
                            if (mediationRewardedAdCallback != null) {
                                switch (i) {
                                    case SASAdView.VideoEvents.VIDEO_START:
                                        mediationRewardedAdCallback.onVideoStart();
                                        break;
                                    case SASAdView.VideoEvents.VIDEO_COMPLETE:
                                        mediationRewardedAdCallback.onVideoComplete();
                                        break;
                                }

                            }
                        }
                    }
                });
            }

            @Override
            public void onRewardedVideoEndCardDisplayed(@NonNull SASRewardedVideoManager sasRewardedVideoManager, @NonNull ViewGroup viewGroup) {
                // noting to report
            }
        });

        // Now request ad on this SASRewardedVideoManager
        rewardedVideoManager.loadRewardedVideo();
    }

    private AdError createAdError(String errorMessage) {
        return new AdError(-1, errorMessage, AdError.UNDEFINED_DOMAIN);
    }

    @Override
    public void showAd(@NonNull Context context) {
        if (rewardedVideoManager != null && rewardedVideoManager.hasRewardedVideo()) {
            rewardedVideoManager.showRewardedVideo();
        }
    }
}
