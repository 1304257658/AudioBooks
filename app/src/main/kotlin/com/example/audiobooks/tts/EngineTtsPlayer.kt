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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine-agnostic streaming player.
 *
 * This class owns ONLY the playback pipeline: prefetch queue, AudioTrack streaming, inter-sentence
 * pauses and playback events. The actual speech synthesis is delegated to whatever [TtsEngine] the
 * [TtsEngineRegistry] resolves for the currently selected model. Swapping the underlying model
 * therefore never touches this file.
 *
 * The engine is created lazily on first playback (not at construction / app startup) because some
 * native runtimes load large graphs that can crash a cold process.
 */
class EngineTtsPlayer(context: Context) : TtsPlayer {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stopped = AtomicBoolean(true)
    private var engine: TtsEngine? = null
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
                val sampleRate = engine.sampleRate
                ensureTrack(sampleRate)
                val queue = Channel<AudioChunk>(capacity = PREFETCH_CHUNKS)
                val producer = launch {
                    var index = startIndex.coerceIn(0, sentences.lastIndex)
                    runCatching {
                        while (!stopped.get() && index in sentences.indices) {
                            val text = sentences[index].text
                            val audio = withContext(Dispatchers.Default) {
                                engine.synthesize(text = text, speakerId = 0, speed = rate)
                            }
                            queue.send(
                                AudioChunk(
                                    index = index,
                                    text = text,
                                    samples = audio
                                )
                            )
                            index += 1
                        }
                    }.onFailure { error ->
                        if (!stopped.get()) {
                            _events.emit(
                                PlaybackEvent.Error(
                                    "TTS generation stopped: ${error.message ?: error::class.java.simpleName}"
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
        engine?.release()
        engine = null
        scope.cancel()
    }

    private fun ensureEngine(): TtsEngine {
        engine?.let { return it }
        val modelState = ModelRepository(appContext).state()
        val spec = modelState.selected
        val factory = TtsEngineRegistry.factory(spec.engineId)
            ?: error("没有为模型 ${spec.name} 注册推理引擎（engineId=${spec.engineId}）。")
        if (!factory.isImplemented) {
            error("${spec.name} 的离线推理引擎仍在开发中。")
        }
        val modelDir = File(LocalProjectPaths(appContext).models, spec.directoryName)
        val missing = factory.requiredFiles(spec).filterNot { File(modelDir, it).isFile }
        if (missing.isNotEmpty()) {
            error("语音模型文件不完整，请先下载。缺少：${missing.joinToString()}")
        }
        return factory.create(appContext, modelDir, spec).also { engine = it }
    }

    /** Drop the cached engine so the next playback rebuilds it (e.g. after switching models). */
    fun clearEngine() {
        stop()
        engine?.release()
        engine = null
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
            '。', '.', '！', '!', '？', '?' -> 420
            '；', ';' -> 320
            '，', '、', ',', '：', ':' -> 220
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
