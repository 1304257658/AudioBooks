package com.example.audiobooks.tts.engine

import android.content.Context
import com.example.audiobooks.storage.TtsModelSpec
import java.io.File

/**
 * A pluggable offline text-to-speech inference backend.
 *
 * The reader / playback layer never talks to a concrete model. It only talks to a [TtsEngine].
 * Swapping the underlying model (VITS today, CosyVoice / Fish tomorrow) means adding a new
 * [TtsEngineFactory] and registering it in [TtsEngineRegistry] — nothing else in the app changes.
 *
 * Implementations are created lazily on first playback (never during app startup) because some
 * runtimes load large native graphs that can crash a cold app process.
 */
interface TtsEngine {
    /** PCM sample rate of the audio produced by [synthesize]. */
    val sampleRate: Int

    /**
     * Synthesize a single chunk of text into mono float PCM in the range [-1, 1].
     *
     * @param text       the text to speak
     * @param speakerId  speaker / voice id (0 for single-speaker models)
     * @param speed      speech rate multiplier (1.0 = normal)
     */
    fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray

    /** Release native resources. The engine must not be used after this call. */
    fun release()
}

/**
 * Knows how to decide whether a model is runnable and how to build its [TtsEngine].
 *
 * One factory == one runtime family. A factory is registered under a stable [engineId]; a
 * [TtsModelSpec] references that id, fully decoupling "which model" from "which runtime".
 */
interface TtsEngineFactory {
    /** Stable identifier referenced by [TtsModelSpec.engineId]. */
    val engineId: String

    /** Human-facing runtime label (shown in the model list). */
    val runtimeLabel: String

    /**
     * Whether this engine has a working, shippable runtime implementation.
     *
     * `false` means the model can still be selected and downloaded, but playback stays disabled
     * with an honest "engine in development" message — we never fall back to a fake voice.
     */
    val isImplemented: Boolean

    /** Files (relative to the model directory) that must exist for the engine to run. */
    fun requiredFiles(spec: TtsModelSpec): List<String>

    /** Build a ready-to-use engine. Only called when [isImplemented] and files are present. */
    fun create(context: Context, modelDir: File, spec: TtsModelSpec): TtsEngine
}
