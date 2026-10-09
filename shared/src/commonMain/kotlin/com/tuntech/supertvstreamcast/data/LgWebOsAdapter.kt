package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import com.tuntech.supertvstreamcast.platform.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** LG webOS SSAP. The client key the TV issues after approval is kept in [secrets] per TV address. */
class LgWebOsAdapter(private val secrets: SecretStore = MemorySecretStore(), private val open: SocketOpener) : RemoteAdapter {
    private var socket: TextSocket? = null
    private var pointer: TextSocket? = null
    private var nextId = 0

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        disconnect()
        // Newer firmware only serves the TLS port; older firmware only the plain one.
        val opened = try { open(host, "wss://$host:3001") } catch (e: CancellationException) { throw e } catch (_: Exception) { open(host, "ws://$host:3000") }
        try {
            opened.send(LgProtocol.register(secrets.get("lg.key.$host")))
            withTimeout(60_000) {
                while (true) when (val message = LgProtocol.message(opened.receive())) {
                    is LgProtocol.Message.Registered -> { secrets.put("lg.key.$host", message.clientKey); break }
                    is LgProtocol.Message.Failure -> error("Registration rejected")
                    else -> Unit
                }
            }
        } catch (e: Throwable) { opened.close(); throw e }
        socket = opened
        pointer = try {
            val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")["socketPath"]?.jsonPrimitive?.contentOrNull
            path?.takeIf { it.startsWith("ws://$host:") || it.startsWith("wss://$host:") }?.let { open(host, it) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        // The software information reports the network adapter address as `device_id`; used for Wake-on-LAN only.
        val mac = try {
            withTimeoutOrNull(2_000) { request("ssap://com.webos.service.update/getCurrentSWInformation") }
                ?.get("device_id")?.jsonPrimitive?.contentOrNull?.takeIf { macBytes(it) != null }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        return RemoteSession(TvDevice(TvBrand.LG, host, "LG webOS TV", mac = mac.orEmpty()), LgProtocol.capabilities(pointer != null))
    }
    override suspend fun send(key: RemoteKey) {
        LgProtocol.ssap(key)?.let { request(it); return }
        val name = LgProtocol.button(key) ?: error("Unsupported key")
        checkNotNull(pointer).send(LgProtocol.buttonFrame(name))
    }
    override suspend fun sendText(text: String) {
        request("ssap://com.webos.service.ime/insertText", buildJsonObject { put("text", text); put("replace", 0) })
    }
    override suspend fun apps(): List<TvApp> = LgProtocol.apps(request("ssap://com.webos.applicationManager/listLaunchPoints"))
    override suspend fun launch(app: TvApp) { request("ssap://system.launcher/launch", buildJsonObject { put("id", app.id) }) }
    override suspend fun move(dx: Int, dy: Int) = checkNotNull(pointer).send(LgProtocol.moveFrame(dx, dy))
    override suspend fun click() = checkNotNull(pointer).send(LgProtocol.CLICK_FRAME)

    /** Sends a request and waits for its own response; `returnValue: false` or an error fails. */
    private suspend fun request(uri: String, payload: JsonObject? = null): JsonObject {
        val socket = checkNotNull(socket)
        val id = "req_${nextId++}"
        socket.send(LgProtocol.request(id, uri, payload))
        return withTimeout(6_000) {
            var result: JsonObject? = null
            while (result == null) when (val message = LgProtocol.message(socket.receive())) {
                is LgProtocol.Message.Response -> if (message.id == id) { check(message.ok); result = message.payload }
                is LgProtocol.Message.Failure -> check(message.id != id)
                else -> Unit
            }
            result
        }
    }
    override fun disconnect() { pointer?.close(); socket?.close(); pointer = null; socket = null }
}
