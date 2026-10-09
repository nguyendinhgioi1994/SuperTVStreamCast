package com.tuntech.supertvstreamcast.data

/**
 * Inflates a gzip file (concatenated members included), handing the output to [onChunk] as
 * `(buffer, length)` pieces; the buffer is reused between calls. Throws on corrupt or truncated input.
 */
expect fun gunzip(bytes: ByteArray, onChunk: (ByteArray, Int) -> Unit)
