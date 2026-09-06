package com.kashi.lyrics.data

import android.content.Context
import android.util.Log
import java.io.File
import com.kashi.lyrics.text.Reading
import org.json.JSONObject

/**
 * 곡별로 사용자가 직접 고친 한자 읽기.
 *
 * 형태소 분석기는 한자 하나에 여러 읽기가 있을 때 가장 흔한 것을 고른다. 노래는 자주
 * 그렇지 않다. 明日 를 아시타 대신 아스로, 行こう 를 이코오 대신 유코오로 부르는 식이다.
 * 어느 쪽이 맞는지는 곡마다 다르므로 전역 사전으로는 풀 수 없고, 곡별로 기억해야 한다.
 *
 * 값은 한글로도 가나로도 쓸 수 있다. 한글은 변환기를 그대로 통과하므로, 일본어 키보드
 * 없이 들리는 대로 적으면 된다.
 */
class ReadingOverrides(context: Context) {

    private val dir = File(context.filesDir, "overrides")

    fun forTrack(trackId: String): Map<String, String> {
        val file = File(dir, "$trackId.json")
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            buildMap {
                for (key in root.keys()) put(key, root.getString(key))
            }
        } catch (e: Exception) {
            file.delete() // 깨진 파일은 지우고 없는 셈 친다.
            emptyMap()
        }
    }

    /** [reading] 이 비어 있으면 그 표현의 override 를 지운다. */
    fun put(trackId: String, surface: String, reading: String) {
        val updated = forTrack(trackId).toMutableMap()
        if (reading.isBlank()) updated.remove(surface) else updated[surface] = reading.trim()
        write(trackId, updated)
    }

    private fun write(trackId: String, map: Map<String, String>) {
        try {
            val file = File(dir, "$trackId.json")
            if (map.isEmpty()) {
                file.delete()
                return
            }
            file.parentFile?.mkdirs()
            file.writeText(JSONObject(map as Map<*, *>).toString())
        } catch (e: Exception) {
            Log.w(TAG, "읽기 교정을 저장하지 못했습니다", e)
        }
    }

    companion object {
        private const val TAG = "ReadingOverrides"

        /** 한 줄에서 읽기를 고칠 만한 단위를 뽑는다. 형태소 경계를 지킨다. */
        fun correctableUnits(line: String): List<String> = Reading.correctableUnits(line)
    }
}
