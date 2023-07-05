package com.smartadserver.android.library.mediation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.formats.NativeAd
import com.google.android.gms.ads.mediation.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.model.SASNativeAdElement
import com.smartadserver.android.library.model.SASNativeAdManager
import com.smartadserver.android.library.ui.SASAdChoicesView
import com.smartadserver.android.library.ui.SASNativeAdMediaView
import com.smartadserver.android.library.util.SASUtil
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.URL

/**
 * Class that handles Google Mobile Ads mediation native ad calls to Smart AdServer SDK.
 */
class SASGMAMediationNativeAdapter : Adapter() {

    // the Smart native ad manager that will handle the mediation ad call
    private var sasNativeAdManager: SASNativeAdManager? = null

    // get a Handler on the main thread to execute code on this thread
    private val handler: Handler = SASUtil.getMainLooperHandler()

    override fun getVersionInfo(): VersionInfo {
        return SASGMAUtils.versionInfo
    }
    override fun getSDKVersionInfo(): VersionInfo {
        return SASGMAUtils.SDKVersionInfo
    }
    override fun initialize(context: Context,
                            initializationCompleteCallback: InitializationCompleteCallback,
                            list: List<MediationConfiguration>) {
        if (applicationContextWeakReference == null) {
            applicationContextWeakReference = WeakReference<Context>(context.applicationContext)

            // Nothing more to do here, Smart banner does not require initialization
            initializationCompleteCallback.onInitializationSucceeded()
        }
    }


    override fun loadNativeAd(
        mediationAdConfiguration: MediationNativeAdConfiguration,
        mediationAdLoadCallback: MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback>
    ) {

        Log.d("SASGMAMediationNativeAdapter", "loadNativeAd for SASGMAMediationNativeAdapter")

        // Get the Smart placement string parameter
        val smartPlacementString = mediationAdConfiguration.serverParameters.getString("parameter") ?: ""

        // Configure the Smart Display SDK and retrieve the ad placement.
        applicationContextWeakReference?.get()?.let { context ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(
                context,
                smartPlacementString, mediationAdConfiguration.mediationExtras
            )

            adPlacement?.let {

                // clean up any previous SASNativeAdManager
                sasNativeAdManager?.onDestroy()

                // instantiate SASNativeAdManager that will perform the Smart ad call
                sasNativeAdManager = SASNativeAdManager(context, adPlacement).apply {
                    // set native ad listener
                    nativeAdListener = object : SASNativeAdManager.NativeAdListener {
                        override fun onNativeAdLoaded(nativeAdElement: SASNativeAdElement) {
                            // convert Smart native ad to a Google UnifiedNativeAd
                            val unifiedNativeAdMapper = createUnifiedNativeAdRequest(
                                nativeAdElement,
                                mediationAdConfiguration.nativeAdOptions,
                                context
                            )

                            // notify Google SDK of native ad loading success, and get corresponding callback to
                            // forward native events
                            val mediationNativeAdCallback = mediationAdLoadCallback.onSuccess(unifiedNativeAdMapper)

                            // install a click listener on the SASNativeAdElement to notify back the customEventNativeListener
                            nativeAdElement.onClickListener = SASNativeAdElement.OnClickListener { _, _ ->
                                mediationNativeAdCallback.reportAdClicked()
                                mediationNativeAdCallback.onAdOpened()
                                mediationNativeAdCallback.onAdLeftApplication()
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
                                mediationAdLoadCallback.onFailure(AdError(errorCode, errorMessage, AdError.UNDEFINED_DOMAIN))
                            }
                        }
                    }

                    // Now request ad for this SASNativeAdManager
                    loadNativeAd()
                }

            }?: run {
                // incorrect smart placement : exit in error
                mediationAdLoadCallback.onFailure(
                    AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                        "Invalid Smart placement IDs. Please check server parameters string", AdError.UNDEFINED_DOMAIN))

            }
        } ?: run {
            mediationAdLoadCallback.onFailure(
                AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Context is null", AdError.UNDEFINED_DOMAIN))
        }

    }

    /**
     * Creates a [UnifiedNativeAdMapper] for the Smart native ad and pass it to Google SDK
     */
    private fun createUnifiedNativeAdRequest(
        nativeAdElement: SASNativeAdElement,
        nativeAdOptions: NativeAdOptions,
        context: Context
    ) : UnifiedNativeAdMapper {

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
            getNativeAdImage(
                context,
                imageElement,
                nativeAdOptions
            )?.let {
                nativeAdMapper.icon = it
            }
        }

        // set native ad cover if available
        nativeAdElement.coverImage?.let { coverImage ->
            getNativeAdImage(context, coverImage, nativeAdOptions)
                ?.let {
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
        }

        return nativeAdMapper
    }

    companion object {
        private var applicationContextWeakReference: WeakReference<Context>? = null

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
        private fun getNativeAdImage(
            context: Context,
            imageElement: SASNativeAdElement.ImageElement,
            nativeAdOptions: NativeAdOptions): NativeAd.Image?
        {

            val imageUrl = imageElement.url

            // create drawable containing image
            var imageDrawable: Drawable? = null
            if (imageUrl.isNotEmpty() && !nativeAdOptions.shouldReturnUrlsForImageAssets()) {
                // try to retrieve contents at url
                try {
                    val inputStream: InputStream = URL(imageUrl).content as InputStream

                    val options: BitmapFactory.Options = BitmapFactory.Options().apply {
                        // To avoid java.lang.OutOfMemory exceptions with too big bitmap image for the device,
                        // we calculate the optimised inSampleSize before decoding the bitmap
                        inSampleSize = calculateInSampleSize(context, imageElement.width, imageElement.height)
                    }

                    val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream, null, options )
                    imageDrawable = bitmap?.let { BitmapDrawable(context.resources, it) }
                } catch (_: IOException) {
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