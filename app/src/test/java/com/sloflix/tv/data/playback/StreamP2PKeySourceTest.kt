package com.sloflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StreamP2PKeySourceTest {
    @Test
    fun `extractKeysFromJs prefers known https key and iv literals`() {
        val js = """
            const name="AES-CBC";
            const other="abcdefghijklmnop";
            const k="${StreamP2PCrypto.HttpsKeyUtf8}";
            const iv="${StreamP2PCrypto.HttpsIvUtf8}";
            crypto.subtle.importKey("raw", k, {name}, true, ["decrypt"]);
        """.trimIndent()

        val keys = StreamP2PKeySource.extractKeysFromJs(js)

        assertNotNull(keys)
        assertEquals(StreamP2PCrypto.HttpsKeyUtf8, keys!!.first)
        assertEquals(StreamP2PCrypto.HttpsIvUtf8, keys.second)
    }

    @Test
    fun `extractKeysFromJs discovers rotated 16-char literals near AES-CBC`() {
        val js = """
            function decrypt(hex){
              const algo={name:"AES-CBC",iv:enc("rotatediv16bytes")};
              return subtle.decrypt(algo, importKey("rotatedkey16byte"), hex);
            }
        """.trimIndent()

        val keys = StreamP2PKeySource.extractKeysFromJs(js)

        assertNotNull(keys)
        assertEquals("rotatedkey16byte", keys!!.first)
        assertEquals("rotatediv16bytes", keys.second)
    }

    @Test
    fun `extractKeysFromJs returns null when no 16-char candidates`() {
        assertNull(StreamP2PKeySource.extractKeysFromJs("var x = 1; AES-CBC only"))
    }

    @Test
    fun `findAssetIndexScriptSrc parses module script from HTML`() {
        val html = """
            <!doctype html><html><head>
            <script type="module" crossorigin src="/assets/index-BHB3gR9K.js"></script>
            </head></html>
        """.trimIndent()

        assertEquals("/assets/index-BHB3gR9K.js", StreamP2PKeySource.findAssetIndexScriptSrc(html))
    }
}
