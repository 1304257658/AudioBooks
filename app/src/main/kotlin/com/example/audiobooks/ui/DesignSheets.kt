package com.example.audiobooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobooks.model.AudioBook
import kotlin.math.roundToInt

// ===========================================================================
// SPEED
// ===========================================================================

@Composable
internal fun SpeedSheet(visible: Boolean, onClose: () -> Unit, value: Float, onChange: (Float) -> Unit) {
    val c = LocalAppColors.current
    val presets = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    AppBottomSheet(visible = visible, onClose = onClose, title = "播放速度") {
        Column(Modifier.padding(horizontal = Dim.pad)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${formatRate(value)}×",
                    style = AppText.h1.copy(fontSize = 52.sp, color = c.accent, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(12.dp))
            Slider(value = value, onValueChange = onChange, valueRange = 0.5f..3f, steps = 9)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0.5×", style = AppText.meta)
                Text("3.0×", style = AppText.meta)
            }
            Spacer(Modifier.height(18.dp))
            presets.chunked(4).forEach { rowVals ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowVals.forEach { p ->
                        Box(Modifier.weight(1f)) {
                            Chip("${formatRate(p)}×", selected = value == p, onClick = { onChange(p) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// SLEEP TIMER
// ===========================================================================

@Composable
internal fun SleepSheet(visible: Boolean, onClose: () -> Unit, currentLabel: String?, onPick: (Int?) -> Unit) {
    val c = LocalAppColors.current
    // value (minutes), label ; -1 = chapter end, null = off
    val opts = listOf<Pair<Int?, String>>(
        null to "关闭",
        15 to "15 分钟",
        30 to "30 分钟",
        45 to "45 分钟",
        60 to "60 分钟",
        -1 to "播完本章"
    )
    fun isSelected(v: Int?): Boolean = when (v) {
        null -> currentLabel == null
        -1 -> currentLabel == "chapter"
        else -> currentLabel == "${v}m"
    }
    AppBottomSheet(visible = visible, onClose = onClose, title = "睡眠定时") {
        Column(Modifier.padding(horizontal = Dim.pad)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(Dim.rM))
                    .background(c.accentWash)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(AppIcons.moon, size = 18.dp, tint = c.accent)
                Text("到点自动暂停，已听过的内容可安心入睡。", style = AppText.meta.copy(color = c.ink2))
            }
            opts.forEach { (v, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(v); onClose() }
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = AppText.title.copy(fontSize = 15.5.sp, color = if (isSelected(v)) c.accent else c.ink),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected(v)) AppIcon(AppIcons.check, size = 20.dp, tint = c.accent)
                }
            }
        }
    }
}

// ===========================================================================
// MODEL / VOICE
// ===========================================================================

@Composable
internal fun ModelSheet(
    visible: Boolean,
    onClose: () -> Unit,
    state: AudioBookUiState,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit
) {
    AppBottomSheet(visible = visible, onClose = onClose, title = "音色 / 模型") {
        ModelListContent(state, onSelectModel, onDownloadModel, onDeleteModel)
    }
}

// ===========================================================================
// BOOK DETAIL
// ===========================================================================

@Composable
internal fun BookSheet(
    visible: Boolean,
    book: AudioBook?,
    voiceShort: String,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onRead: () -> Unit,
    onChapters: () -> Unit
) {
    val c = LocalAppColors.current
    AppBottomSheet(visible = visible && book != null, onClose = onClose) {
        if (book == null) return@AppBottomSheet
        Column(Modifier.padding(horizontal = Dim.pad)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(book, width = 104.dp)
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = AppText.h2.copy(fontSize = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(book.author ?: "本地导入", style = AppText.meta)
                    Spacer(Modifier.height(10.dp))
                    OnDeviceBadge(voiceShort)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(width = 0.dp, color = Color.Transparent)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetaCell("章节", "${book.chapters.size}", Modifier.weight(1f))
                MetaCell("句数", "${book.totalSentences}", Modifier.weight(1f))
                MetaCell("进度", "${(book.progressFraction() * 100).roundToInt()}%", Modifier.weight(1f))
            }
            AppDivider()
            Spacer(Modifier.height(16.dp))
            if (book.isStarted()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (book.isFinished()) "已听完" else "已听到第 ${book.chapterOf(book.currentSentenceIndex) + 1} 章", style = AppText.meta)
                    Text("${(book.progressFraction() * 100).roundToInt()}%", style = AppText.meta)
                }
                Spacer(Modifier.height(6.dp))
                ProgressLine(book.progressFraction())
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton(
                if (book.isStarted() && !book.isFinished()) "继续收听" else "开始收听",
                onClick = onPlay, leadingIcon = AppIcons.play, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("阅读", onRead, leadingIcon = AppIcons.type, modifier = Modifier.weight(1f))
                GhostButton("章节", onChapters, leadingIcon = AppIcons.list, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AppText.title.copy(fontSize = 15.sp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = AppText.meta.copy(fontSize = 11.5.sp))
    }
}
