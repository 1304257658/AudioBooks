package com.example.audiobooks

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.ui.AudioBookUiState
import com.example.audiobooks.ui.AudioBookViewModel
import com.example.audiobooks.ui.ReaderSettings
import java.util.Locale
import kotlin.math.roundToInt

private enum class AppScreen { Library, Reader, Chapters, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioBooksTheme {
                val viewModel: AudioBookViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                AudioBooksScreen(
                    state = state,
                    onImport = viewModel::importBook,
                    onSelectBook = viewModel::selectBook,
                    onTogglePlayback = viewModel::togglePlayback,
                    onJump = viewModel::jumpBy,
                    onRateChange = viewModel::setSpeechRate,
                    onSelectModel = viewModel::selectModel,
                    onDownloadModel = viewModel::downloadModel,
                    onDeleteModel = viewModel::deleteSelectedModel,
                    onAddAnnotation = viewModel::addAnnotation,
                    onDeleteAnnotation = viewModel::deleteAnnotation,
                    onExportAnnotations = viewModel::exportAnnotations,
                    onSearchChange = viewModel::updateSearch,
                    onJumpTo = viewModel::jumpTo,
                    onReaderSettingsChange = viewModel::updateReaderSettings,
                    onRepeatCountChange = viewModel::setRepeatCount,
                    onMarkRepeatStart = viewModel::markRepeatStart,
                    onMarkRepeatEnd = viewModel::markRepeatEnd,
                    onClearRepeatRange = viewModel::clearRepeatRange,
                    onStartSleepTimer = viewModel::startSleepTimer,
                    onMessageShown = viewModel::clearMessage
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioBooksScreen(
    state: AudioBookUiState,
    onImport: (Uri) -> Unit,
    onSelectBook: (AudioBook) -> Unit,
    onTogglePlayback: () -> Unit,
    onJump: (Int) -> Unit,
    onRateChange: (Float) -> Unit,
    onSelectModel: (String) -> Unit,
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onAddAnnotation: (Int, String, String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: () -> Unit,
    onSearchChange: (String) -> Unit,
    onJumpTo: (Int) -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onMarkRepeatStart: () -> Unit,
    onMarkRepeatEnd: () -> Unit,
    onClearRepeatRange: () -> Unit,
    onStartSleepTimer: (Int?) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by remember { mutableStateOf(AppScreen.Library) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    BackHandler(enabled = currentScreen != AppScreen.Library) {
        currentScreen = if (currentScreen == AppScreen.Chapters) AppScreen.Reader else AppScreen.Library
    }
    FullScreenEffect(enabled = currentScreen == AppScreen.Reader)

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.Reader) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                AppScreen.Settings -> "Settings"
                                AppScreen.Chapters -> "Chapters"
                                else -> "AudioBooks"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (currentScreen != AppScreen.Library) {
                            TextButton(
                                onClick = {
                                    currentScreen = if (currentScreen == AppScreen.Chapters) AppScreen.Reader else AppScreen.Library
                                }
                            ) {
                                Text("Back")
                            }
                        }
                    },
                    actions = {
                        if (currentScreen != AppScreen.Settings) {
                            TextButton(onClick = { currentScreen = AppScreen.Settings }) {
                                Text("Settings")
                            }
                        }
                        if (currentScreen == AppScreen.Library) {
                            TextButton(
                                enabled = !state.isBusy,
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf(
                                            "application/epub+zip",
                                            "application/pdf",
                                            "text/plain",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                }
                            ) {
                                Text("Import")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                AppScreen.Library -> LibraryPane(
                    books = state.books,
                    selectedId = state.selectedBook?.id,
                    isBusy = state.isBusy,
                    onSelectBook = { book ->
                        onSelectBook(book)
                        currentScreen = AppScreen.Reader
                    },
                    modifier = Modifier.fillMaxSize()
                )
                AppScreen.Reader -> ReaderPane(
                    state = state,
                    onTogglePlayback = onTogglePlayback,
                    onJump = onJump,
                    onOpenChapters = { currentScreen = AppScreen.Chapters },
                    onAddAnnotation = onAddAnnotation,
                    onSearchChange = onSearchChange,
                    onJumpTo = onJumpTo,
                    onDeleteAnnotation = onDeleteAnnotation,
                    onExportAnnotations = onExportAnnotations,
                    onReaderSettingsChange = onReaderSettingsChange,
                    onRepeatCountChange = onRepeatCountChange,
                    onMarkRepeatStart = onMarkRepeatStart,
                    onMarkRepeatEnd = onMarkRepeatEnd,
                    onClearRepeatRange = onClearRepeatRange,
                    onStartSleepTimer = onStartSleepTimer,
                    modifier = Modifier.fillMaxSize()
                )
                AppScreen.Chapters -> ChaptersPane(
                    state = state,
                    onJumpTo = { index ->
                        onJumpTo(index)
                        currentScreen = AppScreen.Reader
                    },
                    modifier = Modifier.fillMaxSize()
                )
                AppScreen.Settings -> SettingsPane(
                    state = state,
                    onRateChange = onRateChange,
                    onSelectModel = onSelectModel,
                    onDeleteModel = onDeleteModel,
                    onDownloadModel = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onDownloadModel()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LibraryPane(
    books: List<AudioBook>,
    selectedId: String?,
    isBusy: Boolean,
    onSelectBook: (AudioBook) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (isBusy) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (books.isEmpty()) {
            EmptyLibrary()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        selected = book.id == selectedId,
                        onClick = { onSelectBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("No books yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Import EPUB, TXT, or text PDF files. Books and reading history stay local.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookRow(book: AudioBook, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val progress = if (book.totalSentences == 0) {
        0
    } else {
        ((book.currentSentenceIndex + 1).toFloat() / book.totalSentences * 100).roundToInt()
    }.coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOfNotNull(book.author, "${book.totalSentences} sentences", "Progress $progress%")
                .joinToString(" / "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReaderPane(
    state: AudioBookUiState,
    onTogglePlayback: () -> Unit,
    onJump: (Int) -> Unit,
    onOpenChapters: () -> Unit,
    onAddAnnotation: (Int, String, String) -> Unit,
    onSearchChange: (String) -> Unit,
    onJumpTo: (Int) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: () -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onMarkRepeatStart: () -> Unit,
    onMarkRepeatEnd: () -> Unit,
    onClearRepeatRange: () -> Unit,
    onStartSleepTimer: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val book = state.selectedBook
    var showReaderTools by remember { mutableStateOf(false) }
    Box(modifier) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a book to start listening", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.currentSentenceIndex + 1} / ${book.totalSentences}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenChapters) {
                Text("Chapters")
            }
            TextButton(onClick = { showReaderTools = !showReaderTools }) {
                Text("Reader")
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (state.readerHtmlUrl != null) {
                HtmlReader(url = state.readerHtmlUrl, modifier = Modifier.fillMaxSize())
            } else {
                TextReader(
                    state = state,
                    onTogglePlayback = onTogglePlayback,
                    onJump = onJump,
                    onAddAnnotation = onAddAnnotation,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onJump(-1) },
                enabled = state.currentSentenceIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Prev")
            }
            Button(
                onClick = onTogglePlayback,
                modifier = Modifier.weight(1f),
                enabled = state.modelInstalled && state.modelDownloadProgress == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isPlaying) Color(0xFF9B3A35) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (state.isPlaying) "Stop" else "Start")
            }
            Button(
                onClick = { onJump(1) },
                enabled = state.currentSentenceIndex < book.sentences.lastIndex,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Next")
            }
        }
    }
        if (showReaderTools && book != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp
            ) {
                LazyColumn(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Reader tools",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showReaderTools = false }) {
                                Text("Close")
                            }
                        }
                    }
                    item { SearchPanel(state, onSearchChange, onJumpTo) }
                    item { AnnotationPanel(state, onJumpTo, onDeleteAnnotation, onExportAnnotations) }
                    item { SleepTimerPanel(state, onStartSleepTimer) }
                    item {
                        RepeatPanel(
                            state = state,
                            onRepeatCountChange = onRepeatCountChange,
                            onMarkRepeatStart = onMarkRepeatStart,
                            onMarkRepeatEnd = onMarkRepeatEnd,
                            onClearRepeatRange = onClearRepeatRange
                        )
                    }
                    item { ReaderStylePanel(state.readerSettings, onReaderSettingsChange) }
                }
            }
        }
    }
}

@Composable
private fun ChaptersPane(
    state: AudioBookUiState,
    onJumpTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val book = state.selectedBook
    Column(modifier.padding(16.dp)) {
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a book first", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(book.chapters, key = { it.index }) { chapter ->
                val active = state.currentSentenceIndex in chapter.startSentenceIndex..chapter.endSentenceIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJumpTo(chapter.startSentenceIndex) },
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sentences ${chapter.startSentenceIndex + 1}-${chapter.endSentenceIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPane(
    state: AudioBookUiState,
    onRateChange: (Float) -> Unit,
    onSelectModel: (String) -> Unit,
    onDeleteModel: () -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            ModelStatusPanel(
                state = state,
                onSelectModel = onSelectModel,
                onDeleteModel = onDeleteModel,
                onDownloadModel = onDownloadModel
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Playback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Speed ${String.format(Locale.US, "%.2f", state.speechRate)}x")
                    Slider(
                        value = state.speechRate,
                        onValueChange = onRateChange,
                        valueRange = 0.5f..3.0f,
                        steps = 249
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusPanel(
    state: AudioBookUiState,
    onSelectModel: (String) -> Unit,
    onDeleteModel: () -> Unit,
    onDownloadModel: () -> Unit
) {
    val selectedModel = state.models.firstOrNull { it.id == state.selectedModelId }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Voice model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            state.models.forEach { model ->
                val selected = model.id == state.selectedModelId
                val downloaded = model.id in state.downloadedModelIds
                val installed = model.id in state.installedModelIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectModel(model.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(model.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        Text(
                            "${model.runtime} / ${if (downloaded) "downloaded" else if (installed) "installed" else "not downloaded"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            model.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium)
                }
            }

            when {
                state.modelDownloadProgress != null -> {
                    Text("Downloading ${(state.modelDownloadProgress * 100).roundToInt()}%")
                }
                selectedModel?.availableInApp == true && !state.modelInstalled -> {
                    Text(
                        text = "Downloads continue in app storage and report progress in the status bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onDownloadModel, enabled = !state.isBusy) {
                        Text("Download model")
                    }
                }
                selectedModel?.availableInApp == false -> {
                    Text(
                        text = "The package can be downloaded automatically. Runtime integration is still pending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onDownloadModel,
                        enabled = !state.isBusy && selectedModel.id !in state.downloadedModelIds
                    ) {
                        Text(if (selectedModel.id in state.downloadedModelIds) "Downloaded" else "Download model")
                    }
                }
                state.modelInstalled -> {
                    Text(
                        text = "Ready for offline playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selectedModel != null) {
                TextButton(onClick = onDeleteModel, enabled = !state.isBusy) {
                    Text("Delete selected package")
                }
            }
        }
    }
}

@Composable
private fun TextReader(
    state: AudioBookUiState,
    onAddAnnotation: (Int, String, String) -> Unit,
    onTogglePlayback: () -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val book = state.selectedBook ?: return
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(book.id, state.currentSentenceIndex) {
        listState.animateScrollToItem(state.currentSentenceIndex.coerceIn(0, book.sentences.lastIndex))
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(book.sentences, key = { it.index }) { sentence ->
            val index = sentence.index
            val active = index == state.currentSentenceIndex
            val annotated = book.annotations.any { it.sentenceIndex == index }
            Text(
                text = sentence.text,
                style = (if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge).let {
                    it.copy(
                        fontSize = it.fontSize.scaleBy(state.readerSettings.fontScale),
                        lineHeight = it.lineHeight.scaleBy(state.readerSettings.lineSpacing)
                    )
                },
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (annotated) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .combinedClickable(
                        onClick = { if (state.readerSettings.gesturesEnabled) onTogglePlayback() },
                        onDoubleClick = { if (state.readerSettings.gesturesEnabled) onJump(1) },
                        onLongClick = { onAddAnnotation(index, "yellow", "") }
                    )
                    .padding(4.dp)
            )
            if (active) HorizontalDivider()
        }
    }
}

@Composable
private fun SearchPanel(
    state: AudioBookUiState,
    onSearchChange: (String) -> Unit,
    onJumpTo: (Int) -> Unit
) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Search", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Keyword") }
            )
            state.searchResults.take(8).forEach { index ->
                Text(
                    text = "${index + 1}. ${state.selectedBook?.sentences?.getOrNull(index)?.text.orEmpty()}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJumpTo(index) }
                        .padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun AnnotationPanel(
    state: AudioBookUiState,
    onJumpTo: (Int) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: () -> Unit
) {
    val book = state.selectedBook
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bookmarks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (book == null || book.annotations.isEmpty()) {
                Text("Long press a sentence in text mode to bookmark it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                book.annotations.take(8).forEach { annotation ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${annotation.sentenceIndex + 1}. ${book.sentences.getOrNull(annotation.sentenceIndex)?.text.orEmpty()}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onJumpTo(annotation.sentenceIndex) }
                        )
                        TextButton(onClick = { onDeleteAnnotation(annotation.id) }) {
                            Text("Delete")
                        }
                    }
                }
                Button(onClick = onExportAnnotations) {
                    Text("Export Markdown")
                }
            }
        }
    }
}

@Composable
private fun SleepTimerPanel(state: AudioBookUiState, onStartSleepTimer: (Int?) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(state.readerSettings.sleepTimerLabel?.let { "Active: $it" } ?: "Off")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    Button(onClick = { onStartSleepTimer(minutes) }) {
                        Text("${minutes}m")
                    }
                }
            }
            Button(onClick = { onStartSleepTimer(-1) }) {
                Text("Chapter end")
            }
            TextButton(onClick = { onStartSleepTimer(null) }) {
                Text("Cancel timer")
            }
        }
    }
}

private fun TextUnit.scaleBy(value: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * value

@Composable
private fun RepeatPanel(
    state: AudioBookUiState,
    onRepeatCountChange: (Int) -> Unit,
    onMarkRepeatStart: () -> Unit,
    onMarkRepeatEnd: () -> Unit,
    onClearRepeatRange: () -> Unit
) {
    val settings = state.readerSettings
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Repeat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Current sentence repeat: ${settings.repeatCount}x")
            Slider(
                value = settings.repeatCount.toFloat(),
                onValueChange = { onRepeatCountChange(it.roundToInt()) },
                valueRange = 1f..9f,
                steps = 7
            )
            Text("A-B: ${settings.abStart?.plus(1) ?: "-"} to ${settings.abEnd?.plus(1) ?: "-"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMarkRepeatStart) { Text("Set A") }
                Button(onClick = onMarkRepeatEnd) { Text("Set B") }
                TextButton(onClick = onClearRepeatRange) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ReaderStylePanel(settings: ReaderSettings, onReaderSettingsChange: (ReaderSettings) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reader view", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Font ${String.format(Locale.US, "%.2f", settings.fontScale)}x")
            Slider(
                value = settings.fontScale,
                onValueChange = { onReaderSettingsChange(settings.copy(fontScale = it)) },
                valueRange = 0.8f..1.6f,
                steps = 7
            )
            Text("Line spacing ${String.format(Locale.US, "%.2f", settings.lineSpacing)}")
            Slider(
                value = settings.lineSpacing,
                onValueChange = { onReaderSettingsChange(settings.copy(lineSpacing = it)) },
                valueRange = 1.0f..2.0f,
                steps = 9
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onReaderSettingsChange(settings.copy(background = "system")) }) { Text("System") }
                TextButton(onClick = { onReaderSettingsChange(settings.copy(background = "paper")) }) { Text("Paper") }
                TextButton(onClick = { onReaderSettingsChange(settings.copy(background = "black")) }) { Text("Black") }
            }
            TextButton(onClick = { onReaderSettingsChange(settings.copy(gesturesEnabled = !settings.gesturesEnabled)) }) {
                Text(if (settings.gesturesEnabled) "Gestures on" else "Gestures off")
            }
        }
    }
}

@Composable
private fun HtmlReader(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        }
    )
}

@Composable
private fun FullScreenEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (window != null && enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
        onDispose {
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }
}

@Composable
private fun AudioBooksTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8ACBC3),
            secondary = Color(0xFFD0BFA8),
            background = Color(0xFF121412),
            surface = Color(0xFF121412),
            surfaceVariant = Color(0xFF242620),
            secondaryContainer = Color(0xFF3A3329)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1F5E5A),
            secondary = Color(0xFF6E5B46),
            background = Color(0xFFFAFAF8),
            surface = Color(0xFFFAFAF8),
            surfaceVariant = Color(0xFFF0ECE4),
            secondaryContainer = Color(0xFFE4DED3)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
