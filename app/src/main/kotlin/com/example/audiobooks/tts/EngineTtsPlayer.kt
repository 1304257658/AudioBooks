package com.example.audiobooks.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.audiobooks.model.BookSentence
import com.example.audiobooks.storage.LocalProjectPaths
import com.example.audiobooks.storage.ModelRepository
import com.example.audiobooks.tts.engine.TtsEngine
import com.example.audiobooks.tts.engine.TtsEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine-agnostic streaming player.
 *
 * This class owns only the playback pipeline: bounded concurrent synthesis, ordered AudioTrack
 * streaming, inter-sentence pauses and playback events. The actual speech synthesis is delegated
 * to whatever [TtsEngine] the registry resolves for the currently selected model.
 */
class EngineTtsPlayer(context: Context) : TtsPlayer {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stopped = AtomicBoolean(true)
    private val engineLock = Any()
    private val engines = mutableListOf<TtsEngine?>()
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
                val primaryEngine = ensureEngine(slot = 0)
                val sampleRate = primaryEngine.sampleRate
                ensureTrack(sampleRate)

                val start = startIndex.coerceIn(0, sentences.lastIndex)
                val end = sentences.lastIndex
                val workerCount = minOf(MAX_TTS_WORKERS, end - start + 1)
                val indexQueue = Channel<Int>(capacity = PREFETCH_CHUNKS)
                val results = Channel<AudioChunk>(capacity = PREFETCH_CHUNKS)

                val producer = launch {
                    val dispatcher = launch {
                        for (index in start..end) {
                            if (stopped.get()) break
                            indexQueue.send(index)
                        }
                        indexQueue.close()
                    }
                    val workers = List(workerCount) { worker ->
                        launch {
                            val workerEngine = ensureEngine(slot = worker)
                            runCatching {
                                for (index in indexQueue) {
                                    if (stopped.get()) break
                                    val text = sentences[index].text
                                    val audio = withContext(Dispatchers.Default) {
                                        workerEngine.synthesize(text = text, speakerId = 0, speed = rate)
                                    }
                                    results.send(
                                        AudioChunk(
                                            index = index,
                                            text = text,
                                            samples = levelAudio(audio)
                                        )
                                    )
                                }
                            }.onFailure { error ->
                                reportGenerationError(error)
                            }
                        }
                    }
                    runCatching {
                        dispatcher.join()
                        workers.joinAll()
                    }.onFailure { error ->
                        reportGenerationError(error)
                    }.also {
                        results.close()
                    }
                }

                try {
                    val pending = sortedMapOf<Int, AudioChunk>()
                    var nextIndex = start
                    while (!stopped.get() && nextIndex <= end) {
                        while (!stopped.get() && pending[nextIndex] == null) {
                            val received = results.receiveCatching().getOrNull() ?: break
                            pending[received.index] = received
                        }
                        val chunk = pending.remove(nextIndex) ?: break
                        _events.emit(PlaybackEvent.SentenceStarted(chunk.index))
                        if (chunk.samples.isNotEmpty() && !stopped.get()) {
                            writeSamples(chunk.samples)
                            writeSilence(sampleRate, pauseMsFor(chunk.text))
                        }
                        _events.emit(PlaybackEvent.SentenceDone(chunk.index))
                        nextIndex += 1
                    }
                } finally {
                    producer.cancelAndJoin()
                }
            }.onFailure { error ->
                _events.emit(PlaybackEvent.Error("TTS stopped: ${error.message ?: error::class.java.simpleName}"))
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
        releaseEngines()
        scope.cancel()
    }

    private fun reportGenerationError(error: Throwable) {
        if (!stopped.get()) {
            stopped.set(true)
            _events.tryEmit(
                PlaybackEvent.Error(
                    "TTS generation stopped: ${error.message ?: error::class.java.simpleName}"
                )
            )
        }
    }

    private fun ensureEngine(slot: Int): TtsEngine = synchronized(engineLock) {
        while (engines.size <= slot) engines += null
        engines[slot]?.let { return@synchronized it }
        createEngine().also { engines[slot] = it }
    }

    private fun createEngine(): TtsEngine {
        val modelState = ModelRepository(appContext).state()
        val spec = modelState.selected
        val factory = TtsEngineRegistry.factory(spec.engineId)
            ?: error("No TTS engine is registered for ${spec.name} (${spec.engineId}).")
        if (!factory.isImplemented) {
            error("${spec.name} runtime is still under development.")
        }
        val modelDir = File(LocalProjectPaths(appContext).models, spec.directoryName)
        val missing = factory.requiredFiles(spec).filterNot { File(modelDir, it).isFile }
        if (missing.isNotEmpty()) {
            error("Voice model files are incomplete. Missing: ${missing.joinToString()}")
        }
        return factory.create(appContext, modelDir, spec)
    }

    /** Drop cached engines so the next playback rebuilds them after a model switch/delete. */
    fun clearEngine() {
        stop()
        releaseEngines()
    }

    private fun releaseEngines() = synchronized(engineLock) {
        engines.forEach { it?.release() }
        engines.clear()
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
        if (text.contains(PARAGRAPH_BREAK_MARKER)) {
            560
        } else when (text.trim().lastOrNull()) {
            '.', '!', '?', ';', '\u3002', '\uff01', '\uff1f', '\uff1b' -> 420
            ',', ':', '\u3001', '\uff0c', '\uff1a' -> 220
            else -> 260
        }

    private fun levelAudio(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var sumSquares = 0.0
        var peak = 0.0f
        samples.forEach { sample ->
            sumSquares += (sample * sample).toDouble()
            peak = maxOf(peak, kotlin.math.abs(sample))
        }
        if (peak <= 0.0001f) return samples
        val rms = kotlin.math.sqrt(sumSquares / samples.size).toFloat().coerceAtLeast(0.0001f)
        val gain = (TARGET_RMS / rms).coerceIn(MIN_GAIN, MAX_GAIN)
        val peakLimitedGain = minOf(gain, PEAK_CEILING / peak)
        if (kotlin.math.abs(peakLimitedGain - 1.0f) < 0.02f) return samples
        return FloatArray(samples.size) { index ->
            (samples[index] * peakLimitedGain).coerceIn(-PEAK_CEILING, PEAK_CEILING)
        }
    }

    private data class AudioChunk(
        val index: Int,
        val text: String,
        val samples: FloatArray
    )

    companion object {
        private const val PREFETCH_CHUNKS = 4
        private const val MAX_TTS_WORKERS = 2
        private const val TARGET_RMS = 0.12f
        private const val MIN_GAIN = 0.65f
        private const val MAX_GAIN = 2.2f
        private const val PEAK_CEILING = 0.95f
        private const val PARAGRAPH_BREAK_MARKER = "\u2029"
    }
}
