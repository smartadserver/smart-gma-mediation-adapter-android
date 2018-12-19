Smart AdServer - Google Mobile Ads SDK Adapter
==============================================

Introduction
------------
The _Smart Display SDK_ can be used through _Google Mobile Ads_ using the adapter provided in this repository for banners, interstitial and native ads. Those adapters are compatible with the _Smart Display SDK_ v6.10.

Setup
-----

1) Install the _Google Mobile Ads SDK_ according to the official documentation https://developers.google.com/admob/android/sdk.

2) Install the _Smart Display SDK_ by adding the ```displaylibrary``` dependency to your _gradle_ file (more info in [the documentation](http://help.smartadserver.com/android/V6.10/#IntegrationGuides/InstallationGuide.htm)).

3) Checkout this repository and copy the _Custom event classes_ you need into your Android project:

* ```SASGMACustomEventBanner``` for banners.
* ```SASGMACustomEventInterstitial``` for interstitials.
* ```SASGMACustomEventUtil``` in any case.

4) In your Google Ad Mob or Google Ad Manager interface, depending on which tool you use, you will need to setup a mediation group and add a custom event as an 'ad source' that will be activated on ad units of your application.

Typically, to deliver Smart ads on a Google ad unit, you need to create a custom event with :

* the _Class Name_ field: set `com.smartadserver.android.library.mediation.SASGMACustomEventBanner` for banners and `com.smartadserver.android.library.mediation.SASGMACustomEventInterstitial` for interstitials
* the _Parameter_ (optional) field: set your _Smart AdServer_ IDs concatenated as a string using slash separator `[siteID]/[pageID]/[formatID]`

For further information, please refer to Google documentation :

* For AdMob, https://support.google.com/admob/answer/3124703?hl=en&ref_topic=7383089
* For Google Ad Manager, https://support.google.com/admanager/answer/6272813?hl=en


More infos
----------
You can find more informations about the _Smart Display SDK_ and the _Google Mobile Ads SDK Adapter_ in the official documentation:
http://documentation.smartadserver.com/displaySDK
