package com.tk.quicksearch.search.data

import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.models.ContactInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexMatchingTest {
    private val index =
        FileNameIndex(
            ids = longArrayOf(1, 2, 3, 4, 5),
            normalizedNames =
                arrayOf("teja-karlapudi resume.pdf", "holiday photos", "notes.txt", "teja_k.mkv", "readme"),
            isFolder = booleanArrayOf(false, true, false, false, false),
        )

    @Test
    fun fileTokensMatchAnywhereInNewestFirstOrder() {
        assertEquals(listOf(1L), index.matchingIds(listOf("resume", "teja"), null, false, 10, null))
    }

    @Test
    fun fileCompactQueryIgnoresSeparators() {
        assertEquals(listOf(1L, 4L), index.matchingIds(listOf("tejak"), null, false, 10, null))
        assertTrue(FileNameIndex.containsIgnoringSeparators("a.b-c_d e", "abcde"))
        assertFalse(FileNameIndex.containsIgnoringSeparators("abc", "abd"))
    }

    @Test
    fun fileTypeRulesMatchTheProviderQuery() {
        // Names without a dot are skipped unless they are folders.
        assertEquals(emptyList<Long>(), index.matchingIds(listOf("readme"), null, false, 10, null))
        assertEquals(listOf(2L), index.matchingIds(listOf("photos"), null, false, 10, null))
        assertEquals(listOf(2L), index.matchingIds(listOf("o"), null, true, 10, null))
        assertEquals(listOf(4L), index.matchingIds(emptyList(), "MKV", false, 10, null))
    }

    @Test
    fun fileMatchesStopAtTheLimit() {
        assertEquals(listOf(1L), index.matchingIds(listOf("e"), null, false, 1, null))
    }

    @Test
    fun contactTokensMatchPrimaryOrAlternativeNames() {
        val entries =
            listOf(
                entry(1, "Ada Lovelace", "Lovelace, Ada"),
                entry(2, "Alan Turing", null),
                entry(3, "Grace Hopper", null),
            )

        assertEquals(listOf(1L), ids(ContactSearchSnapshot.findByNameTokens(entries, "love ada", 10, emptySet())))
        assertEquals(listOf(3L), ids(ContactSearchSnapshot.findByNameTokens(entries, "gracehop", 10, emptySet())))
        assertEquals(listOf(2L), ids(ContactSearchSnapshot.findByNameTokens(entries, "a", 10, setOf(1L, 3L))))
        assertEquals(listOf(1L), ids(ContactSearchSnapshot.findByNameTokens(entries, "a", 1, emptySet())))
    }

    @Test
    fun lastUsedTimesOnlyMoveForward() {
        val apps = listOf(app("a", lastUsed = 100L), app("b", lastUsed = 500L))

        assertSame(apps, applyLastUsedTimes(apps, mapOf("b" to 400L, "c" to 900L)))
        val updated = applyLastUsedTimes(apps, mapOf("a" to 300L))
        assertEquals(listOf(300L, 500L), updated.map { it.lastUsedTime })
    }

    private fun ids(contacts: List<ContactInfo>) = contacts.map { it.contactId }

    private fun entry(
        id: Long,
        name: String,
        alternativeName: String?,
    ) = ContactSearchEntry(
        contact = ContactInfo(contactId = id, lookupKey = name, displayName = name, phoneNumbers = emptyList()),
        primaryName = name,
        alternativeName = alternativeName,
    )

    private fun app(
        packageName: String,
        lastUsed: Long,
    ) = AppInfo(
        appName = packageName,
        packageName = packageName,
        lastUsedTime = lastUsed,
        totalTimeInForeground = 0L,
        firstInstallTime = 0L,
        isSystemApp = false,
    )
}
