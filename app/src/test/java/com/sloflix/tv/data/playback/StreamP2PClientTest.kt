package com.sloflix.tv.data.playback

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StreamP2PClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolve decrypts video API and returns HLS source with play token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(FixtureHex),
        )
        val client = StreamP2PClient(OkHttpClient())
        val embed = server.url("/#wemw1&folder=hhk").toString()

        val stream = client.resolve(embed).getOrThrow()

        assertTrue(stream.url.contains("/v4/demo/master.m3u8"))
        assertTrue(stream.url.contains("k=fixtureKey"))
        assertTrue(stream.url.contains("kx=1234567890"))
        assertEquals(1, stream.fallbackUrls.size)
        assertTrue(stream.fallbackUrls.single().contains("cf-master.txt"))
        assertTrue(stream.fallbackUrls.single().contains("k=fixtureKey"))
        assertEquals(server.url("/").toString(), stream.headers["Referer"])
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/v1/video"))
        assertTrue(recorded.path!!.contains("id=wemw1"))
        assertEquals("sloflix.com", recorded.requestUrl?.queryParameter("r"))
    }

    @Test
    fun `resolve does not scrape player JS when hardcoded decrypt succeeds`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(FixtureHex),
        )
        val scrapeCalls = AtomicInteger(0)
        val keySource = object : StreamP2PKeySource(OkHttpClient()) {
            override fun fetchAndExtractKeys(origin: String): Pair<String, String>? {
                scrapeCalls.incrementAndGet()
                return super.fetchAndExtractKeys(origin)
            }
        }
        val client = StreamP2PClient(OkHttpClient(), keySource = keySource)
        val embed = server.url("/#wemw1").toString()

        client.resolve(embed).getOrThrow()

        assertEquals(0, scrapeCalls.get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `resolve scrapes keys and retries when hardcoded decrypt fails`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(RotatedFixtureHex),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    <!doctype html><html><head>
                    <script type="module" src="/assets/index-fixture.js"></script>
                    </head></html>
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    const algo={name:"AES-CBC",iv:enc("rotatediv16bytes")};
                    importKey("rotatedkey16byte");
                    """.trimIndent(),
                ),
        )
        val client = StreamP2PClient(OkHttpClient())
        val embed = server.url("/#alt1").toString()

        val stream = client.resolve(embed).getOrThrow()

        assertTrue(stream.url.contains("/v4/alt/master.m3u8"))
        assertTrue(stream.url.contains("k=altKey"))
        assertEquals(3, server.requestCount)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/video"))
        assertEquals("/", server.takeRequest().path)
        assertEquals("/assets/index-fixture.js", server.takeRequest().path)
    }

    private companion object {
        const val FixtureHex =
            "a5dc8b828864323afe4092ba6d25aef4df85f8f907793acf83dbe69d0592d37f" +
                "65fd9a0814a276151d37866715ec3b3fd5394c6c95ce7a23bab932a6b3bd221e" +
                "3ad8b99f049fe7af7ffe77e21e86205c7993532c32751639916d74bbf4b94c2c" +
                "7bac7cfd18a6460ebe93db1adb8e12b4c9a5756781f240d9472c269e73db1450" +
                "bc42d1028d7d934a6b8c67cd95061704888d7eef78218b9f774ba53246aef58e" +
                "7b9d623410c51344a4fff9422700606cad529ac148b4a211c7b3da13a1ad8ebb" +
                "02a6f914662c1a1ce3ba2d57b6ab385a"

        /** AES-CBC with key `rotatedkey16byte` / IV `rotatediv16bytes`. */
        const val RotatedFixtureHex =
            "ba122bdcf8ab8882d2726248d7d0dc3f1191fe7045cc55a56fb97e2408339e6d" +
                "c02a9afac2c7c35495d98fbad35dd82f973568b491255ff89453dbeb77ba632e" +
                "a634abe781fe54cb1061d19ff041be84f9ad6333739d5dad845d9e3a7eb449b3" +
                "a5bf196cbf7e22c6b3a418943fb6154f0267254edd78bd76baac4650b94ed015" +
                "e369144e46c16abd42712bbb53e12225f56a1f006402642c1c8bdf223dcb7dba"
    }
}
