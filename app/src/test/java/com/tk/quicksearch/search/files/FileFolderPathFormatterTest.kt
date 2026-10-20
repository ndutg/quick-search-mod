package com.tk.quicksearch.search.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileFolderPathFormatterTest {
    private fun format(
        relativePath: String?,
        volumeName: String? = "external_primary",
        displayName: String = "file.pdf",
        isDirectory: Boolean = false,
    ) = FileFolderPathFormatter.format(relativePath, volumeName, displayName, isDirectory)

    @Test
    fun keepsFullPath() {
        assertEquals(FileFolderPath(listOf("Download"), false), format("Download/"))
        assertEquals(
            FileFolderPath(listOf("Documents", "Work", "Invoices", "2024"), false),
            format("Documents/Work/Invoices/2024/"),
        )
    }

    @Test
    fun normalizesSeparatorsAndBlankSegments() {
        assertEquals(
            FileFolderPath(listOf("Music", "Albums"), false),
            format(" //Music\\ Albums /"),
        )
    }

    @Test
    fun returnsNullAtPrimaryStorageRoot() {
        assertNull(format(null))
        assertNull(format(""))
        assertNull(format("/", volumeName = null))
    }

    @Test
    fun flagsSecondaryVolumes() {
        assertEquals(
            FileFolderPath(listOf("Media", "Music", "Albums"), true),
            format("Media/Music/Albums/", volumeName = "1A2B-3C4D"),
        )
        assertEquals(FileFolderPath(emptyList(), true), format("", volumeName = "1A2B-3C4D"))
        assertNull(format("", volumeName = "external"))
    }

    @Test
    fun labelsWithContainingFolder() {
        val label = { path: FileFolderPath? -> FileFolderPathFormatter.folderLabel(path, "SD card") }
        assertEquals("2024", label(format("Documents/Work/Invoices/2024/")))
        assertEquals("SD card", label(format("", volumeName = "1A2B-3C4D")))
        assertEquals("Albums", label(format("Music/Albums/", volumeName = "1A2B-3C4D")))
        assertNull(label(format("")))
    }

    @Test
    fun dropsDirectoryOwnNameFromPath() {
        assertEquals(
            FileFolderPath(listOf("Documents", "Finance"), false),
            format("Documents/Finance/Invoices/", displayName = "Invoices", isDirectory = true),
        )
        assertEquals(
            FileFolderPath(listOf("Documents", "Finance"), false),
            format("Documents/Finance/", displayName = "Invoices", isDirectory = true),
        )
    }
}
