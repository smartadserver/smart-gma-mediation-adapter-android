package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.formats.NativeAd;
import com.google.android.gms.ads.formats.NativeAdOptions;
import com.google.android.gms.ads.mediation.NativeAppInstallAdMapper;
import com.google.android.gms.ads.mediation.NativeContentAdMapper;
import com.google.android.gms.ads.mediation.NativeMediationAdRequest;
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;
import com.google.android.gms.ads.mediation.customevent.CustomEventNative;
import com.google.android.gms.ads.mediation.customevent.CustomEventNativeListener;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASNativeAdElement;
import com.smartadserver.android.library.model.SASNativeAdManager;
import com.smartadserver.android.library.ui.SASAdChoicesView;
import com.smartadserver.android.library.ui.SASNativeAdMediaView;
import com.smartadserver.android.library.util.SASUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

/**
 * Class that handles an adMob mediation banner ad call to Smart AdServer SDK.
 */
public class SASGMACustomEventNative extends SASGMACustomEventBase implements CustomEventNative {

    // the Smart native ad manager that will handle the mediation ad call
    private SASNativeAdManager sasNativeAdManager;

    // get a Handler on the main thread to execute code on this thread
    private Handler handler = SASUtil.getMainLooperHandler();


    /**
     * Implementation of CustomEventNative interface.
     * Delegates the native ad call to the Smart AdServer SDK
     */
    @Override
    public void requestNativeAd(final Context context, final CustomEventNativeListener customEventNativeListener, String s,
                                final NativeMediationAdRequest nativeMediationAdRequest, Bundle bundle) {

        // get the smart placement object
        if (s == null) {
            s = "";
        }

        // Configure the Smart Display SDK and retrieve the ad placement.
        SASAdPlacement adPlacement = configureSDKAndGetAdPlacement(context, s, nativeMediationAdRequest);

        // test if the ad placement is valid
        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventNativeListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
            return;
        }

        if (sasNativeAdManager != null) {
            // Quit if there is already a native ad being handled.
            return;
        }

        // instantiate SASNativeAdManager that will perform the Smart ad call
        sasNativeAdManager = new SASNativeAdManager(context, adPlacement);

        // set native ad listener
        sasNativeAdManager.setNativeAdListener(new SASNativeAdManager.NativeAdListener() {


            @Override
            public void onNativeAdLoaded(final SASNativeAdElement nativeAdElement) {

                if (nativeMediationAdRequest.isUnifiedNativeAdRequested()) {
                    // convert Smart native ad to a Google UnifiedNativeAd
                    processUnifiedNativeAdRequest(nativeAdElement, customEventNativeListener, nativeMediationAdRequest, context);

                } else if (nativeMediationAdRequest.isContentAdRequested()) {
                    // convert Smart native ad to a Google NativeContentAd
                    processContentNativeAdRequest(nativeAdElement, customEventNativeListener, nativeMediationAdRequest, context);

                } else if (nativeMediationAdRequest.isAppInstallAdRequested()) {
                    // convert Smart native ad to a Google NativeAppInstallAd
                    processAppInstallAdRequest(nativeAdElement, customEventNativeListener, nativeMediationAdRequest, context);

                } else {
                    // notify Google of a NO fill, as the ad received is not compatible
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            // return no ad
                            customEventNativeListener.onAdFailedToLoad(AdRequest.ERROR_CODE_NO_FILL);
                        }
                    });
                }

                // install a click handler on the SASNativeAdElement to notify back the customEventNativeListener
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
            public void onNativeAdFailedToLoad(final Exception e) {

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
                        customEventNativeListener.onAdFailedToLoad(errorCode);
                    }
                });
            }
        });

        // Now request ad for this SASNativeAdManager
        sasNativeAdManager.loadNativeAd();
    }

    /**
     * Creates a {@link NativeContentAdMapper} for the Smart native ad and pass it to Google SDK
     */
    private void processContentNativeAdRequest(final SASNativeAdElement nativeAdElement,
                                               final CustomEventNativeListener customEventNativeListener,
                                               NativeMediationAdRequest nativeMediationAdRequest,
                                               final Context context) {
        // instantiate a NativeAppInstallAdMapper to map properties from the SASNativeAdElement
        // to the google native ad
        final NativeContentAdMapper nativeAdMapper = new NativeContentAdMapper() {
            @Override
            public void trackViews(View view, Map<String, View> map, Map<String, View> map1) {
                                    /* trying to get only clickable view te refine click behavior doesn't work as expected :
                                       the only clickable View passed is the ad choices view, whereas we expect the call to action button...*/
//                                    Collection<View> clickableViews = map.values();
//                                    if (clickableViews.size() > 0) {
//                                        nativeAdElement.registerView(view,clickableViews.toArray(new View[0]));
//                                    } else {
//                                        nativeAdElement.registerView(view);
//                                    }

                // ... so register the whole view
                nativeAdElement.registerView(view);
            }

            @Override
            public void untrackView(View view) {
                nativeAdElement.unregisterView(view);
            }

        };

        nativeAdMapper.setOverrideClickHandling(true);
        nativeAdMapper.setHeadline(nativeAdElement.getTitle());
        nativeAdMapper.setBody(nativeAdElement.getBody());
        nativeAdMapper.setCallToAction(nativeAdElement.getCalltoAction());
        nativeAdMapper.setAdvertiser("Smart AdServer");

        // Set native ad icon if available
        if (nativeAdElement.getIcon() != null) {
            NativeAd.Image icon = getNativeAdImage(nativeAdElement.getIcon(), nativeMediationAdRequest);
            nativeAdMapper.setLogo(icon);
        }

        // set native ad cover if available
        if (nativeAdElement.getCoverImage() != null) {
            ArrayList<NativeAd.Image> imageList = new ArrayList<>();
            imageList.add(getNativeAdImage(nativeAdElement.getCoverImage(), nativeMediationAdRequest));
            nativeAdMapper.setImages(imageList);
        }

        // add an ad choices view
        SASAdChoicesView adChoicesView = new SASAdChoicesView(context);
        int size = SASUtil.getDimensionInPixels(20, adChoicesView.getResources());
        adChoicesView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        adChoicesView.setNativeAdElement(nativeAdElement);
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(adChoicesView);
        nativeAdMapper.setAdChoicesContent(frameLayout);

        handler.post(new Runnable() {
            @Override
            public void run() {

                // set the MediaView from Smart SDK, if any (NOT WORKING AS IT SEEMS)
                if (nativeAdElement.getMediaElement() != null) {
                    SASNativeAdMediaView mediaView = new SASNativeAdMediaView(context);
                    mediaView.setNativeAdElement(nativeAdElement);
                    mediaView.setBackgroundColor(Color.RED);

                    mediaView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    mediaView.setEnforceAspectRatio(true);

                    nativeAdMapper.setMediaView(mediaView);
                    nativeAdMapper.setHasVideoContent(true);
                }

                // notify Google tha a native ad was loaded
                customEventNativeListener.onAdLoaded(nativeAdMapper);
            }
        });
    }

    /**
     * Creates a {@link NativeContentAdMapper} for the Smart native ad and pass it to Google SDK
     */
    private void processAppInstallAdRequest(final SASNativeAdElement nativeAdElement,
                                            final CustomEventNativeListener customEventNativeListener,
                                            NativeMediationAdRequest nativeMediationAdRequest,
                                            final Context context) {
        // instantiate a NativeAppInstallAdMapper to map properties from the SASNativeAdElement
        // to the google native ad
        final NativeAppInstallAdMapper nativeAdMapper = new NativeAppInstallAdMapper() {
            @Override
            public void trackViews(View view, Map<String, View> map, Map<String, View> map1) {
                                    /* trying to get only clickable view te refine click behavior doesn't work as expected :
                                       the only clickable View passed is the ad choices view, whereas we expect the call to action button...*/
//                                    Collection<View> clickableViews = map.values();
//                                    if (clickableViews.size() > 0) {
//                                        nativeAdElement.registerView(view,clickableViews.toArray(new View[0]));
//                                    } else {
//                                        nativeAdElement.registerView(view);
//                                    }

                // ... so register the whole view
                nativeAdElement.registerView(view);
            }

            @Override
            public void untrackView(View view) {
                nativeAdElement.unregisterView(view);
            }

        };

        nativeAdMapper.setOverrideClickHandling(true);
        nativeAdMapper.setHeadline(nativeAdElement.getTitle());
        nativeAdMapper.setBody(nativeAdElement.getBody());
        nativeAdMapper.setCallToAction(nativeAdElement.getCalltoAction());

        // Set native ad icon if available
        if (nativeAdElement.getIcon() != null) {
            NativeAd.Image icon = getNativeAdImage(nativeAdElement.getIcon(), nativeMediationAdRequest);
            nativeAdMapper.setIcon(icon);
        }

        // set native ad cover if available
        if (nativeAdElement.getCoverImage() != null) {
            ArrayList<NativeAd.Image> imageList = new ArrayList<>();
            imageList.add(getNativeAdImage(nativeAdElement.getCoverImage(), nativeMediationAdRequest));
            nativeAdMapper.setImages(imageList);
        }

        // set star rating
        nativeAdMapper.setStarRating((double) nativeAdElement.getRating());

        // add an ad choices view
        SASAdChoicesView adChoicesView = new SASAdChoicesView(context);
        int size = SASUtil.getDimensionInPixels(20, adChoicesView.getResources());
        adChoicesView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        adChoicesView.setNativeAdElement(nativeAdElement);
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(adChoicesView);
        nativeAdMapper.setAdChoicesContent(frameLayout);

        handler.post(new Runnable() {
            @Override
            public void run() {

                // set the MediaView from Smart SDK, if any (NOT WORKING AS IT SEEMS)
                if (nativeAdElement.getMediaElement() != null) {
                    SASNativeAdMediaView mediaView = new SASNativeAdMediaView(context);
                    mediaView.setNativeAdElement(nativeAdElement);
                    mediaView.setBackgroundColor(Color.RED);

                    mediaView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    mediaView.setEnforceAspectRatio(true);

                    nativeAdMapper.setMediaView(mediaView);
                    nativeAdMapper.setHasVideoContent(true);
                }

                // notify Google tha a native ad was loaded
                customEventNativeListener.onAdLoaded(nativeAdMapper);
            }
        });
    }

    /**
     * Creates a {@link UnifiedNativeAdMapper} for the Smart native ad and pass it to Google SDK
     */
    private void processUnifiedNativeAdRequest(final SASNativeAdElement nativeAdElement,
                                               final CustomEventNativeListener customEventNativeListener,
                                               NativeMediationAdRequest nativeMediationAdRequest,
                                               final Context context) {

        // instantiate a UnifiedNativeAdMapper to map properties from the SASNativeAdElement
        // to the google native ad
        final UnifiedNativeAdMapper nativeAdMapper = new UnifiedNativeAdMapper() {
            @Override
            public void trackViews(View view, Map<String, View> map, Map<String, View> map1) {
                                    /* trying to get only clickable view te refine click behavior doesn't work as expected :
                                       the only clickable View passed is the ad choices view, whereas we expect the call to action button...*/
//                                    Collection<View> clickableViews = map.values();
//                                    if (clickableViews.size() > 0) {
//                                        nativeAdElement.registerView(view,clickableViews.toArray(new View[0]));
//                                    } else {
//                                        nativeAdElement.registerView(view);
//                                    }

                // ... so register the whole view
                nativeAdElement.registerView(view);
            }

            @Override
            public void untrackView(View view) {
                nativeAdElement.unregisterView(view);
            }
        };

        nativeAdMapper.setHeadline(nativeAdElement.getTitle());
        nativeAdMapper.setBody(nativeAdElement.getBody());
        nativeAdMapper.setCallToAction(nativeAdElement.getCalltoAction());
        nativeAdMapper.setAdvertiser("Smart AdServer");

        // Set native ad icon if available
        if (nativeAdElement.getIcon() != null) {
            NativeAd.Image icon = getNativeAdImage(nativeAdElement.getIcon(), nativeMediationAdRequest);
            nativeAdMapper.setIcon(icon);
        }

        // set native ad cover if available
        if (nativeAdElement.getCoverImage() != null) {
            ArrayList<NativeAd.Image> imageList = new ArrayList<>();
            imageList.add(getNativeAdImage(nativeAdElement.getCoverImage(), nativeMediationAdRequest));
            nativeAdMapper.setImages(imageList);
        }

        // set star rating
        nativeAdMapper.setStarRating((double) nativeAdElement.getRating());

        // add an ad choices view
        SASAdChoicesView adChoicesView = new SASAdChoicesView(context);
        int size = SASUtil.getDimensionInPixels(20, adChoicesView.getResources());
        adChoicesView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        adChoicesView.setNativeAdElement(nativeAdElement);
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(adChoicesView);
        nativeAdMapper.setAdChoicesContent(frameLayout);

        handler.post(new Runnable() {
            @Override
            public void run() {

                // set the MediaView from Smart SDK, if any (NOT WORKING AS IT SEEMS)
                if (nativeAdElement.getMediaElement() != null) {
                    SASNativeAdMediaView mediaView = new SASNativeAdMediaView(context);
                    mediaView.setNativeAdElement(nativeAdElement);
                    mediaView.setBackgroundColor(Color.RED);

                    mediaView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    mediaView.setEnforceAspectRatio(true);

                    nativeAdMapper.setMediaView(mediaView);
                    nativeAdMapper.setHasVideoContent(true);
                }

                // notify Google tha a native ad was loaded
                customEventNativeListener.onAdLoaded(nativeAdMapper);
            }
        });
    }

    /**
     * Returns a Google Mobile Ad NativeAd.Image object from a Smart SASNativeAdElement.ImageElement object
     *
     * @param imageElement
     * @param nativeMediationAdRequest
     * @return
     */
    @Nullable
    private static NativeAd.Image getNativeAdImage(@NonNull final SASNativeAdElement.ImageElement imageElement, NativeMediationAdRequest nativeMediationAdRequest) {

        // should we download images ?
        boolean downloadImages = true;
        NativeAdOptions nativeAdOptions = nativeMediationAdRequest.getNativeAdOptions();
        if (nativeAdOptions != null) {
            downloadImages = !nativeMediationAdRequest.getNativeAdOptions().shouldReturnUrlsForImageAssets();
        }

        final String imageUrl = imageElement.getUrl();

        // create drawable containing image
        Drawable imageDrawable = null;
        if (imageUrl != null && imageUrl.length() > 0 && downloadImages) {
            // try to retrieve contents at url
            try {
                InputStream is = (InputStream) new URL(imageUrl).getContent();
                imageDrawable = BitmapDrawable.createFromStream(is, imageUrl);
            } catch (IOException e) {
            }
        }

        // now create google NativeAd image
        final Drawable drawable = imageDrawable;

        NativeAd.Image nativeAdImage = new NativeAd.Image() {
            @Override
            public Drawable getDrawable() {
                return drawable;
            }

            @Override
            public Uri getUri() {
                return Uri.parse(imageUrl);
            }

            @Override
            public double getScale() {
                return 1.0;
            }
        };

        return nativeAdImage;

    }


    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to the SASNativeAdManager instance
     */
    @Override
    public void onDestroy() {
        if (sasNativeAdManager != null) {
            sasNativeAdManager.onDestroy();
            sasNativeAdManager = null;
        }
    }

    /**
     * Implementation of CustomEventNative interface.
     * Forwards the onPause() call to SASNativeAdManager instance
     */
    @Override
    public void onPause() {
        // not supported by SASNativeAdManager
    }

    /**
     * Implementation of CustomEventNative interface.
     * Forwards the onResume() call to the SASNativeAdManager instance
     */
    @Override
    public void onResume() {
        // not supported by SASNativeAdManager
    }

}
