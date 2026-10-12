package com.tuntech.supertvstreamcast

import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.domain.RemoteKey
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.platform.AppPreferences
import com.tuntech.supertvstreamcast.ui.TvViewModel
import com.tuntech.supertvstreamcast.ui.UiError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TvViewModelEventTest {
    private class Prefs : AppPreferences {
        override var brand = TvBrand.SONY.name
        override var goal = ""
        override var lastDevice = ""
    }
    private val events = mutableListOf<Pair<String, Map<String, String>>>()

    private fun vmTest(respond: MockRequestHandler, block: suspend TestScope.(TvViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = TvViewModel(Prefs(), http = HttpClient(MockEngine(respond)), localIp = { null }, events = { name, params -> events += name to params })
        try { block(model) } finally {
            model.viewModelScope.cancel()
            testScheduler.runCurrent()
            Dispatchers.resetMain()
        }
    }

    /** Sony: IRCC codes, product info, network settings, then one IRCC command per key. */
    /** Commands answer on the mock engine's thread, so wait for the jobs instead of the scheduler. */
    private suspend fun TvViewModel.settle() = viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }

    private val sony: MockRequestHandler = { request ->
        when {
            request.url.encodedPath == "/sony/ircc" -> respond("", HttpStatusCode.OK)
            (request.body as io.ktor.http.content.TextContent).text.contains("getRemoteControllerInfo") ->
                respond("""{"result":[{"type":"IRCC"},[{"name":"Confirm","value":"AAAAAQAAAAEAAABlAw=="}]]}""", HttpStatusCode.OK)
            (request.body as io.ktor.http.content.TextContent).text.contains("getNetworkSettings") ->
                respond("""{"result":[[{"netif":"wlan0","hwAddr":"","ipAddrV4":"192.168.1.2"}]]}""", HttpStatusCode.OK)
            else -> respond("""{"result":[{"productCategory":"tv","productName":"BRAVIA","modelName":"XR-55A80L"}]}""", HttpStatusCode.OK)
        }
    }

    @Test fun successIsLoggedOnlyAfterTheTvAnsweredAndFeatureUsedOncePerConnection() = vmTest(sony) { model ->
        model.connect("192.168.1.2", "psk")
        assertEquals(listOf("tv_connect_attempt"), events.map { it.first })
        model.state.first { it.connected && !it.busy }
        val attempt = mapOf("brand" to "sony", "discovered" to "false", "pairing_code" to "false")
        assertEquals(listOf("tv_connect_attempt" to attempt, "tv_connect_success" to attempt + ("device_brand" to "sony")), events.toList())

        model.send(RemoteKey.OK)
        model.send(RemoteKey.OK)
        model.settle()
        assertEquals(listOf(mapOf("feature" to "remote", "action" to "key", "brand" to "sony")), events.filter { it.first == "feature_used" }.map { it.second })
        // No parameter carries the host or the PSK.
        assertTrue(events.none { (_, params) -> params.values.any { "192.168" in it || "psk" in it } })
        // A key the TV did not report is never sent, so it is not a use.
        events.clear()
        model.send(RemoteKey.HOME)
        model.settle()
        assertTrue(events.isEmpty())
    }

    @Test fun rejectedConnectionLogsTheShownErrorAndNoSuccess() = vmTest({ respond("", HttpStatusCode.Forbidden) }) { model ->
        model.connect("192.168.1.2", "wrong")
        assertEquals(UiError.CONNECTION, model.state.first { it.error != null && !it.busy }.error)
        assertEquals(listOf("tv_connect_attempt", "tv_connect_fail"), events.map { it.first })
        assertEquals("connection", events.last().second["reason"])
        assertTrue(events.flatMap { it.second.values }.none { "192.168" in it || "wrong" in it })
    }

    @Test fun anAddressOutsideTheLocalNetworkIsNotAnAttempt() = vmTest(sony) { model ->
        model.connect("8.8.8.8", "psk")
        assertEquals(UiError.INVALID_IP, model.state.value.error)
        assertTrue(events.isEmpty())
    }
}
