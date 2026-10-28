package com.tk.quicksearch.widgets.atAGlanceWidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.drawable.toBitmap
import com.tk.quicksearch.shared.ui.icons.MaterialSymbols
import kotlin.math.roundToInt

/** Every row icon is parceled into the widget's RemoteViews, so icons stay small. */
private const val MAX_ICON_PX = 64

/**
 * Bitmaps for row icons. Glance can't draw a Compose [ImageVector], so Material icons are drawn
 * here as alpha masks that the widget tints. Both kinds are cached for the process, and a cached
 * bitmap reused across rows is sent once.
 */
internal object AtAGlanceWidgetIcons {
    private val vectorBitmaps = mutableMapOf<String, Bitmap>()
    private val appBitmaps = mutableMapOf<String, Bitmap?>()

    fun iconSizePx(context: Context): Int =
        (AtAGlanceWidgetDimens.ICON_SIZE.value * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceIn(1, MAX_ICON_PX)

    @Synchronized
    fun vector(
        context: Context,
        vector: ImageVector,
    ): Bitmap {
        val sizePx = iconSizePx(context)
        return vectorBitmaps.getOrPut("${vector.name}@$sizePx") { renderVector(vector, sizePx) }
    }

    /** The app's icon, or null when the app is gone. */
    @Synchronized
    fun app(
        context: Context,
        packageName: String,
    ): Bitmap? {
        val sizePx = iconSizePx(context)
        return appBitmaps.getOrPut("$packageName@$sizePx") {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
    }

    fun fallbackAppIcon(context: Context): Bitmap = vector(context, MaterialSymbols.Apps)

    private fun renderVector(
        vector: ImageVector,
        sizePx: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        canvas.concat(
            Matrix().apply { setScale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight) },
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        drawNode(canvas, vector.root, paint)
        return bitmap
    }

    /**
     * Material icons are filled paths, so strokes and clip paths are not drawn. Group transforms
     * are, since Material Symbols shift their paths into the viewport with a group translation.
     */
    private fun drawNode(
        canvas: Canvas,
        node: VectorNode,
        paint: Paint,
    ) {
        when (node) {
            is VectorGroup -> {
                canvas.save()
                // The same order as Compose's VectorGroup: translate, rotate and scale around the pivot.
                canvas.translate(node.translationX + node.pivotX, node.translationY + node.pivotY)
                canvas.rotate(node.rotation)
                canvas.scale(node.scaleX, node.scaleY)
                canvas.translate(-node.pivotX, -node.pivotY)
                node.forEach { drawNode(canvas, it, paint) }
                canvas.restore()
            }
            is VectorPath -> {
                val path = PathParser().addPathNodes(node.pathData).toPath()
                path.fillType = node.pathFillType
                paint.alpha = (node.fillAlpha * 255).roundToInt()
                canvas.drawPath(path.asAndroidPath(), paint)
            }
        }
    }
}
