package com.tk.quicksearch.search.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileExtensionQueryTest {
    @Test
    fun parsesExtensionOnlyQuery() {
        assertEquals(FileExtensionQuery("mkv", ""), FileExtensionQuery.parse(".mkv"))
        assertEquals(FileExtensionQuery("mkv", ""), FileExtensionQuery.parse(" .MKV "))
    }

    @Test
    fun parsesExtensionWithNameQuery() {
        assertEquals(FileExtensionQuery("mkv", "test"), FileExtensionQuery.parse(".mkv test"))
        assertEquals(FileExtensionQuery("mp3", "my song"), FileExtensionQuery.parse(".mp3  my song"))
        assertEquals(FileExtensionQuery("7z", ""), FileExtensionQuery.parse(".7z"))
    }

    @Test
    fun ignoresNonExtensionQueries() {
        assertNull(FileExtensionQuery.parse("."))
        assertNull(FileExtensionQuery.parse(".5"))
        assertNull(FileExtensionQuery.parse(".5 + 2"))
        assertNull(FileExtensionQuery.parse(". mkv"))
        assertNull(FileExtensionQuery.parse("test.mkv"))
        assertNull(FileExtensionQuery.parse(".tar.gz"))
        assertNull(FileExtensionQuery.parse(".abcdefghijk"))
    }

    @Test
    fun matchesOnlyFilesWithThatExtension() {
        val query = FileExtensionQuery("mkv", "")
        assertTrue(query.matchesFileName("Test Movie.MKV", isDirectory = false))
        assertFalse(query.matchesFileName("test.mkv.part", isDirectory = false))
        assertFalse(query.matchesFileName("test.mp4", isDirectory = false))
        assertFalse(query.matchesFileName("videos.mkv", isDirectory = true))
    }

    @Test
    fun noResultPrefixOnlyReusedForSameExtension() {
        assertFalse(FileExtensionQuery.isNoResultPrefixReusable(".mk", ".mkv"))
        assertTrue(FileExtensionQuery.isNoResultPrefixReusable(".mkv", ".mkv test"))
        assertTrue(FileExtensionQuery.isNoResultPrefixReusable(".mkv te", ".mkv test"))
        assertFalse(FileExtensionQuery.isNoResultPrefixReusable(".mkv", ".mkv.x"))
        assertTrue(FileExtensionQuery.isNoResultPrefixReusable("tes", "test"))
        assertFalse(FileExtensionQuery.isNoResultPrefixReusable("test", "tes"))
    }
}
