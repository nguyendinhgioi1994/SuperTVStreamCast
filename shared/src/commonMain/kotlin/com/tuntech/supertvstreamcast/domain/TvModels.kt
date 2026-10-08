package com.tuntech.supertvstreamcast.domain

enum class TvBrand(val title: String) {
    SAMSUNG("Samsung"), LG("LG"), SONY("Sony BRAVIA"), GOOGLE("Google / Android TV"),
    TCL("TCL"), HISENSE("Hisense"), ROKU("Roku"), OTHER("TV")
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
/** [guideId] is the playlist `tvg-id` (or Xtream `epg_channel_id`) used to match XMLTV programmes. */
data class Channel(val url: String, val title: String, val group: String, val guideId: String = "")

/** A TV that answered a vendor protocol probe on the local network. */
data class TvDevice(val brand: TvBrand, val host: String, val name: String, val model: String = "")
data class TvApp(val id: String, val title: String)
/** Verified from the TV's own responses; buttons outside this set stay disabled. */
data class RemoteCapabilities(
    val keys: Set<RemoteKey> = emptySet(),
    val text: Boolean = false,
    val apps: Boolean = false,
    val pointer: Boolean = false,
)
/** Brands with an implemented direct-control adapter. Others personalize guidance only. */
val TvBrand.hasRemoteAdapter get() = this == TvBrand.SONY || this == TvBrand.SAMSUNG || this == TvBrand.LG
/** Sony uses an IP-control pre-shared key; Samsung/LG pair with an on-screen TV prompt. */
val TvBrand.needsPsk get() = this == TvBrand.SONY

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

/** Bound imports; preserve quoted metadata and ignore non-HTTP protocols. */
class ParsePlaylistUseCase {
    operator fun invoke(content: String): List<Channel> {
        require(content.length <= 2_000_000)
        val lines = content.removePrefix("﻿").lineSequence().map { it.trim() }
        require(lines.firstOrNull()?.let { it == "#EXTM3U" || it.startsWith("#EXTM3U ") } == true)
        val result = linkedMapOf<String, Channel>()
        var title = ""
        var group = ""
        var guideId = ""
        content.removePrefix("﻿").lineSequence().drop(1).forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:") -> {
                    var quoted = false
                    val split = line.indexOfFirst { c -> if (c == '"') quoted = !quoted; c == ',' && !quoted }
                    title = if (split >= 0) line.substring(split + 1).trim() else ""
                    group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1).orEmpty()
                    guideId = Regex("tvg-id=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.trim().orEmpty()
                }
                line.startsWith("#EXTGRP:") && group.isBlank() -> group = line.removePrefix("#EXTGRP:").trim()
                line.isNotBlank() && !line.startsWith('#') -> {
                    if (isStreamUrl(line) && !result.containsKey(line)) {
                        require(result.size < 5_000)
                        result[line] = Channel(line, title.ifBlank { line.substringBefore('?').substringAfterLast('/') }, group, guideId)
                    }
                    title = ""; group = ""; guideId = ""
                }
            }
        }
        require(result.isNotEmpty())
        return result.values.toList()
    }
}
/** XMLTV guide advertised in the `#EXTM3U` header (`url-tvg` / `x-tvg-url`); first HTTP(S) URL only. */
fun playlistGuideUrl(content: String): String? {
    val header = content.removePrefix("\uFEFF").lineSequence().firstOrNull()?.trim() ?: return null
    if (!header.startsWith("#EXTM3U")) return null
    val value = Regex("(?:url-tvg|x-tvg-url)=\"([^\"]*)\"").find(header)?.groupValues?.get(1) ?: return null
    return value.split(',').map { it.trim() }.firstOrNull(::isStreamUrl)
}
fun isStreamUrl(value: String): Boolean = Regex("https?://[^\\s/]+(?:/[^\\s]*)?", RegexOption.IGNORE_CASE).matches(value)

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
