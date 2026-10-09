package com.tuntech.supertvstreamcast.data

import java.util.zip.GZIPInputStream

actual fun gunzip(bytes: ByteArray, onChunk: (ByteArray, Int) -> Unit) {
    val stream = try { GZIPInputStream(bytes.inputStream(), 64 * 1024) } catch (_: java.io.IOException) { throw IllegalArgumentException() }
    stream.use {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = try { stream.read(buffer) } catch (_: java.io.IOException) { throw IllegalArgumentException() }
            if (read < 0) break
            if (read > 0) onChunk(buffer, read)
        }
    }
}
