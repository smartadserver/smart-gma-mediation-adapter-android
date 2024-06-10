package com.equativ.displaysdk.mediation

import android.content.Context
import android.os.Bundle
import com.equativ.displaysdk.model.SASAdPlacement
import com.equativ.displaysdk.util.SASConfiguration
import com.equativ.displaysdk.util.SASLibraryInfo
import com.equativ.displaysdk.util.SASSecondaryImplementationInfo
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VersionInfo

/**
 * Base class of all other Equativ mediation adapter classes. Handles the SDK configuration.
 */
object SASGMAUtils {
    private const val ADAPTER_VERSION = "2.0"

    @JvmField
    val MEDIATION_EXTRAS_EQUATIV_KEYWORD_TARGETING_KEY: String = "equativ_keyword_targeting"

    // version info needed by google classes, init in a static block
    private var adapterVersionInfo: VersionInfo? = null
    private var sdkVersionInfo: VersionInfo? = null

    /**
     * Configure the Equativ Display SDK if needed and
     *
     * @param context         The application context.
     * @param placementString The Equativ placement String.
     * @param mediationExtras a Bundle containing mediation extra parameters as passed to the Google AdRequest in the application
     * @return a valid SASAdPlacement, or null if the SDK can not be configured or if the placement string is wrongly set.
     */
    fun configureSDKAndGetAdPlacement(
        context: Context,
        placementString: String,
        mediationExtras: Bundle?
    ): SASAdPlacement? {

        // tokenize placement string and fill adPlacement;
        val ids = placementString.split("/")
        try {
            val siteId = ids[0].trim().toLong()
            val pageId = ids[1].trim().toLong()
            val formatId = ids[2].trim().toLong()

            // configure the Equativ Display SDK if necessary
            if (!SASConfiguration.isConfigured) {
                try {
                    if (siteId >= 1) {
                        SASConfiguration.configure(context)
                        SASConfiguration.secondaryImplementationInfo = SASSecondaryImplementationInfo(
                            "GoogleMobileAds",
                            MobileAds.getVersion().toString(),
                            ADAPTER_VERSION
                        )
                    } else {
                        return null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }

            // extract custom targeting from mediation extras
            val targeting = mediationExtras?.getString(MEDIATION_EXTRAS_EQUATIV_KEYWORD_TARGETING_KEY)

            return SASAdPlacement(siteId, pageId, formatId, targeting)

        } catch (e: Exception) {
            // invalid placement, return null
            return null
        }
    }

    val versionInfo: VersionInfo
        get() {
            return (adapterVersionInfo)!!
        }
    val SDKVersionInfo: VersionInfo
        get() {
            return (sdkVersionInfo)!!
        }

    init {
        // adapters version info
        adapterVersionInfo = VersionInfo(1, 0, 0)

        //SDK version info
        val versionInfo = SASLibraryInfo.version.split(".")

        // we expect 3 tokens, no more, no lessÒ
        val majorVersion = versionInfo.getOrNull(0)?.toIntOrNull() ?: 0
        val minorVersion = versionInfo.getOrNull(1)?.toIntOrNull() ?: 0
        val microVersion = versionInfo.getOrNull(1)?.toIntOrNull() ?: 0

        sdkVersionInfo = VersionInfo(majorVersion, minorVersion, microVersion)
    }
}