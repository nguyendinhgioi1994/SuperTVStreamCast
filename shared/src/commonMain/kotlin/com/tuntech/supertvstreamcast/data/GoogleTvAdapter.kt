package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.TlsChannel
import com.tuntech.supertvstreamcast.net.TlsOpener
import com.tuntech.supertvstreamcast.platform.SecretStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The TV is showing a pairing code; call `connect` again with it. */
class PairingCodeRequired : Exception()
/** The typed code does not belong to this pairing session; the session stays open for another try. */
class PairingCodeInvalid : Exception()

/**
 * Google TV / Android TV Remote Service v2. A TV is trusted only after the code shown on its screen
 * verified both certificates; its certificate fingerprint is then pinned in [secrets].
 */
class GoogleTvAdapter(private val open: TlsOpener, private val secrets: SecretStore) : RemoteAdapter {
    private var pairing: Pair<String, TlsChannel>? = null
    private var remote: TlsChannel? = null
    private var reader: Job? = null
    private val writes = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        if (secret.isBlank()) {
            disconnect()
            if (secrets.get(pinKey(host)) != null) {
                try { return control(host) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            startPairing(host)
        }
        val channel = pairing?.takeIf { it.first == host }?.second ?: startPairing(host)
        val hash = GoogleTvProtocol.secret(secret, Der.certificateKey(channel.localCertificate), Der.certificateKey(channel.peerCertificate))
            ?: throw PairingCodeInvalid()
        val pin = sha256(channel.peerCertificate).toHex()
        try {
            channel.writeMessage(GoogleTvProtocol.pairingSecret(hash))
            withTimeout(10_000) { while (GoogleTvProtocol.pairingStep(channel.readMessage()) != GoogleTvProtocol.PairingStep.SECRET_ACK) { } }
        } finally { disconnect() }
        secrets.put(pinKey(host), pin)
        return control(host)
    }
    /** Runs the pairing exchange until the TV displays its code, then always throws [PairingCodeRequired]. */
    private suspend fun startPairing(host: String): Nothing {
        disconnect()
        val channel = open(host, GoogleTvProtocol.PAIRING_PORT)
        try {
            channel.writeMessage(GoogleTvProtocol.pairingRequest(SamsungProtocol.APP_NAME))
            withTimeout(10_000) {
                while (true) when (GoogleTvProtocol.pairingStep(channel.readMessage())) {
                    GoogleTvProtocol.PairingStep.REQUEST_ACK -> channel.writeMessage(GoogleTvProtocol.pairingOption())
                    GoogleTvProtocol.PairingStep.OPTION -> channel.writeMessage(GoogleTvProtocol.pairingConfiguration())
                    GoogleTvProtocol.PairingStep.CONFIGURATION_ACK -> break
                    else -> Unit
                }
            }
        } catch (e: Throwable) { channel.close(); throw e }
        pairing = host to channel
        throw PairingCodeRequired()
    }
    /** Connected once the pinned TV has sent its configuration and accepted ours. */
    private suspend fun control(host: String): RemoteSession {
        val channel = open(host, GoogleTvProtocol.REMOTE_PORT)
        try {
            check(sha256(channel.peerCertificate).toHex() == secrets.get(pinKey(host)))
            var device: TvDevice? = null
            var features = 0
            withTimeout(10_000) {
                while (true) when (val event = GoogleTvProtocol.event(channel.readMessage())) {
                    is GoogleTvProtocol.Event.Configure -> {
                        features = GoogleTvProtocol.features(event.features)
                        val name = listOf(event.vendor, event.model).filter { it.isNotBlank() }.joinToString(" ")
                        device = TvDevice(TvBrand.GOOGLE, host, name.ifBlank { TvBrand.GOOGLE.title }, event.model)
                        channel.writeMessage(GoogleTvProtocol.configure(features))
                    }
                    GoogleTvProtocol.Event.Active -> { checkNotNull(device); channel.writeMessage(GoogleTvProtocol.active(features)); break }
                    GoogleTvProtocol.Event.Started -> { checkNotNull(device); break }
                    is GoogleTvProtocol.Event.Ping -> channel.writeMessage(GoogleTvProtocol.pong(event.value))
                    GoogleTvProtocol.Event.Failure -> error("Remote error")
                    GoogleTvProtocol.Event.Other -> Unit
                }
            }
            remote = channel
            reader = scope.launch { listen(channel, features) }
            return RemoteSession(checkNotNull(device), GoogleTvProtocol.capabilities)
        } catch (e: Throwable) { channel.close(); throw e }
    }
    /** The TV drops clients that stop answering pings, so the stream is read for the whole session. */
    private suspend fun listen(channel: TlsChannel, features: Int) {
        try {
            while (true) when (val event = GoogleTvProtocol.event(channel.readMessage())) {
                is GoogleTvProtocol.Event.Ping -> writes.withLock { channel.writeMessage(GoogleTvProtocol.pong(event.value)) }
                GoogleTvProtocol.Event.Active -> writes.withLock { channel.writeMessage(GoogleTvProtocol.active(features)) }
                else -> Unit
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            // The next command finds no session and reports the lost connection.
            if (remote === channel) remote = null
            channel.close()
        }
    }
    override suspend fun send(key: RemoteKey) {
        val channel = checkNotNull(remote)
        writes.withLock { channel.writeMessage(GoogleTvProtocol.key(key)) }
    }
    override suspend fun sendText(text: String): Unit = error("Text unsupported")
    override suspend fun apps(): List<TvApp> = error("Apps unsupported")
    override suspend fun launch(app: TvApp): Unit = error("Apps unsupported")
    override fun disconnect() {
        pairing?.second?.close(); pairing = null
        remote?.close(); remote = null
        reader?.cancel(); reader = null
    }
    private fun pinKey(host: String) = "google.cert.$host"
}

private suspend fun TlsChannel.writeMessage(body: ByteArray) = write(protoVarint(body.size) + body)
private suspend fun TlsChannel.readMessage(): ByteArray {
    var length = 0
    var shift = 0
    while (true) {
        val byte = read(1)[0].toInt()
        length = length or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0) break
        shift += 7
        check(shift <= 21)
    }
    check(length <= GoogleTvProtocol.MAX_MESSAGE_BYTES)
    return if (length == 0) ByteArray(0) else read(length)
}
