package com.kashi.lyrics.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kashi.lyrics.R
import com.kashi.lyrics.data.LyricLine
import com.kashi.lyrics.ui.MainActivity

/**
 * 잠금화면에 뜨는 3단 가사 알림.
 *
 * 잠금화면 위젯은 Android 16 QPR2 부터야 폰에 돌아왔다. 그 아래 버전까지 덮으려면
 * 상시 알림이 가장 확실한 수단이라 이쪽을 주력으로 쓴다.
 */
class LyricNotifier(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            // 소리도 진동도 없이 조용히 갱신되어야 한다.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(trackLabel: String, line: LyricLine?, placeholder: String? = null) {
        val views = RemoteViews(context.packageName, R.layout.notification_lyrics).apply {
            setTextViewText(R.id.track, trackLabel)
            setTextViewText(R.id.original, placeholder ?: line?.original.orEmpty())
            setTextViewText(R.id.hangul, if (placeholder != null) "" else line?.hangul.orEmpty())
            setTextViewText(
                R.id.translation,
                if (placeholder != null) "" else line?.translation.orEmpty()
            )
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            // 잠금화면에서 내용까지 보이게 한다. 가리면 이 앱은 쓸모가 없다.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun hide() = manager.cancel(NOTIFICATION_ID)

    companion object {
        private const val CHANNEL_ID = "lyrics"
        private const val NOTIFICATION_ID = 1001
    }
}
