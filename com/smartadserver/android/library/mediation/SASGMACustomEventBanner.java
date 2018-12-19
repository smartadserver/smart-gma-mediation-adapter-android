package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventBanner;
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.ui.SASBannerView;
import com.smartadserver.android.library.util.SASUtil;

/**
 * Class that handles an adMob mediation banner ad call to Smart AdServer SDK.
 */
public class SASGMACustomEventBanner extends SASGMACustomEventBase implements CustomEventBanner {

    // Smart banner view that will handle the mediation ad call
    private SASBannerView sasBannerView;

    /**
     * Implementation of CustomEventBanner interface.
     * Delegates the banner ad call to Smart AdServer SDK
     */
    @Override
    public void requestBannerAd(final Context context, final CustomEventBannerListener customEventBannerListener,
                                String s, final AdSize adSize, MediationAdRequest mediationAdRequest, Bundle bundle) {

        // get the smart placement object
        if (s == null) {
            s = "";
        }

        // Configure the Smart Display SDK and retrieve the ad placement.
        SASAdPlacement adPlacement = configureSDKAndGetAdPlacement(context, s, mediationAdRequest);

        // test if the ad placement is valid
        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventBannerListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
            return;
        }

        if (sasBannerView != null) {
            // Quit if there is already a banner being handled.
            return;
        }

        // Instantiate the SASBannerView
        sasBannerView = new SASBannerView(context) {
            /**
             * Overriden to force banner size to received admob size if not expanded
             * @param params
             */
            @Override
            public void setLayoutParams(ViewGroup.LayoutParams params) {
                if (!sasBannerView.isExpanded()) {
                    params.height = adSize.getHeightInPixels(context);
                    params.width = adSize.getWidthInPixels(context);
                }
                super.setLayoutParams(params);
            }
        };

        // Set a listener on the SASBannerView
        sasBannerView.setBannerListener(new SASBannerView.BannerListener() {

            // get a Handler on the main thread to execute code on this thread
            Handler handler = SASUtil.getMainLooperHandler();

            @Override
            public void onBannerAdLoaded(SASBannerView bannerView, SASAdElement sasAdElement) {
                // Smart banner ad was successfully loaded
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventBannerListener.onAdLoaded(sasBannerView);
                    }
                });
            }

            @Override
            public void onBannerAdFailedToLoad(SASBannerView sasBannerView, final Exception e) {
                // Smart banner ad failed to load
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
                        customEventBannerListener.onAdFailedToLoad(errorCode);
                    }
                });
            }

            @Override
            public void onBannerAdClicked(SASBannerView sasBannerView) {
                // Smart banner ad was clicked
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventBannerListener.onAdClicked();
                    }
                });
            }

            @Override
            public void onBannerAdExpanded(SASBannerView sasBannerView) {
                // Smart banner ad was displayed full screen
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventBannerListener.onAdOpened();
                    }
                });
            }

            @Override
            public void onBannerAdCollapsed(SASBannerView sasBannerView) {
                // Smart banner ad was restored to its default state
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        customEventBannerListener.onAdClosed();
                    }
                });
            }

            @Override
            public void onBannerAdResized(SASBannerView sasBannerView) {
                // nothing to do here
            }

            @Override
            public void onBannerAdClosed(SASBannerView sasBannerView) {
                // nothing to do here
            }

            @Override
            public void onBannerAdVideoEvent(SASBannerView sasBannerView, int i) {
                // nothing to do here
            }
        });

        // Now request ad for this SASBannerView
        sasBannerView.loadAd(adPlacement);
    }


    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to SASBannerView
     */
    @Override
    public synchronized void onDestroy() {
        if (sasBannerView != null) {
            sasBannerView.onDestroy();
            sasBannerView = null;
        }
    }

    /**
     * Implementation of CustomEventBanner interface.
     * Forwards the onPause() call to SASBannerView
     */
    @Override
    public void onPause() {
        // not supported by SASBannerView
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onResume() call to SASBannerView
     */
    @Override
    public void onResume() {
        // not supported by SASBannerView
    }
}
