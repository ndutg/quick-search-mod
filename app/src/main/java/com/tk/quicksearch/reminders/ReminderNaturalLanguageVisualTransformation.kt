package com.tk.quicksearch.reminders

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal class ReminderNaturalLanguageVisualTransformation(
    private val highlightColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styledText = AnnotatedString.Builder(text).apply {
            ReminderNaturalLanguageParser.parse(text.text)?.highlightedRanges.orEmpty().forEach { range ->
                addStyle(SpanStyle(color = highlightColor), range.start, range.endExclusive)
            }
        }.toAnnotatedString()
        return TransformedText(styledText, OffsetMapping.Identity)
    }
}
