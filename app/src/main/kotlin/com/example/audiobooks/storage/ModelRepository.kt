package com.example.audiobooks.storage

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.audiobooks.tts.engine.TtsEngineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.min

/** How a model package is fetched. Decoupled from the runtime that later consumes the files. */
enum class DownloadStrategy {
    /** Download an explicit file whitelist from a ModelScope repo (default). */
    MODELSCOPE_FILES,

    /** Download a single .tar.bz2 archive (e.g. sherpa-onnx GitHub release) and extract it. */
    ARCHIVE_TAR_BZ2
}

/**
 * Pure metadata describing a downloadable voice model. It carries NO runtime logic — it only points
 * at an [engineId] (resolved via TtsEngineRegistry) and passes engine-specific paths through
 * [engineParams]. This is what decouples "which model" from "which inference runtime".
 */
data class TtsModelSpec(
    val id: String,
    val name: String,
    val description: String,
    /** Runtime engine id, resolved against TtsEngineRegistry. */
    val engineId: String,
    /** Human-facing runtime label for the model list. */
    val runtime: String,
    val directoryName: String,
    val archiveName: String,
    val downloadUrl: String,
    val modelScopeRepoId: String,
    val downloadFiles: List<String>,
    val sizeBytes: Long,
    val downloadStrategy: DownloadStrategy = DownloadStrategy.MODELSCOPE_FILES,
    /** Engine-specific configuration (e.g. modelName/lexicon/dictDir for sherpa-vits). */
    val engineParams: Map<String, String> = emptyMap()
) {
    val sizeLabel: String
        get() = formatBytes(sizeBytes)

    val approximateSize: String
        get() = formatBytes(sizeBytes)
}

data class ModelInstallState(
    val selected: TtsModelSpec,
    val models: List<TtsModelSpec>,
    val downloadedIds: Set<String>,
    val installedIds: Set<String>,
    val installed: Boolean,
    val modelDir: File,
    val dataRoot: File,
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
            dataRoot = paths.root,
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
        deleteModel(spec.id)
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val spec = MODELS.firstOrNull { it.id == modelId } ?: return@withContext
        val dir = modelDir(spec)
        if (dir.exists()) dir.deleteRecursively()
        File(paths.models, "${spec.directoryName}.tmp").takeIf { it.exists() }?.deleteRecursively()
        File(paths.downloads, spec.archiveName).takeIf { it.exists() }?.delete()
    }

    suspend fun downloadAndInstall(modelId: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        paths.ensure()
        val spec = MODELS.firstOrNull { it.id == modelId } ?: selectedModel()
        showProgressNotification(spec.name, 0)
        val report: (Float) -> Unit = { progress ->
            val percent = (progress * 100).toInt().coerceIn(0, 100)
            onProgress(progress)
            showProgressNotification(spec.name, percent)
        }
        when (spec.downloadStrategy) {
            DownloadStrategy.MODELSCOPE_FILES -> downloadModelScopeSnapshot(spec, report)
            DownloadStrategy.ARCHIVE_TAR_BZ2 -> downloadAndExtractArchive(spec, report)
        }
        File(modelDir(spec), DOWNLOAD_MARKER).writeText(
            JSONObject()
                .put("repo", spec.modelScopeRepoId)
                .put("downloadedAt", System.currentTimeMillis())
                .toString(),
            Charsets.UTF_8
        )
        showDoneNotification(spec.name)
    }

    private fun downloadModelScopeSnapshot(spec: TtsModelSpec, onProgress: (Float) -> Unit) {
        val targetDir = modelDir(spec)
        val tmpDir = File(paths.models, "${spec.directoryName}.tmp")
        tmpDir.mkdirs()
        val available = modelScopeFileMetadata(spec.modelScopeRepoId).associateBy { it.path }
        val files = spec.downloadFiles.mapNotNull { available[it] }
        val missing = spec.downloadFiles.filterNot { it in available }
        require(missing.isEmpty()) { "Model repository is missing expected files: ${missing.joinToString()}" }
        require(files.isNotEmpty()) { "No downloadable model files were found." }

        val declaredTotal = files.sumOf { it.size.takeIf { size -> size > 0L } ?: 0L }
        val totalBytes = declaredTotal.takeIf { it > 0L } ?: spec.sizeBytes.takeIf { it > 0L } ?: files.size.toLong()
        fun currentDownloadedBytes(): Long =
            files.sumOf { file ->
                val local = File(tmpDir, file.path)
                if (file.size > 0L) min(local.length(), file.size) else local.length()
            }

        files.forEach { file ->
            val target = File(tmpDir, file.path).canonicalFile
            require(target.path.startsWith(tmpDir.canonicalPath)) {
                "Model repository contains an unsafe path."
            }
            target.parentFile?.mkdirs()
            if (file.size > 0L && target.length() > file.size) {
                target.delete()
            }
            if (file.size <= 0L || target.length() < file.size) {
                downloadWithResume(
                    url = modelScopeDownloadUrl(spec.modelScopeRepoId, file.path),
                    target = target,
                    expectedBytes = file.size.takeIf { it > 0L }
                ) { fileBytes ->
                    val current = currentDownloadedBytes() - target.length() + fileBytes
                    onProgress((current.toFloat() / totalBytes).coerceIn(0f, 1f))
                }
            }
            onProgress((currentDownloadedBytes().toFloat() / totalBytes).coerceIn(0f, 1f))
        }

        if (targetDir.exists()) targetDir.deleteRecursively()
        require(tmpDir.renameTo(targetDir)) { "Could not move downloaded model into place." }
        onProgress(1f)
    }

    private fun downloadAndExtractArchive(spec: TtsModelSpec, onProgress: (Float) -> Unit) {
        val targetDir = modelDir(spec)
        val tmpDir = File(paths.models, "${spec.directoryName}.tmp")

        val archive = File(paths.downloads, spec.archiveName)
        // Reserve the last 10% of the progress bar for extraction/move.
        downloadWithResume(spec.downloadUrl, archive, expectedBytes = null) { fileBytes ->
            val total = spec.sizeBytes.takeIf { it > 0L } ?: archive.length().coerceAtLeast(1L)
            onProgress((fileBytes.toFloat() / total).coerceIn(0f, 1f) * 0.9f)
        }
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()
        extractTarBz2(archive, tmpDir)
        onProgress(0.95f)

        // Release archives commonly wrap everything in a single top-level folder; flatten it so the
        // model files land directly under <models>/<directoryName>/.
        val entries = tmpDir.listFiles()?.toList().orEmpty()
        val extractedRoot = if (entries.size == 1 && entries.first().isDirectory) entries.first() else tmpDir

        if (targetDir.exists()) targetDir.deleteRecursively()
        if (!extractedRoot.renameTo(targetDir)) {
            // Cross-directory rename can fail; fall back to a recursive copy.
            extractedRoot.copyRecursively(targetDir, overwrite = true)
        }
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        archive.delete()
        onProgress(1f)
    }

    private fun modelScopeFileMetadata(repoId: String): List<ModelScopeFile> {
        val visited = mutableSetOf<String>()
        val files = mutableListOf<ModelScopeFile>()

        fun collect(root: String = "") {
            if (!visited.add(root)) return
            val rootQuery = root.takeIf(String::isNotBlank)?.let {
                "&Root=${URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")}"
            }.orEmpty()
            val apiUrl = "https://www.modelscope.cn/api/v1/models/$repoId/repo/files?Revision=master$rootQuery"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val entries = JSONObject(body).optJSONObject("Data")?.optJSONArray("Files") ?: JSONArray()
            (0 until entries.length()).forEach { i ->
                val item = entries.optJSONObject(i) ?: return@forEach
                val path = item.optString("Path").takeIf(String::isNotBlank) ?: return@forEach
                val size = item.optLong("Size", -1L)
                if (size == 0L && !path.substringAfterLast("/").contains(".")) {
                    collect(path)
                } else {
                    files += ModelScopeFile(path = path, size = size)
                }
            }
        }

        collect()
        return files.distinctBy { it.path }
    }

    private fun modelScopeDownloadUrl(repoId: String, relativePath: String): String {
        val encodedPath = URLEncoder.encode(relativePath, Charsets.UTF_8.name()).replace("+", "%20")
        return "https://www.modelscope.cn/api/v1/models/$repoId/repo?Revision=master&FilePath=$encodedPath"
    }

    private fun downloadWithResume(
        url: String,
        target: File,
        expectedBytes: Long?,
        onProgress: (Long) -> Unit
    ) {
        var attempt = 0
        while (true) {
            try {
                downloadOnceWithResume(url, target, expectedBytes, onProgress)
                if (expectedBytes != null && target.length() != expectedBytes) {
                    error("Downloaded file size mismatch: expected $expectedBytes bytes, got ${target.length()}.")
                }
                return
            } catch (error: Exception) {
                attempt += 1
                if (attempt >= DOWNLOAD_RETRY_COUNT) throw error
                Thread.sleep(DOWNLOAD_RETRY_DELAY_MS * attempt)
            }
        }
    }

    private fun downloadOnceWithResume(
        url: String,
        target: File,
        expectedBytes: Long?,
        onProgress: (Long) -> Unit
    ) {
        val resumeFrom = target.length().takeIf { it > 0L && (expectedBytes == null || it < expectedBytes) } ?: 0L
        if (expectedBytes != null && target.length() == expectedBytes) {
            onProgress(target.length())
            return
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
        }
        val responseCode = connection.responseCode
        if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && target.length() > 0L) {
            onProgress(target.length())
            return
        }
        if (responseCode !in 200..299) {
            error("Download failed with HTTP $responseCode.")
        }
        val append = resumeFrom > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
        if (resumeFrom > 0L && !append) {
            target.delete()
        }

        var copied = if (append) resumeFrom else 0L
        connection.inputStream.use { input ->
            FileOutputStream(target, append).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress(copied)
                }
            }
        }
        onProgress(target.length())
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
            .setContentTitle("Downloading voice model $progress%")
            .setContentText("$modelName - $progress%")
            .setSubText("$progress%")
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

    // A model counts as installed only when (a) a working engine is registered for its engineId and
    // (b) every file that engine needs is present on disk. There is no hard-coded allow-list flag.
    private fun isInstalled(spec: TtsModelSpec): Boolean {
        val factory = TtsEngineRegistry.factory(spec.engineId) ?: return false
        if (!factory.isImplemented) return false
        val dir = modelDir(spec)
        return factory.requiredFiles(spec).all { File(dir, it).isFile }
    }

    private fun isDownloaded(spec: TtsModelSpec): Boolean =
        File(modelDir(spec), DOWNLOAD_MARKER).isFile

    private data class ModelScopeFile(
        val path: String,
        val size: Long
    )

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val DOWNLOAD_MARKER = ".download-complete.json"
        private const val DOWNLOAD_RETRY_COUNT = 3
        private const val DOWNLOAD_RETRY_DELAY_MS = 1_500L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        val MODELS = listOf(
            // Default, fully working offline voice. End-to-end VITS (MeloTTS zh_en) on sherpa-onnx.
            // Distributed by the sherpa-onnx project as a single .tar.bz2 release asset.
            TtsModelSpec(
                id = "vits-melo-tts-zh-en",
                name = "MeloTTS 中文/英文 (VITS)",
                description = "端到端离线中文/英文朗读嗓音，手机实时、体积小，听书默认推荐。基于 sherpa-onnx + ONNX Runtime Mobile。",
                engineId = "sherpa-vits",
                runtime = "sherpa-onnx VITS",
                directoryName = "vits-melo-tts-zh_en",
                archiveName = "vits-melo-tts-zh_en.tar.bz2",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2",
                modelScopeRepoId = "",
                downloadStrategy = DownloadStrategy.ARCHIVE_TAR_BZ2,
                downloadFiles = emptyList(),
                sizeBytes = 174_000_000L,
                engineParams = mapOf(
                    "modelName" to "model.onnx",
                    "lexicon" to "lexicon.txt",
                    "tokens" to "tokens.txt",
                    "dictDir" to "dict",
                    "ruleFsts" to "date.fst,number.fst,phone.fst"
                )
            ),
            // --- Pluggable slots for the high-quality target models (engines pending). ---
            // Selectable + downloadable now; playback stays disabled until each engine ships.
            TtsModelSpec(
                id = "cosyvoice2-onnx",
                name = "CosyVoice2-0.5B ONNX",
                description = "高音质中文目标模型（LLM+flow+vocoder，zero-shot 克隆）。已切到 ModelScope 官方完整仓库，端侧推理引擎开发中。",
                engineId = "cosyvoice2",
                runtime = "ONNX Runtime Mobile（开发中）",
                directoryName = "CosyVoice2-0.5B_ONNX",
                archiveName = "CosyVoice2-0.5B_ONNX.zip",
                downloadUrl = "https://www.modelscope.cn/models/iic/CosyVoice2-0.5B",
                modelScopeRepoId = "iic/CosyVoice2-0.5B",
                downloadFiles = listOf(
                    "configuration.json",
                    "cosyvoice2.yaml",
                    "campplus.onnx",
                    "flow.decoder.estimator.fp32.onnx",
                    "speech_tokenizer_v2.onnx",
                    "speech_tokenizer_v2.batch.onnx",
                    "llm.pt",
                    "flow.pt",
                    "hift.pt",
                    "CosyVoice-BlankEN/config.json",
                    "CosyVoice-BlankEN/generation_config.json",
                    "CosyVoice-BlankEN/merges.txt",
                    "CosyVoice-BlankEN/model.safetensors",
                    "CosyVoice-BlankEN/tokenizer_config.json",
                    "CosyVoice-BlankEN/vocab.json"
                ),
                sizeBytes = 4_858_975_634L
            ),
            TtsModelSpec(
                id = "cosyvoice3-onnx",
                name = "Fun-CosyVoice3-0.5B ONNX",
                description = "更新一代 CosyVoice，目标音质更好。端侧自回归推理引擎开发中。",
                engineId = "cosyvoice3",
                runtime = "ONNX Runtime Mobile（开发中）",
                directoryName = "Fun-CosyVoice3-0.5B-ONNX",
                archiveName = "cosy-voice3-onnx.zip",
                downloadUrl = "https://www.modelscope.cn/models/FunAudioLLM/Fun-CosyVoice3-0.5B-2512",
                modelScopeRepoId = "FunAudioLLM/Fun-CosyVoice3-0.5B-2512",
                downloadFiles = listOf(
                    "configuration.json",
                    "cosyvoice3.yaml",
                    "campplus.onnx",
                    "flow.decoder.estimator.fp32.onnx",
                    "speech_tokenizer_v3.onnx"
                ),
                sizeBytes = 47L + 6_934L + 28_303_423L + 1_326_216_933L + 969_451_503L
            ),
            TtsModelSpec(
                id = "fish-speech-1.5",
                name = "Fish Speech 1.5",
                description = "带情感的目标模型，权重为 PyTorch，需先转换/量化为 ONNX 才能移动端运行。",
                engineId = "fish-speech",
                runtime = "待转换 ONNX（开发中）",
                directoryName = "fish-speech-1.5",
                archiveName = "fish-speech-1.5.zip",
                downloadUrl = "https://www.modelscope.cn/models/fishaudio/fish-speech-1.5",
                modelScopeRepoId = "fishaudio/fish-speech-1.5",
                downloadFiles = listOf(
                    "config.json",
                    "firefly-gan-vq-fsq-8x1024-21hz-generator.pth",
                    "model.pth",
                    "special_tokens.json",
                    "tokenizer.tiktoken"
                ),
                sizeBytes = 697L + 188_518_579L + 1_275_908_030L + 30_964L + 1_698_810L
            )
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.2f GB", gb)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
