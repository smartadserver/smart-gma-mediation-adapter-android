package com.smartadserver.android.library.mediation

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VersionInfo
import com.smartadserver.android.library.model.SASAdPlacement
import com.smartadserver.android.library.util.SASConfiguration
import com.smartadserver.android.library.util.SASLibraryInfo
import com.smartadserver.android.library.util.SASSecondaryImplementationInfo

/**
 * Base class of all other Smart mediation adapter classes. Handles the SDK configuration.
 */
object SASGMAUtils {

    // These adapters version
    private const val ADAPTER_VERSION_MAJOR = 1
    private const val ADAPTER_VERSION_MINOR = 1
    private const val ADAPTER_VERSION_MICRO = 1

    @JvmField
    val MEDIATION_EXTRAS_SMART_KEYWORD_TARGETING_KEY: String = "smart_keyword_targeting"

    // version info needed by google classes, init in a static block
    private var adapterVersionInfo: VersionInfo? = null
    private var sdkVersionInfo: VersionInfo? = null

    /**
     * Configure the Smart Display SDK if needed and
     *
     * @param context         The application context.
     * @param placementString The Smart placement String.
     * @param mediationExtras a Bundle containing mediation extra parameters as passed to the Google AdRequest in the application
     * @return a valid SASAdPlacement, or null if the SDK can not be configured or if the placement string is wrongly set.
     */
    fun configureSDKAndGetAdPlacement(context: Context,
                                      placementString: String,
                                      mediationExtras: Bundle?): SASAdPlacement? {

        // tokenize placement string and fill adPlacement;
        val ids = placementString.split("/")
        try {
            val siteId = ids[0].trim().toInt()
            val pageId = ids[1]
            val formatId = ids[2].trim().toInt()

            // configure the Smart Ad Server SDK if necessary
            if (!SASConfiguration.getSharedInstance().isConfigured) {
                try {
                    if (siteId >= 1) {
                        SASConfiguration.getSharedInstance().configure(context, siteId)
                        SASConfiguration.getSharedInstance().secondaryImplementationInfo = SASSecondaryImplementationInfo(
                            "GoogleMobileAds",
                            MobileAds.getVersion().toString(),
                            "$ADAPTER_VERSION_MAJOR.$ADAPTER_VERSION_MINOR.$ADAPTER_VERSION_MICRO"
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
            val targeting = mediationExtras?.getString(MEDIATION_EXTRAS_SMART_KEYWORD_TARGETING_KEY)

            return SASAdPlacement(siteId.toLong(), pageId, formatId.toLong(), targeting)

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
        adapterVersionInfo = VersionInfo(
            ADAPTER_VERSION_MAJOR,
            ADAPTER_VERSION_MINOR,
            ADAPTER_VERSION_MICRO
        )

        //SDK version info
        val versionInfo = SASLibraryInfo.getSharedInstance().version.split(".")

        // we expect 3 tokens, no more, no less
        val majorVersion = versionInfo.getOrNull(0)?.toIntOrNull() ?: 0
        val minorVersion = versionInfo.getOrNull(1)?.toIntOrNull() ?: 0
        val microVersion = versionInfo.getOrNull(2)?.toIntOrNull() ?: 0

        sdkVersionInfo = VersionInfo(majorVersion, minorVersion, microVersion)
    }
}