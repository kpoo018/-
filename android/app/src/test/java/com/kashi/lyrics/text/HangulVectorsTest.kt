package com.kashi.lyrics.text

import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kotlin 한글 변환기가 Python 정답지(shared/hangul_vectors.json)와 똑같이 나오는지 본다.
 * 벡터는 pipeline/tools/gen_vectors.py 로 만든다.
 */
class HangulVectorsTest {

    private fun vectorsFile(): File {
        // Gradle 은 app/ 에서, build.sh 는 android/ 에서 돈다. 위로 올라가며 찾는다.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/hangul_vectors.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("shared/hangul_vectors.json 을 찾지 못했습니다")
    }

    @Test
    fun `matches every shared vector in both modes`() {
        val vectors = JSONArray(vectorsFile().readText())
        assertTrue("벡터가 비어 있습니다", vectors.length() > 50)

        val failures = mutableListOf<String>()
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val kana = v.getString("kana")
            val checks = listOf(
                "pronunciation" to Hangul.toHangul(kana, HangulMode.PRONUNCIATION),
                "official" to Hangul.toHangul(kana, HangulMode.OFFICIAL),
                "official_medial" to Hangul.toHangul(kana, HangulMode.OFFICIAL, wordInitial = false),
            )
            for ((field, actual) in checks) {
                val expected = v.getString(field)
                if (expected != actual) failures += "$kana [$field]: 기대 '$expected' 실제 '$actual'"
            }
        }
        assertTrue("불일치 ${failures.size}건:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `compose builds syllables from jamo`() {
        assertEquals('가', Hangul.compose('ㄱ', 'ㅏ'))
        assertEquals('각', Hangul.compose('ㄱ', 'ㅏ', 'ㄱ'))
        assertEquals('셈', Hangul.compose('ㅅ', 'ㅔ', 'ㅁ'))
    }

    @Test
    fun `hiragana is normalised to katakana`() {
        assertEquals("キミハ", Hangul.toKatakana("きみは"))
        assertEquals(Hangul.toHangul("きみは"), Hangul.toHangul("キミハ"))
    }
}
