package com.example.audiobooks

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.ui.AppIcon
import com.example.audiobooks.ui.AppIcons
import com.example.audiobooks.ui.AppText
import com.example.audiobooks.ui.AudioBookViewModel
import com.example.audiobooks.ui.AudioBooksTheme
import com.example.audiobooks.ui.BookCover
import com.example.audiobooks.ui.BookSheet
import com.example.audiobooks.ui.ChaptersOverlay
import com.example.audiobooks.ui.Dim
import com.example.audiobooks.ui.ImportOverlay
import com.example.audiobooks.ui.InBookSearchOverlay
import com.example.audiobooks.ui.LibraryScreen
import com.example.audiobooks.ui.LocalAppColors
import com.example.audiobooks.ui.ModelSheet
import com.example.audiobooks.ui.OnDeviceBadge
import com.example.audiobooks.ui.PlayCircle
import com.example.audiobooks.ui.PlayerOverlay
import com.example.audiobooks.ui.ReaderOverlay
import com.example.audiobooks.ui.SearchScreen
import com.example.audiobooks.ui.SettingsScreen
import com.example.audiobooks.ui.SleepSheet
import com.example.audiobooks.ui.SpeedSheet
import com.example.audiobooks.ui.progressFraction

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AudioBookViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(systemDark) }
            var serif by rememberSaveable { mutableStateOf(false) }
            AudioBooksTheme(dark = dark, serifBase = serif) {
                AudioBooksApp(
                    state = state,
                    viewModel = viewModel,
                    dark = dark,
                    serif = serif,
                    onDarkChange = { dark = it },
                    onSerifChange = { serif = it }
                )
            }
        }
    }
}

@Composable
private fun AudioBooksApp(
    state: AudioBookUiStateAlias,
    viewModel: AudioBookViewModel,
    dark: Boolean,
    serif: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onSerifChange: (Boolean) -> Unit
) {
    val c = LocalAppColors.current
    var tab by rememberSaveable { mutableStateOf("library") }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetBook = state.books.firstOrNull { it.id == sheetBookId }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importBook(uri)
            overlay = null
        }
    }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun launchImport() = importLauncher.launch(
        arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream", "*/*")
    )
    fun downloadModel(id: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.downloadModel(id)
    }

    val selectedModel = state.models.firstOrNull { it.id == state.selectedModelId }
    val voiceShort = selectedModel?.name ?: "未选择音色"

    BackHandler(enabled = sheet != null || overlay != null || tab != "library") {
        when {
            sheet != null -> sheet = null
            overlay == "booksearch" -> overlay = "reader"
            overlay == "chapters" -> overlay = "player"
            overlay != null -> overlay = null
            tab != "library" -> tab = "library"
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // ---- tab layer ----
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                "search" -> SearchScreen(state, onOpenBook = { sheetBookId = it.id; sheet = "book" })
                "settings" -> SettingsScreen(
                    state = state,
                    dark = dark,
                    serif = serif,
                    voiceLabel = voiceShort,
                    onDarkChange = onDarkChange,
                    onSerifChange = onSerifChange,
                    onRateChange = viewModel::setSpeechRate,
                    onLanguageChange = { viewModel.updateReaderSettings(state.readerSettings.copy(language = it)) },
                    onManageModels = { sheet = "model" }
                )
                else -> LibraryScreen(
                    state = state,
                    voiceLabel = voiceShort,
                    onOpenBook = { sheetBookId = it.id; sheet = "book" },
                    onOpenNow = { overlay = "player" },
                    onToggle = viewModel::togglePlayback,
                    onImport = { overlay = "import" }
                )
            }
        }

        if (overlay == null) {
            Dock(
                state = state,
                voiceShort = voiceShort,
                tab = tab,
                onTab = { tab = it },
                onOpenNow = { overlay = "player" },
                onToggle = viewModel::togglePlayback,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ---- overlays ----
        when (overlay) {
            "player" -> PlayerOverlay(
                state = state,
                voiceShort = voiceShort,
                onClose = { overlay = null },
                onToggle = viewModel::togglePlayback,
                onJumpTo = viewModel::jumpTo,
                onJumpBy = viewModel::jumpBy,
                onOpenChapters = { overlay = "chapters" },
                onOpenSpeed = { sheet = "speed" },
                onOpenSleep = { sheet = "sleep" },
                onOpenVoice = { sheet = "model" },
                onOpenReader = { overlay = "reader" },
                onDownloadHint = { overlay = null; tab = "settings"; sheet = "model" }
            )
            "reader" -> ReaderOverlay(
                state = state,
                onClose = { overlay = null },
                onModeListen = { overlay = "player" },
                onJumpTo = viewModel::jumpTo,
                onJumpBy = viewModel::jumpBy,
                onToggle = viewModel::togglePlayback,
                onOpenSearch = { viewModel.updateSearch(""); overlay = "booksearch" },
                onAddAnnotation = { viewModel.addAnnotation(it) },
                onDeleteAnnotation = viewModel::deleteAnnotation,
                onExportAnnotations = viewModel::exportAnnotations,
                onReaderSettingsChange = viewModel::updateReaderSettings,
                onResetReaderSettings = viewModel::resetReaderSettings,
                onRepeatCountChange = viewModel::setRepeatCount,
                onMarkRepeatStart = viewModel::markRepeatStart,
                onMarkRepeatEnd = viewModel::markRepeatEnd,
                onClearRepeatRange = viewModel::clearRepeatRange,
                onStartSleepTimer = viewModel::startSleepTimer,
                onRateChange = viewModel::setSpeechRate
            )
            "booksearch" -> InBookSearchOverlay(
                state = state,
                onClose = { overlay = "reader" },
                onSearchChange = viewModel::updateSearch,
                onJump = viewModel::jumpTo
            )
            "chapters" -> state.selectedBook?.let { book ->
                ChaptersOverlay(
                    book = book,
                    currentSentenceIndex = state.currentSentenceIndex,
                    onClose = { overlay = "player" },
                    onPlayChapter = { sentenceIndex ->
                        viewModel.jumpTo(sentenceIndex)
                        overlay = "player"
                        if (state.modelInstalled && !state.isPlaying) viewModel.togglePlayback()
                    }
                )
            }
            "import" -> ImportOverlay(
                isBusy = state.isBusy,
                onClose = { overlay = null },
                onPickFile = { launchImport() },
                onPasteImport = { title, content ->
                    viewModel.importText(title, content)
                    overlay = null
                }
            )
        }

        // ---- sheets ----
        SpeedSheet(
            visible = sheet == "speed",
            onClose = { sheet = null },
            value = state.speechRate,
            onChange = viewModel::setSpeechRate
        )
        SleepSheet(
            visible = sheet == "sleep",
            onClose = { sheet = null },
            currentLabel = state.readerSettings.sleepTimerLabel,
            onPick = viewModel::startSleepTimer
        )
        ModelSheet(
            visible = sheet == "model",
            onClose = { sheet = null },
            state = state,
            onSelectModel = viewModel::selectModel,
            onDownloadModel = { downloadModel(it) },
            onDeleteModel = viewModel::deleteModel
        )
        BookSheet(
            visible = sheet == "book",
            book = sheetBook,
            voiceShort = voiceShort,
            onClose = { sheet = null },
            onPlay = {
                sheet = null
                sheetBook?.let {
                    viewModel.selectBook(it)
                    overlay = "player"
                    if (state.modelInstalled) viewModel.togglePlayback()
                }
            },
            onRead = {
                sheet = null
                sheetBook?.let { viewModel.selectBook(it); overlay = "reader" }
            },
            onChapters = {
                sheet = null
                sheetBook?.let { viewModel.selectBook(it); overlay = "chapters" }
            }
        )

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )
    }
}

@Composable
private fun Dock(
    state: AudioBookUiStateAlias,
    voiceShort: String,
    tab: String,
    onTab: (String) -> Unit,
    onOpenNow: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val book = state.selectedBook
    Column(modifier.fillMaxWidth()) {
        if (book != null) {
            Row(
                Modifier
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dim.rM))
                    .background(c.surface)
                    .clickable(onClick = onOpenNow)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BookCover(book, width = 42.dp, showText = false)
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = AppText.title.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    OnDeviceBadge("$voiceShort · 端侧")
                }
                PlayCircle(
                    playing = state.isPlaying,
                    onClick = onToggle,
                    diameter = 44.dp,
                    iconSize = 20.dp,
                    enabled = state.modelInstalled
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.bg2)
                .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TabItem(AppIcons.library, "书架", tab == "library", { onTab("library") }, Modifier.weight(1f))
            TabItem(AppIcons.search, "搜索", tab == "search", { onTab("search") }, Modifier.weight(1f))
            TabItem(AppIcons.settings, "设置", tab == "settings", { onTab("settings") }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        AppIcon(icon, size = 24.dp, tint = if (selected) c.accent else c.ink3)
        Text(
            label,
            style = AppText.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.accent else c.ink3)
        )
    }
}

private typealias AudioBookUiStateAlias = com.example.audiobooks.ui.AudioBookUiState
