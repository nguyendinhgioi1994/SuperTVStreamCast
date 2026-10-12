package com.tuntech.supertvstreamcast.domain

enum class TvBrand(val title: String) {
    SAMSUNG("Samsung"), LG("LG"), SONY("Sony BRAVIA"), GOOGLE("Google / Android TV"),
    TCL("TCL"), HISENSE("Hisense"), ROKU("Roku"), AMAZON("Amazon Fire TV"), VIZIO("Vizio"),
    PHILIPS("Philips"), PANASONIC("Panasonic"), XIAOMI("Xiaomi"), TOSHIBA("Toshiba"), SHARP("Sharp"),
    INSIGNIA("Insignia"), JVC("JVC"), ONN("onn."), WESTINGHOUSE("Westinghouse"), SKYWORTH("Skyworth / Coocaa"),
    HAIER("Haier"), CASPER("Casper"), NVIDIA("Nvidia Shield"), OTHER("TV")
}
enum class Feature { HOME, REMOTE, MIRROR, IPTV, SETTINGS }
enum class RemoteKey {
    POWER, UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU, INFO, GUIDE, INPUT,
    VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP, CHANNEL_DOWN,
    PLAY, PAUSE, STOP, REWIND, FAST_FORWARD,
    NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9;
    companion object {
        val digits = listOf(NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, NUM_0)
    }
}
/**
 * [guideId] is the playlist `tvg-id` (or Xtream `epg_channel_id`) used to match XMLTV programmes.
 * [userAgent] / [referer] are HTTP headers the playlist asks players to send for this stream.
 */
@kotlinx.serialization.Serializable
data class Channel(
    val url: String, val title: String, val group: String, val guideId: String = "",
    val logo: String = "", val userAgent: String = "", val referer: String = "",
    /** [ContentKind.SERIES] entries are not playable: [url] is only their identity and episodes are listed on demand. */
    val kind: ContentKind = ContentKind.LIVE,
)

/** Control protocol a TV speaks. TCL and Hisense ship several of them, so the brand alone does not decide it. */
enum class TvPlatform(val title: String) {
    SONY_BRAVIA("Sony BRAVIA"), SAMSUNG_TIZEN("Tizen"), LG_WEBOS("webOS"), GOOGLE_TV("Google TV / Android TV"),
    ROKU("Roku TV"), VIDAA("VIDAA"), FIRE_TV("Fire TV"), VIZIO("Vizio SmartCast"), PANASONIC_VIERA("Panasonic Viera"),
}

/**
 * A TV that answered a vendor protocol probe on the local network.
 * [mac] is the network adapter address when the TV reported one; it is only used for Wake-on-LAN.
 * [platform] is the protocol that answered; null for a TV only known by brand.
 */
data class TvDevice(val brand: TvBrand, val host: String, val name: String, val model: String = "", val mac: String = "", val platform: TvPlatform? = null)
data class TvApp(val id: String, val title: String)
/** Verified from the TV's own responses; buttons outside this set stay disabled. */
data class RemoteCapabilities(
    val keys: Set<RemoteKey> = emptySet(),
    val text: Boolean = false,
    val apps: Boolean = false,
    val pointer: Boolean = false,
)
/**
 * Protocols tried, in order, for a TV of this brand whose platform is not known yet. TCL sells Google TV,
 * Fire TV and Roku TV models; Hisense also sells VIDAA. Silent probes come first (Roku, VIDAA and Panasonic
 * change nothing on screen); Fire TV and Vizio only show a PIN when the TV really is one; starting Google TV
 * pairing makes any Android TV show a code, so it is always last.
 */
val TvBrand.platforms: List<TvPlatform> get() = when (this) {
    TvBrand.SAMSUNG -> listOf(TvPlatform.SAMSUNG_TIZEN)
    TvBrand.LG -> listOf(TvPlatform.LG_WEBOS)
    TvBrand.SONY -> listOf(TvPlatform.SONY_BRAVIA)
    TvBrand.GOOGLE, TvBrand.XIAOMI, TvBrand.SKYWORTH, TvBrand.HAIER, TvBrand.CASPER, TvBrand.NVIDIA -> listOf(TvPlatform.GOOGLE_TV)
    TvBrand.ROKU -> listOf(TvPlatform.ROKU)
    TvBrand.AMAZON -> listOf(TvPlatform.FIRE_TV)
    TvBrand.VIZIO -> listOf(TvPlatform.VIZIO)
    TvBrand.TCL -> listOf(TvPlatform.ROKU, TvPlatform.FIRE_TV, TvPlatform.GOOGLE_TV)
    TvBrand.HISENSE -> listOf(TvPlatform.ROKU, TvPlatform.VIDAA, TvPlatform.GOOGLE_TV)
    TvBrand.PHILIPS, TvBrand.SHARP, TvBrand.ONN -> listOf(TvPlatform.ROKU, TvPlatform.GOOGLE_TV)
    TvBrand.PANASONIC -> listOf(TvPlatform.PANASONIC_VIERA, TvPlatform.FIRE_TV, TvPlatform.GOOGLE_TV)
    TvBrand.TOSHIBA -> listOf(TvPlatform.VIDAA, TvPlatform.FIRE_TV, TvPlatform.GOOGLE_TV)
    TvBrand.INSIGNIA, TvBrand.WESTINGHOUSE -> listOf(TvPlatform.ROKU, TvPlatform.FIRE_TV)
    TvBrand.JVC -> listOf(TvPlatform.ROKU, TvPlatform.FIRE_TV, TvPlatform.GOOGLE_TV)
    TvBrand.OTHER -> listOf(TvPlatform.ROKU, TvPlatform.VIDAA, TvPlatform.PANASONIC_VIERA, TvPlatform.FIRE_TV, TvPlatform.VIZIO, TvPlatform.GOOGLE_TV)
}
/** Sony uses an IP-control pre-shared key typed by the user. */
val TvBrand.needsPsk get() = this == TvBrand.SONY
/** Samsung/LG ask the viewer to allow the phone on the TV screen while connecting. */
val TvBrand.promptsOnTv get() = this == TvBrand.SAMSUNG || this == TvBrand.LG

fun isLocalIpv4(value: String): Boolean {
    val octets = ipv4Octets(value) ?: return false
    return octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)
}
private fun ipv4Octets(value: String): List<Int>? {
    val parts = value.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || it.any { c -> !c.isDigit() } || (it.length > 1 && it.startsWith('0')) }) return null
    val octets = parts.map { it.toInt() }
    return if (octets.all { it in 0..255 }) octets else null
}
/** Candidate hosts of the phone's /24 private subnet, excluding network, broadcast and the phone itself. */
fun subnetHosts(localIp: String): List<String> {
    if (!isLocalIpv4(localIp)) return emptyList()
    val octets = ipv4Octets(localIp)!!
    val prefix = octets.take(3).joinToString(".")
    return (1..254).filter { it != octets[3] }.map { "$prefix.$it" }
}

private val STREAM_URL = Regex("https?://[^\\s/]+(?:/[^\\s]*)?", RegexOption.IGNORE_CASE)
fun isStreamUrl(value: String): Boolean = STREAM_URL.matches(value)

/** Groups in first-seen order; blank groups are not listed as a filter. */
fun channelGroups(channels: List<Channel>): List<String> = channels.map { it.group }.filter { it.isNotBlank() }.distinct()
/** Next/previous channel in the visible list, wrapping around, for player zapping. */
fun adjacentChannel(channels: List<Channel>, current: Channel, step: Int): Channel? {
    if (channels.isEmpty()) return null
    val index = channels.indexOfFirst { it.url == current.url }
    if (index < 0) return channels.first()
    return channels[(index + step).mod(channels.size)]
}
/** Most-recent-first history without duplicates, bounded. */
fun withRecent(recent: List<String>, url: String, limit: Int = 12): List<String> = (listOf(url) + recent.filter { it != url }).take(limit)
/** Touchpad swipe → arrow key along the dominant axis; short movements are ignored (taps select). */
fun swipeDirection(dx: Float, dy: Float, threshold: Float): RemoteKey? = when {
    dx * dx + dy * dy < threshold * threshold -> null
    kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> if (dx > 0) RemoteKey.RIGHT else RemoteKey.LEFT
    else -> if (dy > 0) RemoteKey.DOWN else RemoteKey.UP
}
