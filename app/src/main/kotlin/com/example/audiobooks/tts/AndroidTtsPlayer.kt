package com.example.audiobooks.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.audiobooks.model.BookSentence
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class AndroidTtsPlayer(context: Context) : TextToSpeech.OnInitListener, TtsPlayer {
    private val appContext = context.applicationContext
    private val ready = AtomicBoolean(false)
    private val stopped = AtomicBoolean(true)
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var queue: List<BookSentence> = emptyList()
    private var rate = 1.0f

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlaybackEvent> = _events

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready.set(true)
            tts?.language = Locale.CHINA
            tts?.setSpeechRate(rate)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    utteranceId.toIntOrNull()?.let { _events.tryEmit(PlaybackEvent.SentenceStarted(it)) }
                }

                override fun onDone(utteranceId: String) {
                    val index = utteranceId.toIntOrNull() ?: return
                    _events.tryEmit(PlaybackEvent.SentenceDone(index))
                    if (!stopped.get()) speakNext(index + 1)
                }

                @Deprecated("Deprecated in Android TTS API")
                override fun onError(utteranceId: String) {
                    _events.tryEmit(PlaybackEvent.Error("TTS playback failed: $utteranceId"))
                    if (!stopped.get()) utteranceId.toIntOrNull()?.let { speakNext(it + 1) }
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    _events.tryEmit(PlaybackEvent.Error("TTS playback failed: $utteranceId ($errorCode)"))
                    if (!stopped.get()) utteranceId.toIntOrNull()?.let { speakNext(it + 1) }
                }
            })
        } else {
            _events.tryEmit(PlaybackEvent.Error("System TTS initialization failed."))
        }
    }

    override fun play(sentences: List<BookSentence>, startIndex: Int, speechRate: Float) {
        queue = sentences
        rate = speechRate
        stopped.set(false)
        tts?.setSpeechRate(rate)
        speakNext(startIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0)))
    }

    override fun stop() {
        stopped.set(true)
        tts?.stop()
    }

    override fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 3.0f)
        tts?.setSpeechRate(rate)
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakNext(index: Int) {
        if (!ready.get()) return
        if (stopped.get()) return
        if (index !in queue.indices) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(queue[index].text, TextToSpeech.QUEUE_FLUSH, params, index.toString())
    }
}
