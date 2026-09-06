package com.kashi.lyrics.data

import android.util.Log
import com.kashi.lyrics.text.HangulMode
import com.kashi.lyrics.text.Reading
import java.io.IOException

/**
 * 가사 한 곡을 원문 + 한글 발음 + 뜻으로 가공한다. 전부 폰에서 돈다.
 *
 * pipeline/kashi/pipeline.py 의 build() 와 같은 일을 한다.
 */
object LyricBuilder {

    private const val TAG = "LyricBuilder"

    /**
     * 이미 만들어 둔 문서의 원문만 다시 분석한다. 네트워크를 타지 않는다.
     *
     * 표기 모드를 바꾸거나 읽기 교정을 저장했을 때 쓴다. 시간·원문·뜻은 그대로 두고
     * 읽기와 한글만 다시 만든다.
     */
    fun reanalyze(doc: LyricDoc, mode: HangulMode, overrides: Map<String, String>): LyricDoc {
        val lines = doc.lines.map { line ->
            if (line.original.isBlank()) {
                line.copy(reading = "", hangul = "")
            } else {
                val parsed = Reading.analyze(line.original, overrides)
                line.copy(reading = parsed.reading(mode), hangul = parsed.hangul(mode))
            }
        }
        return doc.copy(lines = lines, hangulMode = mode.id)
    }

    /**
     * LRCLIB 에서 가사를 받아 3단 문서로 만든다. 가사가 없으면 null.
     *
     * 번역 실패는 문서를 버릴 이유가 못 된다. 뜻만 비운 채 돌려주고 [LyricDoc.translator] 를
     * "none" 으로 남겨 다음에 다시 시도할 수 있게 한다.
     */
    @Throws(IOException::class)
    fun build(
        artist: String,
        title: String,
        album: String,
        durationMs: Long,
        mode: HangulMode,
        translator: Translator,
        overrides: Map<String, String> = emptyMap(),
    ): LyricDoc? {
        val durationSec = if (durationMs > 0) durationMs / 1000 else null
        val track = Lrclib.find(artist, title, album, durationSec) ?: return null
        val (rawLines, synced) = Lrclib.toLines(track)
        if (rawLines.isEmpty()) return null

        val analyzed = rawLines.map { (_, text) -> if (text.isBlank()) null else Reading.analyze(text, overrides) }

        var translations = rawLines.map { "" }
        var translatorName = Translator.PROVIDER_NONE
        if (translator.name != Translator.PROVIDER_NONE) {
            try {
                translations = translator.translateLines(rawLines.map { it.second })
                translatorName = translator.name
            } catch (e: TranslationException) {
                Log.w(TAG, "번역에 실패했습니다. 뜻 없이 계속합니다: ${e.message}")
            }
        }

        val lines = rawLines.indices.map { i ->
            val (timeMs, text) = rawLines[i]
            val parsed = analyzed[i]
            LyricLine(
                timeMs = timeMs,
                original = text,
                reading = parsed?.reading(mode).orEmpty(),
                hangul = parsed?.hangul(mode).orEmpty(),
                translation = translations[i],
            )
        }

        return LyricDoc(
            trackId = LyricRepository.cacheKey(track.artist, track.title, track.album),
            artist = track.artist,
            title = track.title,
            album = track.album,
            durationMs = (track.durationSec * 1000).toLong(),
            synced = synced,
            hangulMode = mode.id,
            translator = translatorName,
            source = "lrclib:${track.id}",
            lines = lines,
        )
    }
}
