package com.sloflix.tv.data.playback

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reimplements the StreamP2P / playerp2p Web Crypto AES-CBC path used by
 * `sf.strp2p.com` / `test.playerp2p.com` (assets/index-*.js).
 *
 * Key and IV are derived from `location.protocol`. For `https:` with a normal
 * `#videoId…` hash they match the fixed UTF-8 strings [HttpsKeyUtf8] /
 * [HttpsIvUtf8].
 */
internal object StreamP2PCrypto {
    private const val Transformation = "AES/CBC/PKCS5Padding"

    /** Matches browser `R()` for `location.protocol == "https:"`. */
    const val HttpsKeyUtf8: String = "kiemtienmua911ca"

    /** Matches browser `j()` for `https:` with a hash starting with `#`. */
    const val HttpsIvUtf8: String = "1234567890oiuytr"

    fun deriveKey(protocol: String): ByteArray =
        buildKey(protocol).toByteArray(StandardCharsets.UTF_8)

    fun deriveIv(protocol: String): ByteArray =
        buildIv(protocol).toByteArray(StandardCharsets.UTF_8)

    fun decryptHex(hexCiphertext: String, protocol: String = "https:"): String =
        decryptHex(
            hexCiphertext = hexCiphertext,
            keyUtf8 = buildKey(protocol),
            ivUtf8 = buildIv(protocol),
        )

    fun decryptHex(hexCiphertext: String, keyUtf8: String, ivUtf8: String): String {
        val key = keyUtf8.toByteArray(StandardCharsets.UTF_8)
        val iv = ivUtf8.toByteArray(StandardCharsets.UTF_8)
        require(key.size == 16) { "StreamP2P AES key must be 16 bytes, was ${key.size}" }
        require(iv.size == 16) { "StreamP2P AES IV must be 16 bytes, was ${iv.size}" }
        val cipherBytes = hexToBytes(hexCiphertext.trim())
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }

    /** Faithful port of obfuscated `R()`. */
    internal fun buildKey(protocol: String): String {
        val d = "10"
        val g = 110
        val t = 1
        var e = ""
        val digits = Character.codePointAt("ᵟ", 0).toString().map { it.toString() }
        for (digit in digits) {
            e += fromCodePoint((d + digit).toInt())
        }
        e += fromCodePoint(Character.codePointAt(protocol, d.toInt() / 10))
        e += e.substring(1, 3)
        e += fromCodePoint(g) + fromCodePoint(g - 1) + fromCodePoint(g + 7)
        val q = "3579".map { it.toString() }.toMutableList()
        e += fromCodePoint((q[3] + q[2]).toInt()) + fromCodePoint((q[1] + q[2]).toInt())
        val mid = (q[0].toInt() * t + t).toString() + q[3]
        e += fromCodePoint(mid.toInt()) + fromCodePoint(mid.toInt())
        val q3 = q[3].toInt()
        val first = q3 * d.toInt() + q3 * t
        q.reverse()
        val second = q.joinToString("").take(2).toInt()
        e += fromCodePoint(first) + fromCodePoint(second)
        return e
    }

    /** Faithful port of obfuscated `j()` (hash first char assumed `#`). */
    internal fun buildIv(protocol: String): String {
        val d = protocol + "//"
        val hash = "#"
        val t = protocol.length * d.length
        val one = 1
        var c = ""
        for (z in one until 10) {
            c += fromCodePoint(z + t)
        }
        var q = ""
        q = one.toString() + q + one + q + one
        val h = q.length * Character.codePointAt(hash, 0)
        val le = q.toInt() * one + protocol.length
        val l = le + 4
        val x = Character.codePointAt(protocol, one)
        val te = x * one - 2
        c += fromCodePoint(t) +
            fromCodePoint(q.toInt()) +
            fromCodePoint(h) +
            fromCodePoint(le) +
            fromCodePoint(l) +
            fromCodePoint(x) +
            fromCodePoint(te)
        return c
    }

    private fun fromCodePoint(codePoint: Int): String =
        String(Character.toChars(codePoint))

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Ciphertext hex length must be even" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
