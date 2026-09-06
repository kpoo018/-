package com.kashi.lyrics.data

import android.content.Context
import android.util.Log
import com.kashi.lyrics.player.NowPlaying
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 가사 문서를 가져온다. 디스크 캐시를 먼저 보고, 없을 때만 서버를 부른다.
 *
 * 서버는 형태소 분석과 번역을 곡당 한 번만 한다. 앱 쪽 캐시는 그 위에 한 겹 더 얹어
 * 같은 곡을 다시 들을 때 네트워크를 아예 타지 않게 한다.
 */
class LyricRepository(context: Context, private val settings: Settings) {

    private val cacheDir = File(context.cacheDir, "lyrics")

    /** 찾아봤지만 없었던 곡. 한 곡 때문에 매번 서버를 두드리지 않도록 기억해 둔다. */
    private val misses = mutableSetOf<String>()

    suspend fun load(track: NowPlaying): LyricDoc? = withContext(Dispatchers.IO) {
        val mode = settings.hangulMode
        val key = cacheKey(track.artist, track.title, track.album)

        readCache(key, mode)?.let { return@withContext it }
        if ("$key/$mode" in misses) return@withContext null

        val server = settings.serverUrl
        if (server.isBlank()) return@withContext null

        val body = try {
            fetch(server, track, mode)
        } catch (e: Exception) {
            // 네트워크 실패는 '없음'과 다르다. 미스로 기록하지 않고 다음에 다시 시도한다.
            Log.w(TAG, "가사를 받아오지 못했습니다: ${track.title}", e)
            return@withContext null
        }

        if (body == null) {
            misses += "$key/$mode"
            return@withContext null
        }

        // 서버가 준 원문을 그대로 담아 둔다. 다시 직렬화하면서 잃는 것이 없다.
        writeCache(key, mode, body)
        try {
            LyricDoc.parse(body)
        } catch (e: Exception) {
            Log.w(TAG, "가사 문서를 읽지 못했습니다", e)
            null
        }
    }

    /** 표기 모드를 바꿨을 때처럼, 받아 둔 것을 버려야 할 때 부른다. */
    fun clearMisses() = misses.clear()

    /** 서버 응답 본문. 가사가 없으면 null. */
    private fun fetch(server: String, track: NowPlaying, mode: String): String? {
        val query = buildString {
            append("artist=").append(encode(track.artist))
            append("&title=").append(encode(track.title))
            if (track.album.isNotBlank()) append("&album=").append(encode(track.album))
            if (track.durationMs > 0) append("&duration=").append(track.durationMs / 1000)
            append("&mode=").append(encode(mode))
        }

        val connection = (URL("$server/lyrics?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            // 서버가 처음 보는 곡을 만나면 형태소 분석과 번역을 거치므로 넉넉히 준다.
            readTimeout = 120_000
        }

        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> {
                    Log.w(TAG, "서버가 ${connection.responseCode} 을 돌려줬습니다")
                    null
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheFile(key: String, mode: String) = File(File(cacheDir, mode), "$key.json")

    private fun readCache(key: String, mode: String): LyricDoc? {
        val file = cacheFile(key, mode)
        if (!file.exists()) return null
        return try {
            LyricDoc.parse(file.readText())
        } catch (e: Exception) {
            // 깨진 캐시는 지우고 없는 셈 친다.
            file.delete()
            null
        }
    }

    private fun writeCache(key: String, mode: String, body: String) {
        try {
            val file = cacheFile(key, mode)
            file.parentFile?.mkdirs()
            file.writeText(body)
        } catch (e: Exception) {
            Log.w(TAG, "캐시를 쓰지 못했습니다", e)
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val TAG = "LyricRepository"

        /** 서버의 make_track_id 와 같은 규칙. 아티스트+제목+앨범의 SHA-1 앞 16자리. */
        fun cacheKey(artist: String, title: String, album: String): String {
            val raw = (artist.trim() + title.trim() + album.trim()).lowercase()
            return MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }
    }
}
