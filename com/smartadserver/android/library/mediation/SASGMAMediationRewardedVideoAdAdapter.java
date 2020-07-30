package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.mediation.MediationRewardedVideoAdAdapter;
import com.google.android.gms.ads.reward.mediation.MediationRewardedVideoAdListener;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASReward;
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager;
import com.smartadserver.android.library.ui.SASAdView;
import com.smartadserver.android.library.ui.SASInterstitialManager;
import com.smartadserver.android.library.util.SASUtil;

public class SASGMAMediationRewardedVideoAdAdapter extends SASGMACustomEventBase implements MediationRewardedVideoAdAdapter {

    MediationRewardedVideoAdListener mediationRewardedVideoAdListener;
    Context context;
    boolean isInitialized = false;

    // Smart rewarded video manager that will handle the mediation ad call
    private SASRewardedVideoManager rewardedVideoManager;

    @Override
    public void initialize(Context context, MediationAdRequest mediationAdRequest, String s, MediationRewardedVideoAdListener mediationRewardedVideoAdListener, Bundle bundle, Bundle bundle1) {
        this.mediationRewardedVideoAdListener = mediationRewardedVideoAdListener;
        this.context = context;
        isInitialized = true;
        // Nothing more do here, Smart rewarded videos does not require initialization
        mediationRewardedVideoAdListener.onInitializationSucceeded(this);
    }

    @Override
    public void loadAd(MediationAdRequest mediationAdRequest, Bundle bundle, Bundle bundle1) {

        String smartParameters = bundle.getString("parameter");

        // get the smart placement object
        if (smartParameters == null) {
            smartParameters = "";
        }

        // Configure the Smart Display SDK and retrieve the ad placement.
        SASAdPlacement adPlacement = configureSDKAndGetAdPlacement(context, smartParameters, mediationAdRequest);

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            mediationRewardedVideoAdListener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INVALID_REQUEST);
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
            public void onRewardedVideoAdLoaded(SASRewardedVideoManager sasRewardedVideoManager, SASAdElement sasAdElement) {
                // Smart rewarded video ad was successfully loaded
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mediationRewardedVideoAdListener.onAdLoaded(SASGMAMediationRewardedVideoAdAdapter.this);
                    }
                });
            }

            @Override
            public void onRewardedVideoAdFailedToLoad(SASRewardedVideoManager sasRewardedVideoManager, final Exception e) {
                // Smart rewarded video ad failed to load
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        int errorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR;
                        if (e instanceof SASNoAdToDeliverException) {
                            // no ad to deliver
                            errorCode = AdRequest.ERROR_CODE_NO_FILL;
                        } else if (e instanceof SASAdTimeoutException) {
                            // ad request timeout translates to admob network error
                            errorCode = AdRequest.ERROR_CODE_NETWORK_ERROR;
                        }
                        mediationRewardedVideoAdListener.onAdFailedToLoad(SASGMAMediationRewardedVideoAdAdapter.this, errorCode);
                    }
                });
            }

            @Override
            public void onRewardedVideoAdShown(SASRewardedVideoManager sasRewardedVideoManager) {
                // no GMA method to call
            }

            @Override
            public void onRewardedVideoAdFailedToShow(SASRewardedVideoManager sasRewardedVideoManager, Exception e) {
                // no GMA method to call
            }

            @Override
            public void onRewardedVideoAdClosed(SASRewardedVideoManager sasRewardedVideoManager) {
                // Smart rewarded video ad was closed
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mediationRewardedVideoAdListener.onAdClosed(SASGMAMediationRewardedVideoAdAdapter.this);
                    }
                });
            }

            @Override
            public void onRewardReceived(SASRewardedVideoManager sasRewardedVideoManager, final SASReward sasReward) {
                // Smart reward was granted
                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        RewardItem rewardItem = new RewardItem() {

                            String currency = sasReward.getCurrency();
                            double amount = sasReward.getAmount();

                            @Override
                            public String getType() {
                                return currency;
                            }

                            @Override
                            public int getAmount() {
                                return (int) amount;
                            }
                        };
                        mediationRewardedVideoAdListener.onRewarded(SASGMAMediationRewardedVideoAdAdapter.this, rewardItem);
                    }
                });
            }

            @Override
            public void onRewardedVideoAdClicked(SASRewardedVideoManager sasRewardedVideoManager) {
                // Smart rewarded video ad was clicked
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mediationRewardedVideoAdListener.onAdClicked(SASGMAMediationRewardedVideoAdAdapter.this);
                    }
                });
            }

            @Override
            public void onRewardedVideoEvent(SASRewardedVideoManager sasRewardedVideoManager, final int i) {
                // filter video events from Smart rewarded video
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        switch (i) {
                            case SASAdView.VideoEvents.VIDEO_START:
                                mediationRewardedVideoAdListener.onVideoStarted(SASGMAMediationRewardedVideoAdAdapter.this);
                                break;
                            case SASAdView.VideoEvents.VIDEO_COMPLETE:
                                mediationRewardedVideoAdListener.onVideoCompleted(SASGMAMediationRewardedVideoAdAdapter.this);
                                break;
                        }

                        mediationRewardedVideoAdListener.onAdClicked(SASGMAMediationRewardedVideoAdAdapter.this);
                    }
                });
            }

            @Override
            public void onRewardedVideoEndCardDisplayed(SASRewardedVideoManager sasRewardedVideoManager, ViewGroup viewGroup) {
                // Smart rewarded video ad was clicked
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mediationRewardedVideoAdListener.onAdOpened(SASGMAMediationRewardedVideoAdAdapter.this);
                    }
                });
            }
        });

        // Now request ad on this SASRewardedVideoManager
        rewardedVideoManager.loadRewardedVideo();
    }

    @Override
    public void showVideo() {
        if (rewardedVideoManager != null && rewardedVideoManager.hasRewardedVideo()) {
            rewardedVideoManager.showRewardedVideo();
        }
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    @Override
    public void onDestroy() {
        if (rewardedVideoManager != null) {
            rewardedVideoManager.onDestroy();
            rewardedVideoManager = null;
        }
    }

    @Override
    public void onPause() {
        // not supported by SASRewardedVideoManager
    }

    @Override
    public void onResume() {
        // not supported by SASRewardedVideoManager
    }
}
