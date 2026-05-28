package com.example.audiobooks.storage

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TtsModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val runtime: String,
    val availableInApp: Boolean,
    val directoryName: String,
    val archiveName: String,
    val downloadUrl: String,
    val huggingFaceRepoId: String,
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
    val downloadedIds: Set<String>,
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
        val downloadedIds = MODELS.filter { isDownloaded(it) }.mapTo(mutableSetOf()) { it.id }
        val installedIds = MODELS.filter { isInstalled(it) }.mapTo(mutableSetOf()) { it.id }
        return ModelInstallState(
            selected = selected,
            models = MODELS,
            downloadedIds = downloadedIds,
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
        showProgressNotification(spec.name, 0)
        downloadHuggingFaceSnapshot(spec) { progress ->
            val percent = (progress * 100).toInt().coerceIn(0, 100)
            onProgress(progress)
            showProgressNotification(spec.name, percent)
        }
        File(modelDir(spec), DOWNLOAD_MARKER).writeText(
            JSONObject()
                .put("repo", spec.huggingFaceRepoId)
                .put("downloadedAt", System.currentTimeMillis())
                .toString(),
            Charsets.UTF_8
        )
        showDoneNotification(spec.name)
    }

    private fun downloadHuggingFaceSnapshot(spec: TtsModelSpec, onProgress: (Float) -> Unit) {
        val targetDir = modelDir(spec)
        val tmpDir = File(paths.models, "${spec.directoryName}.tmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()
        val files = huggingFaceFiles(spec.huggingFaceRepoId)
            .filterNot { it.endsWith("/") }
            .filterNot { it == ".gitattributes" || it.equals("README.md", ignoreCase = true) }
        require(files.isNotEmpty()) { "No downloadable model files were found." }

        files.forEachIndexed { index, relativePath ->
            val target = File(tmpDir, relativePath).canonicalFile
            require(target.path.startsWith(tmpDir.canonicalPath)) {
                "Model repository contains an unsafe path."
            }
            target.parentFile?.mkdirs()
            val baseProgress = index.toFloat() / files.size
            val fileShare = 1f / files.size
            download(huggingFaceResolveUrl(spec.huggingFaceRepoId, relativePath), target) { fileProgress ->
                onProgress((baseProgress + fileShare * fileProgress).coerceIn(0f, 1f))
            }
        }

        if (targetDir.exists()) targetDir.deleteRecursively()
        require(tmpDir.renameTo(targetDir)) { "Could not move downloaded model into place." }
        onProgress(1f)
    }

    private fun huggingFaceFiles(repoId: String): List<String> {
        val apiUrl = "https://huggingface.co/api/models/$repoId"
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val siblings = JSONObject(body).optJSONArray("siblings") ?: JSONArray()
        return (0 until siblings.length()).mapNotNull { i ->
            siblings.optJSONObject(i)?.optString("rfilename")?.takeIf(String::isNotBlank)
        }
    }

    private fun huggingFaceResolveUrl(repoId: String, relativePath: String): String {
        val encodedPath = relativePath.split("/").joinToString("/") { part ->
            URLEncoder.encode(part, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "https://huggingface.co/$repoId/resolve/main/$encodedPath"
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

    private fun isDownloaded(spec: TtsModelSpec): Boolean =
        File(modelDir(spec), DOWNLOAD_MARKER).isFile

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val DOWNLOAD_MARKER = ".download-complete.json"
        val MODELS = listOf(
            TtsModelSpec(
                id = "cosyvoice2-onnx",
                name = "CosyVoice2-0.5B ONNX",
                description = "Priority model from the research report. High Chinese quality; ONNX Runtime Mobile integration is pending.",
                runtime = "ONNX Runtime Mobile",
                availableInApp = false,
                directoryName = "CosyVoice2-0.5B_ONNX",
                archiveName = "CosyVoice2-0.5B_ONNX.zip",
                downloadUrl = "https://huggingface.co/Lourdle/CosyVoice2-0.5B_ONNX",
                huggingFaceRepoId = "Lourdle/CosyVoice2-0.5B_ONNX",
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
                huggingFaceRepoId = "ayousanz/cosy-voice3-onnx",
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
                downloadUrl = "https://huggingface.co/fishaudio/fish-speech-1.5",
                huggingFaceRepoId = "fishaudio/fish-speech-1.5",
                approximateSize = "Conversion required"
            )
        )
    }
}
