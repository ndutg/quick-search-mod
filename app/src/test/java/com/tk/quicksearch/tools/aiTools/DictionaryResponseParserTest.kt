package com.tk.quicksearch.tools.aiTools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryResponseParserTest {
    @Test
    fun groupsDefinitionsByPartOfSpeechAcrossEntries() {
        val body =
                """
                [{"word":"serene","phonetic":"/səˈɹiːn/","meanings":[
                  {"partOfSpeech":"noun","definitions":[
                    {"definition":"Serenity; clearness; calmness.","synonyms":[]}
                  ],"synonyms":[]},
                  {"partOfSpeech":"verb","definitions":[{"definition":"To make serene."}]},
                  {"partOfSpeech":"adjective","definitions":[
                    {"definition":"Peaceful, calm, unruffled.","example":"A serene mentality.","synonyms":["calm"]},
                    {"definition":"Without worry or anxiety."},
                    {"definition":"Fair and unclouded."}
                  ],"synonyms":["tranquil","calm"]}
                ]},
                {"word":"serene","meanings":[
                  {"partOfSpeech":"noun","definitions":[{"definition":"A fine rain after sunset."}],"synonyms":["serein"]},
                  {"partOfSpeech":"interjection","definitions":[{"definition":"Dropped past the cap."}]}
                ]}]
                """.trimIndent()

        val result = parseDictionaryEntries(body)!!

        assertEquals("serene", result.word)
        assertEquals(listOf("noun", "verb", "adjective"), result.senses.map { it.partOfSpeech })
        assertEquals(
                listOf("Serenity; clearness; calmness.", "A fine rain after sunset."),
                result.senses[0].definitions.map { it.text },
        )
        assertEquals(listOf("serein"), result.senses[0].synonyms)
        val adjective = result.senses[2]
        assertEquals(2, adjective.definitions.size)
        assertEquals("A serene mentality.", adjective.definitions[0].example)
        assertEquals(listOf("calm", "tranquil"), adjective.synonyms)
    }

    @Test
    fun rejectsNonEntryBodies() {
        assertNull(parseDictionaryEntries("<!DOCTYPE html><title>Just a moment...</title>"))
        assertNull(parseDictionaryEntries("""{"title":"No Definitions Found"}"""))
        assertNull(parseDictionaryEntries("""[{"word":"x","meanings":[]}]"""))
    }
}
