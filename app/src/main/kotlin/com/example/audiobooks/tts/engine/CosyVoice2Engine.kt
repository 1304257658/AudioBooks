package com.example.audiobooks.tts.engine

import android.content.Context
import com.example.audiobooks.storage.TtsModelSpec
import java.io.File

/**
 * Engine slot for CosyVoice2 (LLM + flow-matching + HiFi-GAN, zero-shot voice cloning).
 *
 * Status: NOT YET IMPLEMENTED ([isImplemented] = false). The model can be selected and downloaded;
 * playback stays disabled with an honest message until the pipeline below is built. We deliberately
 * never fall back to a system / fake voice.
 *
 * --- Why this is not a drop-in for the VITS engine ---
 * CosyVoice2 is multi-stage and cannot run through sherpa-onnx's OfflineTts:
 *
 *   text + prompt  ->  [Qwen LLM, autoregressive]  ->  speech tokens
 *   speech tokens  ->  [flow matching]             ->  mel spectrogram
 *   mel            ->  [HiFi-GAN vocoder (hift)]   ->  waveform
 *
 * Zero-shot timbre also requires a reference utterance:
 *   reference audio -> [speech tokenizer] -> prompt speech tokens
 *   reference audio -> [campplus]         -> speaker embedding
 *
 * --- Required artifacts (to be downloaded into the model dir) ---
 * The currently configured ModelScope repo (Lourdle/CosyVoice2-0.5B_ONNX) ships ONLY the back half
 * (flow_*.onnx + hift.onnx). To make this engine runnable we additionally need an ONNX export of:
 *   - the autoregressive LLM (text/prompt tokens -> speech tokens)
 *   - the speech tokenizer (reference wav -> prompt tokens)
 *   - the campplus speaker-embedding model
 *   - the text tokenizer vocab / config
 * Update TtsModelSpec.downloadFiles + engineParams once a complete export is chosen.
 *
 * --- Implementation outline (using onnxruntime-android, already bundled via the sherpa aar) ---
 *   1. Load each .onnx as an ai.onnxruntime.OrtSession (lazy, on a background thread).
 *   2. Maintain KV cache for the autoregressive LLM; decode speech tokens until EOS.
 *   3. Run flow + hift to get waveform; return FloatArray at sampleRate (24 kHz for CosyVoice2).
 *   4. Cache the speaker embedding per reference voice so it is computed once, not per sentence.
 *   5. Validate real-time factor on-device; quantize (fp16/int8) if RTF >= 1 for streaming playback.
 *
 * The EngineTtsPlayer streaming contract only needs [sampleRate] + [synthesize]; nothing else in
 * the app changes when this is filled in.
 */
class CosyVoice2EngineFactory : TtsEngineFactory {
    override val engineId: String = "cosyvoice2"
    override val runtimeLabel: String = "ONNX Runtime Mobile（开发中）"
    override val isImplemented: Boolean = false

    override fun requiredFiles(spec: TtsModelSpec): List<String> = spec.downloadFiles

    override fun create(context: Context, modelDir: File, spec: TtsModelSpec): TtsEngine =
        throw NotImplementedError(
            "CosyVoice2 端侧推理流水线尚未实现：缺少 LLM(text→token)、speech tokenizer、" +
                "speaker embedding 等模块，仅有 flow+hift 无法从文本合成。"
        )
}
