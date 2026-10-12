package com.tuntech.supertvstreamcast.domain.cast

import com.tuntech.supertvstreamcast.domain.isLocalIpv4

/** A DLNA Digital Media Renderer as described by its own device description. */
data class RendererDescription(
    val udn: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val avTransportUrl: String,
    val renderingControlUrl: String?,
)

/** UPnP AV (DLNA) pieces: SSDP search, device description, SOAP envelopes, DIDL-Lite and replies. */
object Upnp {
    const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    /** `LOCATION` of an SSDP search reply when it points at a private address, else null. */
    fun location(reply: String): String? {
        val lines = reply.split("\r\n", "\n")
        if (!lines.first().trim().startsWith("HTTP/1.1 200", ignoreCase = true)) return null
        val location = lines.drop(1).firstOrNull { it.substringBefore(':').trim().equals("LOCATION", ignoreCase = true) }
            ?.substringAfter(':')?.trim() ?: return null
        val host = HOST.find(location)?.groupValues?.get(1) ?: return null
        return location.takeIf { isLocalIpv4(host) }
    }

    /** Reads [xml] fetched from [location]. Null unless it is a renderer with an AVTransport service. */
    fun description(xml: String, location: String): RendererDescription? {
        val base = (tag(xml, "URLBase")?.takeIf { it.isNotBlank() } ?: location).let { origin(it) } ?: return null
        val services = SERVICE.findAll(xml).map { it.groupValues[1] }.associate { block ->
            tag(block, "serviceType").orEmpty() to tag(block, "controlURL").orEmpty()
        }
        val transport = services.entries.firstOrNull { it.key.startsWith(AV_TRANSPORT.dropLast(1)) }?.value?.takeIf { it.isNotBlank() } ?: return null
        val rendering = services.entries.firstOrNull { it.key.startsWith(RENDERING_CONTROL.dropLast(1)) }?.value?.takeIf { it.isNotBlank() }
        return RendererDescription(
            udn = tag(xml, "UDN") ?: location,
            name = tag(xml, "friendlyName")?.let(::unescape)?.ifBlank { null } ?: base.substringAfter("://").substringBefore(':'),
            manufacturer = tag(xml, "manufacturer")?.let(::unescape).orEmpty(),
            model = tag(xml, "modelName")?.let(::unescape).orEmpty(),
            avTransportUrl = resolve(base, transport),
            renderingControlUrl = rendering?.let { resolve(base, it) },
        )
    }

    fun envelope(service: String, action: String, args: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:$action xmlns:u=\"$service\">")
        args.forEach { (name, value) -> append("<$name>${escape(value)}</$name>") }
        append("</u:$action></s:Body></s:Envelope>")
    }
    fun soapAction(service: String, action: String) = "\"$service#$action\""

    /** DIDL-Lite metadata for SetAVTransportURI; many TVs refuse a URI without it. */
    fun didl(media: CastMedia): String {
        val upnpClass = when (media.kind) {
            MediaKind.PHOTO -> "object.item.imageItem.photo"
            MediaKind.AUDIO -> "object.item.audioItem.musicTrack"
            MediaKind.VIDEO -> "object.item.videoItem"
        }
        // OP=01: byte seek; FLAGS: streaming transfer mode, background transfer, DLNA 1.5.
        val flags = if (media.kind == MediaKind.PHOTO) "DLNA.ORG_FLAGS=00F00000000000000000000000000000" else "DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escape(media.title)}</dc:title><upnp:class>$upnpClass</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${media.mime}:$flags\">${escape(media.url)}</res></item></DIDL-Lite>"
    }

    /** First `<name>` value in a SOAP reply, unescaped; null when missing. */
    fun value(xml: String, name: String): String? = tag(xml, name)?.let(::unescape)

    /** UPnP error code of a SOAP fault, null when the reply is not a fault. */
    fun faultCode(xml: String): Int? = if (!xml.contains("Fault")) null else tag(xml, "errorCode")?.trim()?.toIntOrNull() ?: -1

    fun transportState(value: String?): PlaybackState = when (value?.trim()?.uppercase()) {
        "PLAYING" -> PlaybackState.PLAYING
        "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> PlaybackState.PAUSED
        "TRANSITIONING" -> PlaybackState.LOADING
        "STOPPED" -> PlaybackState.STOPPED
        "NO_MEDIA_PRESENT" -> PlaybackState.IDLE
        else -> PlaybackState.IDLE
    }

    /** `H+:MM:SS[.fff]` → ms; `NOT_IMPLEMENTED` and garbage → 0. */
    fun parseTime(value: String?): Long {
        val parts = value?.trim()?.substringBefore('.')?.split(':') ?: return 0
        if (parts.size != 3) return 0
        val (h, m, s) = parts.map { it.toLongOrNull() ?: return 0 }
        return ((h * 60 + m) * 60 + s) * 1000
    }
    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        return "${total / 3600}:${(total / 60 % 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
    }

    fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) { '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&apos;"); else -> append(c) }
        }
    }
    fun unescape(text: String): String =
        text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:[A-Za-z0-9_]+:)?$name(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_]+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()
    private fun origin(url: String): String? = ORIGIN.find(url.trim())?.value
    private fun resolve(origin: String, path: String): String = when {
        path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> path
        path.startsWith("/") -> origin + path
        else -> "$origin/$path"
    }
    private val HOST = Regex("^https?://([^/:]+)", RegexOption.IGNORE_CASE)
    private val ORIGIN = Regex("^https?://[^/]+", RegexOption.IGNORE_CASE)
    private val SERVICE = Regex("<(?:[A-Za-z0-9_]+:)?service>(.*?)</(?:[A-Za-z0-9_]+:)?service>", RegexOption.DOT_MATCHES_ALL)
}
