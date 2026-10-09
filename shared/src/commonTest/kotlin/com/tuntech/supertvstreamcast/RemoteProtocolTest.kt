package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.*
import kotlinx.serialization.json.*
import kotlin.test.*

class RemoteProtocolTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    @Test fun sonyCapabilitiesComeOnlyFromReportedCodes() {
        val codes=mapOf("Confirm" to "A","EPG" to "B","Num5" to "C","Forward" to "D")
        val capabilities=SonyProtocol.capabilities(codes)
        assertEquals(setOf(RemoteKey.OK,RemoteKey.GUIDE,RemoteKey.NUM_5,RemoteKey.FAST_FORWARD),capabilities.keys)
        assertEquals("B",SonyProtocol.code(codes,RemoteKey.GUIDE))
        assertNull(SonyProtocol.code(codes,RemoteKey.POWER))
        assertFailsWith<IllegalStateException>{SonyProtocol.ircc("<x/>")}
    }
    @Test fun sonyIdentityRequiresTvCategoryAndAppsNeedUri() {
        assertEquals("KD-55X85K",SonyProtocol.identity("10.0.0.5","""{"result":[{"productCategory":"tv","modelName":"KD-55X85K","productName":""}]}""")?.model)
        assertNull(SonyProtocol.identity("10.0.0.5","""{"result":[{"productCategory":"camera"}]}"""))
        assertNull(SonyProtocol.identity("10.0.0.5","not json"))
        assertEquals(listOf(TvApp("com.sony.dtv.netflix","Netflix")),SonyProtocol.apps("""{"result":[[{"title":"Netflix","uri":"com.sony.dtv.netflix"},{"title":"Broken"}]]}"""))
        assertFailsWith<IllegalStateException>{SonyProtocol.apps("""{"error":[401,"Unauthorized"]}""")}
    }
    @Test fun samsungMessagesMatchRemoteChannelFormat() {
        val key=json(SamsungProtocol.key(RemoteKey.VOLUME_UP)).getValue("params").jsonObject
        assertEquals("Click",key["Cmd"]?.jsonPrimitive?.content)
        assertEquals("KEY_VOLUP",key["DataOfCmd"]?.jsonPrimitive?.content)
        assertEquals("SendRemoteKey",key["TypeOfRemote"]?.jsonPrimitive?.content)
        assertEquals("KEY_7",SamsungProtocol.keyCode(RemoteKey.NUM_7))
        val text=json(SamsungProtocol.text("hi")).getValue("params").jsonObject
        assertEquals("aGk=",text["Cmd"]?.jsonPrimitive?.content)
        assertEquals("SendInputString",text["TypeOfRemote"]?.jsonPrimitive?.content)
        assertEquals("wss://192.168.1.9:8002/api/v2/channels/samsung.remote.control?name=VFYgU3BhY2U=&token=42",SamsungProtocol.url("192.168.1.9",true,"42"))
        assertEquals("ws://192.168.1.9:8001/api/v2/channels/samsung.remote.control?name=VFYgU3BhY2U=",SamsungProtocol.url("192.168.1.9",false,null))
    }
    @Test fun samsungInfoAndEventsAreParsed() {
        val info=SamsungProtocol.info("192.168.1.9","""{"device":{"type":"Samsung SmartTV","name":"Living room","modelName":"QE55Q80","TokenAuthSupport":"true"}}""")
        assertEquals(true,info?.tokenAuth); assertEquals("Living room",info?.device?.name)
        assertNull(SamsungProtocol.info("192.168.1.9","""{"device":{"type":"Speaker"}}"""))
        assertEquals(SamsungProtocol.Event.Connected("123"),SamsungProtocol.event("""{"event":"ms.channel.connect","data":{"token":"123"}}"""))
        assertEquals(SamsungProtocol.Event.Denied,SamsungProtocol.event("""{"event":"ms.channel.unauthorized"}"""))
        val apps=SamsungProtocol.event("""{"event":"ed.installedApp.get","data":{"data":[{"appId":"111299001912","name":"YouTube","app_type":2},{"name":"x"}]}}""")
        assertEquals(SamsungProtocol.Event.Apps(listOf(TvApp("111299001912","YouTube") to 2)),apps)
        assertEquals(SamsungProtocol.Event.Other,SamsungProtocol.event("garbage"))
    }
    @Test fun lgRegistrationRequestsAndPointerFrames() {
        val register=json(LgProtocol.register("key-1"))
        assertEquals("register",register["type"]?.jsonPrimitive?.content)
        assertEquals("key-1",register.getValue("payload").jsonObject["client-key"]?.jsonPrimitive?.content)
        assertNull(json(LgProtocol.register(null)).getValue("payload").jsonObject["client-key"])
        assertEquals("ssap://audio/volumeUp",LgProtocol.ssap(RemoteKey.VOLUME_UP))
        assertEquals("ENTER",LgProtocol.button(RemoteKey.OK))
        assertEquals("3",LgProtocol.button(RemoteKey.NUM_3))
        assertEquals("type:button\nname:UP\n\n",LgProtocol.buttonFrame("UP"))
        assertFalse(RemoteKey.UP in LgProtocol.capabilities(pointer=false).keys)
        assertTrue(RemoteKey.VOLUME_UP in LgProtocol.capabilities(pointer=false).keys)
        assertTrue(RemoteKey.UP in LgProtocol.capabilities(pointer=true).keys)
        assertFalse(RemoteKey.INPUT in LgProtocol.capabilities(pointer=true).keys)
    }
    @Test fun lgMessagesAreClassified() {
        assertEquals(LgProtocol.Message.Registered("abc"),LgProtocol.message("""{"type":"registered","id":"register_0","payload":{"client-key":"abc"}}"""))
        assertEquals(LgProtocol.Message.Failure("r1"),LgProtocol.message("""{"type":"error","id":"r1","error":"401"}"""))
        val response=LgProtocol.message("""{"type":"response","id":"r2","payload":{"returnValue":false}}""")
        assertIs<LgProtocol.Message.Response>(response); assertFalse(response.ok)
        assertEquals(listOf(TvApp("netflix","Netflix")),LgProtocol.apps(json("""{"launchPoints":[{"id":"netflix","title":"Netflix"},{"title":"no id"}]}""")))
    }
    @Test fun subnetHostsExcludeSelfAndRejectPublicNetworks() {
        val hosts=subnetHosts("192.168.1.20")
        assertEquals(253,hosts.size); assertEquals("192.168.1.1",hosts.first()); assertEquals("192.168.1.254",hosts.last())
        assertFalse("192.168.1.20" in hosts)
        assertTrue(subnetHosts("8.8.8.8").isEmpty()); assertTrue(subnetHosts("bad").isEmpty())
    }
    @Test fun channelHelpersSupportGroupsZappingAndRecents() {
        val channels=listOf(Channel("https://a/1","One","News"),Channel("https://a/2","Two",""),Channel("https://a/3","Three","News"),Channel("https://a/4","Four","Sport"))
        assertEquals(listOf("News","Sport"),channelGroups(channels))
        assertEquals(channels[0],adjacentChannel(channels,channels[3],1))
        assertEquals(channels[3],adjacentChannel(channels,channels[0],-1))
        assertEquals(channels[0],adjacentChannel(channels,Channel("https://gone","x",""),1))
        assertNull(adjacentChannel(emptyList(),channels[0],1))
        assertEquals(listOf("b","a"),withRecent(listOf("a","b"),"b"))
        assertEquals(3,withRecent(listOf("a","b","c"),"d",limit=3).size)
    }
    @Test fun extGrpProvidesGroupWhenExtinfHasNone() {
        val channels=ParsePlaylistUseCase()("#EXTM3U\n#EXTINF:-1,Live\n#EXTGRP:Movies\nhttps://example.com/live\n").channels
        assertEquals("Movies",channels.single().group)
    }
    @Test fun swipesMapToDominantAxisAndIgnoreTaps() {
        assertEquals(RemoteKey.RIGHT,swipeDirection(80f,10f,40f))
        assertEquals(RemoteKey.UP,swipeDirection(-5f,-90f,40f))
        assertEquals(RemoteKey.LEFT,swipeDirection(-60f,30f,40f))
        assertNull(swipeDirection(10f,10f,40f))
    }
}
