package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

/** Downloads user-supplied playlists, Xtream Codes listings and XMLTV guides. Remote control lives in [RemoteAdapter] implementations. */
class TvRepository(private val client: HttpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 10_000 }
    }) {
    /** A single-line HTTP(S) URL is downloaded; anything else is treated as pasted M3U content. */
    suspend fun loadPlaylist(value: String): String {
        val trimmed = value.trim()
        if (!isRemoteSource(trimmed)) return value
        return loadText(trimmed, PLAYLIST_MAX_BYTES)
    }
    /** Body of a successful GET, rejecting redirects, oversize bodies and gzip payloads. URLs are never logged. */
    suspend fun loadBytes(url: String, maxBytes: Int): ByteArray {
        require(isStreamUrl(url))
        val response = client.get(url)
        check(response.status.isSuccess())
        val bytes = response.bodyAsChannel().readBuffer(maxBytes + 1L).readByteArray()
        check(bytes.size <= maxBytes)
        return bytes
    }
    suspend fun loadText(url: String, maxBytes: Int): String {
        val bytes = loadBytes(url, maxBytes)
        if (isGzip(bytes)) throw CompressedGuideException()
        return bytes.decodeToString()
    }
    /** Verifies the account with the server before listing live channels. */
    suspend fun loadXtream(login: XtreamLogin): IptvLibrary {
        val extension = XtreamApi.parseAccount(loadText(XtreamApi.accountUrl(login), 200_000))
        val categories = runCatching { loadText(XtreamApi.categoriesUrl(login), 2_000_000) }.getOrDefault("[]")
        val channels = XtreamApi.parseLive(login, extension, categories, loadText(XtreamApi.streamsUrl(login), XTREAM_MAX_BYTES))
        return IptvLibrary(channels, XtreamApi.guideUrl(login), channels.size >= LIBRARY_MAX_CHANNELS)
    }
    fun close() = client.close()
}
fun isRemoteSource(value: String) = !value.contains('\n') && isStreamUrl(value.trim())
