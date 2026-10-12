package com.tuntech.supertvstreamcast.data.cast

import com.tuntech.supertvstreamcast.domain.cast.CastKind
import com.tuntech.supertvstreamcast.domain.cast.CastMedia
import com.tuntech.supertvstreamcast.domain.cast.CastReceiver
import com.tuntech.supertvstreamcast.domain.cast.CastStatus
import com.tuntech.supertvstreamcast.domain.cast.RendererDescription
import com.tuntech.supertvstreamcast.domain.cast.Upnp
import com.tuntech.supertvstreamcast.net.LanBrowser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A session with one receiver. Every call returns false / null when the receiver did not accept it. */
interface CastController {
    val receiver: CastReceiver
    /** Sends [media] and starts it. True only when the receiver accepted the URI and the play command. */
    suspend fun load(media: CastMedia): Boolean
    suspend fun play(): Boolean
    suspend fun pause(): Boolean
    suspend fun stop(): Boolean
    suspend fun seek(positionMs: Long): Boolean
    suspend fun setVolume(volume: Int): Boolean
    /** Current state as the receiver reports it; null when it does not answer. */
    suspend fun status(): CastStatus?
    /** Leaves the session (Cast) / forgets the renderer (DLNA). Does not stop playback by itself. */
    fun release()
}

/** One family of receivers (DLNA, Chromecast). */
interface ReceiverSource {
    val kind: CastKind
    /** Receivers found so far, growing while collected; only started from a user action. */
    fun discover(): Flow<List<CastReceiver>>
    /** Null when the receiver is gone or refused the session. */
    suspend fun connect(receiver: CastReceiver): CastController?
}

/** DLNA / UPnP AV media renderers: found with SSDP, confirmed by their own device description. */
class DlnaSource(private val browser: LanBrowser, private val http: HttpClient = dlnaClient()) : ReceiverSource {
    override val kind = CastKind.DLNA
    private val known = mutableMapOf<String, RendererDescription>()
    private val lock = Mutex()

    override fun discover(): Flow<List<CastReceiver>> = channelFlow {
        val seen = mutableSetOf<String>()
        val found = linkedMapOf<String, CastReceiver>()
        browser.ssdp(listOf(Upnp.MEDIA_RENDERER, Upnp.AV_TRANSPORT)).collect { datagram ->
            val location = Upnp.location(datagram.payload.decodeToString()) ?: return@collect
            if (!seen.add(location)) return@collect
            launch {
                val description = runCatching {
                    http.get(location).takeIf { it.status.isSuccess() }?.let { Upnp.description(it.bodyAsText(), location) }
                }.getOrNull() ?: return@launch
                lock.withLock { known[description.udn] = description }
                val receiver = CastReceiver(description.udn, description.name, CastKind.DLNA,
                    listOf(description.manufacturer, description.model).filter { it.isNotBlank() }.joinToString(" "))
                val list = lock.withLock { found[receiver.id] = receiver; found.values.toList() }
                send(list)
            }
        }
    }

    override suspend fun connect(receiver: CastReceiver): CastController? =
        lock.withLock { known[receiver.id] }?.let { DlnaController(receiver, it, http) }
}

internal fun dlnaClient() = HttpClient {
    followRedirects = false
    install(HttpTimeout) { connectTimeoutMillis = 3_000; requestTimeoutMillis = 8_000; socketTimeoutMillis = 8_000 }
}

private class DlnaController(override val receiver: CastReceiver, private val renderer: RendererDescription, private val http: HttpClient) : CastController {
    override suspend fun load(media: CastMedia): Boolean {
        // Some renderers refuse a new URI while playing; a failed Stop is fine.
        transport("Stop")
        if (transport("SetAVTransportURI", "CurrentURI" to media.url, "CurrentURIMetaData" to Upnp.didl(media)) == null) return false
        return transport("Play", "Speed" to "1") != null
    }
    override suspend fun play() = transport("Play", "Speed" to "1") != null
    override suspend fun pause() = transport("Pause") != null
    override suspend fun stop() = transport("Stop") != null
    override suspend fun seek(positionMs: Long) = transport("Seek", "Unit" to "REL_TIME", "Target" to Upnp.formatTime(positionMs)) != null
    override suspend fun setVolume(volume: Int): Boolean {
        val url = renderer.renderingControlUrl ?: return false
        return soap(url, Upnp.RENDERING_CONTROL, "SetVolume", listOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to volume.coerceIn(0, 100).toString())) != null
    }
    override suspend fun status(): CastStatus? {
        val info = transport("GetTransportInfo") ?: return null
        val position = transport("GetPositionInfo")
        val volume = renderer.renderingControlUrl?.let { soap(it, Upnp.RENDERING_CONTROL, "GetVolume", listOf("InstanceID" to "0", "Channel" to "Master")) }
        return CastStatus(
            state = Upnp.transportState(Upnp.value(info, "CurrentTransportState")),
            positionMs = Upnp.parseTime(position?.let { Upnp.value(it, "RelTime") }),
            durationMs = Upnp.parseTime(position?.let { Upnp.value(it, "TrackDuration") }),
            volume = volume?.let { Upnp.value(it, "CurrentVolume")?.trim()?.toIntOrNull() },
        )
    }
    override fun release() = Unit

    private suspend fun transport(action: String, vararg args: Pair<String, String>) =
        soap(renderer.avTransportUrl, Upnp.AV_TRANSPORT, action, listOf("InstanceID" to "0") + args)

    /** The reply body, or null on a network error, an HTTP error or a SOAP fault. */
    private suspend fun soap(url: String, service: String, action: String, args: List<Pair<String, String>>): String? = runCatching {
        val response = http.post(url) {
            header("SOAPACTION", Upnp.soapAction(service, action))
            contentType(ContentType.parse("text/xml; charset=\"utf-8\""))
            setBody(Upnp.envelope(service, action, args))
        }
        val body = response.bodyAsText()
        body.takeIf { response.status.isSuccess() && Upnp.faultCode(it) == null }
    }.getOrNull()
}
