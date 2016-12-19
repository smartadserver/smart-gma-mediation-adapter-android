package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventBanner;
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;
import com.smartadserver.android.library.SASBannerView;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.ui.SASAdView;

/**
 * Class that handles an adMob mediation banner ad call to Smart AdServer SDK.
 */
public class SASCustomEventBanner implements CustomEventBanner {

    // Smart banner view that will handle the mediation ad call
    SASBannerView sasBannerView;

    // Smart AdResponseHandler to handle smart ad request outcome
    SASAdView.AdResponseHandler mAdResponseHandler;

    /**
     * Implementation of CustomEventBanner interface.
     * Delegates the banner ad call to Smart AdServer SDK
     */
    @Override
    public void requestBannerAd(final Context context, final CustomEventBannerListener customEventBannerListener,
                                String s, final AdSize adSize, MediationAdRequest mediationAdRequest, Bundle bundle) {
        // get smart placement object
        SASCustomEventUtil.SASAdPlacement adPlacement = SASCustomEventUtil.getPlacementFromString(s,mediationAdRequest);

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventBannerListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
        } else {
            if (sasBannerView == null) {
                // instantiate the AdResponseHandler to handle Smart ad call outcome
                mAdResponseHandler = new SASAdView.AdResponseHandler() {
                    @Override
                    public void adLoadingCompleted(SASAdElement sasAdElement) {
                        synchronized (SASCustomEventBanner.this) {
                            if (sasBannerView != null) {
                                sasBannerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // notify admob that ad call has succeeded
                                        customEventBannerListener.onAdLoaded(sasBannerView);
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void adLoadingFailed(final Exception e) {
                        // notify admob that ad call has failed with appropriate eror code
                        synchronized (SASCustomEventBanner.this) {
                            if (sasBannerView != null) {
                                sasBannerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // default generic error code
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

                        }

                    }
                };

                // instantiate SASBannerView that will perform the Smart ad call
                sasBannerView = new SASBannerView(context) {

                    /**
                     * Overriden to notify ad mob that the ad was opened
                     */
                    @Override
                    public void open(String url) {
                        super.open(url);
                        if (isAdWasOpened()) {
                            SASAdElement adElement = sasBannerView.getCurrentAdElement();
                            final boolean openInApp = adElement.isOpenClickInApplication();
                            sasBannerView.post(new Runnable() {
                                @Override
                                public void run() {
                                    customEventBannerListener.onAdClicked();
                                    customEventBannerListener.onAdOpened();
                                    if (!openInApp) {
                                        customEventBannerListener.onAdLeftApplication();
                                    }
                                }
                            });
                        }
                    }

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

                // add state change listener to detect when ad is closed
                sasBannerView.addStateChangeListener(new SASAdView.OnStateChangeListener() {
                    boolean wasOpened = false;
                    public void onStateChanged(
                            SASAdView.StateChangeEvent stateChangeEvent) {
                        switch (stateChangeEvent.getType()) {
                            case SASAdView.StateChangeEvent.VIEW_EXPANDED:
                                // ad was expanded
                                customEventBannerListener.onAdOpened();
                                wasOpened = true;
                                break;
                            case SASAdView.StateChangeEvent.VIEW_DEFAULT:
                                // ad was collapsed
                                if (wasOpened) {
                                    customEventBannerListener.onAdClosed();
                                    wasOpened = false;
                                }
                                break;
                        }
                    }
                });

                // pass received location on to SASBannerView
                sasBannerView.setLocation(mediationAdRequest.getLocation());


                // Now request ad for this SASBannerView
                sasBannerView.loadAd(adPlacement.siteId,adPlacement.pageId,adPlacement.formatId,true,
                        adPlacement.targeting,mAdResponseHandler,10000);

            }
        }

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
