package com.example.audiobooks.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class TtsModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val directoryName: String,
    val archiveName: String,
    val downloadUrl: String,
    val modelName: String = "model.onnx",
    val lexicon: String = "lexicon.txt",
    val ruleFar: String? = null,
    val approximateSize: String
) {
    val requiredFiles: List<String>
        get() = listOfNotNull(modelName, lexicon, "tokens.txt", ruleFar)
}

data class ModelInstallState(
    val selected: TtsModelSpec,
    val models: List<TtsModelSpec>,
    val installedIds: Set<String>,
    val installed: Boolean,
    val modelDir: File,
    val downloadUrl: String
)

class ModelRepository(context: Context) {
    private val paths = LocalProjectPaths(context)

    fun state(): ModelInstallState {
        paths.ensure()
        val selected = selectedModel()
        val installedIds = MODELS.filter { isInstalled(it) }.mapTo(mutableSetOf()) { it.id }
        return ModelInstallState(
            selected = selected,
            models = MODELS,
            installedIds = installedIds,
            installed = selected.id in installedIds,
            modelDir = modelDir(selected),
            downloadUrl = selected.downloadUrl
        )
    }

    fun selectModel(modelId: String): ModelInstallState {
        val selected = MODELS.firstOrNull { it.id == modelId } ?: MODELS.first()
        saveSelectedModelId(selected.id)
        return state()
    }

    suspend fun downloadAndInstall(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        paths.ensure()
        val spec = selectedModel()
        val archive = File(paths.downloads, spec.archiveName)
        download(spec.downloadUrl, archive, onProgress)
        val tmpModels = File(paths.models, "${spec.directoryName}.tmp")
        if (tmpModels.exists()) tmpModels.deleteRecursively()
        tmpModels.mkdirs()
        extractTarBz2(archive, tmpModels)

        val extracted = File(tmpModels, spec.directoryName)
        require(extracted.isDirectory) { "Downloaded model archive has an unexpected layout." }
        val modelDir = modelDir(spec)
        if (modelDir.exists()) modelDir.deleteRecursively()
        require(extracted.renameTo(modelDir)) { "Could not move downloaded model into place." }
        tmpModels.deleteRecursively()
        require(state().installed) { "Model installation is incomplete." }
    }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val length = connection.contentLengthLong.takeIf { it > 0L }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (length != null) onProgress((copied.toFloat() / length).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
    }

    private fun extractTarBz2(archive: File, destination: File) {
        archive.inputStream().buffered().use { fileInput ->
            BZip2CompressorInputStream(fileInput).use { bzInput ->
                TarArchiveInputStream(bzInput).use { tarInput ->
                    while (true) {
                        val entry = tarInput.nextEntry ?: break
                        val target = File(destination, entry.name).canonicalFile
                        require(target.path.startsWith(destination.canonicalPath)) {
                            "Model archive contains an unsafe path."
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { output -> tarInput.copyTo(output) }
                        }
                    }
                }
            }
        }
    }

    private fun selectedModel(): TtsModelSpec {
        val selectedId = runCatching {
            if (paths.settings.isFile) JSONObject(paths.settings.readText()).optString("selectedModelId") else ""
        }.getOrDefault("")
        return MODELS.firstOrNull { it.id == selectedId } ?: MODELS.first()
    }

    private fun saveSelectedModelId(modelId: String) {
        paths.ensure()
        paths.settings.writeText(JSONObject().put("selectedModelId", modelId).toString(), Charsets.UTF_8)
    }

    private fun modelDir(spec: TtsModelSpec): File = File(paths.models, spec.directoryName)

    private fun isInstalled(spec: TtsModelSpec): Boolean =
        spec.requiredFiles.all { File(modelDir(spec), it).isFile }

    companion object {
        val MODELS = listOf(
            TtsModelSpec(
                id = "vits-icefall-zh-aishell3",
                name = "Aishell3 Chinese",
                description = "Small Chinese model. Fast download, acceptable for smoke tests.",
                directoryName = "vits-icefall-zh-aishell3",
                archiveName = "vits-icefall-zh-aishell3.tar.bz2",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2",
                ruleFar = "rule.far",
                approximateSize = "31 MB"
            ),
            TtsModelSpec(
                id = "vits-melo-tts-zh_en",
                name = "MeloTTS Chinese/English",
                description = "Larger bilingual VITS model. Better candidate for audiobook quality.",
                directoryName = "vits-melo-tts-zh_en",
                archiveName = "vits-melo-tts-zh_en.tar.bz2",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2",
                approximateSize = "167 MB"
            )
        )
    }
}
