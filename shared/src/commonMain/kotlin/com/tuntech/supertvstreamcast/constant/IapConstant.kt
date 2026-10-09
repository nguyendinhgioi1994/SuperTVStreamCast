package com.tuntech.supertvstreamcast.constant

object IapConstant {
    /**
     * Qonversion project key of TV Space. Blank until the Qonversion project exists: the splash
     * then skips IAP init, so no paywall is ever auto-shown and no product is listed.
     */
    const val PROJECT_KEY = ""
    const val ENTITLEMENT_KEY = "premium"

    const val IAP_SOURCE_SETTING = "setting"
    const val IAP_SOURCE_HOME = "home"
    const val IAP_SOURCE_ONBOARDING = "onboarding"
    const val IAP_SOURCE_IPTV_LIMIT = "iptv_limit"
}
