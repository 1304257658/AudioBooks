package com.example.audiobooks.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ModelInstallState(
    val installed: Boolean,
    val modelDir: File,
    val downloadUrl: String
)

class ModelRepository(context: Context) {
    private val paths = LocalProjectPaths(context)
    private val modelDir = File(paths.models, MODEL_DIR_NAME)

    fun state(): ModelInstallState =
        ModelInstallState(
            installed = REQUIRED_FILES.all { File(modelDir, it).isFile },
            modelDir = modelDir,
            downloadUrl = MODEL_URL
        )

    suspend fun downloadAndInstall(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        paths.ensure()
        val archive = File(paths.downloads, "$MODEL_DIR_NAME.tar.bz2")
        download(archive, onProgress)
        val tmpModels = File(paths.models, "$MODEL_DIR_NAME.tmp")
        if (tmpModels.exists()) tmpModels.deleteRecursively()
        tmpModels.mkdirs()
        extractTarBz2(archive, tmpModels)

        val extracted = File(tmpModels, MODEL_DIR_NAME)
        require(extracted.isDirectory) { "Downloaded model archive has an unexpected layout." }
        if (modelDir.exists()) modelDir.deleteRecursively()
        require(extracted.renameTo(modelDir)) { "Could not move downloaded model into place." }
        tmpModels.deleteRecursively()
        require(state().installed) { "Model installation is incomplete." }
    }

    private fun download(target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
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

    companion object {
        const val MODEL_DIR_NAME = "vits-icefall-zh-aishell3"
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2"
        val REQUIRED_FILES = listOf("model.onnx", "lexicon.txt", "tokens.txt", "rule.far")
    }
}

