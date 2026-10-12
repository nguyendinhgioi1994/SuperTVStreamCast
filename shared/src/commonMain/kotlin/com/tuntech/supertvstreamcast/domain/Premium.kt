package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a free user hits a paywall (or a rewarded ad) for. The remote features are locked when
 * Remote Config lists them; the IPTV ones are the [IptvLimits] being reached.
 */
enum class PremiumFeature {
    REMOTE_KEYBOARD, REMOTE_TOUCHPAD, REMOTE_APPS, IPTV_SOURCES, IPTV_WATCH_TIME;

    val isRemote: Boolean get() = this == REMOTE_KEYBOARD || this == REMOTE_TOUCHPAD || this == REMOTE_APPS
}

/**
 * Remote Config `PREMIUM_SETTINGS`. [locked] lists the remote features reserved for premium;
 * a rewarded ad unlocks one for [rewardUnlockMinutes], adds [rewardWatchSeconds] of IPTV time
 * today, or [rewardSources] extra saved sources for the session.
 */
data class PremiumSettings(
    val locked: Set<PremiumFeature> = emptySet(),
    val rewardUnlockMinutes: Int = 30,
    val rewardWatchSeconds: Int = 1800,
    val rewardSources: Int = 1,
)

/** Unreadable settings lock nothing, so a broken Remote Config value never blocks the app. */
fun parsePremiumSettings(json: String): PremiumSettings = try {
    val root = Json.parseToJsonElement(json) as JsonObject
    fun number(key: String, default: Int) = (root[key] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceAtLeast(0) ?: default
    val locked = (root["lockedFeatures"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.content?.let { name -> PremiumFeature.entries.firstOrNull { f -> f.name == name } } }
        .filter { it.isRemote }.toSet()
    val defaults = PremiumSettings()
    PremiumSettings(locked, number("rewardUnlockMinutes", defaults.rewardUnlockMinutes),
        number("rewardWatchSeconds", defaults.rewardWatchSeconds), number("rewardSources", defaults.rewardSources))
} catch (_: Exception) { PremiumSettings() }

/** Remote features opened by a rewarded ad, until an epoch-second deadline. Kept for the session only. */
class FeatureUnlocks(private val until: Map<PremiumFeature, Long> = emptyMap()) {
    fun unlock(feature: PremiumFeature, now: Long, minutes: Int) = FeatureUnlocks(until + (feature to now + minutes * 60L))
    fun isLocked(feature: PremiumFeature, settings: PremiumSettings, premium: Boolean, now: Long): Boolean =
        !premium && feature in settings.locked && (until[feature] ?: 0) <= now
    fun locked(settings: PremiumSettings, premium: Boolean, now: Long): Set<PremiumFeature> =
        settings.locked.filterTo(HashSet()) { isLocked(it, settings, premium, now) }
    /** The earliest future deadline, so the UI knows when to lock again. */
    fun nextExpiry(now: Long): Long? = until.values.filter { it > now }.minOrNull()
}
