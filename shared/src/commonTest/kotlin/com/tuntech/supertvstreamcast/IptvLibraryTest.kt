package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.IptvIndex
import com.tuntech.supertvstreamcast.data.IptvCipher
import com.tuntech.supertvstreamcast.data.IptvStore
import com.tuntech.supertvstreamcast.data.aesCtr
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import com.tuntech.supertvstreamcast.data.passcodeHash
import com.tuntech.supertvstreamcast.domain.*
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.*

class IptvLibraryTest {
    private fun channel(id: Int,kind: ContentKind=ContentKind.LIVE)=Channel("https://e.com/$id","Channel $id","",kind=kind)

    @Test fun favoritesRecentsAndHiddenAreSeparateListsOverOneEntry() {
        val a=channel(1); val b=channel(2); val c=channel(3)
        var entries=emptyList<LibraryEntry>().toggleFavorite(b,"s1").toggleFavorite(a,"s1").watched(a,"s1",10).watched(c,"s2",20)
        assertEquals(listOf(a,b),entries.list(LibraryList.FAVORITES).map{it.channel})
        assertEquals(listOf(c,a),entries.list(LibraryList.RECENT).map{it.channel})
        assertEquals(3,entries.size)
        // A hidden channel leaves favorites and recents but keeps its state for when it is shown again.
        entries=entries.setHidden(a,"s1",true)
        assertEquals(listOf(b),entries.list(LibraryList.FAVORITES).map{it.channel})
        assertEquals(listOf(c),entries.list(LibraryList.RECENT).map{it.channel})
        assertEquals(listOf(a),entries.list(LibraryList.HIDDEN).map{it.channel})
        entries=entries.cleared(LibraryList.FAVORITES).cleared(LibraryList.RECENT)
        assertEquals(listOf(a),entries.map{it.channel})
        assertTrue(entries.single().favorite)
        entries=entries.cleared(LibraryList.HIDDEN)
        assertEquals(listOf(a),entries.list(LibraryList.FAVORITES).map{it.channel})
        // Entries that say nothing are dropped; deleting a source drops its entries.
        assertTrue(entries.toggleFavorite(a,"s1").watched(a,"s1",5).cleared(LibraryList.RECENT).isEmpty())
        assertEquals(listOf("s2"),emptyList<LibraryEntry>().toggleFavorite(a,"s1").toggleFavorite(c,"s2").withoutSource("s1").map{it.sourceId})
    }
    @Test fun historyIsBoundedAndKeepsTheSnapshotCurrent() {
        var entries=emptyList<LibraryEntry>().toggleFavorite(channel(0),"s")
        for(i in 0..RECENT_LIMIT+4) entries=entries.watched(channel(i),"s",100L+i)
        val recent=entries.list(LibraryList.RECENT)
        assertEquals(RECENT_LIMIT,recent.size)
        assertEquals("Channel ${RECENT_LIMIT+4}",recent.first().channel.title)
        // The oldest watched channel is still a favorite, just no longer in the history.
        assertEquals(RECENT_LIMIT+1,entries.size)
        assertEquals(0,entries.first{it.favorite}.watchedAt)
        val renamed=channel(9).copy(title="Renamed")
        assertEquals("Renamed",entries.watched(renamed,"s",999).list(LibraryList.RECENT).first().channel.title)
    }
    @Test fun videoProgressResumesExceptAtTheVeryStartOrEnd() {
        val movie=channel(1,ContentKind.MOVIE)
        val entries=emptyList<LibraryEntry>().watched(movie,"s",1)
        fun at(position: Long,duration: Long)=entries.progress(movie.url,position,duration).single()
        assertEquals(0,resumePosition(null))
        assertEquals(0,resumePosition(at(9_000,600_000)))
        assertEquals(120_000,resumePosition(at(120_000,600_000)))
        assertEquals(0,resumePosition(at(590_000,600_000)))
        assertEquals(120_000,resumePosition(at(120_000,0)))
        assertEquals(0.2f,at(120_000,600_000).progress)
        assertNull(at(120_000,0).progress)
        // Progress of something that was never opened is not recorded.
        assertTrue(emptyList<LibraryEntry>().toggleFavorite(movie,"s").progress(movie.url,5,10).single().positionMs==0L)
    }
    @Test fun refreshIsDueOnlyForDownloadableSourcesWithAnInterval() {
        val playlist=IptvSource("s1",SourceType.PLAYLIST,"List","https://e.com/list.m3u",refreshHours=24,syncedAt=1_000)
        assertFalse(isRefreshDue(playlist,1_000+24*3600-1))
        assertTrue(isRefreshDue(playlist,1_000+24*3600))
        assertFalse(isRefreshDue(playlist.copy(refreshHours=0),Long.MAX_VALUE/2))
        assertFalse(isRefreshDue(playlist.copy(type=SourceType.FILE),Long.MAX_VALUE/2))
        assertTrue(isRefreshDue(playlist.copy(type=SourceType.XTREAM),1_000+24*3600))
        assertFalse(isRefreshDue(playlist.copy(type=SourceType.STREAM),Long.MAX_VALUE/2))
    }
    @Test fun sourcesShareOnlyDownloadableLinksAndNeverPrintLogins() {
        val xtream=IptvSource("s1",SourceType.XTREAM,"Panel","http://panel.example.com:8080","user name","p@ss")
        assertEquals("http://panel.example.com:8080/get.php?username=user%20name&password=p%40ss&type=m3u_plus",xtream.shareUrl)
        assertEquals(XtreamLogin("http://panel.example.com:8080","user name","p@ss"),XtreamApi.credentials(xtream.shareUrl!!))
        assertEquals(XtreamLogin("http://panel.example.com:8080","user name","p@ss"),xtream.login)
        assertEquals("panel.example.com:8080",xtream.host)
        assertFalse(xtream.toString().contains("p@ss")||xtream.toString().contains("user name"))
        val playlist=IptvSource("s2",SourceType.PLAYLIST,"List","https://e.com/a/list.m3u?token=secret")
        assertEquals(playlist.url,playlist.shareUrl)
        assertEquals("e.com",playlist.host)
        assertNull(playlist.login)
        assertNull(IptvSource("s3",SourceType.FILE,"").shareUrl)
        assertNull(IptvSource("s4",SourceType.STREAM,"One","https://e.com/live.m3u8").shareUrl)
        assertTrue(xtream.sameOrigin(xtream.copy(id="other",name="Renamed")))
        assertFalse(xtream.sameOrigin(xtream.copy(username="someone")))
        assertEquals("sports",defaultSourceName("https://e.com/lists/sports.m3u?token=1"))
        assertEquals("e.com",defaultSourceName("https://e.com:8080/get.php?username=a&password=b"))
        assertEquals("e.com",defaultSourceName("https://e.com"))
    }
    @Test fun guessesVideoOnDemandFromAttributesLayoutAndExtension() {
        fun kind(url: String,vararg attributes: Pair<String,String>)=guessContentKind(url,attributes.toMap())
        assertEquals(ContentKind.LIVE,kind("https://e.com/live/u/p/1.m3u8"))
        assertEquals(ContentKind.LIVE,kind("https://e.com/1.ts"))
        assertEquals(ContentKind.LIVE,kind("https://e.com/play?file=a.mp4"))
        assertEquals(ContentKind.MOVIE,kind("https://e.com/film.MP4?token=1"))
        assertEquals(ContentKind.MOVIE,kind("https://e.com/movie/u/p/1.mkv"))
        assertEquals(ContentKind.MOVIE,kind("https://e.com/series/u/p/1"))
        assertEquals(ContentKind.MOVIE,kind("https://e.com/1","tvg-type" to "Movie"))
        assertEquals(ContentKind.LIVE,kind("https://e.com/a.mp4","type" to "live"))
        val playlist=ParsePlaylistUseCase()("#EXTM3U\n#EXTINF:-1,Film\nhttps://e.com/film.mkv\n#EXTINF:-1,News\nhttps://e.com/news.m3u8").channels
        assertEquals(listOf(ContentKind.MOVIE,ContentKind.LIVE),playlist.map{it.kind})
    }
    @Test fun passcodeFlowVerifiesCreatesAndConfirms() {
        val stored={pin: String -> pin=="1234"}
        fun PasscodeResult.flow()=(this as PasscodeResult.Next).flow
        // Opening hidden channels with a passcode set: one correct entry.
        val open=PasscodeFlow.start(PasscodePurpose.OPEN_HIDDEN,hasPasscode=true)
        assertEquals(PasscodeStep.VERIFY,open.step)
        val wrong=open.submit("0000",stored).flow()
        assertTrue(wrong.error)
        assertNotEquals(wrong,wrong.submit("0000",stored).flow())
        assertEquals(PasscodeResult.Verified,open.submit("1234",stored))
        // Without one, it is created and confirmed first; a mismatch starts over.
        val create=PasscodeFlow.start(PasscodePurpose.OPEN_HIDDEN,hasPasscode=false)
        assertEquals(PasscodeStep.CREATE,create.step)
        val confirm=create.submit("2468",stored).flow()
        assertEquals(PasscodeStep.CONFIRM,confirm.step)
        val retry=confirm.submit("2469",stored).flow()
        assertEquals(PasscodeStep.CREATE to true,retry.step to retry.error)
        assertEquals(PasscodeResult.Created("2468"),confirm.submit("2468",stored))
        // Changing it asks for the current one, then a new one twice.
        val change=PasscodeFlow.start(PasscodePurpose.CHANGE,hasPasscode=true)
        assertTrue(change.submit("1111",stored).flow().error)
        val next=change.submit("1234",stored).flow()
        assertEquals(PasscodeStep.CREATE,next.step)
        assertEquals(PasscodeResult.Created("9999"),next.submit("9999",stored).flow().submit("9999",stored))
        assertNotEquals(passcodeHash("1234","a"),passcodeHash("1234","b"))
        assertEquals(64,passcodeHash("1234","a").length)
        assertFalse(passcodeHash("1234","a").contains("1234"))
    }
    @Test fun importLinksRoundTripAndRejectOtherSchemes() {
        val request=ImportRequest("My list & more","https://e.com/get.php?username=a%20b&password=c%2Fd&type=m3u_plus")
        assertEquals(request,parseImportLink(buildImportLink(request.name,request.url)))
        assertEquals(ImportRequest("","https://e.com/list.m3u"),parseImportLink(" https://e.com/list.m3u "))
        assertEquals("https://e.com/l.m3u",parseImportLink("tvspace://import-playlist?url=https%3A%2F%2Fe.com%2Fl.m3u")?.url)
        listOf("tvspace://import-playlist?name=x","tvspace://import-playlist?url=file%3A%2F%2F%2Fa","tvspace://other?url=https%3A%2F%2Fe.com","iptv://x","").forEach {
            assertNull(parseImportLink(it),it)
        }
    }
    @Test fun sealsWithAes256CtrAndRejectsAlteredData() {
        fun hex(value: String)=value.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        // NIST SP 800-38A F.5.5 (CTR-AES256.Encrypt), first block.
        val key=hex("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4")
        val iv=hex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        val plain=hex("6bc1bee22e409f96e93d7e117393172a")
        assertContentEquals(hex("601ec313775789a5b7a7f504bbf3d228"),aesCtr(key,iv,plain))
        assertContentEquals(plain,aesCtr(key,iv,aesCtr(key,iv,plain)))
        val cipher=IptvCipher(MemorySecretStore())
        val text="playlist https://e.com/list.m3u?token=secret".encodeToByteArray()
        val sealed=cipher.seal(text)
        assertTrue(cipher.isSealed(sealed))
        assertFalse(cipher.isSealed(text))
        assertContentEquals(text,cipher.open(sealed))
        assertFalse(sealed.decodeToString().contains("secret"))
        assertFalse(sealed.contentEquals(cipher.seal(text)))
        assertContentEquals(ByteArray(0),cipher.open(cipher.seal(ByteArray(0))))
        assertNull(cipher.open(sealed.copyOf().also{it[it.size/2]=(it[it.size/2]+1).toByte()}))
        assertNull(cipher.open(sealed.copyOf(sealed.size-1)))
        assertNull(cipher.open(text))
        assertNull(IptvCipher(MemorySecretStore()).open(sealed))
    }
    @Test fun storeKeepsSourcesEntriesAndCatalogsAcrossInstances() = runTest {
        val directory=FileSystem.SYSTEM_TEMPORARY_DIRECTORY/"tvspace-iptv-test-${kotlin.random.Random.nextLong().toString(16)}"
        try {
            val secrets=MemorySecretStore()
            val store=IptvStore({directory.toString()},IptvCipher(secrets))
            assertEquals(IptvIndex(),store.loadIndex())
            assertNull(store.loadCatalog("s1"))
            val movie=Channel("https://e.com/film.mkv","Phim \"hay\"\nlắm","Điện ảnh","id","https://e.com/l.png","UA/1","https://e.com/",ContentKind.MOVIE)
            val catalog=listOf(channel(1),movie)
            val source=IptvSource("s1",SourceType.XTREAM,"Panel","http://e.com","user","pass",refreshHours=24,addedAt=1,syncedAt=2,channelCount=2,
                guideUrl="http://e.com/xmltv.php",account=XtreamAccount("Active",9,2,1,true,"ts"))
            val index=IptvIndex(listOf(source),emptyList<LibraryEntry>().toggleFavorite(movie,"s1").watched(movie,"s1",7).progress(movie.url,5,10),"hash","salt")
            store.saveCatalog("s1",catalog)
            store.saveIndex(index)
            store.saveIndex(index)
            val reopened=IptvStore({directory.toString()},IptvCipher(secrets))
            assertEquals(index,reopened.loadIndex())
            assertEquals(catalog,reopened.loadCatalog("s1"))
            // Nothing readable is on disk, and another installation's key opens nothing.
            listOf("index.json","catalog_s1.jsonl").forEach {name ->
                val raw=FileSystem.SYSTEM.read(directory/name) {readByteString()}.utf8()
                assertFalse(raw.contains("e.com")||raw.contains("pass")||raw.contains("Panel"),name)
            }
            val stranger=IptvStore({directory.toString()},IptvCipher(MemorySecretStore()))
            assertEquals(IptvIndex(),stranger.loadIndex())
            assertNull(stranger.loadCatalog("s1"))
            // A library saved before sealing existed is still read.
            FileSystem.SYSTEM.write(directory/"catalog_old.jsonl") {writeUtf8("""{"url":"https://e.com/1","title":"Channel 1","group":""}"""+"\n")}
            assertEquals(listOf(channel(1)),reopened.loadCatalog("old"))
            reopened.saveCatalog("s1",emptyList())
            assertEquals(emptyList(),reopened.loadCatalog("s1"))
            reopened.deleteCatalog("s1")
            reopened.deleteCatalog("s1")
            assertNull(reopened.loadCatalog("s1"))
            // A damaged library file reads as empty instead of failing; ids cannot escape the directory.
            FileSystem.SYSTEM.write(directory/"index.json") {writeUtf8("{not json")}
            assertEquals(IptvIndex(),reopened.loadIndex())
            assertFailsWith<IllegalArgumentException>{reopened.saveCatalog("../x",catalog)}
            assertNull(reopened.loadCatalog("../x"))
        } finally {FileSystem.SYSTEM.deleteRecursively(directory)}
    }
}
