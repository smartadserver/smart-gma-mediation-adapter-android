package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.formats.MediaView;
import com.google.android.gms.ads.formats.NativeAd;
import com.google.android.gms.ads.formats.NativeAdOptions;
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
import java.util.Iterator;
import java.util.Map;

/**
 * Class that handles an adMob mediation banner ad call to Smart AdServer SDK.
 */
public class SASGMACustomEventNative implements CustomEventNative {

    // the Smart native ad manager that will handle the mediation ad call
    private SASNativeAdManager sasNativeAdManager;

    // get a Handler on the main thread to execute code on this thread
    private Handler handler = SASUtil.getMainLooperHandler();


    /**
     * Implementation of CustomEventNative interface.
     * Delegates the native ad call to the Smart AdServer SDK
     */
    @Override
    public void requestNativeAd(final @NonNull Context context,
                                final @NonNull CustomEventNativeListener customEventNativeListener,
                                @Nullable String s, final @NonNull NativeMediationAdRequest nativeMediationAdRequest,
                                @Nullable Bundle bundle) {

        // get the smart placement object
        if (s == null) {
            s = "";
        }

        // Configure the Smart Display SDK and retrieve the ad placement.
        SASAdPlacement adPlacement = SASGMACustomEventUtil.configureSDKAndGetAdPlacement(context, s, bundle);

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
            public void onNativeAdLoaded(@NonNull final SASNativeAdElement nativeAdElement) {

                if (nativeMediationAdRequest.isUnifiedNativeAdRequested()) {
                    // convert Smart native ad to a Google UnifiedNativeAd
                    processUnifiedNativeAdRequest(nativeAdElement, customEventNativeListener, nativeMediationAdRequest, context);

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
                    public boolean handleClick(String clickUrl, @NonNull SASNativeAdElement nativeAdElement) {
                        customEventNativeListener.onAdClicked();
                        customEventNativeListener.onAdOpened();
                        customEventNativeListener.onAdLeftApplication();
                        return false;
                    }
                });
            }

            @Override
            public void onNativeAdFailedToLoad(@NonNull final Exception e) {

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
            public void trackViews(@NonNull View view, Map<String, View> map, @NonNull Map<String, View> map1) {

                // If there is a video, we need to filter out any MediaView from the list of clickable views, as we want to
                // preserve the click to expand behaviour of the Smart native media view
                Iterator<View> clickableViewsIterator = map.values().iterator();
                while (clickableViewsIterator.hasNext()) {
                    View v = clickableViewsIterator.next();
                    if (v instanceof MediaView && hasVideoContent()) {
                        clickableViewsIterator.remove();
                    }
                }

                if (map.values().size() > 0) {
                    // register the root view with only specified clickable views (minus any google MediaView)
                    nativeAdElement.registerView(view,map.values().toArray(new View[0]));
                } else {
                    // register the whole root view as clickable
                    nativeAdElement.registerView(view);
                }
            }

            @Override
            public void untrackView(@NonNull View view) {
                nativeAdElement.unregisterView(view);
            }
        };

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
            @NonNull
            @Override
            public Drawable getDrawable() {
                return drawable;
            }

            @NonNull
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
