package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/** The server could not be reached or answered with an error status (as opposed to an unusable body). */
class DownloadException : Exception()

/**
 * Downloads user-supplied playlists, Xtream Codes listings and XMLTV guides. Bodies are parsed
 * while they stream, off the main thread. Remote control lives in [RemoteAdapter] implementations.
 */
class TvRepository(private val client: HttpClient = HttpClient {
        // Provider links commonly redirect (short links, load balancers), also from HTTPS to HTTP.
        install(HttpRedirect) { allowHttpsDowngrade = true }
        install(HttpTimeout) { requestTimeoutMillis = 300_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 30_000 }
    }) {
    /**
     * A single-line HTTP(S) URL is downloaded; anything else is treated as pasted M3U content.
     * [onProgress] receives the channel count while a download is read.
     * @throws HlsStreamException when the link is one HLS stream rather than a channel list.
     */
    suspend fun loadPlaylist(value: String, onProgress: (Int) -> Unit = {}): IptvLibrary = withContext(Dispatchers.Default) {
        val trimmed = value.trim()
        if (!isRemoteSource(trimmed)) return@withContext ParsePlaylistUseCase()(value)
        val parser = PlaylistParser()
        val lines = LineSplitter()
        val feed = { line: String -> parser.feed(line).also { if (parser.size % PROGRESS_STEP == 0) onProgress(parser.size) } }
        var complete = true
        download(trimmed, PLAYLIST_MAX_BYTES) { bytes, length -> lines.feed(bytes, length, feed).also { complete = it } }
        if (complete) lines.finish(feed)
        parser.finish()
    }
    /**
     * Verifies the account with the server, then lists live channels, movies and series. A panel
     * without one of them (or answering it with an error) still imports the others.
     */
    suspend fun loadXtream(login: XtreamLogin, onProgress: (Int) -> Unit = {}): IptvLibrary = withContext(Dispatchers.Default) {
        val account = XtreamApi.parseAccountInfo(loadText(XtreamApi.accountUrl(login), 1_000_000))
        val channels = ArrayList<Channel>()
        var truncated = false
        var failure: Exception? = null
        for (kind in ContentKind.entries) {
            try {
                val categories = try { XtreamApi.categories(loadText(XtreamApi.categoriesUrl(login, kind), 4_000_000)) }
                    catch (_: DownloadException) { emptyMap() } catch (_: IllegalArgumentException) { emptyMap() }
                val reader = XtreamApi.LiveReader(login, account.liveFormat, categories, kind = kind)
                val text = Utf8Chunks()
                val before = channels.size
                download(XtreamApi.streamsUrl(login, kind), XTREAM_MAX_BYTES) { bytes, length ->
                    reader.feed(text.decode(bytes, length)).also { if (reader.size % PROGRESS_STEP == 0) onProgress(before + reader.size) }
                }
                val part = reader.finish()
                channels += part.channels
                truncated = truncated || part.truncated
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (kind == ContentKind.LIVE) failure = e }
        }
        // The live listing's own failure is the one worth reporting when nothing at all was listed.
        if (channels.isEmpty()) throw failure ?: IllegalArgumentException()
        IptvLibrary(channels, XtreamApi.guideUrl(login), truncated, account)
    }
    /** Seasons and episodes of one series, fetched when the user opens it. */
    suspend fun loadSeries(login: XtreamLogin, series: Channel): SeriesInfo = withContext(Dispatchers.Default) {
        XtreamApi.parseSeries(login, loadText(XtreamApi.seriesInfoUrl(login, XtreamApi.seriesId(series)), 8_000_000))
    }
    /** XMLTV guide (plain or gzip) matched against [channels]. */
    suspend fun loadGuide(url: String, channels: List<Channel>, now: Long): Map<String, List<Programme>> = withContext(Dispatchers.Default) {
        parseGuideBytes(loadBytes(url, GUIDE_FILE_MAX_BYTES), channels, now)
    }
    suspend fun loadBytes(url: String, maxBytes: Int): ByteArray {
        val buffer = Buffer()
        download(url, maxBytes) { bytes, length -> buffer.write(bytes, 0, length); true }
        return buffer.readByteArray()
    }
    suspend fun loadText(url: String, maxBytes: Int): String = loadBytes(url, maxBytes).decodeToString()

    /**
     * Streams the body of a successful GET to [onChunk] until it returns false. Fails when the body
     * exceeds [maxBytes]. URLs are never logged.
     */
    private suspend fun download(url: String, maxBytes: Int, onChunk: (ByteArray, Int) -> Boolean) {
        require(isStreamUrl(url))
        // Body problems (oversize, parser errors) leave through `failure` so they are not mistaken for transport errors.
        var failure: Exception? = null
        try {
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) throw DownloadException()
                val channel = response.bodyAsChannel()
                val bytes = ByteArray(64 * 1024)
                var total = 0L
                while (failure == null) {
                    val read = channel.readAvailable(bytes, 0, bytes.size)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) failure = IllegalStateException()
                    else try { if (!onChunk(bytes, read)) break } catch (e: Exception) { failure = e }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { if (failure == null) throw DownloadException() }
        failure?.let { throw it }
    }
    fun close() = client.close()
}
fun isRemoteSource(value: String) = !value.contains('\n') && isStreamUrl(value.trim())

/** Guide bytes from a download or a device file; gzip is inflated in chunks and never held whole. */
fun parseGuideBytes(bytes: ByteArray, channels: List<Channel>, now: Long): Map<String, List<Programme>> {
    val parser = GuideParser(channels, now)
    val text = Utf8Chunks()
    var total = 0L
    val feed = { chunk: ByteArray, length: Int ->
        total += length
        require(total <= GUIDE_MAX_BYTES)
        parser.feed(text.decode(chunk, length))
    }
    if (isGzip(bytes)) gunzip(bytes, feed)
    else {
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(256 * 1024, bytes.size - offset)
            feed(bytes.copyOfRange(offset, offset + length), length)
            offset += length
        }
    }
    return parser.finish()
}
