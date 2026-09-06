package com.kashi.lyrics.text

/**
 * 가나 -> 한글 발음 표기.
 *
 * pipeline/kashi/hangul.py 를 그대로 옮긴 것이다. 그쪽이 정답지이고, 두 구현은
 * shared/hangul_vectors.json 으로 같은 결과를 내는지 검사한다. 규칙을 고칠 때는 Python 을
 * 먼저 고치고 벡터를 다시 만든 뒤 여기를 맞춘다.
 *
 * - [HangulMode.PRONUNCIATION] (기본): 실제로 들리는 소리에 가깝게. 장음을 모음 반복으로
 *   살리고 か/た행을 어디서나 격음(카/타)으로 적는다. 따라 부를 때 쓴다.
 * - [HangulMode.OFFICIAL]: 국립국어원 외래어 표기법. 장음을 적지 않고 か/た행이 어두에서
 *   평음(가/다)이 된다. 고유명사·제목용.
 */
enum class HangulMode(val id: String) {
    PRONUNCIATION("pronunciation"),
    OFFICIAL("official");

    companion object {
        fun of(id: String?): HangulMode = entries.firstOrNull { it.id == id } ?: PRONUNCIATION
    }
}

object Hangul {

    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val JONG = "_ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    /** 초성/중성/종성 자모를 완성형 한 글자로 조합한다. */
    fun compose(cho: Char, jung: Char, jong: Char? = null): Char {
        val jongIndex = if (jong == null) 0 else JONG.indexOf(jong)
        return (0xAC00 + (CHO.indexOf(cho) * 21 + JUNG.indexOf(jung)) * 28 + jongIndex).toChar()
    }

    private data class Mora(val cho: Char, val jung: Char)

    /** 가나 한 모라 -> (초성, 중성). 발음 모드 기준값. */
    private val MORA: Map<String, Mora> = buildMap {
        fun row(cho: Char, vararg pairs: Pair<String, Char>) {
            for ((kana, jung) in pairs) put(kana, Mora(cho, jung))
        }
        row('ㅇ', "ア" to 'ㅏ', "イ" to 'ㅣ', "ウ" to 'ㅜ', "エ" to 'ㅔ', "オ" to 'ㅗ')
        row('ㅋ', "カ" to 'ㅏ', "キ" to 'ㅣ', "ク" to 'ㅜ', "ケ" to 'ㅔ', "コ" to 'ㅗ')
        row('ㄱ', "ガ" to 'ㅏ', "ギ" to 'ㅣ', "グ" to 'ㅜ', "ゲ" to 'ㅔ', "ゴ" to 'ㅗ')
        row('ㅅ', "サ" to 'ㅏ', "シ" to 'ㅣ', "ス" to 'ㅡ', "セ" to 'ㅔ', "ソ" to 'ㅗ')
        row('ㅈ', "ザ" to 'ㅏ', "ジ" to 'ㅣ', "ズ" to 'ㅡ', "ゼ" to 'ㅔ', "ゾ" to 'ㅗ')
        row('ㅌ', "タ" to 'ㅏ', "テ" to 'ㅔ', "ト" to 'ㅗ')
        row('ㅊ', "チ" to 'ㅣ', "ツ" to 'ㅡ')
        row('ㄷ', "ダ" to 'ㅏ', "デ" to 'ㅔ', "ド" to 'ㅗ')
        row('ㅈ', "ヂ" to 'ㅣ', "ヅ" to 'ㅡ')
        row('ㄴ', "ナ" to 'ㅏ', "ニ" to 'ㅣ', "ヌ" to 'ㅜ', "ネ" to 'ㅔ', "ノ" to 'ㅗ')
        row('ㅎ', "ハ" to 'ㅏ', "ヒ" to 'ㅣ', "フ" to 'ㅜ', "ヘ" to 'ㅔ', "ホ" to 'ㅗ')
        row('ㅂ', "バ" to 'ㅏ', "ビ" to 'ㅣ', "ブ" to 'ㅜ', "ベ" to 'ㅔ', "ボ" to 'ㅗ')
        row('ㅍ', "パ" to 'ㅏ', "ピ" to 'ㅣ', "プ" to 'ㅜ', "ペ" to 'ㅔ', "ポ" to 'ㅗ')
        row('ㅁ', "マ" to 'ㅏ', "ミ" to 'ㅣ', "ム" to 'ㅜ', "メ" to 'ㅔ', "モ" to 'ㅗ')
        row('ㅇ', "ヤ" to 'ㅑ', "ユ" to 'ㅠ', "ヨ" to 'ㅛ')
        row('ㄹ', "ラ" to 'ㅏ', "リ" to 'ㅣ', "ル" to 'ㅜ', "レ" to 'ㅔ', "ロ" to 'ㅗ')
        row('ㅇ', "ワ" to 'ㅘ', "ヲ" to 'ㅗ', "ヰ" to 'ㅣ', "ヱ" to 'ㅔ')
        row('ㅂ', "ヴ" to 'ㅜ')

        // 요음. ャ/ュ/ョ 는 초성을 유지한 채 중성만 바꾼다.
        row('ㅋ', "キャ" to 'ㅑ', "キュ" to 'ㅠ', "キョ" to 'ㅛ')
        row('ㄱ', "ギャ" to 'ㅑ', "ギュ" to 'ㅠ', "ギョ" to 'ㅛ')
        row('ㅅ', "シャ" to 'ㅑ', "シュ" to 'ㅠ', "ショ" to 'ㅛ')
        row('ㅈ', "ジャ" to 'ㅏ', "ジュ" to 'ㅜ', "ジョ" to 'ㅗ') // 자/주/조 (쟈/쥬/죠 아님)
        row('ㅊ', "チャ" to 'ㅏ', "チュ" to 'ㅜ', "チョ" to 'ㅗ')
        row('ㅈ', "ヂャ" to 'ㅏ', "ヂュ" to 'ㅜ', "ヂョ" to 'ㅗ')
        row('ㄴ', "ニャ" to 'ㅑ', "ニュ" to 'ㅠ', "ニョ" to 'ㅛ')
        row('ㅎ', "ヒャ" to 'ㅑ', "ヒュ" to 'ㅠ', "ヒョ" to 'ㅛ')
        row('ㅂ', "ビャ" to 'ㅑ', "ビュ" to 'ㅠ', "ビョ" to 'ㅛ')
        row('ㅍ', "ピャ" to 'ㅑ', "ピュ" to 'ㅠ', "ピョ" to 'ㅛ')
        row('ㅁ', "ミャ" to 'ㅑ', "ミュ" to 'ㅠ', "ミョ" to 'ㅛ')
        row('ㄹ', "リャ" to 'ㅑ', "リュ" to 'ㅠ', "リョ" to 'ㅛ')

        // 외래어 표기용 확장 가나.
        row('ㅍ', "ファ" to 'ㅏ', "フィ" to 'ㅣ', "フェ" to 'ㅔ', "フォ" to 'ㅗ', "フュ" to 'ㅠ')
        row('ㅂ', "ヴァ" to 'ㅏ', "ヴィ" to 'ㅣ', "ヴェ" to 'ㅔ', "ヴォ" to 'ㅗ')
        row('ㅌ', "ティ" to 'ㅣ', "トゥ" to 'ㅜ')
        row('ㄷ', "ディ" to 'ㅣ', "ドゥ" to 'ㅜ')
        row('ㅇ', "ウィ" to 'ㅟ', "ウェ" to 'ㅞ', "ウォ" to 'ㅝ')
        row('ㅅ', "シェ" to 'ㅖ')
        row('ㅈ', "ジェ" to 'ㅔ')
        row('ㅊ', "チェ" to 'ㅔ')
        row('ㅊ', "ツァ" to 'ㅏ', "ツィ" to 'ㅣ', "ツェ" to 'ㅔ', "ツォ" to 'ㅗ')
        row('ㅋ', "クァ" to 'ㅘ', "クォ" to 'ㅝ')
        row('ㄱ', "グァ" to 'ㅘ')
    }

    /** 어두에서 평음이 되는 모라 (표기법 모드 전용). */
    private val OFFICIAL_INITIAL: Map<String, Mora> = mapOf(
        "カ" to Mora('ㄱ', 'ㅏ'), "キ" to Mora('ㄱ', 'ㅣ'), "ク" to Mora('ㄱ', 'ㅜ'),
        "ケ" to Mora('ㄱ', 'ㅔ'), "コ" to Mora('ㄱ', 'ㅗ'),
        "キャ" to Mora('ㄱ', 'ㅑ'), "キュ" to Mora('ㄱ', 'ㅠ'), "キョ" to Mora('ㄱ', 'ㅛ'),
        "タ" to Mora('ㄷ', 'ㅏ'), "テ" to Mora('ㄷ', 'ㅔ'), "ト" to Mora('ㄷ', 'ㅗ'),
        "チ" to Mora('ㅈ', 'ㅣ'),
        "チャ" to Mora('ㅈ', 'ㅏ'), "チュ" to Mora('ㅈ', 'ㅜ'), "チョ" to Mora('ㅈ', 'ㅗ'),
    )

    /** 표기법 모드는 어디서든 ツ를 '쓰'로 적는다. */
    private val OFFICIAL_ANYWHERE: Map<String, Mora> = mapOf("ツ" to Mora('ㅆ', 'ㅡ'))

    /** 장음을 적을 때 늘어나는 모음. 요음과 이중모음은 단모음으로 떨어뜨린다. */
    private val ELONG: Map<Char, Char> = mapOf(
        'ㅏ' to 'ㅏ', 'ㅑ' to 'ㅏ', 'ㅘ' to 'ㅏ',
        'ㅣ' to 'ㅣ', 'ㅟ' to 'ㅣ',
        'ㅜ' to 'ㅜ', 'ㅠ' to 'ㅜ',
        'ㅔ' to 'ㅔ', 'ㅖ' to 'ㅔ', 'ㅞ' to 'ㅔ',
        'ㅗ' to 'ㅗ', 'ㅛ' to 'ㅗ', 'ㅝ' to 'ㅗ',
        'ㅡ' to 'ㅡ',
    )

    /** 표기법 모드에서 장음으로 보고 적지 않는 조합. {뒤따르는 모음 가나: 앞 음절의 모음들} */
    private val OFFICIAL_LONG_VOWEL: Map<String, Set<Char>> = mapOf(
        "ウ" to setOf('ㅗ', 'ㅜ'),
        "オ" to setOf('ㅗ'),
        "ア" to setOf('ㅏ'),
        "エ" to setOf('ㅔ'),
        "イ" to setOf('ㅣ'),
    )

    private const val SOKUON = 'ッ'
    private const val HATSUON = 'ン'
    private const val CHOON = 'ー'
    private const val SMALL_VOWELS = "ァィゥェォ"

    private val SOKUON_JONG = mapOf('ㅋ' to 'ㄱ', 'ㄱ' to 'ㄱ', 'ㅍ' to 'ㅂ', 'ㅂ' to 'ㅂ')
    private val HATSUON_JONG = mapOf('ㅁ' to 'ㅁ', 'ㅂ' to 'ㅁ', 'ㅍ' to 'ㅁ', 'ㅋ' to 'ㅇ', 'ㄱ' to 'ㅇ')

    private val MAX_MORA_LEN = MORA.keys.maxOf { it.length }

    private sealed class Unit {
        data class MoraUnit(val mora: Mora) : Unit()
        object Sokuon : Unit()
        object Hatsuon : Unit()
        object Choon : Unit()
        data class Raw(val ch: Char) : Unit()
    }

    /** 히라가나를 가타카나로 정규화한다. 다른 문자는 건드리지 않는다. */
    fun toKatakana(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            sb.append(if (code in 0x3041..0x3096) (code + 0x60).toChar() else ch)
        }
        return sb.toString()
    }

    private fun lookup(kana: String, mode: HangulMode, wordInitial: Boolean): Mora? {
        if (mode == HangulMode.OFFICIAL) {
            if (wordInitial) OFFICIAL_INITIAL[kana]?.let { return it }
            OFFICIAL_ANYWHERE[kana]?.let { return it }
        }
        return MORA[kana]
    }

    /** 이 모라가 바로 앞 음절을 늘이기만 하는 장음인지 (표기법 모드 전용). */
    private fun isLongVowelTail(kana: String, units: List<Unit>): Boolean {
        val allowed = OFFICIAL_LONG_VOWEL[kana] ?: return false
        val last = units.lastOrNull() as? Unit.MoraUnit ?: return false
        val previousJung = last.mora.jung
        return (ELONG[previousJung] ?: previousJung) in allowed
    }

    /**
     * 가나 문자열을 한글 발음 표기로 바꾼다.
     *
     * [wordInitial] 은 표기법 모드에서 어두 평음화를 적용할지 정한다. 한 단어를 여러 조각으로
     * 나눠 넘길 때는 두 번째 조각부터 false 로 준다.
     */
    fun toHangul(
        kana: String,
        mode: HangulMode = HangulMode.PRONUNCIATION,
        wordInitial: Boolean = true,
    ): String {
        val text = toKatakana(kana)

        // 1단계: 모라 단위로 쪼갠다. 긴 것부터 맞춰야 요음/확장 가나가 잡힌다.
        val units = ArrayList<Unit>(text.length)
        var i = 0
        var atWordStart = wordInitial
        while (i < text.length) {
            val ch = text[i]
            when (ch) {
                SOKUON -> { units += Unit.Sokuon; i++; continue }
                HATSUON -> { units += Unit.Hatsuon; i++; continue }
                CHOON -> { units += Unit.Choon; i++; continue }
            }

            var matched = false
            for (length in MAX_MORA_LEN downTo 1) {
                if (i + length > text.length) continue
                val chunk = text.substring(i, i + length)
                val found = lookup(chunk, mode, atWordStart) ?: continue
                if (mode == HangulMode.OFFICIAL && isLongVowelTail(chunk, units)) {
                    i += length // 표기법은 장음을 적지 않는다
                } else {
                    units += Unit.MoraUnit(found)
                    i += length
                    atWordStart = false
                }
                matched = true
                break
            }
            if (!matched) {
                // 남은 작은 가나는 버리고(직전 모라에 이미 반영됨), 그 외 문자는 그대로 흘려보낸다.
                if (ch !in SMALL_VOWELS) {
                    units += Unit.Raw(ch)
                    atWordStart = true
                }
                i++
            }
        }

        // 2단계: 받침을 붙여가며 음절을 조립한다.
        val syllables = ArrayList<CharArray>() // [초성, 중성, 종성(' ' 이면 없음)]
        val out = StringBuilder()

        fun flush() {
            for (s in syllables) out.append(compose(s[0], s[1], s[2].takeIf { it != ' ' }))
            syllables.clear()
        }

        fun nextCho(index: Int): Char? {
            for (k in index + 1 until units.size) {
                when (val u = units[k]) {
                    is Unit.MoraUnit -> return u.mora.cho
                    is Unit.Raw -> return null
                    else -> {}
                }
            }
            return null
        }

        for ((index, unit) in units.withIndex()) {
            when (unit) {
                is Unit.MoraUnit -> syllables += charArrayOf(unit.mora.cho, unit.mora.jung, ' ')
                Unit.Choon -> {
                    if (mode == HangulMode.OFFICIAL) continue
                    val last = syllables.lastOrNull() ?: continue
                    syllables += charArrayOf('ㅇ', ELONG[last[1]] ?: last[1], ' ')
                }
                Unit.Sokuon, Unit.Hatsuon -> {
                    val following = nextCho(index)
                    val jong = if (unit is Unit.Sokuon) {
                        if (mode == HangulMode.OFFICIAL) 'ㅅ' else (following?.let { SOKUON_JONG[it] } ?: 'ㅅ')
                    } else {
                        if (mode == HangulMode.OFFICIAL) 'ㄴ' else (following?.let { HATSUON_JONG[it] } ?: 'ㄴ')
                    }
                    val last = syllables.lastOrNull()
                    if (last != null && last[2] == ' ') {
                        last[2] = jong
                    } else {
                        // 직전 음절에 이미 받침이 있으면 홀로 선 음절로 적는다 (예: ん -> 은).
                        syllables += charArrayOf('ㅇ', 'ㅡ', jong)
                    }
                }
                is Unit.Raw -> {
                    flush()
                    out.append(unit.ch)
                }
            }
        }

        flush()
        return out.toString()
    }
}
