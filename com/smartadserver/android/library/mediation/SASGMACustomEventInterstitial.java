package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitial;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.ui.SASInterstitialManager;
import com.smartadserver.android.library.util.SASUtil;


/**
 * Class that handles an adMob mediation interstitial ad call to Smart AdServer SDK.
 */
public class SASGMACustomEventInterstitial implements CustomEventInterstitial {

    // Smart interstitial manager that will handle the mediation ad call
    private SASInterstitialManager interstitialManager;

    /**
     * Implementation of CustomEventInterstitial interface.
     * Delegates the interstitial ad call to Smart AdServer SDK
     */
    @Override
    public void requestInterstitialAd(@NonNull Context context,
                                      final @NonNull CustomEventInterstitialListener customEventInterstitialListener,
                                      @Nullable String s,
                                      @NonNull MediationAdRequest mediationAdRequest,
                                      @Nullable Bundle bundle) {

        Log.d("CustomEventInterstitial", "requestInterstitialAd for SASGMACustomEventInterstitial");

        // get the smart placement object
        if (s == null) {
            s = "";
        }

        // Configure the Smart Display SDK and retrieve the ad placement.
        SASAdPlacement adPlacement = SASGMACustomEventUtil.configureSDKAndGetAdPlacement(context, s, bundle);

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventInterstitialListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
            return;
        }

        if (interstitialManager != null) {
            // Quit if there is already an interstitial being handled.
            return;
        }

        // instantiate a Smart interstitial manager
        interstitialManager = new SASInterstitialManager(context, adPlacement);

        // Set a listener on this manager
        interstitialManager.setInterstitialListener(new SASInterstitialManager.InterstitialListener() {

            // get a main thread Handler to execute code on this thread
            Handler handler = SASUtil.getMainLooperHandler();

            @Override
            public void onInterstitialAdLoaded(@NonNull SASInterstitialManager sasInterstitialManager, @NonNull SASAdElement sasAdElement) {
                // Smart interstitial ad was successfully loaded
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventInterstitialListener.onAdLoaded();
                    }
                });
            }

            @Override
            public void onInterstitialAdFailedToLoad(@NonNull SASInterstitialManager sasInterstitialManager, @NonNull final Exception e) {
                // Smart interstitial ad failed to load
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
                        customEventInterstitialListener.onAdFailedToLoad(errorCode);
                    }
                });
            }

            @Override
            public void onInterstitialAdShown(@NonNull SASInterstitialManager sasInterstitialManager) {
                // Smart interstitial ad was displayed (full screen)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventInterstitialListener.onAdOpened();
                    }
                });
            }

            @Override
            public void onInterstitialAdFailedToShow(@NonNull SASInterstitialManager sasInterstitialManager, @NonNull Exception e) {
                // no dfp counterpart to call
            }

            @Override
            public void onInterstitialAdClicked(@NonNull SASInterstitialManager sasInterstitialManager) {
                // Smart interstitial ad was clicked
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventInterstitialListener.onAdClicked();
                    }
                });
            }

            @Override
            public void onInterstitialAdDismissed(@NonNull SASInterstitialManager sasInterstitialManager) {
                // Smart interstitial ad was closed
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventInterstitialListener.onAdClosed();
                    }
                });
            }

            @Override
            public void onInterstitialAdVideoEvent(@NonNull SASInterstitialManager sasInterstitialManager, int i) {
                // nothing to do here
            }
        });

        // Now request ad on this SASInterstitialManager
        interstitialManager.loadAd();
    }

    /**
     * Implementation of CustomEventInterstitial interface method.
     * Displays the previously interstitial ad loaded by the {@link SASInterstitialManager}, if any
     */
    @Override
    public void showInterstitial() {
        if (interstitialManager != null && interstitialManager.isShowable()) {
            interstitialManager.show();
        }
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to SASInterstitialView
     */
    @Override
    public synchronized void onDestroy() {
        if (interstitialManager != null) {
            interstitialManager.onDestroy();
            interstitialManager = null;
        }
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onPause() call to SASInterstitialView
     */
    @Override
    public void onPause() {
        // not supported by SASInterstitialView
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onResume() call to SASInterstitialView
     */
    @Override
    public void onResume() {
        // not supported by SASInterstitialView
    }

}
