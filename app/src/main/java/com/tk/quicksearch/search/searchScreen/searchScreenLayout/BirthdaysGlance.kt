package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.ContactBirthday
import com.tk.quicksearch.search.data.ContactBirthdaysRepository
import com.tk.quicksearch.search.data.ContactRepository
import com.tk.quicksearch.search.data.preferences.GlancePreferences
import com.tk.quicksearch.search.models.ContactInfo
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Today's contact birthdays for the home At a Glance card. */
internal class BirthdaysGlance(
    val birthdays: List<ContactBirthday>,
    val open: (ContactBirthday) -> Unit,
    /** Hides the birthday until tomorrow. */
    val dismiss: (ContactBirthday) -> Unit,
)

/**
 * Reads today's birthdays on resume while [enabled], the toggle is on and contacts access is granted.
 * Opening one shows the contact's options drawer through [onShowContactMethods], like a contact result.
 */
@Composable
internal fun rememberBirthdaysGlance(
    enabled: Boolean,
    onShowContactMethods: (ContactInfo) -> Unit,
): BirthdaysGlance {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val repository = remember(appContext) { ContactBirthdaysRepository(appContext) }
    val contactRepository = remember(appContext) { ContactRepository(appContext) }
    val preferences = remember(appContext) { GlancePreferences(appContext) }
    val refreshKey = rememberResumeRefreshKey()
    var birthdays by remember { mutableStateOf<List<ContactBirthday>>(emptyList()) }

    LaunchedEffect(enabled, refreshKey) {
        birthdays =
            if (enabled && preferences.isShowBirthdaysEnabled() && repository.hasPermission()) {
                withContext(Dispatchers.IO) {
                    val today = LocalDate.now()
                    val dismissed = preferences.getDismissedBirthdays(today.toString())
                    repository.birthdaysOn(today).filterNot { it.contactId in dismissed }
                }
            } else {
                emptyList()
            }
    }

    return BirthdaysGlance(
        birthdays = birthdays,
        open = { birthday ->
            scope.launch {
                val contact =
                    withContext(Dispatchers.IO) {
                        contactRepository.getContactsByIds(setOf(birthday.contactId)).firstOrNull()
                    }
                // Falls back to the contacts app if the contact vanished since the card loaded.
                if (contact != null) onShowContactMethods(contact) else repository.open(birthday)
            }
        },
        dismiss = { birthday ->
            preferences.dismissBirthday(LocalDate.now().toString(), birthday.contactId)
            birthdays = birthdays.filterNot { it.contactId == birthday.contactId }
        },
    )
}

@Composable
internal fun BirthdayRow(
    birthday: ContactBirthday,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlanceStatusRow(
        icon = {
            Icon(
                imageVector = Icons.Rounded.Cake,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = birthday.name,
        subtitle =
            birthday.age?.let { stringResource(R.string.home_birthday_turns, it) }
                ?: stringResource(R.string.home_birthday_today),
        onClick = onClick,
        onDismiss = onDismiss,
    )
}
