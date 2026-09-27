package com.tk.quicksearch.search.contacts.utils

import com.tk.quicksearch.search.contacts.models.ContactButtonAction
import com.tk.quicksearch.search.contacts.models.ContactCardAction
import com.tk.quicksearch.search.models.ContactInfo
import com.tk.quicksearch.search.models.ContactMethod
import com.tk.quicksearch.search.models.ContactMethodMimeTypes
import com.tk.quicksearch.search.utils.PhoneNumberUtils
import com.tk.quicksearch.shared.util.PackageConstants

/**
 * Resolves the global first/second contact button action for a specific contact, falling back
 * to Call (first button) or SMS (second button) when the contact can't use the chosen action.
 */
object ContactButtonResolver {
    fun resolveForContact(
        contactInfo: ContactInfo,
        action: ContactButtonAction,
        isPrimary: Boolean,
        phoneNumber: String? = null,
    ): ContactButtonAction =
        if (isAvailableForContact(contactInfo, action, phoneNumber)) {
            action
        } else {
            ContactButtonAction.fallback(isPrimary)
        }

    fun isAvailableForContact(
        contactInfo: ContactInfo,
        action: ContactButtonAction,
        phoneNumber: String? = null,
    ): Boolean {
        val methods = contactInfo.contactMethods
        return when (action) {
            ContactButtonAction.CALL, ContactButtonAction.SMS -> true
            ContactButtonAction.WHATSAPP_MESSAGE ->
                methods.any {
                    it is ContactMethod.WhatsAppMessage ||
                        it is ContactMethod.WhatsAppCall ||
                        it is ContactMethod.WhatsAppVideoCall
                }
            ContactButtonAction.TELEGRAM_MESSAGE ->
                methods.any {
                    it is ContactMethod.TelegramMessage ||
                        it is ContactMethod.TelegramCall ||
                        it is ContactMethod.TelegramVideoCall
                }
            ContactButtonAction.SIGNAL_MESSAGE ->
                methods.any {
                    it is ContactMethod.SignalMessage ||
                        it is ContactMethod.SignalCall ||
                        it is ContactMethod.SignalVideoCall
                }
            else -> findMethod(contactInfo, action, phoneNumber) != null
        }
    }

    /** The contact method that performs [action], matched to [phoneNumber] when given. */
    fun findMethod(
        contactInfo: ContactInfo,
        action: ContactButtonAction,
        phoneNumber: String? = null,
    ): ContactMethod? {
        val methods = contactInfo.contactMethods
        return when (action) {
            ContactButtonAction.CALL -> methods.firstMatching<ContactMethod.Phone>(phoneNumber)
            ContactButtonAction.SMS -> methods.firstMatching<ContactMethod.Sms>(phoneNumber)
            ContactButtonAction.EMAIL -> methods.firstOrNull { it is ContactMethod.Email }
            ContactButtonAction.GOOGLE_MEET -> methods.firstMatching<ContactMethod.GoogleMeet>(phoneNumber)
            ContactButtonAction.WHATSAPP_MESSAGE -> methods.firstMatching<ContactMethod.WhatsAppMessage>(phoneNumber)
            ContactButtonAction.WHATSAPP_CALL -> methods.firstMatching<ContactMethod.WhatsAppCall>(phoneNumber)
            ContactButtonAction.WHATSAPP_VIDEO_CALL ->
                methods.firstMatching<ContactMethod.WhatsAppVideoCall>(phoneNumber)
            ContactButtonAction.WHATSAPP_BUSINESS_MESSAGE ->
                methods.whatsAppBusinessMethod(ContactMethodMimeTypes.WHATSAPP_BUSINESS_MESSAGE, phoneNumber)
            ContactButtonAction.WHATSAPP_BUSINESS_CALL ->
                methods.whatsAppBusinessMethod(ContactMethodMimeTypes.WHATSAPP_BUSINESS_VOICE_CALL, phoneNumber)
            ContactButtonAction.WHATSAPP_BUSINESS_VIDEO_CALL ->
                methods.whatsAppBusinessMethod(ContactMethodMimeTypes.WHATSAPP_BUSINESS_VIDEO_CALL, phoneNumber)
            ContactButtonAction.TELEGRAM_MESSAGE -> methods.firstOrNull { it is ContactMethod.TelegramMessage }
            ContactButtonAction.TELEGRAM_CALL -> methods.firstOrNull { it is ContactMethod.TelegramCall }
            ContactButtonAction.TELEGRAM_VIDEO_CALL -> methods.firstOrNull { it is ContactMethod.TelegramVideoCall }
            ContactButtonAction.SIGNAL_MESSAGE -> methods.firstMatching<ContactMethod.SignalMessage>(phoneNumber)
            ContactButtonAction.SIGNAL_CALL -> methods.firstMatching<ContactMethod.SignalCall>(phoneNumber)
            ContactButtonAction.SIGNAL_VIDEO_CALL -> methods.firstMatching<ContactMethod.SignalVideoCall>(phoneNumber)
        }
    }

    /** The [ContactCardAction] the contact's button performs when no per-contact action is set. */
    fun defaultCardAction(
        contactInfo: ContactInfo,
        action: ContactButtonAction,
        isPrimary: Boolean,
    ): ContactCardAction? {
        val resolved = resolveForContact(contactInfo, action, isPrimary)
        if (resolved == ContactButtonAction.EMAIL) {
            return findMethod(contactInfo, resolved)?.let { ContactCardAction.Email(it.data) }
        }
        val phoneNumber = contactInfo.phoneNumbers.firstOrNull() ?: return null
        val fallback =
            if (isPrimary) ContactCardAction.Phone(phoneNumber) else ContactCardAction.Sms(phoneNumber)
        return when (resolved) {
            ContactButtonAction.CALL -> ContactCardAction.Phone(phoneNumber)
            ContactButtonAction.SMS -> ContactCardAction.Sms(phoneNumber)
            ContactButtonAction.EMAIL -> fallback
            ContactButtonAction.GOOGLE_MEET -> ContactCardAction.GoogleMeet(phoneNumber)
            ContactButtonAction.WHATSAPP_MESSAGE -> ContactCardAction.WhatsAppMessage(phoneNumber)
            ContactButtonAction.WHATSAPP_CALL -> ContactCardAction.WhatsAppCall(phoneNumber)
            ContactButtonAction.WHATSAPP_VIDEO_CALL -> ContactCardAction.WhatsAppVideoCall(phoneNumber)
            ContactButtonAction.WHATSAPP_BUSINESS_MESSAGE,
            ContactButtonAction.WHATSAPP_BUSINESS_CALL,
            ContactButtonAction.WHATSAPP_BUSINESS_VIDEO_CALL,
            ->
                (findMethod(contactInfo, resolved, phoneNumber) as? ContactMethod.CustomApp)
                    ?.let { method ->
                        ContactCardAction.CustomApp(
                            phoneNumber = phoneNumber,
                            mimeType = method.mimeType,
                            packageName = method.packageName,
                            dataId = method.dataId,
                            displayLabel = method.displayLabel,
                        )
                    } ?: fallback
            ContactButtonAction.TELEGRAM_MESSAGE -> ContactCardAction.TelegramMessage(phoneNumber)
            ContactButtonAction.TELEGRAM_CALL -> ContactCardAction.TelegramCall(phoneNumber)
            ContactButtonAction.TELEGRAM_VIDEO_CALL -> ContactCardAction.TelegramVideoCall(phoneNumber)
            ContactButtonAction.SIGNAL_MESSAGE -> ContactCardAction.SignalMessage(phoneNumber)
            ContactButtonAction.SIGNAL_CALL -> ContactCardAction.SignalCall(phoneNumber)
            ContactButtonAction.SIGNAL_VIDEO_CALL -> ContactCardAction.SignalVideoCall(phoneNumber)
        }
    }

    private fun matchesNumber(
        method: ContactMethod,
        phoneNumber: String?,
    ): Boolean =
        phoneNumber == null ||
            method.data.isBlank() ||
            PhoneNumberUtils.isSameNumber(method.data, phoneNumber)

    private inline fun <reified T : ContactMethod> List<ContactMethod>.firstMatching(
        phoneNumber: String?,
    ): ContactMethod? = firstOrNull { it is T && matchesNumber(it, phoneNumber) }

    private fun List<ContactMethod>.whatsAppBusinessMethod(
        mimeType: String,
        phoneNumber: String?,
    ): ContactMethod? =
        firstOrNull {
            it is ContactMethod.CustomApp &&
                it.packageName == PackageConstants.WHATSAPP_BUSINESS_PACKAGE &&
                it.mimeType == mimeType &&
                matchesNumber(it, phoneNumber)
        }
}
