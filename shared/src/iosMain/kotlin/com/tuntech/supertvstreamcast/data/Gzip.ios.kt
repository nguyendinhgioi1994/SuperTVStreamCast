@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.tuntech.supertvstreamcast.data

import kotlinx.cinterop.*
import platform.posix.memset
import platform.zlib.*

actual fun gunzip(bytes: ByteArray, onChunk: (ByteArray, Int) -> Unit) {
    if (bytes.isEmpty()) return
    memScoped {
        val stream = alloc<z_stream>()
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        // windowBits 15 + 16: zlib reads the gzip header and trailer itself.
        require(inflateInit2_(stream.ptr, 15 + 16, ZLIB_VERSION, sizeOf<z_stream>().toInt()) == Z_OK)
        try {
            val out = ByteArray(64 * 1024)
            bytes.usePinned { input -> out.usePinned { output ->
                stream.next_in = input.addressOf(0).reinterpret()
                stream.avail_in = bytes.size.convert()
                while (true) {
                    stream.next_out = output.addressOf(0).reinterpret()
                    stream.avail_out = out.size.convert()
                    val code = inflate(stream.ptr, Z_NO_FLUSH)
                    val produced = out.size - stream.avail_out.toInt()
                    if (produced > 0) onChunk(out, produced)
                    when (code) {
                        Z_OK -> Unit
                        // Another member may follow in a concatenated .gz file.
                        Z_STREAM_END -> if (stream.avail_in > 0u) require(inflateReset(stream.ptr) == Z_OK) else break
                        else -> throw IllegalArgumentException()
                    }
                }
            } }
        } finally { inflateEnd(stream.ptr) }
    }
}
