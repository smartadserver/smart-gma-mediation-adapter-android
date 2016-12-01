package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.mediation.NativeAdMapper;
import com.google.android.gms.ads.mediation.NativeMediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventNative;
import com.google.android.gms.ads.mediation.customevent.CustomEventNativeListener;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASNativeAdElement;
import com.smartadserver.android.library.model.SASNativeAdManager;
import com.smartadserver.android.library.model.SASNativeAdPlacement;
import com.smartadserver.android.library.util.SASConstants;

/**
 * Class that handles an adMob mediation banner ad call to Smart AdServer SDK.
 */
public class SASCustomEventNative implements CustomEventNative {

    // Smart banner view that will handle the mediation ad call
    SASNativeAdManager sasNativeAdManager;

    // Smart AdResponseHandler to handle smart ad request outcome
    SASNativeAdManager.NativeAdResponseHandler nativeAdResponseHandler;



    /**
     * Implementation of CustomEventNative interface.
     * Delegates the banner ad call to Smart AdServer SDK
     */
    @Override
    public void requestNativeAd(final Context context, final CustomEventNativeListener customEventNativeListener, String s, NativeMediationAdRequest nativeMediationAdRequest, Bundle bundle) {
        // get smart placement object
        SASCustomEventUtil.SASAdPlacement adPlacement = SASCustomEventUtil.getPlacementFromString(s,nativeMediationAdRequest);

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventNativeListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
        } else {
            if (sasNativeAdManager == null) {
                // instantiate the AdResponseHandler to handle Smart ad call outcome
                nativeAdResponseHandler = new SASNativeAdManager.NativeAdResponseHandler() {
                    @Override
                    public void nativeAdLoadingCompleted(SASNativeAdElement nativeAdElement) {

                        // TODO native ad mapper
                        NativeAdMapper adMapper = new NativeAdMapper() {
                        };
                        customEventNativeListener.onAdLoaded(adMapper);

                        nativeAdElement.setClickHandler(new SASNativeAdElement.ClickHandler() {

                            @Override
                            public boolean handleClick(String clickUrl, SASNativeAdElement nativeAdElement) {
                                customEventNativeListener.onAdClicked();
                                customEventNativeListener.onAdOpened();
                                customEventNativeListener.onAdLeftApplication();
                                return false;
                            }
                        });
                    }

                    @Override
                    public void nativeAdLoadingFailed(Exception e) {
                        int errorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR;
                        if (e instanceof SASNoAdToDeliverException) {
                            // no ad to deliver
                            errorCode = AdRequest.ERROR_CODE_NO_FILL;
                        } else if (e instanceof SASAdTimeoutException) {
                            // ad request timeout translates to admob network error
                            errorCode = AdRequest.ERROR_CODE_NETWORK_ERROR;
                        }
                        customEventNativeListener.onAdFailedToLoad(errorCode);
                    }
                };

                SASNativeAdPlacement nativeAdPlacement = new SASNativeAdPlacement(SASConstants.DEFAULT_BASE_URL,
                        adPlacement.siteId,adPlacement.pageId,adPlacement.formatId,adPlacement.targeting);

                // instantiate SASNativeAdManager that will perform the Smart ad call
                sasNativeAdManager = new SASNativeAdManager(context,nativeAdPlacement);


                // pass received location on to SASNativeAdManager
                sasNativeAdManager.setLocation(nativeMediationAdRequest.getLocation());

                // Now request ad for this SASNativeAdManager
                sasNativeAdManager.requestNativeAd(nativeAdResponseHandler,10000);
            }
        }
    }




    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to SASBannerView
     */
    @Override
    public void onDestroy() {
        if (sasNativeAdManager != null) {
            sasNativeAdManager.onDestroy();
            sasNativeAdManager = null;
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
