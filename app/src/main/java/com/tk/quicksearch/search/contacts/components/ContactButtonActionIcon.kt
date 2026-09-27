package com.tk.quicksearch.search.contacts.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.contacts.models.ContactButtonAction
import com.tk.quicksearch.shared.ui.components.AppVoiceCallIcon
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/** Icon for a contact card button's default action, styled like the contact row buttons. */
@Composable
internal fun ContactButtonActionIcon(
    action: ContactButtonAction,
    enabled: Boolean = true,
) {
    val disabledTint = MaterialTheme.colorScheme.onSurfaceVariant
    val logoTint = if (enabled) Color.Unspecified else disabledTint
    val callIconTint = if (enabled) AppColors.CallIconTint else disabledTint
    val iconSize = ContactUiConstants.ACTION_ICON_SIZE.dp
    val label = stringResource(action.labelRes)

    @Composable
    fun LogoIcon(
        @DrawableRes res: Int,
        tint: Color = logoTint,
        size: Dp = iconSize,
    ) {
        Icon(
            painter = painterResource(id = res),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }

    when (action) {
        ContactButtonAction.CALL ->
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = label,
                tint = callIconTint,
                modifier = Modifier.size(iconSize),
            )
        ContactButtonAction.SMS ->
            Icon(
                imageVector = Icons.Rounded.Sms,
                contentDescription = label,
                tint = callIconTint,
                modifier = Modifier.size((ContactUiConstants.ACTION_ICON_SIZE * 0.9f).dp),
            )
        ContactButtonAction.EMAIL ->
            Icon(
                imageVector = Icons.Rounded.Email,
                contentDescription = label,
                tint = if (enabled) AppColors.ActionEmail else disabledTint,
                modifier = Modifier.size(iconSize),
            )
        ContactButtonAction.GOOGLE_MEET -> LogoIcon(R.drawable.google_meet)
        ContactButtonAction.WHATSAPP_MESSAGE,
        ContactButtonAction.WHATSAPP_BUSINESS_MESSAGE,
        -> LogoIcon(R.drawable.whatsapp)
        ContactButtonAction.WHATSAPP_CALL,
        ContactButtonAction.WHATSAPP_BUSINESS_CALL,
        -> AppVoiceCallIcon(logoPainterRes = R.drawable.whatsapp_call, size = iconSize, enabled = enabled)
        ContactButtonAction.WHATSAPP_VIDEO_CALL,
        ContactButtonAction.WHATSAPP_BUSINESS_VIDEO_CALL,
        -> LogoIcon(R.drawable.whatsapp_video_call)
        ContactButtonAction.TELEGRAM_MESSAGE -> LogoIcon(R.drawable.telegram)
        ContactButtonAction.TELEGRAM_CALL ->
            AppVoiceCallIcon(logoPainterRes = R.drawable.telegram_call, size = iconSize, enabled = enabled)
        ContactButtonAction.TELEGRAM_VIDEO_CALL -> LogoIcon(R.drawable.telegram_video_call)
        ContactButtonAction.SIGNAL_MESSAGE ->
            LogoIcon(
                res = R.drawable.signal,
                tint = if (enabled) AppColors.ActionSignal else disabledTint,
                size = DesignTokens.SignalMessageIconSize,
            )
        ContactButtonAction.SIGNAL_CALL ->
            AppVoiceCallIcon(logoPainterRes = R.drawable.signal_call, size = iconSize, enabled = enabled)
        ContactButtonAction.SIGNAL_VIDEO_CALL -> LogoIcon(R.drawable.signal_video_call)
    }
}
