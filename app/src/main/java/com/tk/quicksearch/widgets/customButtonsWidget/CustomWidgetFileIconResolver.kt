package com.tk.quicksearch.widgets.customButtonsWidget

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.tk.quicksearch.R
import com.tk.quicksearch.search.models.DeviceFile
import com.tk.quicksearch.search.models.FileType
import com.tk.quicksearch.search.models.FileTypeUtils
import com.tk.quicksearch.shared.ui.icons.MaterialSymbols

private val WORD_EXTENSIONS = setOf("doc", "docx")
private val SHEET_EXTENSIONS = setOf("xls", "xlsx")
private val SLIDES_EXTENSIONS = setOf("ppt", "pptx")
private val TEXT_EXTENSIONS = setOf("txt")
private val EPUB_EXTENSIONS = setOf("epub")
private val ZIP_EXTENSIONS = setOf("zip")

@DrawableRes
fun customWidgetFileIconRes(action: CustomWidgetButtonAction.File): Int? {
    if (action.isDirectory) return null

    val extension = action.displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (extension.isBlank()) return null

    return when {
        FileTypeUtils.isPdf(deviceFileFromAction(action)) -> R.drawable.ic_pdf
        extension in WORD_EXTENSIONS -> R.drawable.ic_doc
        extension in SHEET_EXTENSIONS -> R.drawable.ic_sheet
        extension in SLIDES_EXTENSIONS -> R.drawable.ic_slides
        extension in TEXT_EXTENSIONS -> R.drawable.ic_txt
        extension in EPUB_EXTENSIONS -> R.drawable.ic_epub
        extension in ZIP_EXTENSIONS -> R.drawable.ic_zip
        else -> null
    }
}

@Composable
fun widgetFileIconVector(action: CustomWidgetButtonAction.File): ImageVector {
    if (action.isDirectory) return MaterialSymbols.Folder
    customWidgetFileIconRes(action)?.let { return ImageVector.vectorResource(it) }

    return when (FileTypeUtils.getFileType(deviceFileFromAction(action))) {
        FileType.AUDIO -> MaterialSymbols.AudioFile
        FileType.PICTURES -> MaterialSymbols.Image
        FileType.VIDEOS -> MaterialSymbols.VideoLibrary
        FileType.APKS -> MaterialSymbols.Android
        else -> MaterialSymbols.InsertDriveFile
    }
}

private fun deviceFileFromAction(action: CustomWidgetButtonAction.File): DeviceFile =
    DeviceFile(
        uri = Uri.parse(action.uri),
        displayName = action.displayName,
        mimeType = action.mimeType,
        lastModified = action.lastModified,
        isDirectory = action.isDirectory,
        relativePath = action.relativePath,
        volumeName = action.volumeName,
    )
