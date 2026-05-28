package com.example.audiobooks.tts

import com.example.audiobooks.model.BookSentence
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class UnavailableTtsPlayer(private val reason: String) : TtsPlayer {
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 4)
    override val events: SharedFlow<PlaybackEvent> = _events

    override fun play(sentences: List<BookSentence>, startIndex: Int, speechRate: Float) {
        _events.tryEmit(PlaybackEvent.Error(reason))
    }

    override fun stop() = Unit

    override fun setRate(value: Float) = Unit

    override fun shutdown() = Unit
}
