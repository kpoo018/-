package com.kashi.lyrics.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.kashi.lyrics.LyricState
import com.kashi.lyrics.R
import com.kashi.lyrics.data.LyricDoc
import com.kashi.lyrics.data.LyricLine
import com.kashi.lyrics.data.Settings
import com.kashi.lyrics.data.Translator
import com.kashi.lyrics.service.LyricListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 전체 가사 화면과 설정.
 *
 * AndroidX 없이 프레임워크 View 만 쓴다. 의존성이 kotlin-stdlib, coroutines, kuromoji 뿐이라
 * Google Maven 없이도 빌드된다.
 */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var adapter: LyricAdapter

    private lateinit var settingsPanel: View
    private lateinit var toggleButton: Button
    private lateinit var trackLabel: TextView
    private lateinit var message: TextView
    private lateinit var list: ListView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var lastScrolledIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        settingsPanel = findViewById(R.id.settings_panel)
        toggleButton = findViewById(R.id.toggle_settings)
        trackLabel = findViewById(R.id.track_label)
        message = findViewById(R.id.message)
        list = findViewById(R.id.lyric_list)

        adapter = LyricAdapter(LayoutInflater.from(this))
        list.adapter = adapter

        bindSettings()
        // 권한이 아직 없으면 설정부터 보여준다. 그게 첫 화면에서 할 일이다.
        showSettings(!isListenerEnabled())

        // Android 13+ 는 알림을 띄우려면 따로 허락을 받아야 한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermissionCard()
        collectJob = scope.launch {
            LyricState.snapshot.collect { render(it) }
        }
    }

    override fun onStop() {
        collectJob?.cancel()
        collectJob = null
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showSettings(visible: Boolean) {
        settingsPanel.visibility = if (visible) View.VISIBLE else View.GONE
        toggleButton.setText(if (visible) R.string.close else R.string.settings)
    }

    private fun bindSettings() {
        toggleButton.setOnClickListener {
            showSettings(settingsPanel.visibility != View.VISIBLE)
        }

        findViewById<Button>(R.id.open_permission).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        bindModeGroup()
        bindTranslator()
        bindOffset()
    }

    private fun bindModeGroup() {
        val modeGroup = findViewById<RadioGroup>(R.id.mode_group)
        modeGroup.check(
            if (settings.hangulMode == Settings.MODE_OFFICIAL) R.id.mode_official
            else R.id.mode_pronunciation
        )
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode =
                if (checkedId == R.id.mode_official) Settings.MODE_OFFICIAL
                else Settings.MODE_PRONUNCIATION
            if (mode != settings.hangulMode) {
                settings.hangulMode = mode
                LyricListenerService.requestRefresh()
            }
        }
    }

    private fun bindTranslator() {
        val group = findViewById<RadioGroup>(R.id.translator_group)
        val keyField = findViewById<EditText>(R.id.api_key)
        val secretField = findViewById<EditText>(R.id.api_secret)

        val ids = mapOf(
            Translator.PROVIDER_NONE to R.id.translator_none,
            Translator.PROVIDER_CLAUDE to R.id.translator_claude,
            Translator.PROVIDER_DEEPL to R.id.translator_deepl,
            Translator.PROVIDER_PAPAGO to R.id.translator_papago,
        )

        fun applyProvider(provider: String) {
            val papago = provider == Translator.PROVIDER_PAPAGO
            keyField.visibility = if (provider == Translator.PROVIDER_NONE) View.GONE else View.VISIBLE
            keyField.setHint(if (papago) R.string.papago_id_hint else R.string.api_key_hint)
            secretField.visibility = if (papago) View.VISIBLE else View.GONE
        }

        group.check(ids[settings.translatorProvider] ?: R.id.translator_none)
        keyField.setText(settings.apiKey)
        secretField.setText(settings.apiSecret)
        applyProvider(settings.translatorProvider)

        group.setOnCheckedChangeListener { _, checkedId ->
            val provider = ids.entries.firstOrNull { it.value == checkedId }?.key ?: Translator.PROVIDER_NONE
            applyProvider(provider)
            if (provider != settings.translatorProvider) {
                settings.translatorProvider = provider
                LyricListenerService.requestRefresh()
            }
        }

        fun saveKeys() {
            val key = keyField.text.toString().trim()
            val secret = secretField.text.toString().trim()
            if (key != settings.apiKey || secret != settings.apiSecret) {
                settings.apiKey = key
                settings.apiSecret = secret
                LyricListenerService.requestRefresh()
            }
        }
        for (field in listOf(keyField, secretField)) {
            field.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveKeys() }
            field.setOnEditorActionListener { _, _, _ -> saveKeys(); false }
        }
    }

    private fun bindOffset() {
        val offsetLabel = findViewById<TextView>(R.id.offset_label)
        val offsetSeek = findViewById<SeekBar>(R.id.offset_seek)
        // -3000ms ~ +3000ms 를 100ms 단위로. progress 30 이 0ms 다.
        offsetSeek.progress = ((settings.offsetMs / OFFSET_STEP_MS) + OFFSET_STEPS / 2).toInt()
            .coerceIn(0, OFFSET_STEPS)
        offsetLabel.text = getString(R.string.offset_label, settings.offsetMs)
        offsetSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                offsetLabel.text = getString(R.string.offset_label, offsetOf(progress))
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) {
                settings.offsetMs = offsetOf(bar.progress)
            }
        })
    }

    private fun offsetOf(progress: Int): Long = (progress - OFFSET_STEPS / 2) * OFFSET_STEP_MS

    private fun isListenerEnabled(): Boolean =
        AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun refreshPermissionCard() {
        findViewById<View>(R.id.permission_card).visibility =
            if (isListenerEnabled()) View.GONE else View.VISIBLE
    }

    private fun render(snapshot: LyricState.Snapshot) {
        val track = snapshot.track
        trackLabel.visibility = if (track != null) View.VISIBLE else View.GONE
        trackLabel.text = track?.label.orEmpty()

        val doc = snapshot.doc
        if (doc == null) {
            list.visibility = View.GONE
            message.visibility = View.VISIBLE
            message.text = snapshot.message.ifBlank { getString(R.string.idle_message) }
            adapter.update(null, -1)
            lastScrolledIndex = -1
            return
        }

        message.visibility = View.GONE
        list.visibility = View.VISIBLE
        adapter.update(doc, snapshot.lineIndex)

        // 부르는 줄이 넘어갈 때마다 따라 내려간다. 화면 위쪽 1/3 쯤에 오도록 살짝 앞당긴다.
        if (snapshot.lineIndex >= 0 && snapshot.lineIndex != lastScrolledIndex) {
            lastScrolledIndex = snapshot.lineIndex
            list.smoothScrollToPositionFromTop(snapshot.lineIndex, list.height / 3)
        }
    }

    /** 원문/발음/뜻 세 줄짜리 행. 현재 줄만 밝게, 나머지는 흐리게. */
    private class LyricAdapter(private val inflater: LayoutInflater) : BaseAdapter() {
        private var doc: LyricDoc? = null
        private var currentIndex = -1

        fun update(next: LyricDoc?, index: Int) {
            val changed = next !== doc || index != currentIndex
            doc = next
            currentIndex = index
            if (changed) notifyDataSetChanged()
        }

        override fun getCount(): Int = doc?.lines?.size ?: 0
        override fun getItem(position: Int): LyricLine? = doc?.lines?.getOrNull(position)
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.row_lyric, parent, false)
            val line = getItem(position)
            val current = position == currentIndex
            val alpha = if (current) 1f else 0.45f

            view.findViewById<View>(R.id.row).setBackgroundColor(
                if (current) Color.parseColor("#2A2A3A") else Color.TRANSPARENT
            )
            view.findViewById<TextView>(R.id.original).apply {
                text = line?.original.orEmpty()
                setTextColor(Color.WHITE)
                this.alpha = alpha
            }
            view.findViewById<TextView>(R.id.hangul).apply {
                text = line?.hangul.orEmpty()
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
                setTextColor(Color.parseColor("#BB86FC"))
                this.alpha = alpha
            }
            view.findViewById<TextView>(R.id.translation).apply {
                text = line?.translation.orEmpty()
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
                setTextColor(Color.parseColor("#CCCCCC"))
                this.alpha = alpha
            }
            return view
        }
    }

    companion object {
        private const val OFFSET_STEP_MS = 100L
        private const val OFFSET_STEPS = 60
    }
}
