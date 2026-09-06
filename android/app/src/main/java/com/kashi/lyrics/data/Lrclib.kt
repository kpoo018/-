package com.kashi.lyrics.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * LRCLIB 클라이언트와 LRC 파서. API 키가 없고, 평문 가사와 시간이 붙은 가사를 함께 준다.
 * https://lrclib.net/docs
 */
object Lrclib {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "kashi-android/0.2 (https://github.com/kpoo018)"
    private const val TIMEOUT_MS = 15_000

    class Track(
        val id: Long,
        val artist: String,
        val title: String,
        val album: String,
        val durationSec: Double,
        val plainLyrics: String,
        val syncedLyrics: String,
    ) {
        val isInstrumental: Boolean get() = plainLyrics.isBlank() && syncedLyrics.isBlank()

        companion object {
            fun fromJson(o: JSONObject) = Track(
                id = o.optLong("id"),
                artist = o.optString("artistName"),
                title = o.optString("trackName"),
                album = o.optString("albumName"),
                durationSec = o.optDouble("duration", 0.0),
                plainLyrics = if (o.isNull("plainLyrics")) "" else o.optString("plainLyrics"),
                syncedLyrics = if (o.isNull("syncedLyrics")) "" else o.optString("syncedLyrics"),
            )
        }
    }

    /** 404 는 null. 그 외 실패는 IOException. */
    @Throws(IOException::class)
    private fun request(path: String, params: Map<String, String>): String? {
        val query = params.filterValues { it.isNotBlank() }
            .map { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
            .joinToString("&")
        val connection = (URL("$BASE_URL/$path?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("LRCLIB $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** 정확 매칭. 재생 길이를 함께 주면 다른 버전을 집을 확률이 크게 준다. */
    @Throws(IOException::class)
    fun get(artist: String, title: String, album: String = "", durationSec: Long? = null): Track? {
        val params = mutableMapOf("artist_name" to artist, "track_name" to title, "album_name" to album)
        if (durationSec != null && durationSec > 0) params["duration"] = durationSec.toString()
        return request("get", params)?.let { Track.fromJson(JSONObject(it)) }
    }

    @Throws(IOException::class)
    fun search(query: String = "", artist: String = "", title: String = ""): List<Track> {
        val body = request("search", mapOf("q" to query, "artist_name" to artist, "track_name" to title))
            ?: return emptyList()
        val array = JSONArray(body)
        return (0 until array.length()).map { Track.fromJson(array.getJSONObject(it)) }
    }

    /**
     * 정확 매칭을 먼저 보고, 없으면 검색으로 떨어진다.
     * 검색 결과 중에서는 시간이 붙은 가사를, 그 다음으로 재생 길이가 가까운 것을 고른다.
     */
    @Throws(IOException::class)
    fun find(artist: String, title: String, album: String = "", durationSec: Long? = null): Track? {
        get(artist, title, album, durationSec)?.takeIf { !it.isInstrumental }?.let { return it }

        var candidates = search(artist = artist, title = title).filter { !it.isInstrumental }
        if (candidates.isEmpty() && (artist.isNotBlank() || title.isNotBlank())) {
            candidates = search(query = "$artist $title".trim()).filter { !it.isInstrumental }
        }
        if (candidates.isEmpty()) return null

        return candidates.minWith(
            compareBy<Track> { if (it.syncedLyrics.isNotBlank()) 0 else 1 }
                .thenBy { if (durationSec != null) Math.abs(it.durationSec - durationSec) else 0.0 }
        )
    }

    private val TIMESTAMP = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val METADATA = Regex("""^\[[a-z]+:""")

    /**
     * LRC 본문을 (밀리초, 가사) 목록으로 바꾼다.
     * 한 줄에 타임스탬프가 여러 개 붙을 수 있다(반복 후렴). 시간순으로 정렬한다.
     */
    fun parseLrc(text: String): List<Pair<Long?, String>> {
        val lines = ArrayList<Pair<Long?, String>>()
        for (raw in text.lines()) {
            val stamps = TIMESTAMP.findAll(raw).toList()
            val content = TIMESTAMP.replace(raw, "").trim()
            if (stamps.isEmpty()) {
                // [ar:...] 같은 메타데이터 줄은 버린다.
                if (content.isNotEmpty() && !METADATA.containsMatchIn(raw.trim())) lines += null to content
                continue
            }
            for (stamp in stamps) {
                val (min, sec, frac) = stamp.destructured
                val millis = frac.padEnd(3, '0').ifEmpty { "000" }.toInt()
                lines += (min.toLong() * 60_000 + sec.toLong() * 1000 + millis) to content
            }
        }
        if (lines.any { it.first != null }) lines.sortBy { it.first ?: 0L }
        return lines
    }

    /** 가사 본문에서 (줄 목록, 싱크 여부)를 뽑는다. 빈 줄(간주)은 남긴다. */
    fun toLines(track: Track): Pair<List<Pair<Long?, String>>, Boolean> =
        if (track.syncedLyrics.isNotBlank()) parseLrc(track.syncedLyrics) to true
        else track.plainLyrics.lines().map { null to it.trim() } to false
}
