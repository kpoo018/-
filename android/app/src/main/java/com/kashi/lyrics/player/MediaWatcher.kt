package com.kashi.lyrics.player

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 어떤 앱이 무엇을 재생 중인지 지켜본다.
 *
 * Spotify SDK 를 쓰지 않고 안드로이드의 MediaSession 을 읽는다. 그래야
 * Spotify 개발자 등록(개발 모드 5명 제한)에 묶이지 않고, YouTube Music 이나
 * 로컬 플레이어까지 같은 코드로 커버된다. 대신 알림 접근 권한이 필요하다.
 */
class MediaWatcher(
    private val context: Context,
    private val listenerComponent: ComponentName,
    private val onChanged: (NowPlaying?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var controller: MediaController? = null
    private var lastEmitted: NowPlaying? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachTo(pickBest(controllers.orEmpty()))
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = emit()
        override fun onSessionDestroyed() {
            detach()
            // 세션 하나가 사라졌을 뿐 다른 플레이어가 살아 있을 수 있다.
            refresh()
        }
    }

    fun start() {
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsListener, listenerComponent, handler
            )
            refresh()
        } catch (e: SecurityException) {
            // 알림 접근 권한이 아직 없다. 권한을 받으면 서비스가 다시 붙으면서 재시도된다.
            Log.w(TAG, "미디어 세션을 읽을 권한이 없습니다", e)
        }
    }

    fun stop() {
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            Log.w(TAG, "리스너를 떼지 못했습니다", e)
        }
        detach()
    }

    /** 지금 붙어 있는 세션의 상태를 다시 읽어 내보낸다. */
    fun refresh() {
        try {
            attachTo(pickBest(sessionManager.getActiveSessions(listenerComponent)))
        } catch (e: SecurityException) {
            Log.w(TAG, "미디어 세션을 읽을 권한이 없습니다", e)
        }
    }

    /**
     * 재생 중인 세션을 우선한다. 여러 앱이 동시에 세션을 들고 있는 일은 흔하다.
     * (음악은 멈춰 있고 브라우저 탭이 세션을 잡고 있는 식으로.)
     */
    private fun pickBest(controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }

    private fun attachTo(next: MediaController?) {
        if (next?.sessionToken == controller?.sessionToken) {
            emit()
            return
        }
        detach()
        controller = next
        next?.registerCallback(controllerCallback, handler)
        emit()
    }

    private fun detach() {
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun emit() {
        val snapshot = snapshot()
        // 같은 값을 다시 흘려보내지 않는다. 알림과 위젯을 헛되이 다시 그리게 된다.
        if (snapshot == lastEmitted) return
        lastEmitted = snapshot
        onChanged(snapshot)
    }

    private fun snapshot(): NowPlaying? {
        val active = controller ?: return null
        val metadata = active.metadata ?: return null
        val state = active.playbackState

        val playing = state?.state == PlaybackState.STATE_PLAYING
        return NowPlaying(
            packageName = active.packageName,
            artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "",
            title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "",
            album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0L,
            // 정지 중에도 배속이 0 으로 오는 일이 있어 재생 중일 때만 그대로 쓴다.
            speed = if (playing) (state?.playbackSpeed?.takeIf { it > 0f } ?: 1f) else 1f,
            isPlaying = playing,
            updatedAtElapsed = state?.lastPositionUpdateTime
                ?: android.os.SystemClock.elapsedRealtime(),
        )
    }

    companion object {
        private const val TAG = "MediaWatcher"
    }
}
