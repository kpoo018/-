package com.kashi.lyrics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서버 JSON 파싱과 줄 찾기. 프레임워크 없이 순수 JVM 에서 돈다. */
class LyricDocTest {

    private val json = """
        {
          "track_id": "abc123",
          "artist": "テスト",
          "title": "サンプル",
          "album": "アルバム",
          "duration": 200.5,
          "hangul_mode": "pronunciation",
          "synced": true,
          "lines": [
            {"time": 12.34, "original": "君はまだ知らない", "reading": "キミワ マダ シラナイ",
             "hangul": "키미와 마다 시라나이", "translation": "너는 아직 모르지"},
            {"time": 15.0,  "original": "", "reading": "", "hangul": "", "translation": ""},
            {"time": 16.5,  "original": "東京へ行こう", "reading": "トーキョーエ イコー",
             "hangul": "토오쿄오에 이코오", "translation": "도쿄로 가자"},
            {"time": 20.1,  "original": "walking in the rain", "reading": "",
             "hangul": "walking in the rain", "translation": "빗속을 걸으며"}
          ]
        }
    """.trimIndent()

    private val doc = LyricDoc.parse(json)

    @Test
    fun `parses header and converts seconds to milliseconds`() {
        assertEquals("abc123", doc.trackId)
        assertEquals("テスト", doc.artist)
        assertEquals(200_500L, doc.durationMs)
        assertTrue(doc.synced)
        assertEquals(4, doc.lines.size)
        assertEquals(12_340L, doc.lines[0].timeMs)
        assertEquals("키미와 마다 시라나이", doc.lines[0].hangul)
    }

    @Test
    fun `blank interlude line is kept but marked blank`() {
        assertTrue(doc.lines[1].isBlank)
        assertFalse(doc.lines[0].isBlank)
    }

    @Test
    fun `indexAt returns -1 before the first line`() {
        assertEquals(-1, doc.indexAt(0))
        assertEquals(-1, doc.indexAt(12_339))
    }

    @Test
    fun `indexAt picks the last line that has started`() {
        assertEquals(0, doc.indexAt(12_340))
        assertEquals(0, doc.indexAt(14_999))
        assertEquals(1, doc.indexAt(15_000))
        assertEquals(2, doc.indexAt(18_000))
        assertEquals(3, doc.indexAt(20_100))
        assertEquals(3, doc.indexAt(999_999))
    }

    @Test
    fun `nextTimeAfter gives the boundary to schedule the next wake-up`() {
        assertEquals(12_340L, doc.nextTimeAfter(-1))
        assertEquals(15_000L, doc.nextTimeAfter(0))
        assertEquals(20_100L, doc.nextTimeAfter(2))
        assertNull(doc.nextTimeAfter(3))
    }

    @Test
    fun `unsynced lyrics never resolve to a line`() {
        val plain = LyricDoc.parse(
            """{"synced": false, "lines": [
                 {"time": null, "original": "a", "reading": "", "hangul": "아", "translation": ""},
                 {"time": null, "original": "b", "reading": "", "hangul": "브", "translation": ""}
               ]}"""
        )
        assertFalse(plain.synced)
        assertNull(plain.lines[0].timeMs)
        assertEquals(-1, plain.indexAt(0))
        assertEquals(-1, plain.indexAt(60_000))
        assertNull(plain.nextTimeAfter(-1))
    }

    @Test
    fun `missing fields fall back to empty values`() {
        val minimal = LyricDoc.parse("""{"lines": []}""")
        assertEquals("", minimal.trackId)
        assertEquals("pronunciation", minimal.hangulMode)
        assertEquals(0, minimal.lines.size)
        assertEquals(-1, minimal.indexAt(1000))
    }

    @Test
    fun `cache key matches the server's make_track_id shape`() {
        val key = LyricRepository.cacheKey("YOASOBI", "夜に駆ける", "")
        assertEquals(16, key.length)
        assertTrue(key.all { it in "0123456789abcdef" })
        // 대소문자와 앞뒤 공백은 무시된다
        assertEquals(key, LyricRepository.cacheKey(" yoasobi ", "夜に駆ける", ""))
    }
}
