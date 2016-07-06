package com.smartadserver.android.library.mediation;

import com.google.android.gms.ads.mediation.MediationAdRequest;

import java.util.Set;

/**
 * Utility class
 */
public class SASCustomEventUtil {

    /**
     * Convenience holder class for ad placement parameters
     */
    public static class SASAdPlacement {
        public int siteId;
        public String pageId = "";
        public int formatId;
        public String targeting = "";
    }

    /**
     * Returns a SASAdPlacement instance from passed parameters
     */
    public static SASAdPlacement getPlacementFromString(String placementString, MediationAdRequest adRequest) {
        SASAdPlacement adPlacement = new SASAdPlacement();

        // tokenize placement string and fill adPlacement;
        String[] ids = placementString.split("/");
        if (ids.length >= 3) {
            try {
                adPlacement.siteId = Integer.parseInt(ids[0].trim());
                adPlacement.pageId = ids[1].trim();
                adPlacement.formatId = Integer.parseInt(ids[2].trim());
            } catch (Exception e) {
                // invalid placement, return null
                return null;
            }
        }

        // extract keywords and concatenate them using semicolon as separator
        Set<String> keywordSet = adRequest.getKeywords();
        if (keywordSet != null) {
            for (String keyword: keywordSet) {
                if (adPlacement.targeting.length() > 0) {
                    keyword = ";".concat(keyword);
                }
                adPlacement.targeting = adPlacement.targeting.concat(keyword);
            }
        }

        return adPlacement;
    }
}
