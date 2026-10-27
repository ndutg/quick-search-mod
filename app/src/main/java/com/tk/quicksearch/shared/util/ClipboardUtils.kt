package com.tk.quicksearch.shared.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

/** Text of the clipboard's first item, or null when the clipboard is empty or holds no text. */
suspend fun Clipboard.readText(): String? =
    getClipEntry()
        ?.clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()

/** Replaces the clipboard with plain [text], labeled the way Compose's old ClipboardManager did. */
suspend fun Clipboard.writeText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("plain text", text)))
}
