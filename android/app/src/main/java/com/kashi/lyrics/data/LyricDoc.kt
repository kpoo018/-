package com.kashi.lyrics.data

import org.json.JSONArray
import org.json.JSONObject

/** 가사 한 줄. [timeMs] 가 null 이면 싱크 정보가 없는 가사다. */
data class LyricLine(
    val timeMs: Long?,
    val original: String,
    val reading: String,
    val hangul: String,
    val translation: String,
) {
    val isBlank: Boolean get() = original.isBlank()
}

/** 파이프라인이 만들어 준 한 곡짜리 가사 문서. */
data class LyricDoc(
    val trackId: String,
    val artist: String,
    val title: String,
    val album: String,
    val durationMs: Long,
    val synced: Boolean,
    val hangulMode: String,
    val lines: List<LyricLine>,
    /** 뜻을 만든 번역기. "none" 이면 나중에 번역기가 생겼을 때 다시 만든다. */
    val translator: String = "none",
    val source: String = "",
) {
    /** 시간이 붙은 줄만 모아 둔 색인. 이분 탐색 대상이다. */
    private val timeline: List<Int> =
        lines.indices.filter { lines[it].timeMs != null }

    /**
     * [positionMs] 시점에 보여줄 줄의 위치. 첫 줄보다 앞이면 -1.
     *
     * 싱크가 없는 가사는 언제나 -1 이다. 부를 줄을 짚어 줄 수 없기 때문이다.
     */
    fun indexAt(positionMs: Long): Int {
        if (timeline.isEmpty()) return -1

        var low = 0
        var high = timeline.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            val time = lines[timeline[mid]].timeMs ?: 0L
            if (time <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (found < 0) -1 else timeline[found]
    }

    /** [index] 다음 줄이 시작하는 시각. 마지막 줄이면 null. */
    fun nextTimeAfter(index: Int): Long? {
        val position = if (index < 0) -1 else timeline.indexOf(index)
        return lines.getOrNull(timeline.getOrNull(position + 1) ?: return null)?.timeMs
    }

    fun lineAt(index: Int): LyricLine? = lines.getOrNull(index)

    /** 파이프라인 서버가 내던 것과 같은 모양의 JSON. 캐시 파일 형식이다. */
    fun toJson(): JSONObject {
        val array = JSONArray()
        for (line in lines) {
            array.put(
                JSONObject()
                    .put("time", if (line.timeMs == null) JSONObject.NULL else line.timeMs / 1000.0)
                    .put("original", line.original)
                    .put("reading", line.reading)
                    .put("hangul", line.hangul)
                    .put("translation", line.translation)
            )
        }
        return JSONObject()
            .put("track_id", trackId)
            .put("artist", artist)
            .put("title", title)
            .put("album", album)
            .put("duration", durationMs / 1000.0)
            .put("synced", synced)
            .put("hangul_mode", hangulMode)
            .put("translator", translator)
            .put("source", source)
            .put("lines", array)
    }

    companion object {
        fun parse(json: String): LyricDoc {
            val root = JSONObject(json)
            val array = root.optJSONArray("lines")
            val lines = buildList {
                for (i in 0 until (array?.length() ?: 0)) {
                    val item = array!!.getJSONObject(i)
                    add(
                        LyricLine(
                            // 서버는 초 단위 실수로 준다. 안드로이드 쪽 계산은 전부 밀리초다.
                            timeMs = if (item.isNull("time")) null
                            else (item.getDouble("time") * 1000).toLong(),
                            original = item.optString("original"),
                            reading = item.optString("reading"),
                            hangul = item.optString("hangul"),
                            translation = item.optString("translation"),
                        )
                    )
                }
            }
            return LyricDoc(
                trackId = root.optString("track_id"),
                artist = root.optString("artist"),
                title = root.optString("title"),
                album = root.optString("album"),
                durationMs = (root.optDouble("duration", 0.0) * 1000).toLong(),
                synced = root.optBoolean("synced"),
                hangulMode = root.optString("hangul_mode", "pronunciation"),
                lines = lines,
                translator = root.optString("translator", "none"),
                source = root.optString("source"),
            )
        }
    }
}
