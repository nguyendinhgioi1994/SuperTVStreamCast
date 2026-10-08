package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

/** Downloads user-supplied playlists. Remote control lives in [RemoteAdapter] implementations. */
class TvRepository(private val client: HttpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 5_000 }
    }) {
    suspend fun loadPlaylist(value: String): String {
        if (!value.trim().startsWith("https://")) return value
        require(isStreamUrl(value.trim()))
        val response = client.get(value.trim())
        check(response.status.isSuccess())
        val channel = response.bodyAsChannel()
        val bytes = channel.readBuffer(2_000_001L).readByteArray()
        check(bytes.size <= 2_000_000)
        return bytes.decodeToString()
    }
    fun close() = client.close()
}
