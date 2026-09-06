package com.kashi.lyrics

import com.kashi.lyrics.data.LyricDoc
import com.kashi.lyrics.player.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면이 구독하는 현재 상태.
 *
 * 가사를 실제로 굴리는 것은 알림 리스너 서비스다. 화면은 있을 때도 없을 때도 있으므로
 * 서비스가 여기에 결과를 놓고, 화면은 읽기만 한다.
 */
object LyricState {

    data class Snapshot(
        val track: NowPlaying? = null,
        val doc: LyricDoc? = null,
        /** 지금 부르고 있는 줄. 아직 첫 줄 전이거나 싱크가 없으면 -1. */
        val lineIndex: Int = -1,
        val message: String = "",
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    fun update(track: NowPlaying?, doc: LyricDoc?, lineIndex: Int, message: String = "") {
        _snapshot.value = Snapshot(track, doc, lineIndex, message)
    }

    fun clear(message: String = "") {
        _snapshot.value = Snapshot(message = message)
    }
}
