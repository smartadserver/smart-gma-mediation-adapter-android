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

    // These adapters version
    private const val ADAPTER_VERSION_MAJOR = 2
    private const val ADAPTER_VERSION_MINOR = 0
    private const val ADAPTER_VERSION_MICRO = 0

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
    fun getAdPlacement(
        placementString: String,
        mediationExtras: Bundle?
    ): SASAdPlacement? {

        // tokenize placement string and fill adPlacement;
        val ids = placementString.split("/")
        try {
            val siteId = ids[0].trim().toLong()
            val pageId = ids[1].trim().toLong()
            val formatId = ids[2].trim().toLong()

            // extract custom targeting from mediation extras
            val targeting = mediationExtras?.getString(MEDIATION_EXTRAS_EQUATIV_KEYWORD_TARGETING_KEY)

            return SASAdPlacement(siteId, pageId, formatId, targeting)

        } catch (e: Exception) {
            // invalid placement, return null
            return null
        }
    }

    /**
     * Performs all needed configuration steps for the Equativ SDK
     */
    fun configureEquativSDKIfNeeded(context: Context) {
        if (!SASConfiguration.isConfigured) {
            try {
                SASConfiguration.configure(context)
                SASConfiguration.secondaryImplementationInfo = SASSecondaryImplementationInfo(
                    "GoogleMobileAds",
                    MobileAds.getVersion().toString(),
                    "$ADAPTER_VERSION_MAJOR.$ADAPTER_VERSION_MINOR.$ADAPTER_VERSION_MICRO"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        adapterVersionInfo = VersionInfo(
            ADAPTER_VERSION_MAJOR,
            ADAPTER_VERSION_MINOR,
            ADAPTER_VERSION_MICRO
        )

        //SDK version info
        val versionInfo = SASLibraryInfo.version.split(".")

        // we expect 3 tokens, no more, no less
        val majorVersion = versionInfo.getOrNull(0)?.toIntOrNull() ?: 0
        val minorVersion = versionInfo.getOrNull(1)?.toIntOrNull() ?: 0
        val microVersion = versionInfo.getOrNull(2)?.toIntOrNull() ?: 0

        sdkVersionInfo = VersionInfo(majorVersion, minorVersion, microVersion)
    }
}