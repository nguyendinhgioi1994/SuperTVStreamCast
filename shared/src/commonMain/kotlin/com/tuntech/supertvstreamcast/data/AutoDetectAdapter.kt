package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import kotlinx.coroutines.CancellationException

/**
 * For brands that ship several TV platforms (TCL, Hisense, Panasonic, Toshiba, other TVs…): tries each platform's handshake in
 * order and keeps the first one that connects or asks for a pairing code. Nothing is assumed from the brand.
 */
class AutoDetectAdapter(private val candidates: List<() -> RemoteAdapter?>) : RemoteAdapter {
    private var active: RemoteAdapter? = null

    override suspend fun connect(host: String, secret: String): RemoteSession {
        // A typed code continues the platform that asked for it.
        active?.takeIf { secret.isNotBlank() }?.let { return it.connect(host, secret) }
        disconnect()
        var failure: Exception? = null
        for (create in candidates) {
            val adapter = create() ?: continue
            try {
                return adapter.connect(host, secret).also { active = adapter }
            } catch (e: PairingCodeRequired) {
                active = adapter; throw e
            } catch (e: Exception) {
                adapter.disconnect()
                // The TV answered as this platform but refused control: no other platform will do better.
                if (e is CancellationException || e is RemoteControlDisabled || e is UnsupportedTvModel) throw e
                if (failure == null) failure = e
            }
        }
        throw failure ?: IllegalStateException("No platform")
    }
    private fun current() = checkNotNull(active)
    override suspend fun send(key: RemoteKey) = current().send(key)
    override suspend fun sendText(text: String) = current().sendText(text)
    override suspend fun apps() = current().apps()
    override suspend fun launch(app: TvApp) = current().launch(app)
    override suspend fun move(dx: Int, dy: Int) = current().move(dx, dy)
    override suspend fun click() = current().click()
    override fun disconnect() { active?.disconnect(); active = null }
}
