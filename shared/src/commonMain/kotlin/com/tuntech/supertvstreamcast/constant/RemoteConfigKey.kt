package com.tuntech.supertvstreamcast.constant

data object RemoteConfigKey {
    const val TERM_OF_SERVICE_URL = "TERM_OF_SERVICE_URL"
    const val PRIVACY_POLICY_URL = "PRIVACY_POLICY_URL"
    /** Free-tier IPTV limits: `maximumSources`, `maximumWatchSeconds` (per day); 0 = unlimited. */
    const val IPTV_SETTINGS = "IPTV_SETTINGS"
}
