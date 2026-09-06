package com.kashi.lyrics.data

import android.content.Context

/** 앱 설정. 표기 모드, 번역기, 싱크 보정값을 담는다. */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kashi", Context.MODE_PRIVATE)

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

    /**
     * 앱 전체 켜기/끄기.
     *
     * 알림 리스너 서비스는 시스템이 붙잡고 있어 앱이 스스로 멈출 수 없다. 대신 이 값이
     * 꺼지면 아무것도 그리지 않고 예약도 하지 않는다. 권한을 뺏지 않고 멈추는 방법이다.
     */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 잠금화면 알림을 띄울지. */
    var notificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION, value).apply()

    /** 번역기. none / claude / deepl / papago. 번역은 선택이라 없어도 원문+발음은 나온다. */
    var translatorProvider: String
        get() = prefs.getString(KEY_TRANSLATOR, Translator.PROVIDER_NONE)!!
        set(value) = prefs.edit().putString(KEY_TRANSLATOR, value).apply()

    /** Claude/DeepL 의 API 키, 파파고의 client id. */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "")!!
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    /** 파파고의 client secret. 다른 번역기는 쓰지 않는다. */
    var apiSecret: String
        get() = prefs.getString(KEY_API_SECRET, "")!!
        set(value) = prefs.edit().putString(KEY_API_SECRET, value.trim()).apply()

    fun translator(): Translator = Translator.from(translatorProvider, apiKey, apiSecret)

    companion object {
        const val MODE_PRONUNCIATION = "pronunciation"
        const val MODE_OFFICIAL = "official"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "hangul_mode"
        private const val KEY_OFFSET = "offset_ms"
        private const val KEY_NOTIFICATION = "notification_enabled"
        private const val KEY_TRANSLATOR = "translator_provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
    }
}
