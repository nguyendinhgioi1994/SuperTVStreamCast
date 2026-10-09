package com.tuntech.supertvstreamcast.domain

/** The body is not a channel playlist: an HTML error page, JSON, or binary data. */
class NotPlaylistException : IllegalArgumentException()
/** The body is one HLS stream (`#EXT-X-…` tags) rather than a list of channels. */
class HlsStreamException : IllegalArgumentException()

/**
 * Line-fed M3U reader shared by pasted text, device files and streamed downloads, so a large
 * provider playlist is never held in memory as one string.
 *
 * Tolerates what real playlists contain: tags in any case, a missing `#EXTM3U` first line, `"`, `'`
 * or unquoted attributes, `#EXTGRP`, and per-channel HTTP headers (`#EXTVLCOPT:http-user-agent`,
 * `user-agent="…"`, or `url|User-Agent=…&Referer=…`). Only HTTP(S) streams are kept; duplicates are
 * dropped. Reading stops at [limit] channels with [truncated] set instead of failing the import.
 */
class PlaylistParser(private val limit: Int = LIBRARY_MAX_CHANNELS) {
    private val channels = linkedMapOf<String, Channel>()
    private val groups = mutableMapOf<String, String>()
    private var guideUrl = ""
    private var truncated = false
    private var hls = false
    private var marker = false
    private var first = true
    private var info: String? = null
    private var group = ""
    private var userAgent = ""
    private var referer = ""

    val size get() = channels.size

    /** Consumes one line; returns false once the library is full and the caller can stop reading. */
    fun feed(raw: String): Boolean {
        val line = raw.trim { it.isWhitespace() || it == '﻿' }
        if (line.isEmpty()) return true
        if (first) {
            first = false
            // Fail fast on error pages and API responses instead of reading them to the end.
            if (line[0] == '<' || line[0] == '{' || line[0] == '[') throw NotPlaylistException()
        }
        if (line[0] != '#') return add(line)
        when {
            line.startsWith(EXTINF, ignoreCase = true) -> {
                marker = true
                info = line.substring(EXTINF.length); group = ""; userAgent = ""; referer = ""
            }
            line.startsWith(EXTM3U, ignoreCase = true) -> {
                marker = true
                if (guideUrl.isEmpty()) {
                    val attributes = playlistAttributes(line.substring(EXTM3U.length))
                    guideUrl = GUIDE_KEYS.mapNotNull { attributes[it] }.flatMap { it.split(',') }.map { it.trim() }.firstOrNull(::isStreamUrl).orEmpty()
                }
            }
            line.startsWith(EXTGRP, ignoreCase = true) -> group = line.substring(EXTGRP.length).trim()
            line.startsWith(EXTVLCOPT, ignoreCase = true) -> {
                val option = line.substring(EXTVLCOPT.length)
                val value = option.substringAfter('=', "").trim()
                when (option.substringBefore('=').trim().lowercase()) {
                    "http-user-agent" -> userAgent = value
                    "http-referrer", "http-referer" -> referer = value
                }
            }
            line.startsWith(EXT_X, ignoreCase = true) -> hls = true
        }
        return true
    }

    /** @throws HlsStreamException for a single HLS stream, [NotPlaylistException] when nothing M3U-like was read. */
    fun finish(): IptvLibrary {
        if (channels.isEmpty() && hls) throw HlsStreamException()
        if (!marker || channels.isEmpty()) throw NotPlaylistException()
        return IptvLibrary(channels.values.toList(), guideUrl, truncated)
    }

    private fun add(raw: String): Boolean {
        val meta = info
        val pendingGroup = group; val pendingAgent = userAgent; val pendingReferer = referer
        info = null; group = ""; userAgent = ""; referer = ""
        val pipe = raw.indexOf('|').takeIf { it > 0 && raw.indexOf('=', it) > 0 } ?: raw.length
        val url = raw.substring(0, pipe).trim()
        if (!isStreamUrl(url) || url in channels) return true
        if (channels.size >= limit) { truncated = true; return false }
        val headers = if (pipe == raw.length) emptyMap() else raw.substring(pipe + 1).split('&').associate {
            it.substringBefore('=').trim().lowercase() to decodeUrlPart(it.substringAfter('=', "").trim())
        }
        val comma = meta?.let(::titleSeparator) ?: -1
        val attributes = if (meta == null) emptyMap() else playlistAttributes(if (comma < 0) meta else meta.substring(0, comma))
        val title = (if (comma < 0) "" else meta!!.substring(comma + 1).trim())
            .ifEmpty { attributes["tvg-name"].orEmpty() }
            .ifEmpty { url.substringBefore('?').trimEnd('/').substringAfterLast('/') }
        val groupName = (attributes["group-title"] ?: pendingGroup).trim()
        channels[url] = Channel(
            url = url, title = title, group = groups.getOrPut(groupName) { groupName },
            guideId = attributes["tvg-id"].orEmpty(),
            logo = (attributes["tvg-logo"] ?: attributes["logo"]).orEmpty().takeIf(::isStreamUrl).orEmpty(),
            userAgent = headers["user-agent"] ?: pendingAgent.ifEmpty { attributes["user-agent"] ?: attributes["http-user-agent"].orEmpty() },
            referer = headers["referer"] ?: headers["referrer"] ?: pendingReferer.ifEmpty { attributes["referer"] ?: attributes["http-referrer"].orEmpty() },
            kind = guessContentKind(url, attributes),
        )
        return true
    }

    /** First comma outside double quotes; with unbalanced quotes, the last comma. */
    private fun titleSeparator(value: String): Int {
        var quoted = false
        for (i in value.indices) when (value[i]) {
            '"' -> quoted = !quoted
            ',' -> if (!quoted) return i
        }
        return value.lastIndexOf(',')
    }

    private companion object {
        const val EXTM3U = "#EXTM3U"
        const val EXTINF = "#EXTINF:"
        const val EXTGRP = "#EXTGRP:"
        const val EXTVLCOPT = "#EXTVLCOPT:"
        const val EXT_X = "#EXT-X-"
        val GUIDE_KEYS = listOf("url-tvg", "x-tvg-url", "tvg-url")
    }
}

private val ATTRIBUTE = Regex("""([A-Za-z0-9_.:-]+)=(?:"([^"]*)"|'([^']*)'|(\S+))""")
/** `key="value" key='value' key=value` pairs; keys are lower-cased, blank values dropped, the first occurrence wins. */
internal fun playlistAttributes(text: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    ATTRIBUTE.findAll(text).forEach { match ->
        val groups = match.groups
        val value = (groups[2] ?: groups[3] ?: groups[4])?.value.orEmpty().trim().trim('"')
        val key = match.groupValues[1].lowercase()
        if (value.isNotEmpty() && key !in result) result[key] = value
    }
    return result
}

/** Pasted playlist text. */
class ParsePlaylistUseCase(private val limit: Int = LIBRARY_MAX_CHANNELS) {
    operator fun invoke(content: String): IptvLibrary {
        val parser = PlaylistParser(limit)
        for (line in content.lineSequence()) if (!parser.feed(line)) break
        return parser.finish()
    }
}
/** Playlist bytes from a device file; [onProgress] receives the channel count as it grows. */
fun parsePlaylistBytes(bytes: ByteArray, onProgress: (Int) -> Unit = {}): IptvLibrary {
    val parser = PlaylistParser()
    val lines = LineSplitter()
    val feed = { line: String -> parser.feed(line).also { if (parser.size % PROGRESS_STEP == 0) onProgress(parser.size) } }
    if (lines.feed(bytes, bytes.size, feed)) lines.finish(feed)
    return parser.finish()
}
const val PROGRESS_STEP = 500

/**
 * Splits streamed bytes into UTF-8 lines without keeping the body. `\n` never occurs inside a
 * multi-byte sequence, so lines are cut on raw bytes and decoded one at a time. A line longer than
 * [maxLine] means the body is not text (a video stream pasted as a playlist) and is rejected.
 */
class LineSplitter(private val maxLine: Int = 256 * 1024) {
    private var carry = ByteArray(0)

    /** Calls [onLine] for each completed line; returns false as soon as [onLine] does. */
    fun feed(bytes: ByteArray, length: Int, onLine: (String) -> Boolean): Boolean {
        var start = 0
        for (i in 0 until length) {
            if (bytes[i] != NEWLINE) continue
            val line = if (carry.isEmpty()) bytes.decodeToString(start, i) else (carry + bytes.copyOfRange(start, i)).decodeToString().also { carry = ByteArray(0) }
            start = i + 1
            if (!onLine(line)) return false
        }
        if (start < length) {
            if (carry.size + length - start > maxLine) throw NotPlaylistException()
            carry += bytes.copyOfRange(start, length)
        }
        return true
    }
    fun finish(onLine: (String) -> Boolean) {
        if (carry.isNotEmpty()) onLine(carry.decodeToString())
        carry = ByteArray(0)
    }
    private companion object { const val NEWLINE = '\n'.code.toByte() }
}

/** Decodes streamed UTF-8 chunks, carrying a multi-byte sequence split across two chunks. */
class Utf8Chunks {
    private var carry = ByteArray(0)

    fun decode(bytes: ByteArray, length: Int): String {
        val data = if (carry.isEmpty()) bytes else carry + bytes.copyOfRange(0, length)
        val end = if (carry.isEmpty()) length else data.size
        // Walk back over continuation bytes to the last lead byte and see whether its sequence is complete.
        var lead = end - 1
        while (lead >= 0 && lead > end - 4 && (data[lead].toInt() and 0xC0) == 0x80) lead--
        var cut = end
        if (lead >= 0) {
            val b = data[lead].toInt() and 0xFF
            val needed = when { b >= 0xF0 -> 4; b >= 0xE0 -> 3; b >= 0xC0 -> 2; else -> 1 }
            if (lead + needed > end) cut = lead
        }
        carry = if (cut == end) ByteArray(0) else data.copyOfRange(cut, end)
        return data.decodeToString(0, cut)
    }
}

/** Percent-decoding for header values written in a playlist URL suffix; malformed escapes are kept as typed. */
fun decodeUrlPart(value: String): String {
    if ('%' !in value) return value
    val out = ByteArray(value.length * 3)
    var size = 0
    var i = 0
    while (i < value.length) {
        val code = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (code != null) { out[size++] = code.toByte(); i += 3 }
        else { val encoded = value[i].toString().encodeToByteArray(); encoded.copyInto(out, size); size += encoded.size; i++ }
    }
    return out.decodeToString(0, size)
}
