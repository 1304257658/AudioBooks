package com.example.audiobooks.tts.engine

/**
 * Central registry mapping an `engineId` to its [TtsEngineFactory].
 *
 * This is the single seam between models and runtimes. To support a new model family:
 *   1. implement a [TtsEngineFactory] (+ its [TtsEngine]),
 *   2. register it here,
 *   3. point a `TtsModelSpec.engineId` at it.
 *
 * No playback / UI / storage code needs to change. Model availability across the app is derived
 * from this registry ([isImplemented]) plus on-disk file presence — there is no hard-coded
 * "this model is allowed" flag anywhere.
 */
object TtsEngineRegistry {
    private val factories = LinkedHashMap<String, TtsEngineFactory>()

    init {
        register(SherpaVitsEngineFactory())
        register(CosyVoice2EngineFactory())
        register(CosyVoice3EngineFactory())
        register(FishSpeechEngineFactory())
    }

    fun register(factory: TtsEngineFactory) {
        factories[factory.engineId] = factory
    }

    fun factory(engineId: String): TtsEngineFactory? = factories[engineId]

    fun isImplemented(engineId: String): Boolean = factories[engineId]?.isImplemented == true

    fun runtimeLabel(engineId: String): String = factories[engineId]?.runtimeLabel ?: engineId
}
