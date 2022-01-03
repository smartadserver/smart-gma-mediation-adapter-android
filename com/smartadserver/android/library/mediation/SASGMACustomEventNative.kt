package com.smartadserver.android.library.mediation

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.formats.MediaView
import com.google.android.gms.ads.formats.NativeAd
import com.google.android.gms.ads.mediation.NativeMediationAdRequest
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper
import com.google.android.gms.ads.mediation.customevent.CustomEventNative
import com.google.android.gms.ads.mediation.customevent.CustomEventNativeListener
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASAdPlacement
import com.smartadserver.android.library.model.SASNativeAdElement
import com.smartadserver.android.library.model.SASNativeAdElement.ImageElement
import com.smartadserver.android.library.model.SASNativeAdManager
import com.smartadserver.android.library.model.SASNativeAdManager.NativeAdListener
import com.smartadserver.android.library.ui.SASAdChoicesView
import com.smartadserver.android.library.ui.SASNativeAdMediaView
import com.smartadserver.android.library.util.SASUtil
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlin.collections.ArrayList
import android.graphics.*
import android.view.WindowManager


/**
 * Class that handles an adMob mediation banner ad call to Smart AdServer SDK.
 */
class SASGMACustomEventNative : CustomEventNative {
    // the Smart native ad manager that will handle the mediation ad call
    private var sasNativeAdManager: SASNativeAdManager? = null

    // get a Handler on the main thread to execute code on this thread
    private val handler: Handler = SASUtil.getMainLooperHandler()

    /**
     * Implementation of CustomEventNative interface.
     * Delegates the native ad call to the Smart AdServer SDK
     */
    public override fun requestNativeAd(context: Context,
                                        customEventNativeListener: CustomEventNativeListener,
                                        s: String?, nativeMediationAdRequest: NativeMediationAdRequest,
                                        bundle: Bundle?) {

        // get the smart placement object
        val placementString = s ?: ""

        // Configure the Smart Display SDK and retrieve the ad placement.
        val adPlacement: SASAdPlacement? = SASGMACustomEventUtil.configureSDKAndGetAdPlacement(context, placementString, bundle)

        // test if the ad placement is valid
        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            customEventNativeListener.onAdFailedToLoad(AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Invalid Smart placement IDs. Please check server parameters string", AdError.UNDEFINED_DOMAIN))
            return
        }
        if (sasNativeAdManager != null) {
            // Quit if there is already a native ad being handled.
            return
        }

        // instantiate SASNativeAdManager that will perform the Smart ad call
        sasNativeAdManager = SASNativeAdManager(context, adPlacement)

        // set native ad listener
        sasNativeAdManager?.nativeAdListener = object : NativeAdListener {
            override fun onNativeAdLoaded(nativeAdElement: SASNativeAdElement) {
                if (nativeMediationAdRequest.isUnifiedNativeAdRequested) {
                    // convert Smart native ad to a Google UnifiedNativeAd
                    processUnifiedNativeAdRequest(nativeAdElement, customEventNativeListener, nativeMediationAdRequest, context)
                } else {
                    // notify Google of a NO fill, as the ad received is not compatible
                    handler.post { // return no ad
                        customEventNativeListener.onAdFailedToLoad(
                                AdError(AdRequest.ERROR_CODE_NO_FILL, "No ad to deliver", AdError.UNDEFINED_DOMAIN))
                    }
                }

                // install a click listener on the SASNativeAdElement to notify back the customEventNativeListener
                nativeAdElement.onClickListener = SASNativeAdElement.OnClickListener { _, _ ->
                    customEventNativeListener.onAdClicked()
                    customEventNativeListener.onAdOpened()
                    customEventNativeListener.onAdLeftApplication()
                }
            }

            override fun onNativeAdFailedToLoad(e: Exception) {
                handler.post {
                    var errorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR
                    var errorMessage = e.message ?: ""
                    if (e is SASNoAdToDeliverException) {
                        // no ad to deliver
                        errorCode = AdRequest.ERROR_CODE_NO_FILL
                        errorMessage = "No ad to deliver"
                    } else if (e is SASAdTimeoutException) {
                        // ad request timeout translates to admob network error
                        errorCode = AdRequest.ERROR_CODE_NETWORK_ERROR
                        errorMessage = "Timeout while waiting ad call response"
                    }
                    customEventNativeListener.onAdFailedToLoad(AdError(errorCode, errorMessage, AdError.UNDEFINED_DOMAIN))
                }
            }
        }

        // Now request ad for this SASNativeAdManager
        sasNativeAdManager?.loadNativeAd()
    }

    /**
     * Creates a [UnifiedNativeAdMapper] for the Smart native ad and pass it to Google SDK
     */
    private fun processUnifiedNativeAdRequest(nativeAdElement: SASNativeAdElement,
                                              customEventNativeListener: CustomEventNativeListener,
                                              nativeMediationAdRequest: NativeMediationAdRequest,
                                              context: Context) {

        // instantiate a UnifiedNativeAdMapper to map properties from the SASNativeAdElement
        // to the google native ad
        val nativeAdMapper: UnifiedNativeAdMapper = object : UnifiedNativeAdMapper() {
            override fun trackViews(view: View, map: MutableMap<String, View>, map1: Map<String, View>) {

                // If there is a video, we need to filter out any MediaView from the list of clickable views, as we want to
                // preserve the click to expand behaviour of the Smart native media view
                val clickableViewsIterator: MutableIterator<View> = map.values.iterator()
                while (clickableViewsIterator.hasNext()) {
                    val v: View = clickableViewsIterator.next()
                    if (v is MediaView && hasVideoContent()) {
                        clickableViewsIterator.remove()
                    }
                }
                if (map.values.isNotEmpty()) {
                    // register the root view with only specified clickable views (minus any google MediaView)
                    nativeAdElement.registerView(view, map.values.toTypedArray())
                } else {
                    // register the whole root view as clickable
                    nativeAdElement.registerView(view)
                }
            }

            override fun untrackView(view: View) {
                nativeAdElement.unregisterView(view)
            }
        }
        nativeAdMapper.headline = nativeAdElement.title ?: "headline"
        nativeAdMapper.body = nativeAdElement.body ?: "body"
        nativeAdMapper.callToAction = nativeAdElement.calltoAction ?: "callToAction"

        // Set native ad icon if available
        nativeAdElement.icon?.let { imageElement ->
            getNativeAdImage(context, imageElement, nativeMediationAdRequest)?.let {
                nativeAdMapper.icon = it
            }
        }

        // set native ad cover if available
        nativeAdElement.coverImage?.let { coverImage ->
            getNativeAdImage(context, coverImage, nativeMediationAdRequest)?.let {
                nativeAdMapper.images = ArrayList<NativeAd.Image?>().apply {
                    add(it)
                }
            }
        }

        // set star rating
        nativeAdMapper.starRating = nativeAdElement.rating.toDouble()

        // add an ad choices view
        val adChoicesView = SASAdChoicesView(context)
        val size: Int = SASUtil.getDimensionInPixels(20, adChoicesView.getResources())
        adChoicesView.layoutParams = ViewGroup.LayoutParams(size, size)
        adChoicesView.setNativeAdElement(nativeAdElement)
        val frameLayout = FrameLayout(context)
        frameLayout.addView(adChoicesView)
        nativeAdMapper.adChoicesContent = frameLayout
        handler.post { // set the MediaView from Smart SDK, if any (NOT WORKING AS IT SEEMS)
            if (nativeAdElement.mediaElement != null) {
                val mediaView: SASNativeAdMediaView = SASNativeAdMediaView(context)
                mediaView.nativeAdElement = nativeAdElement
                mediaView.setBackgroundColor(Color.RED)
                mediaView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                mediaView.isEnforceAspectRatio = true
                nativeAdMapper.setMediaView(mediaView)
                nativeAdMapper.setHasVideoContent(true)
            }

            // notify Google tha a native ad was loaded
            customEventNativeListener.onAdLoaded(nativeAdMapper)
        }
    }

    /**
     * Implementation of CustomEventInterstitial interface.
     * Forwards the onDestroy() call to the SASNativeAdManager instance
     */
    override fun onDestroy() {
        sasNativeAdManager?.onDestroy()
        sasNativeAdManager = null
    }

    /**
     * Implementation of CustomEventNative interface.
     * Forwards the onPause() call to SASNativeAdManager instance
     */
    override fun onPause() {
        // not supported by SASNativeAdManager
    }

    /**
     * Implementation of CustomEventNative interface.
     * Forwards the onResume() call to the SASNativeAdManager instance
     */
    override fun onResume() {
        // not supported by SASNativeAdManager
    }

    companion object {

        /**
         * Returns optimized inSampleSize based on the given width / height
         *
         * @param context
         * @param width
         * @param height
         * @return
         */
        private fun calculateInSampleSize(context: Context, width: Int, height: Int): Int {
            // Get device size
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val maxSize = Point()
            display.getSize(maxSize)
            // Using the device size, we set the max width / height
            val maxWith: Int = maxSize.x
            val maxHeight: Int = maxSize.y

            var inSampleSize = 1

            if (height > maxHeight || width > maxWith) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the max height and width.
                while (halfHeight / inSampleSize >= maxHeight && halfWidth / inSampleSize >= maxWith) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }

        /**
         * Returns a Google Mobile Ad NativeAd.Image object from a Smart SASNativeAdElement.ImageElement object
         *
         * @param imageElement
         * @param nativeMediationAdRequest
         * @return
         */
        private fun getNativeAdImage(context: Context, imageElement: ImageElement, nativeMediationAdRequest: NativeMediationAdRequest): NativeAd.Image? {

            // should we download images ?
            val downloadImages = nativeMediationAdRequest.getNativeAdOptions()?.shouldReturnUrlsForImageAssets()?.not() ?: true

            val imageUrl = imageElement.url

            // create drawable containing image
            var imageDrawable: Drawable? = null
            if (imageUrl.isNotEmpty() && downloadImages) {
                // try to retrieve contents at url
                try {
                    val inputStream: InputStream = URL(imageUrl).content as InputStream

                    val options: BitmapFactory.Options = BitmapFactory.Options().apply {
                        // To avoid java.lang.OutOfMemory exceptions with too big bitmap image for the device,
                        // we calculate the optimised inSampleSize before decoding the bitmap
                        inSampleSize = calculateInSampleSize(context, imageElement.width, imageElement.height)
                    }

                    val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream, null, options )
                    imageDrawable = BitmapDrawable(context.resources, bitmap)
                } catch (e: IOException) {
                }
            }

            // now create google NativeAd image if drawable is not null
            return imageDrawable?.let {
                object : NativeAd.Image() {
                    override fun getDrawable(): Drawable {
                        return (it)
                    }

                    override fun getUri(): Uri {
                        return Uri.parse(imageUrl)
                    }

                    override fun getScale(): Double {
                        return 1.0
                    }
                }
            }
        }
    }
}