package com.sloflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamP2PCryptoTest {
    @Test
    fun `https key and iv match browser derivation`() {
        assertEquals(StreamP2PCrypto.HttpsKeyUtf8, StreamP2PCrypto.buildKey("https:"))
        assertEquals(StreamP2PCrypto.HttpsIvUtf8, StreamP2PCrypto.buildIv("https:"))
        assertEquals(16, StreamP2PCrypto.deriveKey("https:").size)
        assertEquals(16, StreamP2PCrypto.deriveIv("https:").size)
    }

    @Test
    fun `decrypts fixture hex to JSON with HLS source field`() {
        val plain = StreamP2PCrypto.decryptHex(FixtureHex)
        assertTrue(plain.contains("\"source\""))
        assertTrue(plain.contains("https://cdn.example.com/v4/demo/master.m3u8"))
        assertTrue(plain.contains("\"cf\""))
    }

    @Test
    fun `decrypts with explicit key and iv`() {
        val plain = StreamP2PCrypto.decryptHex(
            hexCiphertext = RotatedFixtureHex,
            keyUtf8 = "rotatedkey16byte",
            ivUtf8 = "rotatediv16bytes",
        )
        assertTrue(plain.contains("/v4/alt/master.m3u8"))
    }

    private companion object {
        /**
         * Self-encrypted AES-CBC fixture (key/IV = https derivation). Not a live secret.
         * Plaintext:
         * {"source":"https://cdn.example.com/v4/demo/master.m3u8","cf":"https://cf.example.com/v4/js/demo/cf-master.txt","title":"Fixture Title","pk":{"k":"fixtureKey","kx":1234567890},"player":{"allowExternal":true}}
         */
        const val FixtureHex =
            "a5dc8b828864323afe4092ba6d25aef4df85f8f907793acf83dbe69d0592d37f" +
                "65fd9a0814a276151d37866715ec3b3fd5394c6c95ce7a23bab932a6b3bd221e" +
                "3ad8b99f049fe7af7ffe77e21e86205c7993532c32751639916d74bbf4b94c2c" +
                "7bac7cfd18a6460ebe93db1adb8e12b4c9a5756781f240d9472c269e73db1450" +
                "bc42d1028d7d934a6b8c67cd95061704888d7eef78218b9f774ba53246aef58e" +
                "7b9d623410c51344a4fff9422700606cad529ac148b4a211c7b3da13a1ad8ebb" +
                "02a6f914662c1a1ce3ba2d57b6ab385a"

        const val RotatedFixtureHex =
            "ba122bdcf8ab8882d2726248d7d0dc3f1191fe7045cc55a56fb97e2408339e6d" +
                "c02a9afac2c7c35495d98fbad35dd82f973568b491255ff89453dbeb77ba632e" +
                "a634abe781fe54cb1061d19ff041be84f9ad6333739d5dad845d9e3a7eb449b3" +
                "a5bf196cbf7e22c6b3a418943fb6154f0267254edd78bd76baac4650b94ed015" +
                "e369144e46c16abd42712bbb53e12225f56a1f006402642c1c8bdf223dcb7dba"
    }
}
