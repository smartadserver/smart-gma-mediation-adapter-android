package com.smartadserver.android.library.mediation;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitial;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener;
import com.smartadserver.android.library.SASInterstitialView;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.ui.SASAdView;


/**
 * Class that handles an adMob mediation interstitial ad call to Smart AdServer SDK.
 */
public class SASCustomEventInterstitial implements CustomEventInterstitial {

    // Smart interstitial view that will handle the mediation ad call
    SASInterstitialView sasInterstitialView;

    // DFP callback
    CustomEventInterstitialListener interstitialListener;

    // Container view for offscreen interstitial loading (as SASInterstitialView is displayed immediately after successful loading)
    FrameLayout interstitialContainer;

    // Smart AdResponseHandler to handle smart ad request outcome
    SASAdView.AdResponseHandler mAdResponseHandler;


    /**
     * Implementation of CustomEventInterstitial interface.
     * Delegates the interstitial ad call to Smart AdServer SDK
     */
    @Override
    public void requestInterstitialAd(Context context,final CustomEventInterstitialListener customEventInterstitialListener,
                                      String s, MediationAdRequest mediationAdRequest, Bundle bundle) {

        // get smart placement object
        SASCustomEventUtil.SASAdPlacement adPlacement = SASCustomEventUtil.getPlacementFromString(s,mediationAdRequest);

        // store dfp callback for future interaction
        interstitialListener = customEventInterstitialListener;

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventInterstitialListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
        } else {
            if (sasInterstitialView == null) {
                // instantiate the AdResponseHandler to handle Smart ad call outcome
                mAdResponseHandler = new SASAdView.AdResponseHandler() {
                    @Override
                    public void adLoadingCompleted(SASAdElement sasAdElement) {
                        //  notify AdMob of AdLoaded
						synchronized(SASCustomEventInterstitial.this) {
							if (sasInterstitialView != null) {
								sasInterstitialView.post(new Runnable() {
									@Override
									public void run() {
										customEventInterstitialListener.onAdLoaded();
									}
								});
							}
						}
                    }

                    @Override
                    public void adLoadingFailed(final Exception e) {
                        // notify admob that ad call has failed with appropriate eror code
						synchronized(SASCustomEventInterstitial.this) {
							if (sasInterstitialView != null) {
								sasInterstitialView.post(new Runnable() {
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
										customEventInterstitialListener.onAdFailedToLoad(errorCode);
									}
								});
							}
						}
                    }
                };

                // instantiate SASInterstitialView that will perform the Smart ad call
                sasInterstitialView = new SASInterstitialView(context) {

                    /**
                     * Overriden to notify ad mob that the ad was opened
                     */
                    @Override
                    public void open(String url) {
                        super.open(url);
                        if (isAdWasOpened()) {
                            SASAdElement adElement = sasInterstitialView.getCurrentAdElement();
                            final boolean openInApp = adElement.isOpenClickInApplication();
                            sasInterstitialView.post(new Runnable() {
                                @Override
                                public void run() {
                                    customEventInterstitialListener.onAdClicked();
                                    customEventInterstitialListener.onAdOpened();
                                    if (!openInApp) {
                                        customEventInterstitialListener.onAdLeftApplication();
                                    }
                                }
                            });
                        }
                    }
                };

                // add state change listener to detect when ad is closed or loaded and expanded (=ready to be displayed)
                sasInterstitialView.addStateChangeListener(new SASAdView.OnStateChangeListener() {
                    public void onStateChanged(
                            SASAdView.StateChangeEvent stateChangeEvent) {
                        switch (stateChangeEvent.getType()) {
                            case SASAdView.StateChangeEvent.VIEW_HIDDEN:
                                // ad was closed
                                ViewParent parent = interstitialContainer.getParent();
                                if (parent instanceof ViewGroup) {
                                    ((ViewGroup)parent).removeView(interstitialContainer);
                                }
                                customEventInterstitialListener.onAdClosed();
                                break;
                        }
                    }
                });

                // create the (offscreen) FrameLayout that the SASInterstitialView will expand into
                interstitialContainer = new FrameLayout(context);
                interstitialContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                sasInterstitialView.setExpandParentContainer(interstitialContainer);

                // pass received location on to SASBannerView
                sasInterstitialView.setLocation(mediationAdRequest.getLocation());

                // detect layout changes to update padding
                sasInterstitialView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        // on layout change, add a globalLayoutListener to apply padding once layout is done (and not to early)
                        sasInterstitialView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                sasInterstitialView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                Rect r = new Rect();
                                ViewParent parentView = interstitialContainer.getParent();
                                if (parentView instanceof View) {
                                    ((View)parentView).getWindowVisibleDisplayFrame(r);
                                    int topPadding = r.top;
                                    // handle navigation bar overlay by adding padding
                                    int leftPadding = r.left;
                                    int bottomPadding = Math.max(0,((View)parentView).getHeight() - r.bottom);
                                    int rightPadding =  Math.max(0,((View)parentView).getWidth() - r.right);
                                    interstitialContainer.setPadding(leftPadding,topPadding,rightPadding,bottomPadding);
                                }
                            }
                        });
                    }
                });

                // Now request ad for this SASBannerView
                sasInterstitialView.loadAd(adPlacement.siteId,adPlacement.pageId,adPlacement.formatId,true,
                                                        adPlacement.targeting,mAdResponseHandler,10000);


            }
        }
    }

    /**
     * Implementation of CustomEventInterstitial interface method.
     * Displays the previously loaded SASInterstitialView, if any
     */
    @Override
    public void showInterstitial() {

        // find the rootView where to add the interstitialContainer
        View rootContentView = null;
        Context context = sasInterstitialView.getContext();
        if (context instanceof Activity) {
            // try to find root view via Activity if available
            rootContentView = ((Activity)context).getWindow().getDecorView();
        }

        // now actually add the interstitialContainer including appropriate padding fir status/navigation bars
        if (rootContentView instanceof ViewGroup) {
            ((ViewGroup)rootContentView).addView(interstitialContainer);

            // notify DFP listener that ad was presented full screen
            if (interstitialListener != null) {
                interstitialListener.onAdOpened();
            }
        }
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to SASInterstitialView
     */
    @Override
    public synchronized void onDestroy() {
        if (sasInterstitialView != null) {
            sasInterstitialView.onDestroy();
            sasInterstitialView = null;
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
