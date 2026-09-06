package com.kashi.lyrics.player

import android.os.SystemClock

/**
 * 지금 재생 중인 곡의 상태.
 *
 * 재생 위치는 스냅샷이다. [positionNowMs] 가 그 위에 흐른 시간을 더해 실제 위치를
 * 추정한다. 이 보간이 없으면 가사가 폴링 간격만큼 뚝뚝 끊긴다.
 */
data class NowPlaying(
    val packageName: String,
    val artist: String,
    val title: String,
    val album: String,
    val durationMs: Long,
    /** 스냅샷을 뜬 시점의 재생 위치. */
    val positionMs: Long,
    val speed: Float,
    val isPlaying: Boolean,
    /** 스냅샷을 뜬 시각. SystemClock.elapsedRealtime 기준. */
    val updatedAtElapsed: Long,
) {
    /** 지금 이 순간의 재생 위치 추정값. */
    fun positionNowMs(): Long {
        if (!isPlaying) return positionMs
        val elapsed = SystemClock.elapsedRealtime() - updatedAtElapsed
        return positionMs + (elapsed * speed).toLong()
    }

    /** 같은 곡인지. 재생 위치는 계속 바뀌므로 곡을 가리키는 값만 본다. */
    fun isSameTrack(other: NowPlaying?): Boolean =
        other != null && artist == other.artist && title == other.title && album == other.album

    val hasTrackInfo: Boolean get() = title.isNotBlank()

    val label: String get() = if (artist.isBlank()) title else "$artist - $title"
}
