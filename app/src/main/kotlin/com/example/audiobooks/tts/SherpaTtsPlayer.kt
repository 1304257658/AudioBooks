package com.example.audiobooks.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.example.audiobooks.model.BookSentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        rate = speechRate.coerceIn(0.5f, 3.0f)
        stopped.set(false)
        scope.launch {
            runCatching {
                val engine = ensureEngine()
                val sampleRate = engine.sampleRate()
                ensureTrack(sampleRate)
                var index = startIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0))
                while (!stopped.get() && index in sentences.indices) {
                    _events.emit(PlaybackEvent.SentenceStarted(index))
                    val audio = withContext(Dispatchers.Default) {
                        engine.generate(text = sentences[index].text, sid = 0, speed = rate)
                    }
                    if (audio.samples.isNotEmpty() && !stopped.get()) {
                        writeSamples(audio.samples)
                    }
                    _events.emit(PlaybackEvent.SentenceDone(index))
                    index += 1
                }
            }.onFailure { error ->
                _events.emit(PlaybackEvent.Error("Sherpa TTS 澶辫触锛屽凡鍋滄: ${error.message ?: error::class.java.simpleName}"))
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
        val modelDir = File(appContext.filesDir, "models/vits-icefall-zh-aishell3")
        val requiredFiles = listOf("model.onnx", "lexicon.txt", "tokens.txt", "rule.far")
        val missing = requiredFiles.filterNot { File(modelDir, it).isFile }
        if (missing.isNotEmpty()) {
            error(
                "Voice model is not installed. Download vits-icefall-zh-aishell3.tar.bz2 from " +
                    MODEL_URL + " and extract it to app private models/vits-icefall-zh-aishell3. " +
                    "Missing: ${missing.joinToString()}"
            )
        }
        val config = getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = "model.onnx",
            acousticModelName = "",
            vocoder = "",
            voices = "",
            lexicon = "lexicon.txt",
            dataDir = "",
            dictDir = "",
            ruleFsts = "",
            ruleFars = File(modelDir, "rule.far").absolutePath,
            numThreads = 4
        )
        return OfflineTts(assetManager = null, config = config).also { tts = it }
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

    private companion object {
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2"
    }
}


