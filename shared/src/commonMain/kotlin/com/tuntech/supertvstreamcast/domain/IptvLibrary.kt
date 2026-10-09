package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.Serializable

/** [MOVIE] is any seekable video on demand; [SERIES] is an Xtream series whose episodes are listed on demand. */
@Serializable enum class ContentKind { LIVE, MOVIE, SERIES }
@Serializable enum class SourceType { PLAYLIST, FILE, XTREAM, STREAM }

/** Hours between automatic re-syncs; 0 turns them off. */
const val DEFAULT_REFRESH_HOURS = 168
val REFRESH_CHOICES = listOf(0, 24, 72, 168)
const val RECENT_LIMIT = 30

/** Subscription details reported by the Xtream server; times are epoch seconds, 0 when not given. */
@Serializable
data class XtreamAccount(
    val status: String = "", val expiresAt: Long = 0, val maxConnections: Int = 0, val activeConnections: Int = 0,
    val trial: Boolean = false, val liveFormat: String = "m3u8",
)

/**
 * A saved playlist, device file, Xtream account or single stream. [url] is the playlist link, the
 * Xtream server or the stream link; it and the login stay in app-private storage and are never logged.
 */
@Serializable
data class IptvSource(
    val id: String, val type: SourceType, val name: String, val url: String = "",
    val username: String = "", val password: String = "",
    val refreshHours: Int = DEFAULT_REFRESH_HOURS, val addedAt: Long = 0, val syncedAt: Long = 0,
    val channelCount: Int = 0, val guideUrl: String = "", val truncated: Boolean = false,
    val account: XtreamAccount? = null,
) {
    val login: XtreamLogin? get() = if (type == SourceType.XTREAM) XtreamLogin(url, username, password) else null
    /** Files and single streams have nothing to download again. */
    val refreshable get() = type == SourceType.PLAYLIST || type == SourceType.XTREAM
    /** Link another device can import: the playlist URL, or the account's `get.php` link. */
    val shareUrl: String? get() = when (type) {
        SourceType.PLAYLIST -> url
        SourceType.XTREAM -> "$url/get.php?username=${encodeUrlPart(username)}&password=${encodeUrlPart(password)}&type=m3u_plus"
        else -> null
    }
    /** Host shown under the name; never the path or query, which can carry a token. */
    val host: String get() = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
    fun sameOrigin(other: IptvSource) = type == other.type && url == other.url && username == other.username
    override fun toString() = "IptvSource($id, $type)"
}

fun isRefreshDue(source: IptvSource, now: Long): Boolean =
    source.refreshable && source.refreshHours > 0 && now - source.syncedAt >= source.refreshHours * 3600L

/**
 * What the user did with one channel. It carries a snapshot of the channel so favorites, recents
 * and hidden channels can be listed and played without loading their source, and survive a re-sync.
 */
@Serializable
data class LibraryEntry(
    val channel: Channel, val sourceId: String, val favorite: Boolean = false, val hidden: Boolean = false,
    val watchedAt: Long = 0, val positionMs: Long = 0, val durationMs: Long = 0,
) {
    val isEmpty get() = !favorite && !hidden && watchedAt == 0L
    /** Watched share of a video on demand, or null when unknown. */
    val progress: Float? get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
}
enum class LibraryList { FAVORITES, RECENT, HIDDEN }

private fun List<LibraryEntry>.change(channel: Channel, sourceId: String, update: (LibraryEntry) -> LibraryEntry): List<LibraryEntry> {
    val current = firstOrNull { it.channel.url == channel.url } ?: LibraryEntry(channel, sourceId)
    val updated = update(current.copy(channel = channel, sourceId = sourceId))
    return (filter { it.channel.url != channel.url } + updated).normalized()
}
/** Drops entries that say nothing and keeps the watch history bounded. */
private fun List<LibraryEntry>.normalized(): List<LibraryEntry> {
    val stale = filter { it.watchedAt > 0 }.sortedByDescending { it.watchedAt }.drop(RECENT_LIMIT).mapTo(HashSet()) { it.channel.url }
    return map { if (it.channel.url in stale) it.copy(watchedAt = 0, positionMs = 0, durationMs = 0) else it }.filterNot { it.isEmpty }
}
fun List<LibraryEntry>.toggleFavorite(channel: Channel, sourceId: String) = change(channel, sourceId) { it.copy(favorite = !it.favorite) }
fun List<LibraryEntry>.setHidden(channel: Channel, sourceId: String, hidden: Boolean) = change(channel, sourceId) { it.copy(hidden = hidden) }
fun List<LibraryEntry>.watched(channel: Channel, sourceId: String, at: Long) = change(channel, sourceId) { it.copy(watchedAt = at) }
/** Playback position of a video on demand; ignored for channels that were never opened. */
fun List<LibraryEntry>.progress(url: String, positionMs: Long, durationMs: Long): List<LibraryEntry> =
    map { if (it.channel.url == url && it.watchedAt > 0) it.copy(positionMs = positionMs.coerceAtLeast(0), durationMs = durationMs.coerceAtLeast(0)) else it }
fun List<LibraryEntry>.withoutSource(sourceId: String) = filter { it.sourceId != sourceId }
fun List<LibraryEntry>.cleared(list: LibraryList) = map {
    when (list) {
        LibraryList.FAVORITES -> if (it.hidden) it else it.copy(favorite = false)
        LibraryList.RECENT -> if (it.hidden) it else it.copy(watchedAt = 0, positionMs = 0, durationMs = 0)
        LibraryList.HIDDEN -> it.copy(hidden = false)
    }
}.filterNot { it.isEmpty }
/** Hidden channels only ever appear in [LibraryList.HIDDEN]. Recents are newest first, the others A→Z. */
fun List<LibraryEntry>.list(list: LibraryList): List<LibraryEntry> = when (list) {
    LibraryList.FAVORITES -> filter { it.favorite && !it.hidden }.sortedBy { it.channel.title.lowercase() }
    LibraryList.RECENT -> filter { it.watchedAt > 0 && !it.hidden }.sortedByDescending { it.watchedAt }
    LibraryList.HIDDEN -> filter { it.hidden }.sortedBy { it.channel.title.lowercase() }
}
/** Where to resume a video: not for the first seconds, and from the start again once it was nearly finished. */
fun resumePosition(entry: LibraryEntry?): Long {
    val position = entry?.positionMs ?: return 0
    if (position < 10_000) return 0
    return if (entry.durationMs > 0 && position > entry.durationMs - 15_000) 0 else position
}

private val VOD_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg")
/**
 * Live channel or video on demand, from the playlist's `tvg-type` / `type` attribute, else the
 * Xtream URL layout (`/movie/`, `/series/`), else a video file extension.
 */
fun guessContentKind(url: String, attributes: Map<String, String>): ContentKind {
    when ((attributes["tvg-type"] ?: attributes["type"]).orEmpty().lowercase()) {
        "movie", "movies", "vod", "series", "serie", "episode" -> return ContentKind.MOVIE
        "live", "stream", "channel" -> return ContentKind.LIVE
    }
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    if ("/movie/" in path || "/movies/" in path || "/series/" in path) return ContentKind.MOVIE
    return if (path.substringAfterLast('/').substringAfterLast('.', "") in VOD_EXTENSIONS) ContentKind.MOVIE else ContentKind.LIVE
}

enum class PasscodePurpose { OPEN_HIDDEN, CHANGE }
enum class PasscodeStep { VERIFY, CREATE, CONFIRM }
sealed interface PasscodeResult {
    data class Next(val flow: PasscodeFlow) : PasscodeResult
    /** The stored passcode was entered and nothing has to change. */
    data object Verified : PasscodeResult
    /** A new passcode was entered twice and should be stored. */
    data class Created(val pin: String) : PasscodeResult
}
/**
 * Steps of the 4-digit passcode page: verify the stored one, then (to change it, or when none is
 * set) create and confirm a new one. [error] marks a wrong passcode or a confirmation that differs.
 */
data class PasscodeFlow(val purpose: PasscodePurpose, val step: PasscodeStep, val first: String = "", val error: Boolean = false, val attempts: Int = 0) {
    fun submit(pin: String, matches: (String) -> Boolean): PasscodeResult = when (step) {
        PasscodeStep.VERIFY -> when {
            // Counting attempts makes every wrong entry a new state, so the page clears the digits each time.
            !matches(pin) -> PasscodeResult.Next(copy(error = true, attempts = attempts + 1))
            purpose == PasscodePurpose.OPEN_HIDDEN -> PasscodeResult.Verified
            else -> PasscodeResult.Next(PasscodeFlow(purpose, PasscodeStep.CREATE))
        }
        PasscodeStep.CREATE -> PasscodeResult.Next(PasscodeFlow(purpose, PasscodeStep.CONFIRM, first = pin))
        PasscodeStep.CONFIRM -> if (pin == first) PasscodeResult.Created(pin) else PasscodeResult.Next(PasscodeFlow(purpose, PasscodeStep.CREATE, error = true))
    }
    companion object {
        const val LENGTH = 4
        fun start(purpose: PasscodePurpose, hasPasscode: Boolean) = PasscodeFlow(purpose, if (hasPasscode) PasscodeStep.VERIFY else PasscodeStep.CREATE)
    }
}

/** A playlist handed over by a share link, a QR code or another app. */
data class ImportRequest(val name: String, val url: String)
const val IMPORT_SCHEME = "tvspace"
fun buildImportLink(name: String, url: String) = "$IMPORT_SCHEME://import-playlist?name=${encodeUrlPart(name)}&url=${encodeUrlPart(url)}"
/** Accepts the app's own import link or a plain HTTP(S) playlist link. */
fun parseImportLink(text: String): ImportRequest? {
    val trimmed = text.trim()
    if (isStreamUrl(trimmed)) return ImportRequest("", trimmed)
    if (!trimmed.startsWith("$IMPORT_SCHEME://import-playlist", ignoreCase = true)) return null
    val params = trimmed.substringAfter('?', "").substringBefore('#').split('&')
        .associate { it.substringBefore('=').lowercase() to decodeUrlPart(it.substringAfter('=', "").replace('+', ' ')) }
    val url = params["url"].orEmpty().trim()
    return if (isStreamUrl(url)) ImportRequest(params["name"].orEmpty().trim().take(80), url) else null
}

/** Name for a new source when the user gave none: the playlist file name or the server host. */
fun defaultSourceName(url: String): String {
    val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
    val file = path.substringAfter("://", "").substringAfter('/', "").substringAfterLast('/').substringBeforeLast('.')
    return file.takeIf { it.length in 3..40 && it !in GENERIC_NAMES } ?: path.substringAfter("://", path).substringBefore('/').substringBefore(':')
}
private val GENERIC_NAMES = setOf("get", "playlist", "index", "list", "live", "master", "player_api")

/** A notification asked for before a programme starts. [at] is when it fires, [start] when the programme begins (epoch seconds). */
@Serializable
data class Reminder(val id: Int, val channelUrl: String, val channelTitle: String, val start: Long, val title: String, val at: Long)
/** Stable per channel and start time, so a reloaded guide finds its reminders again. */
fun reminderId(channelUrl: String, start: Long): Int = "$channelUrl|$start".hashCode() and 0x7FFFFFFF
/** Minutes before the start that can still be chosen; empty once the programme has begun. */
fun reminderOffsets(start: Long, now: Long): List<Int> = listOf(0, 5, 10, 15, 30, 60).filter { start - it * 60L > now }

/**
 * Free-tier limits from Remote Config `IPTV_SETTINGS`; 0 means unlimited. Premium users have none.
 * [maxSources] counts saved sources of any kind, [dailyWatchSeconds] the playback time per day.
 */
data class IptvLimits(val maxSources: Int = 0, val dailyWatchSeconds: Int = 0) {
    fun canAddSource(saved: Int, premium: Boolean) = premium || maxSources <= 0 || saved < maxSources
    fun canWatch(watchedToday: Int, premium: Boolean) = premium || dailyWatchSeconds <= 0 || watchedToday < dailyWatchSeconds
}
/** Unreadable or missing settings mean no limits rather than a locked app. */
fun parseIptvLimits(json: String): IptvLimits = try {
    val root = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
    fun number(key: String) = (root[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    IptvLimits(number("maximumSources"), number("maximumWatchSeconds"))
} catch (_: Exception) { IptvLimits() }
