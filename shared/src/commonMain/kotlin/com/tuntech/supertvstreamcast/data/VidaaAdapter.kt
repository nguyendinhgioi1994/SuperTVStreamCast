package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.TlsChannel
import com.tuntech.supertvstreamcast.net.TlsOpener
import com.tuntech.supertvstreamcast.platform.SecretStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Hisense VIDAA over the TV's MQTT broker. Connected only after the TV answered a source-list request;
 * when it shows a 4-digit code first, [PairingCodeRequired] keeps the session open for the code.
 * The phone's identifier is kept in [secrets] so the TV remembers it; key presses are not acknowledged.
 */
class VidaaAdapter(private val open: TlsOpener, private val secrets: SecretStore, private val waitMillis: Long = 4_000) : RemoteAdapter {
    private var channel: TlsChannel? = null
    private var host = ""
    private var awaitingCode = false
    private var inbox = Channel<Mqtt.Publish>(64, BufferOverflow.DROP_OLDEST)
    private var jobs: List<Job> = emptyList()
    private val writes = Mutex()
    private val launchData = mutableMapOf<String, JsonObject>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clientId by lazy { secrets.get(CLIENT_KEY) ?: VidaaProtocol.newClientId().also { secrets.put(CLIENT_KEY, it) } }

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        if (awaitingCode && secret.isNotBlank() && this.host == host && channel != null) return submitCode(secret)
        disconnect()
        val opened = open(host, VidaaProtocol.PORT)
        // Socket reads block, so a silent broker is cut off by closing the channel.
        val watchdog = scope.launch { delay(8_000); opened.close() }
        try {
            opened.write(Mqtt.connect(clientId, VidaaProtocol.USERNAME, VidaaProtocol.PASSWORD, KEEP_ALIVE_SECONDS))
            check(Mqtt.connackCode(Mqtt.read(opened::read)) == 0) { "Credentials refused" }
            opened.write(Mqtt.subscribe(1, VidaaProtocol.subscriptions(clientId)))
            check(Mqtt.subackGranted(Mqtt.read(opened::read)))
        } catch (e: Throwable) { opened.close(); throw e } finally { watchdog.cancel() }
        channel = opened
        this.host = host
        start(opened)
        publish(VidaaProtocol.action(clientId, "ui_service", "gettvstate"))
        // A phone the TV does not know yet is challenged in reply to the state request.
        if (await(waitMillis) { it as? VidaaProtocol.Event.CodeRequested } != null) codeRequired()
        return verify()
    }
    private suspend fun submitCode(code: String): RemoteSession {
        val payload = VidaaProtocol.authCode(code) ?: throw PairingCodeInvalid()
        publish(VidaaProtocol.action(clientId, "ui_service", "authenticationcode"), payload)
        val result = await(10_000) { it as? VidaaProtocol.Event.CodeResult ?: it as? VidaaProtocol.Event.CodeClosed }
        if (result is VidaaProtocol.Event.CodeResult && !result.accepted) throw PairingCodeInvalid()
        check(result is VidaaProtocol.Event.CodeResult) { "Code prompt closed" }
        awaitingCode = false
        return verify()
    }
    /** Only a TV that answers a request counts as connected. */
    private suspend fun verify(): RemoteSession {
        publish(VidaaProtocol.action(clientId, "ui_service", "sourcelist"))
        val event = await(6_000) { it.takeIf { e -> e == VidaaProtocol.Event.Sources || e == VidaaProtocol.Event.CodeRequested } }
        if (event == VidaaProtocol.Event.CodeRequested) codeRequired()
        checkNotNull(event) { "No answer" }
        // The adapter address is optional; it only enables Wake-on-LAN.
        publish(VidaaProtocol.action(clientId, "platform_service", "getdeviceinfo"))
        val mac = await(2_000) { it as? VidaaProtocol.Event.DeviceInfo }?.mac.orEmpty()
        return RemoteSession(TvDevice(TvBrand.HISENSE, host, "Hisense VIDAA TV", mac = mac, platform = TvPlatform.VIDAA), VidaaProtocol.capabilities)
    }
    private fun codeRequired(): Nothing { awaitingCode = true; throw PairingCodeRequired(VidaaProtocol.CODE_LENGTH) }

    /** Reads every packet for the session (answering QoS 1 publishes) and pings so the broker keeps the client. */
    private fun start(opened: TlsChannel) {
        val messages = Channel<Mqtt.Publish>(64, BufferOverflow.DROP_OLDEST).also { inbox = it }
        jobs = listOf(
            scope.launch {
                try {
                    while (true) {
                        val packet = Mqtt.read(opened::read)
                        if (packet.type != Mqtt.PUBLISH) continue
                        val (message, id) = Mqtt.publish(packet)
                        id?.let { write(opened, Mqtt.puback(it)) }
                        messages.send(message)
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) {
                    if (channel === opened) channel = null
                    opened.close()
                } finally { messages.close() }
            },
            scope.launch {
                while (true) { delay(KEEP_ALIVE_SECONDS * 500L); runCatching { write(opened, Mqtt.PINGREQ) } }
            },
        )
    }
    /** Next event [pick] accepts, or null after [millis]; a closed session throws. */
    private suspend fun <T : Any> await(millis: Long, pick: (VidaaProtocol.Event) -> T?): T? = withTimeoutOrNull(millis) {
        var found: T? = null
        while (found == null) found = pick(VidaaProtocol.event(inbox.receive()))
        found
    }
    private suspend fun publish(topic: String, payload: String = "") = write(checkNotNull(channel), Mqtt.publish(topic, payload))
    private suspend fun write(target: TlsChannel, bytes: ByteArray) = writes.withLock { target.write(bytes) }

    override suspend fun send(key: RemoteKey) = publish(VidaaProtocol.key(clientId), VidaaProtocol.keyName(key) ?: error("Unsupported key"))
    override suspend fun sendText(text: String): Unit = error("Text unsupported")
    override suspend fun apps(): List<TvApp> {
        publish(VidaaProtocol.action(clientId, "ui_service", "applist"))
        val apps = checkNotNull(await(6_000) { it as? VidaaProtocol.Event.Apps }).apps
        apps.forEach { (app, data) -> launchData[app.id] = data }
        return apps.map { it.first }
    }
    override suspend fun launch(app: TvApp) =
        publish(VidaaProtocol.action(clientId, "ui_service", "launchapp"), checkNotNull(launchData[app.id]).toString())
    override fun disconnect() {
        jobs.forEach { it.cancel() }; jobs = emptyList()
        channel?.close(); channel = null
        awaitingCode = false
        inbox.close()
    }
    private companion object {
        const val CLIENT_KEY = "vidaa.client"
        const val KEEP_ALIVE_SECONDS = 30
    }
}
