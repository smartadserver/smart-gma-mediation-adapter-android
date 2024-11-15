package com.equativ.displaysdk.mediation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.equativ.displaysdk.ad.nativead.SASNativeAdView
import com.equativ.displaysdk.ad.nativead.SASNativeAdViewBinder
import com.equativ.displaysdk.exception.SASException
import com.equativ.displaysdk.model.SASAdInfo
import com.equativ.displaysdk.model.SASNativeAdAssets
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.URL

/**
 * Class that handles Google Mobile Ads mediation native ad calls to Equativ Display SDK.
 */
class SASGMAMediationNativeAdapter : Adapter() {

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

            // Nothing more to do here, the Equativ Display SDK does not require initialization at this stage
            initializationCompleteCallback.onInitializationSucceeded()
        }
    }

    override fun loadNativeAdMapper(
        mediationAdConfiguration: MediationNativeAdConfiguration,
        mediationAdLoadCallback: MediationAdLoadCallback<NativeAdMapper, MediationNativeAdCallback>
    ) {

        Log.d("SASGMAMediationNativeAdapter", "loadNativeAdMapper for SASGMAMediationNativeAdapter")

        // Get the Equativ placement string parameter
        val equativPlacementString = mediationAdConfiguration.serverParameters.getString("parameter") ?: ""

        var mediationNativeAdCallback: MediationNativeAdCallback? = null

        // Configure the Equativ Display SDK and retrieve the ad placement.
        applicationContextWeakReference?.get()?.let { context ->
            val adPlacement = SASGMAUtils.configureSDKAndGetAdPlacement(
                context,
                equativPlacementString, mediationAdConfiguration.mediationExtras
            )

            adPlacement?.let {

                val nativeAdView = SASNativeAdView(context).also { nativeAdView ->

                    // set the native ad listener to process native ad call outcome
                    nativeAdView.nativeAdListener = object : SASNativeAdView.NativeAdListener {

                        override fun onNativeAdClicked() {
                            mediationNativeAdCallback?.reportAdClicked()
                            mediationNativeAdCallback?.onAdOpened()
                            mediationNativeAdCallback?.onAdLeftApplication()
                        }

                        override fun onNativeAdFailedToLoad(exception: SASException) {
                            CoroutineScope(Dispatchers.Main).launch {
                                var errorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR
                                var errorMessage = exception.message ?: ""

                                when (exception.type) {
                                    SASException.Type.NO_AD -> {
                                        // no ad to deliver
                                        errorCode = AdRequest.ERROR_CODE_NO_FILL
                                        errorMessage = "No ad to deliver"
                                    }

                                    SASException.Type.TIMEOUT -> {
                                        // ad request timeout translates to admob network error
                                        errorCode = AdRequest.ERROR_CODE_NETWORK_ERROR
                                        errorMessage = "Timeout while waiting ad call response"
                                    }

                                    else -> {
                                        // keep message and code init values
                                    }
                                }

                                mediationAdLoadCallback.onFailure(
                                    AdError(errorCode, errorMessage, AdError.UNDEFINED_DOMAIN)
                                )
                            }
                        }

                        override fun onNativeAdLoaded(
                            adInfo: SASAdInfo,
                            nativeAdAssets: SASNativeAdAssets
                        ) {
                            // convert Equativ native ad to a Google NativeAd via a NativeAdMapper instance
                            val nativeAdMapper = createNativeAdMapper(
                                nativeAdAssets,
                                mediationAdConfiguration.nativeAdOptions,
                                nativeAdView,
                                context
                            )

                            // notify Google SDK of native ad loading success, and get corresponding callback to
                            // forward native events
                            mediationNativeAdCallback = mediationAdLoadCallback.onSuccess(nativeAdMapper)
                        }

                        override fun onNativeAdRequestClose() {
                            // not supported
                        }
                    }
                }

                // trigger the native ad call
                nativeAdView.loadAd(adPlacement)
            }?: run {
                // incorrect Equativ placement : exit in error
                mediationAdLoadCallback.onFailure(
                    AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                        "Invalid Equativ placement IDs. Please check server parameters string", AdError.UNDEFINED_DOMAIN))

            }
        } ?: run {
            mediationAdLoadCallback.onFailure(
                AdError(AdRequest.ERROR_CODE_INVALID_REQUEST,
                    "Context is null", AdError.UNDEFINED_DOMAIN))
        }
    }

    /**
     * Creates a google NativeAdMapper for the Equativ native ad and pass it to Google SDK
     */
    private fun createNativeAdMapper(
        nativeAdAssets: SASNativeAdAssets,
        nativeAdOptions: NativeAdOptions,
        nativeAdView: SASNativeAdView,
        context: Context
    ) : NativeAdMapper {

        // instantiate a NativeAdMapper to map properties from the SASNativeAdAssets
        // to the google native ad
        val nativeAdMapper: NativeAdMapper = object : NativeAdMapper() {
            override fun trackViews(view: View, map: MutableMap<String, View>, map1: Map<String, View>) {

                // tell the nativeAdView about the actual view rendering the native ad, that needs to be tracked.
                nativeAdView.trackMediationView(view)

                // add a proxy click listener on all specified clickable views to forward click to
                // the SASNativeAdView
                val proxyClickListener = OnClickListener { v ->
                    view.performClick()
                }

                if (map.values.isNotEmpty()) {
                    // add a proxy click listener on all specified clickable views to forward click to
                    // the SASNativeAdView
                    val clickableViewsIterator: MutableIterator<View> = map.values.iterator()
                    while (clickableViewsIterator.hasNext()) {
                        clickableViewsIterator.next().setOnClickListener(proxyClickListener)
                    }
                }
            }

            override fun untrackView(view: View) {
                nativeAdView.onDestroy()
            }
        }
        nativeAdMapper.headline = nativeAdAssets.title ?: "headline"
        nativeAdMapper.body = nativeAdAssets.body ?: "body"
        nativeAdMapper.callToAction = nativeAdAssets.callToAction ?: "callToAction"
        // set star rating if available
        nativeAdAssets.rating?.let { nativeAdMapper.starRating = it }
        nativeAdAssets.advertiser?.let { nativeAdMapper.advertiser = it }

        // Set native ad icon if available
        nativeAdAssets.iconImage?.let { iconImage ->
            runBlocking {
                getNativeAdImage(context, iconImage, nativeAdOptions)?.let {
                    nativeAdMapper.icon = it
                }
            }
        }

        // set native ad cover if available
        nativeAdAssets.mainView?.let { mainImage ->
            runBlocking {
                getNativeAdImage(context,mainImage, nativeAdOptions)?.let {
                    nativeAdMapper.images = ArrayList<NativeAd.Image?>().apply {
                        add(it)
                    }
                }
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
         * Returns a Google Mobile Ad NativeAd.Image object from an Equativ SASNativeAdAssets.ViewAsset object
         */
        private suspend fun getNativeAdImage(
            context: Context,
            viewAsset: SASNativeAdAssets.ViewAsset,
            nativeAdOptions: NativeAdOptions): NativeAd.Image?
        {

            // fetch ViewAsset url. We deliberately ignore ViewAsset view, as this is not supported by GMA
            val imageUrl = viewAsset.url

            // create drawable containing image
            var imageDrawable: Drawable? = null

            // if options specify that we should only return urls for images
            if (nativeAdOptions.shouldReturnUrlsForImageAssets()) {
                imageDrawable = ShapeDrawable(RectShape()) // dummy drawable
            } else if (!imageUrl.isNullOrEmpty()) {

                // try to retrieve contents at url
                try {
                    imageDrawable = withContext(Dispatchers.IO) {
                        val inputStream: InputStream = URL(imageUrl).content as InputStream

                        val options: BitmapFactory.Options = BitmapFactory.Options().apply {
                            // To avoid java.lang.OutOfMemory exceptions with too big bitmap image for the device,
                            // we calculate the optimised inSampleSize before decoding the bitmap
                            val width = viewAsset.width
                            val height = viewAsset.height
                            if (width != null && height != null) {
                                inSampleSize = calculateInSampleSize(context, width, height)
                            }
                        }

                        val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream, null, options )
                        bitmap?.let { BitmapDrawable(context.resources, it) }
                    }

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