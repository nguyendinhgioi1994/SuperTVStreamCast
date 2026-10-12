package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.cast.LocalMedia
import com.tuntech.supertvstreamcast.data.cast.MediaServer
import com.tuntech.supertvstreamcast.net.localIpv4Address
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.*

/** The media server on a real socket, as a TV would call it: whole file, byte ranges, HEAD and unknown paths. */
class MediaServerHostTest {
    private val bytes = ByteArray(200_000) { (it % 251).toByte() }

    @Test fun servesSharedFilesWithRanges() = runBlocking {
        if (localIpv4Address() == null) return@runBlocking // No private network on this machine.
        val server = MediaServer()
        try {
            val url = server.share(LocalMedia("clip.mp4", "video/mp4", bytes.size.toLong()) { Buffer().apply { write(bytes) } })!!
            assertTrue(url.endsWith("/media.mp4"))

            val whole = open(url)
            assertEquals(200, whole.responseCode)
            assertEquals("video/mp4", whole.contentType)
            assertEquals("bytes", whole.getHeaderField("Accept-Ranges"))
            assertContentEquals(bytes, whole.inputStream.readBytes())

            val part = open(url) { setRequestProperty("Range", "bytes=1000-1999") }
            assertEquals(206, part.responseCode)
            assertEquals("bytes 1000-1999/200000", part.getHeaderField("Content-Range"))
            assertContentEquals(bytes.copyOfRange(1000, 2000), part.inputStream.readBytes())

            val head = open(url) { requestMethod = "HEAD" }
            assertEquals(200, head.responseCode)
            assertEquals("200000", head.getHeaderField("Content-Length"))
            assertNotNull(head.getHeaderField("contentFeatures.dlna.org"))

            assertEquals(404, open(url.substringBeforeLast("/").substringBeforeLast("/") + "/guess/media.mp4").responseCode)
            assertEquals(416, open(url) { setRequestProperty("Range", "bytes=500000-") }.responseCode)

            server.stop()
            assertFailsWith<java.io.IOException> { open(url).responseCode }
        } finally {
            server.stop(); server.close()
        }
    }

    private fun open(url: String, setup: HttpURLConnection.() -> Unit = {}) =
        (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 3_000; readTimeout = 5_000; setup() }
}
