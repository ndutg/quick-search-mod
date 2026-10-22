package com.tk.quicksearch.widgetsPanel

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

private val WidgetPanelEdgeFadeHeight = 40.dp

/**
 * Fades widgets out at the top and bottom edges of the panel's scroll viewport instead of
 * slicing them with a hard clip line. Each edge only fades when there is content scrolled past
 * it. Apply before [androidx.compose.foundation.verticalScroll] so it covers the viewport.
 */
internal fun Modifier.widgetPanelEdgeFade(scrollState: ScrollState): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val maxScroll = scrollState.maxValue.toFloat()
            if (maxScroll <= 0f) return@drawWithContent
            val fadePx = min(WidgetPanelEdgeFadeHeight.toPx(), size.height / 2f)
            if (fadePx <= 0f) return@drawWithContent
            val scrollValue = scrollState.value.toFloat()

            fun fadeProgress(distance: Float): Float {
                val progress = (distance / fadePx).coerceIn(0f, 1f)
                return progress * progress * (3 - 2 * progress)
            }

            val topEdgeAlpha = 1f - fadeProgress(scrollValue)
            val bottomEdgeAlpha = 1f - fadeProgress(maxScroll - scrollValue)

            if (topEdgeAlpha < 1f) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = topEdgeAlpha), Color.Black),
                            startY = 0f,
                            endY = fadePx,
                        ),
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomEdgeAlpha < 1f) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Black.copy(alpha = bottomEdgeAlpha)),
                            startY = size.height - fadePx,
                            endY = size.height,
                        ),
                    topLeft = Offset(0f, size.height - fadePx),
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
