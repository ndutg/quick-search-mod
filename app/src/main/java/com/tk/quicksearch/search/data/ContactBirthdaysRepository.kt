package com.tk.quicksearch.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import com.tk.quicksearch.shared.permissions.PermissionHelper
import java.time.LocalDate
import java.time.MonthDay

/** A contact whose birthday falls on the queried day. */
internal class ContactBirthday(
    val contactId: Long,
    val lookupKey: String?,
    val name: String,
    /** The age reached on the queried day, or null when the contact's birth year is unknown. */
    val age: Int?,
)

/** Reads contact birthdays for the At a Glance card. Callers run [birthdaysOn] off the main thread. */
internal class ContactBirthdaysRepository(private val context: Context) {
    fun hasPermission(): Boolean = PermissionHelper.checkContactsPermission(context)

    fun birthdaysOn(day: LocalDate): List<ContactBirthday> {
        if (!hasPermission()) return emptyList()
        val projection =
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                Event.START_DATE,
            )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?"
        val selectionArgs = arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString())
        val birthdays = linkedMapOf<Long, ContactBirthday>()
        runCatching {
            context.contentResolver
                .query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val contactId = cursor.getLong(0)
                        if (contactId in birthdays) continue
                        val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                        val date = parseBirthday(cursor.getString(3)) ?: continue
                        if (!date.fallsOn(day)) continue
                        birthdays[contactId] =
                            ContactBirthday(
                                contactId = contactId,
                                lookupKey = cursor.getString(1),
                                name = name,
                                age = date.year?.let { day.year - it }?.takeIf { it in 1..150 },
                            )
                    }
                }
        }
        return birthdays.values.sortedBy { it.name.lowercase() }
    }

    fun open(birthday: ContactBirthday) {
        val uri =
            ContactsContract.Contacts.getLookupUri(birthday.contactId, birthday.lookupKey)
                ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, birthday.contactId)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private class BirthdayDate(
        val monthDay: MonthDay,
        val year: Int?,
    ) {
        /** Feb 29 birthdays show on Feb 28 in non-leap years. */
        fun fallsOn(day: LocalDate): Boolean {
            val today = MonthDay.from(day)
            return today == monthDay || (monthDay == LEAP_DAY && !day.isLeapYear && today == FEB_28)
        }
    }

    /**
     * Contacts store birthdays as `yyyy-MM-dd`, or `--MM-dd` without a year; some sync adapters
     * add a time part or drop the dashes.
     */
    private fun parseBirthday(raw: String?): BirthdayDate? {
        val value = raw?.trim()?.take(10) ?: return null
        FULL_DATE.matchEntire(value)?.let { match ->
            val (year, month, day) = match.destructured
            return birthdayDate(month.toInt(), day.toInt(), year.toInt())
        }
        NO_YEAR.matchEntire(value)?.let { match ->
            val (month, day) = match.destructured
            return birthdayDate(month.toInt(), day.toInt(), null)
        }
        COMPACT.matchEntire(value)?.let { match ->
            val (year, month, day) = match.destructured
            return birthdayDate(month.toInt(), day.toInt(), year.toInt())
        }
        return null
    }

    private fun birthdayDate(
        month: Int,
        day: Int,
        year: Int?,
    ): BirthdayDate? =
        runCatching { BirthdayDate(MonthDay.of(month, day), year?.takeIf { it > 1900 }) }.getOrNull()

    private companion object {
        val FULL_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
        val NO_YEAR = Regex("""--(\d{1,2})-(\d{1,2})""")
        val COMPACT = Regex("""(\d{4})(\d{2})(\d{2})""")
        val LEAP_DAY: MonthDay = MonthDay.of(2, 29)
        val FEB_28: MonthDay = MonthDay.of(2, 28)
    }
}
