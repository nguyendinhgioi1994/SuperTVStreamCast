package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.*

/** Download caps. Playlists, Xtream listings and guides are parsed while streaming, so these bound time and data use, not memory. */
const val PLAYLIST_MAX_BYTES = 64_000_000
const val XTREAM_MAX_BYTES = 96_000_000
/** Guide file or download as stored (usually gzip); [GUIDE_MAX_BYTES] bounds the decompressed XML. */
const val GUIDE_FILE_MAX_BYTES = 48_000_000
const val GUIDE_MAX_BYTES = 400_000_000L
const val LIBRARY_MAX_CHANNELS = 50_000

/** One imported source; [guideUrl] is the provider's XMLTV source, if any. */
data class IptvLibrary(val channels: List<Channel>, val guideUrl: String = "", val truncated: Boolean = false, val account: XtreamAccount? = null)

/** User-supplied Xtream Codes account. Kept in memory for the session only; never persisted or logged. */
data class XtreamLogin(val server: String, val username: String, val password: String) {
    override fun toString() = "XtreamLogin($server)"
}
class XtreamAuthException : Exception()

/**
 * Xtream Codes `player_api.php` client logic without I/O. A library is built only after the
 * server answers the account request with `user_info.auth == 1`.
 */
object XtreamApi {
    /** Accepts `host`, `http(s)://host:port/` or a pasted `.../player_api.php` / `.../get.php?...` link. */
    fun normalizeServer(input: String): String? {
        val raw = input.trim().substringBefore('?').substringBefore('#')
        if (raw.isEmpty()) return null
        val value = (if (raw.contains("://")) raw else "http://$raw").trimEnd('/')
            .removeSuffix("/player_api.php").removeSuffix("/get.php").removeSuffix("/xmltv.php").trimEnd('/')
        if (!Regex("https?://[A-Za-z0-9.\\-]+(?::\\d{1,5})?(?:/[^\\s?#]*)?", RegexOption.IGNORE_CASE).matches(value)) return null
        val scheme = value.substringBefore("://").lowercase()
        return "$scheme://${value.substringAfter("://")}"
    }
    fun login(server: String, username: String, password: String): XtreamLogin? {
        val normalized = normalizeServer(server) ?: return null
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) return null
        return XtreamLogin(normalized, user, password)
    }
    private fun account(login: XtreamLogin) =
        "${login.server}/player_api.php?username=${encodeUrlPart(login.username)}&password=${encodeUrlPart(login.password)}"
    fun accountUrl(login: XtreamLogin) = account(login)
    fun categoriesUrl(login: XtreamLogin, kind: ContentKind = ContentKind.LIVE) = account(login) + "&action=" + when (kind) {
        ContentKind.LIVE -> "get_live_categories"; ContentKind.MOVIE -> "get_vod_categories"; ContentKind.SERIES -> "get_series_categories"
    }
    fun streamsUrl(login: XtreamLogin, kind: ContentKind = ContentKind.LIVE) = account(login) + "&action=" + when (kind) {
        ContentKind.LIVE -> "get_live_streams"; ContentKind.MOVIE -> "get_vod_streams"; ContentKind.SERIES -> "get_series"
    }
    fun seriesInfoUrl(login: XtreamLogin, seriesId: String) = account(login) + "&action=get_series_info&series_id=${encodeUrlPart(seriesId)}"
    fun movieUrl(login: XtreamLogin, streamId: String, extension: String) = vodUrl(login, "movie", streamId, extension)
    fun episodeUrl(login: XtreamLogin, episodeId: String, extension: String) = vodUrl(login, "series", episodeId, extension)
    private fun vodUrl(login: XtreamLogin, path: String, id: String, extension: String) =
        "${login.server}/$path/${encodeUrlPart(login.username)}/${encodeUrlPart(login.password)}/${encodeUrlPart(id)}.$extension"
    /** Identity of a series in the library. It is not a stream and carries no login. */
    fun seriesKey(login: XtreamLogin, seriesId: String) = "${login.server}/series/$seriesId"
    fun seriesId(series: Channel) = series.url.substringAfterLast('/')
    fun guideUrl(login: XtreamLogin) =
        "${login.server}/xmltv.php?username=${encodeUrlPart(login.username)}&password=${encodeUrlPart(login.password)}"
    fun streamUrl(login: XtreamLogin, streamId: String, extension: String) =
        "${login.server}/live/${encodeUrlPart(login.username)}/${encodeUrlPart(login.password)}/${encodeUrlPart(streamId)}.$extension"

    /** Returns the preferred live container (HLS when allowed); throws [XtreamAuthException] if the server refused the account. */
    fun parseAccount(json: String): String {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException()
        val user = root["user_info"] as? JsonObject ?: throw XtreamAuthException()
        val auth = (user["auth"] as? JsonPrimitive)?.contentOrNull
        if (auth != "1" && auth != "true") throw XtreamAuthException()
        val status = (user["status"] as? JsonPrimitive)?.contentOrNull
        if (status != null && !status.equals("Active", ignoreCase = true)) throw XtreamAuthException()
        val formats = (user["allowed_output_formats"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }.orEmpty()
        return if (formats.isEmpty() || "m3u8" in formats) "m3u8" else if ("ts" in formats) "ts" else throw IllegalArgumentException()
    }
    /** Subscription details from the account answer; throws like [parseAccount] when the account is refused. */
    fun parseAccountInfo(json: String): XtreamAccount {
        val format = parseAccount(json)
        val user = (Json.parseToJsonElement(json) as JsonObject)["user_info"] as JsonObject
        return XtreamAccount(
            status = user.text("status").orEmpty(), expiresAt = user.text("exp_date")?.toLongOrNull() ?: 0,
            maxConnections = user.text("max_connections")?.toIntOrNull() ?: 0, activeConnections = user.text("active_cons")?.toIntOrNull() ?: 0,
            trial = user.text("is_trial") == "1", liveFormat = format,
        )
    }
    /** Server, username and password from a pasted `get.php` / `player_api.php` link, or null if it carries no login. */
    fun credentials(link: String): XtreamLogin? {
        val query = link.trim().substringAfter('?', "").substringBefore('#')
        val params = query.split('&').associate { it.substringBefore('=').lowercase() to decodeUrlPart(it.substringAfter('=', "").replace('+', ' ')) }
        return login(link, params["username"].orEmpty(), params["password"].orEmpty())
    }
    fun categories(json: String): Map<String, String> = (Json.parseToJsonElement(json) as? JsonArray).orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val id = obj.text("category_id") ?: return@mapNotNull null
        id to (obj.text("category_name") ?: "")
    }.toMap()
    /**
     * One `get_live_streams` / `get_vod_streams` / `get_series` entry; null when it has no usable id.
     * [extension] is the live container; movies use their own `container_extension`.
     */
    fun channel(login: XtreamLogin, extension: String, categories: Map<String, String>, obj: JsonObject, kind: ContentKind = ContentKind.LIVE): Channel? {
        val id = obj.text(if (kind == ContentKind.SERIES) "series_id" else "stream_id")?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) } ?: return null
        val url = when (kind) {
            ContentKind.LIVE -> streamUrl(login, id, extension)
            ContentKind.MOVIE -> movieUrl(login, id, container(obj.text("container_extension")))
            ContentKind.SERIES -> seriesKey(login, id)
        }
        val logo = obj.text(if (kind == ContentKind.SERIES) "cover" else "stream_icon")?.trim().orEmpty().takeIf(::isStreamUrl).orEmpty()
        return Channel(url, obj.text("name")?.trim().orEmpty().ifBlank { id }, categories[obj.text("category_id")].orEmpty(),
            if (kind == ContentKind.LIVE) obj.text("epg_channel_id")?.trim().orEmpty() else "", logo, kind = kind)
    }
    private fun container(value: String?) = value?.trim()?.lowercase()?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "mp4"
    /**
     * `get_series_info` answer. Panels send `episodes` as an object keyed by season or as an array of
     * season arrays; episodes without an id are skipped.
     */
    fun parseSeries(login: XtreamLogin, json: String): SeriesInfo {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException()
        val info = root["info"] as? JsonObject
        val seasons: List<JsonElement> = when (val episodes = root["episodes"]) {
            is JsonObject -> episodes.values.toList()
            is JsonArray -> episodes
            else -> emptyList()
        }
        val list = seasons.flatMap { (it as? JsonArray).orEmpty() }.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.text("id")?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) } ?: return@mapNotNull null
            val details = obj["info"] as? JsonObject
            val number = obj.text("episode_num")?.toIntOrNull() ?: 0
            Episode(episodeUrl(login, id, container(obj.text("container_extension"))), obj.text("title")?.trim().orEmpty().take(200),
                obj.text("season")?.toIntOrNull() ?: 0, number, details?.text("duration_secs")?.toLongOrNull() ?: 0,
                details?.text("plot")?.trim().orEmpty().take(600), details?.text("movie_image")?.trim().orEmpty().takeIf(::isStreamUrl).orEmpty())
        }.distinctBy { it.url }.sortedWith(compareBy({ it.season }, { it.number }))
        require(list.isNotEmpty())
        fun field(key: String, max: Int = 200) = info?.text(key)?.trim().orEmpty().take(max)
        return SeriesInfo(field("plot", 1200), field("genre"), field("cast", 400), field("director"), field("releaseDate").ifEmpty { field("release_date") },
            field("rating", 8), field("cover", 2000).takeIf(::isStreamUrl).orEmpty(), list)
    }
    /** Live streams in server order, grouped by category name, capped at [limit]. */
    fun parseLive(login: XtreamLogin, extension: String, categoriesJson: String, streamsJson: String, limit: Int = LIBRARY_MAX_CHANNELS): List<Channel> {
        val reader = LiveReader(login, extension, categories(categoriesJson), limit)
        reader.feed(streamsJson)
        return reader.finish().channels
    }
    /** Streaming `get_live_streams` reader: each array element is decoded on its own, so a large listing is never one JSON tree. */
    class LiveReader(private val login: XtreamLogin, private val extension: String, private val categories: Map<String, String>,
                     private val limit: Int = LIBRARY_MAX_CHANNELS, private val kind: ContentKind = ContentKind.LIVE) {
        private val result = linkedMapOf<String, Channel>()
        private val objects = JsonObjects()
        private var truncated = false
        val size get() = result.size

        /** Returns false once the library is full. */
        fun feed(text: String): Boolean = objects.feed(text) { json ->
            val channel = (runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject)?.let { channel(login, extension, categories, it, kind) }
            if (channel != null && channel.url !in result) {
                if (result.size >= limit) { truncated = true; return@feed false }
                result[channel.url] = channel
            }
            true
        }
        fun finish(): IptvLibrary {
            require(objects.isArray && result.isNotEmpty())
            return IptvLibrary(result.values.toList(), guideUrl(login), truncated)
        }
    }
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}

/** Cuts the top-level objects out of a streamed JSON array (`[{…},{…}]`), honouring strings and escapes. */
class JsonObjects(private val maxObject: Int = 1_000_000) {
    private val current = StringBuilder()
    private var depth = 0
    private var inString = false
    private var escaped = false
    private var started = false
    /** True when the first significant character was `[`. */
    var isArray = false; private set

    fun feed(text: String, onObject: (String) -> Boolean): Boolean {
        for (c in text) {
            if (!started) {
                if (c.isWhitespace() || c == '\uFEFF') continue
                started = true; isArray = c == '['
                require(isArray)
                continue
            }
            if (depth == 0 && c != '{') continue
            current.append(c)
            require(current.length <= maxObject)
            when {
                escaped -> escaped = false
                inString -> if (c == '\\') escaped = true else if (c == '"') inString = false
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) {
                    val json = current.toString(); current.clear()
                    if (!onObject(json)) return false
                }
            }
        }
        return true
    }
}

/** RFC 3986 unreserved characters pass through; everything else is UTF-8 percent-encoded. */
fun encodeUrlPart(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val c = byte.toInt().toChar()
        if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c)
        else { append('%'); append(HEX[(byte.toInt() shr 4) and 15]); append(HEX[byte.toInt() and 15]) }
    }
}
private const val HEX = "0123456789ABCDEF"

/** One stream entered by the user; title falls back to the last path segment. */
fun singleStream(url: String, title: String): Channel? {
    val trimmed = url.trim()
    if (!isStreamUrl(trimmed)) return null
    return Channel(trimmed, title.trim().ifBlank { trimmed.substringBefore('?').substringAfterLast('/').ifBlank { trimmed } }, "")
}

/** One playable episode of an Xtream series. */
data class Episode(val url: String, val title: String, val season: Int, val number: Int, val durationSecs: Long = 0, val plot: String = "", val image: String = "")
data class SeriesInfo(
    val plot: String = "", val genre: String = "", val cast: String = "", val director: String = "", val released: String = "",
    val rating: String = "", val cover: String = "", val episodes: List<Episode> = emptyList(),
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct()
}
/** The episode as a library channel, so it is played, resumed and listed under Recent like a movie. */
fun Episode.toChannel(series: Channel): Channel {
    val code = "S${season}E$number"
    val name = when {
        title.isBlank() -> "${series.title} $code"
        title.contains(code, ignoreCase = true) -> title
        else -> "$code · $title"
    }
    return Channel(url, name, series.title, logo = image.ifEmpty { series.logo }, kind = ContentKind.MOVIE)
}

data class Programme(val start: Long, val stop: Long, val title: String, val description: String = "")

/**
 * Streaming XMLTV reader: text is fed in chunks and only `<channel>` / `<programme>` elements are
 * looked at, one at a time. Keeps programmes for channels in the library that end after [now] and
 * start within [windowSeconds], at most [perChannel] each. Result is keyed by channel URL.
 */
class GuideParser(channels: List<Channel>, private val now: Long, private val windowSeconds: Long = 36 * 3600L, private val perChannel: Int = 24) {
    // Guide channel id → library channel URLs, by tvg-id first, then by display name.
    private val byGuideId = channels.filter { it.guideId.isNotBlank() }.groupBy({ it.guideId.lowercase() }, { it.url })
    private val byName = channels.groupBy({ normalizeName(it.title) }, { it.url })
    private val targets = mutableMapOf<String, List<String>>()
    private val result = mutableMapOf<String, MutableList<Programme>>()
    private var carry = ""
    private var started = false
    private var sawTv = false

    fun feed(chunk: String) {
        var text = if (carry.isEmpty()) chunk else carry + chunk
        if (!started) {
            text = text.trimStart { it.isWhitespace() || it == '\uFEFF' }
            if (text.isEmpty()) return
            require(text[0] == '<')
            started = true
        }
        var pos = 0
        var keep = text.length
        while (true) {
            val open = text.indexOf('<', pos)
            if (open < 0) break
            val name = when {
                text.startsWith("programme", open + 1) -> "programme"
                text.startsWith("channel", open + 1) -> "channel"
                else -> {
                    if (text.startsWith("tv", open + 1)) sawTv = true
                    // A tag name cut by the chunk boundary is finished by the next chunk.
                    if (text.length - open < 12) { keep = open; break }
                    pos = open + 1; continue
                }
            }
            val after = text.getOrNull(open + 1 + name.length)
            if (after != null && !after.isWhitespace() && after != '>' && after != '/') { pos = open + 1; continue }
            val tagEnd = text.indexOf('>', open)
            val close = if (tagEnd < 0) -1 else if (text[tagEnd - 1] == '/') tagEnd else text.indexOf("</$name>", tagEnd)
            if (close < 0) {
                // Incomplete element: wait for more text, unless it is implausibly large (malformed XML).
                if (text.length - open > MAX_ELEMENT) { pos = open + 1; continue }
                keep = open; break
            }
            val attributes = text.substring(open + 1 + name.length, tagEnd)
            val body = if (close == tagEnd) "" else text.substring(tagEnd + 1, close)
            if (name == "channel") channel(attributes, body) else programme(attributes, body)
            pos = if (close == tagEnd) tagEnd + 1 else close + name.length + 3
        }
        carry = if (keep < text.length) text.substring(keep) else ""
    }

    fun finish(): Map<String, List<Programme>> {
        require(sawTv && result.isNotEmpty())
        return result.mapValues { (_, list) -> list.sortedBy { it.start }.distinctBy { it.start }.take(perChannel) }
    }

    private fun channel(attributes: String, body: String) {
        val id = attribute(attributes, "id") ?: return
        val names = DISPLAY_NAME.findAll(body).map { normalizeName(xmlText(it.groupValues[1])) }
        val urls = byGuideId[id.lowercase()].orEmpty() + names.flatMap { byName[it].orEmpty() }
        if (urls.isNotEmpty()) targets[id] = urls.distinct()
    }
    private fun programme(attributes: String, body: String) {
        val id = attribute(attributes, "channel") ?: return
        val urls = targets[id] ?: byGuideId[id.lowercase()] ?: return
        val start = attribute(attributes, "start")?.let(::parseXmltvTime) ?: return
        val stop = attribute(attributes, "stop")?.let(::parseXmltvTime) ?: return
        if (stop <= now || start >= now + windowSeconds || stop <= start) return
        val title = TITLE.find(body)?.groupValues?.get(1)?.let(::xmlText)?.trim().orEmpty()
        if (title.isEmpty()) return
        val desc = DESC.find(body)?.groupValues?.get(1)?.let(::xmlText)?.trim().orEmpty()
        val programme = Programme(start, stop, title.take(200), desc.take(600))
        urls.forEach { result.getOrPut(it) { mutableListOf() } += programme }
    }
    /** `name="value"` or `name='value'` inside an opening tag. */
    private fun attribute(attributes: String, name: String): String? {
        var at = attributes.indexOf("$name=")
        while (at > 0 && !attributes[at - 1].isWhitespace()) at = attributes.indexOf("$name=", at + 1)
        if (at < 0) return null
        val quote = attributes.getOrNull(at + name.length + 1)?.takeIf { it == '"' || it == '\'' } ?: return null
        val end = attributes.indexOf(quote, at + name.length + 2)
        return if (end < 0) null else xmlText(attributes.substring(at + name.length + 2, end))
    }
    private companion object {
        const val MAX_ELEMENT = 1_000_000
        val DISPLAY_NAME = Regex("<display-name\\b[^>]*>(.*?)</display-name>", RegexOption.DOT_MATCHES_ALL)
        val TITLE = Regex("<title\\b[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val DESC = Regex("<desc\\b[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)
    }
}
/** Guide text already in memory. */
class ParseGuideUseCase {
    operator fun invoke(content: String, channels: List<Channel>, now: Long): Map<String, List<Programme>> =
        GuideParser(channels, now).apply { feed(content) }.finish()
}

/** Current programme (if airing) followed by the next ones. */
fun upcoming(programmes: List<Programme>?, now: Long, count: Int = 2): List<Programme> =
    programmes.orEmpty().filter { it.stop > now }.take(count)
fun isAiring(programme: Programme, now: Long) = programme.start <= now && now < programme.stop

/** XMLTV `YYYYMMDDhhmmss ±hhmm` → epoch seconds (missing offset = UTC). */
private val XMLTV_TIME = Regex("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})?\\s*(?:([+-])(\\d{2}):?(\\d{2}))?")
fun parseXmltvTime(value: String): Long? {
    val match = XMLTV_TIME.matchEntire(value.trim()) ?: return null
    val g = match.groupValues
    val month = g[2].toInt(); val day = g[3].toInt(); val hour = g[4].toInt(); val minute = g[5].toInt(); val second = g[6].ifEmpty { "0" }.toInt()
    if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
    val offset = if (g[7].isEmpty()) 0 else (g[8].toInt() * 3600 + g[9].toInt() * 60) * (if (g[7] == "-") -1 else 1)
    return daysFromCivil(g[1].toLong(), month, day) * 86_400 + hour * 3600 + minute * 60 + second - offset
}
/** Howard Hinnant's days-from-civil algorithm (proleptic Gregorian). */
private fun daysFromCivil(year: Long, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}
private fun normalizeName(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
/** Strips CDATA markers and decodes the five XML entities plus numeric references. */
private val XML_ENTITY = Regex("&(#x[0-9A-Fa-f]+|#\\d+|amp|lt|gt|quot|apos);")
fun xmlText(value: String): String {
    val raw = value.replace("<![CDATA[", "").replace("]]>", "")
    if ('&' !in raw) return raw
    return XML_ENTITY.replace(raw) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> {
                val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                if (code == null || code !in 1..0x10FFFF) "" else if (code < 0x10000) code.toChar().toString()
                else { val v = code - 0x10000; charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString() }
            }
        }
    }
}
/** Guides are usually served compressed (`.xml.gz`); detected by the gzip magic bytes. */
fun isGzip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
