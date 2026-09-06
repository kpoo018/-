package com.kashi.lyrics.data

import android.content.Context

/** 앱 설정. 서버 주소와 표기 모드, 싱크 보정값을 담는다. */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kashi", Context.MODE_PRIVATE)

    /** 파이프라인 서버 주소. 예: http://192.168.0.10:8765 */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "")!!
        set(value) = prefs.edit().putString(KEY_SERVER, value.trimEnd('/')).apply()

    /** "pronunciation" 또는 "official". */
    var hangulMode: String
        get() = prefs.getString(KEY_MODE, MODE_PRONUNCIATION)!!
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    /**
     * 사용자가 손으로 맞추는 싱크 보정값(밀리초).
     *
     * 플레이어가 보고하는 재생 위치와 실제 소리 사이에는 기기마다 차이가 있고,
     * LRC 자체의 오차도 있다. 양수면 가사가 더 일찍 넘어간다.
     */
    var offsetMs: Long
        get() = prefs.getLong(KEY_OFFSET, 0L)
        set(value) = prefs.edit().putLong(KEY_OFFSET, value).apply()

    /** 잠금화면 알림을 띄울지. */
    var notificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION, value).apply()

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    companion object {
        const val MODE_PRONUNCIATION = "pronunciation"
        const val MODE_OFFICIAL = "official"

        private const val KEY_SERVER = "server_url"
        private const val KEY_MODE = "hangul_mode"
        private const val KEY_OFFSET = "offset_ms"
        private const val KEY_NOTIFICATION = "notification_enabled"
    }
}
