package com.tk.quicksearch.search.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchTextNormalizerTest {
    @Test
    fun containsGlobPattern_matchesTurkishNamesFromAccentedOrPlainQuery() {
        val names = listOf("Çağrı Rapor.pdf", "çağrı.docx", "ÇAĞRI.txt", "cagri.txt")
        listOf("çağrı", "Çağrı", "cagri", "CAGRI").forEach { query ->
            val pattern = patternFor(query)
            names.forEach { name -> assertTrue("$query -> $name", globMatches(pattern, name)) }
        }
        assertFalse(globMatches(patternFor("çağrı"), "cagra.txt"))
    }

    @Test
    fun containsGlobPattern_matchesOtherTurkishLetters() {
        assertTrue(globMatches(patternFor("ögrenci"), "Öğrenci Listesi.xlsx"))
        assertTrue(globMatches(patternFor("üsküdar"), "ÜSKÜDAR.jpg"))
        assertTrue(globMatches(patternFor("isik"), "Işık.png"))
        assertTrue(globMatches(patternFor("istanbul"), "İstanbul.pdf"))
    }

    @Test
    fun containsGlobPattern_escapesGlobMetacharacters() {
        assertEquals("*[*][?][[]*", FileSearchTextNormalizer.containsGlobPattern("*?["))
    }

    private fun patternFor(query: String): String =
        FileSearchTextNormalizer.containsGlobPattern(FileSearchTextNormalizer.queryTokens(SearchTextNormalizer.normalizeForSearch(query)).single())

    /** Mirrors SQLite GLOB for the subset the builder emits: leading/trailing `*` and `[...]` classes. */
    private fun globMatches(pattern: String, text: String): Boolean {
        val regex =
            buildString {
                var i = 0
                while (i < pattern.length) {
                    when (val c = pattern[i]) {
                        '*' -> append(".*")
                        '[' -> {
                            val end = pattern.indexOf(']', i + 2)
                            append('[').append(Regex.escape(pattern.substring(i + 1, end)).removePrefix("\\Q").removeSuffix("\\E")).append(']')
                            i = end
                        }
                        else -> append(Regex.escape(c.toString()))
                    }
                    i++
                }
            }
        return Regex(regex).matches(text)
    }
}
