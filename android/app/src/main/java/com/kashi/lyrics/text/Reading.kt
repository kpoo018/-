package com.kashi.lyrics.text

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * 일본어 가사 한 줄 -> 읽기(가나) + 한글 발음.
 *
 * pipeline/kashi/reading.py 와 같은 구조다. 형태소 분석기만 fugashi+UniDic 대신
 * 폰에서 도는 Kuromoji+IPADIC 을 쓴다. IPADIC 도 형태소마다 두 읽기를 준다.
 *
 * - reading:       표기 그대로의 가나. 東京 -> トウキョウ
 * - pronunciation: 실제 발음. 東京 -> トーキョー, 조사 は -> ワ, へ -> エ
 *
 * 한자의 특수 읽기(当て字)는 분석기가 잡지 못하므로 [analyze] 의 overrides 로 직접 준다.
 */
object Reading {

    /** 앞 어절에 붙여 읽는 품사(대분류). 새 어절을 시작하지 않는다. */
    private val CLITIC_POS1 = setOf("助詞", "助動詞")

    /** 앞 어절에 붙는 세분류. 名詞-接尾(さん, 的), 動詞-非自立(ている 의 いる) 등. */
    private val CLITIC_POS2 = setOf("接尾", "非自立")

    private const val PUNCT_POS1 = "記号"

    data class Morpheme(
        val surface: String,
        val kana: String,
        val pron: String,
        val pos: String,
        val isClitic: Boolean,
        val isPunct: Boolean,
    ) {
        fun sourceKana(mode: HangulMode): String =
            if (mode != HangulMode.OFFICIAL) pron
            // 표기법 모드는 표기 그대로의 가나를 쓰되(エイ -> 에이 를 지키려면 필요하다),
            // 조사만은 발음을 따른다. は -> ワ, へ -> エ, を -> オ.
            else if (pos == "助詞") pron else kana
    }

    /** 자립어 + 뒤에 붙는 조사/조동사를 묶은 어절. 한글 표기의 띄어쓰기 단위. */
    class Chunk(val tokens: MutableList<Morpheme> = mutableListOf()) {
        val surface: String get() = tokens.joinToString("") { it.surface }

        fun kana(mode: HangulMode): String = tokens.joinToString("") { it.sourceKana(mode) }

        // 어절 전체를 한 번에 변환한다. 어두 평음화는 첫 모라에만 걸리고, 형태소 경계를 넘는
        // 장음(行こ+う -> イコー)도 하나로 이어져야 제대로 접힌다.
        fun hangul(mode: HangulMode): String = Hangul.toHangul(kana(mode), mode, wordInitial = true)
    }

    class AnalyzedLine(val original: String, val chunks: List<Chunk>) {
        fun reading(mode: HangulMode = HangulMode.PRONUNCIATION): String =
            chunks.map { it.kana(mode) }.filter { it.isNotBlank() }.joinToString(" ")

        fun hangul(mode: HangulMode = HangulMode.PRONUNCIATION): String =
            chunks.map { it.hangul(mode) }.filter { it.isNotBlank() }.joinToString(" ")

        fun tokens(): List<Morpheme> = chunks.flatMap { it.tokens }
    }

    /** 사전(약 30MB)을 메모리에 올린다. 첫 호출에 1~2초 걸리므로 백그라운드에서 부른다. */
    private val tokenizer: Tokenizer by lazy { Tokenizer() }

    fun warmUp() {
        tokenizer
    }

    private fun feature(value: String?, fallback: String): String =
        if (value.isNullOrEmpty() || value == "*") fallback else value

    private fun toMorpheme(token: Token): Morpheme {
        val surface = token.surface
        val kana = feature(token.reading, surface)
        val pron = feature(token.pronunciation, kana)
        val pos1 = feature(token.partOfSpeechLevel1, "")
        val pos2 = feature(token.partOfSpeechLevel2, "")
        return Morpheme(
            surface = surface,
            kana = kana,
            pron = pron,
            pos = pos1,
            isClitic = pos1 in CLITIC_POS1 || pos2 in CLITIC_POS2,
            isPunct = pos1 == PUNCT_POS1,
        )
    }

    /**
     * 읽기를 직접 지정한 표현을 기준으로 줄을 잘라낸다.
     * 긴 표현을 먼저 맞춰야 짧은 표현에 먹히지 않는다.
     */
    private fun splitByOverrides(text: String, overrides: Map<String, String>): List<Pair<String, String?>> {
        if (overrides.isEmpty()) return listOf(text to null)
        val keys = overrides.keys.sortedByDescending { it.length }
        val out = ArrayList<Pair<String, String?>>()
        val buffer = StringBuilder()
        var i = 0
        while (i < text.length) {
            val key = keys.firstOrNull { text.startsWith(it, i) }
            if (key != null) {
                if (buffer.isNotEmpty()) {
                    out += buffer.toString() to null
                    buffer.setLength(0)
                }
                out += key to overrides.getValue(key)
                i += key.length
            } else {
                buffer.append(text[i])
                i++
            }
        }
        if (buffer.isNotEmpty()) out += buffer.toString() to null
        return out
    }

    /** お단으로 끝나는 가나. 의지형 う 가 이 뒤에 오면 장음이다. */
    private const val O_ROW = "オコソトノホモヨロヲゴゾドボポョ"

    /**
     * IPADIC 은 行こう/でしょう 를 行こ+う 로 쪼개고 조동사 う 의 발음을 ウ 로 준다.
     * 실제로는 장음(イコー)이다. UniDic 이 한 덩어리로 주는 것과 같아지도록 ー 로 바꾼다.
     */
    private fun normalizeVolitionalU(morphemes: MutableList<Morpheme>) {
        for (i in 1 until morphemes.size) {
            val m = morphemes[i]
            val previous = morphemes[i - 1]
            val previousLast = previous.pron.lastOrNull() ?: continue
            if (m.pos == "助動詞" && m.surface == "う" && previousLast in O_ROW) {
                morphemes[i] = m.copy(pron = "ー")
            }
        }
    }

    /** 가사 한 줄을 형태소 분석해 어절 단위로 묶는다. */
    fun analyze(text: String, overrides: Map<String, String> = emptyMap()): AnalyzedLine {
        val morphemes = ArrayList<Morpheme>()
        for ((piece, reading) in splitByOverrides(text, overrides)) {
            if (reading != null) {
                morphemes += Morpheme(piece, reading, reading, "名詞", isClitic = false, isPunct = false)
            } else {
                tokenizer.tokenize(piece).mapTo(morphemes, ::toMorpheme)
            }
        }

        normalizeVolitionalU(morphemes)

        val chunks = ArrayList<Chunk>()
        for (m in morphemes) {
            val startsNew = chunks.isEmpty() || !m.isClitic || chunks.last().tokens.last().isPunct
            if (startsNew) chunks += Chunk()
            chunks.last().tokens += m
        }
        return AnalyzedLine(text, chunks)
    }
}
