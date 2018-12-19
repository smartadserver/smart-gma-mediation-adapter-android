package com.smartadserver.android.library.mediation;

import android.content.Context;

import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.util.SASConfiguration;

import java.util.Set;

/**
 * Base class of all other SASCustomEvent adapters. Handle the SDK configuration.
 */
public class SASGMACustomEventBase {

    // TODO replace the base url with your own.
    private static final String SMART_BASE_URL = "https://mobile.smartadserver.com";

    /**
     * Configure the Smart Display SDK if needed and
     *
     * @param context         The application context.
     * @param placementString The Smart placement String.
     * @param adRequest       The mediation ad request.
     * @return a valid SASAdPlacement, or null if the SDK can not be configured or if the placement string is wrongly set.
     */
    public static SASAdPlacement configureSDKAndGetAdPlacement(Context context, String placementString, MediationAdRequest adRequest) {

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
                    SASConfiguration.getSharedInstance().configure(context, siteId, SMART_BASE_URL);
                } else {
                    return null;
                }
            } catch (SASConfiguration.ConfigurationException e) {
                e.printStackTrace();
                return null;
            }
        }

        // extract keywords and concatenate them using semicolon as separator
        Set<String> keywordSet = adRequest.getKeywords();
        if (keywordSet != null) {
            for (String keyword : keywordSet) {
                if (targeting.length() > 0) {
                    keyword = ";".concat(keyword);
                }
                targeting = targeting.concat(keyword);
            }
        }

        return new SASAdPlacement(siteId, pageId, formatId, targeting);
    }
}
