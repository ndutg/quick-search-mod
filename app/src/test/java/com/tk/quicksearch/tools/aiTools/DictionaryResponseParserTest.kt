package com.tk.quicksearch.tools.aiTools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryResponseParserTest {
    @Test
    fun parsesFirstDefinitionWithExampleAndSynonyms() {
        val body =
                """
                [{"word":"serendipity","meanings":[
                  {"partOfSpeech":"noun","definitions":[
                    {"definition":"A combination of events which have come together by chance.","synonyms":["chance"]},
                    {"definition":"Unsought discovery.","example":"Finding it was pure serendipity."}
                  ],"synonyms":["luck","chance"]}
                ]}]
                """.trimIndent()

        val result = parseDictionaryEntries(body)!!

        assertEquals("serendipity", result.word)
        assertEquals("noun", result.partOfSpeech)
        assertEquals("A combination of events which have come together by chance.", result.meaning)
        assertEquals("Finding it was pure serendipity.", result.example)
        assertEquals(listOf("chance", "luck"), result.synonyms)
    }

    @Test
    fun rejectsNonEntryBodies() {
        assertNull(parseDictionaryEntries("<!DOCTYPE html><title>Just a moment...</title>"))
        assertNull(parseDictionaryEntries("""{"title":"No Definitions Found"}"""))
        assertNull(parseDictionaryEntries("""[{"word":"x","meanings":[]}]"""))
    }
}
