package com.tuntech.supertvstreamcast

import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.data.cast.CastController
import com.tuntech.supertvstreamcast.data.cast.LocalMedia
import com.tuntech.supertvstreamcast.data.cast.ReceiverSource
import com.tuntech.supertvstreamcast.domain.cast.*
import com.tuntech.supertvstreamcast.ui.cast.CastError
import com.tuntech.supertvstreamcast.ui.cast.CastItem
import com.tuntech.supertvstreamcast.ui.cast.CastViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CastViewModelTest {
    private val tv = CastReceiver("uuid:1", "Living Room TV", CastKind.DLNA)

    private class FakeController(override val receiver: CastReceiver, var accept: Boolean) : CastController {
        val loaded = mutableListOf<CastMedia>()
        var status = CastStatus(PlaybackState.PLAYING, 1_000, 60_000, 30)
        override suspend fun load(media: CastMedia) = accept.also { if (it) loaded += media }
        override suspend fun play() = true
        override suspend fun pause() = true
        override suspend fun stop() = true
        override suspend fun seek(positionMs: Long) = true
        override suspend fun setVolume(volume: Int) = true
        override suspend fun status() = status
        override fun release() = Unit
    }
    private class FakeSource(val controller: FakeController?) : ReceiverSource {
        override val kind = CastKind.DLNA
        override fun discover(): Flow<List<CastReceiver>> = flowOf(listOfNotNull(controller?.receiver))
        override suspend fun connect(receiver: CastReceiver) = controller
    }

    private val events = mutableListOf<Pair<String, Map<String, String>>>()

    private fun vmTest(controller: FakeController?, served: String? = "http://192.168.1.5:4000/t/media.jpg", block: suspend TestScope.(CastViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = CastViewModel(listOf(FakeSource(controller)), share = { served }, stopSharing = {}, events = { n, p -> events += n to p })
        try { block(model) } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun photo(name: String) = CastItem(name, MediaKind.PHOTO, "image/jpeg", local = LocalMedia(name, "image/jpeg", 10) { error("unused") })

    @Test fun scanListsReceiversAndEnds() = vmTest(FakeController(tv, true)) { model ->
        model.scan(seconds = 2)
        runCurrent()
        assertEquals(listOf(tv), model.state.value.receivers)
        assertTrue(model.state.value.scanning)
        advanceTimeBy(2_100)
        assertFalse(model.state.value.scanning)
        assertTrue(model.state.value.scanned)
    }

    @Test fun mediaPickedFirstIsSentOnceAReceiverIsChosen() = vmTest(FakeController(tv, true)) { model ->
        model.cast(listOf(photo("a.jpg"), photo("b.jpg")))
        assertTrue(model.state.value.waitingForReceiver)
        assertFalse(model.state.value.casting)
        model.select(tv)
        runCurrent()
        val s = model.state.value
        assertEquals(tv, s.selected)
        assertTrue(s.casting)
        assertEquals("feature_used", events.single().first)
        assertEquals(mapOf("feature" to "mirror", "media" to "photo", "receiver" to "dlna"), events.single().second)
        // The slideshow moves on by itself after five seconds.
        advanceTimeBy(5_100)
        assertEquals(1, model.state.value.index)
    }

    @Test fun refusedLoadIsNeverShownAsCasting() = vmTest(FakeController(tv, false)) { model ->
        model.select(tv); runCurrent()
        model.castLink("https://example.com/movie.mp4"); runCurrent()
        assertFalse(model.state.value.casting)
        assertEquals(CastError.LOAD, model.state.value.error)
        assertTrue(events.isEmpty())
    }

    @Test fun noWifiAddressMeansLocalFilesCannotBeServed() = vmTest(FakeController(tv, true), served = null) { model ->
        model.select(tv); runCurrent()
        model.cast(listOf(photo("a.jpg"))); runCurrent()
        assertEquals(CastError.NO_WIFI, model.state.value.error)
        assertFalse(model.state.value.casting)
    }

    @Test fun unreachableReceiverReportsConnectError() = vmTest(null) { model ->
        model.select(tv); runCurrent()
        assertNull(model.state.value.selected)
        assertEquals(CastError.CONNECT, model.state.value.error)
    }

    @Test fun badLinkIsRejected() = vmTest(FakeController(tv, true)) { model ->
        assertFalse(model.castLink("youtube video please"))
        assertEquals(CastError.BAD_LINK, model.state.value.error)
    }
}
