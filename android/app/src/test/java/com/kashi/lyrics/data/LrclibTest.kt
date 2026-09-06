package com.kashi.lyrics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrclibTest {

    private val synced = """
        [ar:テスト]
        [00:12.34]君はまだ知らない
        [00:15.00]
        [00:16.50]東京へ行こう
        [00:20.10]walking in the rain
    """.trimIndent()

    @Test
    fun `parses timestamps to milliseconds and drops metadata`() {
        val lines = Lrclib.parseLrc(synced)
        assertEquals(listOf(12_340L, 15_000L, 16_500L, 20_100L), lines.map { it.first })
        assertEquals("君はまだ知らない", lines[0].second)
        assertEquals("", lines[1].second) // 간주 줄은 남긴다
    }

    @Test
    fun `expands repeated timestamps and sorts`() {
        val lines = Lrclib.parseLrc("[00:05.00][01:05.00]ラララ\n[00:30.00]あ")
        assertEquals(listOf(5_000L, 30_000L, 65_000L), lines.map { it.first })
        assertEquals("ラララ", lines[0].second)
        assertEquals("ラララ", lines[2].second)
    }

    @Test
    fun `plain lyrics have no times`() {
        val lines = Lrclib.parseLrc("君はまだ知らない\n東京へ行こう")
        assertEquals(2, lines.size)
        assertNull(lines[0].first)
        assertEquals("東京へ行こう", lines[1].second)
    }

    @Test
    fun `two digit fractions are hundredths`() {
        assertEquals(1_500L, Lrclib.parseLrc("[00:01.5]a")[0].first)
        assertEquals(1_050L, Lrclib.parseLrc("[00:01.05]a")[0].first)
        assertEquals(1_005L, Lrclib.parseLrc("[00:01.005]a")[0].first)
    }

    @Test
    fun `toLines prefers synced and keeps blank interludes`() {
        val track = Lrclib.Track(1, "a", "t", "", 100.0, plainLyrics = "x\ny", syncedLyrics = synced)
        val (lines, isSynced) = Lrclib.toLines(track)
        assertEquals(true, isSynced)
        assertEquals(4, lines.size)

        val plain = Lrclib.Track(1, "a", "t", "", 100.0, plainLyrics = "x\n\ny", syncedLyrics = "")
        val (plainLines, plainSynced) = Lrclib.toLines(plain)
        assertEquals(false, plainSynced)
        assertEquals(listOf("x", "", "y"), plainLines.map { it.second })
    }
}
