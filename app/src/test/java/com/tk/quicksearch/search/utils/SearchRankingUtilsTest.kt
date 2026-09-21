package com.tk.quicksearch.search.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingUtilsTest {
    @Test
    fun diacriticsAndTurkishDotlessIAreNormalizedForSearch() {
        val priority = SearchRankingUtils.calculateMatchPriority("Özgür Işık", "ozgur isik")

        assertTrue(DefaultSearchMatcher.isMatch(priority))
    }

    @Test
    fun dotlessIQueryMatchesAsciiIVariants() {
        val priority = SearchRankingUtils.calculateMatchPriority("Isik", "ışık")

        assertTrue(DefaultSearchMatcher.isMatch(priority))
    }

    @Test
    fun singleTokenQueryMatchesMultiWordTextWhenSpacesAreOmitted() {
        val priority = SearchRankingUtils.calculateMatchPriority("Bala Guna Teja", "balagunateja")

        assertTrue(DefaultSearchMatcher.isMatch(priority))
    }

    @Test
    fun multiWordQueryMatchesTextWhenTargetSpacesAreOmitted() {
        val priority = SearchRankingUtils.calculateMatchPriority("BalaGunaTeja", "bala guna teja")

        assertTrue(DefaultSearchMatcher.isMatch(priority))
    }

    @Test
    fun compactMatchingStillRequiresContiguousCharacters() {
        val priority = SearchRankingUtils.calculateMatchPriority("Bala Teja", "balagunateja")

        assertFalse(DefaultSearchMatcher.isMatch(priority))
    }

    @Test
    fun compactMatchingIgnoresPunctuationSeparators() {
        listOf("fdr", "fdro", "fdroi").forEach { query ->
            val priority = SearchRankingUtils.calculateMatchPriority("F-Droid", query)

            assertTrue("Expected F-Droid to match $query", DefaultSearchMatcher.isMatch(priority))
        }
    }

    @Test
    fun nicknameMatchingIsSpaceInsensitive() {
        val priority =
            SearchRankingUtils.calculateMatchPriorityWithNickname(
                primaryText = "Contact",
                nickname = "Bala Guna Teja",
                query = "balagunateja",
            )

        assertTrue(DefaultSearchMatcher.isMatch(priority))
    }

    @Test
    fun exactNicknameOutranksANameThatOnlyStartsWithTheQuery() {
        val exactNicknamePriority =
            SearchRankingUtils.calculateMatchPriorityWithNickname(
                primaryText = "Someone Else",
                nickname = "Teja",
                query = "teja",
            )
        val namePrefixPriority = SearchRankingUtils.calculateMatchPriority("Tejal", "teja")

        assertTrue(exactNicknamePriority < namePrefixPriority)
    }

    @Test
    fun commaSeparatedNicknamesEachMatchOnTheirOwn() {
        listOf("tv", "remote", "cast").forEach { query ->
            val priority =
                SearchRankingUtils.calculateMatchPriorityWithNickname(
                    primaryText = "Mi Remote",
                    nickname = "tv, remote, cast",
                    query = query,
                )

            assertTrue("Expected \"$query\" to match one of the nicknames", DefaultSearchMatcher.isMatch(priority))
        }
    }

    @Test
    fun aliasIsRankedAsAStandaloneNameRatherThanPartOfTheJoinedString() {
        val query = SearchQueryContext.fromRawQuery("remote")
        val split = DefaultSearchMatcher.match("Mi Remote", query, "tv, remote")
        val single = DefaultSearchMatcher.match("Mi Remote", query, "remote")

        // "remote" starts the second alias, so it must score the same as if it were the only one.
        assertEquals(single, split)
    }

    @Test
    fun blankAliasesAreIgnoredInsteadOfMatchingEverything() {
        val query = SearchQueryContext.fromRawQuery("quantum")

        assertFalse(DefaultSearchMatcher.isMatch(DefaultSearchMatcher.match("No match", query, ", , ")))
        assertFalse(DefaultSearchMatcher.isMatch(CachedSearchMatcher(SearchTextCache()).match("No match", query, ", , ")))
    }

    @Test
    fun normalizeInputTrimsDeduplicatesAndDropsEmptyAliases() {
        assertEquals("tv, remote", NicknameUtils.normalizeInput("  tv ,, remote ,  tv  "))
        assertEquals(null, NicknameUtils.normalizeInput(" , , "))
        assertEquals(null, NicknameUtils.normalizeInput(null))
    }

    @Test
    fun cachedMatcherProducesTheSamePrioritiesAsTheDefaultMatcher() {
        val cachedMatcher = CachedSearchMatcher(SearchTextCache())
        val cases =
            listOf(
                Triple("Özgür Işık", "ozgur isik", null),
                Triple("F-Droid", "fdroi", null),
                Triple("Contact", "balagunateja", "Bala Guna Teja"),
                Triple("Mi Remote", "remote", "tv, remote, cast"),
                Triple("Mi Remote", "cast", "tv, remote, cast"),
                Triple("No match", "quantum", "tv, remote"),
                Triple("Passport Office", "teja passport", null),
                Triple("No match", "quantum", null),
            )

        cases.forEach { (text, rawQuery, nickname) ->
            val query = SearchQueryContext.fromRawQuery(rawQuery)
            assertEquals(
                DefaultSearchMatcher.match(text, query, nickname),
                cachedMatcher.match(text, query, nickname),
            )
        }
    }

    @Test
    fun textCacheReusesIdenticalContentWithoutStalingChangedContent() {
        val cache = SearchTextCache()
        val first = cache.prepare("Localized label")

        assertSame(first, cache.prepare("Localized label"))
        assertNotSame(first, cache.prepare("Changed localized label"))
        assertEquals("changed localized label", cache.prepare("Changed localized label").normalized)
    }
}
