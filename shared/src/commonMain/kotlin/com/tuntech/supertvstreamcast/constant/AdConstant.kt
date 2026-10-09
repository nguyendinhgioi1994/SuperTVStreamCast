package com.tuntech.supertvstreamcast.constant

expect object AdConstant {
    val APP_OPEN_AD_UNIT_ID: String
    val INTERSTITIAL_AD_UNIT_ID: String
    val BANNER_AD_UNIT_ID: String
    val NATIVE_AD_UNIT_ID: String
    val REWARDED_AD_UNIT_ID: String
}

/** Placement names looked up in the `ADS_*_SETTINGS` Remote Config keys. */
object AdName {
    const val APP_FOREGROUND = "APP_FOREGROUND"
    const val APP_EXIT = "APP_EXIT"
    const val INTRODUCTION = "INTRODUCTION"
    const val PREMIUM = "PREMIUM"
}
