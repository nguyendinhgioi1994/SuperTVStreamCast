package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

/**
 * Amazon Fire TV (sticks, Cube and Fire TV Edition TVs). The DIAL request starts the TV's remote service;
 * a stored client token counts only once the TV answered an authenticated app list with it. Otherwise the
 * TV shows a 4-digit PIN ([PairingCodeRequired]) and the token comes from pin/verify. Every command gets
 * an HTTP status, so a refused one is detected.
 */
class FireTvAdapter(
    private val plainHttp: HttpClient, private val localHttp: (String) -> HttpClient, private val secrets: SecretStore,
) : RemoteAdapter {
    private var host = ""
    private var token = ""
    private var awaitingCode = false

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        if (awaitingCode && secret.isNotBlank() && this.host == host) return submitCode(secret)
        disconnect()
        // A TV that is not a Fire TV refuses this port, so nothing appears on its screen.
        val started = plainHttp.post(FireTvProtocol.dial(host))
        check(started.status.isSuccess())
        secrets.get(tokenKey(host))?.let { stored ->
            val accepted = try { call(host, stored, HttpMethod.Get, FireTvProtocol.apps(host)); true }
                catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            if (accepted) return session(host, stored)
        }
        call(host, null, HttpMethod.Post, FireTvProtocol.displayPin(host), FireTvProtocol.displayPinBody(SamsungProtocol.APP_NAME))
        this.host = host; awaitingCode = true
        throw PairingCodeRequired(FireTvProtocol.CODE_LENGTH)
    }
    private suspend fun submitCode(code: String): RemoteSession {
        if (!FireTvProtocol.validCode(code)) throw PairingCodeInvalid()
        val response = localHttp(host).post(FireTvProtocol.verifyPin(host)) {
            fireTvHeaders(null); contentType(ContentType.Application.Json); setBody(FireTvProtocol.verifyPinBody(code))
        }
        val issued = if (response.status.isSuccess()) FireTvProtocol.token(response.bodyAsText()) else null
        // A wrong PIN is refused by the TV, which keeps showing it for another try.
        if (issued == null) throw PairingCodeInvalid()
        awaitingCode = false
        secrets.put(tokenKey(host), issued)
        return session(host, issued)
    }
    private fun session(host: String, token: String): RemoteSession {
        this.host = host; this.token = token
        return RemoteSession(TvDevice(TvBrand.AMAZON, host, "Fire TV", platform = TvPlatform.FIRE_TV), FireTvProtocol.capabilities)
    }
    override suspend fun send(key: RemoteKey) {
        val (url, body) = FireTvProtocol.keyUrl(active(), key) ?: error("Unsupported key")
        call(host, token, HttpMethod.Post, url, body)
    }
    override suspend fun sendText(text: String) {
        call(active(), token, HttpMethod.Post, FireTvProtocol.keyboard(host), FireTvProtocol.textBody(text))
    }
    /** Installed apps when the TV lists them, otherwise common shortcuts (a missing app reports a failure). */
    override suspend fun apps(): List<TvApp> =
        FireTvProtocol.installedApps(call(active(), token, HttpMethod.Get, FireTvProtocol.apps(host))).ifEmpty { FireTvProtocol.shortcuts }
    override suspend fun launch(app: TvApp) { call(active(), token, HttpMethod.Post, FireTvProtocol.launch(host, app)) }

    private suspend fun call(host: String, token: String?, method: HttpMethod, url: String, body: String? = null): String {
        val response = localHttp(host).request(url) {
            this.method = method
            fireTvHeaders(token)
            if (body != null) { contentType(ContentType.Application.Json.withParameter("charset", "utf-8")); setBody(body) }
        }
        check(response.status.isSuccess())
        return response.bodyAsText()
    }
    private fun HttpRequestBuilder.fireTvHeaders(token: String?) {
        header("X-Api-Key", FireTvProtocol.API_KEY)
        token?.let { header("X-Client-Token", it) }
    }
    private fun active() = host.also { check(it.isNotEmpty() && token.isNotEmpty()) }
    override fun disconnect() { host = ""; token = ""; awaitingCode = false }
    private fun tokenKey(host: String) = "firetv.token.$host"
}
