package com.smartadserver.android.library.mediation

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.mediation.*
import com.smartadserver.android.coresdk.util.SCSConfiguration
import com.smartadserver.android.library.model.SASAdPlacement
import com.smartadserver.android.library.util.SASConfiguration
import com.smartadserver.android.library.util.SASLibraryInfo

/**
 * Base class of all other SASCustomEvent adapters. Handle the SDK configuration.
 */
object SASGMACustomEventUtil {
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
        var siteId: Int = -1
        var pageId: String = ""
        var formatId: Int = -1
        var targeting: String = ""

        // tokenize placement string and fill adPlacement;
        val ids = placementString.split("/")
        try {
            siteId = ids.get(0).trim().toInt()
            pageId = ids.get(1)
            formatId = ids.get(2).trim().toInt()
        } catch (e: Exception) {
            // invalid placement, return null
            return null
        }

        // configure the Smart Ad Server SDK if necessary
        if (!SASConfiguration.getSharedInstance().isConfigured()) {
            try {
                if (siteId >= 1) {
                    SASConfiguration.getSharedInstance().configure(context, siteId)
                    SASConfiguration.getSharedInstance().isPrimarySdk = false
                } else {
                    return null
                }
            } catch (e: SCSConfiguration.ConfigurationException) {
                e.printStackTrace()
                return null
            }
        }

        // extract custom targeting from mediation extras
        if (mediationExtras != null) {
            val keywordTargeting: String? = mediationExtras.getString(MEDIATION_EXTRAS_SMART_KEYWORD_TARGETING_KEY)
            if (keywordTargeting != null) {
                targeting = keywordTargeting
            }
        }
        return SASAdPlacement(siteId.toLong(), pageId, formatId.toLong(), targeting)
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
        val versionInfo = SASLibraryInfo.getSharedInstance().getVersion().split(".")

        // we expect 3 tokens, no more, no less
        val majorVersion = versionInfo.getOrNull(0)?.toIntOrNull() ?: 0
        val minorVersion = versionInfo.getOrNull(1)?.toIntOrNull() ?: 0
        val microVersion = versionInfo.getOrNull(1)?.toIntOrNull() ?: 0

        sdkVersionInfo = VersionInfo(majorVersion, minorVersion, microVersion)
    }
}