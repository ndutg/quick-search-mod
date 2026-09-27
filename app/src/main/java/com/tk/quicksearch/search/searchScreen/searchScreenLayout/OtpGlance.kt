package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.OtpNotification
import kotlinx.coroutines.delay

/** How long the copy button shows a check after copying. */
private const val COPIED_FEEDBACK_MILLIS = 2_000L

/**
 * The newest one-time code: its sender (or the app, when the message doesn't say) above the code in
 * large capitals, with copy and dismiss buttons. Tapping the row opens the message.
 */
@Composable
internal fun OtpCodeRow(
    otp: OtpNotification,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(otp.key, otp.code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MILLIS)
            copied = false
        }
    }
    val copyLabel = stringResource(R.string.calculator_copy_result)
    GlanceStatusRow(
        icon = { NotificationAppIcon(otp.packageName) },
        title = otp.sender ?: rememberAppLabel(otp.packageName),
        onClick = { openNotificationTarget(context, otp.packageName, otp.contentIntent) },
        onDismiss = onDismiss,
        belowText = {
            Text(
                text = otp.code,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        },
        trailing = {
            FilledTonalIconButton(
                onClick = {
                    copied = copySensitiveText(context, copyLabel, otp.code)
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = copyLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** Copies [text], marked sensitive so Android 13+ hides it from the clipboard preview. */
private fun copySensitiveText(
    context: Context,
    label: String,
    text: String,
): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    return runCatching { clipboard.setPrimaryClip(clip) }.isSuccess
}
