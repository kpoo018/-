package com.kashi.lyrics.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kashi.lyrics.R
import com.kashi.lyrics.data.ReadingOverrides
import com.kashi.lyrics.text.HangulMode
import com.kashi.lyrics.text.Reading

/**
 * 한자 읽기를 곡별로 고치는 대화상자.
 *
 * 사전은 한자 하나에 읽기가 여럿일 때 가장 흔한 것을 고르는데, 노래는 자주 그렇지 않다.
 * 明日 를 아스로, 行こう 를 유코오로 부르는 식이다. 어느 쪽이 맞는지는 곡마다 다르므로
 * 사람이 알려주는 수밖에 없다.
 *
 * 입력은 한글로 받는다. 일본어 키보드 없이 들리는 대로 적으면 되고, 가타카나를 적어도
 * 변환기가 알아서 처리한다.
 */
object ReadingFixDialog {

    fun show(
        context: Context,
        trackId: String,
        line: String,
        mode: HangulMode,
        overrides: ReadingOverrides,
        onSaved: () -> Unit,
    ) {
        val runs = ReadingOverrides.correctableUnits(line)
        if (runs.isEmpty()) {
            Toast.makeText(context, R.string.fix_reading_none, Toast.LENGTH_SHORT).show()
            return
        }
        if (runs.size == 1) {
            askReading(context, trackId, runs.first(), mode, overrides, onSaved)
            return
        }

        val saved = overrides.forTrack(trackId)
        val labels = runs.map { run ->
            val current = saved[run] ?: Reading.analyze(run).hangul(mode)
            "$run   →   $current"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(R.string.fix_reading_pick)
            .setItems(labels) { _, which ->
                askReading(context, trackId, runs[which], mode, overrides, onSaved)
            }
            .setNegativeButton(R.string.fix_reading_cancel, null)
            .show()
    }

    private fun askReading(
        context: Context,
        trackId: String,
        surface: String,
        mode: HangulMode,
        overrides: ReadingOverrides,
        onSaved: () -> Unit,
    ) {
        val saved = overrides.forTrack(trackId)[surface]
        // 저장된 교정이 없으면 사전이 고른 읽기를 미리 채워 둔다. 고칠 곳만 손보면 된다.
        val current = saved ?: Reading.analyze(surface).hangul(mode)

        val input = EditText(context).apply {
            setText(current)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val help = TextView(context).apply {
            setText(R.string.fix_reading_help)
            textSize = 12f
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(help, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.fix_reading_input, surface))
            .setView(container)
            .setPositiveButton(R.string.fix_reading_save) { _, _ ->
                overrides.put(trackId, surface, input.text.toString())
                Toast.makeText(context, R.string.fix_reading_saved, Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton(R.string.fix_reading_cancel, null)

        // 이미 고쳐 둔 것이 있을 때만 되돌리기를 준다.
        if (saved != null) {
            builder.setNeutralButton(R.string.fix_reading_reset) { _, _ ->
                overrides.put(trackId, surface, "")
                onSaved()
            }
        }
        builder.show()
    }
}
