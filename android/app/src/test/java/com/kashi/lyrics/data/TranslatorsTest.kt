package com.kashi.lyrics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorsTest {

    @Test
    fun `blank lines are not sent to the translator`() {
        val targets = Translator.translatable(listOf("a", "", "  ", "b"))
        assertEquals(mapOf(0 to "a", 3 to "b"), targets)
    }

    @Test
    fun `none translator returns empty strings of the same length`() {
        assertEquals(listOf("", "", ""), Translator.None.translateLines(listOf("a", "", "b")))
    }

    @Test
    fun `from picks provider by key presence`() {
        assertEquals("none", Translator.from("claude", "").name)
        assertEquals("claude", Translator.from("claude", "sk-x").name)
        assertEquals("deepl", Translator.from("deepl", "k:fx").name)
        assertEquals("none", Translator.from("papago", "id", "").name)
        assertEquals("papago", Translator.from("papago", "id", "secret").name)
        assertEquals("none", Translator.from("bogus", "key").name)
    }

    @Test
    fun `claude mapping parses numbered json and fills gaps`() {
        val out = ClaudeTranslator.parseMapping("""{"0": "첫 줄", "2": "셋째 줄"} trailing""", 4)
        assertEquals(listOf("첫 줄", "", "셋째 줄", ""), out)
    }

    @Test
    fun `claude mapping rejects garbage`() {
        val failed = runCatching { ClaudeTranslator.parseMapping("no json here", 2) }
        assertTrue(failed.exceptionOrNull() is TranslationException)
    }
}
