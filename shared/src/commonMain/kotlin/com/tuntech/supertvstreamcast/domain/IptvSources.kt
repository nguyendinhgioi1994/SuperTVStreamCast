package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.*

const val PLAYLIST_MAX_BYTES = 2_000_000
const val GUIDE_MAX_BYTES = 12_000_000
const val XTREAM_MAX_BYTES = 12_000_000
const val LIBRARY_MAX_CHANNELS = 5_000

/** An imported session library; [guideUrl] is the provider's XMLTV source, if any. */
data class IptvLibrary(val channels: List<Channel>, val guideUrl: String = "", val truncated: Boolean = false)

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
    fun categoriesUrl(login: XtreamLogin) = account(login) + "&action=get_live_categories"
    fun streamsUrl(login: XtreamLogin) = account(login) + "&action=get_live_streams"
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
    /** Live streams in server order, grouped by category name, capped at [limit]. */
    fun parseLive(login: XtreamLogin, extension: String, categoriesJson: String, streamsJson: String, limit: Int = LIBRARY_MAX_CHANNELS): List<Channel> {
        val categories = (Json.parseToJsonElement(categoriesJson) as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.text("category_id") ?: return@mapNotNull null
            id to (obj.text("category_name") ?: "")
        }.toMap()
        val streams = Json.parseToJsonElement(streamsJson) as? JsonArray ?: throw IllegalArgumentException()
        val result = linkedMapOf<String, Channel>()
        for (item in streams) {
            if (result.size >= limit) break
            val obj = item as? JsonObject ?: continue
            val id = obj.text("stream_id")?.takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) } ?: continue
            val url = streamUrl(login, id, extension)
            if (url in result) continue
            result[url] = Channel(url, obj.text("name")?.trim().orEmpty().ifBlank { id },
                categories[obj.text("category_id")].orEmpty(), obj.text("epg_channel_id")?.trim().orEmpty())
        }
        require(result.isNotEmpty())
        return result.values.toList()
    }
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
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

data class Programme(val start: Long, val stop: Long, val title: String, val description: String = "")

/**
 * Lightweight XMLTV reader. Keeps only programmes for channels in the library that end after
 * [now] and start within [windowSeconds], at most [perChannel] each. Result is keyed by channel URL.
 */
class ParseGuideUseCase(private val windowSeconds: Long = 36 * 3600L, private val perChannel: Int = 24) {
    operator fun invoke(content: String, channels: List<Channel>, now: Long): Map<String, List<Programme>> {
        require(content.length <= GUIDE_MAX_BYTES)
        val text = content.removePrefix("\uFEFF").trimStart()
        require(text.startsWith("<?xml") || text.startsWith("<!DOCTYPE") || text.startsWith("<tv"))
        require(text.contains("<tv"))
        // Guide channel id → library channel URLs, by tvg-id first, then by display name.
        val byGuideId = channels.filter { it.guideId.isNotBlank() }.groupBy({ it.guideId.lowercase() }, { it.url })
        val byName = channels.groupBy({ normalizeName(it.title) }, { it.url })
        val targets = mutableMapOf<String, Set<String>>()
        Regex("<channel\\b([^>]*)>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { match ->
            val id = attribute(match.groupValues[1], "id") ?: return@forEach
            val names = Regex("<display-name\\b[^>]*>(.*?)</display-name>", RegexOption.DOT_MATCHES_ALL)
                .findAll(match.groupValues[2]).map { normalizeName(xmlText(it.groupValues[1])) }
            val urls = byGuideId[id.lowercase()].orEmpty() + names.flatMap { byName[it].orEmpty() }
            if (urls.isNotEmpty()) targets[id] = urls.toSet()
        }
        val result = mutableMapOf<String, MutableList<Programme>>()
        Regex("<programme\\b([^>]*)>(.*?)</programme>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { match ->
            val attrs = match.groupValues[1]
            val id = attribute(attrs, "channel") ?: return@forEach
            val urls = targets[id] ?: byGuideId[id.lowercase()]?.toSet() ?: return@forEach
            val start = attribute(attrs, "start")?.let(::parseXmltvTime) ?: return@forEach
            val stop = attribute(attrs, "stop")?.let(::parseXmltvTime) ?: return@forEach
            if (stop <= now || start >= now + windowSeconds || stop <= start) return@forEach
            val body = match.groupValues[2]
            val title = Regex("<title\\b[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(::xmlText)?.trim().orEmpty()
            if (title.isEmpty()) return@forEach
            val desc = Regex("<desc\\b[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(::xmlText)?.trim().orEmpty()
            val programme = Programme(start, stop, title.take(200), desc.take(600))
            urls.forEach { result.getOrPut(it) { mutableListOf() } += programme }
        }
        require(result.isNotEmpty())
        return result.mapValues { (_, list) -> list.sortedBy { it.start }.distinctBy { it.start }.take(perChannel) }
    }
    private fun attribute(attrs: String, name: String) =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").find(attrs)?.let { xmlText(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
}

/** Current programme (if airing) followed by the next ones. */
fun upcoming(programmes: List<Programme>?, now: Long, count: Int = 2): List<Programme> =
    programmes.orEmpty().filter { it.stop > now }.take(count)
fun isAiring(programme: Programme, now: Long) = programme.start <= now && now < programme.stop

/** XMLTV `YYYYMMDDhhmmss ±hhmm` → epoch seconds (missing offset = UTC). */
fun parseXmltvTime(value: String): Long? {
    val match = Regex("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})?\\s*(?:([+-])(\\d{2}):?(\\d{2}))?").matchEntire(value.trim()) ?: return null
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
fun xmlText(value: String): String {
    val raw = value.replace("<![CDATA[", "").replace("]]>", "")
    return Regex("&(#x[0-9A-Fa-f]+|#\\d+|amp|lt|gt|quot|apos);").replace(raw) { m ->
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
/** Compressed (`.gz`) guides are not supported; detected by the gzip magic bytes. */
fun isGzip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
class CompressedGuideException : Exception()
