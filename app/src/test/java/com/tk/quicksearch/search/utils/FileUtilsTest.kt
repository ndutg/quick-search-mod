package com.tk.quicksearch.search.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {
    @Test
    fun fileNameWithoutExtension_removesOnlyValidExtension() {
        assertEquals("Quarterly report", FileUtils.getFileNameWithoutExtension("Quarterly report.pdf"))
        assertEquals("archive.tar", FileUtils.getFileNameWithoutExtension("archive.tar.gz"))
        assertEquals(".gitignore", FileUtils.getFileNameWithoutExtension(".gitignore"))
        assertEquals("readme", FileUtils.getFileNameWithoutExtension("readme"))
        assertEquals("draft.", FileUtils.getFileNameWithoutExtension("draft."))
    }
}
