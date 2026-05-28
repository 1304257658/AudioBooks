package com.example.audiobooks.storage

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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
    val runtime: String,
    val availableInApp: Boolean,
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
    private val appContext = context.applicationContext
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

    suspend fun deleteSelectedModel() = withContext(Dispatchers.IO) {
        val spec = selectedModel()
        val dir = modelDir(spec)
        if (dir.exists()) dir.deleteRecursively()
        File(paths.downloads, spec.archiveName).takeIf { it.exists() }?.delete()
    }

    suspend fun downloadAndInstall(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        paths.ensure()
        val spec = selectedModel()
        require(spec.availableInApp) { "${spec.name} runtime is not integrated yet." }
        val archive = File(paths.downloads, spec.archiveName)
        showProgressNotification(spec.name, 0)
        download(spec.downloadUrl, archive) { progress ->
            onProgress(progress)
            showProgressNotification(spec.name, (progress * 100).toInt().coerceIn(0, 100))
        }
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
        showDoneNotification(spec.name)
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

    private fun showProgressNotification(modelName: String, progress: Int) {
        val manager = notificationManager()
        createNotificationChannel(manager)
        val notification = android.app.Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading voice model")
            .setContentText(modelName)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun showDoneNotification(modelName: String) {
        val manager = notificationManager()
        createNotificationChannel(manager)
        val notification = android.app.Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Voice model ready")
            .setContentText(modelName)
            .setOngoing(false)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun notificationManager(): NotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
            )
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
        spec.availableInApp &&
        spec.requiredFiles.all { File(modelDir(spec), it).isFile }

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001
        val MODELS = listOf(
            TtsModelSpec(
                id = "cosyvoice2-onnx",
                name = "CosyVoice2-0.5B ONNX",
                description = "Priority model from the research report. High Chinese quality; ONNX Runtime Mobile integration is pending.",
                runtime = "ONNX Runtime Mobile",
                availableInApp = false,
                directoryName = "CosyVoice2-0.5B_ONNX",
                archiveName = "CosyVoice2-0.5B_ONNX.zip",
                downloadUrl = "https://www.modelscope.cn/models/Lourdle/CosyVoice2-0.5B_ONNX",
                approximateSize = "ModelScope"
            ),
            TtsModelSpec(
                id = "cosyvoice3-onnx",
                name = "Fun-CosyVoice3-0.5B ONNX",
                description = "Newer CosyVoice option from the report. Better quality target; ONNX Runtime Mobile integration is pending.",
                runtime = "ONNX Runtime Mobile",
                availableInApp = false,
                directoryName = "Fun-CosyVoice3-0.5B-ONNX",
                archiveName = "cosy-voice3-onnx.zip",
                downloadUrl = "https://huggingface.co/ayousanz/cosy-voice3-onnx",
                approximateSize = "HuggingFace"
            ),
            TtsModelSpec(
                id = "fish-speech-1.5",
                name = "Fish Speech 1.5",
                description = "Emotion-capable model from the report. It needs conversion/quantization before mobile integration.",
                runtime = "Pending conversion",
                availableInApp = false,
                directoryName = "fish-speech-1.5",
                archiveName = "fish-speech-1.5.zip",
                downloadUrl = "https://www.modelscope.cn/models/fishaudio/fish-speech-1.5",
                approximateSize = "Conversion required"
            )
        )
    }
}
