package com.tk.quicksearch.search.contacts

import android.app.Application
import com.tk.quicksearch.search.contacts.models.ContactButtonAction
import com.tk.quicksearch.search.core.MessagingApp
import com.tk.quicksearch.search.core.SearchUiState
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences

class MessagingHandler(
    private val application: Application,
    private val userPreferences: UserAppPreferences,
    private val uiStateUpdater: ((SearchUiState) -> SearchUiState) -> Unit,
) {
    var primaryContactButton: ContactButtonAction = userPreferences.getPrimaryContactButton()
        private set
    var secondaryContactButton: ContactButtonAction = userPreferences.getSecondaryContactButton()
        private set

    var isWhatsAppInstalled: Boolean = false
        private set
    var isWhatsAppBusinessInstalled: Boolean = false
        private set

    var isTelegramInstalled: Boolean = false
        private set
    var isSignalInstalled: Boolean = false
        private set
    var isGoogleMeetInstalled: Boolean = false
        private set

    private fun resolveInstalled(
        action: ContactButtonAction,
        isPrimary: Boolean,
    ): ContactButtonAction =
        if (
            action.isAppInstalled(
                isWhatsAppInstalled = isWhatsAppInstalled,
                isWhatsAppBusinessInstalled = isWhatsAppBusinessInstalled,
                isTelegramInstalled = isTelegramInstalled,
                isSignalInstalled = isSignalInstalled,
                isGoogleMeetInstalled = isGoogleMeetInstalled,
            )
        ) {
            action
        } else {
            ContactButtonAction.fallback(isPrimary)
        }

    /**
     * Records which contact apps are installed and falls the contact buttons back to Call/SMS
     * when their app is no longer installed.
     */
    fun updateMessagingAvailability(
        whatsappInstalled: Boolean,
        whatsappBusinessInstalled: Boolean,
        telegramInstalled: Boolean,
        signalInstalled: Boolean,
        googleMeetInstalled: Boolean,
        updateState: Boolean = true,
    ) {
        isWhatsAppInstalled = whatsappInstalled
        isWhatsAppBusinessInstalled = whatsappBusinessInstalled
        isTelegramInstalled = telegramInstalled
        isSignalInstalled = signalInstalled
        isGoogleMeetInstalled = googleMeetInstalled

        val resolvedPrimary = resolveInstalled(primaryContactButton, isPrimary = true)
        if (resolvedPrimary != primaryContactButton) {
            primaryContactButton = resolvedPrimary
            userPreferences.setPrimaryContactButton(resolvedPrimary)
        }
        val resolvedSecondary = resolveInstalled(secondaryContactButton, isPrimary = false)
        if (resolvedSecondary != secondaryContactButton) {
            secondaryContactButton = resolvedSecondary
            userPreferences.setSecondaryContactButton(resolvedSecondary)
        }

        if (updateState) {
            uiStateUpdater { state ->
                state.copy(
                    primaryContactButton = primaryContactButton,
                    secondaryContactButton = secondaryContactButton,
                    isWhatsAppInstalled = whatsappInstalled,
                    isWhatsAppBusinessInstalled = whatsappBusinessInstalled,
                    isTelegramInstalled = telegramInstalled,
                    isSignalInstalled = signalInstalled,
                    isGoogleMeetInstalled = googleMeetInstalled,
                )
            }
        }
    }

    fun setPrimaryContactButton(action: ContactButtonAction) {
        primaryContactButton = action
        // Persist the user's explicit choice before resolving availability
        userPreferences.setPrimaryContactButton(action)
        refreshAvailability()
    }

    fun setSecondaryContactButton(action: ContactButtonAction) {
        secondaryContactButton = action
        userPreferences.setSecondaryContactButton(action)
        refreshAvailability()
    }

    /** Onboarding picks a messaging app, which sets the second contact button. */
    fun setMessagingApp(app: MessagingApp) {
        setSecondaryContactButton(ContactButtonAction.fromMessagingApp(app))
    }

    private fun refreshAvailability() {
        updateMessagingAvailability(
            whatsappInstalled = isWhatsAppInstalled,
            whatsappBusinessInstalled = isWhatsAppBusinessInstalled,
            telegramInstalled = isTelegramInstalled,
            signalInstalled = isSignalInstalled,
            googleMeetInstalled = isGoogleMeetInstalled,
        )
    }

    fun isPackageInstalled(packageName: String): Boolean {
        val packageManager = application.packageManager
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }
}
