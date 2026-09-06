package com.kashi.lyrics.service

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.kashi.lyrics.LyricState
import com.kashi.lyrics.R
import com.kashi.lyrics.data.LyricDoc
import com.kashi.lyrics.data.LyricRepository
import com.kashi.lyrics.data.Settings
import com.kashi.lyrics.player.MediaWatcher
import com.kashi.lyrics.player.NowPlaying
import com.kashi.lyrics.text.Reading
import com.kashi.lyrics.widget.LyricWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 가사를 굴리는 본체.
 *
 * 알림 접근 권한을 받은 리스너 서비스는 시스템이 붙잡아 두므로 따로 포그라운드 서비스를
 * 띄우지 않아도 된다. MediaSession 을 읽으려면 어차피 같은 권한이 필요하다.
 *
 * 화면을 일정 간격으로 갱신하지 않는다. 다음 가사 줄이 시작하는 시각을 계산해 그때
 * 딱 한 번 깨어난다. 폴링보다 배터리에 훨씬 낫고, 줄이 넘어가는 순간도 더 정확하다.
 */
class LyricListenerService : NotificationListenerService() {

    private lateinit var settings: Settings
    private lateinit var repository: LyricRepository
    private lateinit var notifier: LyricNotifier

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tick = Runnable { reschedule() }

    private var watcher: MediaWatcher? = null
    private var track: NowPlaying? = null
    private var doc: LyricDoc? = null
    private var fetchJob: Job? = null

    /** 마지막으로 그린 내용. 같은 것을 다시 그리지 않으려고 들고 있다. */
    private var lastRenderKey: String? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repository = LyricRepository(this, settings)
        notifier = LyricNotifier(this)
        instance = this
        // 형태소 사전 로딩에 1~2초 걸린다. 첫 곡에서 기다리지 않도록 미리 올려 둔다.
        scope.launch(Dispatchers.IO) { Reading.warmUp() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        watcher?.stop()
        watcher = MediaWatcher(
            context = this,
            listenerComponent = ComponentName(this, LyricListenerService::class.java),
            onChanged = ::onNowPlayingChanged,
        ).also { it.start() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        stopEverything()
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun stopEverything() {
        handler.removeCallbacks(tick)
        fetchJob?.cancel()
        watcher?.stop()
        watcher = null
        notifier.hide()
        LyricState.clear()
    }

    private fun onNowPlayingChanged(now: NowPlaying?) {
        if (!settings.enabled) {
            goIdle("")
            return
        }
        if (now == null || !now.hasTrackInfo) {
            goIdle(getString(R.string.no_track))
            return
        }

        if (!now.isSameTrack(track)) {
            doc = null
            lastRenderKey = null
            fetchJob?.cancel()
            fetchJob = scope.launch {
                val loaded = repository.load(now)
                // 받아오는 사이에 곡이 바뀌었으면 버린다.
                if (now.isSameTrack(track)) {
                    doc = loaded
                    lastRenderKey = null
                    reschedule()
                }
            }
        }

        track = now
        reschedule()
    }

    /** 아무것도 보여주지 않는 상태로 되돌린다. 꺼졌거나 재생 중인 곡이 없을 때. */
    private fun goIdle(message: String) {
        track = null
        doc = null
        handler.removeCallbacks(tick)
        fetchJob?.cancel()
        lastRenderKey = null
        notifier.hide()
        LyricWidgetProvider.render(this, "", null, placeholder = message)
        LyricState.clear(message)
    }

    /** 지금 보여줄 줄을 그리고, 다음 줄이 시작할 시각에 다시 깨어나도록 예약한다. */
    private fun reschedule() {
        handler.removeCallbacks(tick)

        if (!settings.enabled) {
            goIdle("")
            return
        }
        val now = track ?: return
        val current = doc
        if (current == null) {
            render(now, null, -1, "가사를 찾는 중…")
            return
        }

        val position = now.positionNowMs() + settings.offsetMs
        val index = current.indexAt(position)
        render(
            now,
            current,
            index,
            if (current.synced) "" else "싱크 정보가 없는 가사입니다",
        )

        if (!now.isPlaying) return

        val nextTime = current.nextTimeAfter(index) ?: return
        val delay = ((nextTime - position) / now.speed).toLong()
        // 아무리 길어도 한 번씩은 깨어나 재생 위치를 다시 읽는다. 플레이어가 상태를
        // 자주 알려주지 않으면 추정값이 조금씩 밀리기 때문이다.
        handler.postDelayed(tick, delay.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS))
    }

    private fun render(now: NowPlaying, current: LyricDoc?, index: Int, message: String) {
        val line = current?.lineAt(index)
        val key = "${now.label}|$index|$message|${current?.trackId}"
        if (key == lastRenderKey) return
        lastRenderKey = key

        val placeholder = if (current == null) message else null
        if (settings.notificationEnabled) {
            notifier.show(now.label, line, placeholder)
        } else {
            notifier.hide()
        }
        LyricWidgetProvider.render(this, now.label, line, placeholder)
        LyricState.update(now, current, index, message)
    }

    /** 설정이 바뀌었을 때 화면 쪽에서 부른다. */
    private fun onSettingsChanged() {
        repository.clearMisses()
        if (!settings.enabled) {
            goIdle("")
            return
        }
        doc = null
        lastRenderKey = null
        val now = track
        if (now != null) {
            // 곡이 바뀐 것처럼 다시 태워 표기 모드와 번역기 설정을 새로 반영한다.
            track = null
            onNowPlayingChanged(now)
        } else {
            watcher?.refresh()
        }
    }

    companion object {
        private const val TAG = "LyricListenerService"
        private const val MIN_DELAY_MS = 50L
        private const val MAX_DELAY_MS = 10_000L

        @Volatile
        private var instance: LyricListenerService? = null

        /** 설정을 바꾼 뒤 곧바로 반영하고 싶을 때. 서비스가 죽어 있으면 아무 일도 없다. */
        fun requestRefresh() {
            val service = instance ?: return
            service.handler.post {
                try {
                    service.onSettingsChanged()
                } catch (e: Exception) {
                    Log.w(TAG, "설정을 다시 반영하지 못했습니다", e)
                }
            }
        }
    }
}
