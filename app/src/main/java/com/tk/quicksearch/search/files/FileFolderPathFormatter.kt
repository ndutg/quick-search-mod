package com.tk.quicksearch.search.files

import com.tk.quicksearch.search.models.DeviceFile

/** The folders containing a file, for display in a result row. */
data class FileFolderPath(
    val segments: List<String>,
    val isSecondaryVolume: Boolean,
)

object FileFolderPathFormatter {
    private val primaryVolumeNames = setOf("external_primary", "external")

    fun format(file: DeviceFile): FileFolderPath? =
        format(
            relativePath = file.relativePath,
            volumeName = file.volumeName,
            displayName = file.displayName,
            isDirectory = file.isDirectory,
        )

    fun format(
        relativePath: String?,
        volumeName: String?,
        displayName: String,
        isDirectory: Boolean,
    ): FileFolderPath? {
        var segments =
            relativePath
                .orEmpty()
                .replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        // Guard against providers that report a directory's own name as the last path segment.
        if (isDirectory && segments.lastOrNull() == displayName.trim()) {
            segments = segments.dropLast(1)
        }
        val isSecondaryVolume = isSecondaryVolume(volumeName)
        if (segments.isEmpty() && !isSecondaryVolume) return null

        return FileFolderPath(segments = segments, isSecondaryVolume = isSecondaryVolume)
    }

    /** The folder a file sits in, or [secondaryVolumeLabel] at the root of removable storage. */
    fun folderLabel(
        folderPath: FileFolderPath?,
        secondaryVolumeLabel: String,
    ): String? {
        if (folderPath == null) return null
        return folderPath.segments.lastOrNull()
            ?: secondaryVolumeLabel.takeIf { folderPath.isSecondaryVolume }
    }

    private fun isSecondaryVolume(volumeName: String?): Boolean =
        !volumeName.isNullOrBlank() && volumeName.lowercase() !in primaryVolumeNames
}
