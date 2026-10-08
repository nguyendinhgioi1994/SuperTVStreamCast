package com.tuntech.supertvstreamcast.net

import io.ktor.client.HttpClient

/**
 * Client for one TV on the LAN. TVs present self-signed certificates, so TLS is accepted only for
 * [host] and pinned to the first leaf certificate seen during this client's lifetime (trust on first
 * use, per session). Requests to any other host are refused. Never used for internet traffic.
 */
expect fun pinnedLocalClient(host: String): HttpClient
/** Short-timeout client for LAN discovery probes (plain HTTP/WebSocket only). */
expect fun discoveryClient(): HttpClient
/** Private IPv4 address of the Wi-Fi interface, or null when not on a private network. */
expect fun localIpv4Address(): String?
