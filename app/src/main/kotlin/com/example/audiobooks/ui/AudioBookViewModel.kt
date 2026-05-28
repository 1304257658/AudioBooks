package com.example.audiobooks.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.storage.BookRepository
import com.example.audiobooks.tts.PlaybackEvent
import com.example.audiobooks.tts.SherpaTtsPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioBookUiState(
    val books: List<AudioBook> = emptyList(),
    val selectedBook: AudioBook? = null,
    val currentSentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val speechRate: Float = 1.0f,
    val isBusy: Boolean = false,
    val message: String? = null
)

class AudioBookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BookRepository(application)
    private val player = SherpaTtsPlayer(application)
    private val _state = MutableStateFlow(AudioBookUiState())
    val state: StateFlow<AudioBookUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshBooks()
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

    fun importEpub(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { repository.importEpub(uri) }
                .onSuccess { book ->
                    refreshBooks(book.id)
                    _state.update { it.copy(message = "宸插鍏? ${book.title}") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "瀵煎叆澶辫触") }
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
        player.setRate(rate)
        _state.update { it.copy(speechRate = rate.coerceIn(0.5f, 3.0f)) }
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

    private fun saveProgress(sentenceIndex: Int) {
        val book = _state.value.selectedBook ?: return
        viewModelScope.launch {
            repository.saveProgress(book.id, sentenceIndex)
        }
    }

    private fun Int.coerceInBook(book: AudioBook): Int =
        if (book.sentences.isEmpty()) 0 else coerceIn(0, book.sentences.lastIndex)
}

