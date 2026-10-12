package com.tuntech.supertvstreamcast.domain.cast

/** How the phone talks to a receiver. */
enum class CastKind { DLNA, CHROMECAST, AIRPLAY }

/**
 * A screen media can be sent to. [id] is stable for one receiver across scans (the DLNA UDN or the
 * Cast route id). A receiver only appears here after it answered with its own description.
 */
data class CastReceiver(val id: String, val name: String, val kind: CastKind, val model: String = "")

enum class MediaKind { PHOTO, VIDEO, AUDIO }

/** One item to play on the receiver. [url] must be reachable from the TV (LAN server or the internet). */
data class CastMedia(val url: String, val title: String, val mime: String, val kind: MediaKind)

enum class PlaybackState { IDLE, LOADING, PLAYING, PAUSED, STOPPED, ERROR }

/** What the receiver last reported. Durations are 0 when unknown (photos, live streams). [volume] 0–100, null when the receiver does not say. */
data class CastStatus(
    val state: PlaybackState = PlaybackState.IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int? = null,
)

/** MIME type from a file name or URL path; null when the extension is unknown. */
fun mimeFor(name: String): String? = when (name.substringBefore('?').substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "mp4", "m4v" -> "video/mp4"
    "mov" -> "video/quicktime"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "avi" -> "video/x-msvideo"
    "3gp" -> "video/3gpp"
    "ts" -> "video/mp2t"
    "m3u8" -> "application/x-mpegURL"
    "mp3" -> "audio/mpeg"
    "m4a", "aac" -> "audio/mp4"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "ogg", "oga" -> "audio/ogg"
    else -> null
}

fun kindFor(mime: String): MediaKind = when {
    mime.startsWith("image/") -> MediaKind.PHOTO
    mime.startsWith("audio/") -> MediaKind.AUDIO
    else -> MediaKind.VIDEO
}

/**
 * A link the user pasted for "Web video": only http(s) with a host. The MIME type comes from the
 * extension and defaults to MP4, which most receivers accept; HLS needs a receiver that plays it.
 */
fun webMedia(link: String): CastMedia? {
    val url = link.trim()
    if (!WEB_URL.matches(url)) return null
    val path = url.substringAfter("://").substringAfter('/', "")
    val mime = mimeFor(path) ?: "video/mp4"
    val name = path.substringBefore('?').substringAfterLast('/').ifBlank { url.substringAfter("://").substringBefore('/') }
    return CastMedia(url, name, mime, kindFor(mime))
}
private val WEB_URL = Regex("^https?://[^\\s/?#]+[^\\s]*$", RegexOption.IGNORE_CASE)

/** `1:02:03` / `2:03` for the controller. */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600; val m = total / 60 % 60; val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}
