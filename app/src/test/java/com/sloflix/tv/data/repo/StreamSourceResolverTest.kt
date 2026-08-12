package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MediaSourceDto
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSourceResolverTest {
    @Test
    fun `direct media url is preferred over a player page`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(
                source("https://player.sloflix.com/embed?token=abc"),
                source("https://cdn.example.com/arrival/master.m3u8"),
            ),
        )

        assertEquals("https://cdn.example.com/arrival/master.m3u8", candidates.first())
    }

    @Test
    fun `upstream url is extracted from the player query string`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(
                source(
                    "https://player.sloflix.com/embed?token=abc" +
                        "&source=https%3A%2F%2Fcdn.example.com%2Farrival%2Fmaster.m3u8",
                ),
            ),
        )

        assertEquals(
            listOf(
                "https://cdn.example.com/arrival/master.m3u8",
                "https://player.sloflix.com/embed?token=abc" +
                    "&source=https%3A%2F%2Fcdn.example.com%2Farrival%2Fmaster.m3u8",
            ),
            candidates,
        )
    }

    @Test
    fun `any url shaped query value is used when no known key matches`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(source("https://player.sloflix.com/embed?sig=xyz&upstream=https://cdn.example.com/a.mp4")),
        )

        assertEquals("https://cdn.example.com/a.mp4", candidates.first())
    }

    @Test
    fun `base64 encoded upstream url is decoded`() {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("https://cdn.example.com/a/master.m3u8".toByteArray())
        val candidates = StreamSourceResolver.candidates(
            listOf(source("https://player.sloflix.com/embed?file=$encoded")),
        )

        assertEquals("https://cdn.example.com/a/master.m3u8", candidates.first())
    }

    @Test
    fun `unusable sources are dropped and remaining ones stay as fallbacks`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(
                source(""),
                source("not-a-url"),
                source("https://cdn.example.com/page.html"),
                source("https://cdn.example.com/arrival.mp4"),
            ),
        )

        assertEquals(
            listOf(
                "https://cdn.example.com/arrival.mp4",
                "https://cdn.example.com/page.html",
            ),
            candidates,
        )
    }

    @Test
    fun `player page without an upstream url is still offered`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(source("https://player.sloflix.com/embed?token=abc")),
        )

        assertEquals(listOf("https://player.sloflix.com/embed?token=abc"), candidates)
    }

    private fun source(url: String) = MediaSourceDto(url = url, name = "SLOSubs")
}
