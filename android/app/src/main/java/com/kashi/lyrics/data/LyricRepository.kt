package com.kashi.lyrics.data

import android.content.Context
import android.util.Log
import com.kashi.lyrics.player.NowPlaying
import com.kashi.lyrics.text.HangulMode
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 가사 문서를 가져온다. 디스크 캐시를 먼저 보고, 없을 때만 새로 만든다.
 *
 * 형태소 분석과 번역은 곡당 한 번이면 된다. 같은 곡을 다시 들을 때는 파일 하나 읽는 것으로 끝난다.
 */
class LyricRepository(context: Context, private val settings: Settings) {

    private val cacheDir = File(context.cacheDir, "lyrics")

    /** 찾아봤지만 없었던 곡. 한 곡 때문에 매번 LRCLIB 을 두드리지 않도록 기억해 둔다. */
    private val misses = mutableSetOf<String>()

    suspend fun load(track: NowPlaying): LyricDoc? = withContext(Dispatchers.IO) {
        val mode = HangulMode.of(settings.hangulMode)
        val key = cacheKey(track.artist, track.title, track.album)
        val translator = settings.translator()

        val cached = readCache(key, mode.id)
        if (cached != null) {
            // 번역 없이 만들어 둔 문서인데 이제 번역기가 생겼으면 다시 만든다.
            val wantsTranslation = translator.name != Translator.PROVIDER_NONE
            if (!wantsTranslation || cached.translator != Translator.PROVIDER_NONE) return@withContext cached
        }
        if ("$key/${mode.id}" in misses) return@withContext null

        val doc = try {
            LyricBuilder.build(track.artist, track.title, track.album, track.durationMs, mode, translator)
        } catch (e: IOException) {
            // 네트워크 실패는 '없음'과 다르다. 미스로 기록하지 않고 다음에 다시 시도한다.
            Log.w(TAG, "가사를 받아오지 못했습니다: ${track.title}", e)
            return@withContext cached
        } catch (e: Exception) {
            Log.w(TAG, "가사를 만들지 못했습니다: ${track.title}", e)
            return@withContext cached
        }

        if (doc == null) {
            misses += "$key/${mode.id}"
            return@withContext null
        }
        writeCache(key, mode.id, doc)
        doc
    }

    /** 표기 모드나 번역기를 바꿨을 때처럼, 받아 둔 것을 버려야 할 때 부른다. */
    fun clearMisses() = misses.clear()

    private fun cacheFile(key: String, mode: String) = File(File(cacheDir, mode), "$key.json")

    private fun readCache(key: String, mode: String): LyricDoc? {
        val file = cacheFile(key, mode)
        if (!file.exists()) return null
        return try {
            LyricDoc.parse(file.readText())
        } catch (e: Exception) {
            file.delete() // 깨진 캐시는 지우고 없는 셈 친다.
            null
        }
    }

    private fun writeCache(key: String, mode: String, doc: LyricDoc) {
        try {
            val file = cacheFile(key, mode)
            file.parentFile?.mkdirs()
            file.writeText(doc.toJson().toString())
        } catch (e: Exception) {
            Log.w(TAG, "캐시를 쓰지 못했습니다", e)
        }
    }

    companion object {
        private const val TAG = "LyricRepository"

        /** 파이프라인의 make_track_id 와 같은 규칙. 아티스트+제목+앨범의 SHA-1 앞 16자리. */
        fun cacheKey(artist: String, title: String, album: String): String {
            val raw = (artist.trim() + title.trim() + album.trim()).lowercase()
            return MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }
    }
}
