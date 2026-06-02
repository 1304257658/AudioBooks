package com.example.audiobooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.tts.engine.TtsEngineRegistry
import kotlin.math.roundToInt

// ===========================================================================
// LIBRARY
// ===========================================================================

@Composable
internal fun LibraryScreen(
    state: AudioBookUiState,
    voiceLabel: String,
    onOpenBook: (AudioBook) -> Unit,
    onOpenNow: () -> Unit,
    onToggle: () -> Unit,
    onImport: () -> Unit
) {
    val c = LocalAppColors.current
    var filter by remember { mutableStateOf("all") }
    val filters = listOf("all" to "全部", "reading" to "在读", "done" to "已听完", "new" to "未读")
    val books = state.books
    val cont = state.selectedBook
        ?: books.firstOrNull { it.isStarted() && !it.isFinished() }
        ?: books.firstOrNull()

    val shown = books.filter { b ->
        when (filter) {
            "reading" -> b.isStarted() && !b.isFinished()
            "done" -> b.isFinished()
            "new" -> !b.isStarted()
            else -> true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 156.dp)
    ) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Dim.pad, end = Dim.pad, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AppIcon(AppIcons.offline, size = 13.dp, tint = c.accent)
                    Text("离线 · 端侧 AI 朗读", style = AppText.eyebrow.copy(color = c.accent))
                }
                Spacer(Modifier.height(6.dp))
                Text("书房", style = AppText.h1)
            }
            RoundIconButton(AppIcons.plus, onImport, boxSize = 46.dp, bordered = true)
        }

        // continue listening hero
        if (cont != null) {
            Row(
                Modifier
                    .padding(start = Dim.pad, end = Dim.pad, top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dim.rL))
                    .background(c.surface)
                    .border(1.dp, c.hairline, RoundedCornerShape(Dim.rL))
                    .clickable(onClick = onOpenNow)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BookCover(cont, width = 92.dp)
                Column(Modifier.weight(1f)) {
                    Text("继续收听", style = AppText.eyebrow)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        cont.title,
                        style = AppText.h2.copy(fontSize = 19.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    OnDeviceBadge("$voiceLabel · 第 ${cont.chapterOf(cont.currentSentenceIndex) + 1} 章")
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayCircle(
                            playing = state.isPlaying && state.selectedBook?.id == cont.id,
                            onClick = { onOpenNow(); onToggle() },
                            diameter = 48.dp,
                            iconSize = 22.dp
                        )
                        Column(Modifier.weight(1f)) {
                            ProgressLine(cont.progressFraction())
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${(cont.progressFraction() * 100).roundToInt()}% · 共 ${cont.chapters.size} 章",
                                style = AppText.meta.copy(fontSize = 11.5.sp)
                            )
                        }
                    }
                }
            }
        }

        // filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = Dim.pad, end = Dim.pad, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (id, lbl) ->
                Chip(lbl, selected = filter == id, onClick = { filter = id })
            }
        }

        if (books.isEmpty()) {
            EmptyLibrary(onImport)
        } else {
            // 2-column grid
            val screenW = LocalConfiguration.current.screenWidthDp.dp
            val coverW = (screenW - Dim.pad * 2 - Dim.gap) / 2
            val rows = shown.chunked(2)
            Column(
                Modifier.padding(horizontal = Dim.pad),
                verticalArrangement = Arrangement.spacedBy(Dim.gap)
            ) {
                rows.forEach { rowBooks ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                        rowBooks.forEach { b ->
                            GridBook(b, coverW, Modifier.weight(1f), onOpenBook)
                        }
                        if (rowBooks.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GridBook(book: AudioBook, coverW: androidx.compose.ui.unit.Dp, modifier: Modifier, onOpen: (AudioBook) -> Unit) {
    val c = LocalAppColors.current
    Column(modifier.clickable { onOpen(book) }) {
        Box {
            BookCover(book, width = coverW)
            if (book.isFinished()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center
                ) { AppIcon(AppIcons.check, size = 14.dp, tint = c.accentInk) }
            } else if (book.progressFraction() > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x1F000000))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(book.progressFraction())
                            .height(3.dp)
                            .background(c.accent)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = AppText.title.copy(fontSize = 14.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(book.author ?: "本地导入", style = AppText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dim.pad, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(AppIcons.library, size = 36.dp, tint = c.ink3)
        Text("书房还是空的", style = AppText.h2)
        Text(
            "导入 EPUB · TXT · PDF，端侧 AI 将为你朗读，内容不出本机。",
            style = AppText.body,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        PrimaryButton("添加书籍", onImport, leadingIcon = AppIcons.plus)
    }
}

@Composable
internal fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.hairline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c.accent)
        )
    }
}

// ===========================================================================
// SEARCH (library-level, client side over imported books)
// ===========================================================================

@Composable
internal fun SearchScreen(
    state: AudioBookUiState,
    onOpenBook: (AudioBook) -> Unit
) {
    val c = LocalAppColors.current
    var q by remember { mutableStateOf("") }
    val results = if (q.isBlank()) emptyList() else state.books.filter {
        (it.title + (it.author ?: "")).contains(q, ignoreCase = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 156.dp)
    ) {
        Box(Modifier.padding(start = Dim.pad, end = Dim.pad, top = 8.dp, bottom = 4.dp)) {
            Text("搜索", style = AppText.h1)
        }
        // search field
        Row(
            Modifier
                .padding(start = Dim.pad, end = Dim.pad, top = 12.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.surface)
                .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(AppIcons.search, size = 20.dp, tint = c.ink3)
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("书名 · 作者", style = AppText.body.copy(color = c.ink3))
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = AppText.body.copy(color = c.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (q.isNotEmpty()) RoundIconButton(AppIcons.close, { q = "" }, boxSize = 30.dp, iconSize = 18.dp, tint = c.ink3)
        }

        if (q.isEmpty()) {
            Column(Modifier.padding(Dim.pad)) {
                Text("书房里的书", style = AppText.eyebrow)
                Spacer(Modifier.height(12.dp))
                state.books.forEachIndexed { i, b ->
                    if (i > 0) AppDivider()
                    SearchRow(b) { onOpenBook(b) }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = Dim.pad, vertical = 8.dp)) {
                if (results.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIcon(AppIcons.search, size = 32.dp, tint = c.ink3)
                        Spacer(Modifier.height(12.dp))
                        Text("没有找到「$q」", style = AppText.body)
                    }
                } else {
                    results.forEachIndexed { i, b ->
                        if (i > 0) AppDivider()
                        SearchRow(b) { onOpenBook(b) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(b: AudioBook, onOpen: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BookCover(b, width = 44.dp, showText = false)
        Column(Modifier.weight(1f)) {
            Text(b.title, style = AppText.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("${b.author ?: "本地导入"} · ${b.totalSentences} 句", style = AppText.meta)
        }
        if (b.isStarted()) OnDeviceBadge("在读") else AppIcon(AppIcons.chevR, size = 18.dp, tint = c.ink3)
    }
}

// ===========================================================================
// SETTINGS
// ===========================================================================

@Composable
internal fun SettingsScreen(
    state: AudioBookUiState,
    dark: Boolean,
    serif: Boolean,
    voiceLabel: String,
    onDarkChange: (Boolean) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onRateChange: (Float) -> Unit,
    onLanguageChange: (String) -> Unit,
    onManageModels: () -> Unit
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 156.dp)
    ) {
        Box(Modifier.padding(start = Dim.pad, end = Dim.pad, top = 8.dp, bottom = 4.dp)) {
            Text("设置", style = AppText.h1)
        }

        SettingsGroup("播放") {
            SettingRow(AppIcons.speed, "默认倍速", "新书初始语速") {
                Stepper(
                    value = state.speechRate,
                    onChange = onRateChange,
                    min = 0.5f, max = 3f, step = 0.25f, unit = "×"
                )
            }
            AppDivider()
            SettingRow(AppIcons.voice, "界面语言", "界面文字语言", last = true) {
                Segmented(
                    options = listOf("zh" to "中", "ja" to "日", "en" to "EN"),
                    selected = state.readerSettings.language,
                    onSelect = onLanguageChange,
                    modifier = Modifier.width(150.dp)
                )
            }
        }

        SettingsGroup("端侧 AI 音色 / 模型") {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onManageModels).padding(Dim.pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingIcon(AppIcons.chip)
                Column(Modifier.weight(1f)) {
                    Text("默认音色 / 模型", style = AppText.title.copy(fontSize = 15.sp))
                    Spacer(Modifier.height(2.dp))
                    Text(voiceLabel, style = AppText.meta)
                }
                AppIcon(AppIcons.chevR, size = 18.dp, tint = c.ink3)
            }
            AppDivider()
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onManageModels).padding(Dim.pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingIcon(AppIcons.download)
                Column(Modifier.weight(1f)) {
                    Text("管理模型", style = AppText.title.copy(fontSize = 15.sp))
                    Spacer(Modifier.height(2.dp))
                    Text("已下载 ${state.downloadedModelIds.size} 个 · 可播放 ${state.installedModelIds.size} 个", style = AppText.meta)
                }
                AppIcon(AppIcons.chevR, size = 18.dp, tint = c.ink3)
            }
        }

        SettingsGroup("外观") {
            SettingRow(if (dark) AppIcons.moon else AppIcons.sun, "深色主题", "夜间护眼阅读") {
                AppSwitch(dark) { onDarkChange(!dark) }
            }
            AppDivider()
            SettingRow(AppIcons.type, "正文字体", "界面与书名字体", last = true) {
                Segmented(
                    options = listOf("sans" to "黑体", "serif" to "宋体"),
                    selected = if (serif) "serif" else "sans",
                    onSelect = { onSerifChange(it == "serif") },
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        SettingsGroup("存储与隐私") {
            Column(Modifier.padding(Dim.pad)) {
                Text("数据目录", style = AppText.title.copy(fontSize = 15.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    state.dataRootPath.ifBlank { "不可用" },
                    style = AppText.meta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AppDivider()
            SettingRow(AppIcons.offline, "完全离线运行", "所有推理在本机完成，内容不上传", last = true) {
                OnDeviceBadge("已保障")
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("念 Aloud · 离线听书", style = AppText.meta.copy(color = c.ink3))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, style = AppText.eyebrow, modifier = Modifier.padding(start = Dim.pad, end = Dim.pad, bottom = 8.dp))
        Column(
            Modifier
                .padding(horizontal = Dim.pad)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dim.rM))
                .background(c.surface)
                .border(1.dp, c.hairline, RoundedCornerShape(Dim.rM))
        ) { content() }
    }
}

@Composable
private fun SettingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = LocalAppColors.current
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c.accentWash),
        contentAlignment = Alignment.Center
    ) { AppIcon(icon, size = 18.dp, tint = c.accent) }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String?,
    last: Boolean = false,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(Dim.pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(label, style = AppText.title.copy(fontSize = 15.sp))
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = AppText.meta)
            }
        }
        trailing()
    }
}

@Composable
private fun Stepper(value: Float, onChange: (Float) -> Unit, min: Float, max: Float, step: Float, unit: String) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(AppIcons.chevL, { onChange((value - step).coerceAtLeast(min)) }, boxSize = 32.dp, iconSize = 16.dp)
        Text(
            "${formatRate(value)}$unit",
            style = AppText.title.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.width(48.dp),
            maxLines = 1
        )
        RoundIconButton(AppIcons.chevR, { onChange((value + step).coerceAtMost(max)) }, boxSize = 32.dp, iconSize = 16.dp)
    }
}

internal fun formatRate(v: Float): String {
    val rounded = (v * 100).roundToInt() / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}

// ===========================================================================
// MODEL MANAGEMENT (real models, shown inside a bottom sheet)
// ===========================================================================

@Composable
internal fun ModelListContent(
    state: AudioBookUiState,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dim.pad)
            .padding(bottom = Dim.pad)
    ) {
        Text(
            "切换默认音色 / 模型。仅已实现且已下载的模型可即时播放；其余可下载，等待端侧推理引擎接入。",
            style = AppText.meta.copy(color = c.ink2),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        state.models.forEach { model ->
            val selected = model.id == state.selectedModelId
            val downloaded = model.id in state.downloadedModelIds
            val installed = model.id in state.installedModelIds
            val implemented = TtsEngineRegistry.isImplemented(model.engineId)
            val progress = state.modelDownloadProgressById[model.id]
            val hue = hueFromString(model.name)

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(Dim.rM))
                    .background(if (selected) c.accentWash else c.surface)
                    .border(
                        1.dp,
                        if (selected) c.accent.copy(alpha = 0.45f) else c.hairline,
                        RoundedCornerShape(Dim.rM)
                    )
                    .clickable { onSelectModel(model.id) }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.hsl(hue, 0.5f, 0.85f)),
                        contentAlignment = Alignment.Center
                    ) { AppIcon(AppIcons.voice, size = 19.dp, tint = Color.hsl(hue, 0.5f, 0.40f)) }
                    Column(Modifier.weight(1f)) {
                        Text(model.name, style = AppText.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        val status = when {
                            installed -> "可播放"
                            !implemented -> "引擎开发中"
                            downloaded -> "已下载"
                            else -> "未下载"
                        }
                        Text("${model.runtime} · ${model.sizeLabel} · $status", style = AppText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) {
                        Box(
                            Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accent),
                            contentAlignment = Alignment.Center
                        ) { AppIcon(AppIcons.check, size = 15.dp, tint = c.accentInk) }
                    } else {
                        Box(
                            Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .border(1.5.dp, c.hairline2, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(model.description, style = AppText.meta.copy(color = c.ink2), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (progress != null) {
                        Box(Modifier.weight(1f)) {
                            GenBar(progress)
                        }
                        Text("${(progress * 100).roundToInt()}%", style = AppText.meta.copy(fontWeight = FontWeight.SemiBold))
                    } else {
                        if (downloaded) {
                            GhostButton("删除", { onDeleteModel(model.id) }, leadingIcon = AppIcons.trash)
                        } else {
                            PrimaryButton("下载", { onDownloadModel(model.id) }, leadingIcon = AppIcons.download)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GenBar(progress: Float, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.accent)
        )
    }
}
