package com.example.audiobooks

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioBooksTheme {
                val viewModel: AudioBookViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                AudioBooksScreen(
                    state = state,
                    onImport = viewModel::importEpub,
                    onSelectBook = viewModel::selectBook,
                    onTogglePlayback = viewModel::togglePlayback,
                    onJump = viewModel::jumpBy,
                    onRateChange = viewModel::setSpeechRate,
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
    onDownloadModel: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AudioBooks", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    TextButton(
                        enabled = !state.isBusy,
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/epub+zip", "application/octet-stream", "*/*")
                            )
                        }
                    ) {
                        Text("Import EPUB")
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (maxWidth < 720.dp) {
                Column(Modifier.fillMaxSize()) {
                    LibraryPane(
                        books = state.books,
                        selectedId = state.selectedBook?.id,
                        isBusy = state.isBusy,
                        onSelectBook = onSelectBook,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.34f)
                    )
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    ReaderPane(
                        state = state,
                        onTogglePlayback = onTogglePlayback,
                        onJump = onJump,
                        onRateChange = onRateChange,
                        onDownloadModel = onDownloadModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.66f)
                    )
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    LibraryPane(
                        books = state.books,
                        selectedId = state.selectedBook?.id,
                        isBusy = state.isBusy,
                        onSelectBook = onSelectBook,
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    ReaderPane(
                        state = state,
                        onTogglePlayback = onTogglePlayback,
                        onJump = onJump,
                        onRateChange = onRateChange,
                        onDownloadModel = onDownloadModel,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxSize()
                    )
                }
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
            text = "Use Import EPUB to add a book. The first version supports EPUB only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookRow(book: AudioBook, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
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
            text = listOfNotNull(book.author, "${book.totalSentences} sentences").joinToString(" / "),
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
    onRateChange: (Float) -> Unit,
    onDownloadModel: () -> Unit,
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
            text = book.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        book.author?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))

        ModelStatusPanel(
            state = state,
            onDownloadModel = onDownloadModel
        )
        Spacer(Modifier.height(16.dp))

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
                enabled = state.modelInstalled && state.modelDownloadProgress == null
            ) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            Button(
                onClick = { onJump(1) },
                enabled = state.currentSentenceIndex < book.sentences.lastIndex,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Next")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Speed ${(state.speechRate * 10).roundToInt() / 10.0}x")
        Slider(
            value = state.speechRate,
            onValueChange = onRateChange,
            valueRange = 0.5f..3.0f,
            steps = 4
        )
    }
}

@Composable
private fun ModelStatusPanel(
    state: AudioBookUiState,
    onDownloadModel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (state.modelInstalled) Color(0xFFE6F2EC) else Color(0xFFFFF4DD),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (state.modelInstalled) "Voice model ready" else "Voice model required",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!state.modelInstalled) {
                Text(
                    text = "Download the public Sherpa model before playback. Books and progress stay local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.modelDownloadProgress != null) {
                    Text(
                        text = "Downloading ${(state.modelDownloadProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Button(
                        onClick = onDownloadModel,
                        enabled = !state.isBusy
                    ) {
                        Text("Download model")
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioBooksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF1F5E5A),
            secondary = Color(0xFF6E5B46),
            background = Color(0xFFFAFAF8),
            surface = Color(0xFFFAFAF8),
            surfaceVariant = Color(0xFFF0ECE4),
            secondaryContainer = Color(0xFFE4DED3)
        ),
        content = content
    )
}
