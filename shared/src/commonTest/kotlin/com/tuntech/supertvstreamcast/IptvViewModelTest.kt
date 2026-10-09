package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.IptvCipher
import com.tuntech.supertvstreamcast.data.IptvStore
import com.tuntech.supertvstreamcast.data.TvRepository
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import com.tuntech.supertvstreamcast.ui.UiError
import com.tuntech.supertvstreamcast.ui.iptv.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okio.FileSystem
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IptvViewModelTest {
    private val directory=FileSystem.SYSTEM_TEMPORARY_DIRECTORY/"tvspace-iptv-vm-${kotlin.random.Random.nextLong().toString(16)}"
    private val secrets=MemorySecretStore()
    private var clock=1_000_000L
    private val models=mutableListOf<IptvViewModel>()
    private val playlist="#EXTM3U\n#EXTINF:-1 group-title=\"News\",One\nhttps://e.com/1.m3u8\n#EXTINF:-1 group-title=\"Films\",Film\nhttps://e.com/film.mp4\n"

    /**
     * Main is a queued dispatcher on the test's scheduler: work coming back from a download thread
     * runs on the test thread, never beside it. Every test waits for `busy` to clear before it ends.
     */
    private fun vmTest(block: suspend TestScope.()->Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {block()} finally {
            // Stop what the view models left queued (saves, the watch-time counter) before Main goes away.
            models.forEach {it.viewModelScope.cancel()}
            testScheduler.runCurrent()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }
    private fun TestScope.store(): IptvStore=IptvStore({directory.toString()},IptvCipher(secrets),dispatcher=UnconfinedTestDispatcher(testScheduler))
    private fun TestScope.model(respond: MockRequestHandler={respond(playlist,HttpStatusCode.OK)}): IptvViewModel=
        IptvViewModel(store(),TvRepository(HttpClient(MockEngine(respond))),{clock}).also {models+=it}
    private suspend fun IptvViewModel.await(condition: (IptvUiState)->Boolean): IptvUiState=state.first(condition)
    private suspend fun IptvViewModel.ready(): IptvUiState=await{it.loaded&&!it.busy}

    @Test fun addedSourcesSurviveANewViewModelAndReloadTheirChannels() = vmTest {
        val first=model()
        first.ready()
        first.addPlaylist(" https://e.com/lists/sports.m3u ")
        val added=first.await{it.sources.isNotEmpty()&&!it.busy}
        val source=added.sources.single()
        assertEquals(Triple(SourceType.PLAYLIST,"sports","https://e.com/lists/sports.m3u"),Triple(source.type,source.name,source.url))
        assertEquals(2 to clock,source.channelCount to source.syncedAt)
        assertEquals(listOf(IptvScreen.Hub,IptvScreen.Source),added.stack)
        assertEquals(listOf(ContentKind.LIVE,ContentKind.MOVIE),added.channels.map{it.kind})
        assertEquals(IptvNotice.ADDED,added.notice)
        // The same link again updates the source instead of adding a second one.
        first.addPlaylist("https://e.com/lists/sports.m3u","Other name")
        assertEquals(listOf("sports"),first.await{it.imported==2&&!it.busy}.sources.map{it.name})
        first.favorite(added.channels[0])
        first.rename(source.id,"  My list ")
        first.refreshEvery(source.id,24)
        first.await{it.sources.single().refreshHours==24}

        testScheduler.runCurrent()
        assertEquals(24,store().loadIndex().sources.single().refreshHours)
        var downloads=0
        val second=model{downloads++;respond(playlist,HttpStatusCode.OK)}
        val restored=second.ready()
        assertEquals("My list" to 24,restored.sources.single().let{it.name to it.refreshHours})
        assertEquals(setOf("https://e.com/1.m3u8"),restored.favorites)
        assertTrue(restored.channels.isEmpty())
        second.open(source.id)
        assertEquals(2,second.await{it.channels.isNotEmpty()}.channels.size)
        assertEquals(0,downloads)
        // Once the refresh interval has passed, opening the source downloads it again.
        clock+=24*3600
        val third=model{downloads++;respond(playlist+"#EXTINF:-1,New\nhttps://e.com/new.m3u8\n",HttpStatusCode.OK)}
        third.ready()
        third.open(source.id)
        assertEquals(3,third.await{it.channels.size==3&&!it.busy}.channels.size)
        assertEquals(1,downloads)
        assertEquals(setOf("https://e.com/1.m3u8"),third.state.value.favorites)
    }
    @Test fun failedDownloadsAreReportedAndLeaveTheLibraryAlone() = vmTest {
        val model=model{respond("",HttpStatusCode.NotFound)}
        model.ready()
        model.addPlaylist("https://e.com/missing.m3u")
        assertEquals(UiError.DOWNLOAD,model.await{it.error!=null&&!it.busy}.error)
        assertTrue(model.state.value.sources.isEmpty())
        val html=model{respond("<html>login</html>",HttpStatusCode.OK)}
        html.ready()
        html.addPlaylist("https://e.com/page")
        assertEquals(UiError.PLAYLIST,html.await{it.error!=null&&!it.busy}.error)
        html.addXtream("","user","pass")
        assertEquals(UiError.XTREAM_INPUT,html.state.value.error)
        html.addStream("rtmp://e.com/a","")
        assertEquals(UiError.STREAM_URL,html.state.value.error)
    }
    @Test fun playingTracksHistoryResumesVideosAndMinimizes() = vmTest {
        val model=model()
        model.ready()
        model.addPlaylist("https://e.com/list.m3u")
        val channels=model.await{it.channels.size==2&&!it.busy}.channels
        val (live,film)=channels
        model.play(film,channels)
        assertEquals(film to 0L,model.state.value.player!!.let{it.channel to it.startMs})
        model.progress(film.url,120_000,600_000)
        model.zap(1)
        assertEquals(live,model.state.value.player!!.channel)
        // Live channels never record a position; the film resumes where it stopped.
        model.progress(live.url,5_000,0)
        assertEquals(0,model.state.value.entry(live.url)!!.positionMs)
        clock+=5
        model.play(film,channels)
        assertEquals(120_000,model.state.value.player!!.startMs)
        assertEquals(listOf(film,live),model.state.value.entries.list(LibraryList.RECENT).map{it.channel})
        // Back shrinks the player and keeps it; back again leaves the source; close stops it.
        assertTrue(model.state.value.canGoBack)
        model.back()
        assertTrue(model.state.value.player!!.minimized)
        assertEquals(IptvScreen.Source,model.state.value.screen)
        model.expand()
        assertFalse(model.state.value.player!!.minimized)
        model.ended()
        assertEquals(0,model.state.value.entry(film.url)!!.positionMs)
        model.closePlayer()
        assertNull(model.state.value.player)
        model.back()
        assertEquals(IptvScreen.Hub,model.state.value.screen)
        assertFalse(model.state.value.canGoBack)
    }
    @Test fun hiddenChannelsNeedThePasscodeAndDeletingASourceForgetsItsChannels() = vmTest {
        val model=model()
        model.ready()
        model.addPlaylist("https://e.com/list.m3u")
        val state=model.await{it.channels.size==2&&!it.busy}
        val live=state.channels[0]
        model.favorite(live)
        model.hide(live,true)
        assertEquals(setOf(live.url),model.state.value.hidden)
        assertTrue(model.state.value.entries.list(LibraryList.FAVORITES).isEmpty())
        model.openLibrary(LibraryList.HIDDEN)
        assertEquals(PasscodeStep.CREATE,(model.state.value.screen as IptvScreen.Passcode).flow.step)
        model.submitPasscode("1234")
        model.submitPasscode("1234")
        assertEquals(IptvScreen.Library(LibraryList.HIDDEN),model.state.value.screen)
        assertTrue(model.state.value.passcodeSet)
        model.back()
        model.openLibrary(LibraryList.HIDDEN)
        model.submitPasscode("0000")
        assertTrue((model.state.value.screen as IptvScreen.Passcode).flow.error)
        model.submitPasscode("1234")
        assertEquals(IptvScreen.Library(LibraryList.HIDDEN),model.state.value.screen)
        model.hide(live,false)
        assertEquals(listOf(live),model.state.value.entries.list(LibraryList.FAVORITES).map{it.channel})
        model.delete(state.sources.single().id)
        val after=model.state.value
        assertTrue(after.sources.isEmpty()&&after.entries.isEmpty()&&after.channels.isEmpty())
        assertEquals(listOf<IptvScreen>(IptvScreen.Hub),after.stack)
    }
    @Test fun freeLimitsAskForPremiumInsteadOfAddingOrPlaying() = vmTest {
        val model=model()
        model.ready()
        model.access(premium=false,limits=IptvLimits(maxSources=1,dailyWatchSeconds=20))
        model.addPlaylist("https://e.com/a.m3u")
        val channels=model.await{it.sources.size==1&&!it.busy}.channels
        assertFalse(model.state.value.premiumRequired)
        model.addPlaylist("https://e.com/b.m3u")
        assertTrue(model.state.value.premiumRequired)
        assertEquals(1,model.state.value.sources.size)
        model.clearPremiumRequired()
        model.addStream("https://e.com/live.m3u8","")
        assertTrue(model.state.value.premiumRequired)
        model.clearPremiumRequired()
        // Playback is counted while something plays and stops at the daily limit.
        model.play(channels[0],channels)
        assertNotNull(model.state.value.player)
        testScheduler.advanceTimeBy(25_000)
        assertNull(model.state.value.player)
        assertTrue(model.state.value.premiumRequired)
        model.clearPremiumRequired()
        model.play(channels[0],channels)
        assertNull(model.state.value.player)
        // A new day starts a new allowance, and premium has no limits at all.
        clock+=86_400
        model.play(channels[0],channels)
        assertNotNull(model.state.value.player)
        model.closePlayer()
        model.access(premium=true,limits=IptvLimits(maxSources=1,dailyWatchSeconds=20))
        model.clearPremiumRequired()
        model.addPlaylist("https://e.com/b.m3u")
        assertEquals(2,model.await{it.sources.size==2&&!it.busy}.sources.size)
    }
    @Test fun linksFromOutsideWaitForConfirmationAndRemindersAreKept() = vmTest {
        val model=model()
        model.ready()
        model.offerImport("tvspace://import-playlist?name=Shared&url=https%3A%2F%2Fe.com%2Fshared.m3u")
        assertEquals(ImportRequest("Shared","https://e.com/shared.m3u"),model.state.value.pendingImport)
        assertTrue(model.state.value.sources.isEmpty())
        model.acceptImport()
        assertEquals("Shared",model.await{it.sources.isNotEmpty()&&!it.busy}.sources.single().name)
        assertNull(model.state.value.pendingImport)
        model.scanned("not a link")
        assertEquals(IptvNotice.QR_INVALID,model.state.value.notice)
        model.offerImport("mailto:someone")
        assertEquals(UiError.PLAYLIST,model.state.value.error)
        val soon=Reminder(reminderId("https://e.com/1.m3u8",clock+600),"https://e.com/1.m3u8","One",clock+600,"News",clock+300)
        model.addReminder(soon)
        model.addReminder(soon.copy(at=clock+600))
        assertEquals(listOf(clock+600),model.state.value.reminders.map{it.at})
        assertEquals(soon.id,model.state.value.reminder("https://e.com/1.m3u8",clock+600)?.id)
        model.removeReminder(soon.id)
        assertTrue(model.state.value.reminders.isEmpty())
        assertEquals(listOf(0,5),reminderOffsets(clock+400,clock))
        assertTrue(reminderOffsets(clock,clock).isEmpty())
        assertEquals(IptvLimits(3,1800),parseIptvLimits("""{"maximumSources":3,"maximumWatchSeconds":"1800","other":true}"""))
        assertEquals(IptvLimits(),parseIptvLimits("not json"))
        assertEquals(IptvLimits(),parseIptvLimits("""{"maximumSources":-4}"""))
    }
}
