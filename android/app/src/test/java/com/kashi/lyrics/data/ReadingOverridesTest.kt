package com.kashi.lyrics.data

import com.kashi.lyrics.text.HangulMode
import com.kashi.lyrics.text.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 곡별 읽기 교정. 사전이 고른 읽기와 노래가 부르는 읽기가 다를 때 쓴다.
 * 저장은 안드로이드 파일 API 를 타므로 여기서는 한자 추출과 교정 적용만 본다.
 */
class ReadingOverridesTest {

    @Test
    fun `offers kanji units without breaking morpheme boundaries`() {
        assertEquals(listOf("明日", "扉"), ReadingOverrides.correctableUnits("明日への扉"))
        // 조동사 う 까지 묶어 行こう 를 통째로 준다. 行 만 떼면 남은 こう 가 엉뚱하게 분석된다.
        assertEquals(listOf("一緒", "行こう"), ReadingOverrides.correctableUnits("一緒に行こう"))
        assertEquals(emptyList<String>(), ReadingOverrides.correctableUnits("ありがとう"))
        assertEquals(emptyList<String>(), ReadingOverrides.correctableUnits("walking in the rain"))
    }

    @Test
    fun `repeated unit is offered once`() {
        assertEquals(listOf("君"), ReadingOverrides.correctableUnits("君と君と君"))
    }

    @Test
    fun `dictionary picks the common reading which songs often do not use`() {
        // 사전은 明日 를 아시타로, 行こう 를 이코오로 읽는다. 노래는 아스, 유코오 로 부르곤 한다.
        assertEquals("아시타", Reading.analyze("明日").hangul())
        assertTrue(Reading.analyze("行こう").hangul().startsWith("이코"))
    }

    @Test
    fun `hangul override replaces the reading without a japanese keyboard`() {
        val line = "明日への扉"
        assertTrue(Reading.analyze(line).hangul(), Reading.analyze(line).hangul().startsWith("아시타"))

        val fixed = Reading.analyze(line, mapOf("明日" to "아스")).hangul()
        assertTrue(fixed, fixed.startsWith("아스"))
    }

    @Test
    fun `katakana override also works`() {
        val fixed = Reading.analyze("明日への扉", mapOf("明日" to "アス")).hangul()
        assertTrue(fixed, fixed.startsWith("아스"))
    }

    @Test
    fun `overriding the whole unit keeps it as one word`() {
        // 제안되는 단위(行こう)를 통째로 고치면 띄어쓰기가 끼어들지 않는다.
        assertEquals("유코오", Reading.analyze("行こう", mapOf("行こう" to "유코오")).hangul())
        assertEquals("유코오", Reading.analyze("行こう", mapOf("行こう" to "ユコー")).hangul())
    }

    @Test
    fun `unit override survives surrounding text`() {
        val fixed = Reading.analyze("一緒に行こう", mapOf("行こう" to "유코오")).hangul()
        assertEquals("잇쇼니 유코오", fixed)
    }

    @Test
    fun `override applies in official mode too`() {
        val fixed = Reading.analyze("明日への扉", mapOf("明日" to "아스")).hangul(HangulMode.OFFICIAL)
        assertTrue(fixed, fixed.startsWith("아스"))
    }
}
