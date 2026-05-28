package com.example.audiobooks.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.storage.BookRepository
import com.example.audiobooks.storage.ModelRepository
import com.example.audiobooks.storage.TtsModelSpec
import com.example.audiobooks.tts.AndroidTtsPlayer
import com.example.audiobooks.tts.PlaybackEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ReaderSettings(
    val fontScale: Float = 1.0f,
    val lineSpacing: Float = 1.35f,
    val background: String = "system",
    val language: String = "en",
    val gesturesEnabled: Boolean = true,
    val repeatCount: Int = 1,
    val abStart: Int? = null,
    val abEnd: Int? = null,
    val sleepTimerLabel: String? = null,
    val sleepTimerEndsAt: Long? = null
)

data class AudioBookUiState(
    val books: List<AudioBook> = emptyList(),
    val selectedBook: AudioBook? = null,
    val readerHtmlUrl: String? = null,
    val currentSentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val speechRate: Float = 1.0f,
    val models: List<TtsModelSpec> = emptyList(),
    val selectedModelId: String = "",
    val downloadedModelIds: Set<String> = emptySet(),
    val installedModelIds: Set<String> = emptySet(),
    val modelInstalled: Boolean = false,
    val modelDownloadUrl: String = "",
    val dataRootPath: String = "",
    val modelDownloadProgressById: Map<String, Float> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<Int> = emptyList(),
    val readerSettings: ReaderSettings = ReaderSettings(),
    val isBusy: Boolean = false,
    val message: String? = null
)

class AudioBookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BookRepository(application)
    private val modelRepository = ModelRepository(application)
    private val player = AndroidTtsPlayer(application)
    private val _state = MutableStateFlow(AudioBookUiState())
    private var sleepTimerJob: Job? = null
    private var repeatRemaining = 0
    val state: StateFlow<AudioBookUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshBooks()
            refreshModelState()
        }
        viewModelScope.launch {
            player.events.collect { event ->
                when (event) {
                    is PlaybackEvent.SentenceStarted -> {
                        _state.update { it.copy(currentSentenceIndex = event.index, isPlaying = true) }
                        saveProgress(event.index)
                    }
                    is PlaybackEvent.SentenceDone -> {
                        val selected = _state.value.selectedBook
                        val settings = _state.value.readerSettings
                        val chapterEnd = selected?.chapters
                            ?.firstOrNull { event.index in it.startSentenceIndex..it.endSentenceIndex }
                            ?.endSentenceIndex
                        when {
                            selected == null -> _state.update { it.copy(isPlaying = false) }
                            repeatRemaining > 0 -> {
                                repeatRemaining -= 1
                                player.play(selected.sentences, event.index, _state.value.speechRate)
                            }
                            settings.abStart != null && settings.abEnd != null && event.index >= settings.abEnd -> {
                                repeatRemaining = (settings.repeatCount - 1).coerceAtLeast(0)
                                player.play(selected.sentences, settings.abStart, _state.value.speechRate)
                            }
                            settings.sleepTimerLabel == "chapter" && chapterEnd != null && event.index >= chapterEnd -> {
                                player.stop()
                                _state.update {
                                    it.copy(
                                        isPlaying = false,
                                        readerSettings = it.readerSettings.copy(sleepTimerLabel = null, sleepTimerEndsAt = null),
                                        message = "Stopped at chapter end."
                                    )
                                }
                            }
                            event.index >= selected.sentences.lastIndex -> {
                            _state.update { it.copy(isPlaying = false) }
                            }
                        }
                    }
                    is PlaybackEvent.Error -> _state.update { it.copy(message = event.message) }
                }
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { repository.importBook(uri) }
                .onSuccess { book ->
                    refreshBooks(book.id)
                    _state.update { it.copy(message = "Imported: ${book.title}") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "Import failed.") }
                }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun selectBook(book: AudioBook) {
        player.stop()
        _state.update {
            it.copy(
                selectedBook = book,
                readerHtmlUrl = repository.readerHtmlUrl(book),
                currentSentenceIndex = book.currentSentenceIndex.coerceInBook(book),
                isPlaying = false,
                message = null
            )
        }
    }

    fun togglePlayback() {
        val snapshot = _state.value
        val book = snapshot.selectedBook ?: return
        if (snapshot.isPlaying) {
            player.stop()
            _state.update { it.copy(isPlaying = false) }
            saveProgress(snapshot.currentSentenceIndex)
        } else {
            repeatRemaining = (snapshot.readerSettings.repeatCount - 1).coerceAtLeast(0)
            player.play(book.sentences, snapshot.currentSentenceIndex, snapshot.speechRate)
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun jumpBy(delta: Int) {
        val book = _state.value.selectedBook ?: return
        val next = (_state.value.currentSentenceIndex + delta).coerceInBook(book)
        player.stop()
        _state.update { it.copy(currentSentenceIndex = next, isPlaying = false) }
        saveProgress(next)
    }

    fun jumpTo(sentenceIndex: Int) {
        val book = _state.value.selectedBook ?: return
        val next = sentenceIndex.coerceInBook(book)
        player.stop()
        _state.update { it.copy(currentSentenceIndex = next, isPlaying = false) }
        saveProgress(next)
    }

    fun addAnnotation(sentenceIndex: Int, color: String = "yellow", note: String = "") {
        val book = _state.value.selectedBook ?: return
        viewModelScope.launch {
            repository.addAnnotation(book.id, sentenceIndex, color, note)
            refreshBooks(book.id)
            _state.update { it.copy(message = "Bookmark saved.") }
        }
    }

    fun deleteAnnotation(annotationId: String) {
        val book = _state.value.selectedBook ?: return
        viewModelScope.launch {
            repository.deleteAnnotation(book.id, annotationId)
            refreshBooks(book.id)
        }
    }

    fun exportAnnotations() {
        val book = _state.value.selectedBook ?: return
        viewModelScope.launch {
            val file = repository.exportAnnotationsMarkdown(book.id)
            _state.update {
                it.copy(message = if (file != null) "Annotations exported." else "No annotations exported.")
            }
        }
    }

    fun updateSearch(query: String) {
        val book = _state.value.selectedBook
        val results = if (query.isBlank() || book == null) {
            emptyList()
        } else {
            book.sentences.asSequence()
                .filter { it.text.contains(query, ignoreCase = true) }
                .map { it.index }
                .take(100)
                .toList()
        }
        _state.update { it.copy(searchQuery = query, searchResults = results) }
    }

    fun updateReaderSettings(settings: ReaderSettings) {
        _state.update { it.copy(readerSettings = settings) }
    }

    fun resetReaderSettings() {
        _state.update { it.copy(readerSettings = ReaderSettings(language = it.readerSettings.language)) }
    }

    fun setRepeatCount(count: Int) {
        _state.update {
            it.copy(readerSettings = it.readerSettings.copy(repeatCount = count.coerceIn(1, 9)))
        }
    }

    fun markRepeatStart() {
        _state.update {
            it.copy(readerSettings = it.readerSettings.copy(abStart = it.currentSentenceIndex))
        }
    }

    fun markRepeatEnd() {
        _state.update {
            val start = it.readerSettings.abStart
            val end = it.currentSentenceIndex
            it.copy(readerSettings = it.readerSettings.copy(abEnd = if (start == null) null else end.coerceAtLeast(start)))
        }
    }

    fun clearRepeatRange() {
        _state.update { it.copy(readerSettings = it.readerSettings.copy(abStart = null, abEnd = null)) }
    }

    fun startSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null) {
            _state.update { it.copy(readerSettings = it.readerSettings.copy(sleepTimerLabel = null, sleepTimerEndsAt = null)) }
            return
        }
        if (minutes < 0) {
            _state.update {
                it.copy(readerSettings = it.readerSettings.copy(sleepTimerLabel = "chapter", sleepTimerEndsAt = null))
            }
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update {
            it.copy(readerSettings = it.readerSettings.copy(sleepTimerLabel = "${minutes}m", sleepTimerEndsAt = endAt))
        }
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            player.stop()
            _state.update {
                it.copy(
                    isPlaying = false,
                    readerSettings = it.readerSettings.copy(sleepTimerLabel = null, sleepTimerEndsAt = null),
                    message = "Sleep timer stopped playback."
                )
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        val rounded = ((rate.coerceIn(0.5f, 3.0f) * 100).roundToInt() / 100f).coerceIn(0.5f, 3.0f)
        player.setRate(rounded)
        _state.update { it.copy(speechRate = rounded) }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    modelDownloadProgressById = it.modelDownloadProgressById + (modelId to 0f),
                    message = null
                )
            }
            runCatching {
                modelRepository.downloadAndInstall(modelId) { progress ->
                    _state.update {
                        it.copy(modelDownloadProgressById = it.modelDownloadProgressById + (modelId to progress))
                    }
                }
            }.onSuccess {
                refreshModelState()
                _state.update { it.copy(message = "Voice model downloaded.") }
            }.onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Model download failed.") }
            }
            _state.update { state ->
                state.copy(modelDownloadProgressById = state.modelDownloadProgressById - modelId)
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
            refreshModelState()
            _state.update { it.copy(message = "Model package deleted.") }
        }
    }

    fun selectModel(modelId: String) {
        modelRepository.selectModel(modelId)
        refreshModelState()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        player.shutdown()
        super.onCleared()
    }

    private suspend fun refreshBooks(selectId: String? = null) {
        val books = repository.loadBooks()
        val selected = selectId?.let { id -> books.firstOrNull { it.id == id } }
            ?: _state.value.selectedBook?.let { current -> books.firstOrNull { it.id == current.id } }
            ?: books.firstOrNull()
        _state.update {
            it.copy(
                books = books,
                selectedBook = selected,
                readerHtmlUrl = selected?.let { book -> repository.readerHtmlUrl(book) },
                currentSentenceIndex = selected?.currentSentenceIndex?.coerceInBook(selected) ?: 0
            )
        }
    }

    private fun refreshModelState() {
        val model = modelRepository.state()
        _state.update {
            it.copy(
                models = model.models,
                selectedModelId = model.selected.id,
                downloadedModelIds = model.downloadedIds,
                installedModelIds = model.installedIds,
                modelInstalled = model.installed,
                dataRootPath = model.dataRoot.path,
                modelDownloadUrl = model.downloadUrl
            )
        }
    }

    private fun saveProgress(sentenceIndex: Int) {
        val book = _state.value.selectedBook ?: return
        viewModelScope.launch {
            repository.saveProgress(book.id, sentenceIndex)
        }
    }

    private fun Int.coerceInBook(book: AudioBook): Int =
        if (book.sentences.isEmpty()) 0 else coerceIn(0, book.sentences.lastIndex)
}

