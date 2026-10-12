package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.random.Random

/**
 * Vizio SmartCast. A stored token is used only after the TV accepted it on an authenticated request;
 * otherwise pairing starts, the TV shows a 4-digit PIN and [PairingCodeRequired] keeps the challenge for
 * the typed code. Key presses are acknowledged with `STATUS.RESULT`.
 */
class VizioAdapter(private val localHttp: (String) -> HttpClient, private val secrets: SecretStore) : RemoteAdapter {
    private var host = ""
    private var port = 0
    private var token = ""
    private var challenge: VizioProtocol.Challenge? = null
    private val deviceId by lazy { secrets.get(CLIENT_KEY) ?: newDeviceId().also { secrets.put(CLIENT_KEY, it) } }

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        val pending = challenge
        if (pending != null && secret.isNotBlank() && this.host == host) return submitCode(pending, secret)
        disconnect()
        val stored = secrets.get(tokenKey(host))?.split(':', limit = 2)?.takeIf { it.size == 2 }
        val storedPort = stored?.get(0)?.toIntOrNull()
        if (stored != null && storedPort != null) {
            val accepted = try { VizioProtocol.succeeded(request(host, storedPort, stored[1], HttpMethod.Get, VizioProtocol.POWER_MODE)) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
            // A refused token is replaced by the next successful pairing.
            if (accepted) return session(host, storedPort, stored[1])
        }
        var failure: Exception? = null
        for (candidate in VizioProtocol.PORTS) {
            try {
                val started = VizioProtocol.challenge(request(host, candidate, null, HttpMethod.Put, VizioProtocol.START_PAIRING,
                    VizioProtocol.startPairing(deviceId, SamsungProtocol.APP_NAME)))
                if (started != null) {
                    this.host = host; port = candidate; challenge = started
                    throw PairingCodeRequired(VizioProtocol.CODE_LENGTH)
                }
            } catch (e: PairingCodeRequired) { throw e } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        throw failure ?: IllegalStateException("No SmartCast pairing")
    }
    private suspend fun submitCode(pending: VizioProtocol.Challenge, code: String): RemoteSession {
        if (!VizioProtocol.validCode(code)) throw PairingCodeInvalid()
        val body = request(host, port, null, HttpMethod.Put, VizioProtocol.PAIR, VizioProtocol.pair(deviceId, pending, code))
        val issued = VizioProtocol.authToken(body)
        if (issued == null) {
            if (VizioProtocol.wrongCode(body)) throw PairingCodeInvalid()
            challenge = null
            error("Pairing refused")
        }
        challenge = null
        // Only a token the TV accepts on an authenticated request counts.
        check(VizioProtocol.succeeded(request(host, port, issued, HttpMethod.Get, VizioProtocol.POWER_MODE)))
        secrets.put(tokenKey(host), "$port:$issued")
        return session(host, port, issued)
    }
    private suspend fun session(host: String, port: Int, token: String): RemoteSession {
        this.host = host; this.port = port; this.token = token
        val model = try { VizioProtocol.model(request(host, port, token, HttpMethod.Get, VizioProtocol.DEVICE_INFO)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { "" }
        return RemoteSession(TvDevice(TvBrand.VIZIO, host, "Vizio SmartCast TV", model, platform = TvPlatform.VIZIO), VizioProtocol.capabilities)
    }
    override suspend fun send(key: RemoteKey) {
        check(token.isNotEmpty())
        check(VizioProtocol.succeeded(request(host, port, token, HttpMethod.Put, VizioProtocol.KEY_COMMAND, VizioProtocol.keyPress(key))))
    }
    override suspend fun sendText(text: String): Unit = error("Text unsupported")
    override suspend fun apps(): List<TvApp> = error("Apps unsupported")
    override suspend fun launch(app: TvApp): Unit = error("Apps unsupported")
    private suspend fun request(host: String, port: Int, auth: String?, method: HttpMethod, path: String, body: String? = null): String {
        val response = localHttp(host).request(VizioProtocol.base(host, port) + path) {
            this.method = method
            auth?.let { header("AUTH", it) }
            body?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
        // SmartCast answers refusals with a JSON status as well, so the body is read either way.
        val text = response.bodyAsText()
        check(response.status.isSuccess() || VizioProtocol.result(text) != null)
        return text
    }
    override fun disconnect() { host = ""; port = 0; token = ""; challenge = null }
    private fun tokenKey(host: String) = "vizio.token.$host"
    private fun newDeviceId() = buildString { repeat(16) { append("0123456789abcdef"[Random.nextInt(16)]) } }
    private companion object { const val CLIENT_KEY = "vizio.client" }
}
