Equativ - Google Mobile Ads SDK Adapter
==============================================

Introduction
------------
The _Equativ Display SDK_ can be used through _Google Mobile Ads_ using the adapter provided in this repository for banners, interstitial and native ads. Those adapters are compatible with the _Equativ Display SDK_ v8.xx and Google Mobile Ads v22.6.0

Setup
-----

1) Install the _Google Mobile Ads SDK_ according to the official documentation https://developers.google.com/admob/android/sdk.

2) Install the _Equativ Display SDK_ by adding the ```equativ-display-sdk``` dependency to your _gradle_ file (more info in [the documentation](https://documentation.smartadserver.com/displaySDK8/android/gettingstarted.html)).

3) Checkout this repository and copy the _Custom event classes_ you need into your Android project:

* ```SASGMAMediationBannerAdapter``` for banners.
* ```SASGMAMediationInterstitialAdapter``` for interstitials.
* ```SASGMAMediationRewardedVideoAdAdapter ``` for rewarded videos.
* ```SASGMAMediationNativeAdapter``` for native ads.
* ```SASGMAUtils``` in any case.


4) In your Google Ad Mob or Google Ad Manager interface, depending on which tool you use, you will need to setup a mediation group and add a custom event as an 'ad source' that will be activated on ad units of your application.

Typically, to deliver Equativ ads on a Google ad unit, you need to create a custom event with :

* the _Class Name_ field: set `com.equativ.displaysdk.mediation.SASGMAMediationBannerAdapter` for banners, `com.equativ.displaysdk.mediation.SASGMAMediationInterstitialAdapter` for interstitials, `com.equativ.displaysdk.mediation.SASGMAMediationRewardedVideoAdAdapter` for rewarded videos ads and `com.equativ.displaysdk.mediation.SASGMAMediationNativeAdapter` for native ads
* the _Parameter_ (optional) field: set your _Equativ_ IDs concatenated as a string using slash separator `[siteID]/[pageID]/[formatID]`

5) As mentioned by Google documentation, you **must** initialize all mediation adapters by calling the appropriate `MobileAds.initialize()` method. More details here https://developers.google.com/admob/android/quick-start#initialize_the_mobile_ads_sdk.

6) If you intend to use keyword targeting in your Equativ insertions, typically if you want it to match any custom targeting you have set-up on Google Ad Manager interface, you will have to set it on Google ad requests in your application.

This is done by using `addNetworkExtrasBundle()` on the Google AdRequest object for mediation adapters (or `addCustomEventExtrasBundle()` for deprecated `SASGMACustomEvent*` classes). 
For instance, in banner case, if your Equativ insertion uses "myCustomBannerTargeting" string on any Equativ programmed banner insertion :

```
val mediationExtras = Bundle()
mediationExtras.putString(SASGMAUtils.MEDIATION_EXTRAS_EQUATIV_KEYWORD_TARGETING_KEY,"myCustomBannerTargeting")
val adRequest: AdRequest = AdRequest.Builder().addNetworkExtrasBundle(SASGMAMediationBannerAdapter::class.java, mediationExtras).build()
```
            
This way, Google SDK will pass that bundle to the `SASGMAMediationBannerAdapter` instance, which in turn will extract the keyword targeting to be passed to the Equativ SDK.

For further information, please refer to Google documentation :

* For AdMob, https://support.google.com/admob/answer/3124703?hl=en&ref_topic=7383089
* For Google Ad Manager, https://support.google.com/admanager/answer/6272813?hl=en


More infos
----------
You can find more informations about the _Equativ Display SDK_ and the _Google Mobile Ads SDK Adapter_ in the official documentation:
https://documentation.smartadserver.com/displaySDK