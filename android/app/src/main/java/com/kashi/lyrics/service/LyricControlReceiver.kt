package com.kashi.lyrics.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kashi.lyrics.data.Settings

/**
 * 알림의 "끄기" 버튼.
 *
 * 상시 알림은 쓸어서 지울 수 없다. 지워지면 가사가 넘어가는 것도 멈춰야 하는데 그걸
 * 알 방법이 없기 때문이다. 대신 명시적인 끄기 버튼을 둔다.
 */
class LyricControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TURN_OFF) return
        Settings(context).enabled = false
        LyricListenerService.requestRefresh()
    }

    companion object {
        const val ACTION_TURN_OFF = "com.kashi.lyrics.TURN_OFF"
    }
}
