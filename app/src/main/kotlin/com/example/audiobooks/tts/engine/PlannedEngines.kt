package com.example.audiobooks.tts.engine

import android.content.Context
import com.example.audiobooks.storage.TtsModelSpec
import java.io.File

/**
 * Engine slot for Fun-CosyVoice3 (newer CosyVoice generation).
 *
 * Status: NOT YET IMPLEMENTED. Same multi-stage architecture as CosyVoice2 (autoregressive token
 * LM -> flow -> vocoder, with campplus speaker embedding + speech_tokenizer_v3). The configured
 * repo (FunAudioLLM/Fun-CosyVoice3-0.5B-2512) ships campplus.onnx, flow.decoder.estimator.fp32.onnx
 * and speech_tokenizer_v3.onnx, but the on-device token-LM decode loop and post-processing still
 * need to be wired the same way as CosyVoice2 (see CosyVoice2Engine for the outline).
 */
class CosyVoice3EngineFactory : TtsEngineFactory {
    override val engineId: String = "cosyvoice3"
    override val runtimeLabel: String = "ONNX Runtime Mobile（开发中）"
    override val isImplemented: Boolean = false

    override fun requiredFiles(spec: TtsModelSpec): List<String> = spec.downloadFiles

    override fun create(context: Context, modelDir: File, spec: TtsModelSpec): TtsEngine =
        throw NotImplementedError("Fun-CosyVoice3 端侧推理流水线尚未实现。")
}

/**
 * Engine slot for Fish Speech 1.5 (emotion-capable, dual-AR + firefly-GAN vocoder).
 *
 * Status: NOT YET IMPLEMENTED. The published weights are PyTorch (.pth) and must first be converted
 * / quantized to ONNX before any mobile runtime is possible. Until a mobile-ready export exists,
 * this slot stays disabled.
 */
class FishSpeechEngineFactory : TtsEngineFactory {
    override val engineId: String = "fish-speech"
    override val runtimeLabel: String = "待转换 ONNX（开发中）"
    override val isImplemented: Boolean = false

    override fun requiredFiles(spec: TtsModelSpec): List<String> = spec.downloadFiles

    override fun create(context: Context, modelDir: File, spec: TtsModelSpec): TtsEngine =
        throw NotImplementedError("Fish Speech 1.5 需先转换/量化为 ONNX 才能在移动端运行。")
}
