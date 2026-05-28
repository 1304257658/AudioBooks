package com.example.audiobooks.tts

sealed interface PlaybackEvent {
    data class SentenceStarted(val index: Int) : PlaybackEvent
    data class SentenceDone(val index: Int) : PlaybackEvent
    data class Error(val message: String) : PlaybackEvent
}


