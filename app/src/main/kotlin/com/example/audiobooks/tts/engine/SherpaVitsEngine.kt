package com.example.audiobooks.tts.engine

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.example.audiobooks.storage.TtsModelSpec
import java.io.File

/**
 * Working offline engine for end-to-end VITS-family models (e.g. MeloTTS zh_en) running on
 * sherpa-onnx + ONNX Runtime Mobile. This is the first shippable voice for the audiobook reader.
 *
 * All model-specific paths come from [TtsModelSpec.engineParams], so the same engine drives any
 * sherpa-supported VITS model without code changes — only the spec entry differs.
 *
 * Recognized engineParams keys:
 *   modelName  (default "model.onnx")
 *   tokens     (default "tokens.txt")
 *   lexicon    (e.g. "lexicon.txt"; optional for phoneme-driven models)
 *   dictDir    (jieba dict directory, e.g. "dict"; optional)
 *   dataDir    (espeak-ng data dir; optional)
 *   ruleFsts   (comma-separated fst filenames, e.g. "date.fst,number.fst,phone.fst"; optional)
 *   ruleFar    (single .far filename; optional)
 *   numThreads (default "2")
 */
class SherpaVitsEngine(private val tts: OfflineTts) : TtsEngine {
    override val sampleRate: Int get() = tts.sampleRate()

    override fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray =
        tts.generate(text = text, sid = speakerId, speed = speed).samples

    override fun release() {
        tts.release()
    }
}

class SherpaVitsEngineFactory : TtsEngineFactory {
    override val engineId: String = ENGINE_ID
    override val runtimeLabel: String = "sherpa-onnx VITS"
    override val isImplemented: Boolean = true

    override fun requiredFiles(spec: TtsModelSpec): List<String> {
        val p = spec.engineParams
        return listOfNotNull(
            p["modelName"] ?: "model.onnx",
            p["tokens"] ?: "tokens.txt",
            p["lexicon"]?.takeIf { it.isNotBlank() }
        )
    }

    override fun create(context: Context, modelDir: File, spec: TtsModelSpec): TtsEngine {
        val p = spec.engineParams
        fun abs(name: String) = File(modelDir, name).absolutePath
        val ruleFsts = p["ruleFsts"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(",") { abs(it) }
            .orEmpty()

        val config = getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = p["modelName"] ?: "model.onnx",
            acousticModelName = "",
            vocoder = "",
            voices = "",
            lexicon = p["lexicon"].orEmpty(),
            dataDir = p["dataDir"]?.takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty(),
            dictDir = p["dictDir"]?.takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty(),
            ruleFsts = ruleFsts,
            ruleFars = p["ruleFar"]?.takeIf { it.isNotBlank() }?.let { abs(it) }.orEmpty(),
            numThreads = p["numThreads"]?.toIntOrNull() ?: 2
        )
        return SherpaVitsEngine(OfflineTts(assetManager = null, config = config))
    }

    companion object {
        const val ENGINE_ID = "sherpa-vits"
    }
}
