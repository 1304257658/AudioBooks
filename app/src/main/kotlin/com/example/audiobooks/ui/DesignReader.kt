package com.example.audiobooks.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.audiobooks.model.AudioBook
import kotlin.math.roundToInt

private val FontSteps = listOf(0.9f, 1.05f, 1.25f, 1.45f)

// ===========================================================================
// Shared listen/read toggle
// ===========================================================================

@Composable
internal fun ListenReadToggle(mode: String, onMode: (String) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("listen" to ("听书" to AppIcons.bookopen), "read" to ("阅读" to AppIcons.type)).forEach { (value, p) ->
            val (label, icon) = p
            val on = value == mode
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable { onMode(value) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppIcon(icon, size = 16.dp, tint = if (on) c.accent else c.ink3)
                Text(label, style = AppText.meta.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.accent else c.ink3))
            }
        }
    }
}

// ===========================================================================
// READER OVERLAY
// ===========================================================================

@Composable
internal fun ReaderOverlay(
    state: AudioBookUiState,
    onClose: () -> Unit,
    onModeListen: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onJumpBy: (Int) -> Unit,
    onToggle: () -> Unit,
    onOpenSearch: () -> Unit,
    onAddAnnotation: (Int) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: () -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onResetReaderSettings: () -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onMarkRepeatStart: () -> Unit,
    onMarkRepeatEnd: () -> Unit,
    onClearRepeatRange: () -> Unit,
    onStartSleepTimer: (Int?) -> Unit,
    onRateChange: (Float) -> Unit
) {
    val c = LocalAppColors.current
    val book = state.selectedBook ?: return
    val settings = state.readerSettings
    val idx = state.currentSentenceIndex
    val curCh = book.chapterOf(idx)
    val ch = book.chapters.getOrNull(curCh)
    var layout by remember { mutableStateOf("text") } // text | original
    var showTools by remember { mutableStateOf(false) }

    val bg = when (settings.background) {
        "paper" -> Color(0xFFF3ECD8)
        "black" -> Color(0xFF0A0A0A)
        else -> c.bg
    }
    val textColor = when (settings.background) {
        "paper" -> Color(0xFF2B2620)
        "black" -> Color(0xFFE9E4DA)
        else -> c.ink2
    }
    val activeColor = when (settings.background) {
        "black" -> Color(0xFFFFFFFF)
        else -> c.ink
    }

    Column(Modifier.fillMaxSize().background(bg)) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RoundIconButton(AppIcons.chevD, onClose, iconSize = 26.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ListenReadToggle(mode = "read", onMode = { if (it == "listen") onModeListen() })
            }
            RoundIconButton(AppIcons.settings, { showTools = true }, iconSize = 21.dp)
            RoundIconButton(AppIcons.search, onOpenSearch, iconSize = 21.dp)
            RoundIconButton(AppIcons.type, {
                val cur = FontSteps.minByOrNull { kotlin.math.abs(it - settings.fontScale) } ?: 1f
                val next = FontSteps[(FontSteps.indexOf(cur) + 1) % FontSteps.size]
                onReaderSettingsChange(settings.copy(fontScale = next))
            }, iconSize = 22.dp)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (layout == "original" && state.readerHtmlUrl != null) {
                HtmlReader(state.readerHtmlUrl, Modifier.fillMaxSize())
            } else {
                val chSentences = if (ch != null) {
                    book.sentences.subList(ch.startSentenceIndex, (ch.endSentenceIndex + 1).coerceAtMost(book.sentences.size))
                } else book.sentences
                val activeLocal = (idx - (ch?.startSentenceIndex ?: 0)).coerceIn(0, (chSentences.size - 1).coerceAtLeast(0))
                val listState = rememberLazyListState()
                LaunchedEffect(idx, state.isPlaying) {
                    if (chSentences.isNotEmpty()) listState.animateScrollToItem(activeLocal.coerceIn(0, chSentences.lastIndex))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = Dim.pad)) {
                    item {
                        Column(Modifier.padding(top = 12.dp, bottom = 14.dp)) {
                            Text("${book.title} · 第 ${curCh + 1} 章", style = AppText.eyebrow)
                            Spacer(Modifier.height(6.dp))
                            Text(ch?.title ?: book.title, style = AppText.h2.copy(fontSize = 25.sp))
                            Spacer(Modifier.height(10.dp))
                            OnDeviceBadge("端侧 AI 朗读")
                            Spacer(Modifier.height(8.dp))
                            AppDivider()
                        }
                    }
                    items(chSentences.size) { i ->
                        val sentence = chSentences[i]
                        val active = sentence.index == idx
                        val annotated = book.annotations.any { it.sentenceIndex == sentence.index }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(topEnd = Dim.rS, bottomEnd = Dim.rS))
                                .background(if (active) c.accentWash else Color.Transparent)
                                .clickable { onJumpTo(sentence.index) }
                        ) {
                            Box(Modifier.width(2.5.dp).fillMaxHeight().background(if (active) c.accent else Color.Transparent))
                            Text(
                                sentence.text.replace(" ", ""),
                                style = AppText.body.copy(
                                    fontFamily = LocalAppFonts.current.serif,
                                    fontSize = (17 * settings.fontScale).sp,
                                    lineHeight = (17 * settings.fontScale * settings.lineSpacing * 1.35f).sp,
                                    color = if (active) activeColor else textColor,
                                    fontWeight = if (annotated) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(start = 14.dp, top = 9.dp, bottom = 9.dp, end = 4.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }

        // compact transport
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = Dim.pad, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoundIconButton(AppIcons.prev, { onJumpBy(-1) }, boxSize = 40.dp, iconSize = 22.dp)
            PlayCircle(playing = state.isPlaying, onClick = onToggle, diameter = 52.dp, iconSize = 22.dp, enabled = state.modelInstalled)
            RoundIconButton(AppIcons.next, { onJumpBy(1) }, boxSize = 40.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f)) {
                val total = book.totalSentences
                Text("第 ${idx + 1} / $total 句", style = AppText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                ProgressLine(book.progressFraction())
            }
        }
    }

    ReaderToolsSheet(
        visible = showTools,
        onClose = { showTools = false },
        state = state,
        layout = layout,
        onLayoutChange = { layout = it },
        onAddAnnotation = onAddAnnotation,
        onDeleteAnnotation = onDeleteAnnotation,
        onExportAnnotations = onExportAnnotations,
        onJumpTo = onJumpTo,
        onReaderSettingsChange = onReaderSettingsChange,
        onResetReaderSettings = onResetReaderSettings,
        onRepeatCountChange = onRepeatCountChange,
        onMarkRepeatStart = onMarkRepeatStart,
        onMarkRepeatEnd = onMarkRepeatEnd,
        onClearRepeatRange = onClearRepeatRange,
        onStartSleepTimer = onStartSleepTimer,
        onRateChange = onRateChange
    )
}

// ===========================================================================
// READER TOOLS SHEET (preserves all original reader features)
// ===========================================================================

@Composable
private fun ReaderToolsSheet(
    visible: Boolean,
    onClose: () -> Unit,
    state: AudioBookUiState,
    layout: String,
    onLayoutChange: (String) -> Unit,
    onAddAnnotation: (Int) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onExportAnnotations: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onResetReaderSettings: () -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onMarkRepeatStart: () -> Unit,
    onMarkRepeatEnd: () -> Unit,
    onClearRepeatRange: () -> Unit,
    onStartSleepTimer: (Int?) -> Unit,
    onRateChange: (Float) -> Unit
) {
    val c = LocalAppColors.current
    val book = state.selectedBook
    val settings = state.readerSettings
    AppBottomSheet(visible = visible, onClose = onClose, title = "阅读工具") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dim.pad)
        ) {
            // Speed
            ToolSection("语速 ${formatRate(state.speechRate)}×") {
                Slider(value = state.speechRate, onValueChange = onRateChange, valueRange = 0.5f..3f, steps = 9)
            }
            // Font + line spacing
            ToolSection("字号 ${formatRate(settings.fontScale)}×") {
                Slider(
                    value = settings.fontScale,
                    onValueChange = { onReaderSettingsChange(settings.copy(fontScale = it)) },
                    valueRange = 0.8f..1.6f, steps = 7
                )
            }
            ToolSection("行距 ${formatRate(settings.lineSpacing)}") {
                Slider(
                    value = settings.lineSpacing,
                    onValueChange = { onReaderSettingsChange(settings.copy(lineSpacing = it)) },
                    valueRange = 1.0f..2.0f, steps = 9
                )
            }
            // Background
            ToolSection("背景") {
                Segmented(
                    options = listOf("system" to "跟随", "paper" to "纸张", "black" to "夜间"),
                    selected = settings.background,
                    onSelect = { onReaderSettingsChange(settings.copy(background = it)) }
                )
            }
            // Layout mode (text vs original webview) only when original is available
            if (state.readerHtmlUrl != null) {
                ToolSection("排版") {
                    Segmented(
                        options = listOf("text" to "纯文本(同步)", "original" to "原始排版"),
                        selected = layout,
                        onSelect = onLayoutChange
                    )
                }
            }
            // Gestures
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("点按手势", style = AppText.title.copy(fontSize = 15.sp))
                    Text("点按句子播放/暂停", style = AppText.meta)
                }
                AppSwitch(settings.gesturesEnabled) {
                    onReaderSettingsChange(settings.copy(gesturesEnabled = !settings.gesturesEnabled))
                }
            }
            AppDivider()
            // Repeat
            ToolSection("单句复读 ${settings.repeatCount}×") {
                Slider(
                    value = settings.repeatCount.toFloat(),
                    onValueChange = { onRepeatCountChange(it.roundToInt()) },
                    valueRange = 1f..9f, steps = 7
                )
            }
            // A-B repeat
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "A-B 复读：${settings.abStart?.plus(1)?.toString() ?: "-"} → ${settings.abEnd?.plus(1)?.toString() ?: "-"}",
                    style = AppText.title.copy(fontSize = 15.sp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("设 A", onMarkRepeatStart, modifier = Modifier.weight(1f))
                    GhostButton("设 B", onMarkRepeatEnd, modifier = Modifier.weight(1f))
                    GhostButton("清除", onClearRepeatRange, modifier = Modifier.weight(1f))
                }
            }
            AppDivider()
            // Sleep timer
            ToolSection("睡眠定时") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        Box(Modifier.weight(1f)) {
                            Chip("${m}分", selected = settings.sleepTimerLabel == "${m}m", onClick = { onStartSleepTimer(m) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        Chip("本章", selected = settings.sleepTimerLabel == "chapter", onClick = { onStartSleepTimer(-1) }, modifier = Modifier.fillMaxWidth())
                    }
                    Box(Modifier.weight(1f)) {
                        Chip("关", selected = settings.sleepTimerLabel == null, onClick = { onStartSleepTimer(null) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            AppDivider()
            // Bookmarks
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("书签", style = AppText.title.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    GhostButton("收藏当前句", { onAddAnnotation(state.currentSentenceIndex) })
                }
                Spacer(Modifier.height(8.dp))
                val annotations = book?.annotations.orEmpty()
                if (annotations.isEmpty()) {
                    Text("还没有书签。", style = AppText.meta)
                } else {
                    annotations.take(12).forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${a.sentenceIndex + 1}. ${book?.sentences?.getOrNull(a.sentenceIndex)?.text.orEmpty()}",
                                style = AppText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { onJumpTo(a.sentenceIndex); onClose() }
                            )
                            RoundIconButton(AppIcons.trash, { onDeleteAnnotation(a.id) }, boxSize = 32.dp, iconSize = 16.dp, tint = c.ink3)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton("导出书签 Markdown", onExportAnnotations, leadingIcon = AppIcons.download, modifier = Modifier.fillMaxWidth())
                }
            }
            AppDivider()
            Spacer(Modifier.height(10.dp))
            GhostButton("重置阅读设置", onResetReaderSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToolSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = AppText.title.copy(fontSize = 15.sp))
        Spacer(Modifier.height(6.dp))
        content()
    }
}

// ===========================================================================
// IN-BOOK SEARCH OVERLAY
// ===========================================================================

@Composable
internal fun InBookSearchOverlay(
    state: AudioBookUiState,
    onClose: () -> Unit,
    onSearchChange: (String) -> Unit,
    onJump: (Int) -> Unit
) {
    val c = LocalAppColors.current
    val book = state.selectedBook ?: return
    val q = state.searchQuery
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = Dim.pad, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoundIconButton(AppIcons.chevL, onClose, iconSize = 24.dp)
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.surface)
                    .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(AppIcons.search, size = 19.dp, tint = c.ink3)
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("在《${book.title}》中查找", style = AppText.body.copy(color = c.ink3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = q, onValueChange = onSearchChange, singleLine = true,
                        textStyle = AppText.body.copy(color = c.ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (q.isNotEmpty()) RoundIconButton(AppIcons.close, { onSearchChange("") }, boxSize = 28.dp, iconSize = 17.dp, tint = c.ink3)
            }
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (q.isBlank()) {
                Text(
                    "搜索全书正文，直接定位到匹配句子 —— 与朗读位置同步。",
                    style = AppText.meta.copy(color = c.ink2),
                    modifier = Modifier.padding(horizontal = Dim.pad, vertical = 8.dp)
                )
            } else {
                val results = state.searchResults
                Text(
                    if (results.isNotEmpty()) "找到 ${results.size} 处「$q」" else "未找到「$q」",
                    style = AppText.meta,
                    modifier = Modifier.padding(horizontal = Dim.pad, vertical = 8.dp)
                )
                results.forEachIndexed { i, sentenceIndex ->
                    if (i > 0) AppDivider(Modifier.padding(horizontal = Dim.pad))
                    val sentence = book.sentences.getOrNull(sentenceIndex)
                    val chTitle = book.chapters.getOrNull(book.chapterOf(sentenceIndex))?.title ?: ""
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJump(sentenceIndex); onClose() }
                            .padding(horizontal = Dim.pad, vertical = 14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(chTitle, style = AppText.title.copy(fontSize = 14.sp, color = c.accent), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("第 ${sentenceIndex + 1} 句", style = AppText.meta)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            highlight(sentence?.text?.replace(" ", "").orEmpty(), q, c.accent),
                            style = AppText.body.copy(fontFamily = LocalAppFonts.current.serif),
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun highlight(text: String, query: String, accent: Color) = buildAnnotatedString {
    if (query.isBlank()) { append(text); return@buildAnnotatedString }
    var start = 0
    val lower = text.lowercase()
    val q = query.lowercase()
    while (true) {
        val found = lower.indexOf(q, start)
        if (found < 0) { append(text.substring(start)); break }
        append(text.substring(start, found))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(found, found + query.length))
        }
        start = found + query.length
    }
}

// ===========================================================================
// WebView (original EPUB layout)
// ===========================================================================

@Composable
internal fun HtmlReader(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                loadUrl(url)
            }
        },
        update = { webView -> if (webView.url != url) webView.loadUrl(url) }
    )
}
