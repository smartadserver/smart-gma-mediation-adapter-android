Smart AdServer - Google Ads Mobile SDK Adapter
==============================================

Introduction
------------
The _Smart AdServer Android SDK_ can be used through _Google Ads Mobile SDK (DFP)_ using the adapter provided in this repository for both _SASBannerView_ and _SASInterstitialView_.

Setup
-----

To start using the _Smart AdServer Android SDK_ through DFP, simply add all the classes included in this repository in your project (**the project needs to include a correctly installed _Smart AdServer Android SDK_**).

You can declare _SDK Mediation Creatives_ in the _DFP_ interface. To setup the _Custom Event_ (under _Ad networks_), you need to fill:

* the _Parameter_ field: set your _Smart AdServer_ IDs using slash separator `[siteID]/[pageID]/[formatID]`
* the _Class Name_ field: set `com.smartadserver.android.library.mediation.SASCustomEventBanner` for banners and `com.smartadserver.android.library.mediation.SASCustomEventInterstitial` for interstitials

More infos
----------
You can find more informations about the _Smart AdServer Android SDK_ and the _Google Ads Mobile Mediation Adapters_ in the official documentation:
http://help.smartadserver.com/en/
