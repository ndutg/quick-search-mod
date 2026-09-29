package com.tk.quicksearch.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import com.tk.quicksearch.shared.permissions.PermissionHelper
import java.time.LocalDate
import java.time.MonthDay

/** A contact whose birthday or anniversary falls on the queried day. */
internal class ContactBirthday(
    val contactId: Long,
    val lookupKey: String?,
    val name: String,
    val isAnniversary: Boolean,
    /** The age turned or years married, when the contact's date has a plausible year. */
    val years: Int? = null,
)

/**
 * Reads contact birthdays and anniversaries for the At a Glance card. Callers run [birthdaysOn] off
 * the main thread.
 */
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
                Event.TYPE,
            )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} IN (?, ?)"
        val selectionArgs =
            arrayOf(
                Event.CONTENT_ITEM_TYPE,
                Event.TYPE_BIRTHDAY.toString(),
                Event.TYPE_ANNIVERSARY.toString(),
            )
        val birthdays = linkedMapOf<Pair<Long, Boolean>, ContactBirthday>()
        runCatching {
            context.contentResolver
                .query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val contactId = cursor.getLong(0)
                        val isAnniversary = cursor.getInt(4) == Event.TYPE_ANNIVERSARY
                        val key = contactId to isAnniversary
                        if (key in birthdays) continue
                        val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                        val (date, year) = parseEventDate(cursor.getString(3)) ?: continue
                        if (!date.fallsOn(day)) continue
                        birthdays[key] =
                            ContactBirthday(
                                contactId = contactId,
                                lookupKey = cursor.getString(1),
                                name = name,
                                isAnniversary = isAnniversary,
                                // Some apps store a placeholder year (iOS uses 1604) for dates without one.
                                years = year?.let { day.year - it }?.takeIf { it in 1..MAX_YEARS },
                            )
                    }
                }
        }
        return birthdays.values.sortedWith(compareBy({ it.isAnniversary }, { it.name.lowercase() }))
    }

    fun open(birthday: ContactBirthday) {
        val uri =
            ContactsContract.Contacts.getLookupUri(birthday.contactId, birthday.lookupKey)
                ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, birthday.contactId)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Feb 29 dates show on Feb 28 in non-leap years. */
    private fun MonthDay.fallsOn(day: LocalDate): Boolean {
        val today = MonthDay.from(day)
        return today == this || (this == LEAP_DAY && !day.isLeapYear && today == FEB_28)
    }

    /**
     * Contacts store event dates as `yyyy-MM-dd`, or `--MM-dd` without a year; some sync adapters
     * add a time part or drop the dashes. Returns the month and day, with the year when there is one.
     */
    private fun parseEventDate(raw: String?): Pair<MonthDay, Int?>? {
        val value = raw?.trim()?.take(10) ?: return null
        val match =
            FULL_DATE.matchEntire(value)?.destructured?.let { (year, month, day) -> Triple(year, month, day) }
                ?: NO_YEAR.matchEntire(value)?.destructured?.let { (month, day) -> Triple(null, month, day) }
                ?: COMPACT.matchEntire(value)?.destructured?.let { (year, month, day) -> Triple(year, month, day) }
                ?: return null
        val monthDay = runCatching { MonthDay.of(match.second.toInt(), match.third.toInt()) }.getOrNull() ?: return null
        return monthDay to match.first?.toInt()
    }

    private companion object {
        val FULL_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
        val NO_YEAR = Regex("""--(\d{1,2})-(\d{1,2})""")
        val COMPACT = Regex("""(\d{4})(\d{2})(\d{2})""")
        val LEAP_DAY: MonthDay = MonthDay.of(2, 29)
        val FEB_28: MonthDay = MonthDay.of(2, 28)
        const val MAX_YEARS = 120
    }
}
