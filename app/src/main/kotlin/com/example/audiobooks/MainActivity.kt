package com.example.audiobooks

import android.Manifest
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.ui.AudioBookUiState
import com.example.audiobooks.ui.AudioBookViewModel
import java.util.Locale
import kotlin.math.roundToInt

private enum class AppScreen { Library, Reader, Settings }

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
        currentScreen = AppScreen.Library
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentScreen) {
                            AppScreen.Reader -> state.selectedBook?.title ?: "Reader"
                            AppScreen.Settings -> "Settings"
                            AppScreen.Library -> "AudioBooks"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (currentScreen != AppScreen.Library) {
                        TextButton(onClick = { currentScreen = AppScreen.Library }) {
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
                    modifier = Modifier.fillMaxSize()
                )
                AppScreen.Settings -> SettingsPane(
                    state = state,
                    onRateChange = onRateChange,
                    onSelectModel = onSelectModel,
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
    modifier: Modifier = Modifier
) {
    val book = state.selectedBook
    Column(modifier.padding(20.dp)) {
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a book to start listening", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        Text(
            text = "${state.currentSentenceIndex + 1} / ${book.totalSentences}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val start = (state.currentSentenceIndex - 4).coerceAtLeast(0)
                val end = (state.currentSentenceIndex + 12).coerceAtMost(book.sentences.lastIndex)
                items((start..end).toList()) { index ->
                    val sentence = book.sentences[index]
                    val active = index == state.currentSentenceIndex
                    Text(
                        text = sentence.text,
                        style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (active) HorizontalDivider()
                }
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
}

@Composable
private fun SettingsPane(
    state: AudioBookUiState,
    onRateChange: (Float) -> Unit,
    onSelectModel: (String) -> Unit,
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
                            "${model.runtime} / ${if (installed) "installed" else "not installed"}",
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
                        text = "This model is listed from the research report, but its mobile runtime is not integrated yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.modelInstalled -> {
                    Text(
                        text = "Ready for offline playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
