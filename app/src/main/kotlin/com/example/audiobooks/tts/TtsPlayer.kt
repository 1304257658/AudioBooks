package com.example.audiobooks.tts

import com.example.audiobooks.model.BookSentence
import kotlinx.coroutines.flow.SharedFlow

interface TtsPlayer {
    val events: SharedFlow<PlaybackEvent>

    fun play(sentences: List<BookSentence>, startIndex: Int, speechRate: Float)
    fun stop()
    fun setRate(value: Float)
    fun shutdown()
}


