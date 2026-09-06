package com.kashi.lyrics.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kuromoji 로 읽기를 뽑아 한글로 옮기는 전체 경로. 순수 JVM 에서 돈다. */
class ReadingTest {

    @Test
    fun `particles and long vowels come out right in pronunciation mode`() {
        val line = Reading.analyze("君はまだ知らない東京へ行こう")
        assertEquals("키미와 마다 시라나이 토오쿄오에 이코오", line.hangul())
        assertEquals("キミワ マダ シラナイ トーキョーエ イコー", line.reading())
    }

    @Test
    fun `official mode keeps ei and drops ou`() {
        assertEquals("기미와 마다 시라나이 도쿄에 이코", Reading.analyze("君はまだ知らない東京へ行こう").hangul(HangulMode.OFFICIAL))
        assertEquals("헤이세이 우마레노 센세이", Reading.analyze("平成生まれの先生").hangul(HangulMode.OFFICIAL))
    }

    @Test
    fun `clitics attach to the previous chunk`() {
        val line = Reading.analyze("夢ならばどれほどよかったでしょう")
        assertEquals(listOf("夢ならば", "どれほど", "よかったでしょう"), line.chunks.map { it.surface })
        assertEquals("유메나라바 도레호도 요캇타데쇼오", line.hangul())
    }

    @Test
    fun `latin words pass through untouched`() {
        val line = Reading.analyze("walking in the rain 傘もささずに")
        val hangul = line.hangul()
        assertTrue(hangul, hangul.startsWith("walking in the rain"))
        assertTrue(hangul, hangul.endsWith("카사모 사사즈니"))
    }

    @Test
    fun `overrides replace the reading of ateji`() {
        val plain = Reading.analyze("運命だと言って笑った")
        val overridden = Reading.analyze("運命だと言って笑った", mapOf("運命" to "サダメ"))
        assertTrue(plain.hangul(), plain.hangul().startsWith("움메이"))
        assertEquals("사다메다토 잇테 와랏타", overridden.hangul())
    }

    @Test
    fun `sokuon and hatsuon assimilate`() {
        assertEquals("각코오", Reading.analyze("学校").hangul())
        assertEquals("셈파이", Reading.analyze("先輩").hangul())
    }

    @Test
    fun `empty line yields nothing`() {
        assertEquals("", Reading.analyze("").hangul())
    }
}
