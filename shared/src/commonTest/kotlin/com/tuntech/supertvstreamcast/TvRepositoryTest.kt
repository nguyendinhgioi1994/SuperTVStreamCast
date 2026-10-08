package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.TvRepository
import com.tuntech.supertvstreamcast.domain.RemoteKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TvRepositoryTest {
    @Test fun authenticatesAndUsesCodesReturnedByTv() = runTest {
        var calls=0
        val repository=TvRepository(HttpClient(MockEngine {request ->
            calls++
            assertEquals("session-secret",request.headers["X-Auth-PSK"])
            if(calls==1) {
                assertEquals("/sony/system",request.url.encodedPath)
                respond("""{"result":[{"type":"IRCC"},[{"name":"Confirm","value":"AAAAAQAAAAEAAABlAw=="}]]}""",HttpStatusCode.OK)
            } else {
                assertEquals("/sony/ircc",request.url.encodedPath)
                assertTrue((request.body as TextContent).text.contains("AAAAAQAAAAEAAABlAw=="))
                assertEquals("\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"",request.headers["SOAPACTION"])
                respond("",HttpStatusCode.OK)
            }
        }))
        try {
            repository.connect("192.168.1.2","session-secret")
            repository.send(RemoteKey.OK)
            assertEquals(2,calls)
            repository.disconnect()
            assertFailsWith<IllegalStateException>{repository.send(RemoteKey.OK)}
            assertEquals(2,calls)
        } finally {repository.close()}
    }
    @Test fun rejectedAuthenticationNeverCreatesASession() = runTest {
        val repository=TvRepository(HttpClient(MockEngine {respond("",HttpStatusCode.Forbidden)}))
        try {
            assertFailsWith<IllegalStateException>{repository.connect("192.168.1.2","wrong")}
            assertFailsWith<IllegalStateException>{repository.send(RemoteKey.OK)}
        } finally {repository.close()}
    }
    @Test fun apiErrorsAndMissingCommandCapabilitiesFailConnection() = runTest {
        for(body in listOf("""{"error":[403,"Forbidden"]}""","""{"result":[{},[]]}""")) {
            val repository=TvRepository(HttpClient(MockEngine {respond(body,HttpStatusCode.OK)}))
            try {assertFailsWith<IllegalStateException>{repository.connect("192.168.1.2","key")}}
            finally {repository.close()}
        }
    }
    @Test fun playlistRedirectsAndOversizedResponsesAreRejected() = runTest {
        for((body,status) in listOf("" to HttpStatusCode.Found,"a".repeat(2_000_001) to HttpStatusCode.OK)) {
            val repository=TvRepository(HttpClient(MockEngine {respond(body,status)}))
            try {assertFailsWith<IllegalStateException>{repository.loadPlaylist("https://example.com/list.m3u")}}
            finally {repository.close()}
        }
    }
}
