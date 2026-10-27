package com.tk.quicksearch.search.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tk.quicksearch.R
import com.tk.quicksearch.search.models.DeviceFile
import com.tk.quicksearch.search.models.FileTypeUtils
import com.tk.quicksearch.search.utils.FileUtils
import com.tk.quicksearch.shared.ui.components.PreserveHostKeyboardStateEffect
import com.tk.quicksearch.shared.ui.icons.MaterialSymbols
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PREVIEW_CORNER_RADIUS = 16.dp
private val DIALOG_MAX_WIDTH = 640.dp
private const val DIALOG_HEIGHT_FRACTION = 0.85f
private val ACTION_BUTTON_HEIGHT = 52.dp
private val ACTION_ICON_SIZE = 20.dp
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 8f

@Composable
fun FilePreviewDialog(
    deviceFile: DeviceFile,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    PreserveHostKeyboardStateEffect()
    val dialogHeight = LocalConfiguration.current.screenHeightDp.dp * DIALOG_HEIGHT_FRACTION
    val title = remember(deviceFile.displayName) {
        FileUtils.getFileNameWithoutExtension(deviceFile.displayName)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.SpacingLarge)
                .height(dialogHeight),
            shape = DesignTokens.ShapeXLarge,
            color = AppColors.DialogBackground,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = DesignTokens.SpacingLarge,
                            end = DesignTokens.SpacingXSmall,
                            top = DesignTokens.SpacingXSmall,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = MaterialSymbols.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.SpacingMedium)
                        .clip(RoundedCornerShape(PREVIEW_CORNER_RADIUS))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    when {
                        FileTypeUtils.isPdf(deviceFile) -> PdfPreview(deviceFile)
                        FileTypeUtils.isImage(deviceFile) -> ImagePreview(deviceFile)
                    }
                }

                FilePreviewActions(onOpen = onOpen, onShare = onShare)
            }
        }
    }
}

@Composable
private fun FilePreviewActions(
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DesignTokens.SpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onShare,
            modifier = Modifier.size(ACTION_BUTTON_HEIGHT),
        ) {
            Icon(
                imageVector = MaterialSymbols.Share,
                contentDescription = stringResource(R.string.action_share),
                modifier = Modifier.size(ACTION_ICON_SIZE),
            )
        }
        Button(
            onClick = onOpen,
            modifier = Modifier
                .weight(1f)
                .height(ACTION_BUTTON_HEIGHT),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(
                imageVector = MaterialSymbols.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(ACTION_ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.action_open),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ImagePreview(deviceFile: DeviceFile) {
    val context = LocalContext.current
    var bitmap by remember(deviceFile.uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(deviceFile.uri) { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(deviceFile.uri) {
        bitmap = withContext(Dispatchers.IO) { loadScaledBitmap(context, deviceFile) }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    scale = newScale
                    if (newScale > MIN_SCALE) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = deviceFile.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
            else -> Text(
                text = stringResource(R.string.common_error_unable_to_open, deviceFile.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PdfPreview(deviceFile: DeviceFile) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var pages by remember(deviceFile.uri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember(deviceFile.uri) { mutableStateOf(true) }

    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetXState = remember { mutableFloatStateOf(0f) }
    val offsetYState = remember { mutableFloatStateOf(0f) }

    // Consume LazyColumn's scroll when zoomed so single-finger drag pans instead
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (scaleState.floatValue > MIN_SCALE) available else Offset.Zero
        }
    }

    val pageWidthPx = with(density) { 360.dp.roundToPx() }

    LaunchedEffect(deviceFile.uri) {
        pages = withContext(Dispatchers.IO) { renderPdfPages(context, deviceFile, pageWidthPx) }
        loading = false
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            pages.isNotEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .nestedScroll(nestedScrollConnection)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scaleState.floatValue * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            scaleState.floatValue = newScale
                            if (newScale > MIN_SCALE) {
                                offsetXState.floatValue += pan.x
                                offsetYState.floatValue += pan.y
                            } else {
                                offsetXState.floatValue = 0f
                                offsetYState.floatValue = 0f
                            }
                        }
                    },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleState.floatValue
                            scaleY = scaleState.floatValue
                            translationX = offsetXState.floatValue
                            translationY = offsetYState.floatValue
                        },
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                    contentPadding = PaddingValues(DesignTokens.SpacingSmall),
                ) {
                    itemsIndexed(pages) { _, page ->
                        Image(
                            bitmap = page.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                        )
                    }
                }
            }
            else -> Text(
                text = stringResource(R.string.common_error_unable_to_open, deviceFile.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun loadScaledBitmap(context: Context, deviceFile: DeviceFile): Bitmap? {
    return try {
        val targetWidth = 1080
        val sampleSize = context.contentResolver.openInputStream(deviceFile.uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            maxOf(1, opts.outWidth / targetWidth)
        } ?: 1
        context.contentResolver.openInputStream(deviceFile.uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}

private fun renderPdfPages(context: Context, deviceFile: DeviceFile, pageWidthPx: Int): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        pfd = context.contentResolver.openFileDescriptor(deviceFile.uri, "r") ?: return emptyList()
        renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                val scale = pageWidthPx.toFloat() / page.width
                val pageHeightPx = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(pageWidthPx, pageHeightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply { setScale(scale, scale) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pages.add(bitmap)
            }
        }
    } catch (_: Exception) {
        // return whatever pages rendered so far
    } finally {
        renderer?.close()
        pfd?.close()
    }
    return pages
}
