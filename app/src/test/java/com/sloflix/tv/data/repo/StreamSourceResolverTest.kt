package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MediaSourceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourceResolverTest {
    @Test
    fun `extracts only the source query param from the player page`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(
                source(
                    "https://player.sloflix.com/?source=https%3A%2F%2Fcdn.example.com%2Ffile" +
                        "&vtt=https%3A%2F%2Fi.doodcdn.io%2Fslide.jpg" +
                        "&poster=https%3A%2F%2Fdoimg.net%2Fsplash.jpg",
                ),
            ),
        )

        assertEquals(listOf("https://cdn.example.com/file"), candidates)
    }

    @Test
    fun `resolves subtitle_location to the sloflix subtitles host`() {
        val tracks = StreamSourceResolver.subtitles(
            listOf(
                MediaSourceDto(
                    url = "https://player.sloflix.com/?source=https%3A%2F%2Fcdn.example.com%2Ffile",
                    name = "SLOSubs",
                    subtitleLocation = "abc-123.vtt",
                ),
            ),
        )

        assertEquals(
            listOf(
                com.sloflix.tv.domain.model.SubtitleTrack(
                    url = "https://www.sloflix.com/subtitles/abc-123.vtt",
                ),
            ),
            tracks,
        )
    }

    @Test
    fun `prefers subtitle query param from the player page`() {
        val tracks = StreamSourceResolver.subtitles(
            listOf(
                MediaSourceDto(
                    url = "https://player.sloflix.com/?source=https%3A%2F%2Fcdn.example.com%2Ffile" +
                        "&subtitle=https%3A%2F%2Fcdn.example.com%2Fsubs.vtt",
                    name = "SLOSubs",
                    subtitleLocation = "ignored.vtt",
                ),
            ),
        )

        assertEquals("https://cdn.example.com/subs.vtt", tracks.first().url)
        assertEquals(2, tracks.size) // query + location, distinct urls
    }

    @Test
    fun `direct media urls are kept`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(
                source("https://cdn.example.com/arrival.mp4"),
                source("https://player.sloflix.com/?token=abc"),
            ),
        )

        assertEquals(listOf("https://cdn.example.com/arrival.mp4"), candidates)
    }

    @Test
    fun `html player hosts without a source param are dropped`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(
                source("https://player.sloflix.com/embed?token=abc"),
                source("https://sf.strp2p.com/#abc"),
            ),
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `non source query urls are ignored`() {
        val candidates = StreamSourceResolver.candidates(
            listOf(source("https://player.sloflix.com/?upstream=https%3A%2F%2Fcdn.example.com%2Fa.mp4")),
        )

        assertTrue(candidates.isEmpty())
    }

    private fun source(url: String) = MediaSourceDto(url = url, name = "SLOSubs")
}
