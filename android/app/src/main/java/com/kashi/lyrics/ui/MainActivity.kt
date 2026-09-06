package com.kashi.lyrics.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kashi.lyrics.LyricState
import com.kashi.lyrics.data.Settings
import com.kashi.lyrics.service.LyricListenerService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)

        // Android 13+ 는 알림을 띄우려면 따로 허락을 받아야 한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppScreen(settings)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(settings: Settings) {
    var showSettings by remember { mutableStateOf(!settings.isConfigured) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("가사") },
                actions = {
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "닫기" else "설정")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                SettingsPanel(settings)
                HorizontalDivider()
            }
            LyricPane()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(settings: Settings) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var mode by remember { mutableStateOf(settings.hangulMode) }
    var offset by remember { mutableFloatStateOf(settings.offsetMs.toFloat()) }

    val listenerEnabled = remember {
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!listenerEnabled) {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("알림 접근 권한이 필요합니다", fontWeight = FontWeight.Bold)
                    Text(
                        "재생 중인 곡과 재생 위치를 읽으려면 이 권한이 있어야 합니다. "
                            + "Spotify 계정이나 개발자 등록은 필요하지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }) { Text("권한 설정 열기") }
                }
            }
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                settings.serverUrl = it
            },
            label = { Text("가사 서버 주소") },
            placeholder = { Text("http://192.168.0.10:8765") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("한글 표기", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(
                Settings.MODE_PRONUNCIATION to "발음 (토오쿄오)",
                Settings.MODE_OFFICIAL to "표기법 (도쿄)",
            )
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = mode == value,
                    onClick = {
                        mode = value
                        settings.hangulMode = value
                        LyricListenerService.requestRefresh()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }

        Text("싱크 보정: ${offset.toInt()}ms", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = offset,
            onValueChange = { offset = it },
            onValueChangeFinished = { settings.offsetMs = offset.toLong() },
            valueRange = -3000f..3000f,
            steps = 59,
        )
        Text(
            "가사가 늦게 넘어가면 오른쪽으로 옮기세요.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LyricPane() {
    val snapshot by LyricState.snapshot.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val doc = snapshot.doc

    // 부르고 있는 줄이 넘어갈 때마다 따라 내려간다. 화면 한가운데쯤에 오도록 살짝 앞당긴다.
    LaunchedEffect(snapshot.lineIndex, doc) {
        if (doc != null && snapshot.lineIndex >= 0) {
            listState.animateScrollToItem((snapshot.lineIndex - 3).coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize()) {
        val track = snapshot.track
        if (track != null) {
            Text(
                track.label,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(snapshot.message.ifBlank { "재생을 시작하면 가사가 나옵니다" })
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(doc.lines) { index, line ->
                if (line.isBlank) {
                    Spacer(Modifier.height(6.dp))
                } else {
                    LyricRow(
                        original = line.original,
                        hangul = line.hangul,
                        translation = line.translation,
                        isCurrent = index == snapshot.lineIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricRow(
    original: String,
    hangul: String,
    translation: String,
    isCurrent: Boolean,
) {
    val alpha = if (isCurrent) 1f else 0.45f
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                else Modifier
            )
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Text(
            original,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (hangul.isNotBlank()) {
            Text(
                hangul,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        }
        if (translation.isNotBlank()) {
            Text(
                translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.8f),
            )
        }
    }
}
