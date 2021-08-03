Smart AdServer - Google Mobile Ads SDK Adapter
==============================================

Introduction
------------
The _Smart Display SDK_ can be used through _Google Mobile Ads_ using the adapter provided in this repository for banners, interstitial and native ads. Those adapters are compatible with the _Smart Display SDK_ v7.0.

Setup
-----

1) Install the _Google Mobile Ads SDK_ according to the official documentation https://developers.google.com/admob/android/sdk.

2) Install the _Smart Display SDK_ by adding the ```smart-display-sdk``` dependency to your _gradle_ file (more info in [the documentation](https://documentation.smartadserver.com/displaySDK/android/gettingstarted.html)).

3) Checkout this repository and copy the _Custom event classes_ you need into your Android project:

* ```SASGMACustomEventBanner``` for banners.
* ```SASGMACustomEventInterstitial``` for interstitials.
* ```SASGMAMediationRewardedVideoAdAdapter ``` for rewarded videos.
* ```SASGMACustomEventNative``` for native ads.
* ```SASGMACustomEventUtil``` in any case.

4) In your Google Ad Mob or Google Ad Manager interface, depending on which tool you use, you will need to setup a mediation group and add a custom event as an 'ad source' that will be activated on ad units of your application.

Typically, to deliver Smart ads on a Google ad unit, you need to create a custom event with :

* the _Class Name_ field: set `com.smartadserver.android.library.mediation.SASGMACustomEventBanner` for banners, `com.smartadserver.android.library.mediation.SASGMACustomEventInterstitial` for interstitials, com.smartadserver.android.library.mediation.SASGMAMediationRewardedVideoAdAdapter for rewarded videos ads and `com.smartadserver.android.library.mediation.SASGMACustomEventNative` for native ads
* the _Parameter_ (optional) field: set your _Smart AdServer_ IDs concatenated as a string using slash separator `[siteID]/[pageID]/[formatID]`

5) As mentioned by Google documentation, you **must** initialize all mediation adapters by calling the appropriate `MobileAds.initialize()` method. More details here https://developers.google.com/admob/android/quick-start#initialize_the_mobile_ads_sdk.

6) If you intend to use keyword targeting in your Smart insertions, typically if you want it to match any custom targeting you have set-up on Google Ad Manager interface, you will have to set it on Google ad requests in your application.

This is done by using either addCustomEventExtrasBundle() or addNetworkExtrasBundle() (depending on the class of the adapter) on the Google AdRequest object.
For instance, in banner case, if your smart insertion uses "myCustomBannerTargeting" string on any Smart programmed banner insertion :

Bundle mediationExtras = new Bundle();
mediationExtras.putString(SASGMACustomEventUtil.MEDIATION_EXTRAS_SMART_KEYWORD_TARGETING_KEY, "myCustomBannerTargeting");
AdRequest adRequest = new AdRequest.Builder().addCustomEventExtrasBundle(SASGMACustomEventBanner.class,mediationExtras).build();

This way, Google SDK will pass that bundle to the SASGMACustomEventBanner instance, which in turn will extract the keyword targeting to be passed to the Smart SDK.

For further information, please refer to Google documentation :

* For AdMob, https://support.google.com/admob/answer/3124703?hl=en&ref_topic=7383089
* For Google Ad Manager, https://support.google.com/admanager/answer/6272813?hl=en


More infos
----------
You can find more informations about the _Smart Display SDK_ and the _Google Mobile Ads SDK Adapter_ in the official documentation:
https://documentation.smartadserver.com/displaySDK