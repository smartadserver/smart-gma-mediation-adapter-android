package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.VersionInfo;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.util.SASConfiguration;
import com.smartadserver.android.library.util.SASLibraryInfo;

import java.util.Set;

/**
 * Base class of all other SASCustomEvent adapters. Handle the SDK configuration.
 */
public class SASGMACustomEventUtil {

    public final static String MEDIATION_EXTRAS_SMART_KEYWORD_TARGETING_KEY = "smart_keyword_targeting";

    // version info needed by google classes, init in a static block
    private static VersionInfo adapterVersionInfo = null;
    private static VersionInfo sdkVersionInfo = null;

    static {
        adapterVersionInfo = new VersionInfo(1,0,0);

        int majorVersion = 0;
        int minorVersion = 0;
        int microVersion = 0;

        String[] versionInfo = SASLibraryInfo.getSharedInstance().getVersion().split(".");

        // we expect 3 tokens, no more, no less
        if (versionInfo.length >= 1) {
            try {
                majorVersion = Integer.parseInt(versionInfo[0]);
            } catch (NumberFormatException ignored) {}
        }
        if (versionInfo.length >= 2) {
            try {
                minorVersion = Integer.parseInt(versionInfo[1]);
            } catch (NumberFormatException ignored) {}
        }
        if (versionInfo.length >= 3) {
            try {
                microVersion = Integer.parseInt(versionInfo[2]);
            } catch (NumberFormatException ignored) {}
        }

        sdkVersionInfo = new VersionInfo(majorVersion, minorVersion, microVersion);
    }

    /**
     * Configure the Smart Display SDK if needed and
     *
     * @param context         The application context.
     * @param placementString The Smart placement String.
     * @param mediationExtras a Bundle containing mediation extra parameters as passed to the Google AdRequest in the application
     * @return a valid SASAdPlacement, or null if the SDK can not be configured or if the placement string is wrongly set.
     */
    @Nullable
    public static SASAdPlacement configureSDKAndGetAdPlacement(@NonNull Context context,
                                                               @NonNull String placementString,
                                                               @Nullable Bundle mediationExtras) {

        int siteId = -1;
        String pageId = "";
        int formatId = -1;
        String targeting = "";

        // tokenize placement string and fill adPlacement;
        String[] ids = placementString.split("/");
        if (ids.length >= 3) {
            try {
                siteId = Integer.parseInt(ids[0].trim());
                pageId = ids[1].trim();
                formatId = Integer.parseInt(ids[2].trim());
            } catch (Exception e) {
                // invalid placement, return null
                return null;
            }
        }

        // configure the Smart Ad Server SDK if necessary
        if (!SASConfiguration.getSharedInstance().isConfigured()) {
            try {
                if (siteId >= 1) {
                    SASConfiguration.getSharedInstance().configure(context, siteId);
                } else {
                    return null;
                }
            } catch (SASConfiguration.ConfigurationException e) {
                e.printStackTrace();
                return null;
            }
        }

        // extract custom targeting from mediation extras
        if (mediationExtras != null) {
            String keywordTargeting = mediationExtras.getString(MEDIATION_EXTRAS_SMART_KEYWORD_TARGETING_KEY);
            if (keywordTargeting != null) {
                targeting = keywordTargeting;
            }
        }

        return new SASAdPlacement(siteId, pageId, formatId, targeting);
    }



    @NonNull
    public static VersionInfo getVersionInfo() {
        return adapterVersionInfo;
    }

    @NonNull
    public static VersionInfo getSDKVersionInfo() {
        return sdkVersionInfo;
    }
}
