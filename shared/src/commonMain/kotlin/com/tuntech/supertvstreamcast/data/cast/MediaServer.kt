package com.tuntech.supertvstreamcast.data.cast

import com.tuntech.supertvstreamcast.domain.cast.HttpRange
import com.tuntech.supertvstreamcast.net.localIpv4Address
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readTo
import kotlin.random.Random

/** A picked file: its size and a way to read it from the start (the server skips to a range itself). */
class LocalMedia(val name: String, val mime: String, val size: Long, val open: () -> RawSource)

/**
 * Tiny HTTP server on the Wi-Fi address that lets a TV fetch files picked on the phone. Only
 * shared files are served, each under a random path segment, and only while casting; GET/HEAD
 * with byte ranges so the TV can seek. Nothing else on the phone is reachable through it.
 */
class MediaServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val files = mutableMapOf<String, LocalMedia>()
    private var selector: SelectorManager? = null
    private var server: ServerSocket? = null
    private var acceptor: Job? = null
    private var base: String? = null

    /** URL the TV can fetch [media] from, or null when the phone has no private Wi-Fi address. */
    suspend fun share(media: LocalMedia): String? = lock.withLock {
        val root = base ?: start() ?: return@withLock null
        val token = Random.nextLong().toULong().toString(36) + Random.nextInt().toUInt().toString(36)
        files[token] = media
        val ext = media.name.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.let { ".$it" } ?: ""
        "$root/$token/media$ext"
    }

    /** Forgets every shared file and closes the socket. */
    suspend fun stop() = lock.withLock {
        files.clear()
        acceptor?.cancel(); acceptor = null
        runCatching { server?.close() }; server = null
        runCatching { selector?.close() }; selector = null
        base = null
    }

    private suspend fun start(): String? {
        val ip = localIpv4Address() ?: return null
        val manager = SelectorManager(Dispatchers.IO)
        val socket = runCatching { aSocket(manager).tcp().bind(InetSocketAddress(ip, 0)) }.getOrElse { manager.close(); return null }
        val port = (socket.localAddress as? InetSocketAddress)?.port ?: run { socket.close(); manager.close(); return null }
        selector = manager; server = socket
        acceptor = scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { client.use { serve(it.openReadChannel(), it.openWriteChannel(autoFlush = false)) } }
            }
        }
        return "http://$ip:$port".also { base = it }
    }

    private suspend fun serve(input: io.ktor.utils.io.ByteReadChannel, output: ByteWriteChannel) {
        val head = withTimeoutOrNull(10_000) {
            buildList<String> { while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; add(line); if (size > 64) break } }
        } ?: return
        val request = HttpRange.request(head)
        val token = request?.path?.trimStart('/')?.substringBefore('/')
        val media = token?.let { lock.withLock { files[it] } }
        if (request == null || media == null) { output.writeStringUtf8(HttpRange.notFound()); output.finish(); return }
        val span = HttpRange.parse(request.range, media.size)
        val dlna = if (media.mime.startsWith("image/")) "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=00F00000000000000000000000000000"
            else "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        output.writeStringUtf8(HttpRange.responseHead(media.mime, media.size, span, dlna))
        if (request.method == "HEAD" || span == HttpRange.UNSATISFIABLE) { output.finish(); return }
        val start = span?.start ?: 0L
        var remaining = span?.length ?: media.size
        runCatching {
            media.open().buffered().use { source ->
                source.skip(start)
                val chunk = Buffer()
                val bytes = ByteArray(CHUNK)
                while (remaining > 0) {
                    val read = source.readAtMostTo(chunk, minOf(CHUNK.toLong(), remaining))
                    if (read <= 0) break
                    val n = read.toInt()
                    chunk.readTo(bytes, 0, n)
                    output.writeFully(bytes, 0, n)
                    output.flush()
                    remaining -= n
                }
            }
        }
        output.finish()
    }

    private suspend fun ByteWriteChannel.finish() { runCatching { flushAndClose() } }

    fun close() { scope.cancel() }

    private companion object { const val CHUNK = 64 * 1024 }
}
