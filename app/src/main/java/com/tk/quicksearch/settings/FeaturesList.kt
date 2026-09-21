package com.tk.quicksearch.settings

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.tk.quicksearch.R
import com.tk.quicksearch.shared.util.RenderMarkdownDocument
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FEATURES_ASSET_FILE_NAME = "FEATURES.md"
private const val FEATURES_EXPORT_FILE_NAME = "Quick Search Features.md"
private const val MARKDOWN_MIME_TYPE = "text/markdown"

@Composable
internal fun FeaturesList(
    scrollState: ScrollState? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markdown by
        produceState<String?>(initialValue = null, context) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.assets.open(FEATURES_ASSET_FILE_NAME).bufferedReader().use { it.readText() }
                    }.getOrNull()
                }
        }

    if (markdown.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.settings_features_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
        return
    }

    RenderMarkdownDocument(
        markdown = markdown.orEmpty(),
        scrollState = scrollState,
        modifier = modifier,
    )
}

internal suspend fun downloadAndShareFeatures(context: Context) {
    val uri =
        withContext(Dispatchers.IO) {
            val markdown =
                context.assets.open(FEATURES_ASSET_FILE_NAME).bufferedReader().use { it.readText() }
            saveFeaturesToDownloads(context, markdown)
        }

    Toast.makeText(
        context,
        R.string.settings_features_saved_to_downloads,
        Toast.LENGTH_SHORT,
    ).show()
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = MARKDOWN_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(FEATURES_EXPORT_FILE_NAME, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.action_share)),
    )
}

private fun saveFeaturesToDownloads(
    context: Context,
    markdown: String,
): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FEATURES_EXPORT_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, MARKDOWN_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri =
            checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Unable to create the features document in Downloads"
            }
        try {
            resolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                writer.write(markdown)
            } ?: error("Unable to write the features document")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    } else {
        val downloadsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        check(downloadsDirectory.exists() || downloadsDirectory.mkdirs()) {
            "Unable to access Downloads"
        }
        val file = File(downloadsDirectory, FEATURES_EXPORT_FILE_NAME)
        file.writeText(markdown)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
