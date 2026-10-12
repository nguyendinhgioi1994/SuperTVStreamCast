package com.tuntech.supertvstreamcast.constant

data object RemoteConfigKey {
    const val TERM_OF_SERVICE_URL = "TERM_OF_SERVICE_URL"
    const val PRIVACY_POLICY_URL = "PRIVACY_POLICY_URL"
    /** Address for Settings → Contact us and shared logs; the Contact row is hidden while it is empty. */
    const val SUPPORT_EMAIL = "SUPPORT_EMAIL"
    /** Free-tier IPTV limits: `maximumSources`, `maximumWatchSeconds` (per day); 0 = unlimited. */
    const val IPTV_SETTINGS = "IPTV_SETTINGS"
    /** Premium-only remote features and rewarded-ad grants, see `domain/Premium.kt`. */
    const val PREMIUM_SETTINGS = "PREMIUM_SETTINGS"
}
