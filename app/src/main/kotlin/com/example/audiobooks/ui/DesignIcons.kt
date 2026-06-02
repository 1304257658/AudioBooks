package com.example.audiobooks.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ColorFilter

/**
 * Line/solid icons rebuilt as ImageVectors from the prototype's SVG path data (data.jsx).
 * Circles/rects were converted to equivalent path commands. Color is applied at draw time
 * via [AppIcon] tint, so the baked color here is arbitrary.
 */
private fun strokeIcon(pathData: String, sw: Float = 1.8f): ImageVector {
    val nodes = PathParser().parsePathString(pathData).toNodes()
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = nodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = sw,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

private fun fillIcon(pathData: String): ImageVector {
    val nodes = PathParser().parsePathString(pathData).toNodes()
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(pathData = nodes, fill = SolidColor(Color.Black))
    }.build()
}

internal object AppIcons {
    val library = strokeIcon("M4 5v14M9 5v14M4 5h5v14H4z M13 6l4-1 3 13-4 1z")
    val search = strokeIcon("M4,11 a7,7 0 1,0 14,0 a7,7 0 1,0 -14,0 Z M20 20l-3.5-3.5")
    val settings = strokeIcon("M8.8,12 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M18.5 5.5l-1.8 1.8M7.3 16.7l-1.8 1.8")
    val play = fillIcon("M7 4.5v15l13-7.5z")
    val pause = fillIcon("M7 4.5h4v15H7zM13 4.5h4v15h-4z")
    val back15 = strokeIcon("M12 5a7 7 0 1 1-6.3 4M5.5 4v5h5")
    val fwd15 = strokeIcon("M12 5a7 7 0 1 0 6.3 4M18.5 4v5h-5")
    val prev = fillIcon("M19 5l-9 7 9 7z M6 5h1.6v14h-1.6z")
    val next = fillIcon("M5 5l9 7-9 7z M16.4 5h1.6v14h-1.6z")
    val chevL = strokeIcon("M15 5l-7 7 7 7")
    val chevR = strokeIcon("M9 5l7 7-7 7")
    val chevD = strokeIcon("M5 9l7 7 7-7")
    val close = strokeIcon("M6 6l12 12M18 6L6 18")
    val plus = strokeIcon("M12 5v14M5 12h14")
    val list = strokeIcon("M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
    val timer = strokeIcon("M4,13 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z M12 13V9M9 2h6")
    val speed = strokeIcon("M5 19a9 9 0 1 1 14 0M12 14l4-4")
    val voice = strokeIcon("M4 10v4M8 6v12M12 9v6M16 4v16M20 10v4")
    val bolt = fillIcon("M13 2L4 14h6l-1 8 9-12h-6z")
    val moon = strokeIcon("M20 14.5A8 8 0 1 1 9.5 4 6.5 6.5 0 0 0 20 14.5z")
    val offline = strokeIcon("M12 19a1 1 0 1 0 .01 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M3 3l18 18")
    val file = strokeIcon("M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z M14 3v5h5")
    val paste = strokeIcon("M6,4 h12 v17 h-12 Z M9 4V3h6v1M9 9h6M9 13h6M9 17h3")
    val check = strokeIcon("M5 12.5l4.5 4.5L19 7", sw = 2.2f)
    val chip = strokeIcon("M7,7 h10 v10 h-10 Z M10 2v3M14 2v3M10 19v3M14 19v3M2 10h3M2 14h3M19 10h3M19 14h3")
    val download = strokeIcon("M12 4v11M7 11l5 5 5-5M5 20h14")
    val trash = strokeIcon("M5 7h14M9 7V5h6v2M6 7l1 13h10l1-13")
    val info = strokeIcon("M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z M12 11v5M12 8h.01")
    val type = strokeIcon("M5 6h14M12 6v13M9 19h6")
    val sun = strokeIcon("M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z M12 2v2M12 20v2M4 12H2M22 12h-2M5.6 5.6L4.2 4.2M19.8 19.8l-1.4-1.4M18.4 5.6l1.4-1.4M4.2 19.8l1.4-1.4")
    val bookopen = strokeIcon("M12 6.5C10.5 5 8 4.5 4 5v13c4-.5 6.5 0 8 1.5 1.5-1.5 4-2 8-1.5V5c-4-.5-6.5 0-8 1.5z M12 6.5v13")
}

@Composable
internal fun AppIcon(
    icon: ImageVector,
    size: Dp = 24.dp,
    tint: Color = LocalAppColors.current.ink,
    modifier: Modifier = Modifier
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit
    )
}
