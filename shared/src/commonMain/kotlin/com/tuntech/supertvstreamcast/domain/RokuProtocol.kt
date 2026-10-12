package com.tuntech.supertvstreamcast.domain

/**
 * Roku External Control Protocol (ECP): plain HTTP on port 8060, XML replies.
 * See https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
 */
object RokuProtocol {
    const val PORT = 8060
    const val SSDP_TARGET = "roku:ecp"

    fun base(host: String) = "http://$host:$PORT"

    /** ECP key names. Volume, power and channel keys exist only on Roku TVs, so they depend on [Info]. */
    fun keyName(key: RemoteKey): String? = when (key) {
        RemoteKey.UP -> "Up"; RemoteKey.DOWN -> "Down"; RemoteKey.LEFT -> "Left"; RemoteKey.RIGHT -> "Right"
        RemoteKey.OK -> "Select"; RemoteKey.BACK -> "Back"; RemoteKey.HOME -> "Home"
        // The ✱ button opens the options menu; Roku has no separate info key.
        RemoteKey.MENU -> "Info"
        // Play is a play/pause toggle on Roku.
        RemoteKey.PLAY, RemoteKey.PAUSE -> "Play"
        RemoteKey.REWIND -> "Rev"; RemoteKey.FAST_FORWARD -> "Fwd"
        RemoteKey.POWER -> "PowerOff"
        RemoteKey.VOLUME_UP -> "VolumeUp"; RemoteKey.VOLUME_DOWN -> "VolumeDown"; RemoteKey.MUTE -> "VolumeMute"
        RemoteKey.CHANNEL_UP -> "ChannelUp"; RemoteKey.CHANNEL_DOWN -> "ChannelDown"
        else -> null
    }
    private val tvOnly = setOf(RemoteKey.POWER, RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN, RemoteKey.MUTE, RemoteKey.CHANNEL_UP, RemoteKey.CHANNEL_DOWN)

    data class Info(
        val device: TvDevice,
        val isTv: Boolean,
        val powerControl: Boolean,
        val volumeControl: Boolean,
    )
    /** Parses `query/device-info`; anything that is not a `<device-info>` document is not a Roku. */
    fun info(host: String, xml: String): Info? {
        if (!xml.contains("<device-info")) return null
        val fields = elements(xml)
        val isTv = fields["is-tv"] == "true"
        val vendor = fields["vendor-name"].orEmpty()
        val brand = when {
            vendor.contains("TCL", ignoreCase = true) -> TvBrand.TCL
            vendor.contains("Hisense", ignoreCase = true) -> TvBrand.HISENSE
            else -> TvBrand.ROKU
        }
        val name = listOf("user-device-name", "friendly-device-name", "default-device-name").firstNotNullOfOrNull { fields[it]?.ifBlank { null } }
            ?: listOf(vendor, "Roku").filter { it.isNotBlank() }.distinct().joinToString(" ")
        val model = fields["friendly-model-name"]?.ifBlank { null } ?: fields["model-name"].orEmpty()
        val mac = listOf("wifi-mac", "ethernet-mac").firstNotNullOfOrNull { fields[it]?.takeIf { mac -> macBytes(mac) != null } }.orEmpty()
        // Roku OS 15 reports both explicitly; older TVs only say that they are TVs.
        return Info(
            TvDevice(brand, host, name, model, mac, TvPlatform.ROKU), isTv,
            powerControl = fields["supports-tv-power-control"]?.let { it == "true" } ?: isTv,
            volumeControl = fields["supports-audio-volume-control"]?.let { it == "true" } ?: isTv,
        )
    }
    fun capabilities(info: Info) = RemoteCapabilities(
        keys = RemoteKey.entries.filter { key ->
            keyName(key) != null && when (key) {
                RemoteKey.POWER -> info.powerControl
                RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN, RemoteKey.MUTE -> info.volumeControl
                in tvOnly -> info.isTv
                else -> true
            }
        }.toSet(),
        text = true, apps = true,
    )
    fun keypress(host: String, name: String) = "${base(host)}/keypress/$name"
    /** One `Lit_` keypress per character; typed into the TV's on-screen keyboard. */
    fun literals(text: String): List<String> = text.map { "Lit_" + percentEncode(it.toString()) }
    fun launch(host: String, app: TvApp) = "${base(host)}/launch/${percentEncode(app.id)}"
    fun icon(host: String, app: TvApp) = "${base(host)}/query/icon/${percentEncode(app.id)}"

    /** `<app id="12" type="appl" version="...">Netflix</app>` entries of `query/apps`; only channels (`appl`) can be launched. */
    fun apps(xml: String): List<TvApp> = APP.findAll(xml).mapNotNull { match ->
        val attributes = ATTRIBUTE.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
        val id = attributes["id"]?.ifBlank { null } ?: return@mapNotNull null
        if (attributes["type"].let { it != null && it != "appl" }) return@mapNotNull null
        TvApp(id, unescape(match.groupValues[2]).trim().ifBlank { id })
    }.toList()

    private fun elements(xml: String): Map<String, String> =
        ELEMENT.findAll(xml).associate { it.groupValues[1] to unescape(it.groupValues[2]).trim() }
    private val ELEMENT = Regex("<([a-z0-9-]+)>([^<]*)</\\1>")
    private val APP = Regex("<app\\b([^>]*)>([^<]*)</app>")
    private val ATTRIBUTE = Regex("([a-z-]+)=\"([^\"]*)\"")
}

/** Named and decimal XML entities in element text. */
internal fun unescape(text: String): String = if (!text.contains('&')) text else text
    .replace(NUMERIC_ENTITY) { match -> match.groupValues[1].toIntOrNull()?.takeIf { it in 32..0xFFFF }?.toChar()?.toString() ?: "" }
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
private val NUMERIC_ENTITY = Regex("&#(\\d{1,5});")

/** RFC 3986 unreserved characters stay; every other UTF-8 byte is %XX. */
internal fun percentEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) append(c.toChar())
        else { append('%'); append(HEX[c shr 4]); append(HEX[c and 0xF]) }
    }
}
private const val HEX = "0123456789ABCDEF"
