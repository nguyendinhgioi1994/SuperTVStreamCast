package com.tuntech.supertvstreamcast.domain.cast

/** Inclusive byte range of a file of `size` bytes. */
data class ByteSpan(val start: Long, val end: Long) { val length get() = end - start + 1 }

/** The request line and headers the media server needs. */
data class MediaRequest(val method: String, val path: String, val range: String?)

object HttpRange {
    /**
     * `Range: bytes=a-b` / `a-` / `-n` against [size]. Null means "send the whole file"; a span outside
     * the file is reported as [UNSATISFIABLE]. Multiple ranges are answered with the first one only.
     */
    fun parse(header: String?, size: Long): ByteSpan? {
        val spec = header?.trim()?.takeIf { it.startsWith("bytes=", ignoreCase = true) }?.substring(6)?.substringBefore(',')?.trim() ?: return null
        val dash = spec.indexOf('-').takeIf { it >= 0 } ?: return null
        val from = spec.substring(0, dash).trim(); val to = spec.substring(dash + 1).trim()
        if (size <= 0) return UNSATISFIABLE
        return when {
            from.isEmpty() -> to.toLongOrNull()?.takeIf { it > 0 }?.let { ByteSpan((size - it).coerceAtLeast(0), size - 1) } ?: UNSATISFIABLE
            else -> {
                val start = from.toLongOrNull() ?: return null
                val end = (to.toLongOrNull() ?: (size - 1)).coerceAtMost(size - 1)
                if (start >= size || end < start) UNSATISFIABLE else ByteSpan(start, end)
            }
        }
    }
    val UNSATISFIABLE = ByteSpan(-1, -2)

    /** Request line + headers up to the blank line; null when it is not a GET/HEAD request. */
    fun request(head: List<String>): MediaRequest? {
        val parts = head.firstOrNull()?.split(' ') ?: return null
        if (parts.size < 2 || parts[0] !in setOf("GET", "HEAD")) return null
        val range = head.drop(1).firstOrNull { it.substringBefore(':').trim().equals("Range", ignoreCase = true) }?.substringAfter(':')?.trim()
        return MediaRequest(parts[0], parts[1], range)
    }

    /** Response head for a file of [size] bytes; [span] null means the whole file. */
    fun responseHead(mime: String, size: Long, span: ByteSpan?, dlna: String): String = buildString {
        when {
            span == UNSATISFIABLE -> append("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\n")
            span != null -> append("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes ${span.start}-${span.end}/$size\r\nContent-Length: ${span.length}\r\n")
            else -> append("HTTP/1.1 200 OK\r\nContent-Length: $size\r\n")
        }
        append("Content-Type: $mime\r\nAccept-Ranges: bytes\r\nConnection: close\r\n")
        // DLNA renderers (Samsung, LG, Sony) check these before they will stream or seek.
        append("transferMode.dlna.org: ${if (mime.startsWith("image/")) "Interactive" else "Streaming"}\r\n")
        append("contentFeatures.dlna.org: $dlna\r\n\r\n")
    }
    fun notFound() = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
}
