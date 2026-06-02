package com.example.audiobooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobooks.model.AudioBook
import kotlin.math.roundToInt

// ===========================================================================
// Overlay header
// ===========================================================================

@Composable
internal fun OverlayHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    backIcon: ImageVector = AppIcons.chevD,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = Dim.pad, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoundIconButton(backIcon, onBack, iconSize = 24.dp)
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(eyebrow, style = AppText.eyebrow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = AppText.h2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
    }
}

// ===========================================================================
// PLAYER OVERLAY
// ===========================================================================

@Composable
internal fun PlayerOverlay(
    state: AudioBookUiState,
    voiceShort: String,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onJumpBy: (Int) -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenReader: () -> Unit,
    onDownloadHint: () -> Unit
) {
    val c = LocalAppColors.current
    val book = state.selectedBook ?: return
    val chapters = book.chapters
    val idx = state.currentSentenceIndex
    val curCh = book.chapterOf(idx)
    val ch = chapters.getOrNull(curCh)
    val span = if (ch != null) (ch.endSentenceIndex - ch.startSentenceIndex) else 0
    val within = if (ch != null && span > 0) ((idx - ch.startSentenceIndex).toFloat() / span).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        // top bar: back / listen-read toggle / chapters
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(AppIcons.chevD, onClose, iconSize = 26.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ListenReadToggle(mode = "listen", onMode = { if (it == "read") onOpenReader() })
            }
            RoundIconButton(AppIcons.list, onOpenChapters, iconSize = 22.dp)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            BookCover(book, width = 236.dp)
            Spacer(Modifier.height(22.dp))
            Text(book.title, style = AppText.h1.copy(fontSize = 27.sp), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(book.author ?: "本地导入", style = AppText.meta, textAlign = TextAlign.Center)

            Spacer(Modifier.height(16.dp))
            // voice chip
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.accentWash)
                    .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenVoice)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(AppIcons.voice, size = 15.dp, tint = c.accent)
                Text(voiceShort, style = AppText.meta.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent), maxLines = 1)
                Box(Modifier.width(1.dp).height(12.dp).background(c.accent.copy(alpha = 0.3f)))
                OnDeviceBadge("端侧 AI")
            }

            if (!state.modelInstalled) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dim.rM))
                        .background(c.accentWash)
                        .clickable(onClick = onDownloadHint)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIcon(AppIcons.download, size = 20.dp, tint = c.accent)
                    Text("需要先下载语音模型才能播放，点此前往", style = AppText.meta.copy(color = c.ink2), modifier = Modifier.weight(1f))
                    AppIcon(AppIcons.chevR, size = 16.dp, tint = c.accent)
                }
            }

            Spacer(Modifier.height(26.dp))
            // waveform
            Waveform(progress = within, live = state.isPlaying, height = 46.dp) { f ->
                if (ch != null) onJumpTo(ch.startSentenceIndex + (f * span).roundToInt())
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${idx + 1}", style = AppText.meta)
                Text(ch?.title ?: "", style = AppText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Center)
                Text("${book.totalSentences}", style = AppText.meta)
            }

            Spacer(Modifier.height(24.dp))
            // transport
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(AppIcons.prev, {
                    chapters.getOrNull(curCh - 1)?.let { onJumpTo(it.startSentenceIndex) }
                }, boxSize = 50.dp, iconSize = 26.dp)
                RoundIconButton(AppIcons.back15, { onJumpBy(-1) }, boxSize = 52.dp, iconSize = 30.dp)
                PlayCircle(playing = state.isPlaying, onClick = onToggle, enabled = state.modelInstalled)
                RoundIconButton(AppIcons.fwd15, { onJumpBy(1) }, boxSize = 52.dp, iconSize = 30.dp)
                RoundIconButton(AppIcons.next, {
                    chapters.getOrNull(curCh + 1)?.let { onJumpTo(it.startSentenceIndex) }
                }, boxSize = 50.dp, iconSize = 26.dp)
            }
            Spacer(Modifier.height(20.dp))
        }

        // bottom utility bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            UtilButton(AppIcons.speed, "${formatRate(state.speechRate)}×", onOpenSpeed, Modifier.weight(1f))
            UtilButton(AppIcons.timer, state.readerSettings.sleepTimerLabel ?: "定时", onOpenSleep, Modifier.weight(1f),
                tint = if (state.readerSettings.sleepTimerLabel != null) c.accent else c.ink2)
            UtilButton(AppIcons.list, "章节", onOpenChapters, Modifier.weight(1f))
            UtilButton(AppIcons.chip, "音色", onOpenVoice, Modifier.weight(1f))
        }
    }
}

@Composable
private fun UtilButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalAppColors.current.ink2) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppIcon(icon, size = 20.dp, tint = tint)
        Text(label, style = AppText.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tint), maxLines = 1)
    }
}

// ===========================================================================
// CHAPTERS OVERLAY
// ===========================================================================

@Composable
internal fun ChaptersOverlay(
    book: AudioBook,
    currentSentenceIndex: Int,
    onClose: () -> Unit,
    onPlayChapter: (Int) -> Unit
) {
    val c = LocalAppColors.current
    val curCh = book.chapterOf(currentSentenceIndex)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        OverlayHeader(title = "章节", eyebrow = book.title, onBack = onClose)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            Row(
                Modifier
                    .padding(start = Dim.pad, end = Dim.pad, top = 4.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dim.rM))
                    .background(c.accentWash)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(AppIcons.offline, size = 20.dp, tint = c.accent)
                Text("音频由端侧模型按需生成，已听过的章节可完全离线反复收听。", style = AppText.meta.copy(color = c.ink2))
            }
            book.chapters.forEach { chapter ->
                val isCur = chapter.index == curCh
                val done = chapter.endSentenceIndex < currentSentenceIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCur) c.accentWash else Color.Transparent)
                        .clickable { onPlayChapter(chapter.startSentenceIndex) }
                        .padding(horizontal = Dim.pad, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        if (isCur) {
                            Waveform(progress = 0f, live = true, height = 18.dp, bars = 4)
                        } else {
                            Text(
                                (chapter.index + 1).toString().padStart(2, '0'),
                                style = AppText.meta.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (done) c.ink3 else c.ink2)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            chapter.title,
                            style = AppText.title.copy(fontSize = 15.sp, color = if (isCur) c.accent else c.ink),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("句 ${chapter.startSentenceIndex + 1}–${chapter.endSentenceIndex + 1}", style = AppText.meta)
                    }
                    if (done && !isCur) AppIcon(AppIcons.check, size = 18.dp, tint = c.ink3)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ===========================================================================
// IMPORT OVERLAY
// ===========================================================================

@Composable
internal fun ImportOverlay(
    isBusy: Boolean,
    onClose: () -> Unit,
    onPickFile: () -> Unit,
    onPasteImport: (String, String) -> Unit
) {
    val c = LocalAppColors.current
    var stage by remember { mutableStateOf("pick") } // pick | paste
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        OverlayHeader(
            title = if (stage == "pick") "添加书籍" else "粘贴文本生成",
            onBack = { if (stage == "paste") stage = "pick" else onClose() }
        )

        if (stage == "pick") {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dim.pad)) {
                Text(
                    "导入本地文本，端侧 AI 将为你朗读 —— 无需联网，内容不出本机。",
                    style = AppText.body, modifier = Modifier.padding(vertical = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dim.rL))
                        .border(1.5.dp, c.hairline2, RoundedCornerShape(Dim.rL))
                        .clickable(onClick = onPickFile)
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accentWash),
                        contentAlignment = Alignment.Center
                    ) { AppIcon(AppIcons.file, size = 26.dp, tint = c.accent) }
                    Spacer(Modifier.height(12.dp))
                    Text("选择本地文件", style = AppText.title)
                    Spacer(Modifier.height(4.dp))
                    Text("EPUB · TXT · PDF", style = AppText.meta)
                }
                Spacer(Modifier.height(14.dp))
                GhostButton("粘贴文本生成", { stage = "paste" }, leadingIcon = AppIcons.paste, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                if (isBusy) BusyRow()
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dim.pad)) {
                Text("标题", style = AppText.eyebrow, modifier = Modifier.padding(vertical = 8.dp))
                FieldBox {
                    BasicTextField(
                        value = title, onValueChange = { title = it }, singleLine = true,
                        textStyle = AppText.body.copy(color = c.ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text("新书标题", style = AppText.body.copy(color = c.ink3))
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("正文", style = AppText.eyebrow, modifier = Modifier.padding(vertical = 8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(Dim.rM))
                        .background(c.surface)
                        .border(1.dp, c.hairline, RoundedCornerShape(Dim.rM))
                        .padding(14.dp)
                ) {
                    if (content.isEmpty()) Text("在此粘贴要朗读的文本…", style = AppText.body.copy(color = c.ink3))
                    BasicTextField(
                        value = content, onValueChange = { content = it },
                        textStyle = AppText.body.copy(color = c.ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    "开始生成并收听",
                    onClick = { if (content.isNotBlank()) onPasteImport(title.ifBlank { "粘贴文本" }, content) },
                    leadingIcon = AppIcons.bolt,
                    enabled = content.isNotBlank() && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                if (isBusy) BusyRow()
            }
        }
    }
}

@Composable
private fun FieldBox(content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

@Composable
private fun BusyRow() {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
        Text("正在导入并解析…", style = AppText.meta)
    }
}
