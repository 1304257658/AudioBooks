package com.example.audiobooks.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.example.audiobooks.model.BookSentence
import com.example.audiobooks.storage.LocalProjectPaths
import com.example.audiobooks.storage.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SherpaTtsPlayer(context: Context) : TtsPlayer {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stopped = AtomicBoolean(true)
    private var tts: OfflineTts? = null
    private var track: AudioTrack? = null
    private var rate = 1.0f

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlaybackEvent> = _events

    override fun play(sentences: List<BookSentence>, startIndex: Int, speechRate: Float) {
        stop()
        if (sentences.isEmpty()) return
        rate = speechRate.coerceIn(0.5f, 3.0f)
        stopped.set(false)
        scope.launch {
            runCatching {
                val engine = ensureEngine()
                val sampleRate = engine.sampleRate()
                ensureTrack(sampleRate)
                val queue = Channel<AudioChunk>(capacity = PREFETCH_CHUNKS)
                val producer = launch {
                    var index = startIndex.coerceIn(0, sentences.lastIndex)
                    runCatching {
                        while (!stopped.get() && index in sentences.indices) {
                            val text = sentences[index].text
                            val audio = withContext(Dispatchers.Default) {
                                engine.generate(text = text, sid = 0, speed = rate)
                            }
                            queue.send(
                                AudioChunk(
                                    index = index,
                                    text = text,
                                    samples = audio.samples
                                )
                            )
                            index += 1
                        }
                    }.onFailure { error ->
                        if (!stopped.get()) {
                            _events.emit(
                                PlaybackEvent.Error(
                                    "Sherpa TTS generation stopped: ${error.message ?: error::class.java.simpleName}"
                                )
                            )
                        }
                    }.also {
                        queue.close()
                    }
                }

                try {
                    for (chunk in queue) {
                        if (stopped.get()) break
                        _events.emit(PlaybackEvent.SentenceStarted(chunk.index))
                        if (chunk.samples.isNotEmpty() && !stopped.get()) {
                            writeSamples(chunk.samples)
                            writeSilence(sampleRate, pauseMsFor(chunk.text))
                        }
                        _events.emit(PlaybackEvent.SentenceDone(chunk.index))
                    }
                } finally {
                    producer.cancelAndJoin()
                }
            }.onFailure { error ->
                _events.emit(PlaybackEvent.Error("Sherpa TTS stopped: ${error.message ?: error::class.java.simpleName}"))
                stopped.set(true)
            }
        }
    }

    override fun stop() {
        stopped.set(true)
        track?.pause()
        track?.flush()
    }

    override fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 3.0f)
    }

    override fun shutdown() {
        stop()
        track?.release()
        track = null
        tts?.release()
        tts = null
        scope.cancel()
    }

    private fun ensureEngine(): OfflineTts {
        tts?.let { return it }
        val modelState = ModelRepository(appContext).state()
        val spec = modelState.selected
        if (!spec.availableInApp) {
            error("${spec.name} runtime is not integrated yet.")
        }
        val modelDir = File(LocalProjectPaths(appContext).models, spec.directoryName)
        val requiredFiles = spec.requiredFiles
        val missing = requiredFiles.filterNot { File(modelDir, it).isFile }
        if (missing.isNotEmpty()) {
            error(
                "Voice model is not installed. Use Download model first. Missing: ${missing.joinToString()}"
            )
        }
        val config = getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = spec.modelName,
            acousticModelName = "",
            vocoder = "",
            voices = "",
            lexicon = spec.lexicon,
            dataDir = "",
            dictDir = "",
            ruleFsts = "",
            ruleFars = spec.ruleFar?.let { File(modelDir, it).absolutePath }.orEmpty(),
            numThreads = 4
        )
        return OfflineTts(assetManager = null, config = config).also { tts = it }
    }

    fun clearEngine() {
        stop()
        tts?.release()
        tts = null
    }

    private fun ensureTrack(sampleRate: Int) {
        val current = track
        if (current != null && current.sampleRate == sampleRate) {
            current.play()
            return
        }
        current?.release()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate)
        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also { it.play() }
    }

    private fun writeSamples(samples: FloatArray) {
        val pcm = ShortArray(samples.size)
        samples.forEachIndexed { index, sample ->
            pcm[index] = (sample.coerceIn(-1.0f, 1.0f) * Short.MAX_VALUE).toInt().toShort()
        }
        track?.write(pcm, 0, pcm.size)
    }

    private fun writeSilence(sampleRate: Int, pauseMs: Int) {
        if (pauseMs <= 0 || stopped.get()) return
        val samples = (sampleRate * pauseMs) / 1000
        track?.write(ShortArray(samples), 0, samples)
    }

    private fun pauseMsFor(text: String): Int =
        when (text.trim().lastOrNull()) {
            '\u3002', '.', '\uff01', '!', '\uff1f', '?' -> 420
            '\uff1b', ';' -> 320
            '\uff0c', '\u3001', ',', '\uff1a', ':' -> 220
            else -> 260
        }

    private data class AudioChunk(
        val index: Int,
        val text: String,
        val samples: FloatArray
    )

    companion object {
        private const val PREFETCH_CHUNKS = 4
    }
}


