package com.example.audiobooks.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.storage.BookRepository
import com.example.audiobooks.storage.ModelRepository
import com.example.audiobooks.storage.TtsModelSpec
import com.example.audiobooks.tts.PlaybackEvent
import com.example.audiobooks.tts.SherpaTtsPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AudioBookUiState(
    val books: List<AudioBook> = emptyList(),
    val selectedBook: AudioBook? = null,
    val currentSentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val speechRate: Float = 1.0f,
    val models: List<TtsModelSpec> = emptyList(),
    val selectedModelId: String = "",
    val installedModelIds: Set<String> = emptySet(),
    val modelInstalled: Boolean = false,
    val modelDownloadUrl: String = "",
    val modelDownloadProgress: Float? = null,
    val isBusy: Boolean = false,
    val message: String? = null
)

class AudioBookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BookRepository(application)
    private val modelRepository = ModelRepository(application)
    private val player = SherpaTtsPlayer(application)
    private val _state = MutableStateFlow(AudioBookUiState())
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
                        if (selected != null && event.index >= selected.sentences.lastIndex) {
                            _state.update { it.copy(isPlaying = false) }
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
                currentSentenceIndex = book.currentSentenceIndex.coerceInBook(book),
                isPlaying = false,
                message = null
            )
        }
    }

    fun togglePlayback() {
        val snapshot = _state.value
        val book = snapshot.selectedBook ?: return
        val selectedModel = snapshot.models.firstOrNull { it.id == snapshot.selectedModelId }
        if (selectedModel?.availableInApp == false) {
            _state.update { it.copy(message = "${selectedModel.name} runtime is not integrated yet.") }
            return
        }
        if (!snapshot.modelInstalled) {
            _state.update { it.copy(message = "Voice model is not installed. Download it first.") }
            return
        }
        if (snapshot.isPlaying) {
            player.stop()
            _state.update { it.copy(isPlaying = false) }
            saveProgress(snapshot.currentSentenceIndex)
        } else {
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

    fun setSpeechRate(rate: Float) {
        val rounded = ((rate.coerceIn(0.5f, 3.0f) * 100).roundToInt() / 100f).coerceIn(0.5f, 3.0f)
        player.setRate(rounded)
        _state.update { it.copy(speechRate = rounded) }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, modelDownloadProgress = 0f, message = null) }
            runCatching {
                modelRepository.downloadAndInstall { progress ->
                    _state.update { it.copy(modelDownloadProgress = progress) }
                }
            }.onSuccess {
                refreshModelState()
                _state.update { it.copy(message = "Voice model installed.") }
            }.onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Model download failed.") }
            }
            _state.update { it.copy(isBusy = false, modelDownloadProgress = null) }
        }
    }

    fun selectModel(modelId: String) {
        player.clearEngine()
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
                installedModelIds = model.installedIds,
                modelInstalled = model.installed,
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

