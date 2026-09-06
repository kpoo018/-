package com.kashi.lyrics.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kashi.lyrics.R
import com.kashi.lyrics.data.LyricLine
import com.kashi.lyrics.ui.MainActivity

/**
 * 홈 화면 위젯. Android 16 QPR2 이상에서는 잠금화면에도 올릴 수 있다.
 *
 * 위젯은 스스로 갱신하지 않는다(updatePeriodMillis=0). 가사 줄이 넘어갈 때마다
 * [LyricListenerService] 가 [render] 를 불러 준다.
 */
class LyricWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // 위젯을 새로 놓았을 때. 다음 가사 줄에서 실제 내용이 채워진다.
        render(context, "", null, placeholder = context.getString(R.string.app_name))
    }

    companion object {
        fun render(
            context: Context,
            trackLabel: String,
            line: LyricLine?,
            placeholder: String? = null,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LyricWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_lyrics).apply {
                setTextViewText(R.id.track, trackLabel)
                setTextViewText(R.id.original, placeholder ?: line?.original.orEmpty())
                setTextViewText(
                    R.id.hangul,
                    if (placeholder != null) "" else line?.hangul.orEmpty()
                )
                setTextViewText(
                    R.id.translation,
                    if (placeholder != null) "" else line?.translation.orEmpty()
                )
                setOnClickPendingIntent(R.id.original, open)
            }

            manager.updateAppWidget(ids, views)
        }
    }
}
