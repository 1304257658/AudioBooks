package com.example.audiobooks.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobooks.model.AudioBook
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Mapping helpers: real AudioBook -> design concepts
// ---------------------------------------------------------------------------

/** Stable hue (0..360) derived from the book title, used for procedural covers. */
internal fun hueFromString(s: String): Float {
    var h = 0
    for (ch in s) h = (h * 31 + ch.code) and 0x7fffffff
    return (h % 360).toFloat()
}

/** Listening progress 0f..1f from sentence position. */
internal fun AudioBook.progressFraction(): Float {
    if (totalSentences <= 1) return if (currentSentenceIndex > 0) 1f else 0f
    return (currentSentenceIndex.toFloat() / (totalSentences - 1)).coerceIn(0f, 1f)
}

internal fun AudioBook.isStarted(): Boolean = lastReadAt != null
internal fun AudioBook.isFinished(): Boolean =
    isStarted() && totalSentences > 0 && currentSentenceIndex >= totalSentences - 1

internal fun AudioBook.chapterOf(sentenceIndex: Int): Int {
    val c = chapters.indexOfFirst { sentenceIndex in it.startSentenceIndex..it.endSentenceIndex }
    return if (c >= 0) c else 0
}

// ---------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------

@Composable
internal fun AppDivider(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hairline)
    )
}

@Composable
internal fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    tint: Color = LocalAppColors.current.ink,
    bordered: Boolean = false
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .size(boxSize)
            .clip(CircleShape)
            .then(if (bordered) Modifier.background(c.surface).border(1.dp, c.hairline, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, size = iconSize, tint = tint)
    }
}

private val CircleShape = androidx.compose.foundation.shape.CircleShape

@Composable
internal fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.accentWash else c.surface2)
            .border(
                1.dp,
                if (selected) c.accent.copy(alpha = 0.4f) else c.hairline,
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = AppText.meta.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (selected) c.accent else c.ink2
            )
        )
    }
}

@Composable
internal fun AppSwitch(on: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) c.accent else c.hairline2)
            .clickable(onClick = onToggle)
    ) {
        Box(
            Modifier
                .padding(start = if (on) 21.dp else 3.dp, top = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
internal fun Segmented(
    options: List<Pair<String, String>>, // value to label
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = AppText.meta.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) c.accent else c.ink2
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** Small "on-device" badge with a pulsing dot. */
@Composable
internal fun OnDeviceBadge(text: String, modifier: Modifier = Modifier, color: Color = LocalAppColors.current.accent) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Text(
            text,
            style = AppText.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
        )
    }
}

// ---------------------------------------------------------------------------
// Book cover (procedural, "type" style from the prototype)
// ---------------------------------------------------------------------------

@Composable
internal fun BookCover(
    title: String,
    author: String?,
    hue: Float,
    width: Dp,
    modifier: Modifier = Modifier,
    showText: Boolean = true
) {
    val height = width * 1.4f
    val bg = Color.hsl(hue, 0.20f, 0.88f)
    val titleColor = Color.hsl(hue, 0.30f, 0.32f)
    val authorColor = titleColor.copy(alpha = 0.62f)
    val w = width.value
    Box(
        modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(Dim.rS))
            .background(bg)
            .padding((w * 0.12f).dp)
    ) {
        if (showText) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    title,
                    style = AppText.h2.copy(
                        fontSize = (w * 0.14f).coerceAtLeast(11f).sp,
                        lineHeight = (w * 0.155f).coerceAtLeast(12f).sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (w >= 90 && !author.isNullOrBlank()) {
                    Spacer(Modifier.height((w * 0.04f).dp))
                    Text(
                        author,
                        style = AppText.meta.copy(
                            fontSize = (w * 0.062f).coerceAtLeast(9f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = authorColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookCover(book: AudioBook, width: Dp, modifier: Modifier = Modifier, showText: Boolean = true) {
    BookCover(book.title, book.author, hueFromString(book.title), width, modifier, showText)
}

// ---------------------------------------------------------------------------
// Waveform
// ---------------------------------------------------------------------------

private val WaveSeed: FloatArray = FloatArray(56) { i ->
    (0.28 + 0.72 * abs(sin(i * 0.7) * 0.6 + sin(i * 0.27 + 1) * 0.4)).toFloat().coerceIn(0.1f, 1f)
}

@Composable
internal fun Waveform(
    progress: Float,
    live: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    bars: Int = 56,
    onSeek: ((Float) -> Unit)? = null
) {
    val c = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val n = bars.coerceAtMost(WaveSeed.size)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek?.invoke((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val gap = size.width * 0.012f
        val barW = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val base = WaveSeed[i]
            val factor = if (live) (0.45f + 0.55f * abs(sin(phase + i * 0.6f))) else 1f
            val h = (size.height * base * factor).coerceAtLeast(2f)
            val x = i * (barW + gap)
            val on = i.toFloat() / n <= progress
            drawRoundRect(
                color = if (on) c.accent else c.hairline2,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom sheet
// ---------------------------------------------------------------------------

@Composable
internal fun AppBottomSheet(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit
) {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x6B140F08))
                    .clickable(onClick = onClose)
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(animationSpec = tween(280)) { it },
            exit = slideOutVertically(animationSpec = tween(220)) { it }
        ) {
            Column(
                modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.surface)
                    .clickable(enabled = false) {}
                    .padding(bottom = Dim.pad)
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.hairline2)
                )
                if (title != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Dim.pad, end = Dim.pad, top = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = AppText.h2, modifier = Modifier.weight(1f))
                        RoundIconButton(AppIcons.close, onClose, iconSize = 22.dp)
                    }
                }
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) c.accent else c.hairline2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leadingIcon != null) AppIcon(leadingIcon, size = 18.dp, tint = c.accentInk)
            Text(
                text,
                style = AppText.title.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accentInk)
            )
        }
    }
}

@Composable
internal fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leadingIcon != null) AppIcon(leadingIcon, size = 18.dp, tint = c.ink)
            Text(text, style = AppText.title.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink))
        }
    }
}

/** Big circular play/pause button. */
@Composable
internal fun PlayCircle(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 76.dp,
    iconSize: Dp = 32.dp,
    enabled: Boolean = true
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (enabled) c.accent else c.hairline2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(if (playing) AppIcons.pause else AppIcons.play, size = iconSize, tint = c.accentInk)
    }
}
