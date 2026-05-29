package com.example.audiobooks.tts.engine

import android.content.Context
import com.example.audiobooks.storage.TtsModelSpec
import java.io.File

/**
 * Engine slot for CosyVoice2 (LLM + flow-matching + vocoder, zero-shot voice cloning).
 *
 * Status: NOT YET IMPLEMENTED ([isImplemented] = false). The model can be selected and downloaded;
 * playback stays disabled until the on-device pipeline is wired. We deliberately never fall back to
 * a system voice.
 *
 * The app now downloads from the official ModelScope repo `iic/CosyVoice2-0.5B` instead of the
 * older partial flow/hift export. That repo provides the missing development artifacts such as
 * `campplus.onnx`, `speech_tokenizer_v2*.onnx`, the flow/vocoder checkpoints, and the text-tokenizer
 * files. The remaining runtime work is still substantial because the text-speech LLM is published
 * as `llm.pt`, so Android playback needs either an ONNX/mobile export of that stage or a separate
 * mobile runtime plan.
 *
 * Pipeline outline:
 *   text + prompt -> text-speech LLM -> speech tokens
 *   speech tokens -> flow matching -> acoustic features
 *   acoustic features -> vocoder -> waveform
 *   reference audio -> speech tokenizer + campplus -> prompt tokens + speaker embedding
 */
class CosyVoice2EngineFactory : TtsEngineFactory {
    override val engineId: String = "cosyvoice2"
    override val runtimeLabel: String = "ONNX Runtime Mobile (under development)"
    override val isImplemented: Boolean = false

    override fun requiredFiles(spec: TtsModelSpec): List<String> = spec.downloadFiles

    override fun create(context: Context, modelDir: File, spec: TtsModelSpec): TtsEngine =
        throw NotImplementedError(
            "CosyVoice2 mobile inference is not implemented yet: the official ModelScope package " +
                "still needs a mobile runnable text-speech LLM stage plus tokenizer/post-processing."
        )
}
